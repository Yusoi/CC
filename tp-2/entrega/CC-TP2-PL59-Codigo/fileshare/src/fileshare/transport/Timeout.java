/* -------------------------------------------------------------------------- */

package fileshare.transport;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* -------------------------------------------------------------------------- */

/**
 * Utility class for scheduling, re-scheduling, and cancelling actions.
 *
 * Note that this class is package-private.
 */
class Timeout
{
    private ScheduledExecutorService executor = null;

    /**
     * Schedules an action for execution if no action is currently scheduled.
     *
     * @param action the action to be executed
     * @param delayNanos the number of nanoseconds that should elapse before the
     *        action is executed
     */
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

    /**
     * Schedules an action for execution, replacing any currently scheduled
     * action.
     *
     * @param action the action to be executed
     * @param delayNanos the number of nanoseconds that should elapse before the
     *        action is executed
     */
    synchronized void scheduleReplace(Runnable action, long delayNanos)
    {
        cancelIfScheduled();
        scheduleIfNotScheduled(action, delayNanos);
    }

    /**
     * Cancels the execution of any currently scheduled action.
     */
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
