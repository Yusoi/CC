/* -------------------------------------------------------------------------- */

package fileshare.transport;

/* -------------------------------------------------------------------------- */

class IncomingConnectionRequest
{
    private final Endpoint remoteEndpoint;
    private final int remoteConnectionSeqnum;

    public IncomingConnectionRequest(
        Endpoint remoteEndpoint,
        int remoteConnectionSeqnum
    )
    {
        this.remoteEndpoint = remoteEndpoint;
        this.remoteConnectionSeqnum = remoteConnectionSeqnum;
    }

    public Endpoint getRemoteEndpoint()
    {
        return this.remoteEndpoint;
    }

    public int getRemoteConnectionSeqnum()
    {
        return this.remoteConnectionSeqnum;
    }
}

/* -------------------------------------------------------------------------- */
