/* -------------------------------------------------------------------------- */

package fileshare.transport;

import fileshare.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

    private final DataInputStream input;
    private final DataOutputStream output;

    private boolean closed;

    ReliableSocketConnection(
        ReliableSocket reliableSocket,
        Endpoint remoteEndpoint,
        short localConnectionId
    )
    {
        this.reliableSocket = reliableSocket;

        this.remoteEndpoint = remoteEndpoint;
        this.localConnectionId = localConnectionId;

        this.input = new DataInputStream(this.new Input());
        this.output = new DataOutputStream(this.new Output());

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
     * Returns the unique input stream for this side of this connection.
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
     * @return the input stream for this side of this connection
     */
    public DataInputStream getInput()
    {
        return this.input;
    }

    /**
     * Returns the unique output stream for this side of this connection.
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
     * @return the output stream for this side of this connection
     */
    public DataOutputStream getOutput()
    {
        return this.output;
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
     * Any unread data in the input stream is lost.
     *
     * Any unsent data in the output stream is lost. Use {@code
     * getOutput().flush()} before invoking this method to ensure that all
     * unsent data is sent.
     *
     * Any active calls on this side's input or output streams will throw an
     * {@link InterruptedException} when this method is called.
     *
     * Reading from {@link #getInput()} and writing to {@link #getOutput()}
     * after invoking this method will throw {@link IllegalStateException}.
     *
     * See {@link #getInput()} and {@link #getOutput()} for more information on
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
    }

    void processPacketData(DataInputStream packetInput) throws IOException
    {
        // TODO: implement
    }

    void processPacketDataAck(DataInputStream packetInput) throws IOException
    {
        // TODO: implement
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
        private long receivedBytes = 0;

        // the buffer is cyclic
        private final byte[] buffer = new byte[
            Config.MAX_DATA_PAYLOAD_BYTES_IN_TRANSIT
            ];

        private int bufferPosition = 0;
        private int bufferLength = 0;

        @Override
        public int read() throws IOException
        {
            // TODO: implement

            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            // TODO: implement

            return 0;
        }
    }

    private class Output extends OutputStream
    {
        // to avoid recreating the buffer for holding packets to be sent
        private final byte[] packetBuffer = new byte[Config.MAX_PACKET_SIZE];

        private long totalAckedBytes = 0;

        private final byte[] unsentBuffer = new byte[Config.MAX_DATA_PACKET_PAYLOAD_SIZE];
        private int unsentBytes = 0;

        private final byte[] unackedBuffer = new byte[Config.MAX_UNACKNOWLEDGED_DATA_BYTES];
        private Long unackedNanos = null;
        private int unackedOffset = 0;
        private int unackedBytes = 0;

        private synchronized void sendUnsentData()
        {
            if (unsentBytes > 0)
            {
                // wait until there is enough space in unacked buffer

                Util.waitUntil(
                    this,
                    () -> unackedBuffer.length - unackedBytes >= unsentBytes
                );

                // send packet

                ReliableSocketConnection.this.reliableSocket.sendPacketData(
                    packetBuffer,
                    ReliableSocketConnection.this.remoteEndpoint,
                    ReliableSocketConnection.this.localConnectionId,
                    totalAckedBytes,
                    unsentBuffer,
                    unsentBytes
                );

                // copy data to unacked buffer

                Util.copyCircular(
                    unsentBuffer,
                    0,
                    unackedBuffer,
                    (unackedOffset + unackedBytes) % unackedBuffer.length,
                    unsentBytes
                );

                unackedBytes += unsentBytes;

                unsentBytes = 0;

                // set timestamp if previously unset

                if (unackedNanos == null)
                    unackedNanos = System.nanoTime();
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
            // validate state

            if (ReliableSocketConnection.this.isClosed())
                throw new IllegalStateException();

            // copy bytes to buffer

            final var end = off + len;

            while (off < end)
            {
                final var lenToCopy = Math.min(
                    this.buffer.length - this.bufferPosition,
                    end - off
                );

                System.arraycopy(
                    b, off, this.buffer, this.bufferPosition, lenToCopy
                );

                this.bufferPosition += lenToCopy;
                off += lenToCopy;

                // flush if buffer is full

                if (this.bufferPosition == this.buffer.length)
                    this.flush();
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

            // wait until there is no more unacked data

            Util.waitUntil(this, () -> unackedBytes == 0);
        }
    }
}

/* -------------------------------------------------------------------------- */
