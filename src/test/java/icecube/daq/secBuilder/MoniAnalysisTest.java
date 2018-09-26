package icecube.daq.secBuilder;

import icecube.daq.common.MockAppender;
import icecube.daq.io.PayloadFileReader;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.PayloadFormatException;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.impl.ASCIIMonitor;
import icecube.daq.payload.impl.HardwareMonitor;
import icecube.daq.payload.impl.Monitor;
import icecube.daq.secBuilder.test.AlertData;
import icecube.daq.secBuilder.test.MockAlerter;
import icecube.daq.secBuilder.test.MockDOMRegistry;
import icecube.daq.secBuilder.test.MockDispatcher;
import icecube.daq.secBuilder.test.MockPayload;
import icecube.daq.secBuilder.test.MockSpliceableFactory;
import icecube.daq.secBuilder.test.MockUTCTime;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.LocatePDAQ;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;

import org.junit.*;
import static org.junit.Assert.*;

class MonitorCreator
{
    public static final long ONE_SECOND = 10000000000L;
    public static final long ONE_MINUTE = 60L * ONE_SECOND;
    public static final long TEN_MINUTES = 10L * ONE_MINUTE;

    static ASCIIMonitor ascii(long domId, long time, int speCount,
                              int mpeCount, int launches, int deadtime)
        throws PayloadException
    {
        return ascii(domId, time, String.format("F %d %d %d %d", speCount,
                                                mpeCount, launches, deadtime));
    }

    static ASCIIMonitor ascii(long domId, long time, String msg)
        throws PayloadException
    {
        final int bufLen = 16 + 18 + msg.length();
        ByteBuffer buf = ByteBuffer.allocate(bufLen);

        final int hdrLen = writeHeaders(buf, time, domId, Monitor.ASCII);

        buf.position(hdrLen);
        buf.put(msg.getBytes());

        assertEquals("Expected buffer length " + bufLen + ", not " +
                     (hdrLen + msg.length()), hdrLen + msg.length(), bufLen);

        buf.position(bufLen);
        buf.flip();

        return new ASCIIMonitor(buf, 0);
    }

    static HardwareMonitor hardware(long domId, long time, short[] data,
                                    int speScalar, int mpeScalar)
        throws PayloadException
    {
        if (data.length != HardwareMonitor.NUM_DATA_ENTRIES) {
            throw new Error("Expected " + HardwareMonitor.NUM_DATA_ENTRIES +
                            " data fields, not " + data.length);
        }

        final int bufLen = 16 + 18 + 2 + (data.length * 2) + 8;

        ByteBuffer buf = ByteBuffer.allocate(bufLen);

        final int hdrLen = writeHeaders(buf, time, domId, Monitor.HARDWARE);

        buf.put(hdrLen, (byte) 0); // eventVersion
        buf.put(hdrLen, (byte) 0xff); // spare byte

        for (int i = 0; i < data.length; i++) {
            buf.putShort(hdrLen + 2 + (i * 2), data[i]);
        }

        final int scalarPos = hdrLen + 2 + (data.length * 2);

        buf.putInt(scalarPos, speScalar);
        buf.putInt(scalarPos + 4, speScalar);

        buf.position(bufLen);
        buf.flip();

        return new HardwareMonitor(buf, 0);
    }

    private static int writeHeaders(ByteBuffer buf, long time, long domId,
                                    int moniType)
    {
        // payload header
        buf.putInt(0, buf.capacity());
        buf.putInt(4, PayloadRegistry.PAYLOAD_ID_MON);
        buf.putLong(8, time);

        // monitor header
        buf.putLong(16, domId);
        buf.putShort(24, (short) (buf.capacity() - 24));
        buf.putShort(26, (short) moniType);

        long domClock = time;
        for (int i = 5; i >= 0; i--) {
            buf.put(28 + i, (byte)(domClock & 0xff));
            domClock >>= 8;
        }

        return 34;
    }
}

class MoniGenerator
    implements Iterable<Monitor>, Iterator<Monitor>
{
    private long baseDOM;
    private long baseTime;
    private int interval;
    private int maxItems;

    private int nextItem;
    private short[] data = new short[HardwareMonitor.NUM_DATA_ENTRIES];
    private HashMap<Long, Short> setVals = new HashMap<Long, Short>();

    public MoniGenerator(long baseDOM, long baseTime, int interval,
                         int maxItems)
    {
        this.baseDOM = baseDOM;
        this.baseTime = baseTime;
        this.interval = interval;
        this.maxItems = maxItems;
    }

    @Override
    public boolean hasNext()
    {
        return nextItem < maxItems;
    }

    public int itemNumber()
    {
        return nextItem - 1;
    }

    public Iterator<Monitor> iterator()
    {
        return this;
    }

    public Monitor next()
    {
        if (nextItem >= maxItems) {
            throw new NoSuchElementException("Cannot generate more than " +
                                             maxItems + " monitoring records");
        }

        final int item = nextItem++;
        try {
            return generate(item);
        } catch (PayloadException pe) {
            throw new NoSuchElementException("Cannot generate item#" + item +
                                             ":" + pe.getMessage());
        }
    }

    private Monitor generate(int item)
        throws PayloadException
    {
        final long domId = (baseDOM + item) & 0xf;
        final long time = baseTime + ((long) item * MonitorCreator.ONE_SECOND);

        if ((item & 1) == 0) {
            // generate ASCII record
            final int speCount = item + 10;
            final int mpeCount = item + 11;
            final int launches = item + 15;
            final int deadtime = item + 21;
            return MonitorCreator.ascii(domId, time, speCount, mpeCount,
                                        launches, deadtime);
        } else {
            // generate hardware record

            final int hvSetIndex = 24;

            for (int di = 0; di < data.length; di++) {
                short val = (short) (item + di);
                if (di == hvSetIndex) {
                    if (!setVals.containsKey(domId)) {
                        setVals.put(domId, val);
                    }
                    val = setVals.get(domId);
                }
                data[di] = val;
            }

            return MonitorCreator.hardware(domId, time, data, interval,
                                           interval & 0xcaca);
        }
    }

    @Override
    public void remove()
    {
        throw new Error("Unimplemented");
    }
}

public class MoniAnalysisTest
{
    private static final MockAppender appender =
        new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
    //new MockAppender(org.apache.log4j.Level.WARN)/*.setVerbose(false)*/;

    private static final String tempDir = System.getProperty("java.io.tmpdir");

    private MockAlerter alerter;

    private MoniAnalysis buildAnalysis(boolean verbose)
    {
        MoniAnalysis ma = new MoniAnalysis(new MockDispatcher());

        alerter.setVerbose(verbose);

        ma.setAlertQueue(new AlertQueue(alerter));

        return ma;
    }

    private MockDOMRegistry buildDOMRegistry(boolean fakeIcetop)
    {
        MockDOMRegistry reg = new MockDOMRegistry();
        for (int i = 0; i < 16; i++) {
            int loc = i;
            int str = (i >> 3) + 1;
            int hub = str;

            if (fakeIcetop && loc > 12) {
                loc = i + 48;
                hub = str + 200;
            }

            reg.addDom((long) i, str, loc + 1, hub);
        }

        return reg;
    }

    private void auditFile(MoniAnalysis ma, File path)
        throws IOException
    {
        int n = 0;
        PayloadFileReader in = new PayloadFileReader(path);
        try {
            for (IPayload pay : in) {
                // load the payload
                try {
                    ((ILoadablePayload) pay).loadPayload();
                } catch (IOException ioe) {
                    System.err.println("Cannot load payload " + pay);
                    ioe.printStackTrace();
                    continue;
                } catch (PayloadException pe) {
                    System.err.println("Cannot load payload " + pay);
                    pe.printStackTrace();
                    continue;
                }

                if (pay instanceof HardwareMonitor) {
                    HardwareMonitor mon = (HardwareMonitor) pay;

                    System.out.printf("%012x %s HARD %d %d\n", mon.getDomId(),
                                      mon.getPayloadTimeUTC().toDateString(),
                                      mon.getSPEScalar(), mon.getMPEScalar());
                } else if (pay instanceof ASCIIMonitor) {
                    ASCIIMonitor mon = (ASCIIMonitor) pay;

                    // looking for "fast" moni records:
                    //   "F" speCount mpeCount ??? deadtime
                    final String str = mon.getString();
                    if (!str.startsWith("F ")) {
                        continue;
                    }

                    String[] flds = str.split("\\s+");
                    if (flds.length != 5) {
                        System.err.println("Ignoring fast monitoring record" +
                                  " (#flds != 5): " + str);
                        continue;
                    }

                    int speCount = 0;
                    int mpeCount = 0;
                    for (int i = 1; i < 3; i++) {
                        int val;
                        try {
                            val = Integer.parseInt(flds[i]);
                        } catch (NumberFormatException nfe) {
                            System.err.println("Ignoring fast monitoring" +
                                               " record (bad value #" +
                                               (i - 1) + " \"" + flds[i] +
                                               "\"): " + str);
                            continue;
                        }

                        switch (i) {
                        case 1:
                            speCount = val;
                            break;
                        case 2:
                            mpeCount = val;
                            break;
                        }
                    }

                    System.out.printf("%012x %s FAST %d %d\n", mon.getDomId(),
                                      mon.getPayloadTimeUTC().toDateString(),
                                      speCount, mpeCount);
                }
            }
        } finally {
            try {
                in.close();
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    private static void checkCounts(MockAlerter alerter, long startTick,
                                    long stopTick, MoniValidator validator)
    {
        // figure out how many bins were reported
        final int numBins =
            (int) ((stopTick - startTick) / MonitorCreator.TEN_MINUTES) + 1;

        for (String name : alerter.getNames()) {
            final int count;
            if (name.equals(MoniAnalysis.DEADTIME_MONI_NAME) ||
                name.equals(MoniAnalysis.POWER_MONI_NAME))
            {
                count = 1;
            } else {
                count = numBins;
            }
            assertEquals("Unexpected alert count for " + name, count,
                         alerter.countAlerts(name));

            if (validator != null) {
                validator.validate(alerter, name);
            }

            alerter.clear(name);
        }
    }

    private void runTest(MockDOMRegistry reg)
        throws MoniException, PayloadException
    {
        MockDispatcher disp = new MockDispatcher();
        disp.setDispatchDestStorage(tempDir);

        AlertQueue aq = new AlertQueue(alerter);

        MoniAnalysis ma = new MoniAnalysis(disp);
        ma.setDOMRegistry(reg);
        ma.setAlertQueue(aq);

        MoniValidator validator = new MoniValidator(reg);

        final long baseDOM = 7;
        final long baseTime = 1234567890;
        final int interval = 11;
        final int maxItems = 1200;

        MoniGenerator gen = new MoniGenerator(baseDOM, baseTime, interval,
                                              maxItems);
        long startTick = Long.MIN_VALUE;
        long stopTick = Long.MIN_VALUE;
        for (Monitor mon : gen) {
            validator.setTime(gen.itemNumber());

            validator.add(mon);
            ma.gatherMonitoring(mon);

            if (startTick == Long.MIN_VALUE) {
                startTick = mon.getUTCTime();
            }
            stopTick = mon.getUTCTime();
        }
        ma.finishMonitoring(stopTick);

        aq.stopAndWait();

        // save last sets of counts
        validator.endBin();

        checkCounts(alerter, startTick, stopTick, validator);
    }

    private void sendFile(MoniAnalysis ma, File path)
        throws IOException
    {
        int n = 0;
        PayloadFileReader in = new PayloadFileReader(path);
        try {
            for (IPayload pay : in) {
                try {
                    ma.gatherMonitoring(pay);
                } catch (MoniException me) {
                    me.printStackTrace();
                    continue;
                }
            }
        } finally {
            try {
                in.close();
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    @Before
    public void setUp()
        throws Exception
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        alerter = new MockAlerter();

        // ensure LocatePDAQ uses the test version of the config directory
        File configDir =
            new File(getClass().getResource("/config").getPath());
        if (!configDir.exists()) {
            throw new IllegalArgumentException("Cannot find config" +
                                               " directory under " +
                                               getClass().getResource("/"));
        }

        System.setProperty(LocatePDAQ.CONFIG_DIR_PROPERTY,
                           configDir.getAbsolutePath());
    }

    @After
    public void tearDown()
        throws Exception
    {
        System.clearProperty(LocatePDAQ.CONFIG_DIR_PROPERTY);

        if (appender.getNumberOfMessages() > 0) {
            try {
                // ignore errors about missing HDF5 library
                for (int i = 0; i < appender.getNumberOfMessages(); i++) {
                    final String msg = (String) appender.getMessage(i);
                    if (!msg.startsWith("Cannot find HDF library;") &&
                        !msg.contains("was not moved to the dispatch stor") &&
                        !msg.startsWith("Cannot create initial dataset pr") &&
                        !msg.startsWith("Cannot create HDF writer"))
                    {
                        fail("Unexpected log message " + i + ": " +
                             appender.getMessage(i));
                    }
                }
            } finally {
                appender.clear();
            }
        }

        assertEquals("Bad number of alert messages",
                     0, alerter.countAllAlerts());
    }

    @Test
    public void testBadPayload()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(new MockDOMRegistry());

        MockMoniPayload pay = new MockMoniPayload(1234);

        try {
            ma.gatherMonitoring(pay);
            fail("Should not work due to bad payload");
        } catch (MoniException te) {
            assertEquals("Bad exception", "Saw non-Monitor payload " +
                         pay, te.getMessage());
        }
    }

    @Test
    public void testEmpty()
        throws MoniException, PayloadException
    {
        MockDOMRegistry reg = buildDOMRegistry(false);

        AlertQueue aq = new AlertQueue(alerter);

        MoniAnalysis ma = new MoniAnalysis(new MockDispatcher());
        ma.setDOMRegistry(reg);
        ma.setAlertQueue(aq);

        MoniValidator validator = new MoniValidator(reg);

        long baseDOM = 7;
        long baseTime = 1234567890;
        int nextTime = 11;
        long startTick = Long.MIN_VALUE;
        long stopTick = Long.MIN_VALUE;
        for (int i = 0; i < 1200; i += nextTime, nextTime++) {
            long domId = (baseDOM + i) & 0xf;
            long time = baseTime + ((long) i * MonitorCreator.ONE_SECOND);

            validator.setTime(i);

            ASCIIMonitor mon = MonitorCreator.ascii(domId, time, "X");
            ma.gatherMonitoring(mon);

            if (startTick == Long.MIN_VALUE) {
                startTick = mon.getUTCTime();
            }
            stopTick = time;
        }
        ma.finishMonitoring(stopTick);

        aq.stopAndWait();

        // save last sets of counts
        validator.endBin();

        checkCounts(alerter, startTick, stopTick, validator);
    }

    @Test
    public void testEmptyFinalBin()
        throws MoniException, PayloadException
    {
        final long domId = 0x123456789ABCL;

        MockDOMRegistry reg = new MockDOMRegistry();
        reg.addDom(domId, 11, 11);

        AlertQueue aq = new AlertQueue(alerter);

        MoniAnalysis ma = new MoniAnalysis(new MockDispatcher());
        ma.setDOMRegistry(reg);
        ma.setAlertQueue(aq);

        long baseTime = 1234567890;
        long binStart = baseTime;

        short[] data = new short[HardwareMonitor.NUM_DATA_ENTRIES];

        final int hvSetIndex = 24;
        final int baseData = (int)(baseTime % (long) Integer.MAX_VALUE);
        for (int di = 0; di < data.length; di++) {
            short val = (short) (baseData + di);
            data[di] = val;
        }

        int interval = 11;
        final int maxTime = 30;
        long startTick = Long.MIN_VALUE;
        for (int i = 0; i < maxTime; i++) {

            long time = baseTime + ((long) i * 58 * MonitorCreator.ONE_SECOND);
            if (time - binStart > MonitorCreator.TEN_MINUTES) {
                binStart += MonitorCreator.TEN_MINUTES;
            }

            final int speScalar = i + 10;
            final int mpeScalar = i + 5;

            final HardwareMonitor hard =
                MonitorCreator.hardware(domId, time, data, speScalar,
                                        mpeScalar);

            ma.gatherMonitoring(hard);

            if (startTick == Long.MIN_VALUE) {
                startTick = time;
            }
        }

        // add zero-filled record to final bin
        final long stopTick = binStart + MonitorCreator.TEN_MINUTES +
            (58 * MonitorCreator.ONE_SECOND);
        final HardwareMonitor hard =
            MonitorCreator.hardware(domId, stopTick, data, 0, 0);
        ma.gatherMonitoring(hard);

        ma.finishMonitoring(stopTick);

        aq.stopAndWait();

        checkCounts(alerter, startTick, stopTick, null);
    }

    @Test
    public void testInIce()
        throws MoniException, PayloadException
    {
        MockDOMRegistry reg = buildDOMRegistry(false);

        runTest(reg);
    }

    @Test
    public void testIceTop()
        throws MoniException, PayloadException
    {
        MockDOMRegistry reg = buildDOMRegistry(true);

        runTest(reg);
    }

    @Test
    public void testIOException()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(new MockDOMRegistry());

        MockMoniPayload pay = new MockMoniPayload(1234);
        pay.setLoadPayloadException(new IOException("TestMessage"));

        try {
            ma.gatherMonitoring(pay);
            fail("Should not work due to loadPayload exception");
        } catch (MoniException te) {
            assertEquals("Bad exception", "Cannot load monitoring payload " +
                         pay, te.getMessage());
        }
    }

    @Test
    public void testInactiveAlerter()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = new MoniAnalysis(new MockDispatcher());

        alerter.setVerbose(verbose);

        AlertQueue aq = new AlertQueue(alerter);
        ma.setAlertQueue(aq);
        ma.setDOMRegistry(new MockDOMRegistry());

        aq.stopAndWait();

        try {
            ma.gatherMonitoring(new MockMoniPayload(1234));
            fail("Should not work without alerter");
        } catch (MoniException me) {
            assertEquals("Bad exception", "AlertQueue " + aq + " is stopped",
                         me.getMessage());
        }
    }

    @Test
    public void testNoAlerter()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(new MockDOMRegistry());

        ma.setAlertQueue(null);

        try {
            ma.gatherMonitoring(new MockMoniPayload(1234));
            fail("Should not work without alerter");
        } catch (MoniException me) {
            assertEquals("Bad exception", "AlertQueue has not been set",
                         me.getMessage());
        }
    }

    @Test
    public void testNoRegistry()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(null);

        try {
            ma.gatherMonitoring(new MockMoniPayload(1234));
            fail("Should not work without DOM registry");
        } catch (MoniException te) {
            assertEquals("Bad exception", "DOM registry has not been set",
                         te.getMessage());
        }
    }

    @Test
    public void testPayloadException()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(new MockDOMRegistry());

        MockMoniPayload pay = new MockMoniPayload(1234);
        pay.setLoadPayloadException(new PayloadFormatException("TestMessage"));

        try {
            ma.gatherMonitoring(pay);
            fail("Should not work due to loadPayload exception");
        } catch (MoniException te) {
            assertEquals("Bad exception", "Cannot load monitoring payload " +
                         pay, te.getMessage());
        }
    }

    private void stopQueue(AlertQueue aq)
    {
        if (!aq.isStopped()) {
            aq.stop();
            for (int i = 0; i < 1000; i++) {
                if (aq.isStopped()) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }
}

class MoniValidator
{
    private MockDOMRegistry reg;

    private long curTime = Long.MIN_VALUE;
    private ArrayList<Map<DOMInfo, MoniTotals>> allTotals =
        new ArrayList<Map<DOMInfo, MoniTotals>>();
    private HashMap<DOMInfo, MoniTotals> curTotals;

    MoniValidator(MockDOMRegistry reg)
    {
        this.reg = reg;
    }

    void add(Monitor mon)
        throws PayloadException
    {
        mon.loadPayload();

        DOMInfo dom = reg.getDom(mon.getDomId());
        if (!curTotals.containsKey(dom)) {
            curTotals.put(dom, new MoniTotals());
        }
        curTotals.get(dom).add(mon);
    }

    private static final double computeAverage(List<Integer> list)
    {
        if (list.isEmpty()) {
            return 0.0;
        }

        long total = 0;
        for (Integer val : list) {
            total += val;
        }
        return (double) total / (double) list.size();
    }

    private static final double computeRMS(List<Integer> list)
    {
        if (list.size() == 0) {
            return 0.0;
        }

        double total = 0.0;
        for (Integer val : list) {
            total += val.doubleValue();
        }
        return Math.sqrt(total) / (double) list.size();
    }

    void endBin()
    {
        if (curTotals != null && curTotals.size() > 0) {
            allTotals.add(curTotals);
        }
    }

    void setTime(long time)
    {
        if (time > curTime + 600) {
            endBin();

            curTotals = new HashMap<DOMInfo, MoniTotals>();

            curTime = time;
        }
    }

    void validate(MockAlerter alerter, String nm)
    {
        if (alerter.countAlerts(nm) == 1 && allTotals.size() > 1) {
            validateTotals(alerter, nm);
        } else {
            validateBins(alerter, nm);
        }
    }

    void validateBins(MockAlerter alerter, String nm)
    {
        final int numAlerts = alerter.countAlerts(nm);
        if (numAlerts > allTotals.size()) {
            fail("Found " + numAlerts + " bins but only " + allTotals.size() +
                 " totals are available");
        }

        for (int i = 0; i < numAlerts; i++) {
            AlertData ad = alerter.get(nm, i);
            Map<DOMInfo, MoniTotals> expMap = allTotals.get(i);

            if (nm == MoniAnalysis.MPE_MONI_NAME) {
                validateMPE(ad, expMap);
            } else if (nm == MoniAnalysis.SPE_MONI_NAME) {
                validateSPE(ad, expMap);
            } else if (nm == MoniAnalysis.HVDIFF_MONI_NAME) {
                validateHV(ad, expMap);
            } else if (nm == MoniAnalysis.MBTEMP_MONI_NAME) {
                validateTemp(ad, expMap);
            } else {
                fail("Not validating binned " + nm);
            }
        }
    }

    private void validateDeadtime(AlertData ad,
                                  Map<DOMInfo, MoniTotals> expMap)
    {
        Map<String, Double> valueMap =
            ad.getMap(MoniAnalysis.MONI_VALUE_FIELD);
        for (Map.Entry<DOMInfo, MoniTotals> e : expMap.entrySet()) {
            String omID = e.getKey().getDeploymentLocation();

            if (e.getValue().asciiCount == 0) {
                assertFalse("Found unexpected Deadtime value for " + omID,
                            valueMap.containsKey(omID));
            } else {
                final double val =
                    MoniAnalysis.convertToDeadtime(e.getValue().deadtimeTotal,
                                                   e.getValue().asciiCount);

                assertTrue("Missing Deadtime value " + val + " for " + omID,
                           valueMap.containsKey(omID));
                assertEquals("Bad " + omID + " Deadtime value",
                             val, valueMap.get(omID).doubleValue(), 0.001);
            }
        }
    }

    private void validateHV(AlertData ad, Map<DOMInfo, MoniTotals> expMap)
    {
        Map<String, Double> valueMap =
            ad.getMap(MoniAnalysis.MONI_VALUE_FIELD);

        for (Map.Entry<DOMInfo, MoniTotals> e : expMap.entrySet()) {
            String omID = e.getKey().getDeploymentLocation();

            if (e.getValue().hardCount == 0) {
                assertFalse("Found unexpected HV value for " + omID,
                            valueMap.containsKey(omID));
            } else {
                assertTrue("Missing HV value for " + omID,
                           valueMap.containsKey(omID));

                final double val =
                    MoniAnalysis.convertToVoltage(e.getValue().hvTotal,
                                                  e.getValue().hardCount);
                final double diff = val - e.getValue().baseVoltage;
                assertEquals("Bad " + omID + " HV voltage",
                             diff, valueMap.get(omID).doubleValue(), 0.001);
            }
        }
    }

    private void validateMPE(AlertData ad, Map<DOMInfo, MoniTotals> expMap)
    {
        Map<String, Double> rateMap =
            ad.getMap(MoniAnalysis.MONI_RATE_FIELD);
        Map<String, Double> errMap =
            ad.getMap(MoniAnalysis.MONI_ERROR_FIELD);

        for (Map.Entry<DOMInfo, MoniTotals> e : expMap.entrySet()) {
            String omID = e.getKey().getDeploymentLocation();

            assertTrue("Missing MPE rate for " + omID,
                       rateMap.containsKey(omID));
            assertTrue("Missing MPE error for " + omID,
                       errMap.containsKey(omID));

            final double avg = computeAverage(e.getValue().mpeScalar);
            assertEquals("Bad " + omID + " MPE rate",
                         avg, rateMap.get(omID).doubleValue(), 0.001);

            final double rms =
                computeRMS(e.getValue().mpeScalar);
            assertEquals("Bad " + omID + " MPE error",
                         rms, errMap.get(omID).doubleValue(), 0.001);
        }
    }

    private void validatePower(AlertData ad,
                               Map<DOMInfo, MoniTotals> expMap)
    {
        Map<String, Double> valueMap =
            ad.getMap(MoniAnalysis.MONI_VALUE_FIELD);
        for (Map.Entry<DOMInfo, MoniTotals> e : expMap.entrySet()) {
            String omID = e.getKey().getDeploymentLocation();

            if (e.getValue().hardCount == 0) {
                assertFalse("Found unexpected Power value for " + omID,
                            valueMap.containsKey(omID));
            } else {
                final double val =
                    MoniAnalysis.convertToMBPower(e.getValue().power5VTotal,
                                                  e.getValue().hardCount);

                assertTrue("Missing Power value " + val + " for " + omID,
                           valueMap.containsKey(omID));
                assertEquals("Bad " + omID + " Power value",
                             val, valueMap.get(omID).doubleValue(), 0.001);
            }
        }
    }

    private void validateSPE(AlertData ad, Map<DOMInfo, MoniTotals> expMap)
    {
        Map<String, Double> rateMap =
            ad.getMap(MoniAnalysis.MONI_RATE_FIELD);
        Map<String, Double> errMap =
            ad.getMap(MoniAnalysis.MONI_ERROR_FIELD);

        for (Map.Entry<DOMInfo, MoniTotals> e : expMap.entrySet()) {
            String omID = e.getKey().getDeploymentLocation();

            assertTrue("Missing SPE rate for " + omID,
                       rateMap.containsKey(omID));
            assertTrue("Missing SPE error for " + omID,
                       errMap.containsKey(omID));

            final double avg = computeAverage(e.getValue().speScalar);
            assertEquals("Bad " + omID + " SPE rate",
                         avg, rateMap.get(omID).doubleValue(), 0.001);

            final double rms =
                computeRMS(e.getValue().speScalar);
            assertEquals("Bad " + omID + " SPE error",
                         rms, errMap.get(omID).doubleValue(), 0.001);
        }
    }

    private void validateTemp(AlertData ad,
                              Map<DOMInfo, MoniTotals> expMap)
    {
        Map<String, Double> valueMap =
            ad.getMap(MoniAnalysis.MONI_VALUE_FIELD);

        for (Map.Entry<DOMInfo, MoniTotals> e : expMap.entrySet()) {
            String omID = e.getKey().getDeploymentLocation();

            if (e.getValue().hardCount == 0) {
                assertFalse("Found unexpected temperature value for " + omID,
                            valueMap.containsKey(omID));
            } else {
                assertTrue("Missing temperature value for " + omID,
                           valueMap.containsKey(omID));

                double avg = (double) e.getValue().tempTotal /
                    (double) e.getValue().hardCount;
                assertEquals("Bad " + omID + " temperature",
                             avg, valueMap.get(omID).doubleValue(), 0.001);
            }
        }
    }

    private void validateTotals(MockAlerter alerter, String nm)
    {
        final AlertData ad = alerter.get(nm, 0);

        Map<DOMInfo, MoniTotals> totalMap =
            new HashMap<DOMInfo, MoniTotals>();
        for (int i = 0; i < allTotals.size(); i++) {
            Map<DOMInfo, MoniTotals> expMap = allTotals.get(i);
            for (Map.Entry<DOMInfo, MoniTotals> e : expMap.entrySet()) {
                if (!totalMap.containsKey(e.getKey())) {
                    totalMap.put(e.getKey(), new MoniTotals());
                }
                totalMap.get(e.getKey()).add(e.getValue());
            }
        }

        if (nm == MoniAnalysis.DEADTIME_MONI_NAME) {
            validateDeadtime(ad, totalMap);
        } else if (nm == MoniAnalysis.POWER_MONI_NAME) {
            validatePower(ad, totalMap);
        } else {
            fail("Not validating totals for " + nm);
        }
    }

    class MoniTotals
    {
        long deadtimeTotal;
        int asciiCount;

        ArrayList<Integer> speScalar = new ArrayList<Integer>();
        ArrayList<Integer> mpeScalar = new ArrayList<Integer>();
        int scalarCount;
        double baseVoltage;
        boolean baseSet;
        long hvTotal;
        long power5VTotal;
        double tempTotal;
        int hardCount;

        void add(ASCIIMonitor mon)
        {
            final String str = mon.getString();
            if (!str.startsWith("F ")) {
                throw new Error("Bad ASCII record \"" + str + "\"");
            }

            String[] flds = str.split("\\s+");
            if (flds.length != 5) {
                throw new Error("Bad ASCII record \"" + str + "\"");
            }

            long val;
            try {
                val = Integer.parseInt(flds[4]);
            } catch (NumberFormatException nfe) {
                throw new Error("Bad deadtime in \"" + str + "\"", nfe);
            }

            deadtimeTotal += val;
            asciiCount++;
        }

        void add(HardwareMonitor mon)
        {
            speScalar.add(mon.getSPEScalar());
            mpeScalar.add(mon.getMPEScalar());
            if (!baseSet) {
                final int hvSet = mon.getPMTBaseHVSetValue();
                baseVoltage = MoniAnalysis.convertToVoltage(hvSet, 1);
                baseSet = true;
            }
            hvTotal += mon.getPMTBaseHVMonitorValue();
            power5VTotal += mon.getADC5VPowerSupply();
            tempTotal +=
                MoniAnalysis.translateTemperature(mon.getMBTemperature());
            hardCount++;
        }

        void add(MoniTotals mt)
        {
            deadtimeTotal += mt.deadtimeTotal;
            asciiCount += mt.asciiCount;

            // ignore binned MPE/SPE values
            if (!baseSet) {
                baseVoltage = mt.baseVoltage;
                baseSet = mt.baseSet;
            }

            // ignore binned HV and temperature values
            power5VTotal += mt.power5VTotal;
            hardCount += mt.hardCount;
        }

        void add(Monitor mon)
        {
            if (mon instanceof ASCIIMonitor) {
                add((ASCIIMonitor) mon);
            } else if (mon instanceof HardwareMonitor) {
                add((HardwareMonitor) mon);
            } else {
                throw new Error("Cannot add unknown type " +
                                mon.getClass().getName());
            }
        }

        @Override
        public String toString()
        {
            final double cvtVolt = ((2048.0 / 4095.0) * (5.2 / 2.0)) /
                (double) hardCount;

            double powerVolt = (double) power5VTotal * cvtVolt;
            return String.format("spe %s mpe %s hv %d temp %f power %f",
                                 speScalar, mpeScalar, hvTotal, tempTotal,
                                 powerVolt);
        }
    }
}

class MockMoniPayload
    extends MockPayload
{
    private long utcTime;
    private MockUTCTime utcObj;

    MockMoniPayload(long utcTime)
    {
        this.utcTime = utcTime;
    }

    /**
     * Unimplemented
     * @return Error
     */
    @Override
    public int getPayloadInterfaceType()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public IUTCTime getPayloadTimeUTC()
    {
        if (utcObj == null && utcTime != Long.MIN_VALUE) {
            utcObj = new MockUTCTime(utcTime);
        }

        return utcObj;
    }

    @Override
    public long getUTCTime()
    {
        return utcTime;
    }

    @Override
    public int hashCode()
    {
        return 123;
    }
}
