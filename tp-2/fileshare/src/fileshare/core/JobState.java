/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class JobState
{
    private Job job;
    private long totalBytes;
    private long tranferredBytes;
    private String errorMessage;

    /**
     * TODO: document
     *
     * @param job TODO: document
     * @param totalBytes TODO: document
     * @param tranferredBytes TODO: document
     * @param errorMessage TODO: document
     */
    public JobState(
        Job job,
        long totalBytes,
        long tranferredBytes,
        String errorMessage
        )
    {
        if (totalBytes < 0)
            throw new IllegalArgumentException("TODO: write");

        if (tranferredBytes < 0)
            throw new IllegalArgumentException("TODO: write");

        if (tranferredBytes > totalBytes)
            throw new IllegalArgumentException("TODO: write");

        this.job             = Objects.requireNonNull(job);
        this.totalBytes      = totalBytes;
        this.tranferredBytes = tranferredBytes;
        this.errorMessage    = errorMessage;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Job getJob()
    {
        return this.job;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public long getTotalBytes()
    {
        return this.totalBytes;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public long getTranferredBytes()
    {
        return this.tranferredBytes;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public String getErrorMessage()
    {
        return this.errorMessage;
    }
}

/* -------------------------------------------------------------------------- */
