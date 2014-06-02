package icecube.daq.secBuilder;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.payload.PayloadException;
import icecube.daq.secBuilder.test.MockAppender;
import icecube.daq.secBuilder.test.TCalData;
import icecube.daq.secBuilder.test.TCalDataFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;

public class TCalFitTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        //new MockAppender(org.apache.log4j.Level.WARN).setVerbose(false);

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

    protected void setUp()
        throws Exception
    {
        super.setUp();

        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
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

        super.tearDown();
    }

    private static final void checkFit(WaveformFit wfit, int entry,
                                       long expDOMT0, long expDelta,
                                       long expRoundtrip, double expDorSample,
                                       double expDomSample)
    {
        assertTrue("tcalfit failed for " + wfit.getName() + " #" + entry,
                   wfit.isValid());

        assertEquals("Bad DOM T0 for " + wfit.getName() + " #" + entry,
                     expDOMT0, wfit.getDOMT0());
        assertEquals("Bad delta for " + wfit.getName() + " #" + entry,
                     expDelta, wfit.getDelta());
        assertEquals("Bad roundtrip for " + wfit.getName() + " #" + entry,
                     expRoundtrip, wfit.getRoundtrip());
        assertEquals("Bad DOR sample for " + wfit.getName() + " #" + entry,
                     expDorSample, wfit.getDorSample(), 0.000001);
        assertEquals("Bad DOM sample for " + wfit.getName() + " #" + entry,
                     expDomSample, wfit.getDomSample(), 0.000001);
    }

    public void testCentroid()
        throws AlertException, PayloadException
    {
        final boolean verbose = false;

        for (int i = 0; i < TCalDataFactory.size(); i++) {
            TCalData td = TCalDataFactory.get(i);

            WaveformFit wfit =
                new WaveformFitCentroid(td.getDorTXTime(), td.getDorRXTime(),
                                        td.getDorWaveform(),
                                        td.getDomRXTime(), td.getDomTXTime(),
                                        td.getDomWaveform(), verbose);

            long expDOMT0, expDelta, expRoundtrip;
            double expDorSample, expDomSample;
            switch (i) {
            case 0:
                expDOMT0 = 41084625824944807L;
                expDelta = 71043438147469L;
                expRoundtrip = 335666L;
                expDorSample = 32.102874;
                expDomSample = 33.227946;
                break;
            case 1:
                expDOMT0 = 41084427753309812L;
                expDelta = 71637693452001L;
                expRoundtrip = 251751L;
                expDorSample = 33.254759;
                expDomSample = 33.246794;
                break;
            case 2:
               expDOMT0 = 41084636467137752L;
                expDelta = 71043438129578L;
                expRoundtrip = 335664L;
                expDorSample = 32.320036;
                expDomSample = 33.006404;
                break;
            case 3:
                expDOMT0 = 41084646770107652L;
                expDelta = 71043438112284L;
                expRoundtrip = 335680L;
                expDorSample = 32.749007;
                expDomSample = 32.609486;
                break;
            case 4:
               expDOMT0 = 41084657360720791L;
                expDelta = 71043438094498L;
                expRoundtrip = 335661L;
                expDorSample = 32.157541;
                expDomSample = 33.163642;
                break;
            case 5:
                expDOMT0 = 41084667737698759L;
                expDelta = 71043438077062L;
                expRoundtrip = 335663L;
                expDorSample = 32.287556;
                expDomSample = 33.037658;
                break;
            case 6:
                expDOMT0 = 41084480352915223L;
                expDelta = 71637693619185L;
                expRoundtrip = 251764L;
                expDorSample = 33.634505;
                expDomSample = 32.892795;
                break;
            case 7:
                expDOMT0 = 41084490909456449L;
                expDelta = 71637693652746L;
                expRoundtrip = 251790L;
                expDorSample = 32.781534;
                expDomSample = 33.797061;
                break;
            default:
                throw new Error("Unknown TCalData entry #" + i);
            }

            checkFit(wfit, i, expDOMT0, expDelta, expRoundtrip, expDorSample,
                     expDomSample);
        }
    }

    public void testCrossover()
        throws AlertException, PayloadException
    {
        final boolean verbose = false;

        for (int i = 0; i < TCalDataFactory.size(); i++) {
            TCalData td = TCalDataFactory.get(i);

            WaveformFit wfit =
                new WaveformFitCrossover(td.getDorTXTime(), td.getDorRXTime(),
                                         td.getDorWaveform(),
                                         td.getDomRXTime(), td.getDomTXTime(),
                                         td.getDomWaveform(), verbose);

            long expDOMT0, expDelta, expRoundtrip;
            double expDorSample, expDomSample;
            switch (i) {
            case 0:
                expDOMT0 = 41084625824946690L;
                expDelta = 71043438147467L;
                expRoundtrip = 343193L;
                expDorSample = 39.627350;
                expDomSample = 40.758252;
                break;
            case 1:
                expDOMT0 = 41084427753311463L;
                expDelta = 71637693452018L;
                expRoundtrip = 258389L;
                expDorSample = 39.925279;
                expDomSample = 39.851090;
                break;
            case 2:
                expDOMT0 = 41084636467139634L;
                expDelta = 71043438129588L;
                expRoundtrip = 343211L;
                expDorSample = 39.885574;
                expDomSample = 40.535228;
                break;
            case 3:
                expDOMT0 = 41084646770109531L;
                expDelta = 71043438112296L;
                expRoundtrip = 343217L;
                expDorSample = 40.309438;
                expDomSample = 40.123814;
                break;
            case 4:
                expDOMT0 = 41084657360722671L;
                expDelta = 71043438094500L;
                expRoundtrip = 343183L;
                expDorSample = 39.681948;
                expDomSample = 40.682129;
                break;
            case 5:
                expDOMT0 = 41084667737700644L;
                expDelta = 71043438077063L;
                expRoundtrip = 343200L;
                expDorSample = 39.824808;
                expDomSample = 40.574630;
                break;
            case 6:
                expDOMT0 = 41084480352916878L;
                expDelta = 71637693619208L;
                expRoundtrip = 258432L;
                expDorSample = 40.348788;
                expDomSample = 39.513799;
                break;
            case 7:
                expDOMT0 = 41084490909458106L;
                expDelta = 71637693652760L;
                expRoundtrip = 258443L;
                expDorSample = 39.461843;
                expDomSample = 40.422607;
                break;
            default:
                throw new Error("Unknown TCalData entry #" + i);
            }

            checkFit(wfit, i, expDOMT0, expDelta, expRoundtrip, expDorSample,
                     expDomSample);
        }
    }

    public void testThreshold()
        throws AlertException, PayloadException
    {
        final boolean verbose = false;

        for (int i = 0; i < TCalDataFactory.size(); i++) {
            TCalData td = TCalDataFactory.get(i);

            WaveformFit wfit =
                new WaveformFitThreshold(td.getDorTXTime(), td.getDorRXTime(),
                                         td.getDorWaveform(),
                                         td.getDomRXTime(), td.getDomTXTime(),
                                         td.getDomWaveform(), verbose);

            long expDOMT0, expDelta, expRoundtrip;
            double expDorSample, expDomSample;
            switch (i) {
            case 0:
                expDOMT0 = 41084625824943697L;
                expDelta = 71043438147409L;
                expRoundtrip = 331106L;
                expDorSample = 27.423775;
                expDomSample = 28.787081;
                break;
            case 1:
                expDOMT0 = 41084427753308741L;
                expDelta = 71637693451906L;
                expRoundtrip = 247277L;
                expDorSample = 28.589335;
                expDomSample = 28.963636;
                break;
            case 2:
                expDOMT0 = 41084636467136639L;
                expDelta = 71043438129523L;
                expRoundtrip = 331103L;
                expDorSample = 27.649274;
                expDomSample = 28.555692;
                break;
            case 3:
                expDOMT0 = 41084646770106539L;
                expDelta = 71043438112220L;
                expRoundtrip = 331096L;
                expDorSample = 28.034260;
                expDomSample = 28.156100;
                break;
            case 4:
                expDOMT0 = 41084657360719686L;
                expDelta = 71043438094437L;
                expRoundtrip = 331118L;
                expDorSample = 27.490741;
                expDomSample = 28.743421;
                break;
            case 5:
                expDOMT0 = 41084667737697650L;
                expDelta = 71043438077006L;
                expRoundtrip = 331115L;
                expDorSample = 27.627495;
                expDomSample = 28.601504;
                break;
            case 6:
                expDOMT0 = 41084480352914142L;
                expDelta = 71637693619093L;
                expRoundtrip = 247255L;
                expDorSample = 28.942242;
                expDomSample = 28.566277;
                break;
            case 7:
                expDOMT0 = 41084490909455359L;
                expDelta = 71637693652651L;
                expRoundtrip = 247237L;
                expDorSample = 28.038313;
                expDomSample = 29.434698;
                break;
            default:
                throw new Error("Unknown TCalData entry #" + i);
            }

            checkFit(wfit, i, expDOMT0, expDelta, expRoundtrip, expDorSample,
                     expDomSample);
        }
    }

    public void testIntercept()
        throws AlertException, PayloadException
    {
        final boolean verbose = false;

        for (int i = 0; i < TCalDataFactory.size(); i++) {
            TCalData td = TCalDataFactory.get(i);

            WaveformFit wfit =
                new WaveformFitIntercept(td.getDorTXTime(), td.getDorRXTime(),
                                         td.getDorWaveform(),
                                         td.getDomRXTime(), td.getDomTXTime(),
                                         td.getDomWaveform(), verbose);

            long expDOMT0, expDelta, expRoundtrip;
            double expDorSample, expDomSample;
            switch (i) {
            case 0:
                expDOMT0 = 41084625824942690L;
                expDelta = 71043438147456L;
                expRoundtrip = 327174L;
                expDorSample = 23.586194;
                expDomSample = 24.760924;
                break;
            case 1:
                expDOMT0 = 41084427753307868L;
                expDelta = 71637693452018L;
                expRoundtrip = 244012L;
                expDorSample = 25.548276;
                expDomSample = 25.473881;
                break;
            case 2:
                expDOMT0 = 41084636467135607L;
                expDelta = 71043438129563L;
                expRoundtrip = 327057L;
                expDorSample = 23.682810;
                expDomSample = 24.429679;
                break;
            case 3:
                expDOMT0 = 41084646770105513L;
                expDelta = 71043438112286L;
                expRoundtrip = 327123L;
                expDorSample = 24.195475;
                expDomSample = 24.050397;
                break;
            case 4:
                expDOMT0 = 41084657360718668L;
                expDelta = 71043438094483L;
                expRoundtrip = 327138L;
                expDorSample = 23.602941;
                expDomSample = 24.671892;
                break;
            case 5:
                expDOMT0 = 41084667737696606L;
                expDelta = 71043438077060L;
                expRoundtrip = 327046L;
                expDorSample = 23.666467;
                expDomSample = 24.424731;
                break;
            case 6:
                expDOMT0 = 41084480352913272L;
                expDelta = 71637693619188L;
                expRoundtrip = 243964L;
                expDorSample = 25.841233;
                expDomSample = 25.086098;
                break;
            case 7:
                expDOMT0 = 41084490909454491L;
                expDelta = 71637693652747L;
                expRoundtrip = 243957L;
                expDorSample = 24.949938;
                expDomSample = 25.963773;
                break;
            default:
                throw new Error("Unknown TCalData entry #" + i);
            }

            checkFit(wfit, i, expDOMT0, expDelta, expRoundtrip, expDorSample,
                     expDomSample);
        }
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
