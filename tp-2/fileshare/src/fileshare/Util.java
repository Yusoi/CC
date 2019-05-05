/* -------------------------------------------------------------------------- */

package fileshare;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/* -------------------------------------------------------------------------- */

/**
 * Miscellaneous utilities.
 */
public final class Util
{
    /**
     * TODO: document
     *
     * @param message TODO: document
     *
     * @throws RuntimeException TODO: document
     */
    public static void throwIfNotEmpty(String message)
    {
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
     * TODO: document
     *
     * @param input TODO: document
     * @param inputPosition TODO: document
     * @param inputSize TODO: document
     * @param output TODO: document
     * @param onBytesTransferred TODO: document
     *
     * @throws NullPointerException if {@code input} or {@code output} are
     *         {@code null}
     */
    public static void transferFromFile(
        FileChannel input,
        long inputPosition,
        long inputSize,
        WritableByteChannel output,
        LongConsumer onBytesTransferred
    ) throws IOException
    {
        if (input.transferTo(inputPosition, inputSize, output) != inputSize)
            throw new IOException(":/");

        onBytesTransferred.accept(inputSize);
    }

    /**
     * TODO: document
     *
     * @param input TODO: document
     * @param output TODO: document
     * @param outputPosition TODO: document
     * @param outputSize TODO: document
     * @param onBytesTransferred TODO: document
     *
     * @throws NullPointerException if {@code input} or {@code output} are
     *         {@code null}
     */
    public static void transferToFile(
        ReadableByteChannel input,
        FileChannel output,
        long outputPosition,
        long outputSize,
        LongConsumer onBytesTransferred
    ) throws IOException
    {
        if (output.transferFrom(input, outputPosition, outputSize) != outputSize)
            throw new IOException(":/");

        onBytesTransferred.accept(outputSize);
    }











    /**
     * Transfers data from one channel to another.
     *
     * onBytesTransferred is always called at least once.
     *
     * The final call to onBytesTransferred gives the throughput over the whole
     * transfer (and may report 0 bytes).
     *
     * @param input
     * @param output
     * @param onBytesTransferred
     * @return
     *
     * @throws IOException
     */
    public static long transfer(
        ReadableByteChannel input,
        WritableByteChannel output,
        BiConsumer< Long, Long > onBytesTransferred
        ) throws IOException
    {
        final int  BUFFER_SIZE   = 1  << 13;
        final long PROGRESS_SIZE = 1L << 14;

        final var buffer = ByteBuffer.allocate(BUFFER_SIZE);

        long transferredTotal         = 0;
        long transferredSinceProgress = 0;

        final long startNanos  = System.nanoTime();
        long lastProgressNanos = startNanos;

        while (input.read(buffer) >= 0 || buffer.position() != 0)
        {
            buffer.flip();
            final int written = output.write(buffer);
            buffer.compact();

            transferredTotal         += written;
            transferredSinceProgress += written;

            if (transferredSinceProgress >= PROGRESS_SIZE)
            {
                final long nowNanos = System.nanoTime();

                onBytesTransferred.accept(
                    transferredSinceProgress,
                    (long) (transferredSinceProgress / ((nowNanos - lastProgressNanos) / 1_000_000_000d))
                    );

                transferredSinceProgress = 0;
                lastProgressNanos        = nowNanos;
            }
        }

        final long endNanos = System.nanoTime();

        if (transferredSinceProgress > 0)
        {
            onBytesTransferred.accept(
                transferredSinceProgress,
                (long) (transferredSinceProgress / ((endNanos - lastProgressNanos) / 1_000_000_000d))
                );
        }

        onBytesTransferred.accept(
            0L,
            (long) (transferredTotal / ((endNanos - startNanos) / 1_000_000_000d))
            );

        return transferredTotal;
    }

    // No point in ever instantiating this class.
    private Util()
    {
    }
}

/* -------------------------------------------------------------------------- */
