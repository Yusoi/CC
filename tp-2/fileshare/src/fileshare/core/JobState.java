/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.transport.Endpoint;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/* -------------------------------------------------------------------------- */

/**
 * Represents the state of an ongoing job.
 *
 * This class is thread-safe.
 */
public class JobState
{
    /**
     * Defines the possible phases of a job.
     */
    public enum Phase
    {
        /**
         * Data transfer has not yet begun.
         */
        STARTING,

        /**
         * Data transfer is in progress.
         */
        RUNNING,

        /**
         * The job finished successfully.
         */
        SUCCEEDED,

        /**
         * The job failed.
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
     * Creates a {@code JobState} in the {@link Phase#STARTING} phase.
     *
     * @param job the job whose state the created {@code JobState} will
     *        represent
     *
     * @throws NullPointerException if {@code job} is {@code null}
     */
    public JobState(Job job)
    {
        // validate arguments

        Objects.requireNonNull(job);

        // initialize instance

        this.job = job;
        this.phase = Phase.STARTING;
        this.transferredBytes = new AtomicLong(0);
    }

    /**
     * Returns the job whose state this {@code JobState} represents.
     *
     * @return the job whose state this {@code JobState} represents
     */
    public Job getJob()
    {
        return this.job;
    }

    /**
     * Returns the phase in which the job currently is.
     *
     * @return the phase in which the job currently is
     */
    public synchronized Phase getPhase()
    {
        return this.phase;
    }

    /**
     * Checks whether the job has finished (successfully or not).
     *
     * A job has finished if its phase is either {@link Phase#SUCCEEDED} or
     * {@link Phase#FAILED}.
     *
     * @return whether the job has finished
     */
    public boolean hasFinished()
    {
        return this.phase == Phase.SUCCEEDED || this.phase == Phase.FAILED;
    }

    /**
     * Returns the percentage of data transferred so far, relative to the total
     * amount of data to be transferred.
     *
     * @return the percentage of data transferred so far
     *
     * @throws IllegalStateException if the job's current phase is {@link
     *         Phase#FAILED}
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
     * Computes and returns the throughput attained by the data transfer since
     * the last time this method was invoked (or since the data transfer began,
     * if this method was not yet invoked).
     *
     * The overall throughput since the last time this method was invoked (and
     * until the job finished, if that happened).
     *
     * @return the data transfer throughput as defined above
     *
     * @throws IllegalStateException if the job's current phase is not {@link
     *         Phase#STARTING} or {@link Phase#RUNNING}
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
     * Computes and returns the overall throughput attained by the data
     * transfer.
     *
     * @return the overall data transfer throughput
     *
     * @throws IllegalStateException if the job's current phase is not {@link
     *         Phase#SUCCEEDED}
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
     * Returns a message describing the error that caused the job to fail.
     *
     * @return a message describing the error that caused the job to fail
     *
     * @throws IllegalStateException if the job's current phase is not {@link
     *         Phase#FAILED}
     */
    public synchronized String getErrorMessage()
    {
        if (this.phase != Phase.FAILED)
            throw new IllegalStateException("job has not failed");

        return this.errorMessage;
    }

    /**
     * Sets the job's phase to {@link Phase#RUNNING}, indicating that the data
     * transfer has begun.
     *
     * @param totalBytes the total number of bytes to be transferred
     *
     * @throws IllegalStateException if the job's current phase is not {@link
     *         Phase#STARTING}
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
     * Adds the specified value to the amount of bytes already transferred.
     *
     * @param bytes the value by which to increase the amount of bytes already
     *        transferred
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
     * Sets the job's phase to {@link Phase#SUCCEEDED}, indicating that the data
     * transfer has finished successfully.
     *
     * If the job's phase is {@link Phase#SUCCEEDED} or {@link Phase#FAILED},
     * this method has no effect.
     *
     * @throws IllegalStateException if the job's current phase is {@link
     *         Phase#STARTING}
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
     * Sets the job's phase to {@link Phase#FAILED}, indicating that the data
     * transfer has failed.
     *
     * If the job's phase is {@link Phase#FAILED}, this method has no effect.
     *
     * @param peerEndpoint the endpoint of the peer that caused the error, or
     *        {@code null} if not applicable
     * @param errorMessage a message describing the error that caused the job to
     *        fail, or {@code null} if no such message is available
     *
     * @throws IllegalStateException if the job's current phase is {@link
     *         Phase#SUCCEEDED}
     */
    public synchronized void fail(Endpoint peerEndpoint, String errorMessage)
    {
        if (this.phase == Phase.SUCCEEDED)
            throw new IllegalStateException("job has succeeded");

        if (this.phase != Phase.FAILED)
        {
            final String prefix =
                (peerEndpoint == null) ?
                    "" :
                    peerEndpoint.toString() + ": ";

            final String message = (errorMessage == null) ? "" : errorMessage;

            this.errorMessage = prefix + message;

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
