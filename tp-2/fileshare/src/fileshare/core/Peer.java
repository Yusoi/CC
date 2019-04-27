/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.transport.ReliableSocket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Peer implements AutoCloseable
{
    private static class JobStateImpl extends JobState
    {
        private final Job job;

        private Optional< Long > totalBytes;
        private long transferredBytes;
        private Optional< String > errorMessage;

        public JobStateImpl(
            Job job,
            Optional< Long > totalBytes,
            long transferredBytes,
            Optional< String > errorMessage
            )
        {
            // validate arguments

            Objects.requireNonNull(job);
            Objects.requireNonNull(totalBytes);
            Objects.requireNonNull(errorMessage);

            if (transferredBytes < 0)
                throw new IllegalArgumentException();

            if (totalBytes.isPresent())
            {
                if (totalBytes.get() < 0 || totalBytes.get() < transferredBytes)
                    throw new IllegalArgumentException();
            }

            // initialize instance

            this.job              = job;
            this.totalBytes       = totalBytes;
            this.transferredBytes = transferredBytes;
            this.errorMessage     = errorMessage;
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

    private final ReliableSocket socket;

    private final ExportedDirectory exportedDirectory;
    private final PeerWhitelist peerWhitelist;

    /**
     * TODO: document
     *
     * @param localPort the local UDP port
     * @param exportedDirectoryPath TODO: document
     */
    public Peer(int localPort, Path exportedDirectoryPath)
    {
        this.socket = new ReliableSocket(localPort);

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
     * TODO: document
     */
    public void start()
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     */
    @Override
    public void close()
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     *
     * @param jobs TODO: document
     * @param onJobStatesUpdated TODO: document
     */
    public void runJobs(
        List< Job > jobs,
        Consumer< List< JobState > > onJobStatesUpdated
        )
    {
        // initialize job states

        final var jobStates =
            jobs
            .stream()
            .map(job -> (JobState) new JobStateImpl(
                job, Optional.empty(), 0, Optional.empty()
            ))
            .collect(Collectors.toUnmodifiableList());

        final var jobStatesUpdated = new AtomicBoolean(false);

        // send initial state update

        onJobStatesUpdated.accept(jobStates);

        // start jobs

        final var jobThreads = new ArrayList< Thread >();

        for (final var jobState : jobStates)
        {
            final var thread = new Thread(() -> this.runJob(
                (JobStateImpl) jobState,
                () -> jobStatesUpdated.set(true)
                ));

            jobThreads.add(thread);

            thread.start();
        }

        // wait for all jobs to complete

        while (!jobStates.stream().allMatch(JobState::hasFinished))
        {
            if (jobStatesUpdated.getAndSet(false))
                onJobStatesUpdated.accept(jobStates);

            try
            {
                Thread.sleep(200);
            }
            catch (InterruptedException e)
            {
            }
        }

        // send final state update

        onJobStatesUpdated.accept(jobStates);

        // wait for all thread to die

        for (final var thread : jobThreads)
        {
            while (true)
            {
                try
                {
                    thread.join();
                    break;
                }
                catch (InterruptedException e)
                {
                }
            }
        }
    }

    private void runJob(JobStateImpl state, Runnable stateUpdated)
    {
        // TODO: support jobs with multiple remotes

        if (state.getJob().getRemoteEndpoints().size() > 1)
        {
            throw new UnsupportedOperationException(
                "multiple remotes not yet supported"
                );
        }

        final var remoteEndpoint = state.getJob().getRemoteEndpoints().get(0);

        // establish connection with remote

        try (final var connection = this.socket.connect(remoteEndpoint))
        {
            final var in = new DataInputStream(connection.getInputStream());
            final var out = new DataOutputStream(connection.getOutputStream());

            switch (state.getJob().getType())
            {
                case GET:
                    runJobGet(state, in, out, stateUpdated);
                    break;

                case PUT:
                    // runJobPut(state, in, out, stateUpdated);
                    break;
            }
        }
        catch (Exception e)
        {
            state.setErrorMessage(Optional.of(e.getMessage()));
        }

        // ensure notification that job is done

        stateUpdated.run();
    }

    private void runJobGet(
        JobStateImpl state,
        DataInputStream in,
        DataOutputStream out,
        Runnable stateUpdated
        ) throws IOException
    {
        // send request

        out.writeByte(0);
        out.writeUTF(state.getJob().getRemoteFilePath().toString());
        out.flush();

        // receive response

        final long response = in.readLong();

        if (response < 0)
        {
            throw new IllegalArgumentException(
                "File does not exist on remote."
                );
        }

        // update state with total bytes

        state.setTotalBytes(Optional.of(response));
        stateUpdated.run();

        // transfer file

        this.exportedDirectory.writeFile(
            state.getJob().getLocalFilePath(),
            in,
            response,
            t -> { state.setTransferredBytes(t); stateUpdated.run(); }
            );
    }
}

/* -------------------------------------------------------------------------- */
