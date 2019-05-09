/* -------------------------------------------------------------------------- */

package fileshare.transport;

import fileshare.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * A connection (reliable data transfer channel) between two instances of {@link
 * ReliableSocket}.
 *
 * This class is thread-safe.
 */
public class ReliableSocketConnection implements AutoCloseable
{
    private final ReliableSocket reliableSocket;

    private final Endpoint remoteEndpoint;
    private final short localConnectionId;
    private final short remoteConnectionId;

    private final Input input;
    private final Output output;

    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;

    private boolean disconnected;
    private boolean closed;

    ReliableSocketConnection(
        ReliableSocket reliableSocket,
        Endpoint remoteEndpoint,
        short localConnectionId,
        short remoteConnectionId
    )
    {
        this.reliableSocket = reliableSocket;

        this.remoteEndpoint = remoteEndpoint;
        this.localConnectionId = localConnectionId;
        this.remoteConnectionId = remoteConnectionId;

        this.input = this.new Input();
        this.output = this.new Output();

        this.dataInputStream = new DataInputStream(this.input);
        this.dataOutputStream = new DataOutputStream(this.output);

        this.disconnected = false;
        this.closed = false;
    }

    /**
     * Returns the {@link ReliableSocket} from which this connection was
     * created.
     *
     * This method always succeeds, even if this connection is closed.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the {@link ReliableSocket} from which this connection was created
     */
    public ReliableSocket getSocket()
    {
        return this.reliableSocket;
    }

    /**
     * Returns the endpoint of the host on the other side of this connection.
     *
     * This method always succeeds, even if this connection is closed.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the endpoint of the host on the other side of this connection
     */
    public Endpoint getRemoteEndpoint()
    {
        return this.remoteEndpoint;
    }

    /**
     * Returns the unique dataInputStream stream for this side of this connection.
     *
     * This method always succeeds, even if this connection is closed.
     *
     * If this side of this connection is open but the other side has been
     * closed, the returned stream is still readable (no data sent by the remote
     * is lost) and will indicate end-of-file when all data sent by the remote
     * has been read.
     *
     * If this side of this connection has been closed, reading from the
     * returned stream will result in {@link IllegalStateException} being
     * thrown.
     *
     * Any active calls on the returned stream will throw an exception when
     * {@link #close()} is invoked on this instance.
     *
     * The returned stream's {@link InputStream#close()} method has no effect.
     *
     * Note that the returned stream is NOT thread-safe.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the dataInputStream stream for this side of this connection
     */
    public DataInputStream getDataInputStream()
    {
        return this.dataInputStream;
    }

    /**
     * Returns the unique dataOutputStream stream for this side of this connection.
     *
     * The returned stream's data is buffered (to a certain unspecified size).
     * In order to force-send buffered data, use the returned stream's
     * {@link OutputStream#flush()} method.
     *
     * The returned stream's {@link OutputStream#flush()} method blocks until
     * data is acknowledged by the receiver.
     *
     * This method always succeeds, even if this connection is closed.
     *
     * If either side of this connection has been closed, writing to the
     * returned stream will result in {@link IllegalStateException} being
     * thrown.
     *
     * Any active calls on the returned stream will throw an exception when
     * {@link #close()} is invoked on this instance.
     *
     * The returned stream's {@link OutputStream#close()} method has no effect.
     *
     * Note that the returned stream is NOT thread-safe.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the dataOutputStream stream for this side of this connection
     */
    public DataOutputStream getDataOutputStream()
    {
        return this.dataOutputStream;
    }

    /**
     * Checks whether the connection is no longer established, either because
     * this side of the connection was closed, or because the remote side of
     * this connection was closed.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return whether this side of the connection has been closed
     */
    public synchronized boolean isDisconnected()
    {
        return this.disconnected;
    }

    /**
     * Checks whether this side of the connection has been closed.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return whether this side of the connection has been closed
     */
    public synchronized boolean isClosed()
    {
        return this.closed;
    }

    /**
     * Closes this end/side of the connection.
     *
     * Any unread data in the dataInputStream stream is lost.
     *
     * Any unsent data in the dataOutputStream stream is lost. Use {@code
     * getDataOutputStream().flush()} before invoking this method to ensure that all
     * unsent data is sent.
     *
     * Any active calls on this side's dataInputStream or dataOutputStream streams will throw an
     * {@link InterruptedException} when this method is called.
     *
     * Reading from {@link #getDataInputStream()} and writing to {@link #getDataOutputStream()}
     * after invoking this method will throw {@link IllegalStateException}.
     *
     * See {@link #getDataInputStream()} and {@link #getDataOutputStream()} for more information on
     * the effects of this method on this side's streams.
     *
     * If this end of the connection is already closed, this method has no
     * effect.
     *
     * If this method fails, this end of the connection will nevertheless be
     * left in a closed state.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     */
    @Override
    public synchronized void close()
    {
        if (!this.disconnected)
        {
            // try to inform remote of close

            final byte[] packetBuffer = new byte[Config.MAX_PACKET_SIZE];

            for (int i = 0; i < Config.MAX_DISCONNECT_ATTEMPTS; ++i)
            {
                // send DISC packet

                try
                {
                    this.reliableSocket.sendPacketDisc(
                        packetBuffer,
                        this.remoteEndpoint,
                        this.localConnectionId
                    );
                }
                catch (IOException ignored)
                {
                    // error sending DISC packet, give up trying to inform remote
                    break;
                }

                // TODO: wait for response or timeout

                // TODO: if responded, return

                // TODO: if didn't respond, try again
            }

            // set disconnected flag

            this.disconnected = true;
        }

        // remove connection from parent socket

        synchronized (this.reliableSocket.openConnections)
        {
            this.reliableSocket.openConnections.remove(
                new ConnectionIdentifier(
                    this.remoteEndpoint,
                    this.remoteConnectionId
                )
            );
        }

        // set closed flag

        this.closed = true;

        // notify waiters

        this.notifyAll();
    }

    void processPacketData(
        DataInputStream packetInput,
        int remainingBytes
    ) throws IOException
    {
        final var dataOffset = packetInput.readLong();

        System.err.println(
            String.format(
                "Received DATA: offset = %d, length = %d",
                dataOffset, remainingBytes - 8
            )
        );

        this.input.onDataReceived(dataOffset, packetInput, remainingBytes - 8);
    }

    void processPacketDataAck(DataInputStream packetInput) throws IOException
    {
        final var ackUpTo = packetInput.readLong();

        System.err.println(
            String.format(
                "Received DATA-ACK: ack-up-to = %d",
                ackUpTo
            )
        );

        this.output.onAcknowledgmentReceived(ackUpTo);
    }

    synchronized void processPacketDisc() throws IOException
    {
        // this.disconnected = true;
    }

    void processPacketDiscAck() throws IOException
    {
        // TODO: implement
    }

    private class Input extends InputStream
    {
        // the buffer is circular
        private final byte[] receiveBuffer = new byte[
            Config.MAX_DATA_PACKET_PAYLOAD_SIZE
            ];

        private int receiveBufferStart = 0;
        private int receiveBufferLen = 0;

        private long nextByteToBeRead = 0;

        private void onDataReceived(
            long dataOffset,
            DataInputStream dataInput,
            int dataSize
        )
        {
            synchronized (ReliableSocketConnection.this)
            {
                try
                {
                    // ignore data if disconnected

                    if (ReliableSocketConnection.this.isDisconnected())
                        return;

                    // ignore data if empty

                    if (dataSize == 0)
                        return;

                    // ignore data if offset is higher than expected (hole)

                    if (dataOffset > this.nextByteToBeRead)
                        return;

                    // simply send acknowledgment if data was already received

                    if (dataOffset < this.nextByteToBeRead)
                    {
                        ReliableSocketConnection.this.reliableSocket.sendPacketDataAck(
                            new byte[Config.MAX_PACKET_SIZE],
                            ReliableSocketConnection.this.remoteEndpoint,
                            ReliableSocketConnection.this.localConnectionId,
                            this.nextByteToBeRead
                        );

                        return;
                    }

                    // ignore data if not enough space in receive buffer

                    if (this.receiveBuffer.length - this.receiveBufferLen < dataSize)
                        return;

                    // copy data to receive buffer

                    final var firstSize = Math.min(
                        dataSize,
                        this.receiveBuffer.length - this.receiveBufferStart
                    );

                    final var secondSize = dataSize - firstSize;

                    if (firstSize > 0)
                    {
                        dataInput.readFully(
                            this.receiveBuffer,
                            this.receiveBufferStart,
                            firstSize
                        );
                    }

                    if (secondSize > 0)
                    {
                        dataInput.readFully(
                            this.receiveBuffer,
                            0,
                            secondSize
                        );
                    }

                    this.receiveBufferLen += dataSize;
                    this.nextByteToBeRead += dataSize;

                    // send acknowledgment

                    ReliableSocketConnection.this.reliableSocket.sendPacketDataAck(
                        new byte[Config.MAX_PACKET_SIZE],
                        ReliableSocketConnection.this.remoteEndpoint,
                        ReliableSocketConnection.this.localConnectionId,
                        this.nextByteToBeRead
                    );

                    // notify waiters if buffer was previously empty

                    if (this.receiveBufferLen == dataSize)
                        ReliableSocketConnection.this.notifyAll();
                }
                catch (IOException ignored)
                {
                    // I/O error, close connection

                    ReliableSocketConnection.this.close();
                }
            }
        }

        @Override
        public int read() throws IOException
        {
            final byte[] b = new byte[1];

            if (this.read(b, 0, 1) == -1)
                return -1;
            else
                return Byte.toUnsignedInt(b[0]);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            synchronized (ReliableSocketConnection.this)
            {
                // validate arguments

                Objects.checkFromIndexSize(off, len, b.length);

                // validate state

                if (ReliableSocketConnection.this.isClosed())
                    throw new IllegalStateException("closed");

                // read data

                final var end = off + len;

                int readBytes = 0;

                while (off < end)
                {
                    // wait until data is ready to be read or EOF was reached

                    Util.waitUntil(ReliableSocketConnection.this, () ->
                        this.receiveBufferLen > 0 ||
                        ReliableSocketConnection.this.isDisconnected()
                    );

                    // fail if this side of the connection is closed

                    if (ReliableSocketConnection.this.isClosed())
                        throw new IllegalStateException("closed");

                    // break if EOF was reached

                    if (this.receiveBufferLen == 0)
                        break;

                    // compute number of bytes to be copied

                    final var bytesToBeCopied = Math.min(
                        this.receiveBufferLen,
                        end - off
                    );

                    // copy data to user receiveBuffer

                    Util.circularCopy(
                        this.receiveBuffer,
                        this.receiveBufferStart,
                        b,
                        off,
                        bytesToBeCopied
                    );

                    // update offset into user buffer

                    off += bytesToBeCopied;

                    // remove data from receive buffer

                    this.receiveBufferStart =
                        (this.receiveBufferStart + bytesToBeCopied)
                            % this.receiveBuffer.length;

                    this.receiveBufferLen -= bytesToBeCopied;

                    // update number of read bytes

                    readBytes += bytesToBeCopied;
                }

                return (readBytes > 0) ? readBytes : -1;
            }
        }
    }

    private class Output extends OutputStream
    {
        // to avoid continuously recreating the buffer for outgoing packets
        private final byte[] outgoingPacketBuffer = new byte[
            Config.MAX_PACKET_SIZE
            ];

        private final Config.RttEstimator rttEstimator =
            Config.RTT_ESTIMATOR.get();

        // unsent

        private final byte[] unsentBuffer = new byte[
            Config.MAX_DATA_PACKET_PAYLOAD_SIZE
            ];

        private int unsentBytes = 0;

        private final byte[] unackedBuffer = new byte[
            Config.MAX_UNACKNOWLEDGED_DATA
            ];

        // unacked

        private int unackedBufferStart = 0;
        private int unackedBufferLen = 0;

        // acked

        private long ackedBytes = 0;

        // ack timeout

        private final Timeout ackTimeout = new Timeout();

        private int ackTimeoutCounter = 0;

        private void sendUnsentData() throws IOException
        {
            synchronized (ReliableSocketConnection.this)
            {
                // TODO: close connection on error

                if (this.unsentBytes > 0)
                {
                    // wait until there is enough space in unacked buffer

                    Util.waitUntil(ReliableSocketConnection.this, () ->
                        this.unackedBuffer.length - this.unackedBufferLen
                            >= this.unsentBytes
                                   || ReliableSocketConnection.this.isDisconnected()
                    );

                    // fail if the connection disconnected

                    if (ReliableSocketConnection.this.isDisconnected())
                        throw new IllegalStateException("disconnected");

                    // send packet

                    ReliableSocketConnection.this.reliableSocket.sendPacketData(
                        outgoingPacketBuffer,
                        ReliableSocketConnection.this.remoteEndpoint,
                        ReliableSocketConnection.this.localConnectionId,
                        ackedBytes,
                        unsentBuffer,
                        0,
                        unsentBytes
                    );

                    // copy data to unacknowledged data buffer

                    Util.circularCopy(
                        unsentBuffer,
                        0,
                        unackedBuffer,
                        (unackedBufferStart + unackedBufferLen) % unackedBuffer.length,
                        unsentBytes
                    );

                    unackedBufferLen += unsentBytes;

                    unsentBytes = 0;

                    // start acknowledgement timeout timer if not already running

                    ackTimeout.scheduleIfNotScheduled(
                        this::onAcknowledgmentTimeout,
                        this.rttEstimator.computeTimeoutNanos()
                    );
                }
            }
        }

        private void onAcknowledgmentTimeout()
        {
            synchronized (ReliableSocketConnection.this)
            {
                // if there is still unacknowledged data

                if (this.unackedBufferLen > 0)
                {
                    if (this.ackTimeoutCounter >= Config.MAX_RETRANSMISSIONS)
                    {
                        ReliableSocketConnection.this.close();
                        return;
                    }

                    // resend unacknowledged data

                    for (int off = 0; off < this.unackedBufferLen; )
                    {
                        final var bytesToSend = Math.min(
                            this.unackedBufferLen - off,
                            Config.MAX_DATA_PACKET_PAYLOAD_SIZE
                        );

                        try
                        {
                            ReliableSocketConnection.this.reliableSocket.sendPacketData(
                                this.outgoingPacketBuffer,
                                ReliableSocketConnection.this.remoteEndpoint,
                                ReliableSocketConnection.this.localConnectionId,
                                this.ackedBytes + off,
                                this.unackedBuffer,
                                this.unackedBufferStart + off,
                                bytesToSend
                            );
                        }
                        catch (IOException ignored)
                        {
                        }

                        off += bytesToSend;
                    }

                    // increment ack timout counter

                    this.ackTimeoutCounter += 1;

                    // reset acknowledgement timeout timer

                    this.ackTimeout.scheduleReplace(
                        this::onAcknowledgmentTimeout,
                        this.rttEstimator.computeTimeoutNanos()
                    );
                }
            }
        }

        private void onAcknowledgmentReceived(long ackUpTo)
            throws IOException
        {
            synchronized (ReliableSocketConnection.this)
            {
                // ignore if data is already acknowledged

                if (ackUpTo <= this.ackedBytes)
                    return;

                // update acked and unacked byte counters

                final var ackedDelta = ackUpTo - this.ackedBytes;

                if (ackedDelta > this.unackedBufferLen)
                {
                    // received acknowledgement for unsent data, close connection

                    ReliableSocketConnection.this.close();

                    throw new IOException(
                        "received acknowledgement for unsent data"
                    );
                }

                this.ackedBytes += ackedDelta;
                this.unackedBufferStart += ackedDelta;
                this.unackedBufferLen -= ackedDelta;

                // reset ack timeout counter

                this.ackTimeoutCounter = 0;

                // notify waiters

                ReliableSocketConnection.this.notifyAll();

                // schedule acknowledgement timeout if data is still unacknowledged

                if (this.unackedBufferLen > 0)
                {
                    this.ackTimeout.scheduleReplace(
                        this::onAcknowledgmentTimeout,
                        this.rttEstimator.computeTimeoutNanos()
                    );
                }
                else
                {
                    this.ackTimeout.cancelIfScheduled();
                }
            }
        }

        @Override
        public void write(int b) throws IOException
        {
            this.write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len)
            throws IOException
        {
            synchronized (ReliableSocketConnection.this)
            {
                // validate arguments

                Objects.checkFromIndexSize(off, len, b.length);

                // validate state

                if (ReliableSocketConnection.this.isDisconnected())
                    throw new IllegalStateException("disconnected");

                // while not all data in b has been written

                final var end = off + len;

                while (off < end)
                {
                    // compute number of bytes to be written
                    // = min(bytes left in b, space left in unsent receiveBuffer)

                    final var lenToWrite = Math.min(
                        end - off,
                        this.unsentBuffer.length - this.unsentBytes
                    );

                    // write bytes to unsent receiveBuffer

                    System.arraycopy(
                        b, off, this.unsentBuffer, this.unsentBytes, lenToWrite
                    );

                    // update number of unsent bytes and offset

                    this.unsentBytes += lenToWrite;
                    off += lenToWrite;

                    // send unsent data if unsent receiveBuffer is full

                    if (this.unsentBytes == this.unsentBuffer.length)
                        this.sendUnsentData();
                }
            }
        }

        @Override
        public void flush() throws IOException
        {
            synchronized (ReliableSocketConnection.this)
            {
                // validate state

                if (ReliableSocketConnection.this.isDisconnected())
                    throw new IllegalStateException("disconnected");

                // send unsent data

                this.sendUnsentData();

                // wait until there is no more unacknowledged data

                Util.waitUntil(
                    ReliableSocketConnection.this,
                    () -> this.unackedBufferLen == 0 ||
                              ReliableSocketConnection.this.isDisconnected()
                );

                // fail if the connection disconnected

                if (ReliableSocketConnection.this.isDisconnected())
                    throw new IllegalStateException("disconnected");
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
