/* -------------------------------------------------------------------------- */

package fileshare;

import java.io.IOException;
import java.nio.ByteBuffer;
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
     * Transfers data between to channels
     *
     * @param input
     * @param output
     * @param onBytesTransferred
     * @return
     * @throws IOException
     */
    public static long transfer(
        ReadableByteChannel input,
        WritableByteChannel output,
        LongConsumer onBytesTransferred
        ) throws IOException
    {
        final int  BUFFER_SIZE   = 1  << 13;
        final long PROGRESS_SIZE = 1l << 14;

        final var buffer = ByteBuffer.allocate(BUFFER_SIZE);

        long transferredTotal         = 0;
        long transferredSinceProgress = 0;

        while (input.read(buffer) >= 0 || buffer.position() != 0)
        {
            buffer.flip();
            final int written = output.write(buffer);
            buffer.compact();

            transferredTotal         += written;
            transferredSinceProgress += written;

            if (transferredSinceProgress >= PROGRESS_SIZE)
            {
                onBytesTransferred.accept(transferredSinceProgress);
                transferredSinceProgress = 0;
            }
        }

        if (transferredSinceProgress > 0)
            onBytesTransferred.accept(transferredSinceProgress);

        return transferredTotal;
    }

    // No point in ever instantiating this class.
    private Util()
    {
    }
}

/* -------------------------------------------------------------------------- */
