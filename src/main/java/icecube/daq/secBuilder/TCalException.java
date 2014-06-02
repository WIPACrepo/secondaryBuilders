package icecube.daq.secBuilder;

import icecube.daq.juggler.alert.AlertException;

public class TCalException
    extends AlertException
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
