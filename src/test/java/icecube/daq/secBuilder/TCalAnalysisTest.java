package icecube.daq.secBuilder;

import icecube.daq.io.PayloadFileReader;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.PayloadFormatException;
import icecube.daq.payload.impl.TimeCalibration;
import icecube.daq.secBuilder.test.AlertData;
import icecube.daq.secBuilder.test.MockAlerter;
import icecube.daq.secBuilder.test.MockAppender;
import icecube.daq.secBuilder.test.MockDOMRegistry;
import icecube.daq.secBuilder.test.MockDispatcher;
import icecube.daq.secBuilder.test.MockPayload;
import icecube.daq.secBuilder.test.MockSpliceableFactory;
import icecube.daq.secBuilder.test.TCalData;
import icecube.daq.secBuilder.test.TCalDataFactory;
import icecube.daq.util.IDOMRegistry;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;

public class TCalAnalysisTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        //new MockAppender(org.apache.log4j.Level.WARN).setVerbose(false);

    private static final String ALERT_NAME = TCalAnalysis.TCAL_EXCEPTION_NAME;

    private MockAlerter alerter;

    private static final int ALGORITHM = 2;

    private void auditFile(File path, boolean verbose)
        throws IOException
    {
        PayloadFileReader in = new PayloadFileReader(path);
        try {
            int num = 0;
            int depth = 0;

            if (verbose) System.out.println("\n\nrealdaq tcal header:");

            for (IPayload pay : in) {
                // load the payload
                try {
                    ((ILoadablePayload) pay).loadPayload();
                } catch (IOException ioe) {
                    System.err.println("Cannot load payload " + pay);
                    ioe.printStackTrace();
                    continue;
                } catch (PayloadFormatException pfe) {
                    System.err.println("Cannot load payload " + pay);
                    pfe.printStackTrace();
                    continue;
                }

                if (pay instanceof TimeCalibration) {
                    TimeCalibration tcal = (TimeCalibration) pay;

                    //fit(tcal, num, depth, verbose);
                    if (tcal.getDomId() == 0x7df47437aefdL) {
                        dumpJava(tcal);
                        if (num++ == 10) break;
                    }
                } else {
                    System.err.println("??? " + pay);
                }

                //if (num++ == 51) break;
            }
        } finally {
            try {
                in.close();
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    private static void dumpJava(TimeCalibration tcal)
    {
        System.out.printf("        new TCalData(%dL, 0x%012xL, %dL,\n",
                          tcal.getUTCTime(), tcal.getDomId(),
                          tcal.getDorTXTime());

        final String indent = "                     ";
        System.out.printf("%s%dL,\n", indent, tcal.getDorRXTime());
        System.out.printf("%snew short[] {\n", indent);
        dumpJavaWaveform(tcal.getDorWaveform(), indent + "    ");
        System.out.printf("%s}, %dL, %dL,\n", indent, tcal.getDomTXTime(),
                          tcal.getDorRXTime());
        System.out.printf("%snew short[] {\n", indent);
        dumpJavaWaveform(tcal.getDomWaveform(), indent + "    ");
        System.out.printf("%s}, \"%s\", %dL),\n", indent, tcal.getDateString(),
                          tcal.getDorGpsSyncTime());
    }

    private static void dumpJavaWaveform(short[] waveform, String indent)
    {
        boolean first = true;
        int num = 0;
        for (int i = 0; i < waveform.length; i++) {
            String front;
            if (num == 0) {
                if (first) {
                    front = indent;
                    indent = ",\n" + indent;
                    first = false;
                } else {
                    front = indent;
                }
            } else {
                front = ", ";
            }

            System.out.printf("%s%3d", front, waveform[i]);
            if (++num == 11) {
                num = 0;
            }
        }
        System.out.println();
    }

    private TCalAnalysis buildAnalysis(boolean verbose)
    {
        TCalAnalysis ta = new TCalAnalysis(new MockDispatcher());

        alerter.setVerbose(verbose);

        ta.setAlerter(alerter);

        return ta;
    }

    private static void checkAlert(MockAlerter alerter, String errorStr,
                                   TCalData td)
    {
        assertEquals("Unexpected alerts", 1, alerter.countAlerts(ALERT_NAME));

        AlertData alert = alerter.get(ALERT_NAME, 0);
        assertNotNull("Couldn't get expected alert", alert);

        Map<String, Object> values = alert.getValues();
        assertEquals("Bad error message", errorStr, values.get("error"));
        assertEquals("Bad DOR TX",
                     (Long) td.getDorTXTime(), values.get("DORTX"));
        assertEquals("Bad DOM RX",
                     (Long) td.getDomRXTime(), values.get("DOMRX"));
        assertEquals("Bad DOM TX",
                     (Long) td.getDomTXTime(), values.get("DOMTX"));
        assertEquals("Bad DOR RX",
                     (Long) td.getDorRXTime(), values.get("DORRX"));

        alerter.clear(ALERT_NAME);
    }

    private static void compareWaveform(String name, short[] valid,
                                        short[] check)
    {
        assertNotNull(name + " waveform is null", check);
        if (valid.length != check.length) {
            fail(String.format("Expected %s waveform with %d entries," +
                                   " not %d", name, valid.length,
                               check.length));
        }

        for (int i = 0; i < valid.length; i++) {
            assertEquals("Bad " + name + " waveform #" + i, valid[i],
                         check[i]);
        }
    }

    // emulate FAT_reader output for debugging
    private void fit(TimeCalibration tcal, int num, int depth, boolean verbose)
        throws TCalException
    {
        if (verbose) {
            System.out.println("New Payload: ==> " + depth);
            System.out.println("plen " + tcal.length());
            System.out.println("ctype " + 0);
            System.out.println("ntype " + tcal.getPayloadType());
            System.out.println("utime " + tcal.getUTCTime());
        }

        if (verbose) {
            System.out.println("TcalFormatTriggerPayload");
            System.out.println();
            System.out.println("tcal():");
        }

        final long dorDiff =
            tcal.getDorRXTime() - tcal.getDorTXTime();
        final long domDiff =
            tcal.getDomTXTime() - tcal.getDomRXTime();

        if (domDiff != 612) {
            System.err.printf("!! domDiff %d (not 612)\n",
                              domDiff);
        }

        if (tcal.getGpsQualityByte() != ' ') {
            System.err.println("!! gpsSync '" +
                               tcal.getGpsQualityByte() + "'");
        }

        if (verbose) {
            System.out.printf("DOM id: %012x\n", tcal.getDomId());
            System.out.println("length 224, format (0x0001)");
            System.out.printf("cal(%d) dor_tx(0x%x) dor_rx(0x%x)" +
                              " dom_rx(0x%x) dom_tx(0x%x)\n", num,
                              tcal.getDorTXTime(),
                              tcal.getDorRXTime(),
                              tcal.getDomRXTime(),
                              tcal.getDomTXTime());

            short[] dorwf = tcal.getDorWaveform();
            System.out.printf("dor_wf(");
            for (int i=0; i<dorwf.length; i++) {
                if (i > 0) System.out.printf(", ");
                System.out.printf("%d", dorwf[i]);
            }
            System.out.printf(")\n");

            short[] domwf = tcal.getDomWaveform();
            System.out.printf("dom_wf(");
            for (int i=0; i<domwf.length; i++) {
                if (i > 0) System.out.printf(", ");
                System.out.printf("%d", domwf[i]);
            }
            System.out.printf(")\n");
            System.out.printf("\n");

            final int startMarker = 1;

            char qual = (char) tcal.getGpsQualityByte();
            System.out.printf("DOR GPS TIME: 0x%x",
                              tcal.getDorGpsSyncTime());
            System.out.printf(", GPS string: %c%s",
                              (char) startMarker,
                              tcal.getDateString());
            System.out.printf("  format=%d", startMarker);
            System.out.printf(" quality byte='%c'", qual);

            long gpsSecs;
            try {
                gpsSecs = tcal.getGpsSeconds();
            } catch (PayloadException pe) {
                fail("Cannot get GPS seconds: " + pe);
                gpsSecs = Long.MIN_VALUE;
            }

            System.out.printf(" seconds: %d\n", gpsSecs);

            final long gpsdordiff = (gpsSecs * 10000000000L) -
                (500 * tcal.getDorGpsSyncTime());

            System.out.printf("GPS diff: %d\n", gpsdordiff);
        }

        long[] domT0 = new long[1];
        long[] delta = new long[1];
        long[] rtrip = new long[1];

        TCalFit.tcalfit(ALGORITHM, verbose, tcal.getDorTXTime(),
                        tcal.getDorRXTime(), tcal.getDorWaveform(),
                        tcal.getDomRXTime(), tcal.getDomTXTime(),
                        tcal.getDomWaveform(), domT0, delta,
                        rtrip);

        {
            System.out.printf("utc %d dom %012x\n", tcal.getUTCTime(),
                               tcal.getDomId());
            System.out.printf("dor_tx %d dom_rx %d dom_tx %d dor_rx %d\n",
                              tcal.getDorTXTime(), tcal.getDomRXTime(),
                              tcal.getDomTXTime(), tcal.getDorRXTime());

            short[] dorwf = tcal.getDorWaveform();
            System.out.printf("\tdor_wf: ");
            for (int i=0; i<dorwf.length; i++) {
                if (i > 0) System.out.printf(", ");
                System.out.printf("%d", dorwf[i]);
            }
            System.out.println();

            short[] domwf = tcal.getDomWaveform();
            System.out.printf("\tdom_wf: ");
            for (int i=0; i<domwf.length; i++) {
                if (i > 0) System.out.printf(", ");
                System.out.printf("%d", domwf[i]);
            }
            System.out.println();
            System.out.printf("gpsStr \"%s\" sync %d\n", tcal.getDateString(),
                              tcal.getDorGpsSyncTime());
            System.out.printf("domT0 %d delta %d rtrip %d\n", domT0[0],
                              delta[0], rtrip[0]);
        }

        if (verbose) {
            System.out.printf("domT0=%d\n\tdelta=%d\n", domT0[0],
                              delta[0]);
            System.out.printf("id 0x%012x\n\tDOM time=0x%012x\n" +
                              "\tdelta=0x%014x\n" +
                              "\tDOR time=0x%014x\n" +
                              "\tDOR_tx+rx/2=0x%014x\n\n",
                              tcal.getDomId(), domT0[0], delta[0],
                              domT0[0]+delta[0],
                              (tcal.getDorTXTime() +
                               tcal.getDorRXTime())*25);
        }

        if (verbose) System.out.println("End Payload: <== " + depth);
    }

    private void initAnalysis(TCalAnalysis ta, TCalData td)
    {
        MockDOMRegistry reg = new MockDOMRegistry();
        reg.addDom(td.getDomId(), 2029, 1);
        ta.setDOMRegistry(reg);
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
        return new TestSuite(TCalAnalysisTest.class);
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

    private static void validate(TCalData td, TimeCalibration tcal)
        throws PayloadFormatException
    {
        // load the payload
        tcal.loadPayload();

        assertNotNull("createTCal() returned null", tcal);
        assertEquals("Bad UTC time", td.getUTCTime(), tcal.getUTCTime());
        assertEquals("Bad DOM ID", td.getDomId(), tcal.getDomId());

        assertEquals("Bad DOR xmit time",
                     td.getDorTXTime(), tcal.getDorTXTime());
        assertEquals("Bad DOR recv time",
                     td.getDorRXTime(), tcal.getDorRXTime());
        compareWaveform("DOR", td.getDorWaveform(), tcal.getDorWaveform());

        assertEquals("Bad DOM xmit time",
                     td.getDomTXTime(), tcal.getDomTXTime());
        assertEquals("Bad DOM recv time",
                     td.getDomRXTime(), tcal.getDomRXTime());
        compareWaveform("DOM", td.getDomWaveform(), tcal.getDomWaveform());

        assertEquals("Bad GPS string",
                     td.getDateString(), tcal.getDateString());
        assertEquals("Bad sync time",
                     td.getDorGpsSyncTime(), tcal.getDorGpsSyncTime());
    }

    // run a tcal data file through the fit() method
    public void ZZZtestStream()
    {
        final boolean verbose = true;

        File top = new File("/Users/dglo/prj/pdaq-madonna");

        FileFilter filter = new FileFilter() {
                @Override
                public boolean accept(File f)
                {
                    return f.isFile() &&
                        f.getName().startsWith("tcal_124652_");
                    //f.getName().startsWith("tcal_31329_");
                }
            };

        for (File f : top.listFiles(filter)) {
            try {
                auditFile(f, verbose);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // run factory data through the fit() method
    public void ZZZtestFactory()
        throws TCalException
    {
        final boolean verbose = true;

        for (int i = 0; i < TCalDataFactory.size(); i++) {
            TCalData td = TCalDataFactory.get(i);

            TimeCalibration tcal;
            try {
                tcal = td.create();
            } catch (PayloadException pe) {
                pe.printStackTrace();
                fail("Cannot create " + td);
                continue;
            }

            // load the payload
            try {
                tcal.loadPayload();
            } catch (PayloadFormatException pfe) {
                System.err.println("Cannot load " + tcal);
                pfe.printStackTrace();
                continue;
            }

            fit(tcal, i, 0, verbose);
        }
    }

    public void testValid()
        throws MoniException, PayloadException
    {
        TCalAnalysis ta = new TCalAnalysis(new MockDispatcher());


        for (int i = 0; i < TCalDataFactory.size(); i++) {
            TCalData td = TCalDataFactory.get(i);

            MockDOMRegistry reg = new MockDOMRegistry();
            reg.addDom(td.getDomId(), 2029, 1);
            ta.setDOMRegistry(reg);

            ta.setAlerter(alerter);

            TimeCalibration tcal = td.create();

            validate(td, tcal);

            ta.gatherMonitoring(tcal);
        }
    }

    public void testNoRegistry()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        ta.setDOMRegistry(null);

        try {
            ta.gatherMonitoring(td.create());
            fail("Should not work without DOM registry");
        } catch (TCalException te) {
            assertEquals("Bad exception", "DOM registry has not been set",
                         te.getMessage());
        }
    }

    public void testNoAlerter()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        ta.setAlerter(null);

        try {
            ta.gatherMonitoring(td.create());
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

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        alerter.close();

        try {
            ta.gatherMonitoring(td.create());
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

        TCalAnalysis ta = buildAnalysis(verbose);
        ta.setDOMRegistry(new MockDOMRegistry());

        MockTCalPayload pay = new MockTCalPayload();
        pay.setLoadPayloadException(new IOException("TestMessage"));

        try {
            ta.gatherMonitoring(pay);
            fail("Should not work due to loadPayload exception");
        } catch (TCalException te) {
            assertEquals("Bad exception", "Cannot load monitoring payload " +
                         pay, te.getMessage());
        }
    }

    public void testPayloadException()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);
        ta.setDOMRegistry(new MockDOMRegistry());

        MockTCalPayload pay = new MockTCalPayload();
        pay.setLoadPayloadException(new PayloadFormatException("TestMessage"));

        try {
            ta.gatherMonitoring(pay);
            fail("Should not work due to loadPayload exception");
        } catch (TCalException te) {
            assertEquals("Bad exception", "Cannot load monitoring payload " +
                         pay, te.getMessage());
        }
    }

    public void testBadPayload()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);
        ta.setDOMRegistry(new MockDOMRegistry());

        MockTCalPayload pay = new MockTCalPayload();

        try {
            ta.gatherMonitoring(pay);
            fail("Should not work due to bad payload");
        } catch (TCalException te) {
            assertEquals("Bad exception", "Saw non-TimeCalibration payload " +
                         pay, te.getMessage());
        }
    }

    public void testBadDOM()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        final long badDOM = 987654321L;

        td.setDomId(badDOM);

        ta.gatherMonitoring(td.create());

        checkAlert(alerter, String.format("Unknown DOM %012x", badDOM), td);
    }

    public void testBadQuality()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        final char badQuality = 'X';

        td.setQuality(badQuality);

        ta.gatherMonitoring(td.create());

        checkAlert(alerter, String.format("GPS Quality byte='%c'",
                                          badQuality), td);
    }

    public void testGPSString()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        final String badDate = "ABCDEFGHIJKL";

        td.setDateString(badDate);

        ta.gatherMonitoring(td.create());

        checkAlert(alerter, "DOR GPS string \"" + badDate + "\" is invalid",
                   td);
    }

    public void testHugeDORTX()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        final long badDorTx = Long.MAX_VALUE;

        td.setDorTXTime(badDorTx);

        ta.gatherMonitoring(td.create());

        checkAlert(alerter, "DOR GPS timestamp is invalid", td);
    }

    public void testHugeSync()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        final long badSync = Long.MAX_VALUE;

        td.setDorGpsSyncTime(badSync);

        ta.gatherMonitoring(td.create(), verbose);

        checkAlert(alerter, "DOR GPS timestamp is invalid", td);
    }

    public void testInvalidGPSTime()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        final long badSync = Long.MAX_VALUE;

        td.setDorTXTime(0x2000000000000L);
        td.setDorGpsSyncTime(0x123456789L);

        ta.gatherMonitoring(td.create(), verbose);

        checkAlert(alerter, "DOR GPS timestamp is invalid", td);
    }

    public void testModifiedGPSDiff()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        MockDOMRegistry reg = new MockDOMRegistry();
        ta.setDOMRegistry(reg);

        HashMap<Long, Integer> domInc = new HashMap<Long, Integer>();

        final long oneSecond = 10000000000L;

        for (int i = 0; i < TCalDataFactory.size(); i++) {
            TCalData td = TCalDataFactory.get(i);

            final long gpsTicks = td.getGpsSeconds() * oneSecond;

            // save GPS difference before modifying GPS sync time
            final long oldDiff = gpsTicks - (500 * td.getDorGpsSyncTime());

            int numSeen = 1;
            if (!domInc.containsKey(td.getDomId())) {
                // if this is the first time we've seen data for this DOM,
                //  add it to the registry and to our internal DOM map
                reg.addDom(td.getDomId(), 2029, 1 + i);
            } else {
                td.setDorGpsSyncTime(td.getDorGpsSyncTime() + 1);
                numSeen = domInc.get(td.getDomId()) + 1;
            }
            domInc.put(td.getDomId(), numSeen);

            // save post-modification GPS difference
            final long newDiff = gpsTicks - (500 * td.getDorGpsSyncTime());

            ta.gatherMonitoring(td.create(), verbose);

            switch (numSeen) {
            case 1: // no alerts on first entry
                break;
            case 2:
                final String errmsg2 =
                    String.format("GPS diff changed from %d to %d", oldDiff,
                                  newDiff);
                checkAlert(alerter, errmsg2, td);
                break;
            case 3:
                final String errmsg3 =
                    String.format("Accepting new GPS diff %d", newDiff);
                checkAlert(alerter, errmsg3, td);
                break;
            default:
                break;
            }
        }
    }

    public void testBadWaveform()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        short[] wf = td.getDorWaveform();
        wf[32] = 0;
        td.setDorWaveform(wf);

        ta.gatherMonitoring(td.create(), verbose);

        final String errmsg =
            String.format("Bad waveform, skipping %012x tcal 1",
                          td.getDomId());
        checkAlert(alerter, errmsg, td);
    }

    public void testBadRTrip()
        throws MoniException, PayloadException
    {
        final boolean verbose = false;

        TCalAnalysis ta = buildAnalysis(verbose);

        TCalData td = TCalDataFactory.get(0);

        initAnalysis(ta, td);

        for (int i = 0; i < 4; i++) {
            ta.gatherMonitoring(td.create(), verbose);

            if (i < 3) {
                assertEquals("Unexpected alerts", 0,
                             alerter.countAlerts(ALERT_NAME));
            } else {
                // should probably compute these but I'm lazy
                final double rtrip = 34319.3;
                final double mean = rtrip;
                final double rms = 0.0;

                final String errmsg =
                    String.format("Bad waveform, skipping %012x tcal %d" +
                                  " rtrip %g (mean=%g, rms=%g)", td.getDomId(),
                                  i + 1, rtrip, mean, rms);
                checkAlert(alerter, errmsg, td);
            }
        }
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}

class MockTCalPayload
    extends MockPayload
{
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
}
