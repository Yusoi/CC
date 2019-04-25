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
    /**
     * TODO: document
     *
     * @param cidrPattern TODO: document
     * @return TODO: document
     */
    public static boolean isValidCidrPattern(String cidrPattern)
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    /**
     * TODO: document
     *
     * @param cidrPattern TODO: document
     * @param address TODO: document
     * @return TODO: document
     */
    public static boolean cidrPatternMatches(
        String cidrPattern,
        InetAddress address
        )
    {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    private final Set< String > patterns;

    /**
     * TODO: document
     */
    public PeerWhitelist()
    {
        this.patterns = new HashSet<>();
    }

    /**
     * TODO: document
     *
     * @param cidrPattern TODO: document
     */
    public synchronized void add(String cidrPattern)
    {
        if (!PeerWhitelist.isValidCidrPattern(cidrPattern))
            throw new IllegalArgumentException();

        this.patterns.add(cidrPattern);
    }

    /**
     * TODO: document
     *
     * @param cidrPattern TODO: document
     */
    public synchronized void remove(String cidrPattern)
    {
        if (!PeerWhitelist.isValidCidrPattern(cidrPattern))
            throw new IllegalArgumentException();

        this.patterns.remove(cidrPattern);
    }

    /**
     * TODO: document
     */
    public synchronized void clear()
    {
        this.patterns.clear();
    }

    /**
     * TODO: document
     *
     * @param address TODO: document
     * @return TODO: document
     */
    public synchronized boolean isWhitelisted(InetAddress address)
    {
        Objects.requireNonNull(address);

        return this.patterns.stream().anyMatch(
            cidrPattern -> PeerWhitelist.cidrPatternMatches(cidrPattern, address)
            );
    }
}

/* -------------------------------------------------------------------------- */
