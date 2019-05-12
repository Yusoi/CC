/* -------------------------------------------------------------------------- */

package fileshare.core;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.AddressStringParameters;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.IPAddressStringParameters;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * Defines a contiguous range of IPv4 or IPv6 addresses.
 *
 * Instances of this class are immutable, equality comparable, and hashable.
 */
public class AddressRange
{
    /**
     * Creates an {@code AddressRange} from an IPv4 or IPv6 CIDR-notation
     * string.
     *
     * The following are examples of valid CIDR-notation strings:
     *
     * <ul>
     *     <li>192.168.100.14/24</li>
     *     <li>2001:db8::/48</li>
     *     <li>::1/128</li>
     * </ul>
     *
     * @param cidrNotation the CIDR-notation string to be parsed
     * @return the range specified by {@code cidrNotation}
     *
     * @throws NullPointerException if {@code cidrNotation} is {@code null}
     * @throws IllegalArgumentException if {@code cidrNotation} is not a valid
     *         CIDR-notation string
     */
    public static AddressRange parseCidrNotation(String cidrNotation)
    {
        Objects.requireNonNull(cidrNotation);

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

        try
        {
            addressString.validate();
        }
        catch (AddressStringException e)
        {
            throw new IllegalArgumentException(
                String.format(
                    "Invalid CIDR-notation string \"%s\".",
                    cidrNotation
                )
            );
        }

        return new AddressRange(addressString);
    }

    private final IPAddressString addressString;

    private AddressRange(IPAddressString addressString)
    {
        this.addressString = addressString;
    }

    /**
     * Checks whether this address range contains the specified address.
     *
     * @param address the address
     * @return whether this address range contains {@code address}
     *
     * @throws NullPointerException if {@code address} is {@code null}
     * @throws IllegalArgumentException if {@code address} is not an instance of
     *         {@link Inet4Address} or {@link Inet6Address}
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
                "address must be an instance of Inet4Address or Inet6Address"
            );
        }

        return this.addressString.contains(ipAddress.toAddressString());
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != AddressRange.class)
            return false;

        final var other = (AddressRange) obj;

        return Objects.equals(this.addressString, other.addressString);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.addressString);
    }
}

/* -------------------------------------------------------------------------- */
