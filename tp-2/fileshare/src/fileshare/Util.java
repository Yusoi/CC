/* -------------------------------------------------------------------------- */

package fileshare;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;
import java.util.function.LongConsumer;

/* -------------------------------------------------------------------------- */

/**
 * Miscellaneous utilities.
 */
public final class Util
{
    /**
     * Throws a {@link RuntimeException} with the specified string as its
     * message, if the string is not empty.
     *
     * @param message the exception message
     *
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws RuntimeException if message is not empty
     */
    public static void throwIfNotEmpty(String message)
    {
        Objects.requireNonNull(message);

        if (!message.isEmpty())
            throw new RuntimeException(message);
    }

    /**
     * Repeatedly invokes {@code thread.join()} until it succeeds without
     * raising {@link InterruptedException}.
     *
     * @param thread the thread to be joined
     *
     * @throws NullPointerException if {@code thread} is null
     */
    public static void uninterruptibleJoin(Thread thread)
    {
        Objects.requireNonNull(thread);

        while (true)
        {
            try
            {
                thread.join();
                break;
            }
            catch (InterruptedException ignored)
            {
            }
        }
    }

    /**
     * Invokes {@code Thread.sleep(milliseconds)}, returning immediately if
     * {@code InterruptedException} is thrown.
     *
     * @param milliseconds the length of time to sleep in milliseconds
     *
     * @throws IllegalArgumentException if {@code milliseconds} is negative
     */
    public static void sleepUntilElapsedOrInterrupted(long milliseconds)
    {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch (InterruptedException ignored)
        {
        }
    }

    /**
     * Transfers data from a file channel to a writable channel.
     *
     * If {@code onBytesTransferred} is not {@code null}, it is invoked
     * periodically with the amount of bytes transferred since the last time it
     * was invoked.
     *
     * @param input the source file channel channel
     * @param inputPosition the offset in the file at which to read the data
     * @param inputSize the number of bytes to be transferred
     * @param output the destination writable channel
     * @param onBytesTransferred callback invoked when bytes are transferred
     *
     * @throws NullPointerException if {@code input} or {@code output} are
     *         {@code null}
     * @throws IOException if an I/O error occurs
     */
    public static void transferFromFile(
        FileChannel input,
        long inputPosition,
        long inputSize,
        WritableByteChannel output,
        LongConsumer onBytesTransferred
    ) throws IOException
    {
        Objects.requireNonNull(input);
        Objects.requireNonNull(output);

        long transferredTotal = 0;

        while (transferredTotal < inputSize)
        {
            final long transferred = input.transferTo(
                inputPosition + transferredTotal,
                Math.min(inputSize - transferredTotal, 10 * (1L << 20)),
                output
            );

            if (transferred <= 0)
            {
                throw new IOException(
                    String.format(
                        "Only transferred %d of %d bytes.",
                        transferredTotal, inputSize
                    )
                );
            }

            transferredTotal += transferred;

            if (onBytesTransferred != null)
                onBytesTransferred.accept(transferred);
        }
    }

    /**
     * Transfers data from a readable channel to a file channel.
     *
     * If {@code onBytesTransferred} is not {@code null}, it is invoked
     * periodically with the amount of bytes transferred since the last time it
     * was invoked.
     *
     * @param input the source readable channel
     * @param output the destination file channel
     * @param outputPosition the offset in the file at which to write the data
     * @param outputSize the number of bytes to be transferred
     * @param onBytesTransferred callback invoked when bytes are transferred
     *
     * @throws NullPointerException if {@code input} or {@code output} are
     *         {@code null}
     * @throws IOException if an I/O error occurs
     */
    public static void transferToFile(
        ReadableByteChannel input,
        FileChannel output,
        long outputPosition,
        long outputSize,
        LongConsumer onBytesTransferred
    ) throws IOException
    {
        Objects.requireNonNull(input);
        Objects.requireNonNull(output);

        long transferredTotal = 0;

        while (transferredTotal < outputSize)
        {
            final long transferred = output.transferFrom(
                input,
                outputPosition + transferredTotal,
                Math.min(outputSize - transferredTotal, 10 * (1L << 20))
            );

            if (transferred <= 0)
            {
                throw new IOException(
                    String.format(
                        "Only transferred %d of %d bytes.",
                        transferredTotal, outputSize
                    )
                );
            }

            transferredTotal += transferred;

            if (onBytesTransferred != null)
                onBytesTransferred.accept(transferred);
        }
    }

    // No point in ever instantiating this class.
    private Util()
    {
    }
}

/* -------------------------------------------------------------------------- */
