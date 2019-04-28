/* -------------------------------------------------------------------------- */

package fileshare;

/* -------------------------------------------------------------------------- */

public final class Util
{
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
