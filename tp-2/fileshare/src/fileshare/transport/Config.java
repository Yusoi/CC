/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/* -------------------------------------------------------------------------- */

class Config
{
    // packet types

    public static final byte TYPE_ID_CONN            = 0;
    public static final byte TYPE_ID_CONN_REJECT     = 1;
    public static final byte TYPE_ID_CONN_ACCEPT     = 2;
    public static final byte TYPE_ID_CONN_ACCEPT_ACK = 3;
    public static final byte TYPE_ID_DATA            = 4;
    public static final byte TYPE_ID_DATA_ACK        = 5;
    public static final byte TYPE_ID_DISC            = 6;
    public static final byte TYPE_ID_DISC_ACK        = 7;

    // connection establishment

    public static final int MAX_CONNECTION_RETRIES = 5;

    /**
     * In milliseconds.
     */
    public static final int CONNECTION_RETRY_DELAY = 500;

    // connection termination

    // sizes

    public static final int MAX_PACKET_SIZE = 1000;

    // integrity

    /**
     * Factory of checksum computing objects.
     */
    public static final Supplier< Checksum > CHECKSUM = CRC32::new;
}

/* -------------------------------------------------------------------------- */
