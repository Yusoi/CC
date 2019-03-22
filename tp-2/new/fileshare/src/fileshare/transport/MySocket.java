/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Predicate;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class MySocket
{
    private final int localPort;
    private final ServerSocket serverSocket;

    /**
     * TODO: document
     *
     * @param localPort TODO: document
     */
    public MySocket(int localPort)
    {
        try
        {
            this.localPort    = localPort;
            this.serverSocket = new ServerSocket(localPort);
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
    public int getLocalPort()
    {
        return this.localPort;
    }

    /**
     * TODO: document
     *
     * @param acceptOrReject TODO: document
     * @return TODO: document
     */
    public MySocketConnection listen(
        Predicate< InetSocketAddress > acceptOrReject
        )
    {
        try
        {
            final Socket socket = this.serverSocket.accept();

            final InetSocketAddress address = new InetSocketAddress(
                socket.getInetAddress(),
                socket.getPort()
                );

            if (acceptOrReject.test(address))
            {
                return new MySocketConnection(this, socket);
            }
            else
            {
                socket.close();
                throw new RuntimeException("Rejected connection.");
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * TODO: document
     *
     * @param remoteEndpoint TODO: document
     * @return TODO: document
     */
    public MySocketConnection connect(InetSocketAddress remoteEndpoint)
    {
        try
        {
            final Socket socket = new Socket(
                remoteEndpoint.getAddress(),
                remoteEndpoint.getPort()
                );

            return new MySocketConnection(this, socket);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

    }

    /**
     * TODO: document
     */
    public void close()
    {
        try
        {
            this.serverSocket.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}

/* -------------------------------------------------------------------------- */
