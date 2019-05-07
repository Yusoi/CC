/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
class ConnectionIdentifier
{
    private final Endpoint remoteEndpoint;
    private final int connectionSeqnum;

    /**
     * TODO: document
     *
     * @param remoteEndpoint TODO: document
     * @param connectionSeqnum TODO: document
     */
    public ConnectionIdentifier(
        Endpoint remoteEndpoint,
        int connectionSeqnum
    )
    {
        this.remoteEndpoint = Objects.requireNonNull(remoteEndpoint);
        this.connectionSeqnum = connectionSeqnum;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Endpoint getRemoteEndpoint()
    {
        return this.remoteEndpoint;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
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
