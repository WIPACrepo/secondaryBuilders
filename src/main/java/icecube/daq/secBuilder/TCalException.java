package icecube.daq.secBuilder;

public class TCalException
    extends MoniException
{
    TCalException(String message)
    {
        super(message);
    }

    TCalException(String message, Throwable thr)
    {
        super(message, thr);
    }
}
