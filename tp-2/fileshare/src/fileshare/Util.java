/* -------------------------------------------------------------------------- */

package fileshare;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;
import java.util.function.BooleanSupplier;
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
     * @throws RuntimeException if {@code message} is not empty
     */
    public static void throwIfNotEmpty(String message)
    {
        Objects.requireNonNull(message);

        if (!message.isEmpty())
            throw new RuntimeException(message);
    }

    /**
     * Copies bytes between two possibly circular arrays.
     *
     * @param from the source array
     * @param fromIndex the starting index in the source array
     * @param to the destination array
     * @param toIndex the starting index in the destination array
     * @param size the number of bytes to be copied
     */
    public static void circularCopy(
        byte[] from,
        int fromIndex,
        byte[] to,
        int toIndex,
        int size
    )
    {
        if (size > from.length)
            throw new IllegalArgumentException();

        if (size > to.length)
            throw new IllegalArgumentException();

        for (int i = 0; i < size; ++i)
            to[(toIndex + i) % to.length] = from[(fromIndex + i) % from.length];
    }

    /**
     * Waits until a certain condition becomes true, relying on the {@link
     * Object#wait()} method of the specified object.
     *
     * @param monitor the object on which to invoke the {@link Object#wait()}
     *        method
     * @param condition the condition to wait to become true
     */
    public static void waitUntil(
        Object monitor,
        BooleanSupplier condition
    )
    {
        while (!condition.getAsBoolean())
        {
            try
            {
                monitor.wait();
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
                Math.min(inputSize - transferredTotal, 1L << 16),
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
                Math.min(outputSize - transferredTotal, 1L << 16)
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
