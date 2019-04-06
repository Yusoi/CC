/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Peer
{
    /**
     * TODO: document
     *
     * @param port TODO: document
     * @param exportDirPath TODO: document
     */
    public Peer(int port, Path exportDirPath)
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public int getPort()
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Path getExportDirPath()
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public List< InetSocketAddress > getConnectedRemoteEndpoints()
    {
        // TODO: implement
        throw new UnsupportedOperationException();
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
    public void stop()
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     *
     * @param callbacks TODO: document
     */
    public void startListening(ListenCallbacks callbacks)
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     */
    public void stopListening()
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
