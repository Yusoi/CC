/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.Objects;

/* -------------------------------------------------------------------------- */

class ConnectionIdentifier
{
    private final Endpoint remoteEndpoint;
    private final int connectionSeqnum;

    public ConnectionIdentifier(
        Endpoint remoteEndpoint,
        int connectionSeqnum
    )
    {
        this.remoteEndpoint = Objects.requireNonNull(remoteEndpoint);
        this.connectionSeqnum = connectionSeqnum;
    }

    public Endpoint getRemoteEndpoint()
    {
        return this.remoteEndpoint;
    }

    public int getConnectionSeqnum()
    {
        return this.connectionSeqnum;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != ConnectionIdentifier.class)
            return false;

        final var other = (ConnectionIdentifier) obj;

        return
            this.remoteEndpoint.equals(other.remoteEndpoint) &&
                this.connectionSeqnum == other.connectionSeqnum;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.remoteEndpoint, this.connectionSeqnum);
    }
}

/* -------------------------------------------------------------------------- */
