/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 *
 * This class is thread-safe.
 */
public class PeerWhitelist
{
    private final Set< AddressRange > ranges;

    /**
     * TODO: document
     */
    public PeerWhitelist()
    {
        this.ranges = new HashSet<>();
    }

    /**
     * TODO: document
     *
     * @param range TODO: document
     */
    public synchronized void add(AddressRange range)
    {
        Objects.requireNonNull(range);
        this.ranges.add(range);
    }

    /**
     * TODO: document
     *
     * @param range TODO: document
     */
    public synchronized void remove(AddressRange range)
    {
        Objects.requireNonNull(range);
        this.ranges.remove(range);
    }

    /**
     * TODO: document
     */
    public synchronized void clear()
    {
        this.ranges.clear();
    }

    /**
     * TODO: document
     *
     * @param address TODO: document
     * @return TODO: document
     *
     * @throws NullPointerException if address is null
     */
    public synchronized boolean isWhitelisted(InetAddress address)
    {
        Objects.requireNonNull(address);
        return this.ranges.stream().anyMatch(range -> range.contains(address));
    }
}

/* -------------------------------------------------------------------------- */
