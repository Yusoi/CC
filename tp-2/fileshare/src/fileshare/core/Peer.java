/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
     * In milliseconds.
     */
    private static final long STATUS_UPDATE_DELAY = 200;

    /**
     * TODO: document
     */
    private static final byte JOB_ID_GET = 0;

    /**
     * TODO: document
     */
    private static final byte JOB_ID_PUT = 1;

    private State state;

    private final ReliableSocket socket;
    private final Thread listenThread;
    private final List< Thread > servingThreads;

    private final ExportedDirectory exportedDirectory;
    private final PeerWhitelist peerWhitelist;

    /**
     * TODO: document
     *
     * @param localPort the local UDP port
     * @param exportedDirectoryPath TODO: document
     *
     * @throws IllegalArgumentException if localPort is non-positive
     * @throws NullPointerException if exportedDirectoryPath is null
     */
    public Peer(int localPort, Path exportedDirectoryPath)
    {
        this.state             = State.CREATED;

        this.socket            = new ReliableSocket(localPort);
        this.listenThread      = new Thread(this::listen);
        this.servingThreads    = new ArrayList<>();

        this.exportedDirectory = new ExportedDirectory(exportedDirectoryPath);
        this.peerWhitelist     = new PeerWhitelist();
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
    public PeerWhitelist getPeerWhitelist()
    {
        return this.peerWhitelist;
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
        // stop listening thread (if running)

        if (this.state == State.RUNNING)
        {
            // interrupt listening and serving threads

            this.listenThread.interrupt();
            this.servingThreads.forEach(Thread::interrupt);

            // join listening and serving threads

            Util.uninterruptibleJoin(this.listenThread);
            this.servingThreads.forEach(Util::uninterruptibleJoin);

            // clear serving thread list

            this.servingThreads.clear();
        }

        // update state

        this.state = State.CLOSED;
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
            .map(AtomicReference::new)
            .collect(Collectors.toUnmodifiableList());

        final var jobStatesUpdated = new AtomicBoolean(false);

        final Runnable sendJobStateUpdate = () -> onJobStatesUpdated.accept(
            jobStates
                .stream()
                .map(AtomicReference::get)
                .collect(Collectors.toUnmodifiableList())
            );

        // send initial state update

        sendJobStateUpdate.run();

        // start jobs

        final var jobThreads = new ArrayList< Thread >();

        for (final var jobStateReference : jobStates)
        {
            final var thread = new Thread(() -> this.runJob(
                jobStateReference.get(),
                s -> { jobStateReference.set(s); jobStatesUpdated.set(true); }
            ));

            jobThreads.add(thread);

            thread.start();
        }

        // wait for all jobs to complete

        while (!jobStates.stream().allMatch(s -> s.get().hasFinished()))
        {
            if (jobStatesUpdated.getAndSet(false))
                sendJobStateUpdate.run();

            Util.sleepUntilElapsedOrInterrupted(STATUS_UPDATE_DELAY);
        }

        // send final state update

        sendJobStateUpdate.run();

        // wait for all threads to die

        jobThreads.forEach(Util::uninterruptibleJoin);
    }

    private void runJob(JobState state, Consumer< JobState > stateUpdated)
    {
        try
        {
            // TODO: support jobs with multiple remotes

            if (state.getJob().getRemoteEndpoints().size() > 1)
            {
                throw new UnsupportedOperationException(
                    "multiple remotes not yet supported"
                );
            }

            final var remoteEndpoint =
                state
                .getJob()
                .getRemoteEndpoints()
                .get(0);

            // establish connection with remote

            try (final var connection = this.socket.connect(remoteEndpoint))
            {
                final var connInput  = connection.getDataInputStream();
                final var connOutput = connection.getDataOutputStream();

                switch (state.getJob().getType())
                {
                    case GET:
                        runJobGet(state, stateUpdated, connInput, connOutput);
                        break;

                    case PUT:
                        runJobPut(state, stateUpdated, connInput, connOutput);
                        break;
                }
            }
        }
        catch (Exception e)
        {
            // update state with error

            state = state.withErrorMessage(Optional.of(e.getMessage()));
            stateUpdated.accept(state);
        }
    }

    private void runJobGet(
        JobState state,
        Consumer< JobState > stateUpdated,
        DataInputStream connInput,
        DataOutputStream connOutput
        ) throws IOException
    {
        // send request

        connOutput.writeByte(JOB_ID_GET);
        connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
        connOutput.flush();

        // receive response

        final long fileSize = connInput.readLong();

        if (fileSize < 0)
        {
            throw new IllegalArgumentException(
                "File does not exist in remote."
            );
        }

        // update state with total bytes

        state = state.withTotalBytes(Optional.of(fileSize));
        stateUpdated.accept(state);

        // open local file

        final var fileOutput = this.exportedDirectory.openFileForWriting(
            state.getJob().getLocalFilePath(),
            fileSize
            );

        try (fileOutput)
        {
            // receive file data

            final long transferredBytes;

            if (fileSize == 0)
            {
                transferredBytes = 0;
            }
            else
            {
                try (final var channel = fileOutput.getChannel())
                {
                    transferredBytes = channel.transferFrom(
                        Channels.newChannel(connInput),
                        0,
                        fileSize
                        );
                }
            }

            // check transferred byte count

            if (transferredBytes < fileSize)
            {
                throw new RuntimeException(
                    String.format(
                        "Only transferred %s of %s bytes.",
                        transferredBytes, fileSize
                    )
                );
            }

            // commit file changes

            fileOutput.commitAndClose();
        }

        // update state with transferred bytes (TODO: update periodically)

        state = state.withTransferredBytes(fileSize);
        stateUpdated.accept(state);
    }

    private void runJobPut(
        JobState state,
        Consumer< JobState > stateUpdated,
        DataInputStream connInput,
        DataOutputStream connOutput
        ) throws IOException
    {
        // open local file

        final var fileInput = this.exportedDirectory.openFileForReading(
            state.getJob().getLocalFilePath()
            );

        final long fileSize;

        try (fileInput)
        {
            fileSize = fileInput.length();

            // update state with total bytes

            state = state.withTotalBytes(Optional.of(fileSize));
            stateUpdated.accept(state);

            // send request (no response necessary)

            connOutput.writeByte(JOB_ID_PUT);
            connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
            connOutput.writeLong(fileSize);

            // send file data

            try (final var fileInputChannel = fileInput.getChannel())
            {
                fileInputChannel.transferTo(
                    0,
                    fileInput.length(),
                    Channels.newChannel(connOutput)
                );
            }

        }

        // update state with transferred bytes (TODO: update periodically)

        state = state.withTransferredBytes(fileSize);
        stateUpdated.accept(state);
    }

    private void listen()
    {
        while (!Thread.interrupted())
        {
            final var connection = socket.listen(
                e -> this.peerWhitelist.isWhitelisted(e.getAddress())
            );

            final Thread thread;

            try
            {
                thread = new Thread(() -> this.serveJob(connection));
                thread.start();
            }
            catch (Exception e)
            {
                connection.close();
                throw e;
            }

            this.servingThreads.add(thread);

            try (connection)
            {
            }
            catch (IOException e)
            {
            }
        }
    }

    private void serveJob(ReliableSocketConnection connection)
    {
        try (connection)
        {
            final var input  = new DataInputStream(connection.getInputStream());
            final var output = new DataOutputStream(connection.getOutputStream());

            final var jobTypeByte = input.readByte();
            final var filePath    = Path.of(input.readUTF());

            switch (jobTypeByte)
            {
                case 0:
                    serveJobGet(output, filePath);
                    break;

                case 1:
                    serveJobPut(output, filePath);
                    break;
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void serveJobGet(
        DataOutputStream input,
        Path filePath
        )
    {

    }

    private static class JobStateImpl extends JobState
    {
        private final Job job;

        private Optional< Long > totalBytes;
        private long transferredBytes;
        private Optional< Long > throughput;

        private Optional< String > errorMessage;

        public JobStateImpl(Job job)
        {
            this.job              = job;

            this.totalBytes       = Optional.empty();
            this.transferredBytes = 0;
            this.throughput       = Optional.empty();

            this.errorMessage     = Optional.empty();

        }

        @Override
        public Job getJob()
        {
            return this.job;
        }

        @Override
        public Optional< Long > getTotalBytes()
        {
            return this.totalBytes;
        }

        @Override
        public long getTransferredBytes()
        {
            return this.transferredBytes;
        }

        @Override
        public Optional< Long > getThroughput()
        {
            return this.throughput;
        }

        @Override
        public Optional< String > getErrorMessage()
        {
            return this.errorMessage;
        }

        public void setTotalBytes(Optional<Long> totalBytes)
        {
            this.totalBytes = totalBytes;
        }

        public void setTransferredBytes(long transferredBytes)
        {
            this.transferredBytes = transferredBytes;
        }

        public void setErrorMessage(Optional<String> errorMessage)
        {
            this.errorMessage = errorMessage;
        }
    }
}

/* -------------------------------------------------------------------------- */
