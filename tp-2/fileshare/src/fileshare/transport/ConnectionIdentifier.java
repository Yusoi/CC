/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * Identifies a connection.
 *
 * Note that this class is package-private.
 */
class ConnectionIdentifier
{
    private final Endpoint remoteEndpoint;
    private final short connectionId;

    /**
     * Creates a {@code ConnectionIdentifier}.
     *
     * @param remoteEndpoint the endpoint of the connection's remote end
     * @param connectionId the identifier of one of the connection's ends
     *
     * @throws NullPointerException if {@code remoteEndpoint} is {@code null}
     */
    ConnectionIdentifier(
        Endpoint remoteEndpoint,
        short connectionId
    )
    {
        this.remoteEndpoint = Objects.requireNonNull(remoteEndpoint);
        this.connectionId = connectionId;
    }

    /**
     * Returns the endpoint of the connection's remote end.
     *
     * @return the endpoint of the connection's remote end
     */
    Endpoint getRemoteEndpoint()
    {
        return this.remoteEndpoint;
    }

    /**
     * Returns the identifier of one of the connection's ends.
     *
     * @return the identifier of one of the connection's ends
     */
    short getConnectionId()
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
