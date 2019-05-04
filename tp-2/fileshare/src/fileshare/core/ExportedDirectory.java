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
import java.util.Objects;

/* -------------------------------------------------------------------------- */

/**
 * Mediates access to a file system sub-tree exported by a local peer.
 *
 * This class is thread-safe.
 */
public class ExportedDirectory
{
    /**
     * A subclass of {@link RandomAccessFile} returned by {@link
     * #openFileForWriting(Path, long)} that only commits changes when
     * requested.
     */
    public abstract class TemporaryRandomAccessFile extends RandomAccessFile
    {
        private TemporaryRandomAccessFile(
            File file,
            long fileSize
            ) throws IOException
        {
            super(file, "rw");
            super.setLength(fileSize);
        }

        /**
         * Commits changes to the file and closes it.
         *
         * @throws IOException if an I/O error occurs
         */
        public abstract void commitAndClose() throws IOException;
    }

    private final Path directoryPath;
    private final Path resolvedDirectoryPath;

    private final Map< Path, Integer > fileLocks;

    /**
     * Creates an instance of {@code ExportedDirectory} corresponding to a
     * certain file system sub-tree.
     *
     * @param directoryPath a path to the root directory of the file system
     *        sub-tree to be exported
     */
    public ExportedDirectory(Path directoryPath) throws IOException
    {
        this.directoryPath = directoryPath;
        this.resolvedDirectoryPath = directoryPath.toRealPath().normalize();

        this.fileLocks = new HashMap<>();
    }

    /**
     * Returns a path to the exported file system sub-tree.
     *
     * @return a path to the exported file system sub-tree
     */
    public Path getDirectoryPath()
    {
        return this.directoryPath;
    }

    /**
     * Resolves a path relative to the exported file system sub-tree's root.
     *
     * If the path points to a file system entry that is not a regular file, a
     * {@link FileNotFoundException} exception is thrown.
     *
     * If {@code mustExist} is {@code true} and the path points to a
     * non-existing entry, a {@link FileNotFoundException} exception is thrown.
     *
     * @param path the path to be resolved
     * @return the resolved paht
     *
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code path} is absolute
     * @throws IllegalArgumentException if {@code path} resolves to outside the
     *         exported file system sub-tree
     * @throws FileNotFoundException if {@code path} points to a file system
     *         entry that is not a regular file
     * @throws FileNotFoundException if {@code mustExist} is {@code true}
     *         and {@code path} points to a non-existing entry
     * @throws IOException if an I/O error occurs
     */
    public Path resolveFilePath(Path path, boolean mustExist) throws IOException
    {
        // validate arguments

        Objects.requireNonNull(path);

        if (path.isAbsolute())
            throw new IllegalArgumentException("Path must be relative.");

        // resolve file path

        final var resolvedFilePath =
            this.resolvedDirectoryPath
            .resolve(path)
            .toRealPath()
            .normalize();

        // ensure path is bellow exported directory

        if (!resolvedFilePath.startsWith(this.resolvedDirectoryPath))
        {
            throw new IllegalArgumentException(
                "Path is outside the exported directory."
                );
        }

        // check file existence and type

        if (Files.exists(resolvedFilePath))
        {
            if (!Files.isRegularFile(resolvedFilePath))
                throw new FileNotFoundException("Not a regular file.");
        }
        else
        {
            if (mustExist)
                throw new FileNotFoundException("File does not exist");
        }

        // return resolved file path

        return resolvedFilePath;
    }

    /**
     * Opens a file with reading permissions.
     *
     * The file is locked until the returned {@link RandomAccessFile} is closed.
     *
     * This method succeeds even if the file is already open with reading
     * permissions.
     *
     * @param path a path to the file to be opened
     * @return a {@link RandomAccessFile} corresponding to the opened file
     *
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code path} is absolute
     * @throws IllegalArgumentException if {@code path} resolves to outside the
     *         exported file system sub-tree
     * @throws FileNotFoundException if {@code path} points to a file system
     *         entry that does not exist or is not a regular file
     * @throws IllegalStateException if the file is locked for writing
     * @throws IOException if an I/O error occurs
     */
    public RandomAccessFile openFileForReading(Path path) throws IOException
    {
        final var resolvedFilePath = this.resolveFilePath(path, true);

        this.lockFileAsReader(resolvedFilePath);

        try
        {
            return new RandomAccessFile(resolvedFilePath.toFile(), "r")
            {
                private boolean closed = false;

                @Override
                public void close() throws IOException
                {
                    if (!this.closed)
                    {
                        this.closed = true;

                        try
                        {
                            super.close();
                        }
                        finally
                        {
                            ExportedDirectory.this.unlockFileAsReader(
                                resolvedFilePath
                            );
                        }
                    }
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
     * Opens a file with writing permissions.
     *
     * The file is locked until the returned {@link TemporaryRandomAccessFile}
     * is closed.
     *
     * The returned {@link TemporaryRandomAccessFile} is initially empty and
     * changes made to it are only persisted in the file if {@link
     * TemporaryRandomAccessFile#commitAndClose()} is invoked.
     *
     * @param path a path to the file to be opened
     * @return a {@link TemporaryRandomAccessFile} corresponding to the opened
     *         file
     *
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code path} is absolute
     * @throws IllegalArgumentException if {@code path} resolves to outside the
     *         exported file system sub-tree
     * @throws FileNotFoundException if {@code path} points to a file system
     *         entry that does not exist or is not a regular file
     * @throws IllegalStateException if the file is locked for writing
     * @throws IOException if an I/O error occurs
     */
    public TemporaryRandomAccessFile openFileForWriting(
        Path path,
        long fileSize
        ) throws IOException
    {
        final var resolvedFilePath = this.resolveFilePath(path, false);
        Files.createDirectories(resolvedFilePath.getParent());

        this.lockFileAsWriter(resolvedFilePath);

        try
        {
            final var tempFilePath = Files.createTempFile(
                resolvedFilePath.getParent(), null, null
            );

            return this.new TemporaryRandomAccessFile(
                tempFilePath.toFile(),
                fileSize
                )
            {
                private boolean closed = false;

                @Override
                public void commitAndClose() throws IOException
                {
                    if (!this.closed)
                    {
                        this.closed = true;

                        try
                        {
                            super.close();

                            Files.move(
                                tempFilePath,
                                resolvedFilePath,
                                StandardCopyOption.REPLACE_EXISTING
                            );
                        }
                        finally
                        {
                            ExportedDirectory.this.unlockFileAsWriter(
                                resolvedFilePath
                            );
                        }
                    }
                }

                @Override
                public void close() throws IOException
                {
                    if (!this.closed)
                    {
                        this.closed = true;

                        try
                        {
                            super.close();

                            Files.delete(tempFilePath);
                        }
                        finally
                        {
                            ExportedDirectory.this.unlockFileAsWriter(
                                resolvedFilePath
                            );
                        }
                    }
                }

            };
        }
        catch (Throwable t)
        {
            this.unlockFileAsWriter(resolvedFilePath);
            throw t;
        }
    }

    private void lockFileAsReader(Path resolvedFilePath)
    {
        synchronized (this.fileLocks)
        {
            final int lockValue = this.fileLocks.getOrDefault(
                resolvedFilePath, 0
                );

            if (lockValue == -1)
                throw new IllegalStateException("already locked for writing");

            this.fileLocks.put(resolvedFilePath, lockValue + 1);
        }
    }

    private void unlockFileAsReader(Path resolvedFilePath)
    {
        synchronized (this.fileLocks)
        {
            final int lockValue = this.fileLocks.get(resolvedFilePath);

            if (lockValue == 1)
                this.fileLocks.remove(resolvedFilePath);
            else
                this.fileLocks.put(resolvedFilePath, lockValue - 1);
        }
    }

    private void lockFileAsWriter(Path resolvedFilePath)
    {
        synchronized (this.fileLocks)
        {
            if (this.fileLocks.containsKey(resolvedFilePath))
                throw new IllegalStateException("already locked");

            this.fileLocks.put(resolvedFilePath, -1);
        }
    }

    private void unlockFileAsWriter(Path resolvedFilePath)
    {
        synchronized (this.fileLocks)
        {
            this.fileLocks.remove(resolvedFilePath);
        }
    }
}

/* -------------------------------------------------------------------------- */
