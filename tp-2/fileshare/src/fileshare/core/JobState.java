/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.transport.Endpoint;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 *
 * This class is thread-safe.
 */
public class JobState
{
    /**
     * TODO: document
     */
    public enum Phase
    {
        /**
         * TODO: document
         */
        STARTING,

        /**
         * TODO: document
         */
        RUNNING,

        /**
         * TODO: document
         */
        SUCCEEDED,

        /**
         * TODO: document
         */
        FAILED
    }

    private final Job job;

    private Phase phase;

    private long totalBytes;
    private AtomicLong transferredBytes;

    private long startNanos;

    private long markNanos;
    private long markTransferredBytes;

    private long endNanos;
    private long endTransferredBytes;

    private String errorMessage;

    /**
     * TODO: document
     *
     * @param job TODO: document
     *
     * @throws NullPointerException if {@code job} is {@code null}
     */
    public JobState(Job job)
    {
        this.job = job;
        this.phase = Phase.STARTING;
        this.transferredBytes = new AtomicLong(0);
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
    public synchronized Phase getPhase()
    {
        return this.phase;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public boolean hasFinished()
    {
        return this.phase == Phase.SUCCEEDED || this.phase == Phase.FAILED;
    }

    /**
     * TODO: document
     *
     * Allowed on STARTING, RUNNING, and SUCCEEDED.
     *
     * @return TODO: document
     */
    public synchronized int getProgressPercentage()
    {
        switch (this.phase)
        {
            case STARTING:
                return 0;

            case RUNNING:
                return Math.min(
                    100,
                    (int)Math.floorDiv(
                        100 * this.transferredBytes.get(),
                        this.totalBytes
                    )
                );

            case SUCCEEDED:
                return 100;

            case FAILED:
                throw new IllegalStateException("job has failed");
        }

        throw new RuntimeException();
    }

    /**
     * TODO: document
     *
     * The overall throughput since the last time this method was invoked (and
     * until the job finished, if that happened).
     *
     * @return TODO: document
     */
    public synchronized long getImmediateThroughput()
    {
        // validate state

        if (this.phase != Phase.STARTING && this.phase != Phase.RUNNING)
            throw new IllegalStateException("job is not running");

        if (this.phase == Phase.STARTING)
            return 0;

        // get current time and transferred bytes

        final long transferred = this.transferredBytes.get();
        final long now = System.nanoTime();

        // compute immediate throughput

        final long immediateThroughput =
            (1_000_000_000L * (transferred - this.markTransferredBytes)) /
                (now - this.markNanos);

        // update marked time and transferred bytes

        this.markNanos = now;
        this.markTransferredBytes = transferred;

        // return immediate throughput

        return immediateThroughput;
    }

    /**
     * TODO: document
     *
     * The overall throughput since the transfer began (and until the job
     * finished, if that happened).
     *
     * @return TODO: document
     */
    public synchronized long getOverallThroughput()
    {
        if (this.phase != Phase.SUCCEEDED)
        {
            throw new IllegalStateException(
                "job has failed or has not yet finished"
            );
        }

        // compute and return overall throughput

        return
            (1_000_000_000L * this.endTransferredBytes) /
                (this.endNanos - this.startNanos);
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public synchronized String getErrorMessage()
    {
        if (this.phase != Phase.FAILED)
            throw new IllegalStateException("job has not failed");

        return this.errorMessage;
    }

    /**
     * Allowed on STARTING and leads to RUNNING.
     *
     * @param totalBytes
     */
    public synchronized void start(long totalBytes)
    {
        // validate arguments and state

        if (totalBytes < 0)
        {
            throw new IllegalArgumentException(
                "totalBytes must not be negative"
            );
        }

        if (this.phase != Phase.STARTING)
            throw new IllegalStateException("job has already started");

        // start job

        this.totalBytes = totalBytes;

        this.startNanos = System.nanoTime();

        this.markNanos = this.startNanos;
        this.markTransferredBytes = 0;

        this.phase = Phase.RUNNING;
    }

    /**
     * TODO: document
     *
     * Allowed on any phase.
     *
     * @param bytes TODO: document
     *
     * @throws IllegalArgumentException if {@code bytes} is negative
     */
    public void addToTransferredBytes(long bytes)
    {
        if (bytes < 0)
            throw new IllegalArgumentException("bytes must not be negative");

        this.transferredBytes.addAndGet(bytes);
    }

    /**
     * TODO: document
     *
     * Allowed on RUNNING, SUCCEEDED, and FAILED, and leads to SUCCEEDED.
     *
     * Does not replace previous SUCCEEDED or FAILED.
     */
    public synchronized void succeed()
    {
        if (this.phase == Phase.STARTING)
            throw new IllegalStateException("job has not yet started");

        if (this.phase == Phase.RUNNING)
        {
            this.endNanos = System.nanoTime();
            this.endTransferredBytes = this.transferredBytes.get();

            this.phase = Phase.SUCCEEDED;
        }
    }

    /**
     * TODO: document
     *
     * Same as {@code fail(null, errorMessage)}.
     *
     * @param errorMessage TODO: document
     */
    public void fail(String errorMessage)
    {
        this.fail(null, errorMessage);
    }

    /**
     * TODO: document
     *
     * Allowed on STARTING, RUNNING, and FAILED, and leads to FAILED.
     *
     * Does not replace previous error message, if any.
     *
     * @param peerEndpoint TODO: document
     * @param errorMessage TODO: document
     */
    public synchronized void fail(Endpoint peerEndpoint, String errorMessage)
    {
        if (this.phase == Phase.SUCCEEDED)
            throw new IllegalStateException("job has succeeded");

        if (this.phase != Phase.FAILED)
        {
            if (errorMessage == null)
                errorMessage = "";

            if (peerEndpoint == null)
                this.errorMessage = errorMessage;
            else
                this.errorMessage = peerEndpoint.toString() + ": " + errorMessage;

            this.phase = Phase.FAILED;
        }
    }

    @Override
    public synchronized JobState clone()
    {
        final var other = new JobState(job);

        other.phase = this.phase;

        other.totalBytes = this.totalBytes;
        other.transferredBytes = new AtomicLong(this.transferredBytes.get());

        other.startNanos = this.startNanos;

        other.markNanos = this.markNanos;
        other.markTransferredBytes = this.markTransferredBytes;

        other.endNanos = this.endNanos;
        other.endTransferredBytes = this.endTransferredBytes;

        other.errorMessage = this.errorMessage;

        return other;
    }
}

/* -------------------------------------------------------------------------- */
