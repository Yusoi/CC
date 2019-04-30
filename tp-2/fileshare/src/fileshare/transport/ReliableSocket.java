/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Predicate;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class ReliableSocket implements AutoCloseable
{
    private final ServerSocket tcpServerSocket;

    /**
     * TODO: document
     *
     * @param localPort the local UDP port
     */
    public ReliableSocket(int localPort) throws IOException
    {
        this.tcpServerSocket = new ServerSocket(localPort);
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public int getLocalPort()
    {
        return this.tcpServerSocket.getLocalPort();
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
     * @param accept predicate that determines whether a connection should be
     *        accepted
     * @return TODO: document
     *
     * @throws IllegalStateException if another invocation of this method is in
     *         progress
     * @throws IllegalStateException if this socket is already closed when this
     *         method is invoked
     */
    public ReliableSocketConnection listen(
        Predicate< Endpoint > accept
        ) throws IOException
    {
        while (true)
        {
            final var tcpSocket = this.tcpServerSocket.accept();

            final var remoteEndpoint = new Endpoint(
                tcpSocket.getInetAddress(),
                tcpSocket.getPort()
                );

            if (accept.test(remoteEndpoint))
                return new ReliableSocketConnection(this, tcpSocket);
            else
                tcpSocket.close();
        }
    }

    /**
     * Attempt to connect to the specified remote remoteEndpoint.
     *
     * @param remoteEndpoint TODO: document
     * @return TODO: document
     *
     * @throws NullPointerException if remoteEndpoint is null
     * @throws IOException if the connection is rejected by the remote
     * @throws IOException if an I/O error occurs
     */
    public ReliableSocketConnection connect(
        Endpoint remoteEndpoint
        ) throws IOException
    {
        final var tcpSocket = new Socket(
            remoteEndpoint.getAddress(),
            remoteEndpoint.getPort()
            );

        return new ReliableSocketConnection(this, tcpSocket);
    }

    /**
     * Close this socket and any open connection.
     *
     * If this socket is already closed, this method does nothing.
     */
    @Override
    public void close() throws IOException
    {
        this.tcpServerSocket.close();
    }
}

/* -------------------------------------------------------------------------- */
