/* -------------------------------------------------------------------------- */

package fileshare.transport;

/* -------------------------------------------------------------------------- */

class Segments
{
    public static void sendConn(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int clientConnectionSeqnum
    )
    {
        // TODO: implement
    }

    public static void sendConnReject(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int serverConnectionSeqnum
    )
    {
        // TODO: implement
    }

    public static void sendConnAccept(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int clientConnectionSeqnum,
        int serverConnectionSeqnum
    )
    {
        // TODO: implement
    }

    public static void sendConnAcceptAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int serverConnectionSeqnum
    )
    {
        // TODO: implement
    }

    public static void sendData(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum,
        int payloadSeqnum,
        byte[] payloadBuffer,
        int payloadLength
    )
    {
        // TODO: implement
    }

    public static void sendDataAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum,
        int payloadSeqnum
    )
    {
        // TODO: implement
    }

    public static void sendDisc(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum
    )
    {
        // TODO: implement
    }

    public static void sendDiscAck(
        byte[] packetBuffer,
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum
    )
    {
        // TODO: implement
    }
}

/* -------------------------------------------------------------------------- */
