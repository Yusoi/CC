/* -------------------------------------------------------------------------- */

package fileshare.core;

import inet.ipaddr.AddressStringParameters;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.IPAddressStringParameters;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 *
 * Instances of this class are equality comparable and hashable.
 */
public class AddressRange
{
    /**
     * TODO: document
     *
     * @param cidrNotation TODO: document
     * @return TODO: document
     *
     * @throws IllegalArgumentException TODO: document
     */
    public static AddressRange fromCidrNotation(String cidrNotation)
    {
        final var params = new IPAddressStringParameters.Builder()
            .allowAll(false)
            .allowEmpty(false)
            .allowMask(false)
            .allowPrefixOnly(false)
            .allowSingleSegment(false)
            .allowWildcardedSeparator(false)
            .setRangeOptions(AddressStringParameters.RangeParameters.NO_RANGE)
            .toParams();

        final var addressString = new IPAddressString(cidrNotation, params);

        return new AddressRange(addressString);
    }

    private final IPAddressString addressString;

    private AddressRange(IPAddressString addressString)
    {
        this.addressString = addressString;
    }

    /**
     * TODO: document
     *
     * @param address TODO: document
     * @return TODO: document
     *
     * @throws IllegalArgumentException TODO: document
     */
    public boolean contains(InetAddress address)
    {
        final IPAddress ipAddress;

        if (address instanceof Inet4Address)
        {
            ipAddress = new IPv4Address((Inet4Address) address);
        }
        else if (address instanceof Inet6Address)
        {
            ipAddress = new IPv6Address((Inet6Address) address);
        }
        else
        {
            throw new IllegalArgumentException(
                "address must be of type Inet4Address or Inet6Address"
            );
        }

        return this.addressString.contains(ipAddress.toAddressString());
    }

    @Override
    public boolean equals(Object obj)
    {
        return
            obj != null &&
            obj instanceof AddressRange &&
            this.addressString.equals(((AddressRange)obj).addressString);
    }

    @Override
    public int hashCode()
    {
        return this.addressString.hashCode();
    }
}

/* -------------------------------------------------------------------------- */
