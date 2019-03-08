package Client;

import java.io.IOException;
import java.net.DatagramPacket;

public class TransfereCC {

    private AgenteUDP audp;
    private byte[] buf;

    public String sendMessage(String msg) {
        try {
            buf = msg.getBytes();
            DatagramPacket packet
                    = new DatagramPacket(buf, buf.length, audp.getAddress(), 7777);
            audp.getSocket().send(packet);
            packet = new DatagramPacket(buf, buf.length);
            audp.getSocket().receive(packet);
            String received = new String(
                    packet.getData(), 0, packet.getLength());
            return received;
        } catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }
}
