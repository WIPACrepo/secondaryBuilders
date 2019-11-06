package icecube.daq.secBuilder;

/**
 * Thrown when a JHDF5 operation fails.
 */
public class I3HDFException
    extends Exception
{
    /**
     * Create an exception with the specified message
     *
     * @param msg error message
     */
    I3HDFException(String msg)
    {
        super(msg);
    }

    /**
     * Create an exception wrapping the original JHDF5 exception
     *
     * @param thr original JHDF5 exception
     */
    I3HDFException(Throwable thr)
    {
        super(thr);
    }
}
