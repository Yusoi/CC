/* -------------------------------------------------------------------------- */

package fileshare.transport;

import fileshare.Util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/* -------------------------------------------------------------------------- */

/**
 * A UDP-backed socket from which reliable data transfer channels (termed
 * <i>connections</i>) between this and other similar sockets can be obtained.
 *
 * This class is thread-safe.
 */
public class ReliableSocket implements AutoCloseable
{
    /**
     * Defines the possible states of a {@code ReliableSocket}.
     */
    public enum State
    {
        /**
         * The socket was created but not opened.
         */
        CREATED,

        /**
         * The socket is open.
         */
        OPEN,

        /**
         * The socket is closed.
         */
        CLOSED
    }

    private final AtomicReference< State > state;

    private final DatagramSocket udpSocket;
    private final Thread receiverThread;
    private final AtomicInteger nextLocalConnectionId;

    private final Object listenCallThreadMonitor;
    private Thread listenCallThread;

    private final Object connectCallThreadCountMonitor;
    private int connectCallThreadCount;

    // connection seqnum is remote
    private final BlockingQueue< ConnectionIdentifier >
        incomingConnectionRequests;

    // key's connection seqnum is local
    private final Map< ConnectionIdentifier, OutgoingConnectionRequest >
        outgoingConnectionRequests;

    // key's connection seqnum is remote
    final Map< ConnectionIdentifier, ReliableSocketConnection >
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
        this.nextLocalConnectionId = new AtomicInteger(new Random().nextInt());

        this.listenCallThreadMonitor = new Object();
        this.listenCallThread = null;

        this.connectCallThreadCountMonitor = new Object();
        this.connectCallThreadCount = 0;

        this.incomingConnectionRequests = new LinkedBlockingQueue<>();
        this.outgoingConnectionRequests = new HashMap<>();
        this.openConnections = new HashMap<>();
    }

    /**
     * Returns the socket's local UDP port.
     *
     * This method always succeeds, even if this socket is closed.
     *
     * @return the socket's local UDP port
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
     * Listens for incoming connection requests.
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
     * This method will throw an {@link IllegalStateException} if invoked
     * concurrently with other invocations of itself on the same socket.
     *
     * @param accept predicate that determines whether a connection should be
     *        accepted
     * @return the established connection, or {@code null} if this socket
     *         closed
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
        synchronized (this.listenCallThreadMonitor)
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

            // check that no invocation of listen() is ongoing

            if (this.listenCallThread != null)
                throw new IllegalStateException();

            // store current thread (to later be interrupted if necessary)

            this.listenCallThread = Thread.currentThread();
        }

        try
        {
            // create buffer for packets to be sent

            final var packetBuffer = new byte[Config.MAX_PACKET_SIZE];

            while (true)
            {
                // return null if socket is now closed

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

                synchronized (this)
                {
                    if (this.openConnections.containsKey(connectionId))
                        continue; // previously accepted connection, skip
                }

                // determine whether to accept or reject connection

                if (accept.test(connectionId.getRemoteEndpoint()))
                {
                    // accept connection, send CONN-ACCEPT

                    final var localConnectionId =
                        (short) this.nextLocalConnectionId.getAndIncrement();

                    this.sendPacketConnAccept(
                        packetBuffer,
                        connectionId.getRemoteEndpoint(),
                        connectionId.getConnectionId(),
                        localConnectionId
                    );

                    // register connection

                    final var connection = new ReliableSocketConnection(
                        this,
                        connectionId.getRemoteEndpoint(),
                        localConnectionId,
                        connectionId.getConnectionId()
                    );

                    synchronized (this)
                    {
                        this.openConnections.put(connectionId, connection);
                    }

                    // return connection

                    return connection;
                }
                else
                {
                    // reject connection, send CONN-REJECT

                    this.sendPacketConnReject(
                        packetBuffer,
                        connectionId.getRemoteEndpoint(),
                        connectionId.getConnectionId()
                    );
                }
            }
        }
        finally
        {
            // clear listening flag

            synchronized (this.listenCallThreadMonitor)
            {
                this.listenCallThread = null;
                this.listenCallThreadMonitor.notifyAll();
            }
        }
    }

    /**
     * Attempts to establish a connection with the specified remote endpoint.
     *
     * An {@link IOException} is thrown if the remote explicitly declines the
     * connection attempt.
     *
     * If this socket is closed, invoking this method will result in {@link
     * IllegalStateException} being thrown.
     *
     * Any active calls of this method will throw an {@link
     * InterruptedException} when {@link #close()} is invoked on this socket.
     *
     * @param remoteEndpoint the remote's endpoint
     * @return the established connection
     *
     * @throws NullPointerException if {@code remoteEndpoint} is {@code null}
     * @throws IOException if the connection is rejected by the remote
     * @throws IOException if an I/O error occurs
     */
    public ReliableSocketConnection connect(
        Endpoint remoteEndpoint
        ) throws InterruptedException, IOException
    {
        synchronized (this.connectCallThreadCountMonitor)
        {
            // validate state

            if (this.state.get() != State.OPEN)
                throw new IllegalStateException();

            // increment active invocation count

            this.connectCallThreadCount += 1;
        }

        try
        {
            // allocate buffer for holding data of packets to be sent

            final var packetBuffer = new byte[Config.MAX_PACKET_SIZE];

            // generate local connection id

            final var localConnectionId =
                (short) this.nextLocalConnectionId.getAndIncrement();

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
                for (int i = 0; i < Config.MAX_CONNECT_ATTEMPTS; ++i)
                {
                    // send connection request

                    this.sendPacketConn(
                        packetBuffer, remoteEndpoint, localConnectionId
                    );

                    // wait for response

                    final Optional< Short > remoteConnectionId;

                    try
                    {
                        remoteConnectionId = connectionRequest.waitForResponse(
                            Config.CONNECT_RESPONSE_TIMEOUT
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
                                remoteConnectionId.get()
                            );

                            this.openConnections.put(
                                new ConnectionIdentifier(
                                    remoteEndpoint,
                                    remoteConnectionId.get()
                                ),
                                connection
                            );

                            // return connection

                            return connection;
                        }
                    }
                }

                // reached maximum number of retries and received no response

                throw new IOException(
                    "The peer did not respond to the connection request."
                );
            }
            catch (Throwable t)
            {
                synchronized (this)
                {
                    // unregister connection attempt

                    this.outgoingConnectionRequests.remove(
                        new ConnectionIdentifier(
                            remoteEndpoint,
                            localConnectionId
                        )
                    );
                }

                // rethrow

                throw t;
            }
        }
        finally
        {
            // decrement active invocation count

            synchronized (this.connectCallThreadCountMonitor)
            {
                this.connectCallThreadCount -= 1;

                if (this.connectCallThreadCount == 0)
                    this.connectCallThreadCountMonitor.notifyAll();
            }
        }
    }

    /**
     * Closes this socket and any open connections previously obtained from it
     * (as if by invoking {@link ReliableSocketConnection#close()} on each of
     * them).
     *
     * If this socket is already closed, this method has no effect.
     */
    @Override
    public void close()
    {
        final var previousState = this.state.getAndSet(State.CLOSED);

        switch (previousState)
        {
            case CREATED:

                // close UDP socket

                this.udpSocket.close();

                break;

            case OPEN:

                // abort ongoing listen() invocation

                synchronized (this.listenCallThreadMonitor)
                {
                    if (this.listenCallThread != null)
                        this.listenCallThread.interrupt();
                }

                // abort all ongoing connect() invocations

                synchronized (this.outgoingConnectionRequests)
                {
                    for (final var r : this.outgoingConnectionRequests.values())
                        r.interrupt();
                }

                // wait for ongoing listen() invocation to finish

                synchronized (this.listenCallThreadMonitor)
                {
                    while (this.listenCallThread != null)
                    {
                        try
                        {
                            this.listenCallThreadMonitor.wait();
                        }
                        catch (InterruptedException ignored)
                        {
                        }
                    }
                }

                // wait for all ongoing connect() invocations to finish

                synchronized (this.connectCallThreadCountMonitor)
                {
                    while (this.connectCallThreadCount > 0)
                    {
                        try
                        {
                            this.connectCallThreadCountMonitor.wait();
                        }
                        catch (InterruptedException ignored)
                        {
                        }
                    }
                }

                // close all open connections

                final List< ReliableSocketConnection > openConnectionsCopy;

                synchronized (this)
                {
                    openConnectionsCopy = new ArrayList<>(
                        this.openConnections.values()
                    );
                }

                openConnectionsCopy.forEach(ReliableSocketConnection::close);

                // close UDP socket

                this.udpSocket.close();

                // wait for receiver thread to finish

                Util.uninterruptibleJoin(this.receiverThread);

                break;

            case CLOSED:
                break;
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

            executor.shutdown();

            while (true)
            {
                try
                {
                    executor.awaitTermination(
                        Long.MAX_VALUE,
                        TimeUnit.NANOSECONDS
                    );

                    break;
                }
                catch (InterruptedException ignored)
                {
                }
            }
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
                    processPacketData(
                        remoteEndpoint, packetInput, packetData.length - 5
                    );
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
        final var remoteConnectionId = packetInput.readShort();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionId
        );

        if (this.openConnections.containsKey(connectionId))
            return; // CONN packet for already established connection, ignore

        // store connection request (if not already stored)

        try
        {
            this.incomingConnectionRequests.put(connectionId);
        }
        catch (InterruptedException ignored)
        {
        }
    }

    private void processPacketConnAccept(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var localConnectionId = packetInput.readShort();
        final var remoteConnectionId = packetInput.readShort();

        synchronized (this)
        {
            final var attempt = this.outgoingConnectionRequests.get(
                new ConnectionIdentifier(remoteEndpoint, localConnectionId)
            );

            attempt.accepted(remoteConnectionId);
        }
    }

    private void processPacketConnReject(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var localConnectionId = packetInput.readShort();

        synchronized (this)
        {
            final var attempt = this.outgoingConnectionRequests.get(
                new ConnectionIdentifier(remoteEndpoint, localConnectionId)
            );

            attempt.rejected();
        }
    }

    private void processPacketData(
        Endpoint remoteEndpoint,
        DataInputStream packetInput,
        int remainingBytes
    ) throws IOException
    {
        final var remoteConnectionId = packetInput.readShort();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionId
        );

        this.openConnections.get(connectionId).processPacketData(
            packetInput, remainingBytes - 2
        );
    }

    private void processPacketDataAck(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var remoteConnectionId = packetInput.readShort();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionId
        );

        this.openConnections.get(connectionId).processPacketDataAck(packetInput);
    }

    private void processPacketDisc(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var remoteConnectionId = packetInput.readShort();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionId
        );

        this.openConnections.get(connectionId).processPacketDisc();
    }

    private void processPacketDiscAck(
        Endpoint remoteEndpoint,
        DataInputStream packetInput
    ) throws IOException
    {
        final var remoteConnectionId = packetInput.readShort();

        final var connectionId = new ConnectionIdentifier(
            remoteEndpoint,
            remoteConnectionId
        );

        this.openConnections.get(connectionId).processPacketDiscAck();
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
        short localConnectionId
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_CONN);
        b.putShort(localConnectionId);

        this.sendPacket(b, remoteEndpoint);
    }

    private void sendPacketConnAccept(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        short remoteConnectionId,
        short localConnectionId
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_CONN_ACCEPT);
        b.putShort(remoteConnectionId);
        b.putShort(localConnectionId);

        this.sendPacket(b, remoteEndpoint);
    }

    private void sendPacketConnReject(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        short remoteConnectionId
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_CONN_REJECT);
        b.putShort(remoteConnectionId);

        this.sendPacket(b, remoteEndpoint);
    }

    // payloadBuffer may be circular
    void sendPacketData(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        short localConnectionId,
        long payloadPosition,
        byte[] payloadBuffer,
        int payloadBufferOffset,
        int payloadBufferLength
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DATA);
        b.putShort(localConnectionId);
        b.putLong(payloadPosition);

        final var firstLength = Math.min(
            payloadBufferLength,
            payloadBuffer.length - payloadBufferOffset
        );

        final var secondLength = payloadBufferLength - firstLength;

        b.put(payloadBuffer, payloadBufferOffset, firstLength);

        if (secondLength > 0)
            b.put(payloadBuffer, 0, secondLength);

        this.sendPacket(b, remoteEndpoint);
    }

    void sendPacketDataAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        short localConnectionId,
        long ackUpTo
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DATA_ACK);
        b.putShort(localConnectionId);
        b.putLong(ackUpTo);

        this.sendPacket(b, remoteEndpoint);
    }

    void sendPacketDisc(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        short localConnectionId
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DISC);
        b.putShort(localConnectionId);

        this.sendPacket(b, remoteEndpoint);
    }

    void sendPacketDiscAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        short localConnectionId
    ) throws IOException
    {
        final var b = this.createByteBufferForSending(packetBuffer);

        b.put(Config.TYPE_ID_DISC_ACK);
        b.putShort(localConnectionId);

        this.sendPacket(b, remoteEndpoint);
    }
}

/* -------------------------------------------------------------------------- */
