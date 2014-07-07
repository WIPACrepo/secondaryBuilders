package icecube.daq.secBuilder;

import icecube.daq.io.Dispatcher;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.alert.AlertException;
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
    // these values are duplicated in icecube.daq.livemoni.LiveMoni
    public static final String TCAL_EXCEPTION_NAME = "dom_tcalException";
    public static final int TCAL_EXCEPTION_VERSION = 0;

    /** number of tcal cycles required in a ring buffer */
    private static final int TNIN = 3;
    /** max number of tcal cycles stored in a ring buffer */
    private static final int TNUM = 50;
    /** max number of tcal cycles to advance past the last timestamp */
    private static final int TADV = 40;
    /** number of the last tcal cycles used in the avg. roundtrip calc */
    private static final int INCT = 50;
    /** maximum allowed deviation (in rms) of the tcal roundtrip time */
    private static final double RTST = 5.0;

    /** Logger */
    private static final Log LOG = LogFactory.getLog(TCalAnalysis.class);

    /** one second in 10ths of nanoseconds */
    private static final long ONE_SECOND = 10000000000L;

    /** difference in GPS time and DOR time which indicates an error */
    private static final long GPSERR = 0xffffffffffffL;

    private static IDOMRegistry domRegistry;

    private Alerter alerter;

    private HashMap<Long, DOMStats> domStats = new HashMap<Long, DOMStats>();

    public TCalAnalysis(Dispatcher dispatcher)
    {
        super(dispatcher);
    }

    /**
     * Find the DOM with the specified mainboard ID
     *
     * @param mbId mainboard ID
     *
     * @return DeployedDOM object
     */
    public DOMStats findDOM(long mbId)
        throws TCalException
    {
        Long mbKey = mbId;
        if (domStats.containsKey(mbKey)) {
            return domStats.get(mbKey);
        }

        DOMStats cdom = new DOMStats(mbId);
        domStats.put(mbKey, cdom);
        return cdom;
    }

    /**
     * Gather data for monitoring messages
     *
     * @param payload payload
     *
     * @throws MoniException if there is a problem
     */
    public void gatherMonitoring(IPayload payload)
        throws MoniException
    {
        gatherMonitoring(payload, false);
    }

    /**
     * Gather data for monitoring messages
     *
     * @param payload payload
     *
     * @throws MoniException if there is a problem
     */
    public void gatherMonitoring(IPayload payload, boolean verbose)
        throws MoniException
    {
        // make sure we've got everything we need
        if (domRegistry == null) {
            throw new TCalException("DOM registry has not been set");
        } else if (alerter == null) {
            throw new MoniException("Alerter has not been set");
        } else if (!alerter.isActive()) {
            throw new MoniException("Alerter " + alerter + " is not active");
        }

        // load the payload
        try {
            ((ILoadablePayload) payload).loadPayload();
        } catch (IOException ioe) {
            throw new TCalException("Cannot load monitoring payload " +
                                    payload, ioe);
        } catch (PayloadFormatException pfe) {
            throw new TCalException("Cannot load monitoring payload " +
                                    payload, pfe);
        }

        if (!(payload instanceof TimeCalibration)) {
            throw new TCalException("Saw non-TimeCalibration payload " +
                                    payload);
        }

        TimeCalibration tcal = (TimeCalibration) payload;

        validate(tcal, verbose);
    }


    // this method is a near-copy of icecube.daq.livemoni.LiveMoni.send()
    public void send(String errmsg, DOMStats domStats, TimeCalibration tcal)
    {
        HashMap valueMap = new HashMap();
        valueMap.put("version", TCAL_EXCEPTION_VERSION);
        if (domStats != null) {
            valueMap.put("string", domStats.getMajor());
            valueMap.put("om", domStats.getMinor());
        }
        valueMap.put("error", errmsg);

        if (tcal != null) {
            valueMap.put("DORTX", tcal.getDorTXTime());
            valueMap.put("DORRX", tcal.getDorRXTime());
            valueMap.put("DORWF", tcal.getDorWaveform());
            valueMap.put("DOMTX", tcal.getDomTXTime());
            valueMap.put("DOMRX", tcal.getDomRXTime());
            valueMap.put("DOMWF", tcal.getDomWaveform());
        }

        try {
            alerter.send(TCAL_EXCEPTION_NAME, Alerter.Priority.SCP, valueMap);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + TCAL_EXCEPTION_NAME, ae);
        } catch (Throwable thr) {
            LOG.error("Cannot send " + TCAL_EXCEPTION_NAME + " value " +
                      valueMap, thr);
        }
    }

    /**
     * Set the object used to send monitoring quantities
     *
     * @param alert alerter
     */
    public void setAlerter(Alerter alerter)
    {
        this.alerter = alerter;
    }

    /**
     * Set the DOM registry object
     *
     * @param reg registry
     */
    public static void setDOMRegistry(IDOMRegistry reg)
    {
        domRegistry = reg;
    }

    private void validate(TimeCalibration tcal, boolean verbose)
        throws TCalException
    {
        DOMStats cdom;
        try {
            cdom = findDOM(tcal.getDomId());
        } catch (TCalException te) {
            send(String.format("Unknown DOM %012x", tcal.getDomId()), null,
                 tcal);
            return;
        }

        if (tcal.getGpsQualityByte() != ' ') {
            final String errmsg =
                String.format("GPS Quality byte='%c'",
                              tcal.getGpsQualityByte());
            send(errmsg, cdom, tcal); // err|=1
            return;
        }

        final long dor_t0 = tcal.getDorTXTime();
        final long dor_gps = tcal.getDorGpsSyncTime();
        final long gpssecs;
        try {
            gpssecs = tcal.getGpsSeconds();
        } catch (PayloadException pe) {
            send("DOR GPS string \"" + tcal.getDateString() + "\" is invalid",
                 cdom, tcal);
            return;
        }

        final long gpsdordiff = (gpssecs * ONE_SECOND) - (500 * dor_gps);
        final boolean isGPSValid = Math.abs(dor_t0 - dor_gps) < GPSERR;

        final int tnum = TNIN;
        final int inct = INCT;
        final double rtst = RTST;

        if (cdom.gpsdordiff == 0) {
            if (isGPSValid) {
                cdom.gpsdordiff = gpsdordiff;
            } else {
                send("DOR GPS timestamp is invalid", cdom, tcal); // err|=2
            }
        } else {
            if (cdom.gpsdordiff != gpsdordiff) {
                if (cdom.gpsdiffold == gpsdordiff) {
                    final String msg =
                        String.format("Accepting new GPS diff %d", gpsdordiff);
                    send(msg, cdom, tcal); // err|=8
                    cdom.gpsdordiff = gpsdordiff;
                } else {
                    final String msg =
                        String.format("GPS diff changed from %d to %d",
                                      cdom.gpsdordiff, gpsdordiff);
                    send(msg, cdom, tcal); // err|=4
                }
            }

            if (isGPSValid) {
                cdom.gpsdiffold = gpsdordiff;
            }
        }

        long[] domT0Ptr = new long[1];
        long[] deltaPtr = new long[1];
        long[] rtripPtr = new long[1];

        WaveformFit wfit =
            new WaveformFitCrossover(tcal.getDorTXTime(), tcal.getDorRXTime(),
                                     tcal.getDorWaveform(),
                                     tcal.getDomRXTime(), tcal.getDomTXTime(),
                                     tcal.getDomWaveform(), verbose);
        if (!wfit.isValid()) {
            final String msg =
                String.format("Bad waveform, skipping %012x tcal %d",
                              cdom.id, cdom.last+1);
            send(msg, cdom, tcal);
            return;
        }

        final long rtrip = wfit.getRoundtrip();

        if (cdom.rtrip == 0) {
            cdom.rtrip = rtrip;
        } else {
            double rms = rtrip - cdom.rtrip;
            rms *= rms;
            if (cdom.last < tnum || rms < rtst * rtst * cdom.rtrip_rms) {
                int aux = Math.min(inct, cdom.last + 1);
                cdom.rtrip_rms = (rms + aux * cdom.rtrip_rms) / (1 + aux);
                cdom.rtrip = (rtrip + aux * cdom.rtrip) / (1 + aux);
            } else if (inct > 0) {
                final String msg =
                    String.format("Bad waveform, skipping %012x tcal %d" +
                                  " rtrip %g (mean=%g, rms=%g)", cdom.id,
                                  cdom.last+1, rtrip / 10.0, cdom.rtrip / 10.0,
                                  Math.sqrt(cdom.rtrip_rms)/10.);
                send(msg, cdom, tcal);
                return;
            }
        }

        cdom.last++;
    }

    class DOMStats
    {
        private long id;
        private int major;
        private int minor;

        private int last; // last calibration index (last % TNUM)

        private long gpsdordiff; // difference gps - dor time [0.1 ns]
        private long gpsdiffold; // difference gps - dor time [0.1 ns] history
        private double rtrip; // roundtrip average time
        private double rtrip_rms; // roundtrip rms

        DOMStats(long mbId)
            throws TCalException
        {
            final String mbStr = String.format("%012x", mbId);
            DeployedDOM domInfo = domRegistry.getDom(mbStr);
            if (domInfo == null) {
                final String errmsg =
                    String.format("DOM %012x does not exist", mbId);
                throw new TCalException(errmsg);
            }

            id = mbId;
            major = domInfo.getStringMajor();
            minor = domInfo.getStringMinor();
        }

        public int getMajor()
        {
            return major;
        }

        public int getMinor()
        {
            return minor;
        }
    }
}
