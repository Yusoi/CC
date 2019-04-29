/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.util.Optional;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public abstract class JobState
{
    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public abstract Job getJob();

    /**
     * TODO: document
     *
     * Is non-negative.
     *
     * @return TODO: document
     */
    public abstract Optional< Long > getTotalBytes();

    /**
     * TODO: document
     *
     * If total bytes is empty, this is 0.
     *
     * Otherwise is non-negative and lower than or equal to total bytes.
     *
     * @return TODO: document
     */
    public abstract long getTransferredBytes();

    /**
     * TODO: document
     *
     * Not a perfect representation of the throughput, and the interval to which
     * the throughput is relative is left unspecified.
     *
     * @return TODO: document
     */
    public abstract Optional< Long > getThroughput();

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public abstract Optional< String > getErrorMessage();

    /**
     * TODO: document
     *
     * Always between 0 and 100, inclusive. Also 0 if totalBytes is empty.
     *
     * @return TODO: document
     */
    public int getTransferredPercentage()
    {
        if (this.getTotalBytes().isEmpty())
        {
            return 0;
        }
        else if (this.getTotalBytes().get() == 0)
        {
            return 100;
        }
        else
        {
            return 100 * (int)Math.floorDiv(
                this.getTransferredBytes(),
                this.getTotalBytes().get()
                );
        }
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public boolean hasFinished()
    {
        return
            (this.getTotalBytes().isPresent() &&
            this.getTransferredBytes() == this.getTotalBytes().get()) ||
            this.getErrorMessage().isPresent();
    }
}

/* -------------------------------------------------------------------------- */
