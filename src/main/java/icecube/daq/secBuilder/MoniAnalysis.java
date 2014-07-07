package icecube.daq.secBuilder;

import icecube.daq.io.Dispatcher;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.PayloadFormatException;
import icecube.daq.payload.impl.ASCIIMonitor;
import icecube.daq.payload.impl.HardwareMonitor;
import icecube.daq.payload.impl.Monitor;
import icecube.daq.payload.impl.UTCTime;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.DeployedDOM;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MoniAnalysis
    extends SBSplicedAnalysis
{
    /** Deadtime message variable name */
    public static final String DEADTIME_MONI_NAME = "dom_deadtime";
    /** Deadtime message version number */
    public static final int DEADTIME_MONI_VERSION = 0;

    /** 5v message variable name */
    public static final String HV_MONI_NAME = "dom_mainboardPowerHV";
    /** 5v message version number */
    public static final int HV_MONI_VERSION = 0;

    /** Power supply voltage message variable name */
    public static final String POWER_MONI_NAME = "dom_mainboardPowerRail";
    /** Power supply voltage message version number */
    public static final int POWER_MONI_VERSION = 0;

    /** SPE monitoring message variable name */
    public static final String SPE_MONI_NAME = "dom_spe_moni_rate";
    /** MPE monitoring message variable name */
    public static final String MPE_MONI_NAME = "dom_mpe_moni_rate";
    /** SPE/MPE monitoring message version number */
    public static final int SPE_MPE_MONI_VERSION = 0;

    /** Logger */
    private static final Log LOG = LogFactory.getLog(MoniAnalysis.class);

    /** 10 minutes in 10ths of nanoseconds */
    private static final long TEN_MINUTES = 10L * 60L * 10000000000L;

    private static IDOMRegistry domRegistry;

    private Alerter alerter;

    private IUTCTime binStartTime = null;
    private IUTCTime binEndTime = null;

    private HashMap<Long, DOMValues> domValues =
        new HashMap<Long, DOMValues>();

    private boolean sentHVSet;

    public MoniAnalysis(Dispatcher dispatcher)
    {
        super(dispatcher);
    }

    /**
     * Convert a DOM reading to an actual voltage
     *
     * @param reading
     *
     * @return voltage
     */
    private static double convertToVoltage(double reading)
    {
        final double val5V = (2048.0 / 4095.0) * (5.2 / 2.0);

        return (double) reading * val5V;
    }

    /**
     * Find the DOM with the specified mainboard ID
     *
     * @param mainboard ID
     *
     * @return DeployedDOM object
     */
    public DeployedDOM findDOM(long mbKey)
    {
        final String mbId = String.format("%012x", mbKey);
        return domRegistry.getDom(mbId);
    }

    /**
     * Send any cached monitoring data
     */
    public void finishMonitoring()
    {
        if (binStartTime == null) {
            throw new Error("Start time has not been set!");
        } else if (binEndTime == null) {
            throw new Error("End time has not been set!");
        }

        final String startTime = binStartTime.toDateString();
        final String endTime = binEndTime.toDateString();

        sendBinnedMonitorValues(startTime, endTime);
        sendSummaryMonitorValues();

        binStartTime = null;
        binEndTime = null;
        domValues.clear();
    }

    /**
     * Gather data for monitoring messages
     *
     * @param payload payload
     */
    public void gatherMonitoring(IPayload payload)
        throws MoniException
    {
        // make sure we've got everything we need
        if (domRegistry == null) {
            throw new MoniException("DOM registry has not been set");
        } else if (alerter == null) {
            throw new MoniException("Alerter has not been set");
        } else if (!alerter.isActive()) {
            throw new MoniException("Alerter " + alerter + " is not active");
        }

        // load the payload
        try {
            ((ILoadablePayload) payload).loadPayload();
        } catch (IOException ioe) {
            throw new MoniException("Cannot load monitoring payload " +
                                    payload, ioe);
        } catch (PayloadFormatException pfe) {
            throw new MoniException("Cannot load monitoring payload " +
                                    payload, pfe);
        }

        // make sure the payload has a UTCTime value
        if (payload.getPayloadTimeUTC() == null) {
            throw new MoniException("Cannot get UTC time from monitoring" +
                                    " payload " + payload);
        }

        // if this is the first value, set the binning start time
        if (binStartTime == null) {
            binStartTime = payload.getPayloadTimeUTC();
        }

        // save previous end time, set current end time
        IUTCTime prevEnd = binEndTime;
        binEndTime = payload.getPayloadTimeUTC();

        final long endTime = binStartTime.longValue() + TEN_MINUTES;
        if (payload.getUTCTime() > endTime) {
            // use old bin start/end times as time range
            sendBinnedMonitorValues(binStartTime.toDateString(),
                                    prevEnd.toDateString());

            // set new bin start
            binStartTime = new UTCTime(endTime);
        }

        if (payload instanceof HardwareMonitor) {
            HardwareMonitor mon = (HardwareMonitor) payload;

            Long mbKey = mon.getDomId();

            DOMValues dval;
            if (domValues.containsKey(mbKey)) {
                dval = domValues.get(mbKey);
            } else {
                dval = new DOMValues();
                domValues.put(mbKey, dval);
            }

            dval.speScalar += mon.getSPEScalar();
            dval.mpeScalar += mon.getMPEScalar();

            dval.hvSet = mon.getPMTBaseHVSetValue();

            dval.hvTotal += mon.getPMTBaseHVMonitorValue();
            dval.hvCount++;

            dval.power5VTotal += mon.getADC5VPowerSupply();
            dval.power5VCount++;

        } else if (payload instanceof ASCIIMonitor) {
            ASCIIMonitor mon = (ASCIIMonitor) payload;

            // looking for "fast" moni records:
            //   "F" speCount mpeCount nonAbortedLaunches deadtime
            final String str = mon.getString();
            if (!str.startsWith("F ")) {
                return;
            }

            String[] flds = str.split("\\s+");
            if (flds.length != 5) {
                LOG.error("Ignoring fast monitoring record (#flds != 5): " +
                          str);
                return;
            }

            // this loop originally grabbed all the values; I'm keeping it
            // in place in case we ever decide to get the first two counts
            int deadtime = 0;
            for (int i = 4; i < 5; i++) {
                int val;
                try {
                    val = Integer.parseInt(flds[i]);
                } catch (NumberFormatException nfe) {
                    LOG.error("Ignoring fast monitoring record (bad value #" +
                              (i - 1) + " \"" + flds[i] + "\"): " + str);
                    break;
                }

                switch (i) {
                    //case 1:
                    //speCount = val;
                    //break;
                    //case 2:
                    //mpeCount = val;
                    //break;
                case 4:
                    deadtime = val;
                    break;
                default:
                    LOG.error("Not setting value for #" + i);
                    break;
                }
            }

            Long mbKey = mon.getDomId();

            DOMValues dval;
            if (domValues.containsKey(mbKey)) {
                dval = domValues.get(mbKey);
            } else {
                dval = new DOMValues();
                domValues.put(mbKey, dval);
            }

            dval.deadtimeTotal += deadtime;
            dval.deadtimeCount++;
        } else if (!(payload instanceof Monitor)) {
            throw new MoniException("Saw non-Monitor payload " + payload);
        }
    }

    /**
     * Send average deadtime
     */
    private void send_Deadtime()
    {
        HashMap<String, Double> map = new HashMap<String, Double>();

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);
            if (dv.dom == null) {
                dv.dom = findDOM(mbKey);
                if (dv.dom == null) {
                    LOG.error("Cannot find entry for DOM " +
                              String.format("%012x", mbKey));
                    continue;
                }
            }

            // 'deadtime' is average number of 25ns clock cycles per second
            // a PMT pulse arrived while both ATWDs were busy.
            final double deadtime = (double) dv.deadtimeTotal /
                (double) dv.deadtimeCount;

            // 'deadFraction' converts 'deadtime' to a fraction of a second
            //   (40000000 = 1000000000 ns/sec / 25 ns)
            final double deadFraction = deadtime / 40000000.0;

            map.put(dv.getOmID(), deadFraction);

            dv.deadtimeTotal = 0;
            dv.deadtimeCount = 0;
        }

        HashMap valueMap = new HashMap();
        valueMap.put("version", DEADTIME_MONI_VERSION);
        valueMap.put("runNumber", getRunNumber());
        valueMap.put("value", map);

        sendMessage(DEADTIME_MONI_NAME, valueMap);
    }

    /**
     * Send PowerHV and, if this is the first call, HVPowerSet.
     *
     * @param startTime starting date/time string
     * @param endTime ending date/time string
     */
    private void send_HV(String startTime, String endTime)
    {
        HashMap<String, Double> hvMap = new HashMap<String, Double>();

        // settings are only sent once
        HashMap<String, Double> setMap;
        if (!sentHVSet) {
            setMap = new HashMap<String, Double>();
        } else {
            setMap = null;
        }

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);
            if (dv.dom == null) {
                dv.dom = findDOM(mbKey);
                if (dv.dom == null) {
                    LOG.error("Cannot find entry for DOM " +
                              String.format("%012x", mbKey));
                    continue;
                }
            }

            double voltage = convertToVoltage((double) dv.hvTotal /
                                              (double) dv.hvCount);
            hvMap.put(dv.getOmID(), voltage);

            dv.hvTotal = 0;
            dv.hvCount = 0;

            if (setMap != null) {
                double setV = convertToVoltage((double) dv.hvSet);
                setMap.put(dv.getOmID(), setV);
            }
        }

        HashMap valueMap = new HashMap();
        valueMap.put("recordingStartTime", startTime);
        valueMap.put("recordingEndTime", endTime);
        valueMap.put("version", HV_MONI_VERSION);
        valueMap.put("runNumber", getRunNumber());

        if (setMap != null) {
            valueMap.put("value", setMap);
            sendMessage(HV_MONI_NAME + "Set", valueMap);

            // remember that we sent the HV settings
            sentHVSet = true;
        }

        valueMap.put("value", hvMap);
        sendMessage(HV_MONI_NAME, valueMap);
    }

    /**
     * Send average Power Supply voltage
     */
    private void send_Power()
    {
        HashMap<String, Double> map = new HashMap<String, Double>();

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);
            if (dv.dom == null) {
                dv.dom = findDOM(mbKey);
                if (dv.dom == null) {
                    LOG.error("Cannot find entry for DOM " +
                              String.format("%012x", mbKey));
                    continue;
                }
            }

            double voltage = convertToVoltage((double) dv.power5VTotal /
                                              (double) dv.power5VCount);
            map.put(dv.getOmID(), voltage);

            dv.power5VTotal = 0;
            dv.power5VCount = 0;
        }

        HashMap valueMap = new HashMap();
        valueMap.put("version", POWER_MONI_VERSION);
        valueMap.put("runNumber", getRunNumber());
        valueMap.put("value", map);

        sendMessage(POWER_MONI_NAME, valueMap);
    }

    /**
     * Send SPE and MPE values.
     *
     * @param startTime starting date/time string
     * @param endTime ending date/time string
     */
    private void send_SPE_MPE(String startTime, String endTime)
    {
        HashMap<String, Long> speMap = new HashMap<String, Long>();
        HashMap<String, Long> mpeMap = new HashMap<String, Long>();

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);
            if (dv.dom == null) {
                dv.dom = findDOM(mbKey);
                if (dv.dom == null) {
                    LOG.error("Cannot find entry for DOM " +
                              String.format("%012x", mbKey));
                    continue;
                }
            }

            speMap.put(dv.getOmID(), dv.speScalar);

            dv.speScalar = 0;

            if (dv.dom.isIceTop()) {
                mpeMap.put(dv.getOmID(), dv.mpeScalar);

                dv.mpeScalar = 0;
            }
        }

        HashMap valueMap = new HashMap();
        valueMap.put("recordingStartTime", startTime);
        valueMap.put("recordingEndTime", endTime);
        valueMap.put("version", SPE_MPE_MONI_VERSION);
        valueMap.put("runNumber", getRunNumber());

        valueMap.put("value", speMap);
        sendMessage(SPE_MONI_NAME, valueMap);

        valueMap.put("value", mpeMap);
        sendMessage(MPE_MONI_NAME, valueMap);
    }

    /**
     * Send 10 minute values
     *
     * @param startTime starting date/time string
     * @param endTime ending date/time string
     */
    private void sendBinnedMonitorValues(String startTime, String endTime)
    {
        send_SPE_MPE(startTime, endTime);
        send_HV(startTime, endTime);
    }

    private void sendMessage(String varname, Map<String, Object> value)
    {
        try {
            alerter.send(varname, Alerter.Priority.SCP, binEndTime, value);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + varname, ae);
        }
    }

    /**
     * Send once-a-run values
     */
    private void sendSummaryMonitorValues()
    {
        send_Deadtime();
        send_Power();
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
     * Per-DOM monitoring data
     */
    class DOMValues
    {
        DeployedDOM dom;

        long speScalar;
        long mpeScalar;

        int hvSet;

        long hvTotal;
        int hvCount;

        long power5VTotal;
        int power5VCount;

        long deadtimeTotal;
        int deadtimeCount;

        // OM ID generated from deployed DOM's major/minor values
        private String omId;

        /**
         * Get the OM ID
         *
         * @return "(string, position)"
         */
        public String getOmID()
        {
            if (omId == null) {
                omId = String.format("(%d, %d)", dom.getStringMajor(),
                                     dom.getStringMajor());
            }

            return omId;
        }
    }
}
