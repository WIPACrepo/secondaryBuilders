package icecube.daq.secBuilder;

import icecube.daq.io.Dispatcher;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.PayloadFormatException;
import icecube.daq.payload.impl.TimeCalibration;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.DeployedDOM;

import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TCalAnalysis
    extends SBSplicedAnalysis
{
    /** Logger */
    private static final Log LOG = LogFactory.getLog(TCalAnalysis.class);

    public TCalAnalysis(Dispatcher dispatcher)
    {
        super(dispatcher);
    }

    public void setAlertQueue(AlertQueue aq)
    {
        // do nothing
    }

    public void setDOMRegistry(IDOMRegistry domReg)
    {
        // do nothing
    }
}
