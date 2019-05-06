/* -------------------------------------------------------------------------- */

package fileshare.transport;

/* -------------------------------------------------------------------------- */

import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

class ReliableSocketConfig
{
    // sizes

    public static int MAX_PACKET_SIZE = 1000;

    // integrity

    public static Supplier< Checksum > CHECKSUM = CRC32::new;
}

/* -------------------------------------------------------------------------- */
