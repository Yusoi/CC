package Client;

import java.io.IOException;
import java.net.*;

public class AgenteUDP {
    private DatagramSocket socket;
    private InetAddress address;

    public AgenteUDP() {
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try {
            address = InetAddress.getByName("localhost");
        }catch(UnknownHostException e){
            e.printStackTrace();
        }
    }

    public void close() {
        socket.close();
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }


}
