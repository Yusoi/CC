/* -------------------------------------------------------------------------- */

package fileshare;

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
     */
    public static void uninterruptibleJoin(Thread thread)
    {
        while (true)
        {
            try
            {
                thread.join();
                break;
            }
            catch (InterruptedException e)
            {
            }
        }
    }

    // No point in ever instantiating this class.
    private Util()
    {
    }
}

/* -------------------------------------------------------------------------- */
