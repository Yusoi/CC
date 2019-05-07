/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/* -------------------------------------------------------------------------- */

class Config
{
    // packet type identifiers

    public static final byte TYPE_ID_CONN            = 0;
    public static final byte TYPE_ID_CONN_REJECT     = 1;
    public static final byte TYPE_ID_CONN_ACCEPT     = 2;
    public static final byte TYPE_ID_CONN_ACCEPT_ACK = 3;
    public static final byte TYPE_ID_DATA            = 4;
    public static final byte TYPE_ID_DATA_ACK        = 5;
    public static final byte TYPE_ID_DISC            = 6;
    public static final byte TYPE_ID_DISC_ACK        = 7;

    // sizes

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

    // connection establishment

    /**
     * The amount of times that a CONN packet should be sent before failing if
     * no response is received.
     */
    public static final int MAX_CONNECTION_ATTEMPTS = 5;

    /**
     * How many milliseconds to wait before resending a CONN packet if no
     * response is received.
     */
    public static final int CONNECTION_RETRY_DELAY = 500;

    // connection termination

    /**
     * The amount of times that a DISC packet should be sent if no corresponding
     * DISC-ACK packet is received.
     */
    public static final int MAX_DISCONNECTION_ATTEMPTS = 5;

    /**
     * How many milliseconds to wait before resending a DISC packet if no
     * corresponding DISC-ACK packet is received.
     */
    public static final int DISCONNECTION_RETRY_DELAY = 500;

    // integrity verification

    /**
     * Factory of checksum computing objects to be used to verify packet
     * integrity.
     */
    public static final Supplier< Checksum > CHECKSUM = CRC32::new;

    // No point in ever instantiating this class.
    private Config()
    {
    }
}

/* -------------------------------------------------------------------------- */
