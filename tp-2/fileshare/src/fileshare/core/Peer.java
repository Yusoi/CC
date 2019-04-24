/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.transport.MySocket;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Peer implements AutoCloseable
{
    private final MySocket socket;
    private final Path exportDirPath;
    private final Whitelist whitelist;

    /**
     * TODO: document
     *
     * @param localPort the local UDP port
     * @param exportDirPath TODO: document
     */
    public Peer(int localPort, Path exportDirPath)
    {
        this.socket        = new MySocket(localPort);
        this.exportDirPath = Objects.requireNonNull(exportDirPath);
        this.whitelist     = new Whitelist();
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
    public Path getExportDirPath()
    {
        return this.exportDirPath;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Whitelist getWhitelist()
    {
        return this.whitelist;
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
     * @param callbacks TODO: document
     */
    public void runJobs(List< Job > jobs, JobCallbacks callbacks)
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }
}

/* -------------------------------------------------------------------------- */
