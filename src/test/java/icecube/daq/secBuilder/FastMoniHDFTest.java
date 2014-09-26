package icecube.daq.secBuilder;

import java.io.File;

import icecube.daq.secBuilder.test.MockAppender;
import icecube.daq.secBuilder.test.MockDispatcher;

import org.apache.log4j.BasicConfigurator;

import org.junit.*;
import static org.junit.Assert.*;

public class FastMoniHDFTest
{
    private static final MockAppender appender =
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        new MockAppender(org.apache.log4j.Level.WARN).setVerbose(false);

    private static final String tempDir = System.getProperty("java.io.tmpdir");

    @Before
    public void setUp()
        throws Exception
    {
        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @After
    public void tearDown()
        throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());
    }

    @Test
    public void testHdfClass()
        throws Exception
    {
        final int runNum = 12345;

        MockDispatcher disp = new MockDispatcher();
        disp.setDispatchDestStorage(tempDir);

        FastMoniHDF hfm;
        try {
            hfm = new FastMoniHDF(disp, runNum);
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Skipping FastMoniHDF tests");
            return;
        }

        int[] data = new int[FastMoniHDF.WIDTH];

        for (int j = 0; j < 4; j++) {
            // fill the data
            for (int w = 0; w < FastMoniHDF.WIDTH; w++) {
                data[w] = w + j * 10;
            }

            if (j == 2) {
                hfm.switchToNewRun(runNum + 1);
            }

            hfm.write(data);
        }

        hfm.close();
    }
}
