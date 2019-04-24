/* -------------------------------------------------------------------------- */

package fileshare.core;

/* -------------------------------------------------------------------------- */

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * TODO: document
 *
 * This class is thread-safe: its methods can be invoked concurrently.
 *
 * Symbolic links are not supported.
 */
public class ExportDir
{
    private final Path path;
    private final Map< Path, Integer > fileLocks;

    /**
     * TODO: document
     *
     * @param path TODO: document
     */
    public ExportDir(Path path)
    {
        this.path      = path;
        this.fileLocks = new HashMap<>();
    }

    public Path getPath() {
        return path;
    }

    /**
     * TODO: document
     *
     * @param filePath TODO: document
     * @return TODO: document
     */
    public void readFile(Path filePath, OutputStream outputStream)
    {
        this.lockFileAsReader(filePath);

        try
        {

        }
        finally
        {
            this.unlockFileAsReader(filePath);
        }
    }

    /**
     * TODO: document
     *
     * Reads all remaining data in stream and creates a file (overwriting if it
     * already exists) with that data as its content.
     *
     * @param filePath TODO: document
     * @return TODO: document
     */
    public void writeFile(
        Path filePath,
        InputStream inputStream,
        long fileSize,
        LongConsumer onBytesTransferred
        )
    {
        this.lockFileAsWriter(filePath);

        try
        {

        }
        finally
        {
            this.unlockFileAsWriter(filePath);
        }
    }

    private void lockFileAsReader(Path canonicalFilePath)
    {
        synchronized (this.fileLocks)
        {
            final int lockValue = this.fileLocks.getOrDefault(
                canonicalFilePath, 0
                );

            if (lockValue == -1)
                throw new IllegalStateException("already locked for writing");

            this.fileLocks.put(canonicalFilePath, lockValue + 1);
        }
    }

    private void unlockFileAsReader(Path canonicalFilePath)
    {
        synchronized (this.fileLocks)
        {
            final int lockValue = this.fileLocks.get(canonicalFilePath);

            if (lockValue == 1)
                this.fileLocks.remove(canonicalFilePath);
            else
                this.fileLocks.put(canonicalFilePath, lockValue - 1);
        }
    }

    private void lockFileAsWriter(Path canonicalFilePath)
    {
        synchronized (this.fileLocks)
        {
            if (this.fileLocks.containsKey(canonicalFilePath))
                throw new IllegalStateException("already locked");

            this.fileLocks.put(canonicalFilePath, -1);
        }
    }

    private void unlockFileAsWriter(Path canonicalFilePath)
    {
        synchronized (this.fileLocks)
        {
            this.fileLocks.remove(canonicalFilePath);
        }
    }
}

/* -------------------------------------------------------------------------- */
