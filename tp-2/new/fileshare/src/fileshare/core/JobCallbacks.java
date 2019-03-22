/* -------------------------------------------------------------------------- */

package fileshare.core;

import java.util.List;

/* -------------------------------------------------------------------------- */

/**
 * TODO: document
 */
public interface JobCallbacks
{
    /**
     * TODO: document
     *
     * @param jobStates TODO: document
     */
    default void jobStatesUpdated(List< JobState > jobStates)
    {
    }

    /**
     * TODO: document
     */
    default void jobsFinished()
    {
    }
}

/* -------------------------------------------------------------------------- */
