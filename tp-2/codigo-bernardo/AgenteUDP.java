import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AgenteUDP implements Runnable {
    private File file;
    private ConcurrentHashMap<Long,Estado> estado;
    private DatagramSocket socket;
    private InetAddress address;

    public AgenteUDP(ConcurrentHashMap<Long,Estado> estado, File file) {
        this.file = file;
        this.estado = estado;

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

    @Override
    public void run(){

        byte[] buf;

        try {
            buf = Files.readAllBytes(file.toPath());

            //TODO adicionar overhead ao buffer
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 7777);
            socket.send(packet);
            packet = new DatagramPacket(buf, buf.length);
            //TODO testar mensagem de sucesso ou falha
            socket.receive(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int generateChecksum(byte[] obj) {
        return obj.hashCode();
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
