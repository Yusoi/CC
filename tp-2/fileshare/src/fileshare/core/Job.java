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
    private final List< Endpoint > peerEndpoints;
    private final Path localFilePath;
    private final Path remoteFilePath;

    /**
     * Constructs an instance of {@link Job}.
     *
     * The manner in which the remotes specified by {@code peerEndpoints} are
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
     * @param type the type of the job
     * @param peerEndpoints the endpoints of the peers to be used by the job
     * @param localFilePath the local path of the job's file
     * @param remoteFilePath the path of the job's file in the peers
     *
     * @throws NullPointerException if {@code type}, {@code peerEndpoints},
     *         {@code localFilePath}, or {@code remoteFilePath} are {@code null}
     * @throws IllegalArgumentException if {@code peerEndpoints} is empty
     */
    public Job(
        JobType type,
        List< Endpoint > peerEndpoints,
        Path localFilePath,
        Path remoteFilePath
        )
    {
        // validate arguments

        Objects.requireNonNull(type);
        Objects.requireNonNull(peerEndpoints);
        Objects.requireNonNull(localFilePath);
        Objects.requireNonNull(remoteFilePath);

        if (peerEndpoints.size() == 0)
        {
            throw new IllegalArgumentException(
                "peerEndpoints must not be empty"
                );
        }

        // initialize instance

        this.type = Objects.requireNonNull(type);
        this.peerEndpoints = new ArrayList<>(peerEndpoints);
        this.localFilePath = Objects.requireNonNull(localFilePath);
        this.remoteFilePath = Objects.requireNonNull(remoteFilePath);
    }

    /**
     * Returns the type of this job.
     *
     * @return the type of this job
     */
    public JobType getType()
    {
        return this.type;
    }

    /**
     * Returns a list of the endpoints of the peers to be used by this job.
     *
     * The returned list is unmodifiable.
     *
     * @return a list of the endpoints of the peers to be used by this job
     */
    public List< Endpoint > getPeerEndpoints()
    {
        return Collections.unmodifiableList(this.peerEndpoints);
    }

    /**
     * Returns a path to this job's local file.
     *
     * @return a path to this job's local file
     */
    public Path getLocalFilePath()
    {
        return this.localFilePath;
    }

    /**
     * Returns a path to this job's file in the peers.
     *
     * @return a path to this job's file in the peers
     */
    public Path getRemoteFilePath()
    {
        return this.remoteFilePath;
    }
}

/* -------------------------------------------------------------------------- */
