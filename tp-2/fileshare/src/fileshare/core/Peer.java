/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/* -------------------------------------------------------------------------- */

/**
 * Represents a local FileShare peer.
 *
 * This class is thread-safe.
 */
public class Peer implements AutoCloseable
{
    /**
     * Defines the possible states of a peer.
     */
    public enum State
    {
        /**
         * The peer was not yet started.
         */
        CREATED,

        /**
         * The peer has been started and is running.
         */
        OPEN,

        /**
         * The peer has been closed.
         */
        CLOSED
    }

    /**
     * The value that should be used as a default peer port number.
     */
    public static final int DEFAULT_PORT = 7777;

    private static final long JOB_STATE_UPDATE_DELAY = 200; // in milliseconds

    private State state;

    private final ExportedDirectory exportedDirectory;
    private final AddressWhitelist peerWhitelist;

    private final ReliableSocket socket;
    private final Thread listenThread;
    private final List< Thread > servingThreads;

    /**
     * Initializes a local FileShare peer.
     *
     * @param localPort the peer's local UDP port
     * @param exportedDirectoryPath a path to the local directory to be exported
     *        by the peer
     *
     * @throws IllegalArgumentException if {@code localPort} is not a valid UDP
     *         port number
     * @throws NullPointerException if {@code exportedDirectoryPath} is {@code
     *         null}
     * @throws IOException if an I/O error occurs
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
     * Returns the current state of this peer.
     *
     * @return the current state of this peer
     */
    public synchronized State getState()
    {
        return this.state;
    }

    /**
     * Returns the local UDP port used by this peer.
     *
     * @return the local UDP port used by this peer
     */
    public int getLocalPort()
    {
        return this.socket.getLocalPort();
    }

    /**
     * Returns a path to the local directory exported by this peer.
     *
     * @return a path to the local directory exported by this peer
     */
    public Path getExportedDirectoryPath()
    {
        return this.exportedDirectory.getDirectoryPath();
    }

    /**
     * Returns the addresses whitelist of peersthat may connect to this peer.
     *
     * @return the addresses whitelist of peersthat may connect to this peer
     */
    public AddressWhitelist getPeerWhitelist()
    {
        return this.peerWhitelist;
    }

    /**
     * Opens this peer, setting its state to {@link State#OPEN}.
     *
     * @throws IllegalStateException if this peer is not in state {@link
     *         State#CREATED}
     */
    public synchronized void open()
    {
        // validate state

        if (this.state == State.OPEN)
            throw new IllegalStateException("Peer is already running.");

        if (this.state == State.CLOSED)
            throw new IllegalStateException("Peer is closed.");

        // open socket

        this.socket.open();

        // start listening thread

        this.listenThread.start();

        // update state

        this.state = State.OPEN;
    }

    /**
     * Closes this peer, setting its state to {@link State#CLOSED}.
     *
     * If this peer is in state {@link State#CLOSED}, this method has no effect.
     */
    @Override
    public synchronized void close()
    {
        if (this.state == State.OPEN)
        {
            // close socket

            this.socket.close();

            // wait for listening thread to finish

            Util.uninterruptibleJoin(this.listenThread);

            // wait for serving threads to finish

            final List< Thread > servingThreadsCopy;

            synchronized (this.servingThreads)
            {
                servingThreadsCopy = new ArrayList<>(this.servingThreads);
            }

            servingThreadsCopy.forEach(Util::uninterruptibleJoin);
        }

        // update state

        this.state = State.CLOSED;
    }

    /**
     * Runs one or more jobs.
     *
     * The {@code onJobStatesUpdated} callback is invoked with a list of one job
     * state for each of the jobs specified in {@code jobs}, in the same order.
     *
     * @param jobs a list of the jobs to be run
     * @param onJobStatesUpdated callback invoked when the state of the jobs
     *        being run is updated
     *
     * @throws NullPointerException if {@code jobs} or {@code
     *         onJobStatesUpdated} are {@code null}
     * @throws IllegalArgumentException if {@code jobs} is empty
     */
    public synchronized void runJobs(
        List< Job > jobs,
        Consumer< List< JobState > > onJobStatesUpdated
        )
    {
        // validate arguments and state

        Objects.requireNonNull(jobs);
        Objects.requireNonNull(onJobStatesUpdated);

        if (jobs.size() == 0)
            throw new IllegalArgumentException("jobs must not be empty");

        if (this.state != State.OPEN)
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
            while (true)
            {
                // listen for connection attempts

                final var connection = this.socket.listen(
                    ep -> this.peerWhitelist.isWhitelisted(ep.getAddress())
                );

                // finish listening thread if local socket was closed

                if (connection == null)
                    return;

                // create serving thread

                try
                {
                    final var thread = new Thread(
                        () -> this.serveJob(connection)
                    );

                    // add thread to serving thread list

                    synchronized (this.servingThreads)
                    {
                        this.servingThreads.add(thread);
                    }

                    // start serving thread

                    thread.start();
                }
                catch (Throwable t)
                {
                    connection.close();
                    throw t;
                }
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
            // receive job type

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
            if (!this.servingThreads.remove(Thread.currentThread()))
                throw new RuntimeException();
        }
    }
}

/* -------------------------------------------------------------------------- */
