/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.transport.ReliableSocket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Peer implements AutoCloseable
{
    private final ReliableSocket socket;

    private final ExportedDirectory exportedDirectory;
    private final PeerWhitelist peerWhitelist;

    /**
     * TODO: document
     *
     * @param localPort the local UDP port
     * @param exportDirPath TODO: document
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
        return this.exportedDirectory.getPath();
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
        class BooleanHolder
        {
            boolean value = false;
        }

        // initialize job states

        final var jobStates =
            jobs
            .stream()
            .map(j -> new JobState(j, Optional.empty(), 0, Optional.empty()))
            .collect(Collectors.toUnmodifiableList());

        final var jobStatesUpdated = new BooleanHolder();

        // send initial state update

        onJobStatesUpdated.accept(jobStates);

        // start jobs

        final var jobThreads = new ArrayList< Thread >();

        for (final var jobState : jobStates)
        {
            final var thread = new Thread(() -> this.runJob(
                jobState,
                () -> {
                    synchronized (jobStatesUpdated)
                    {
                        jobStatesUpdated.value = true;
                        jobStatesUpdated.notify();
                    }
                }
                ));

            jobThreads.add(thread);

            thread.start();
        }

        // wait for all jobs to complete

        while (jobStates.stream().anyMatch(JobState::hasFinished))
        {
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

    private void runJob(JobState state, Runnable stateUpdated)
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
        JobState state,
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
