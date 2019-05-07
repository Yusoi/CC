/* -------------------------------------------------------------------------- */

package fileshare.transport;

import fileshare.Util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/* -------------------------------------------------------------------------- */

/**
 * A UDP-backed socket from which reliable data transfer channels (termed
 * *connections*) between this and other similar sockets can be obtained.
 *
 * This class is thread-safe.
 */
public class ReliableSocket implements AutoCloseable
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
        OPEN,

        /**
         * TODO: document
         */
        CLOSED
    }

    private final AtomicReference< State > state;

    private final DatagramSocket udpSocket;
    private final Thread receiverThread;
    private final AtomicInteger nextLocalConnectionSeqnum;

    private final AtomicBoolean isListening;

    // connection seqnum is remote
    private final BlockingQueue< ConnectionIdentifier >
        incomingConnectionRequests;

    // key's connection seqnum is local
    private final Map< ConnectionIdentifier, OutgoingConnectionRequest >
        outgoingConnectionRequests;

    // key's connection seqnum is remote
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

        this.state = new AtomicReference<>(State.CREATED);

        this.udpSocket = new DatagramSocket(localPort);
        this.receiverThread = new Thread(this::receiver);
        this.nextLocalConnectionSeqnum = new AtomicInteger(0);

        this.isListening = new AtomicBoolean(false);

        this.incomingConnectionRequests = new LinkedBlockingQueue<>();
        this.outgoingConnectionRequests = new HashMap<>();
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
     * Returns the socket's state.
     *
     * @return the socket's state
     */
    public State getState()
    {
        return state.get();
    }

    /**
     * Opens the socket.
     *
     * @throws IllegalStateException if the socket's state is not {@link
     *         State#CREATED}
     */
    public void open()
    {
        if (!this.state.compareAndSet(State.CREATED, State.OPEN))
            throw new IllegalStateException();

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
        // check state

        switch (this.state.get())
        {
            case CREATED:
                throw new IllegalStateException();

            case OPEN:
                break;

            case CLOSED:
                return null;
        }

        // check if already listening and set listening flag

        if (isListening.getAndSet(true))
            throw new IllegalStateException();

        try
        {
            // create buffer for packets to be sent

            final var packetBuffer = new byte[Config.MAX_PACKET_SIZE];

            while (true)
            {
                // return if socket was closed

                if (this.state.get() == State.CLOSED)
                    return null;

                // wait for request to arrive

                final ConnectionIdentifier connectionId;

                try
                {
                    connectionId = this.incomingConnectionRequests.take();
                }
                catch (InterruptedException ignored)
                {
                    // interrupted, retry

                    continue;
                }

                // check if connection was already accepted

                if (this.openConnections.containsKey(connectionId))
                    continue; // previously accepted connection, skip

                // determine whether to accept or reject connection

                if (accept.test(connectionId.getRemoteEndpoint()))
                {
                    // accept connection, send CONN-ACCEPT

                    final var localConnectionId =
                        this.nextLocalConnectionSeqnum.getAndIncrement();

                    this.sendPacketConnAccept(
                        packetBuffer,
                        connectionId.getRemoteEndpoint(),
                        connectionId.getConnectionSeqnum(),
                        localConnectionId
                    );

                    // register connection

                    final var connection = new ReliableSocketConnection(
                        this,
                        connectionId.getRemoteEndpoint(),
                        localConnectionId,
                        connectionId.getConnectionSeqnum()
                    );

                    this.openConnections.put(connectionId, connection);

                    // return connection

                    return connection;
                }
                else
                {
                    // reject connection, send CONN-REJECT

                    this.sendPacketConnReject(
                        packetBuffer,
                        connectionId.getRemoteEndpoint(),
                        connectionId.getConnectionSeqnum()
                    );
                }
            }
        }
        finally
        {
            // clear listening flag

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
        if (this.state != State.OPEN)
            throw new IllegalStateException();

        // allocate buffer for holding data of packets to be sent

        final var packetBuffer = new byte[Config.MAX_PACKET_SIZE];

        // generate local connection id

        final var localConnectionId =
            this.nextLocalConnectionSeqnum.getAndIncrement();

        // register connection request

        final var connectionRequest = new OutgoingConnectionRequest();

        synchronized (this)
        {
            this.outgoingConnectionRequests.put(
                new ConnectionIdentifier(
                    remoteEndpoint,
                    localConnectionId
                ),
                connectionRequest
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
                    remoteConnectionId = connectionRequest.waitForResponse(
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
                    // connection accepted

                    synchronized (this)
                    {
                        // unregister connection attempt

                        this.outgoingConnectionRequests.remove(
                            new ConnectionIdentifier(
                                remoteEndpoint,
                                localConnectionId
                            )
                        );

                        // register connection

                        final var connection = new ReliableSocketConnection(
                            this,
                            remoteEndpoint,
                            localConnectionId,
                            remoteConnectionId.getAsInt()
                        );

                        this.openConnections.put(
                            new ConnectionIdentifier(
                                remoteEndpoint,
                                remoteConnectionId.getAsInt()
                            ),
                            connection
                        );

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

                this.outgoingConnectionRequests.remove(connectionId);
            }

            throw t;
        }
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
    public void close()
    {
        if (this.state != State.CLOSED)
        {
            this.state = State.CLOSED;

            // abort any ongoing listen or connect calls

            // close all open connections

            this.openConnections.values().forEach(
                ReliableSocketConnection::close
            );

            // close UDP socket

            this.udpSocket.close();

            // wait for receiver thread to finish

            Util.uninterruptibleJoin(this.receiverThread);
        }
    }

    private void receiver()
    {
        final var executor = Executors.newCachedThreadPool();

        try
        {
            final var packet = new DatagramPacket(
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
        catch (Exception ignored)
        {
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

                case Config.TYPE_ID_CONN_ACCEPT:
                    processPacketConnAccept(remoteEndpoint, packetInput);
                    break;

                case Config.TYPE_ID_CONN_REJECT:
                    processPacketConnReject(remoteEndpoint, packetInput);
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
        final var remoteConnectionSeqnum = packetInput.readInt();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionSeqnum
        );

        if (this.openConnections.containsKey(connectionId))
            return; // CONN packet for already established connection, ignore

        // store connection request (if not already stored)

        this.incomingConnectionRequests.put(connectionId);
    }

    private void processPacketConnAccept(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var localConnectionSeqnum = packetInput.readInt();
        final var remoteConnectionSeqnum = packetInput.readInt();

        synchronized (this)
        {
            final var attempt = this.outgoingConnectionRequests.get(
                new ConnectionIdentifier(remoteEndpoint, localConnectionSeqnum)
            );

            attempt.accepted(remoteConnectionSeqnum);
        }
    }

    private void processPacketConnReject(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var localConnectionSeqnum = packetInput.readInt();

        synchronized (this)
        {
            final var attempt = this.outgoingConnectionRequests.get(
                new ConnectionIdentifier(remoteEndpoint, localConnectionSeqnum)
            );

            attempt.rejected();
        }
    }

    private void processPacketData(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var remoteConnectionSeqnum = packetInput.readInt();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionSeqnum
        );

        this.openConnections.get(connectionId).processPacketData(packetInput);
    }

    private void processPacketDataAck(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var remoteConnectionSeqnum = packetInput.readInt();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionSeqnum
        );

        this.openConnections.get(connectionId).processPacketDataAck(packetInput);
    }

    private void processPacketDisc(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var remoteConnectionSeqnum = packetInput.readInt();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionSeqnum
        );

        this.openConnections.get(connectionId).processPacketDisc(packetInput);
    }

    private void processPacketDiscAck(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var remoteConnectionSeqnum = packetInput.readInt();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionSeqnum
        );

        this.openConnections.get(connectionId).processPacketDiscAck(packetInput);
    }

    private ByteBuffer createByteBufferForSending(byte[] packetBuffer)
    {
        return ByteBuffer.wrap(packetBuffer, 4, packetBuffer.length - 4);
    }

    private void sendPacket(
        ByteBuffer byteBuffer,
        Endpoint remoteEndpoint
    ) throws IOException
    {
        // compute checksum

        final var checksum = Config.CHECKSUM.get();
        checksum.update(byteBuffer.array(), 4, byteBuffer.position() - 4);

        // insert checksum into buffer

        byteBuffer.putInt(0, (int)checksum.getValue());

        // create packet

        final var packet = new DatagramPacket(
            byteBuffer.array(),
            byteBuffer.position(),
            remoteEndpoint.getAddress(),
            remoteEndpoint.getPort()
        );

        // send packet

        this.udpSocket.send(packet);
    }

    private void sendPacketConn(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int localConnectionSeqnum
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_CONN);
        b.putInt(localConnectionSeqnum);

        this.sendPacket(b, remoteEndpoint);
    }

    private void sendPacketConnAccept(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum,
        int localConnectionSeqnum
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_CONN_ACCEPT);
        b.putInt(remoteConnectionSeqnum);
        b.putInt(localConnectionSeqnum);

        this.sendPacket(b, remoteEndpoint);
    }

    private void sendPacketConnReject(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_CONN_REJECT);
        b.putInt(remoteConnectionSeqnum);

        this.sendPacket(b, remoteEndpoint);
    }

    void sendPacketData(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int localConnectionSeqnum,
        int payloadSeqnum,
        byte[] payloadBuffer,
        int payloadLength
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DATA);
        b.putInt(localConnectionSeqnum);
        b.putInt(payloadSeqnum);
        b.put(payloadBuffer, 0, payloadLength);

        this.sendPacket(b, remoteEndpoint);
    }

    void sendPacketDataAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int localConnectionSeqnum,
        int payloadSeqnum
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DATA_ACK);
        b.putInt(localConnectionSeqnum);
        b.putInt(payloadSeqnum);

        this.sendPacket(b, remoteEndpoint);
    }

    void sendPacketDisc(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int localConnectionSeqnum
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DISC);
        b.putInt(localConnectionSeqnum);

        this.sendPacket(b, remoteEndpoint);
    }

    void sendPacketDiscAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int localConnectionSeqnum
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DISC_ACK);
        b.putInt(localConnectionSeqnum);

        this.sendPacket(b, remoteEndpoint);
    }
}

/* -------------------------------------------------------------------------- */
