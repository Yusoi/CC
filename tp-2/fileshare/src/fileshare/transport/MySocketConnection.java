/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class MySocketConnection implements AutoCloseable
{
    private final MySocket mySocket;
    private final Socket socket;

    MySocketConnection(MySocket mySocket, Socket socket)
    {
        this.mySocket = mySocket;
        this.socket   = socket;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public MySocket getSocket()
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
