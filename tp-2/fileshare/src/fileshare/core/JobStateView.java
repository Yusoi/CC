/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.util.Optional;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public interface JobStateView
{
    /**
     * TODO: document
     *
     * @return TODO: document
     */
    Job getJob();

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    Optional< Long > getTotalBytes();

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    long getTransferredBytes();

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    Optional< String > getErrorMessage();

    /**
     * TODO: document
     *
     * Always between 0 and 100, inclusive. Also 0 if totalBytes is empty.
     *
     * @return TODO: document
     */
    default int getTransferredPercentage()
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
    default boolean hasFinished()
    {
        return
            (this.getTotalBytes().isPresent() &&
            this.getTransferredBytes() == this.getTotalBytes().get()) ||
            this.getErrorMessage().isPresent();
    }
}

/* -------------------------------------------------------------------------- */
