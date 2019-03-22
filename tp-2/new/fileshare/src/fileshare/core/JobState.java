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
    private long totalFileBytes;
    private long tranferredFileBytes;
    private String errorMessage;

    /**
     * TODO: document
     *
     * @param job TODO: document
     * @param totalFileBytes TODO: document
     * @param tranferredFileBytes TODO: document
     * @param errorMessage TODO: document
     */
    public JobState(
        Job job,
        long totalFileBytes,
        long tranferredFileBytes,
        String errorMessage
        )
    {
        if (totalFileBytes < 0)
            throw new IllegalArgumentException("TODO: write");

        if (tranferredFileBytes < 0)
            throw new IllegalArgumentException("TODO: write");

        this.job                 = Objects.requireNonNull(job);
        this.totalFileBytes      = totalFileBytes;
        this.tranferredFileBytes = tranferredFileBytes;
        this.errorMessage        = errorMessage;
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
    public long getTotalFileBytes()
    {
        return this.totalFileBytes;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public long getTranferredFileBytes()
    {
        return this.tranferredFileBytes;
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
