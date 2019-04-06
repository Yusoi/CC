/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public class Job
{
    private JobType type;
    private ArrayList< InetSocketAddress > remoteEndpoints;
    private Path localFilePath;
    private Path remoteFilePath;

    /**
     * TODO: document
     *
     * @param type TODO: document
     * @param remoteEndpoints TODO: document
     * @param localFilePath TODO: document
     * @param remoteFilePath TODO: document
     */
    public Job(
        JobType type,
        List< InetSocketAddress > remoteEndpoints,
        Path localFilePath,
        Path remoteFilePath
        )
    {
        Objects.requireNonNull(remoteEndpoints);

        if (remoteEndpoints.size() == 0)
            throw new IllegalArgumentException("TODO: write");

        this.type            = Objects.requireNonNull(type);
        this.remoteEndpoints = new ArrayList<>(remoteEndpoints);
        this.localFilePath   = Objects.requireNonNull(localFilePath);
        this.remoteFilePath  = Objects.requireNonNull(remoteFilePath);
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public JobType getType()
    {
        return this.type;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public List< InetSocketAddress > getRemoteEndpoints()
    {
        return Collections.unmodifiableList(this.remoteEndpoints);
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Path getLocalFilePath()
    {
        return this.localFilePath;
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Path getRemoteFilePath()
    {
        return this.remoteFilePath;
    }
}

/* -------------------------------------------------------------------------- */
