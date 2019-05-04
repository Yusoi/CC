/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/* -------------------------------------------------------------------------- */

/**
 * A whitelist of network addresses.
 *
 * This class is thread-safe.
 */
public class AddressWhitelist
{
    private final Set< AddressRange > ranges;

    /**
     * Creates an empty {@code AddressWhitelist}.
     */
    public AddressWhitelist()
    {
        this.ranges = new HashSet<>();
    }

    /**
     * Adds an address range to this whitelist.
     *
     * Overlapping or duplicate address ranges are allowed.
     *
     * @param range the address range to be added to this whitelist
     *
     * @throws NullPointerException if {@code range} is {@code null}
     */
    public synchronized void add(AddressRange range)
    {
        this.ranges.add(Objects.requireNonNull(range));
    }

    /**
     * Removes an address range from this whitelist.
     *
     * The specified address range must be equal to a previously added range.
     * Overlapping address ranges are not modified or removed.
     *
     * If this whitelist does not contain {@code range}, this method has no
     * effect.
     *
     * @param range the address range to be removed from this whitelist
     *
     * @throws NullPointerException if {@code range} is {@code null}
     */
    public synchronized void remove(AddressRange range)
    {
        this.ranges.remove(Objects.requireNonNull(range));
    }

    /**
     * Removes all address ranges from this whitelist.
     */
    public synchronized void clear()
    {
        this.ranges.clear();
    }

    /**
     * Checks whether a certain address is whitelisted.
     *
     * @param address the address to be checked
     * @return whether {@code address} is whitelisted
     *
     * @throws NullPointerException if {@code address} is {@code null}
     */
    public synchronized boolean isWhitelisted(InetAddress address)
    {
        Objects.requireNonNull(address);
        return this.ranges.stream().anyMatch(range -> range.contains(address));
    }
}

/* -------------------------------------------------------------------------- */
