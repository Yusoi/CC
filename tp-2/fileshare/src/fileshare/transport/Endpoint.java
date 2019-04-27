/* -------------------------------------------------------------------------- */

package fileshare.transport;

import inet.ipaddr.HostName;
import inet.ipaddr.HostNameException;
import inet.ipaddr.HostNameParameters;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 *
 * InetSocketAddress already does this, but we want to force addresses to be
 * resolved.
 *
 * Instances of this class are immutable.
 */
public class Endpoint
{
    /**
     * TODO: document
     *
     * @param endpoint TODO: document
     * @param defaultPort TODO: document
     *
     * @return TODO: document
     *
     * @throws IllegalArgumentException TODO: document
     * @throws UnknownHostException TODO: document
     */
    public static Endpoint parse(String endpoint, int defaultPort)
        throws UnknownHostException
    {
        // validate defaultPort argument

        if (defaultPort < 1 || defaultPort > 65535)
        {
            throw new IllegalArgumentException(
                "defaultPort must be between 1 and 65535, inclusive"
            );
        }

        // create HostName object

        final var params = new HostNameParameters.Builder()
            .allowEmpty(false)
            .allowService(false)
            .toParams();

        final var hostNameObj = new HostName(endpoint, params);

        // parse endpoint

        final var port = hostNameObj.getPort();

        try
        {
            return new Endpoint(
                hostNameObj.toInetAddress(),
                port != null ? port : defaultPort
            );
        }
        catch (HostNameException | IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Invalid endpoint string.");
        }
    }

    private final InetAddress address;
    private final int port;

    /**
     * TODO: document
     *
     * @param address TODO: document
     * @param port TODO: document
     *
     * @throws IllegalArgumentException TODO: document
     */
    public Endpoint(InetAddress address, int port)
    {
        // validate arguments

        Objects.requireNonNull(address, "address must not be null");

        if (port < 1 || port > 65535)
        {
            throw new IllegalArgumentException(
                "port must be between 1 and 65535, inclusive"
                );
        }

        // initialize instance

        this.address = address;
        this.port    = port;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public InetAddress getAddress()
    {
        return this.address;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public int getPort()
    {
        return this.port;
    }
}

/* -------------------------------------------------------------------------- */
