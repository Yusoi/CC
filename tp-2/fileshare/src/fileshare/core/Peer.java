/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.Util;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
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

    private static final long STATUS_UPDATE_DELAY = 200; // in milliseconds
    private static final byte JOB_ID_GET = 0;
    private static final byte JOB_ID_PUT = 1;

    private State state;

    private final ReliableSocket socket;
    private final Thread listenThread;
    private final List< Thread > servingThreads;

    private final ExportedDirectory exportedDirectory;
    private final AddressWhitelist peerWhitelist;

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

        this.socket            = new ReliableSocket(localPort);
        this.listenThread      = new Thread(this::listen);
        this.servingThreads    = new ArrayList<>();

        this.exportedDirectory = new ExportedDirectory(exportedDirectoryPath);
        this.peerWhitelist     = new AddressWhitelist();
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

        // start job threads

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

        // report progress periodically until all jobs are finished

        while (!jobStates.stream().allMatch(s -> s.get().hasFinished()))
        {
            if (jobStatesUpdated.getAndSet(false))
                sendJobStateUpdate.run();

            Util.sleepUntilElapsedOrInterrupted(STATUS_UPDATE_DELAY);
        }

        // wait for all job threads to die

        jobThreads.forEach(Util::uninterruptibleJoin);

        // send final state update

        sendJobStateUpdate.run();
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
                final var connInput = new DataInputStream(
                    connection.getInputStream()
                    );

                final var connOutput = new DataOutputStream(
                    connection.getOutputStream()
                    );

                // run job

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

    private void runJobPutMultipleRemotes(
        JobState state,
        Runnable onStateUpdated
        ) throws IOException
    {
        final var numRemotes = state.getJob().getRemoteEndpoints().size();

        final var connections = new ArrayList< ReliableSocketConnection >();
        final var threads     = new ArrayList< Thread >();

        // open local file

        final var fileInput = this.exportedDirectory.openFileForReading(
            state.getJob().getLocalFilePath()
            );

        try (fileInput)
        {
            // update state with total bytes

            final var fileSize = fileInput.length();

            state.setTotalBytes(Optional.of(numRemotes * fileSize));
            onStateUpdated.run();

            // open connections

            for (final var endpoint : state.getJob().getRemoteEndpoints())
                connections.add(this.socket.connect(endpoint));

            // ...

            for (final var conn : connections)
            {
                final var connInput  = new DataInputStream(conn.getInputStream());
                final var connOutput = new DataOutputStream(conn.getOutputStream());

                final var thread = new Thread(() -> {

                    // send request

                    connOutput.writeByte(2);
                    connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
                    connOutput.writeLong(fileSize);
                    connOutput.flush();

                    // receive response

                    final var error1 = connInput.readUTF();

                    if (!error1.isEmpty())
                    {
                        // TODO: implement
                    }

                    // send file and shutdown output

                    Util.transfer(
                        fileInput.getChannel(),
                        Channels.newChannel(connOutput),
                        t -> {
                            synchronized (state)
                            {
                                state.setTransferredBytes(
                                    state.getTransferredBytes() + t
                                    );
                            }

                            onStateUpdated.run();
                        }
                        );

                    conn.shutdownOutput();

                    // receive response

                    final var error2 = connInput.readUTF();

                    if (!error2.isEmpty())
                    {
                        // TODO: implement
                    }
                });
            }
        }
        finally
        {
            // interrupt threads and wait for them to die

            threads.forEach(Thread::interrupt);
            threads.forEach(Util::uninterruptibleJoin);

            // close connections

            for (final var connection : connections)
                connection.close();
        }
    }

    private void runJobGetMultipleRemotes(
        JobState state,
        Consumer< JobState > stateUpdated
        ) throws IOException
    {
        final var connections = new ArrayList< ReliableSocketConnection >();

        try
        {

            // open connections

            for (final var endpoint : state.getJob().getRemoteEndpoints())
                connections.add(this.socket.connect(endpoint));

            final var connInputStreams =
                connections
                    .stream()
                    .map(ReliableSocketConnection::getInputStream)
                    .map(DataInputStream::new)
                    .collect(Collectors.toUnmodifiableList());

            final var connOutputStreams =
                connections
                    .stream()
                    .map(ReliableSocketConnection::getOutputStream)
                    .map(DataOutputStream::new)
                    .collect(Collectors.toUnmodifiableList());

            // check file existence and size in all remotes

            for (final var connInput : connInputStreams)
            {
                connOutput.writeByte(JOB_ID_GET);
                connOutput.writeUTF(state.getJob().getRemoteFilePath().toString());
                connOutput.flush();

                connInput.readLong();
            }

            // partition file

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
                // transfer file data


            }

            // update state with transferred bytes (TODO: update periodically)

            state = state.withTransferredBytes(fileSize);
            stateUpdated.accept(state);
        }
        finally
        {
            // close connections

            for (final var connection : connections)
                connection.close();
        }



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

    private void listen()
    {
        try
        {
            while (!Thread.interrupted())
            {
                final var connection = socket.listen(
                    ep -> this.peerWhitelist.isWhitelisted(ep.getAddress())
                    );

                final Thread thread;

                try
                {
                    thread = new Thread(() -> this.serveJob(connection));

                    this.servingThreads.add(thread);

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
            final var connInput = new DataInputStream(
                connection.getInputStream()
                );

            final var connOutput = new DataOutputStream(
                connection.getOutputStream()
                );

            // read job info

            final var jobTypeByte = connInput.readByte();

            final Path localFilePath;

            try
            {
                localFilePath = Path.of(connInput.readUTF());
            }
            catch (InvalidPathException e)
            {
                return;
            }

            // serve job

            switch (jobTypeByte)
            {
                case 0:
                    serveJobGet(localFilePath, connInput, connOutput);
                    break;

                case 1:
                    serveJobPut(localFilePath, connInput, connOutput);
                    break;
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void serveJobGet(
        Path localFilePath,
        DataInputStream connInput,
        DataOutputStream connOutput
        ) throws IOException
    {
        // open local file

        final RandomAccessFile fileInput;

        try
        {
            fileInput = this.exportedDirectory.openFileForReading(
                localFilePath
                );
        }
        catch (FileNotFoundException e)
        {
            connOutput.writeLong(-1);
            return;
        }

        try (fileInput)
        {
            // send file size

            connOutput.writeLong(fileInput.length());

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
    }

    private void serveJobPut(
        Path localFilePath,
        DataInputStream connInput,
        DataOutputStream connOutput
        ) throws IOException
    {
        // read file size

        final long fileSize = connInput.readLong();

        // open local file

        final var fileOutput = this.exportedDirectory.openFileForWriting(
            localFilePath,
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
    }
}

/* -------------------------------------------------------------------------- */
