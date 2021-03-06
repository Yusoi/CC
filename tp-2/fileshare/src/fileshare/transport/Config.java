/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/* -------------------------------------------------------------------------- */

/**
 * Configuration of the reliable data transfer protocol.
 *
 * Note that this class is package-private.
 */
class Config
{
    // packet type identifiers

    static final byte TYPE_ID_CONN        = 0;
    static final byte TYPE_ID_CONN_ACCEPT = 1;
    static final byte TYPE_ID_CONN_REJECT = 2;
    static final byte TYPE_ID_DATA        = 3;
    static final byte TYPE_ID_DATA_ACK    = 4;
    static final byte TYPE_ID_DISC        = 5;
    static final byte TYPE_ID_DISC_ACK    = 6;

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
    static final int MAX_PACKET_SIZE = 1500 - 60 - 8;

    /**
     * The maximum size of the payload in a DATA packet.
     *
     * = MAX_PACKET_SIZE
     * - checksum size (4 bytes)
     * - packet type identifier size (1 byte)
     * - sender-side connection identifier (4 bytes)
     * - data offset (8 bytes)
     */
    static final int MAX_DATA_PACKET_PAYLOAD_SIZE =
        MAX_PACKET_SIZE - 4 - 1 - 4 - 8;

    // packet integrity

    /**
     * Factory of checksum computation objects to be used to verify packet
     * integrity.
     */
    static final Supplier< Checksum > CHECKSUM = CRC32::new;

    // connection establishment

    /**
     * How many times a CONN packet should be sent before failing if no response
     * is received.
     */
    static final int MAX_CONNECT_ATTEMPTS = 5;

    /**
     * How many milliseconds to wait before resending a CONN packet if no
     * response is received.
     */
    static final int CONNECT_RESPONSE_TIMEOUT = 500;

    // data transfer

    /**
     * The maximum amount of sent but unacknowledged data, in bytes.
     */
    static final int MAX_UNACKNOWLEDGED_DATA = 1 << 12;

    /**
     * The size of the receive buffer, in bytes.
     */
    static final int RECEIVE_BUFFER_SIZE = 1 << 20;

    /**
     * The maximum number of DATA packet retransmissions, after which the
     * connection should be closed.
     */
    static final int MAX_RETRANSMISSIONS = 20;

    interface RttEstimator
    {
        void update(long sampleRttNanos);
        long computeTimeoutNanos();
    }

    /**
     * Factory of round-trip time estimator objects, used to compute the delay
     * for unacknowledged data retransmission.
     */
    static final Supplier< RttEstimator > RTT_ESTIMATOR =
        () -> new RttEstimator()
        {
            private static final double ALPHA = 0.125;
            private static final double BETA = 0.25;

            private double estimatedRtt = 0;
            private double devRtt = 15_000_000; // 15 milliseconds

            @Override
            public void update(long sampleRttNanos)
            {
                final var sampleRtt = (double) sampleRttNanos;

                this.estimatedRtt =
                    (1 - ALPHA) * this.estimatedRtt
                    + ALPHA * sampleRtt;

                this.devRtt =
                    (1 - BETA) * this.devRtt +
                    + BETA * Math.abs(sampleRtt - this.estimatedRtt);
            }

            @Override
            public long computeTimeoutNanos()
            {
                return (long) (this.estimatedRtt + 4 * this.devRtt);
            }
        };

    // connection termination

    /**
     * How many times a DISC packet should be sent if no corresponding DISC-ACK
     * packet is received.
     */
    static final int MAX_DISCONNECT_ATTEMPTS = 3;

    /**
     * How many milliseconds to wait before resending a DISC packet if no
     * corresponding DISC-ACK packet is received.
     */
    static final int DISCONNECT_RESPONSE_TIMEOUT = 200;

    // No point in ever instantiating this class.
    private Config()
    {
    }
}

/* -------------------------------------------------------------------------- */
