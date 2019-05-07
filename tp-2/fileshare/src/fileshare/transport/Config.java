/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/* -------------------------------------------------------------------------- */

class Config
{
    // connection establishment

    public static int MAX_CONNECTION_RETRIES = 5;

    /**
     * In milliseconds.
     */
    public static int CONNECTION_RETRY_DELAY = 500;

    // connection termination

    // sizes

    public static int MAX_PACKET_SIZE = 1000;

    // integrity

    /**
     * Factory of checksum computing objects.
     */
    public static Supplier< Checksum > CHECKSUM = CRC32::new;
}

/* -------------------------------------------------------------------------- */
