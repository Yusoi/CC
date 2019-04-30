/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

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
    /**
     * TODO: document
     */
    public abstract class RandomAccessFileForWriting extends RandomAccessFile
    {
        private RandomAccessFileForWriting(
            File file,
            long fileSize
            ) throws IOException
        {
            super(file, "rw");
            super.setLength(fileSize);
        }

        /**
         * TODO: document
         */
        public abstract void commitAndClose() throws IOException;
    }

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
        this.fileLocks     = new HashMap<>();
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
     *
     * @throws IOException TODO: document
     */
    public RandomAccessFile openFileForReading(Path filePath) throws IOException
    {
        final var resolvedFilePath = this.resolveFilePath(filePath, true);

        this.lockFileAsReader(resolvedFilePath);

        try
        {
            return new RandomAccessFile(resolvedFilePath.toFile(), "r")
            {
                @Override
                public void close() throws IOException
                {
                    super.close();

                    ExportedDirectory.this.unlockFileAsReader(resolvedFilePath);
                }
            };
        }
        catch (Throwable t)
        {
            this.unlockFileAsReader(resolvedFilePath);
            throw t;
        }
    }

    /**
     * TODO: document
     *
     * The file is locked until the returned stream is closed.
     *
     * Must call commitOnExit() on the returned stream for changes to take
     * effect.
     *
     * @param filePath TODO: document
     * @param fileSize TODO: document
     * @return TODO: document
     *
     * @throws IOException TODO: document
     */
    public RandomAccessFileForWriting openFileForWriting(
        Path filePath,
        long fileSize
        ) throws IOException
    {
        final var resolvedFilePath = this.resolveFilePath(filePath, false);
        Files.createDirectories(resolvedFilePath.getParent());

        this.lockFileAsWriter(resolvedFilePath);

        try
        {
            final var tempFilePath = Files.createTempFile(
                resolvedFilePath.getParent(), null, null
            );

            return this.new RandomAccessFileForWriting(
                tempFilePath.toFile(),
                fileSize
                )
            {
                @Override
                public void commitAndClose() throws IOException
                {
                    super.close();

                    Files.move(
                        tempFilePath,
                        resolvedFilePath,
                        StandardCopyOption.REPLACE_EXISTING
                    );

                    ExportedDirectory.this.unlockFileAsWriter(resolvedFilePath);
                }

                @Override
                public void close() throws IOException
                {
                    super.close();

                    Files.delete(tempFilePath);

                    ExportedDirectory.this.unlockFileAsWriter(resolvedFilePath);
                }

            };
        }
        catch (Throwable t)
        {
            this.unlockFileAsWriter(resolvedFilePath);
            throw t;
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
