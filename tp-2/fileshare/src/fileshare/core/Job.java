/* -------------------------------------------------------------------------- */

package fileshare.core;

import fileshare.transport.Endpoint;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * Describes a job.
 */
public class Job
{
    private final JobType type;
    private final List< Endpoint > remoteEndpoints;
    private final Path localFilePath;
    private final Path remoteFilePath;

    /**
     * Constructs an instance of {@link Job}.
     *
     * The manner in which the remotes specified by {@code remoteEndpoints} are
     * used depends on the job's type:
     *
     * <ul>
     *   <li>
     *     If {@code type} is {@link JobType#GET}, the job will download a file
     *     by obtaining separate sections from each of the specified remotes.
     *   </li>
     *   <li>
     *     If {@code type} is {@link JobType#PUT}, the job will upload a file to
     *     each of the specified remotes.
     *   </li>
     * </ul>
     *
     * @param type the job's type
     * @param remoteEndpoints the remote endpoints to be used by the job
     * @param localFilePath the local path of the file
     * @param remoteFilePath the path of the file in the remotes
     *
     * @throws NullPointerException if type, remoteEndpoints, localFilePath, or
     *         remoteFilePath are null
     * @throws IllegalArgumentException if remoteEndpoints is empty
     */
    public Job(
        JobType type,
        List< Endpoint > remoteEndpoints,
        Path localFilePath,
        Path remoteFilePath
        )
    {
        // validate arguments

        Objects.requireNonNull(
            type,
            "type must not be null"
            );

        Objects.requireNonNull(
            remoteEndpoints,
            "remoteEndpoints must not be null"
            );

        Objects.requireNonNull(
            localFilePath,
            "localFilePath must not be null"
            );

        Objects.requireNonNull(
            remoteFilePath,
            "remoteFilePath must not be null"
            );

        if (remoteEndpoints.size() == 0)
        {
            throw new IllegalArgumentException(
                "remoteEndpoints must not be empty"
                );
        }

        // initialize instance

        this.type            = Objects.requireNonNull(type);
        this.remoteEndpoints = new ArrayList<>(remoteEndpoints);
        this.localFilePath   = Objects.requireNonNull(localFilePath);
        this.remoteFilePath  = Objects.requireNonNull(remoteFilePath);
    }

    /**
     * Returns the job type.
     *
     * @return the job type
     */
    public JobType getType()
    {
        return this.type;
    }

    /**
     * Returns an unmodifiable list of the remotes to be used by this job.
     *
     * @return TODO: document
     */
    public List< Endpoint > getPeerEndpoints()
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
