/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
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
     * TODO: document
     *
     * @return TODO: document
     */
    public ReliableSocket getSocket()
    {
        return this.reliableSocket;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Endpoint getRemoteEndpoint()
    {
        return new Endpoint(
            this.tcpSocket.getInetAddress(),
            this.tcpSocket.getPort()
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
    public InputStream getInputStream() throws IOException
    {
        return this.tcpSocket.getInputStream();
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
    public OutputStream getOutputStream() throws IOException
    {
        return this.tcpSocket.getOutputStream();
    }

    /**
     * TODO: document
     *
     * @return {@code new DataInputStream(this.getInputStream())}
     */
    public DataInputStream getDataInputStream() throws IOException
    {
        return new DataInputStream(this.getInputStream());
    }

    /**
     * TODO: document
     *
     * @return {@code new DataOutputStream(this.getOutputStream())}
     */
    public DataOutputStream getDataOutputStream() throws IOException
    {
        return new DataOutputStream(this.getOutputStream());
    }

    /**
     * TODO: document
     *
     * Closes the connection. Any unread input data or non-flushed output data
     * is lost. The remote can, however, read data that was written and flushed
     * before the connection was closed.
     */
    @Override
    public void close() throws IOException
    {
        this.tcpSocket.close();
    }
}

/* -------------------------------------------------------------------------- */
