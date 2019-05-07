/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.OptionalInt;
import java.util.concurrent.TimeoutException;

/* -------------------------------------------------------------------------- */

class OutgoingConnectionAttempt
{
    private OptionalInt remoteConnectionSeqnum;

    public OutgoingConnectionAttempt()
    {
        remoteConnectionSeqnum = null;
    }

    /**
     * Waits for a response from the remote regarding the connection
     * attempt.
     *
     * @param timeoutMilliseconds the timeout duration in milliseconds
     * @return an empty optional if the connection was rejected, otherwise
     *         an optional whose value is the remote connection seqnum
     *
     * @throws TimeoutException if the timeout duration elapsed and no
     *         response was received
     */
    public OptionalInt waitForResponse(
        long timeoutMilliseconds
    ) throws TimeoutException
    {
        final var nanosStart = System.nanoTime();
        var nanosNow = nanosStart;

        while (true)
        {
            if ((nanosNow - nanosStart) / 1000 >= timeoutMilliseconds)
                throw new TimeoutException();

            synchronized (this)
            {
                if (this.remoteConnectionSeqnum != null)
                    return this.remoteConnectionSeqnum;

                try
                {
                    this.wait(
                        timeoutMilliseconds - (nanosNow - nanosStart) / 1000
                    );
                }
                catch (InterruptedException ignored)
                {
                }
            }

            nanosNow = System.nanoTime();
        }
    }

    public synchronized void rejected()
    {
        this.remoteConnectionSeqnum = OptionalInt.empty();

        this.notify();
    }

    public synchronized void accepted(int remoteConnectionSeqnum)
    {
        this.remoteConnectionSeqnum =
            OptionalInt.of(remoteConnectionSeqnum);

        this.notify();
    }
}

/* -------------------------------------------------------------------------- */
