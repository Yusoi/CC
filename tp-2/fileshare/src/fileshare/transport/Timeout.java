/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* -------------------------------------------------------------------------- */

class Timeout
{
    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor();

    synchronized void scheduleIfNotScheduled(Runnable action, long delayNanos)
    {
        executor.schedule(
            action,
            delayNanos,
            TimeUnit.NANOSECONDS
        );
    }

    synchronized void scheduleReplace(Runnable action, long delayNanos)
    {
        executor.schedule(
            action,
            delayNanos,
            TimeUnit.NANOSECONDS
        );
    }

    synchronized void cancelIfScheduled()
    {
    }
}

/* -------------------------------------------------------------------------- */
