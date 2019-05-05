/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.transport.Endpoint;

import java.util.Objects;
import java.util.Optional;
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

    private long startNanos;
    private final AtomicLong transferredBytes;

    private long markNanos;
    private long markTransferredBytes;

    private long endNanos;

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
     * Allowed on STARTING, RUNNING, and SUCCEEDED.
     *
     * @return TODO: document
     */
    public synchronized int getProgressPercentage()
    {
        // TODO: implement

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
     * The overall throughput since the last time this method was invoked (and
     * until the job finished, if that happened).
     *
     * @return TODO: document
     */
    public synchronized long getImmediateThroughput()
    {
        // TODO: implement
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
        // TODO: implement
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
     * Allowed on RUNNING and SUCCEEDED, and leads to SUCCEEDED.
     */
    public synchronized void succeed()
    {
        if (this.phase != Phase.RUNNING && this.phase != Phase.SUCCEEDED)
            throw new IllegalStateException("job has not yet started");

        if (this.phase == Phase.RUNNING)
        {
            // TODO: implement

            this.phase = Phase.SUCCEEDED;
        }
    }

    /**
     * TODO: document
     *
     * Allowed on RUNNING and FAILED, and leads to FAILED.
     *
     * Does not replace previous error message, if any.
     *
     * @param errorMessage TODO: document
     */
    public synchronized void fail(String errorMessage)
    {
        if (this.phase != Phase.RUNNING && this.phase != Phase.FAILED)
            throw new IllegalStateException();

        if (this.phase == Phase.RUNNING)
        {
            // TODO: implement

            this.phase = Phase.FAILED;
        }
    }

    public synchronized void fail(Endpoint peerEndpoint, String errorMessage)
    {
        
    }

    @Override
    public synchronized JobState clone()
    {

    }












    /**
     * TODO: document
     *
     * @param job TODO: document
     * @param totalBytes TODO: document
     * @param transferredBytes TODO: document
     * @param throughput TODO: document
     * @param errorMessage TODO: document
     *
     * @throws NullPointerException TODO: document
     * @throws IllegalArgumentException TODO: document
     */
    public JobState(
        Job job,
        Optional< Long > totalBytes,
        long transferredBytes,
        Optional< Long > throughput,
        Optional< String > errorMessage
        )
    {
        // validate arguments

        Objects.requireNonNull(totalBytes);
        Objects.requireNonNull(throughput);
        Objects.requireNonNull(errorMessage);

        if (totalBytes.isEmpty())
        {
            if (transferredBytes != 0)
                throw new IllegalArgumentException("TODO: write");

            if (throughput.isPresent())
                throw new IllegalArgumentException("TODO: write");
        }
        else
        {
            if (totalBytes.get() < 0)
                throw new IllegalArgumentException("TODO: write");

            if (transferredBytes < 0)
                throw new IllegalArgumentException("TODO: write");

            if (transferredBytes > totalBytes.get())
                throw new IllegalArgumentException("TODO: write");

            if (throughput.isPresent() && throughput.get() < 0)
                throw new IllegalArgumentException("TODO: write");
        }

        // initialize instance

        this.job              = job;

        this.totalBytes       = totalBytes;
        this.transferredBytes = transferredBytes;
        this.throughput       = throughput;

        this.errorMessage     = errorMessage;
    }

    /**
     * TODO: document
     *
     * Is non-negative.
     *
     * @return TODO: document
     */
    public Optional< Long > getTotalBytes()
    {
        return this.totalBytes;
    }

    /**
     * TODO: document
     *
     * @param totalBytes TODO: document
     */
    public void setTotalBytes(Optional< Long > totalBytes)
    {
        this.totalBytes = totalBytes;
    }

    /**
     * TODO: document
     *
     * If total bytes is empty, this is 0.
     *
     * Otherwise is non-negative and lower than or equal to total bytes.
     *
     * @return TODO: document
     */
    public long getTransferredBytes()
    {
        return this.transferredBytes;
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
     * Not a perfect representation of the throughput, and the interval to which
     * the throughput is relative is left unspecified.
     *
     * In bytes per second.
     *
     * @return TODO: document
     */
    public Optional< Long > getThroughput()
    {
        return this.throughput;
    }

    /**
     * TODO: document
     *
     * @param throughput TODO: document
     */
    public void setThroughput(Optional< Long > throughput)
    {
        this.throughput = throughput;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Optional< String > getErrorMessage()
    {
        return this.errorMessage;
    }

    /**
     * TODO: document
     *
     * @param errorMessage TODO: document
     */
    public void setErrorMessage(Optional< String > errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    /**
     * TODO: document
     *
     * Always between 0 and 100, inclusive. Also 0 if totalBytes is empty. Only
     * 100 if the transfer is complete.
     *
     * @return TODO: document
     */
}

/* -------------------------------------------------------------------------- */
