/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 *
 * This class is thread-safe.
 */
public class Peer implements AutoCloseable
{
    /**
     * TODO: document
     */
    public enum State
    {
        /**
         * TODO: document
         */
        CREATED,

        /**
         * TODO: document
         */
        RUNNING,

        /**
         * TODO: document
         */
        CLOSED
    }

    /**
     * The value that should be used as a default peer port number.
     */
    public static final int DEFAULT_PORT = 7777;

    private static final long JOB_STATE_UPDATE_DELAY = 500; // in milliseconds

    private State state;

    private final ExportedDirectory exportedDirectory;
    private final AddressWhitelist peerWhitelist;

    private final ReliableSocket socket;
    private final Thread listenThread;
    private final List< Thread > servingThreads;

    /**
     * TODO: document
     *
     * @param localPort the local UDP port
     * @param exportedDirectoryPath TODO: document
     *
     * @throws IllegalArgumentException if localPort is non-positive
     * @throws NullPointerException if exportedDirectoryPath is null
     */
    public Peer(int localPort, Path exportedDirectoryPath) throws IOException
    {
        this.state             = State.CREATED;

        this.exportedDirectory = new ExportedDirectory(exportedDirectoryPath);
        this.peerWhitelist     = new AddressWhitelist();

        this.socket            = new ReliableSocket(localPort);
        this.listenThread      = new Thread(this::listen);
        this.servingThreads    = new ArrayList<>();
    }

    /**
     * Returns the peer's current state.
     *
     * @return the peer's current state
     */
    public synchronized State getState()
    {
        return this.state;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public int getLocalPort()
    {
        return this.socket.getLocalPort();
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Path getExportedDirectoryPath()
    {
        return this.exportedDirectory.getDirectoryPath();
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public AddressWhitelist getPeerWhitelist()
    {
        return this.peerWhitelist;
    }

    /**
     * TODO: document
     *
     * Error if the peer is already running or if it was already closed.
     */
    public synchronized void start()
    {
        // validate state

        if (this.state == State.RUNNING)
            throw new IllegalStateException("Peer is already running.");

        if (this.state == State.CLOSED)
            throw new IllegalStateException("Peer is closed.");

        // start listening thread

        this.listenThread.start();

        // update state

        this.state = State.RUNNING;
    }

    /**
     * TODO: document
     *
     * Calling this before the peer is started or when the peer is already
     * closed does nothing.
     */
    @Override
    public synchronized void close()
    {
        if (this.state == State.RUNNING)
        {
            // close socket

            this.socket.close();

            // join listening and serving threads

            Util.uninterruptibleJoin(this.listenThread);

            synchronized (this.servingThreads)
            {
                this.servingThreads.forEach(Util::uninterruptibleJoin);
            }

            // clear serving thread list

            synchronized (this.servingThreads)
            {
                this.servingThreads.clear();
            }

            // update state

            this.state = State.CLOSED;
        }
    }

    /**
     * TODO: document
     *
     * @param jobs TODO: document
     * @param onJobStatesUpdated TODO: document
     */
    public synchronized void runJobs(
        List< Job > jobs,
        Consumer< List< JobState > > onJobStatesUpdated
        )
    {
        // validate state

        if (this.state != State.RUNNING)
            throw new IllegalStateException("Peer is not running.");

        // initialize job states

        final var jobStates =
            jobs
            .stream()
            .map(JobState::new)
            .collect(Collectors.toUnmodifiableList());

        final Runnable sendJobStateUpdate =
            () -> onJobStatesUpdated.accept(
                jobStates
                    .stream()
                    .map(JobState::clone)
                    .collect(Collectors.toUnmodifiableList())
                );

        // send initial state update

        sendJobStateUpdate.run();

        // start job threads

        final var jobThreads = new ArrayList< Thread >();

        try
        {
            for (final var jobState : jobStates)
            {
                final var thread = new Thread(() -> this.runJob(jobState));
                jobThreads.add(thread);
                thread.start();
            }

            // report progress periodically until all jobs are finished

            while (!jobStates.stream().allMatch(JobState::hasFinished))
            {
                sendJobStateUpdate.run();
                Util.sleepUntilElapsedOrInterrupted(JOB_STATE_UPDATE_DELAY);
            }
        }
        catch (Throwable t)
        {
            // interrupt job threads

            jobThreads.forEach(Thread::interrupt);

            // rethrow

            throw t;
        }
        finally
        {
            // wait for all job threads to die

            jobThreads.forEach(Util::uninterruptibleJoin);
        }

        // send final state update

        sendJobStateUpdate.run();
    }

    private void runJob(JobState state)
    {
        switch (state.getJob().getType())
        {
            case GET:
                PeerRunGetImpl.run(state, this.socket, this.exportedDirectory);
                break;

            case PUT:
                PeerRunPutImpl.run(state, this.socket, this.exportedDirectory);
                break;
        }
    }

    private void listen()
    {
        try
        {
            final var connection = this.socket.listen(
                ep -> this.peerWhitelist.isWhitelisted(ep.getAddress())
                );

            if (connection == null)
                return; // peer is being closed

            try
            {
                final var thread = new Thread(
                    () -> this.serveJob(connection)
                );

                synchronized (this.servingThreads)
                {
                    this.servingThreads.add(thread);
                }

                thread.start();
            }
            catch (Throwable t)
            {
                connection.close();
                throw t;
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void serveJob(ReliableSocketConnection connection)
    {
        try (connection)
        {
            // get job type

            final byte jobType = connection.getInput().readByte();

            // serve job

            switch (jobType)
            {
                case 0:
                    PeerServeGetImpl.serve(connection, this.exportedDirectory);
                    break;

                case 1:
                    PeerServePutImpl.serve(connection, this.exportedDirectory);
                    break;
            }
        }
        catch (Exception ignored)
        {
        }

        // remove thread from serving thread list

        synchronized (this.servingThreads)
        {
            this.servingThreads.remove(Thread.currentThread());
        }
    }
}

/* -------------------------------------------------------------------------- */
