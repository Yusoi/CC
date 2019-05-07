/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.Objects;

/* -------------------------------------------------------------------------- */

class ConnectionIdentifier
{
    private final Endpoint remoteEndpoint;
    private final int localConnectionSeqnum;

    public ConnectionIdentifier(
        Endpoint remoteEndpoint,
        int localConnectionSeqnum
    )
    {
        this.remoteEndpoint = Objects.requireNonNull(remoteEndpoint);
        this.localConnectionSeqnum = localConnectionSeqnum;
    }

    public Endpoint getRemoteEndpoint()
    {
        return this.remoteEndpoint;
    }

    public int getLocalConnectionSeqnum()
    {
        return this.localConnectionSeqnum;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != ConnectionIdentifier.class)
            return false;

        final var other = (ConnectionIdentifier) obj;

        return
            Objects.equals(this.remoteEndpoint, other.remoteEndpoint) &&
                Objects.equals(this.localConnectionSeqnum, other.localConnectionSeqnum);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.remoteEndpoint, this.localConnectionSeqnum);
    }
}

/* -------------------------------------------------------------------------- */
