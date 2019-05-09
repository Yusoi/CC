/* -------------------------------------------------------------------------- */

package fileshare.transport;

import fileshare.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private final Input input;
    private final Output output;

    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;

    private boolean closed;
    private boolean closedByThisSide;

    ReliableSocketConnection(
        ReliableSocket reliableSocket,
        Endpoint remoteEndpoint,
        short localConnectionId
    )
    {
        this.reliableSocket = reliableSocket;

        this.remoteEndpoint = remoteEndpoint;
        this.localConnectionId = localConnectionId;

        this.input = this.new Input();
        this.output = this.new Output();

        this.dataInputStream = new DataInputStream(this.input);
        this.dataOutputStream = new DataOutputStream(this.output);

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
        // TODO: implement

        this.closed = true;
        this.closedByThisSide = true;
    }

    void processPacketData(
        DataInputStream packetInput,
        int remainingBytes
    ) throws IOException
    {
        final var dataOffset = packetInput.readLong();

    }

    void processPacketDataAck(DataInputStream packetInput) throws IOException
    {
        final var ackUpTo = packetInput.readLong();

        this.output.onAcknowledgmentReceived(ackUpTo);
    }

    void processPacketDisc(DataInputStream packetInput) throws IOException
    {
        // TODO: implement
    }

    void processPacketDiscAck(DataInputStream packetInput) throws IOException
    {
        // TODO: implement
    }

    private class Input extends InputStream
    {
        private final byte[] receiveBuffer = new byte[
            Config.MAX_DATA_PACKET_PAYLOAD_SIZE
            ];

        private int receiveStart = 0;
        private int receiveBytes = 0;

        private long nextByteToBeRead = 0;

        private synchronized void onDataReceived(
            long dataOffset,
            DataInputStream payloadInput,
            int payloadSize
        )
        {
            // ignore data if unexpected offset

            if (dataOffset != this.nextByteToBeRead)
                return;

            // ignore data if not enough space in receive buffer

            if (this.receiveBuffer.length - this.receiveBytes < payloadSize)
                return;

            // copy data to receive buffer

            // TODO: implement

            this.receiveBytes += payloadSize;
            this.nextByteToBeRead += payloadSize;

            // if buffer was previously empty, notify waiters

            if (receiveBytes == payloadSize)
                this.notifyAll();
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
        public synchronized int read(byte[] b, int off, int len)
            throws IOException
        {
            // validate arguments

            Objects.checkFromIndexSize(off, len, b.length);

            // validate state

            // read data

            final var end = off + len;

            int readBytes = 0;

            while (off < end)
            {
                // wait until data is ready to be read or EOF was reached

                Util.waitUntil(this, () -> this.receiveBytes > 0);

                // break if EOF was reached

                if (ReliableSocketConnection.this.isClosed())
                    break;

                // compute number of bytes to be copied

                final var bytesToBeCopied = Math.min(
                    this.receiveBytes,
                    end - off
                );

                // copy data to user receiveBuffer

                Util.circularCopy(
                    this.receiveBuffer,
                    this.receiveStart,
                    b,
                    off,
                    bytesToBeCopied
                );

                // update offset into user buffer

                off += bytesToBeCopied;

                // remove data from receive buffer

                this.receiveStart =
                    (this.receiveStart + bytesToBeCopied)
                        % this.receiveBuffer.length;

                // update number of read bytes

                readBytes += bytesToBeCopied;
            }

            return (readBytes > 0) ? readBytes : -1;
        }
    }

    private class Output extends OutputStream
    {
        // to avoid continuously recreating the receiveBuffer for outgoing packets
        private final byte[] outgoingPacketBuffer = new byte[
            Config.MAX_PACKET_SIZE
            ];

        private final Config.RttEstimator rttEstimator =
            Config.RTT_ESTIMATOR.get();

        private final byte[] unsentBuffer = new byte[Config.MAX_DATA_PACKET_PAYLOAD_SIZE];
        private int unsentBytes = 0;

        private final byte[] unackedBuffer = new byte[Config.MAX_UNACKNOWLEDGED_DATA_BYTES];
        private int unackedBufferStart = 0;
        private int unackedBytes = 0;

        private long ackedBytes = 0;
        private long lastRetransmittedBytePlusOne = 0;

        private final ScheduledExecutorService ackTimeoutExecutor =
            Executors.newSingleThreadScheduledExecutor();

        private synchronized void sendUnsentData() throws IOException
        {
            // TODO: close connection on error

            if (unsentBytes > 0)
            {
                // wait until there is enough space in unacked receiveBuffer

                Util.waitUntil(
                    this,
                    () -> unackedBuffer.length - unackedBytes >= unsentBytes
                );

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

                // copy data to unacknowledged data receiveBuffer

                Util.copyCircular(
                    unsentBuffer,
                    0,
                    unackedBuffer,
                    (unackedBufferStart + unackedBytes) % unackedBuffer.length,
                    unsentBytes
                );

                unackedBytes += unsentBytes;

                unsentBytes = 0;

                // start acknowledgement timeout timer if not already running

                ackTimeoutExecutor.schedule(
                    this::onAcknowledgmentTimeout,
                    this.rttEstimator.computeTimeoutNanos(),
                    TimeUnit.NANOSECONDS
                );
            }
        }

        private synchronized void onAcknowledgmentTimeout()
        {
            if (this.unackedBytes > 0)
            {
                // resend unacknowledged data

                for (int off = 0; off < this.unackedBytes; )
                {
                    final var bytesToSend = Math.min(
                        this.unackedBytes - off,
                        Config.MAX_DATA_PACKET_PAYLOAD_SIZE
                    );

                    ReliableSocketConnection.this.reliableSocket.sendPacketData(
                        this.outgoingPacketBuffer,
                        ReliableSocketConnection.this.remoteEndpoint,
                        ReliableSocketConnection.this.localConnectionId,
                        this.ackedBytes + off,
                        this.unackedBuffer,
                        this.unackedBufferStart + off,
                        bytesToSend
                    );

                    off += bytesToSend;
                }

                // update last retransmitted byte

                this.lastRetransmittedBytePlusOne =
                    this.ackedBytes + this.unackedBytes;

                // reset acknowledgement timeout timer

                this.ackTimeoutExecutor.schedule(
                    this::onAcknowledgmentTimeout,
                    this.rttEstimator.computeTimeoutNanos(),
                    TimeUnit.NANOSECONDS
                );
            }
        }

        private synchronized void onAcknowledgmentReceived(long ackUpTo)
            throws IOException
        {
            // ignore if data is already acknowledged

            if (ackUpTo <= this.ackedBytes)
                return;

            // update acked and unacked byte counters

            final var ackedDelta = ackUpTo - this.ackedBytes;

            if (ackedDelta > this.unackedBytes)
            {
                // received acknowledgement for unsent data, close connection

                ReliableSocketConnection.this.close();

                throw new IOException(
                    "received acknowledgement for unsent data"
                );
            }

            this.ackedBytes += ackedDelta;
            this.unackedBufferStart += ackedDelta;
            this.unackedBytes -= ackedDelta;

            // notify waiters

            this.notifyAll();

            // update RTT estimation

            // TODO: implement

            // schedule acknowledgement timeout if there still are
            // unacknowledged bytes

            if (this.unackedBytes > 0)
            {
                ackTimeoutExecutor.schedule(
                    this::onAcknowledgmentTimeout,
                    this.rttEstimator.computeTimeoutNanos(),
                    TimeUnit.NANOSECONDS
                );
            }
        }

        @Override
        public void write(int b) throws IOException
        {
            this.write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len)
            throws IOException
        {
            // validate arguments

            Objects.checkFromIndexSize(off, len, b.length);

            // validate state

            if (ReliableSocketConnection.this.isClosed())
                throw new IllegalStateException();

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

        @Override
        public synchronized void flush() throws IOException
        {
            // validate state

            if (ReliableSocketConnection.this.isClosed())
                throw new IllegalStateException();

            // send unsent data

            this.sendUnsentData();

            // wait until there is no more unacknowledged data

            Util.waitUntil(this, () -> unackedBytes == 0);
        }
    }
}

/* -------------------------------------------------------------------------- */
