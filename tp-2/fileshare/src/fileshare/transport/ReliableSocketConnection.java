/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

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

    private final Socket tcpSocket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    ReliableSocketConnection(
        ReliableSocket mySocket,
        Socket tcpSocket
        ) throws IOException
    {
        this.reliableSocket = mySocket;

        this.tcpSocket    = tcpSocket;
        this.inputStream  = tcpSocket.getInputStream();
        this.outputStream = tcpSocket.getOutputStream();
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
        return new Endpoint(
            this.tcpSocket.getInetAddress(),
            this.tcpSocket.getPort()
            );
    }

    /**
     * Returns the input stream for this side of this connection.
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
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the input stream for this side of this connection
     */
    public InputStream getInputStream()
    {
        return this.inputStream;
    }

    /**
     * Returns the output stream for this side of this connection.
     *
     * The returned stream's data is buffered (to a certain unspecified size).
     * In order to force-send buffered data, use the returned stream's
     * {@link OutputStream#flush()} method.
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
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the output stream for this side of this connection
     */
    public OutputStream getOutputStream()
    {
        return this.outputStream;
    }

    /**
     * Checks whether this side of the connection has been closed.
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return whether this side of the connection has been closed
     */
    public boolean isClosed()
    {
        return this.tcpSocket.isClosed();
    }

    /**
     * Closes this end/side of the connection.
     *
     * Any unread data in the input stream is lost.
     *
     * Any unsent data in the output stream is lost. Use {@code
     * getOutputStream().flush()} before invoking this method to ensure that all
     * unsent data is sent.
     *
     * Any active calls on this side's input or output streams will throw an
     * exception when this method is called.
     *
     * Reading from {@link #getInputStream()} and writing to {@link
     * #getOutputStream()} after invoking this method will throw {@link
     * IllegalStateException}.
     *
     * See {@link #getInputStream()} and {@link #getOutputStream()} for more
     * information on the effects of this method on this side's streams.
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
    public void close() throws IOException
    {
        this.tcpSocket.close();
    }
}

/* -------------------------------------------------------------------------- */
