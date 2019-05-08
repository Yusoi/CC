/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
class Config
{
    // packet type identifiers

    public static final byte TYPE_ID_CONN        = 0;
    public static final byte TYPE_ID_CONN_ACCEPT = 1;
    public static final byte TYPE_ID_CONN_REJECT = 2;
    public static final byte TYPE_ID_DATA        = 3;
    public static final byte TYPE_ID_DATA_ACK    = 4;
    public static final byte TYPE_ID_DISC        = 5;
    public static final byte TYPE_ID_DISC_ACK    = 6;

    // packet size

    /**
     * The maximum size of any packet, including checksum and type identifier,
     * in bytes.
     *
     * This value is chosen in order to avoid IP packet fragmentation.
     *
     * = standard ethernet MTU (1500 bytes)
     * - maximum IPv4 header size (60 bytes)
     * - UDP header size (8 bytes)
     */
    public static final int MAX_PACKET_SIZE = 1500 - 60 - 8;

    public static final int MAX_DATA_PAYLOAD_SIZE =
        MAX_PACKET_SIZE - 4 - 1 - 4 - 4;

    // packet integrity

    /**
     * Factory of checksum computing objects to be used to verify packet
     * integrity.
     */
    public static final Supplier< Checksum > CHECKSUM = CRC32::new;

    // connection establishment

    /**
     * How many times a CONN packet should be sent before failing if no response
     * is received.
     */
    public static final int MAX_CONNECTION_ATTEMPTS = 5;

    /**
     * How many milliseconds to wait before resending a CONN packet if no
     * response is received.
     */
    public static final int CONNECTION_RETRY_DELAY = 500;

    // connection termination

    /**
     * How many times a DISC packet should be sent if no corresponding DISC-ACK
     * packet is received.
     */
    public static final int MAX_DISCONNECTION_ATTEMPTS = 3;

    /**
     * How many milliseconds to wait before resending a DISC packet if no
     * corresponding DISC-ACK packet is received.
     */
    public static final int DISCONNECTION_RETRY_DELAY = 200;

    // receive window

    /**
     * In bytes.
     */
    public static final int MAX_DATA_PAYLOAD_BYTES_IN_TRANSIT = 1 << 20;

    // No point in ever instantiating this class.
    private Config()
    {
    }
}

/* -------------------------------------------------------------------------- */
