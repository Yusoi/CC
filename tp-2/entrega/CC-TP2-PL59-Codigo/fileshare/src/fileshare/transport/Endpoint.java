/* -------------------------------------------------------------------------- */

package fileshare.transport;

import inet.ipaddr.HostName;
import inet.ipaddr.HostNameException;
import inet.ipaddr.HostNameParameters;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * Identifies an endpoint by its address and port number.
 *
 * This class mainly differs from {@link InetSocketAddress} in that it does not
 * allow unresolved hostnames.
 *
 * Instances of this class are immutable, equality comparable, and hashable.
 */
public class Endpoint
{
    /**
     * Creates an {@code Endpoint} from a string representation.
     *
     * The following are examples of valid endpoint string representations:
     *
     * <ul>
     *     <li>{@code 127.0.0.1}</li>
     *     <li>{@code 127.0.0.1:7777}</li>
     *     <li>{@code [2001:db8::ff00:42:8329]:1234}</li>
     *     <li>{@code [2001:db8::ff00:42:8329]:1234}</li>
     *     <li>{@code hostname1}</li>
     *     <li>{@code hostname1:1234}</li>
     * </ul>
     *
     * Note that a hostname can be given instead of an address, in which case an
     * attempt will be made to resolve it. If that attempt fails, {@link
     * UnknownHostException} is thrown.
     *
     * @param endpoint the endpoint's string representation
     * @param defaultPort the port to be used if none is explicitly specified in
     *        the string representation
     *
     * @return the {@code Endpoint} corresponding to the given string
     *
     * @throws NullPointerException if endpoint is null
     * @throws IllegalArgumentException if {@code endpoint} is not a valid
     *         string representation of an endpoint
     * @throws UnknownHostException if a hostname was specified instead of an
     *         address and the attempt to resolve it failed
     */
    public static Endpoint parse(String endpoint, int defaultPort)
        throws UnknownHostException
    {
        Objects.requireNonNull(endpoint);

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
     * Creates an {@code Endpoint} from an address and a port number.
     *
     * @param address the endpoint's address
     * @param port the endpoint's port number
     *
     * @throws NullPointerException if {@code address} is {@code null}
     * @throws IllegalArgumentException if {@code address} is not an instance of
     *         {@link Inet4Address} or {@link Inet6Address}
     * @throws IllegalArgumentException if {@code port} is less than 1 or
     *         greater than 65535
     */
    public Endpoint(InetAddress address, int port)
    {
        // validate arguments

        Objects.requireNonNull(address);

        if (!(address instanceof Inet4Address) &&
                !(address instanceof Inet6Address))
        {
            throw new IllegalArgumentException(
                "address must be an instance of Inet4Address or Inet6Address"
            );
        }

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
     * Returns the endpoint's address.
     *
     * @return the endpoint's address
     */
    public InetAddress getAddress()
    {
        return this.address;
    }

    /**
     * Returns the endpoint's port number.
     *
     * @return the endpoint's port number
     */
    public int getPort()
    {
        return this.port;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != Endpoint.class)
            return false;

        final var other = (Endpoint) obj;

        return
            Objects.equals(this.getAddress(), other.getAddress()) &&
            Objects.equals(this.getPort()   , other.getPort()   );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.getAddress(), this.getPort());
    }

    @Override
    public String toString()
    {
        final String format;

        if (this.getAddress() instanceof Inet4Address)
            format = "%s:%d";
        else
            format = "[%s]:%d";

        return String.format(
            format,
            this.getAddress().getHostAddress(),
            this.getPort()
        );
    }
}

/* -------------------------------------------------------------------------- */
