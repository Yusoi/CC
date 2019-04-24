/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Whitelist
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
    public Whitelist()
    {
        this.patterns = new HashSet<>();
    }

    /**
     * TODO: document
     *
     * @param cidrPattern TODO: document
     */
    public void add(String cidrPattern)
    {
        if (!Whitelist.isValidCidrPattern(cidrPattern))
            throw new IllegalArgumentException();

        this.patterns.add(cidrPattern);
    }

    /**
     * TODO: document
     *
     * @param cidrPattern TODO: document
     */
    public void remove(String cidrPattern)
    {
        if (!Whitelist.isValidCidrPattern(cidrPattern))
            throw new IllegalArgumentException();

        this.patterns.remove(cidrPattern);
    }

    /**
     * TODO: document
     */
    public void clear()
    {
        this.patterns.clear();
    }

    /**
     * TODO: document
     *
     * @param address TODO: document
     * @return TODO: document
     */
    public boolean isWhitelisted(InetAddress address)
    {
        Objects.requireNonNull(address);

        return this.patterns.stream().anyMatch(
            cidrPattern -> Whitelist.cidrPatternMatches(cidrPattern, address)
            );
    }
}

/* -------------------------------------------------------------------------- */
