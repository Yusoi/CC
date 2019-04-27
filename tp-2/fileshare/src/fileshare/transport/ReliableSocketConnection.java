/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class ReliableSocketConnection implements AutoCloseable
{
    /**
     * Whether the receiver should explicitly request the retransmission of
     * corrupted packets.
     */
    private static final boolean ENABLE_RETRANSMISSION_REQUESTS = false;

    private final ReliableSocket mySocket;
    private final Socket socket;

    ReliableSocketConnection(ReliableSocket mySocket, Socket socket)
    {
        this.mySocket = mySocket;
        this.socket   = socket;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public ReliableSocket getSocket()
    {
        return this.mySocket;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public InetSocketAddress getRemoteEndpoint()
    {
        return new InetSocketAddress(
            this.socket.getInetAddress(),
            this.socket.getPort()
        );
    }

    /**
     * TODO: document
     *
     * The stream's close() method has no effect.
     *
     * If the connection was closed and there is no unread data, the stream
     * gives EOF. It is possible to read the remaining data even if the
     * connection is already closed.
     *
     * @return TODO: document
     */
    public InputStream getInputStream()
    {
        try
        {
            return this.socket.getInputStream();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * TODO: document
     *
     * The stream's data is buffered (to a certain unspecified size). In order
     * to force write, use the stream's flush() method.
     *
     * The stream's close() method calls its flush() method but has no other
     * effect.
     *
     * @return TODO: document
     */
    public OutputStream getOutputStream()
    {
        try
        {
            return this.socket.getOutputStream();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * TODO: document
     *
     * Closes the connection. Any unread input data or non-flushed output data
     * is lost. The remote can, however, read data that was written and flushed
     * before the connection was closed.
     */
    @Override
    public void close()
    {
        try
        {
            this.socket.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}

/* -------------------------------------------------------------------------- */
