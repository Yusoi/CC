/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.net.InetSocketAddress;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public interface ListenCallbacks
{
    /**
     * TODO: document
     *
     * @param remoteEndpoint TODO: document
     * @return TODO: document
     */
    boolean acceptOrRejectConnection(InetSocketAddress remoteEndpoint);

    /**
     * TODO: document
     *
     * @param remoteEndpoint TODO: document
     */
    default void connectionAccepted(InetSocketAddress remoteEndpoint)
    {
    }

    /**
     * TODO: document
     *
     * @param remoteEndpoint TODO: document
     */
    default void connectionRejected(InetSocketAddress remoteEndpoint)
    {
    }

    /**
     * TODO: document
     *
     * @param remoteEndpoint TODO: document
     * @param errorMessage TODO: document
     */
    default void connectionFailed(
        InetSocketAddress remoteEndpoint,
        String errorMessage
        )
    {
    }
}

/* -------------------------------------------------------------------------- */
