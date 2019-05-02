/* -------------------------------------------------------------------------- */

package fileshare.transport;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
enum SegmentType
{
    /**
     * TODO: document
     */
    SYN(0),

    /**
     * TODO: document
     */
    SYN_ACK(1),

    /**
     * TODO: document
     */
    SYN_ACK_ACK(2),

    /**
     * TODO: document
     */
    DATA(3),

    /**
     * TODO: document
     */
    DATA_ACK(4),

    /**
     * TODO: document
     */
    DATA_NEGACK(5),

    /**
     * TODO: document
     */
    FIN(6),

    /**
     * TODO: document
     */
    FIN_ACK(7);

    private final int number;

    SegmentType(int number)
    {
        this.number = number;
    }

    public static SegmentType fromNumber(int number)
    {
        switch (number)
        {
            case 0: return SegmentType.SYN;
            case 1: return SegmentType.SYN_ACK;
            case 2: return SegmentType.SYN_ACK_ACK;
            case 3: return SegmentType.DATA;
            case 4: return SegmentType.DATA_ACK;
            case 5: return SegmentType.DATA_NEGACK;
            case 6: return SegmentType.FIN;
            case 7: return SegmentType.FIN_ACK;

            default:
                throw new IllegalArgumentException(String.format(
                    "Invalid segment type number %s.", number
                    ));
        }
    }

    public int toNumber()
    {
        return this.number;
    }
}

/* -------------------------------------------------------------------------- */
