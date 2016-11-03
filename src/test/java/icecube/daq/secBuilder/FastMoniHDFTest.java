package icecube.daq.secBuilder;

import icecube.daq.common.MockAppender;
import icecube.daq.secBuilder.test.MockDispatcher;

import java.io.File;

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
        if (appender.getNumberOfMessages() > 0) {
            // ignore errors about missing HDF5 library
            for (int i = 0; i < appender.getNumberOfMessages(); i++) {
                final String msg = (String) appender.getMessage(i);
                if (!msg.startsWith("Cannot find HDF library;") &&
                    !msg.contains("was not moved to the dispatch storage"))
                {
                    fail("Unexpected log message " + i + ": " +
                         appender.getMessage(i));
                }
            }
        }
    }

    @Test
    public void testHdfClass()
        throws Exception
    {
        if (!FastMoniHDF.checkForLibrary()) {
            System.err.println("Skipping FastMoniHDF tests");
            return;
        }

        final int runNum = 12345;

        MockDispatcher disp = new MockDispatcher();
        disp.setDispatchDestStorage(tempDir);

        FastMoniHDF hfm = new FastMoniHDF(disp, runNum);

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
