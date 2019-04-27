/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Predicate;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class ReliableSocket implements AutoCloseable
{
    private final int localPort;
    private final ServerSocket serverSocket;

    /**
     * TODO: document
     *
     * @param localPort the local UDP port
     */
    public ReliableSocket(int localPort)
    {
        try
        {
            this.localPort    = localPort;
            this.serverSocket = new ServerSocket(localPort);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public int getLocalPort()
    {
        return this.localPort;
    }

    /**
     * Listen for incoming connection requests.
     *
     * This method blocks until a connection request is received and accepted,
     * in which case the connection is returned, or until this socket is closed,
     * in which case null is returned.
     *
     * When a connection request is received, the accept predicate is invoked
     * with the connection's remote endpoint. If the predicate returns true, the
     * connection is accepted and this method returns it; otherwise, it is
     * rejected and this method continues listening for incoming connection
     * requests.
     *
     * @param accept predicate that determines if a connection should be
     *        accepted
     * @return TODO: document
     * @throws IllegalStateException if another invocation of this method is in
     *         progress
     * @throws IllegalStateException if this socket is already closed when this
     *         method is invoked
     */
    public ReliableSocketConnection listen(Predicate< Endpoint > accept)
    {
        try
        {
            while (true)
            {
                final Socket socket = this.serverSocket.accept();

                final var endpoint = new Endpoint(
                    socket.getInetAddress(),
                    socket.getPort()
                    );

                if (accept.test(endpoint))
                    return new ReliableSocketConnection(this, socket);
                else
                    socket.close();
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Attempt to connect to the specified remote endpoint.
     *
     * @param remoteEndpoint TODO: document
     * @return TODO: document
     * @throws RuntimeException if the connection is rejected by the remote
     */
    public ReliableSocketConnection connect(InetSocketAddress remoteEndpoint)
    {
        try
        {
            final Socket socket = new Socket(
                remoteEndpoint.getAddress(),
                remoteEndpoint.getPort()
                );

            return new ReliableSocketConnection(this, socket);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

    }

    /**
     * Close this socket and any open connection.
     *
     * If this socket is already closed, this method does nothing.
     */
    @Override
    public void close()
    {
        try
        {
            this.serverSocket.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}

/* -------------------------------------------------------------------------- */
