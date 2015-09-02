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
import java.util.ArrayList;
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
    public static final String HV_MONI_NAME = "dom_pmt_hv";
    /** 5v setting message variable name */
    public static final String HVSET_MONI_NAME = "dom_pmt_hv_set";
    /** 5v message version number */
    public static final int HV_MONI_VERSION = 0;

    /** Power supply voltage message variable name */
    public static final String POWER_MONI_NAME = "dom_mainboard_power_rail";
    /** Power supply voltage message version number */
    public static final int POWER_MONI_VERSION = 0;

    /** SPE monitoring message variable name */
    public static final String SPE_MONI_NAME = "dom_spe_moni_rate";
    /** MPE monitoring message variable name */
    public static final String MPE_MONI_NAME = "dom_mpe_moni_rate";
    /** SPE/MPE monitoring message version number */
    public static final int SPE_MPE_MONI_VERSION = 0;

    /** Name of field used to send SPE/MPE rate error */
    public static final String MONI_ERROR_FIELD = "rate_error";
    /** Name of field used to send SPE/MPE rates */
    public static final String MONI_RATE_FIELD = "rate";
    /** Name of field used to send other values */
    public static final String MONI_VALUE_FIELD = "value";

    /** Logger */
    private static final Log LOG = LogFactory.getLog(MoniAnalysis.class);

    /** 10 minutes in 10ths of nanoseconds */
    private static final long TEN_MINUTES = 10L * 60L * 10000000000L;

    /** Special value to indicate there is no value for this time */
    private static final long NO_UTCTIME = Long.MIN_VALUE;

    private static IDOMRegistry domRegistry;

    private AlertQueue alertQueue;
    private boolean warnedQueueStopped;

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
     * Convert a DOM deadtime reading to a fraction of a second
     *
     * @param total total of all values
     * @param count number of values
     *
     * @return deadtime
     */
    public static final double convertToDeadtime(long total, int count)
    {
        return ((double) total / (double) count) / 40000000.0;
    }

    /**
     * Convert a DOM mainboard power reading to a voltage
     *
     * @param total total of all values
     * @param count number of values
     *
     * @return voltage
     */
    public static final double convertToMBPower(long total, int count)
    {
        return ((double) total / (double) count) * 0.002 * (25.0 / 10.0);
    }

    /**
     * Convert a DOM reading to an actual voltage
     *
     * @param total total of all values
     * @param count number of values
     *
     * @return voltage
     */
    public static final double convertToVoltage(long total, int count)
    {
        return ((double) total / (double) count) / 2.0;
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
     * @param map map from mainboard IDs to DOMValues
     * @param mbKey mainboard ID
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
        if (binStartTime == NO_UTCTIME || binEndTime == NO_UTCTIME) {
            LOG.error("Monitoring start/end time has not been set, not" +
                      " sending binned monitoring values");
        } else {
            final String startTime = UTCTime.toDateString(binStartTime);
            final String endTime = UTCTime.toDateString(binEndTime);

            if (binEndTime < binStartTime) {
                LOG.error("Final bin end time " + endTime +
                          " is earlier than start time " + startTime);
            } else {
                sendBinnedMonitorValues(startTime, endTime);
            }
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
            if (warnedQueueStopped) {
                return;
            }

            warnedQueueStopped = true;
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

        // if this is the first value, set the binning start time
        if (binStartTime == NO_UTCTIME) {
            binStartTime = payload.getUTCTime();
        }

        // save previous end time, set current end time
        long prevEnd = binEndTime;
        binEndTime = payload.getUTCTime();

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
                synchronized (dval) {
                    dval.addSPEScalar(mon.getSPEScalar());
                    dval.addMPEScalar(mon.getMPEScalar());

                    if (dval.hvCount == 0) {
                        // only grab 'set' value once
                        dval.hvSet = mon.getPMTBaseHVSetValue();
                    }
                    dval.hvTotal += mon.getPMTBaseHVMonitorValue();
                    dval.hvCount++;

                    dval.power5VTotal += mon.getADC5VPowerSupply();
                    dval.power5VCount++;
                }
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

                synchronized (dval) {
                    dval.deadtimeTotal += deadtime;
                    dval.deadtimeCount++;
                }

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
            synchronized (dv) {
                if (dv.deadtimeCount == 0) {
                    if (dv.deadtimeTotal > 0) {
                        LOG.error("Found deadtime " + dv.deadtimeTotal +
                                  " total with 0 count for " + dv.getOmID());
                        dv.deadtimeTotal = 0;
                    }

                    // skip DOM if there were no reported values
                    continue;
                }

                deadtime = (double) dv.deadtimeTotal /
                    (double) dv.deadtimeCount;
                dv.deadtimeTotal = 0;
                dv.deadtimeCount = 0;
            }

            // convert 'deadtime' to a fraction of a second
            //   (40000000 = 1000000000 ns/sec / 25 ns)
            map.put(dv.getOmID(), deadtime / 40000000.0);
        }

        if (map.size() > 0) {
            HashMap valueMap = new HashMap();
            valueMap.put("version", DEADTIME_MONI_VERSION);
            valueMap.put("runNumber", getRunNumber());
            valueMap.put(MONI_VALUE_FIELD, map);
            sendMessage(DEADTIME_MONI_NAME, valueMap);
        }
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
            synchronized (dv) {
                if (dv.hvCount == 0) {
                    if (dv.hvTotal > 0) {
                        LOG.error("Found HV " + dv.hvTotal +
                                  " total with 0 count for " + dv.getOmID());
                        dv.hvTotal = 0;
                    }

                    // skip DOM if there were no reported values
                    continue;
                }

                voltage = convertToVoltage(dv.hvTotal, dv.hvCount);
                dv.hvTotal = 0;
                dv.hvCount = 0;
            }

            hvMap.put(dv.getOmID(), voltage);
            if (setMap != null) {
                double setV = convertToVoltage(dv.hvSet, 1);
                setMap.put(dv.getOmID(), setV);
            }
        }

        if (setMap != null && setMap.size() > 0) {
            HashMap setMsg = new HashMap();
            setMsg.put("recordingStartTime", startTime);
            setMsg.put("recordingStopTime", endTime);
            setMsg.put("version", HV_MONI_VERSION);
            setMsg.put("runNumber", getRunNumber());
            setMsg.put(MONI_VALUE_FIELD, setMap);
            sendMessage(HVSET_MONI_NAME, setMsg);

            // remember that we sent the HV settings
            sentHVSet = true;
        }

        if (hvMap.size() > 0) {
            HashMap hvMsg = new HashMap();
            hvMsg.put("recordingStartTime", startTime);
            hvMsg.put("recordingStopTime", endTime);
            hvMsg.put("version", HV_MONI_VERSION);
            hvMsg.put("runNumber", getRunNumber());
            hvMsg.put(MONI_VALUE_FIELD, hvMap);
            sendMessage(HV_MONI_NAME, hvMsg);
        }
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
            synchronized (dv) {
                if (dv.power5VCount == 0) {
                    if (dv.power5VTotal > 0) {
                        LOG.error("Found 5V " + dv.power5VTotal +
                                  " total with 0 count for " + dv.getOmID());
                        dv.power5VTotal = 0;
                    }

                    // skip DOM if there were no reported values
                    continue;
                }

                voltage = convertToMBPower(dv.power5VTotal, dv.power5VCount);
                dv.power5VTotal = 0;
                dv.power5VCount = 0;
            }
            map.put(dv.getOmID(), voltage);
        }

        if (map.size() > 0) {
            HashMap valueMap = new HashMap();
            valueMap.put("version", POWER_MONI_VERSION);
            valueMap.put("runNumber", getRunNumber());
            valueMap.put(MONI_VALUE_FIELD, map);
            sendMessage(POWER_MONI_NAME, valueMap);
        }
    }

    /**
     * Send SPE and MPE values.
     *
     * @param startTime starting date/time string
     * @param endTime ending date/time string
     */
    private void sendSPEMPE(String startTime, String endTime)
    {
        HashMap<String, Double> speRate = new HashMap<String, Double>();
        HashMap<String, Double> speRateError = new HashMap<String, Double>();
        HashMap<String, Double> mpeRate = new HashMap<String, Double>();
        HashMap<String, Double> mpeRateError = new HashMap<String, Double>();

        for (Long mbKey : domValues.keySet()) {
            DOMValues dv = domValues.get(mbKey);

            synchronized (dv) {
                if (dv.hasSPE()) {
                    double mean = dv.getSPEMean();

                    speRate.put(dv.getOmID(), mean);
                    speRateError.put(dv.getOmID(), dv.getSPEStdDev(mean));
                    dv.resetSPE();
                }

                if (dv.dom.isIceTop() && dv.hasMPE()) {
                    double mean = dv.getMPEMean();

                    mpeRate.put(dv.getOmID(), mean);
                    mpeRateError.put(dv.getOmID(), dv.getMPEStdDev(mean));
                    dv.resetMPE();
                }
            }
        }

        if (speRate.size() > 0) {
            HashMap speMsg = new HashMap();
            speMsg.put("recordingStartTime", startTime);
            speMsg.put("recordingStopTime", endTime);
            speMsg.put("version", SPE_MPE_MONI_VERSION);
            speMsg.put("runNumber", getRunNumber());
            speMsg.put(MONI_RATE_FIELD, speRate);
            speMsg.put(MONI_ERROR_FIELD, speRateError);
            sendMessage(SPE_MONI_NAME, speMsg);
        }

        if (mpeRate.size() > 0) {
            HashMap mpeMsg = new HashMap();
            mpeMsg.put("recordingStartTime", startTime);
            mpeMsg.put("recordingStopTime", endTime);
            mpeMsg.put("version", SPE_MPE_MONI_VERSION);
            mpeMsg.put("runNumber", getRunNumber());
            mpeMsg.put(MONI_RATE_FIELD, mpeRate);
            mpeMsg.put(MONI_ERROR_FIELD, mpeRateError);
            sendMessage(MPE_MONI_NAME, mpeMsg);
        }
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
     * MPE/SPE scalar values
     */
    class ScalarValues
    {
        private ArrayList<Integer> data = new ArrayList<Integer>();

        /**
         * Add a scalar value
         *
         * @param val value to add
         */
        void add(int val)
        {
            data.add(val);
        }

        /**
         * Does the list contain one or more values?
         *
         * @return <tt>true</tt> if there is data
         */
        boolean isEmpty()
        {
            return data.isEmpty();
        }

        /**
         * Calculate the mean value
         *
         * @return mean value
         */
        double mean()
        {
            if (data.isEmpty()) {
                return 0.0;
            }

            long sum = 0;
            for (Integer v : data) {
                sum += v;
            }

            return (double) sum / (double) data.size();
        }

        /**
         * Clear the list of values
         */
        void reset()
        {
            data.clear();
        }

        /**
         * Calculate the standard deviation
         *
         * @return mean previoously calculated mean value
         *
         * @return standard deviation
         */
        double standardDeviation(double mean)
        {
            if (data.size() < 2) {
                return 0.0;
            }

            double sum = 0.0;
            for (Integer v : data) {
                final double tmp = v.doubleValue() - mean;
                sum += tmp * tmp;
            }

            return Math.sqrt(sum / (data.size() - 1));
        }
    }

    /**
     * Per-DOM monitoring data
     */
    class DOMValues
    {
        DeployedDOM dom;

        ScalarValues speScalar = new ScalarValues();
        ScalarValues mpeScalar = new ScalarValues();

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
         * Add an MPE scaler value
         *
         * @param val value to add
         */
        public void addMPEScalar(int val)
        {
            mpeScalar.add(val);
        }

        /**
         * Add an SPE scaler value
         *
         * @param val value to add
         */
        public void addSPEScalar(int val)
        {
            speScalar.add(val);
        }

        /**
         * Get the mean value for the current list of MPE values
         *
         * @return mean value
         */
        public double getMPEMean()
        {
            return mpeScalar.mean();
        }

        /**
         * Get the standard deviation for the current list of MPE values
         *
         * @param mean mean value of current list of values
         *
         * @return standard deviation
         */
        public double getMPEStdDev(double mean)
        {
            return mpeScalar.standardDeviation(mean);
        }

        /**
         * Get the mean value for the current list of SPE values
         *
         * @return mean value
         */
        public double getSPEMean()
        {
            return speScalar.mean();
        }

        /**
         * Get the standard deviation for the current list of SPE values
         *
         * @param mean mean value of current list of values
         *
         * @return standard deviation
         */
        public double getSPEStdDev(double mean)
        {
            return speScalar.standardDeviation(mean);
        }

        /**
         * Has DOM sent one or more MPE values?
         *
         * @return <tt>true</tt> if there is MPE data
         */
        public boolean hasMPE()
        {
            return mpeScalar != null && !mpeScalar.isEmpty();
        }

        /**
         * Has DOM sent one or more SPE values?
         *
         * @return <tt>true</tt> if there is SPE data
         */
        public boolean hasSPE()
        {
            return speScalar != null && !speScalar.isEmpty();
        }

        /**
         * Clear the list of MPE values
         */
        public void resetMPE()
        {
            if (mpeScalar != null) {
                mpeScalar.reset();
            }
        }

        /**
         * Clear the list of MPE values
         */
        public void resetSPE()
        {
            if (speScalar != null) {
                speScalar.reset();
            }
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
            return String.format("%s: spe %s mpe %s hv %d hvTot %d hvCnt %d" +
                                 " 5VTot %d 5VCnt %d deadTot %d deadCnt %d",
                                 getOmID(), speScalar, mpeScalar, hvSet,
                                 hvTotal, hvCount, power5VTotal, power5VCount,
                                 deadtimeTotal, deadtimeCount);
        }
    }
}
