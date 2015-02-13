package icecube.daq.secBuilder;

import icecube.daq.io.Dispatcher;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IUTCTime;
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

    /** Special value to indicate there is no value for this time */
    private static final long NO_UTCTIME = Long.MIN_VALUE;

    private static IDOMRegistry domRegistry;

    private AlertQueue alertQueue;

    private long binStartTime = NO_UTCTIME;
    private long binEndTime = NO_UTCTIME;

    private HashMap<Long, DOMValues> domValues =
        new HashMap<Long, DOMValues>();

    private boolean sentHVSet;

    private FastMoniHDF fastMoni;
    private boolean noJHDFLib;

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

    public boolean disableIceTopFastMoni()
    {
        if (fastMoni == null) {
            return false;
        }

        noJHDFLib = true;
        return true;
    }

    /**
     * Find the DOM with the specified mainboard ID
     *
     * @param mainboard ID
     *
     * @return DeployedDOM object
     */
    public DOMValues findDOMValues(HashMap<Long, DOMValues> map, Long mbKey)
    {
        // if cached entry exists for this DOM, return it
        if (map.containsKey(mbKey)) {
            return map.get(mbKey);
        }

        DeployedDOM dom = domRegistry.getDom(mbKey);
        if (dom == null) {
            return null;
        }

        DOMValues dval = new DOMValues(dom);
        map.put(mbKey, dval);

        return dval;
    }

    /**
     * Send any cached monitoring data
     */
    public void finishMonitoring()
    {
        if (binStartTime == NO_UTCTIME) {
            throw new Error("Start time has not been set!");
        } else if (binEndTime == NO_UTCTIME) {
            throw new Error("End time has not been set!");
        }

        final String startTime = UTCTime.toDateString(binStartTime);
        final String endTime = UTCTime.toDateString(binEndTime);

        if (binEndTime < binStartTime) {
            LOG.error("Final bin end time " + endTime +
                      " is earlier than start time " + startTime);
        } else {
            sendBinnedMonitorValues(startTime, endTime);
        }

        sendSummaryMonitorValues();

        if (fastMoni != null) {
            try {
                fastMoni.close();
            } catch (I3HDFException ex) {
                LOG.error("Failed to close FastMoniHDF", ex);
                fastMoni = null;
            }
        }

        binStartTime = NO_UTCTIME;
        binEndTime = NO_UTCTIME;
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
        } else if (alertQueue == null) {
            throw new MoniException("AlertQueue has not been set");
        } else if (alertQueue.isStopped()) {
            throw new MoniException("AlertQueue " + alertQueue +
                                    " is stopped");
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
        if (binStartTime == NO_UTCTIME &&
            payload.getPayloadTimeUTC() != null)
        {
            binStartTime = payload.getPayloadTimeUTC().longValue();
        }

        // save previous end time, set current end time
        long prevEnd = binEndTime;
        if (payload.getPayloadTimeUTC()  == null) {
            binEndTime = NO_UTCTIME;
        } else {
            binEndTime = payload.getPayloadTimeUTC().longValue();
        }

        final long nextStart = binStartTime + TEN_MINUTES;
        if (payload.getUTCTime() > nextStart) {
            // use old bin start/end times as time range
            final String startTime = UTCTime.toDateString(binStartTime);
            final String endTime = UTCTime.toDateString(prevEnd);

            if (prevEnd < binStartTime) {
                LOG.error("Bin end time " + endTime +
                          " is earlier than start time " + startTime);
            } else {
                sendBinnedMonitorValues(startTime, endTime);
            }

            // set new bin start
            binStartTime = nextStart;
        }

        if (payload instanceof HardwareMonitor) {
            HardwareMonitor mon = (HardwareMonitor) payload;

            DOMValues dval = findDOMValues(domValues, mon.getDomId());
            if (dval == null) {
                LOG.error("Cannot find DOM " + mon.getDomId());
            } else {
                dval.speScalar += mon.getSPEScalar();
                dval.mpeScalar += mon.getMPEScalar();
                dval.scalarCount++;

                dval.hvSet = mon.getPMTBaseHVSetValue();

                dval.hvTotal += mon.getPMTBaseHVMonitorValue();
                dval.hvCount++;

                dval.power5VTotal += mon.getADC5VPowerSupply();
                dval.power5VCount++;
            }
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

            DOMValues dval = findDOMValues(domValues, mon.getDomId());
            if (dval == null) {
                LOG.error("Cannot find DOM " + mon.getDomId());
            } else {
                final boolean icetop = dval.dom.isIceTop();

                int speCount = Integer.MIN_VALUE;
                int mpeCount = Integer.MIN_VALUE;
                int launches = Integer.MIN_VALUE;

                // this loop originally grabbed all the values; I'm keeping it
                // in place in case we ever decide to get the first two counts
                int deadtime = 0;
                for (int i = 1; i < 5; i++) {
                    // if this is not an icetop DOM we only need deadtime
                    if (!icetop && i != 4) {
                        continue;
                    }

                    int val;
                    try {
                        val = Integer.parseInt(flds[i]);
                    } catch (NumberFormatException nfe) {
                        LOG.error("Ignoring fast monitoring record" +
                                  " (bad value #" + (i - 1) + " \"" +
                                  flds[i] + "\"): " + str);
                        break;
                    }

                    switch (i) {
                    case 1:
                        speCount = val;
                        break;
                    case 2:
                        mpeCount = val;
                        break;
                    case 3:
                        launches = val;
                        break;
                    case 4:
                        deadtime = val;
                        break;
                    default:
                        LOG.error("Not setting value for #" + i);
                        break;
                    }
                }

                dval.deadtimeTotal += deadtime;
                dval.deadtimeCount++;

                if (icetop) {
                    if (fastMoni == null && !noJHDFLib) {
                        try {
                            fastMoni = new FastMoniHDF(getDispatcher(),
                                                       getRunNumber());
                        } catch (I3HDFException ex) {
                            LOG.error("Cannot create HDF writer; IceTop" +
                                      " monitoring values will not be written",
                                      ex);
                            noJHDFLib = true;
                        } catch (UnsatisfiedLinkError ule) {
                            LOG.error("Cannot find HDF library; IceTop" +
                                      " monitoring values will not be written",
                                      ule);
                            noJHDFLib = true;
                        }
                    }

                    if (fastMoni != null) {
                        int[] data = new int[] {
                            speCount, mpeCount, launches, deadtime
                        };
                        try {
                            fastMoni.write(data);
                        } catch (I3HDFException ex) {
                            LOG.error("Cannot write IceTop FAST values for " +
                                      dval.dom);
                        }
                    }
                }
            }
        } else if (!(payload instanceof Monitor)) {
            throw new MoniException("Saw non-Monitor payload " + payload);
        }
    }

    /**
     * Send average deadtime
     */
    private void sendDeadtime()
    {
        HashMap<String, Double> map = new HashMap<String, Double>();

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);

            // 'deadtime' is average number of 25ns clock cycles per second
            // a PMT pulse arrived while both ATWDs were busy.
            final double deadtime;
            if (dv.deadtimeCount == 0) {
                if (dv.deadtimeTotal > 0) {
                    LOG.error("Found deadtime " + dv.deadtimeTotal +
                              " total with 0 count for " + dv.getOmID());
                }
                deadtime = 0.0;
            } else {
                deadtime = (double) dv.deadtimeTotal /
                    (double) dv.deadtimeCount;
            }

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
    private void sendHV(String startTime, String endTime)
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

            double voltage;
            if (dv.hvCount == 0) {
                if (dv.hvTotal > 0) {
                    LOG.error("Found HV " + dv.hvTotal +
                              " total with 0 count for " + dv.getOmID());
                }
                voltage = 0.0;
            } else {
                voltage = convertToVoltage((double) dv.hvTotal /
                                           (double) dv.hvCount);
            }
            hvMap.put(dv.getOmID(), voltage);

            dv.hvTotal = 0;
            dv.hvCount = 0;

            if (setMap != null) {
                double setV = convertToVoltage((double) dv.hvSet);
                setMap.put(dv.getOmID(), setV);
            }
        }

        if (setMap != null) {
            HashMap setMsg = new HashMap();
            setMsg.put("recordingStartTime", startTime);
            setMsg.put("recordingStopTime", endTime);
            setMsg.put("version", HV_MONI_VERSION);
            setMsg.put("runNumber", getRunNumber());
            setMsg.put("value", setMap);
            sendMessage(HV_MONI_NAME + "Set", setMsg);

            // remember that we sent the HV settings
            sentHVSet = true;
        }

        HashMap hvMsg = new HashMap();
        hvMsg.put("recordingStartTime", startTime);
        hvMsg.put("recordingStopTime", endTime);
        hvMsg.put("version", HV_MONI_VERSION);
        hvMsg.put("runNumber", getRunNumber());
        hvMsg.put("value", hvMap);
        sendMessage(HV_MONI_NAME, hvMsg);
    }

    /**
     * Send average Power Supply voltage
     */
    private void sendPower()
    {
        HashMap<String, Double> map = new HashMap<String, Double>();

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);

            double voltage;
            if (dv.power5VCount == 0) {
                if (dv.power5VTotal > 0) {
                    LOG.error("Found 5V " + dv.power5VTotal +
                              " total with 0 count for " + dv.getOmID());
                }
                voltage = 0.0;
            } else {
                voltage = convertToVoltage((double) dv.power5VTotal /
                                           (double) dv.power5VCount);
            }
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
    private void sendSPEMPE(String startTime, String endTime)
    {
        HashMap<String, Long> speMap = new HashMap<String, Long>();
        HashMap<String, Long> mpeMap = new HashMap<String, Long>();

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);

            long avg;

            if (dv.scalarCount == 0) {
                avg = 0;
            } else {
                avg = dv.speScalar / dv.scalarCount;
            }
            speMap.put(dv.getOmID(), avg);

            dv.speScalar = 0;

            if (dv.dom.isIceTop()) {
                if (dv.scalarCount == 0) {
                    avg = 0;
                } else {
                    avg = dv.mpeScalar / dv.scalarCount;
                }
                mpeMap.put(dv.getOmID(), avg);

                dv.mpeScalar = 0;
            }

            dv.scalarCount = 0;
        }

        HashMap speMsg = new HashMap();
        speMsg.put("recordingStartTime", startTime);
        speMsg.put("recordingStopTime", endTime);
        speMsg.put("version", SPE_MPE_MONI_VERSION);
        speMsg.put("runNumber", getRunNumber());
        speMsg.put("rate", speMap);
        sendMessage(SPE_MONI_NAME, speMsg);

        HashMap mpeMsg = new HashMap();
        mpeMsg.put("recordingStartTime", startTime);
        mpeMsg.put("recordingStopTime", endTime);
        mpeMsg.put("version", SPE_MPE_MONI_VERSION);
        mpeMsg.put("runNumber", getRunNumber());
        mpeMsg.put("rate", mpeMap);
        sendMessage(MPE_MONI_NAME, mpeMsg);
    }

    /**
     * Send 10 minute values
     *
     * @param startTime starting date/time string
     * @param endTime ending date/time string
     */
    private void sendBinnedMonitorValues(String startTime, String endTime)
    {
        sendSPEMPE(startTime, endTime);
        sendHV(startTime, endTime);
    }

    private void sendMessage(String varname, Map<String, Object> value)
    {
        try {
            alertQueue.push(varname, Alerter.Priority.SCP,
                            new UTCTime(binEndTime), value);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + varname, ae);
        } catch (Throwable thr) {
            LOG.error("Cannot send " + varname + " value " + value, thr);
        }
    }

    /**
     * Send once-a-run values
     */
    private void sendSummaryMonitorValues()
    {
        sendDeadtime();
        sendPower();
    }

    /**
     * Set the object used to send monitoring quantities
     *
     * @param newQueue new alert queue
     */
    public void setAlertQueue(AlertQueue newQueue)
    {
        if (alertQueue != null && !alertQueue.isStopped()) {
            alertQueue.stop();
        }

        alertQueue = newQueue;

        if (alertQueue != null && alertQueue.isStopped()) {
            alertQueue.start();
        }
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
     * Switch to a new run.
     *
     * @return number of events dispatched before the run was switched
     *
     * @param runNumber new run number
     */
    public long switchToNewRun(int runNumber)
    {
        // parent class switches dispatcher to new run
        long rtnval = super.switchToNewRun(runNumber);

        if (fastMoni != null) {
            try {
                fastMoni.switchToNewRun(runNumber);
            } catch (I3HDFException ex) {
                LOG.error("Cannot switch to new HDF5 file for run " +
                          runNumber, ex);
                fastMoni = null;
            }
        }

        return rtnval;
    }

    /**
     * Per-DOM monitoring data
     */
    class DOMValues
    {
        DeployedDOM dom;

        long speScalar;
        long mpeScalar;
        long scalarCount;

        int hvSet;

        long hvTotal;
        int hvCount;

        long power5VTotal;
        int power5VCount;

        long deadtimeTotal;
        int deadtimeCount;

        // OM ID generated from deployed DOM's major/minor values
        private String omId;

        DOMValues(DeployedDOM dom)
        {
            this.dom = dom;
        }

        /**
         * Get the OM ID
         *
         * @return "(string, position)"
         */
        public String getOmID()
        {
            if (omId == null) {
                omId = String.format("(%d, %d)", dom.getStringMajor(),
                                     dom.getStringMinor());
            }

            return omId;
        }

        public String toString()
        {
            return String.format("%s: spe %d mpe %d hv %d hvTot %d hvCnt %d" +
                                 " 5VTot %d 5VCnt %d deadTot %d deadCnt %d",
                                 getOmID(), speScalar, mpeScalar, hvSet,
                                 hvTotal, hvCount, power5VTotal, power5VCount,
                                 deadtimeTotal, deadtimeCount);
        }
    }
}
