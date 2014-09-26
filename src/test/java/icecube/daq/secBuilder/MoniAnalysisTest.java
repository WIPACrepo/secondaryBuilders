package icecube.daq.secBuilder;

import icecube.daq.io.PayloadFileReader;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.PayloadFormatException;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.impl.ASCIIMonitor;
import icecube.daq.payload.impl.HardwareMonitor;
import icecube.daq.payload.impl.Monitor;
import icecube.daq.secBuilder.test.MockAlerter;
import icecube.daq.secBuilder.test.MockAppender;
import icecube.daq.secBuilder.test.MockDOMRegistry;
import icecube.daq.secBuilder.test.MockDispatcher;
import icecube.daq.secBuilder.test.MockPayload;
import icecube.daq.secBuilder.test.MockSpliceableFactory;
import icecube.daq.secBuilder.test.MockUTCTime;
import icecube.daq.util.IDOMRegistry;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;

public class MoniAnalysisTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        //new MockAppender(org.apache.log4j.Level.WARN).setVerbose(false);

    private static final String tempDir = System.getProperty("java.io.tmpdir");

    private MockAlerter alerter;

    private MoniAnalysis buildAnalysis(boolean verbose)
    {
        MoniAnalysis ma = new MoniAnalysis(new MockDispatcher());

        alerter.setVerbose(verbose);

        ma.setAlerter(alerter);

        return ma;
    }

    private IDOMRegistry buildDOMRegistry(boolean fakeIcetop)
    {
        MockDOMRegistry reg = new MockDOMRegistry();
        for (int i = 0; i < 16; i++) {
            int loc = i;
            if (fakeIcetop && loc > 12) {
                loc = i + 48;
            }

            reg.addDom((long) i, i >> 3, loc);
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

    protected void setUp()
        throws Exception
    {
        super.setUp();

        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        alerter = new MockAlerter();
    }

    public static Test suite()
    {
        return new TestSuite(MoniAnalysisTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        assertEquals("Bad number of alert messages",
                     0, alerter.countAllAlerts());

        super.tearDown();
    }

    public void ZZZtestStream()
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
        ma.setAlerter(alerter);

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

    public void testInIce()
        throws MoniException, PayloadException
    {
        MoniAnalysis ma = new MoniAnalysis(new MockDispatcher());
        ma.setDOMRegistry(buildDOMRegistry(false));
        ma.setAlerter(alerter);

        short[] data = new short[HardwareMonitor.NUM_DATA_ENTRIES];

        long baseDOM = 7;
        long baseTime = 1234567890;
        int nextTime = 11;
        for (int i = 0; i < 1200; i += nextTime, nextTime++) {
            long domId = (baseDOM + i) & 0xf;
            long time = baseTime + ((long) i * 10000000000L);

            Monitor mon;
            if ((i & 1) == 0) {
                String msg = String.format("F %d %d %d %d", i + 10, i + 11,
                                           i + 15, i + 21);
                mon = createASCIIRecord(domId, time, msg);
            } else {
                for (int di = 0; di < data.length; di++) {
                    data[di] = (short) (i + di);
                }

                mon = createHardwareRecord(domId, time, data,
                                           nextTime, nextTime & 0xcaca);
            }

            ma.gatherMonitoring(mon);
        }
        ma.finishMonitoring();

        String[] twice = new String[] {
            MoniAnalysis.SPE_MONI_NAME,
            MoniAnalysis.MPE_MONI_NAME,
            MoniAnalysis.HV_MONI_NAME,
        };

        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        counts.put(MoniAnalysis.SPE_MONI_NAME, 2);
        counts.put(MoniAnalysis.MPE_MONI_NAME, 2);
        counts.put(MoniAnalysis.HV_MONI_NAME, 2);
        counts.put(MoniAnalysis.DEADTIME_MONI_NAME, 1);
        counts.put(MoniAnalysis.POWER_MONI_NAME, 1);
        counts.put(MoniAnalysis.HV_MONI_NAME + "Set", 1);

        for (String nm : counts.keySet()) {
            assertEquals("Unexpected alert count for " + nm,
                         counts.get(nm).intValue(), alerter.countAlerts(nm));

            alerter.clear(nm);
        }
    }

    public void testIceTop()
        throws MoniException, PayloadException
    {
        MockDispatcher disp = new MockDispatcher();
        disp.setDispatchDestStorage(tempDir);

        MoniAnalysis ma = new MoniAnalysis(disp);
        ma.setDOMRegistry(buildDOMRegistry(true));
        ma.setAlerter(alerter);

        short[] data = new short[HardwareMonitor.NUM_DATA_ENTRIES];

        long baseDOM = 3;
        long baseTime = 1234567890;
        int nextTime = 11;
        for (int i = 0; i < 1200; i += nextTime, nextTime++) {
            long domId = (baseDOM + i) & 0xf;
            long time = baseTime + ((long) i * 10000000000L);

            Monitor mon;
            if ((i & 1) == 0) {
                String msg = String.format("F %d %d %d %d", i + 10, i + 11,
                                           i + 15, i + 21);
                mon = createASCIIRecord(domId, time, msg);
            } else {
                for (int di = 0; di < data.length; di++) {
                    data[di] = (short) (i + di);
                }

                mon = createHardwareRecord(domId, time, data,
                                           nextTime, nextTime & 0xcaca);
            }

            ma.gatherMonitoring(mon);
        }
        ma.finishMonitoring();

        String[] twice = new String[] {
            MoniAnalysis.SPE_MONI_NAME,
            MoniAnalysis.MPE_MONI_NAME,
            MoniAnalysis.HV_MONI_NAME,
        };

        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        counts.put(MoniAnalysis.SPE_MONI_NAME, 2);
        counts.put(MoniAnalysis.MPE_MONI_NAME, 2);
        counts.put(MoniAnalysis.HV_MONI_NAME, 2);
        counts.put(MoniAnalysis.DEADTIME_MONI_NAME, 1);
        counts.put(MoniAnalysis.POWER_MONI_NAME, 1);
        counts.put(MoniAnalysis.HV_MONI_NAME + "Set", 1);

        for (String nm : counts.keySet()) {
            assertEquals("Unexpected alert count for " + nm,
                         counts.get(nm).intValue(), alerter.countAlerts(nm));

            alerter.clear(nm);
        }
    }

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

    public void testNoAlerter()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(new MockDOMRegistry());

        ma.setAlerter(null);

        try {
            ma.gatherMonitoring(new MockMoniPayload(1234));
            fail("Should not work without alerter");
        } catch (MoniException me) {
            assertEquals("Bad exception", "Alerter has not been set",
                         me.getMessage());
        }
    }

    public void testInactiveAlerter()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(new MockDOMRegistry());

        alerter.close();

        try {
            ma.gatherMonitoring(new MockMoniPayload(1234));
            fail("Should not work without alerter");
        } catch (MoniException me) {
            assertEquals("Bad exception", "Alerter " + alerter +
                         " is not active", me.getMessage());
        }
    }

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

    public void testBadPayloadUTC()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        MoniAnalysis ma = buildAnalysis(verbose);
        ma.setDOMRegistry(new MockDOMRegistry());

        MockMoniPayload pay = new MockMoniPayload(Long.MIN_VALUE);

        try {
            ma.gatherMonitoring(pay);
            fail("Should not work due to bad payload");
        } catch (MoniException te) {
            assertEquals("Bad exception", "Cannot get UTC time from" +
                         " monitoring payload " + pay, te.getMessage());
        }
    }

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

    public static void main(String[] args)
    {
        TestRunner.run(suite());
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
