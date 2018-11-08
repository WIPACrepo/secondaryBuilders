package icecube.daq.secBuilder;

import icecube.daq.io.Dispatcher;
import icecube.daq.juggler.alert.AlertQueue;

public class TCalAnalysis
    extends SBSplicedAnalysis
{
    public TCalAnalysis(Dispatcher dispatcher)
    {
        super(dispatcher);
    }

    public void setAlertQueue(AlertQueue aq)
    {
        // do nothing
    }
}
