package icecube.daq.secBuilder;

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
import icecube.daq.secBuilder.test.MockAppender;
import icecube.daq.secBuilder.test.MockDOMRegistry;
import icecube.daq.secBuilder.test.MockDispatcher;
import icecube.daq.secBuilder.test.MockPayload;
import icecube.daq.secBuilder.test.MockSpliceableFactory;
import icecube.daq.secBuilder.test.MockUTCTime;
import icecube.daq.util.DeployedDOM;
import icecube.daq.util.IDOMRegistry;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;

import org.junit.*;
import static org.junit.Assert.*;

public class MoniAnalysisTest
{
    private static final MockAppender appender =
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        new MockAppender(org.apache.log4j.Level.WARN)/*.setVerbose(false)*/;

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

    private ASCIIMonitor createASCIIRecord(long domId, long time, String msg)
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

    private HardwareMonitor createHardwareRecord(long domId, long time,
                                                 short[] data, int speScalar,
                                                 int mpeScalar)
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

    private void runInOneRealm(boolean fakeIceTop)
        throws MoniException, PayloadException
    {
        MockDispatcher disp = new MockDispatcher();
        disp.setDispatchDestStorage(tempDir);

        MockDOMRegistry reg = buildDOMRegistry(fakeIceTop);

        AlertQueue aq = new AlertQueue(alerter);

        MoniAnalysis ma = new MoniAnalysis(disp);
        ma.setDOMRegistry(reg);
        ma.setAlertQueue(aq);

        MoniValidator validator = new MoniValidator(reg);

        short[] data = new short[HardwareMonitor.NUM_DATA_ENTRIES];

        HashMap<Long, Short> setVals = new HashMap<Long, Short>();

        long baseDOM = 7;
        long baseTime = 1234567890;
        int nextTime = 11;
        for (int i = 0; i < 1200; i += nextTime, nextTime++) {
            validator.setTime(i);

            long domId = (baseDOM + i) & 0xf;
            long time = baseTime + ((long) i * 10000000000L);

            Monitor mon;
            if ((i & 1) == 0) {
                String msg = String.format("F %d %d %d %d", i + 10, i + 11,
                                           i + 15, i + 21);
                ASCIIMonitor tmp = createASCIIRecord(domId, time, msg);
                validator.add(tmp);
                mon = tmp;
            } else {
                final int hvSetIndex = 24;

                for (int di = 0; di < data.length; di++) {
                    short val = (short) (i + di);
                    if (di == hvSetIndex) {
                        if (!setVals.containsKey(domId)) {
                            setVals.put(domId, val);
                        }
                        val = setVals.get(domId);
                    }
                    data[di] = val;
                }

                HardwareMonitor tmp = createHardwareRecord(domId, time, data,
                                                           nextTime,
                                                           nextTime & 0xcaca);
                validator.add(tmp);
                mon = tmp;
            }

            ma.gatherMonitoring(mon);
        }
        ma.finishMonitoring();

        aq.stopAndWait();

        // save last sets of counts
        validator.endBin();

        String[] twice = new String[] {
            MoniAnalysis.SPE_MONI_NAME,
            MoniAnalysis.MPE_MONI_NAME,
            MoniAnalysis.HVDIFF_MONI_NAME,
        };

        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        counts.put(MoniAnalysis.SPE_MONI_NAME, 2);
        counts.put(MoniAnalysis.MPE_MONI_NAME, 2);
        counts.put(MoniAnalysis.HVDIFF_MONI_NAME, 2);
        counts.put(MoniAnalysis.DEADTIME_MONI_NAME, 1);
        counts.put(MoniAnalysis.POWER_MONI_NAME, 1);

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            assertEquals("Unexpected alert count for " + e.getKey(),
                         e.getValue().intValue(),
                         alerter.countAlerts(e.getKey()));

            validator.validate(alerter, e.getKey());

            alerter.clear(e.getKey());
        }
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
        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        alerter = new MockAlerter();
    }

    @After
    public void tearDown()
        throws Exception
    {
        if (appender.getNumberOfMessages() > 0) {
            // ignore errors about missing HDF5 library
            for (int i = 0; i < appender.getNumberOfMessages(); i++) {
                final String msg = (String) appender.getMessage(i);
                if (!msg.startsWith("Cannot find HDF library;") &&
                    !msg.contains("was not moved to the dispatch storage") &&
                    !msg.startsWith("Cannot create initial dataset prop") &&
                    !msg.startsWith("Cannot create HDF writer"))
                {
                    fail("Unexpected log message " + i + ": " +
                         appender.getMessage(i));
                }
            }
        }

        assertEquals("Bad number of alert messages",
                     0, alerter.countAllAlerts());
    }

    // this code will send a moni file through MoniAnalysis
    // it's useful for development, but there are no checks
    // so it's not a good unit test
    // DISABLED @Test
    public void testStream()
    {
        File top = new File("/Users/dglo/prj/pdaq-madonna");

        FileFilter filter = new FileFilter() {
                @Override
                public boolean accept(File f)
                {
                    return f.isFile() &&
                        f.getName().startsWith("moni_124652_");
                }
            };

        MoniAnalysis ma = new MoniAnalysis(new MockDispatcher());
        ma.setDOMRegistry(buildDOMRegistry(false));
        ma.setAlertQueue(new AlertQueue(alerter));

        for (File f : top.listFiles(filter)) {
            try {
                //sendFile(ma, f);
                auditFile(ma, f);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        ma.finishMonitoring();
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

        short[] data = new short[HardwareMonitor.NUM_DATA_ENTRIES];

        long baseDOM = 7;
        long baseTime = 1234567890;
        int nextTime = 11;
        for (int i = 0; i < 1200; i += nextTime, nextTime++) {
            validator.setTime(i);

            long domId = (baseDOM + i) & 0xf;
            long time = baseTime + ((long) i * 10000000000L);

            ma.gatherMonitoring(createASCIIRecord(domId, time, "X"));
        }
        ma.finishMonitoring();

        aq.stopAndWait();

        // save last sets of counts
        validator.endBin();

        String[] twice = new String[] {
            MoniAnalysis.SPE_MONI_NAME,
            MoniAnalysis.MPE_MONI_NAME,
            MoniAnalysis.HVDIFF_MONI_NAME,
        };

        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        counts.put(MoniAnalysis.SPE_MONI_NAME, 0);
        counts.put(MoniAnalysis.MPE_MONI_NAME, 0);
        counts.put(MoniAnalysis.HVDIFF_MONI_NAME, 0);
        counts.put(MoniAnalysis.DEADTIME_MONI_NAME, 0);
        counts.put(MoniAnalysis.POWER_MONI_NAME, 0);

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            assertEquals("Unexpected alert count for " + e.getKey(),
                         e.getValue().intValue(),
                         alerter.countAlerts(e.getKey()));

            validator.validate(alerter, e.getKey());

            alerter.clear(e.getKey());
        }
    }

    @Test
    public void testInIce()
        throws MoniException, PayloadException
    {
        runInOneRealm(false);
    }

    @Test
    public void testIceTop()
        throws MoniException, PayloadException
    {
        runInOneRealm(true);
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

    private int writeHeaders(ByteBuffer buf, long time, long domId,
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
    private ArrayList<Map<DeployedDOM, MoniTotals>> allTotals =
        new ArrayList<Map<DeployedDOM, MoniTotals>>();
    private HashMap<DeployedDOM, MoniTotals> curTotals;

    MoniValidator(MockDOMRegistry reg)
    {
        this.reg = reg;
    }

    void add(ASCIIMonitor mon)
        throws PayloadException
    {
        mon.loadPayload();

        DeployedDOM dom = reg.getDom(mon.getDomId());
        if (!curTotals.containsKey(dom)) {
            curTotals.put(dom, new MoniTotals());
        }
        curTotals.get(dom).add(mon);
    }

    void add(HardwareMonitor mon)
        throws PayloadException
    {
        mon.loadPayload();

        DeployedDOM dom = reg.getDom(mon.getDomId());
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
        if (curTotals != null) {
            allTotals.add(curTotals);
        }
    }

    void setTime(long time)
    {
        if (time > curTime + 600) {
            endBin();

            curTotals = new HashMap<DeployedDOM, MoniTotals>();

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
        for (int i = 0; i < alerter.countAlerts(nm); i++) {
            AlertData ad = alerter.get(nm, i);
            Map<DeployedDOM, MoniTotals> expMap = allTotals.get(i);

            if (nm == MoniAnalysis.MPE_MONI_NAME) {
                validateMPE(ad, expMap);
            } else if (nm == MoniAnalysis.SPE_MONI_NAME) {
                validateSPE(ad, expMap);
            } else if (nm == MoniAnalysis.HVDIFF_MONI_NAME) {
                validateHV(ad, expMap);
            } else {
                fail("Not validating binned " + nm);
            }
        }
    }

    private void validateDeadtime(AlertData ad,
                                  Map<DeployedDOM, MoniTotals> expMap)
    {
        Map<String, Double> valueMap =
            ad.getMap(MoniAnalysis.MONI_VALUE_FIELD);
        for (Map.Entry<DeployedDOM, MoniTotals> e : expMap.entrySet()) {
            String omID = String.format("(%d, %d)",
                                        e.getKey().getStringMajor(),
                                        e.getKey().getStringMinor());

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

    private void validateHV(AlertData ad, Map<DeployedDOM, MoniTotals> expMap)
    {
        Map<String, Double> valueMap =
            ad.getMap(MoniAnalysis.MONI_VALUE_FIELD);

        for (Map.Entry<DeployedDOM, MoniTotals> e : expMap.entrySet()) {
            String omID = String.format("(%d, %d)",
                                        e.getKey().getStringMajor(),
                                        e.getKey().getStringMinor());

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

    private void validateMPE(AlertData ad, Map<DeployedDOM, MoniTotals> expMap)
    {
        Map<String, Double> rateMap =
            ad.getMap(MoniAnalysis.MONI_RATE_FIELD);
        Map<String, Double> errMap =
            ad.getMap(MoniAnalysis.MONI_ERROR_FIELD);

        for (Map.Entry<DeployedDOM, MoniTotals> e : expMap.entrySet()) {
            String omID = String.format("(%d, %d)",
                                        e.getKey().getStringMajor(),
                                        e.getKey().getStringMinor());

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
                               Map<DeployedDOM, MoniTotals> expMap)
    {
        Map<String, Double> valueMap =
            ad.getMap(MoniAnalysis.MONI_VALUE_FIELD);
        for (Map.Entry<DeployedDOM, MoniTotals> e : expMap.entrySet()) {
            String omID = String.format("(%d, %d)",
                                        e.getKey().getStringMajor(),
                                        e.getKey().getStringMinor());

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

    private void validateSPE(AlertData ad, Map<DeployedDOM, MoniTotals> expMap)
    {
        Map<String, Double> rateMap =
            ad.getMap(MoniAnalysis.MONI_RATE_FIELD);
        Map<String, Double> errMap =
            ad.getMap(MoniAnalysis.MONI_ERROR_FIELD);

        for (Map.Entry<DeployedDOM, MoniTotals> e : expMap.entrySet()) {
            String omID = String.format("(%d, %d)",
                                        e.getKey().getStringMajor(),
                                        e.getKey().getStringMinor());

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

    private void validateTotals(MockAlerter alerter, String nm)
    {
        final AlertData ad = alerter.get(nm, 0);

        Map<DeployedDOM, MoniTotals> totalMap =
            new HashMap<DeployedDOM, MoniTotals>();
        for (int i = 0; i < allTotals.size(); i++) {
            Map<DeployedDOM, MoniTotals> expMap = allTotals.get(i);
            for (Map.Entry<DeployedDOM, MoniTotals> e : expMap.entrySet()) {
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

            // ignore binned HV values
            power5VTotal += mt.power5VTotal;
            hardCount += mt.hardCount;
        }

        public String toString()
        {
            final double cvtVolt = ((2048.0 / 4095.0) * (5.2 / 2.0)) /
                (double) hardCount;

            double powerVolt = (double) power5VTotal * cvtVolt;
            return String.format("spe %s mpe %s hv %d power %f", speScalar,
                                 mpeScalar, hvTotal, powerVolt);
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
    public int getPayloadInterfaceType()
    {
        throw new Error("Unimplemented");
    }

    public int hashCode()
    {
        return 123;
    }

    public IUTCTime getPayloadTimeUTC()
    {
        if (utcObj == null && utcTime != Long.MIN_VALUE) {
            utcObj = new MockUTCTime(utcTime);
        }

        return utcObj;
    }

    public long getUTCTime()
    {
        return utcTime;
    }
}
