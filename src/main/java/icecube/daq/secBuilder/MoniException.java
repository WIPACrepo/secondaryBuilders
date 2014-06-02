package icecube.daq.secBuilder;

import icecube.daq.juggler.alert.AlertException;

public class MoniException
    extends AlertException
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
