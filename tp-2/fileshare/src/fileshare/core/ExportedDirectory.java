/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 *
 * This class is thread-safe: its methods can be invoked concurrently.
 *
 * The behavior of this class' methods in face of symbolic links under the
 * exported directory is unspecified.
 */
public class ExportedDirectory
{
    private final Path directoryPath;

    private final Map< Path, Integer > fileLocks;

    /**
     * TODO: document
     *
     * @param directoryPath TODO: document
     */
    public ExportedDirectory(Path directoryPath)
    {
        this.directoryPath = directoryPath;

        this.fileLocks = new HashMap<>();
    }

    /**
     * TODO: document
     *
     * @return TODO: document
     */
    public Path getDirectoryPath()
    {
        return this.directoryPath;
    }

    /**
     * TODO: document
     *
     * If fileMustExist, throws a FileNotFoundException if the file pointed to
     * by the path doesn't exist; otherwise, only the file's parent directory
     * must exist.
     *
     * @param filePath TODO: document
     * @return the file's size
     */
    public Path resolveFilePath(
        Path filePath,
        boolean fileMustExist
        ) throws IOException
    {
        // validate arguments

        if (filePath.isAbsolute())
            throw new IllegalArgumentException("filePath must be relative");

        // resolve file path

        final var resolvedFilePath =
            this.directoryPath
            .resolve(filePath)
            .toRealPath()
            .normalize();

        // check file existence and type

        if (Files.exists(resolvedFilePath))
        {
            if (!Files.isRegularFile(resolvedFilePath))
                throw new FileNotFoundException("file is not a regular file");
        }
        else
        {
            if (fileMustExist)
                throw new FileNotFoundException("file does not exist");
        }

        // return resolved file path

        return resolvedFilePath;
    }


    /**
     * TODO: document
     *
     * The file is locked until the returned stream is closed.
     *
     * @param filePath TODO: document
     * @return TODO: document
     */
    public RandomAccessFile openFileForReading(Path filePath)
    {

    }

    public RandomAccessFile openFileForWriting(Path filePath, long fileSize)
    {

    }




    /**
     * TODO: document
     *
     * Writes the file to outputStream
     *
     * @param filePath TODO: document
     * @return the file's size
     */
    public void readFile(
        Path filePath,
        OutputStream toStream,
        LongConsumer onBytesTransferredIncreased
        ) throws IOException
    {
        final var resolvedFilePath = this.resolveFilePath(filePath, true);

        this.lockFileAsReader(filePath);

        final long fileSize = Files.size(resolvedFilePath);

        try
        {
            final var in = new FileInputStream(filePath);
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
        InputStream fromStream,
        long fileSize,
        LongConsumer onBytesTransferredIncreased
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
