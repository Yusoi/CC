/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final DatagramSocket udpSocket;
    private final Thread receiverThread;

    private final AtomicBoolean isListening;

    /**
     * Last connection identifier generated by this side.
     */
    private final AtomicInteger nextLocalConnectionSeqnum;

    private final Map< ConnectionIdentifier, IncomingConnectionAttempt >
        incomingConnectionAttempts;

    private final Map< ConnectionIdentifier, OutgoingConnectionAttempt >
        outgoingConnectionAttempts;

    private final Map< ConnectionIdentifier, ReliableSocketConnection >
        openConnections;

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

        this.udpSocket = new DatagramSocket(localPort);
        this.receiverThread = new Thread(this::receiver);

        this.isListening = new AtomicBoolean(false);

        this.nextLocalConnectionSeqnum = new AtomicInteger(0);

        this.incomingConnectionAttempts = new HashMap<>();
        this.outgoingConnectionAttempts = new HashMap<>();
        this.openConnections = new HashMap<>();
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
    public int getLocalPort()
    {
        return this.udpSocket.getLocalPort();
    }

    /**
     * TODO: document
     */
    public void open()
    {
        this.receiverThread.start();
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
    public ReliableSocketConnection listen(
        Predicate< Endpoint > accept
        ) throws IOException
    {
        if (isListening.getAndSet(true))
            throw new IllegalStateException();

        try
        {
            // TODO: implement
        }
        finally
        {
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
    public ReliableSocketConnection connect(
        Endpoint remoteEndpoint
        ) throws IOException
    {
        // allocate buffer for holding data of packets to be sent

        final var packetBuffer = new byte[Config.MAX_PACKET_SIZE];

        // generate local connection id

        final var localConnectionId =
            this.nextLocalConnectionSeqnum.getAndIncrement();

        // register connection attempt

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            localConnectionId
        );

        final var connectionAttempt = new OutgoingConnectionAttempt();

        synchronized (this)
        {
            this.outgoingConnectionAttempts.put(
                connectionId,
                connectionAttempt
            );
        }

        try
        {
            for (int i = 0; i < Config.MAX_CONNECTION_ATTEMPTS; ++i)
            {
                // send connection request

                this.sendPacketConn(
                    packetBuffer, remoteEndpoint, localConnectionId
                );

                // wait for response

                final OptionalInt remoteConnectionId;

                try
                {
                    remoteConnectionId = connectionAttempt.waitForResponse(
                        Config.CONNECTION_RETRY_DELAY
                    );
                }
                catch (TimeoutException ignored)
                {
                    // no response received, retry
                    continue;
                }

                if (remoteConnectionId.isEmpty())
                {
                    // connection refused

                    throw new IOException(
                        "The peer refused to establish a connection."
                    );
                }
                else
                {
                    // connection accepted, send acknowledgment

                    this.sendPacketConnAcceptAck(
                        packetBuffer,
                        remoteEndpoint,
                        remoteConnectionId.getAsInt()
                    );

                    synchronized (this)
                    {
                        // unregister connection attempt

                        this.outgoingConnectionAttempts.remove(connectionId);

                        // register connection

                        final var connection = new ReliableSocketConnection(
                            this,
                            remoteEndpoint,
                            localConnectionId,
                            remoteConnectionId.getAsInt()
                        );

                        this.openConnections.put(connectionId, connection);

                        // return connection

                        return connection;
                    }
                }
            }

            // reached maximum number of retries and received no response, fail

            throw new IOException(
                "The peer did not respond to the connection request."
            );
        }
        catch (Throwable t)
        {
            synchronized (this)
            {
                // unregister connection attempt

                this.outgoingConnectionAttempts.remove(connectionId);
            }

            throw t;
        }
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
    public boolean isClosed()
    {
        // TODO: implement
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
     * (TODO: would simplify things if this didn't throw checked exceptions)
     * If this method fails, this socket and its associated connections will
     * nevertheless be left in a closed state.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method (with the exception that
     * invoking {@link #listen(Predicate)} while another invocation is active on
     * the same instance will result in an exception).
     */
    @Override
    public void close()
    {
        // TODO: implement

        this.connections.forEach(ReliableSocketConnection::close);
    }

    private void receiver()
    {
        final var executor = Executors.newCachedThreadPool();

        try
        {
            final DatagramPacket packet = new DatagramPacket(
                new byte[Config.MAX_PACKET_SIZE],
                Config.MAX_PACKET_SIZE
            );

            while (true)
            {
                this.udpSocket.receive(packet);

                final var remoteEndpoint = new Endpoint(
                    packet.getAddress(),
                    packet.getPort()
                );

                final var packetDataCopy = Arrays.copyOfRange(
                    packet.getData(),
                    packet.getOffset(),
                    packet.getOffset() + packet.getLength()
                );

                executor.execute(
                    () -> this.processPacket(remoteEndpoint, packetDataCopy)
                );
            }
        }
        finally
        {
            // wait for ongoing packet processing to finish

            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            executor.shutdown();

            // TODO: implement
        }
    }

    private void processPacket(Endpoint remoteEndpoint, byte[] packetData)
    {
        try
        {
            final var packetInput = new DataInputStream(
                new ByteArrayInputStream(packetData)
            );

            // verify packet integrity

            final var checksum = Config.CHECKSUM.get();
            checksum.update(packetData, 4, packetData.length - 4);

            final int computedChecksum = (int)checksum.getValue();
            final int receivedChecksum = packetInput.readInt();

            if (computedChecksum != receivedChecksum)
                return; // drop corrupt packet

            // get packet type

            final var packetType = packetInput.readByte();

            // delegate further processing

            switch (packetType)
            {
                case Config.TYPE_ID_CONN:
                    processPacketConn(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_CONN_REJECT:
                    processPacketConnReject(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_CONN_ACCEPT:
                    processPacketConnAccept(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_CONN_ACCEPT_ACK:
                    processPacketConnAcceptAck(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_DATA:
                    processPacketData(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_DATA_ACK:
                    processPacketDataAck(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_DISC:
                    processPacketDisc(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_DISC_ACK:
                    processPacketDiscAck(remoteEndpoint, packetInput);
                    break;
            }
        }
        catch (Exception ignored)
        {
            // drop packet
        }
    }

    private void processPacketConn(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {

    }

    private void processPacketConnReject(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var localConnectionNumber = packetInput.readInt();

        final var attempt = this.outgoingConnectionAttempts.get(
            new ConnectionIdentifier(remoteEndpoint, localConnectionNumber)
        );

        attempt.rejected();
    }

    private void processPacketConnAccept(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var localConnectionNumber = packetInput.readInt();
        final var remoteConnectionNumber = packetInput.readInt();

        final var attempt = this.outgoingConnectionAttempts.get(
            new ConnectionIdentifier(remoteEndpoint, localConnectionNumber)
        );

        attempt.accepted(remoteConnectionNumber);
    }

    private void processPacketConnAcceptAck(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {

    }

    private void processPacketData(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {

    }

    private void processPacketDataAck(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {

    }

    private void processPacketDisc(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {

    }

    private void processPacketDiscAck(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {

    }

    private void sendPacketConn(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int clientConnectionSeqnum
    )
    {
        // TODO: implement
    }

    private void sendPacketConnReject(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int serverConnectionSeqnum
    )
    {
        // TODO: implement
    }

    private void sendPacketConnAccept(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int clientConnectionSeqnum,
        int serverConnectionSeqnum
    )
    {
        // TODO: implement
    }

    private void sendPacketConnAcceptAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int serverConnectionSeqnum
    )
    {
        // TODO: implement
    }

    private void sendPacketData(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum,
        int payloadSeqnum,
        byte[] payloadBuffer,
        int payloadLength
    )
    {
        // TODO: implement
    }

    private void sendPacketDataAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum,
        int payloadSeqnum
    )
    {
        // TODO: implement
    }

    private void sendPacketDisc(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum
    )
    {
        // TODO: implement
    }

    private void sendPacketDiscAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum
    )
    {
        // TODO: implement
    }
}

/* -------------------------------------------------------------------------- */
