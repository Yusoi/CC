/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/* -------------------------------------------------------------------------- */

/**
 * A connection between two instances of {@link ReliableSocket}.
 *
 * This class is thread-safe.
 */
public class ReliableSocketConnection implements AutoCloseable
{
    private final ReliableSocket reliableSocket;
    private final Socket tcpSocket;

    ReliableSocketConnection(ReliableSocket mySocket, Socket tcpSocket)
    {
        this.reliableSocket = mySocket;
        this.tcpSocket      = tcpSocket;
    }

    /**
     * Returns the {@link ReliableSocket} from which this connection was
     * created.
     *
     * This method succeeds even if this connection is closed.
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
     * This method succeeds even if this connection is closed.
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
     * If this connection is open but the other side's output has been shut
     * down, the returned stream is still readable (no data sent by the remote
     * is lost) and will indicate EOF when all data sent by the remote has been
     * read.
     *
     * If this connection is closed, reading from the returned stream will
     * result in {@link IOException} being thrown.
     *
     * The returned stream's {@link InputStream#close()} method has no effect.
     *
     * This method succeeds even if this connection is closed.
     *
     * If this method fails, this connection is left in a closed state (as if by
     * invoking {@link #close()}).
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the input stream for this side of this connection
     */
    public InputStream getInputStream()
    {
        try
        {
            return this.tcpSocket.getInputStream();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the output stream for this side of this connection.
     *
     * The returned stream's data is buffered (to a certain unspecified size).
     * In order to force-send buffered data, use the returned stream's
     * {@link OutputStream#flush()} method.
     *
     * If this connection is open but this side's output stream has been shut
     * down, or if this connection is closed, writing to the returned stream
     * will result in {@link IOException} being thrown.
     *
     * The returned stream's {@link InputStream#close()} method has no effect.
     *
     * This method succeeds even if this connection is closed.
     *
     * If this method fails, this connection is left in a closed state (as if by
     * invoking {@link #close()}).
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @return the output stream for this side of this connection
     */
    public OutputStream getOutputStream()
    {
        try
        {
            return this.tcpSocket.getOutputStream();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Shuts down this side's output stream.
     *
     * Invoking this method first flushes this connection's output stream (thus
     * sending any already written but buffered data; see
     * {@link #getOutputStream()}) and then closes that stream.
     *
     * It is *not* mandatory to invoke this method prior to invoking
     * {@link #close()}.
     *
     * This connection still has to be closed with {@link #close()} even if both
     * sides invoke this method.
     *
     * See {@link #getInputStream()} and {@link #getOutputStream()} for more
     * information on the effects of this method on this connection's streams.
     *
     * If this side's output stream is already shut down, or if this connection
     * is closed, this method has no effect.
     *
     * If this method fails, this connection is left in a closed state (as if by
     * invoking {@link #close()}).
     *
     * This class' API (including this method) is fully thread-safe: all methods
     * may be called concurrently with any method.
     *
     * @throws IOException if an I/O error occurs
     */
    public void shutdownOutput() throws IOException
    {
        this.tcpSocket.shutdownOutput();
    }

    /**
     * Closes this connection on both ends.
     *
     * Any unread data in this connection's input stream is lost.
     *
     * Any unsent data buffered in this connection's output stream is lost.
     *
     * See {@link #getInputStream()} and {@link #getOutputStream()} for more
     * information on the effects of this method.
     *
     * If this connection is already closed, this method has no effect.
     *
     * If this method fails, this connection will nevertheless be left in a
     * closed state.
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
