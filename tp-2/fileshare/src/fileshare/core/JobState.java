/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.util.Objects;
import java.util.Optional;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class JobState implements JobStateView
{
    private Job job;
    private Optional< Long > totalBytes;
    private long transferredBytes;
    private Optional< String > errorMessage;

    /**
     * TODO: document
     *
     * @param job TODO: document
     * @param totalBytes TODO: document
     * @param transferredBytes TODO: document
     * @param errorMessage TODO: document
     *
     * @throws NullPointerException if job, totalBytes, or errorMessage are null
     * @throws IllegalArgumentException if transferredBytes is negative
     * @throws IllegalArgumentException if totalBytes is non-empty and is
     *     negative or lower than transferredBytes
     */
    public JobState(
        Job job,
        Optional< Long > totalBytes,
        long transferredBytes,
        Optional< String > errorMessage
        )
    {
        // validate arguments

        Objects.requireNonNull(job         , "job must not be null"         );
        Objects.requireNonNull(totalBytes  , "totalBytes must not be null"  );
        Objects.requireNonNull(errorMessage, "errorMessage must not be null");

        if (transferredBytes < 0)
        {
            throw new IllegalArgumentException(
                "transferredBytes must be non-negative"
                );
        }

        if (totalBytes.isPresent())
        {
            if (totalBytes.get() < 0)
            {
                throw new IllegalArgumentException(
                    "totalBytes must be non-negative"
                    );
            }

            if (totalBytes.get() < transferredBytes)
            {
                throw new IllegalArgumentException(
                    "transferredBytes must be less than or equal to" +
                    " totalBytes"
                    );
            }
        }

        // initialize instance

        this.job              = job;
        this.totalBytes       = totalBytes;
        this.transferredBytes = transferredBytes;
        this.errorMessage     = errorMessage;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    @Override
    public Job getJob()
    {
        return this.job;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    @Override
    public Optional< Long > getTotalBytes()
    {
        return this.totalBytes;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    @Override
    public long getTransferredBytes()
    {
        return this.transferredBytes;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    @Override
    public Optional< String > getErrorMessage()
    {
        return this.errorMessage;
    }

    /**
     * TODO: document
     *
     * @param totalBytes TODO: document
     */
    public void setTotalBytes(Optional<Long> totalBytes)
    {
        this.totalBytes = totalBytes;
    }

    /**
     * TODO: document
     *
     * @param transferredBytes TODO: document
     */
    public void setTransferredBytes(long transferredBytes)
    {
        this.transferredBytes = transferredBytes;
    }

    /**
     * TODO: document
     *
     * @param errorMessage TODO: document
     */
    public void setErrorMessage(Optional<String> errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    // /**
    //  * TODO: document
    //  *
    //  * @param totalBytes TODO: document
    //  * @return TODO: document
    //  *
    //  * @throws NullPointerException if totalBytes is null
    //  * @throws IllegalArgumentException if totalBytes is non-empty and is
    //  *     negative or lower than the return value of this.getTransferredBytes()
    //  */
    // public JobState withTotalBytes(Optional< Long > totalBytes)
    // {
    //     return new JobState(
    //         this.getJob(),
    //         totalBytes,
    //         this.getTransferredBytes(),
    //         this.getErrorMessage()
    //         );
    // }

    // /**
    //  * TODO: document
    //  *
    //  * @param transferredBytes TODO: document
    //  * @return TODO: document
    //  *
    //  * @throws IllegalArgumentException if transferredBytes is negative
    //  * @throws IllegalArgumentException if the return value of
    //  *     this.getTotalBytes() is non-empty and lower than transferredBytes
    //  */
    // public JobState withTransferredBytes(long transferredBytes)
    // {
    //     return new JobState(
    //         this.getJob(),
    //         this.getTotalBytes(),
    //         transferredBytes,
    //         this.getErrorMessage()
    //         );
    // }

    // /**
    //  * TODO: document
    //  *
    //  * @param errorMessage TODO: document
    //  * @return TODO: document
    //  *
    //  * @throws NullPointerException if errorMessage is null
    //  */
    // public JobState withErrorMessage(Optional< String > errorMessage)
    // {
    //     return new JobState(
    //         this.getJob(),
    //         this.getTotalBytes(),
    //         this.getTransferredBytes(),
    //         errorMessage
    //         );
    // }
}

/* -------------------------------------------------------------------------- */
