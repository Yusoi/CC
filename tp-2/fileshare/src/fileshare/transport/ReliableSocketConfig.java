/* -------------------------------------------------------------------------- */

package fileshare.transport;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
final class ReliableSocketConfig
{
    /**
     * The maximum size of received segments (including header).
     *
     * = standard ethernet MTU (1500 bytes)
     * - maximum IPv4 header size (60 bytes)
     * - UDP header size (8 bytes)
     */
    public static int MAX_SEGMENT_SIZE = 1500 - 60 - 8;

    /**
     * How many seconds should elapse before an unacknowledged data segment
     * is retransmitted.
     */
    public static float RETRANSMISSION_DELAY = 0.200f;

    /**
     * How many segments can be held in the receive buffer.
     */
    public static int RECEIVE_WINDOW = 1024;

    /**
     * Whether negative data acknowledgment (DATA-NEGACK) segments should be
     * sent.
     */
    public static boolean SEND_DATA_NEGACK = false;

    // No point in ever instantiating this class.
    private ReliableSocketConfig()
    {
    }
}

/* -------------------------------------------------------------------------- */
