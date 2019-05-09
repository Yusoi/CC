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
    private final short connectionId;

    /**
     * TODO: document
     *
     * @param remoteEndpoint TODO: document
     * @param connectionSeqnum TODO: document
     */
    public ConnectionIdentifier(
        Endpoint remoteEndpoint,
        short connectionSeqnum
    )
    {
        this.remoteEndpoint = Objects.requireNonNull(remoteEndpoint);
        this.connectionId = connectionSeqnum;
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
    public short getConnectionId()
    {
        return this.connectionId;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != ConnectionIdentifier.class)
            return false;

        final var other = (ConnectionIdentifier) obj;

        return
            this.remoteEndpoint.equals(other.remoteEndpoint) &&
                this.connectionId == other.connectionId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.remoteEndpoint, this.connectionId);
    }
}

/* -------------------------------------------------------------------------- */
