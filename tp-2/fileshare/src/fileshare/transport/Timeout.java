/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* -------------------------------------------------------------------------- */

class Timeout
{
    private ScheduledExecutorService executor = null;

    synchronized void scheduleIfNotScheduled(Runnable action, long delayNanos)
    {
        if (executor == null)
        {
            executor = Executors.newSingleThreadScheduledExecutor();

            executor.schedule(
                action,
                delayNanos,
                TimeUnit.NANOSECONDS
            );
        }
    }

    synchronized void scheduleReplace(Runnable action, long delayNanos)
    {
        cancelIfScheduled();
        scheduleIfNotScheduled(action, delayNanos);
    }

    synchronized void cancelIfScheduled()
    {
        if (executor != null)
        {
            executor.shutdownNow();

            try
            {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException ignored)
            {
            }

            executor = null;
        }
    }
}

/* -------------------------------------------------------------------------- */
