/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

/* -------------------------------------------------------------------------- */

/**
 * Manages an outgoing connection request.
 *
 * Note that this class is package-private.
 */
class OutgoingConnectionRequest
{
    private Optional< Short > remoteConnectionSeqnum;
    private boolean interrupted;

    /**
     * Creates an {@code OutgoingConnectionRequest}.
     */
    OutgoingConnectionRequest()
    {
        remoteConnectionSeqnum = null;
        interrupted = false;
    }

    /**
     * Waits for a response from the remote regarding the connection request.
     *
     * @param timeoutMilliseconds the timeout duration in milliseconds
     * @return an empty optional if the connection was rejected, otherwise
     *         an optional whose value is the remote connection identifier
     *
     * @throws InterruptedException if {@link #interrupt()} is or was invoked
     * @throws TimeoutException if the timeout duration elapsed and no
     *         response was received
     */
    Optional< Short > waitForResponse(long timeoutMilliseconds)
        throws InterruptedException, TimeoutException
    {
        final var nanosStart = System.nanoTime();
        var nanosNow = nanosStart;

        while (true)
        {
            if ((nanosNow - nanosStart) / 1000 >= timeoutMilliseconds)
                throw new TimeoutException();

            synchronized (this)
            {
                if (this.interrupted)
                    throw new InterruptedException();

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

    /**
     * Signals that the connection request was accepted.
     *
     * @param remoteConnectionSeqnum the remote connection identifier
     */
    synchronized void accepted(short remoteConnectionSeqnum)
    {
        this.remoteConnectionSeqnum = Optional.of(remoteConnectionSeqnum);

        this.notifyAll();
    }

    /**
     * Signals that the connection request was rejected.
     */
    synchronized void rejected()
    {
        this.remoteConnectionSeqnum = Optional.empty();

        this.notifyAll();
    }

    /**
     * Interrupts the connection attempt.
     */
    synchronized void interrupt()
    {
        this.interrupted = true;

        this.notifyAll();
    }
}

/* -------------------------------------------------------------------------- */
