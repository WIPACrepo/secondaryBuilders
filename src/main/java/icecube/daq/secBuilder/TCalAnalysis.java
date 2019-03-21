package icecube.daq.secBuilder;

import icecube.daq.io.Dispatcher;
import icecube.daq.juggler.alert.AlertQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
