/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/* -------------------------------------------------------------------------- */

/**
 * A UDP-backed socket from which reliable data transfer channels (termed
 * *connections*) between this and other sockets can be obtained.
 *
 * This class is thread-safe.
 */
public class ReliableSocket implements AutoCloseable
{

    private final DatagramSocket udpServerSocket;
    private final int bufferSize = 256;
    private final int headerSize = 40;
    private final AtomicBoolean isListening;

    final List< ReliableSocketConnection > connections;

    /**
     * Creates a {@code ReliableSocket} on the specified local UDP port.
     *
     * @param localPort the socket's local UDP port
     *
     * @throws IOException if an I/O error occurs
     */
    public ReliableSocket(int localPort) throws IOException
    {
        // validate arguments

        if (localPort < 1 || localPort > 65535)
        {
            throw new IllegalArgumentException(
                "port must be between 1 and 65535, inclusive"
            );
        }

        // initialize instance

        this.udpServerSocket = new DatagramSocket(localPort);
        this.isListening = new AtomicBoolean(false);

        this.connections = new ArrayList<>();
    }

    /**
     * Returns this socket's local UDP port.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method (with the exception that
     * invoking {@link #listen(Predicate)} while another invocation is active on
     * the same instance will result in an exception).
     *
     * @return this socket's local UDP port
     */
    public int getLocalPort() {
        return this.udpServerSocket.getLocalPort();
    }

    /**
     * Listen for incoming connection requests.
     *
     * This method blocks until a connection request is received and accepted,
     * in which case the created {@link ReliableSocketConnection} is returned,
     * or until this socket is closed, in which case {@code null} is returned.
     *
     * When a connection request is received, the {@code accept} predicate is
     * invoked with the connection's remote endpoint. If the predicate returns
     * {@code true}, the connection is accepted and this method returns it;
     * otherwise, it is rejected and this method continues listening for
     * incoming connection requests.
     *
     * This method will also return {@code null} if invoked when this socket is
     * already closed.
     *
     * This method will throw {@link IllegalStateException} if invoked
     * concurrently with other invocations of itself on the same socket.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method (with the exception that
     * invoking {@link #listen(Predicate)} while another invocation is active on
     * the same instance will result in an exception).
     *
     * @param accept predicate that determines whether a connection should be
     *        accepted
     * @return the established connection, or null if this socket was closed
     *
     * @throws IllegalStateException if another invocation of this method is in
     *         progress
     * @throws IllegalStateException if this socket is already closed when this
     *         method is invoked
     * @throws IOException if an I/O error occurs
     */
    public ReliableSocketConnection listen(Predicate<Endpoint> accept) throws IOException {
        if (isListening.getAndSet(true))
            throw new IllegalStateException();

        var buf = new byte[bufferSize];

        try{
            while(true){
                var packet = new DatagramPacket(buf, bufferSize);
                this.udpServerSocket.receive(packet);

                //TODO test if it's a correct connection message

                final var remoteEndpoint = new Endpoint(packet.getAddress(), packet.getPort());

                if (accept.test(remoteEndpoint)) {
                    //udpSocket.getOutputStream().write(0);

                    //TODO verify this
                    final var connection = new ReliableSocketConnection(this,udpServerSocket,remoteEndpoint);

                    synchronized (this.connections) {
                        this.connections.add(connection);
                    }

                } else {
                    udpServerSocket.close();
                }
            }
        } finally {
            isListening.set(false);
        }

    }

    /**
     * Attempts to connect to the specified remote endpoint.
     *
     * An {@link IOException} is thrown if the remote explicitly declines the
     * connection attempt.
     *
     * If this socket is closed, invoking this method will result in {@link
     * IllegalStateException} being thrown.
     *
     * Any active calls of this method will throw an exception when {@link
     * #close()} is invoked on this instance.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method (with the exception that
     * invoking {@link #listen(Predicate)} while another invocation is active on
     * the same instance will result in an exception).
     *
     * @param remoteEndpoint the remote's endpoint
     * @return the established connection
     *
     * @throws NullPointerException if remoteEndpoint is null
     * @throws IOException if the connection is rejected by the remote
     * @throws IOException if an I/O error occurs
     */
    public ReliableSocketConnection connect(Endpoint remoteEndpoint) throws IOException {

        var udpSocket = new DatagramSocket();
        var buf = new byte[bufferSize];

        //TODO Fill with connection message
        buf = "connect".getBytes();

        //Sends connection message
        DatagramPacket packet = new DatagramPacket(buf,bufferSize);
        packet.setSocketAddress(new InetSocketAddress(remoteEndpoint.getAddress(),remoteEndpoint.getPort()));
        udpSocket.send(packet);

        try {

            //Receives confirmation message
            udpSocket.receive(packet);
            if ((new String(packet.getData())).isEmpty())
                throw new IOException("Connection refused.");
            else {
                //TODO testar mensagem

            }
        } catch (Throwable t) {
            udpSocket.close();
            throw t;
        }

        final var connection = new ReliableSocketConnection(this, udpSocket, remoteEndpoint);

        synchronized (this.connections) {
            this.connections.add(connection);
        }

        return connection;
    }

    /**
     * Checks whether this socket has been closed.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method (with the exception that
     * invoking {@link #listen(Predicate)} while another invocation is active on
     * the same instance will result in an exception).
     *
     * @return whether this socket has been closed
     */
    public boolean isClosed() {
        return this.udpServerSocket.isClosed();
    }

    /**
     * Closes this socket and any open connection previously obtained from it
     * (as if by invoking {@link ReliableSocketConnection#close()} on each of
     * them).
     *
     * Any active calls of {@link #listen(Predicate)} or {@link
     * #connect(Endpoint)} on this socket will throw an exception when this
     * method is called.
     *
     * Invoking {@link #listen(Predicate)} or {@link #connect(Endpoint)} on this
     * socket after it is closed will result in {@link IOException} being
     * thrown.
     *
     * If this socket is already closed, this method has no effect.
     *
     * If this method fails, this socket and its associated connections will
     * nevertheless be left in a closed state.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method (with the exception that
     * invoking {@link #listen(Predicate)} while another invocation is active on
     * the same instance will result in an exception).
     */
    @Override
    public void close() throws IOException {

        synchronized (this.connections) {
            for (final var connection : this.connections) {
                try {
                    //TODO send message confirming the disconnection



                } catch (Exception ignored) {
                }
            }
        }

        this.udpServerSocket.close();
    }
}

/* -------------------------------------------------------------------------- */

