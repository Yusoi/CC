
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