/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class MySocketConnection
{
    private final MySocket mySocket;

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    MySocketConnection(MySocket mySocket, Socket socket)
    {
        try
        {
            this.mySocket = mySocket;

            this.socket = socket;
            this.input  = new DataInputStream(socket.getInputStream());
            this.output = new DataOutputStream(socket.getOutputStream());
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
    public byte[] receive()
    {
        try
        {
            final int length = this.input.readInt();

            final byte[] data = new byte[length];
            this.input.readFully(data);

            return data;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * TODO: document
     *
     * @param data TODO: document
     */
    public void send(byte[] data)
    {
        try
        {
            this.output.writeInt(data.length);
            this.output.write(data);
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
            this.socket.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}

/* -------------------------------------------------------------------------- */
