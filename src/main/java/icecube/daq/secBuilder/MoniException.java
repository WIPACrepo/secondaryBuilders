package icecube.daq.secBuilder;

public class MoniException
    extends Exception
{
    MoniException(String message)
    {
        super(message);
    }

    MoniException(String message, Throwable thr)
    {
        super(message, thr);
    }
}
