package icecube.daq.secBuilder;

import icecube.daq.io.DispatchException;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.secBuilder.test.LoggingCase;
import icecube.daq.secBuilder.test.MockBufferCache;
import icecube.daq.secBuilder.test.MockPayload;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

class DummyPayload
    extends MockPayload
{
    DummyPayload()
    {
        super();
    }

    @Override
    public long getUTCTime()
    {
        return 1234567890L;
    }

    @Override
    public int length()
    {
        return 1;
    }

    @Override
    public int writePayload(boolean writeLoaded, int offset, ByteBuffer buf)
        throws IOException
    {
        // pretend that we wrote the appropriate number of bytes
        return length();
    }
}

public class SuperDispatcherTest
    extends LoggingCase
{
    private File testDirectory;

    /**
     * Constructs an instance of this test.
     *
     * @param name the name of the test.
     */
    public SuperDispatcherTest(String name)
    {
        super(name);
    }

    private static boolean clearDirectory(File dir)
    {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDirectory(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        return true;
    }

    public static File createTempDirectory()
        throws IOException
    {
        final File temp;

        temp = File.createTempFile("sdtest", "dir");

        if (!(temp.delete()))
        {
            throw new IOException("Could not delete temp file: " +
                                  temp.getAbsolutePath());
        }

        if (!(temp.mkdir()))
        {
            throw new IOException("Could not create temp directory: " +
                                  temp.getAbsolutePath());
        }

        return temp;
    }

    private static boolean deleteDirectory(File dir)
    {
        if (!clearDirectory(dir)) {
            return false;
        }

        return dir.delete();
    }

    private static void listDirectory(File dir, String title)
    {
        if (title == null) {
            title = "";
        }

        System.out.println("===== " + title);
        for (String name : dir.list()) {
            System.out.println("  " + name);
        }
    }

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        File tempFile = new File(SuperDispatcher.TEMP_PREFIX + "physics");
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        File tempFile = new File(SuperDispatcher.TEMP_PREFIX + "physics");
        if (tempFile.exists()) {
            tempFile.delete();
        }

        if (testDirectory != null) {
            if (!deleteDirectory(testDirectory)) {
                System.err.println("Couldn't tear down test directory");
            }
        }

        super.tearDown();
    }

    /**
     * Create test suite for this class.
     *
     * @return the suite of tests declared in this class.
     */
    public static Test suite()
    {
        return new TestSuite(SuperDispatcherTest.class);
    }

    public void testDispatchEvent()
        throws DispatchException
    {
        final String runNumber = "123456";

        try {
            testDirectory = createTempDirectory();
        } catch (IOException ioe) {
            fail("Cannot create temporary directory");
        }

        IByteBufferCache bufCache = new MockBufferCache("DispEvt");

        final String tstDir = testDirectory.getAbsolutePath();
        SuperDispatcher sdisp =
            new SuperDispatcher(tstDir, "physics", bufCache);
        assertNoLogMessages();

        assertNotNull("ByteBuffer was null", sdisp.getByteBufferCache());

        assertEquals("Total dispatched events is not zero",
                     0, sdisp.getTotalDispatchedEvents());

        sdisp.setSuperSaver(true);

        final File startSentinal =
            new File(testDirectory, "supersaver." + runNumber);
        final File stopSentinal =
            new File(testDirectory, "supersaved." + runNumber);
        assertFalse("Found starting sentinal file before start",
                    startSentinal.exists());
        assertFalse("Found stopping sentinal file before start",
                    stopSentinal.exists());

        sdisp.startDispatch(runNumber, false);
        assertTrue("Starting sentinal file was not created by start",
                    startSentinal.exists());
        assertFalse("Stopping sentinal file was created by start",
                    stopSentinal.exists());

        sdisp.dispatchEvent(new DummyPayload());
        assertTrue("Starting sentinal file was destroyed by dispatch",
                    startSentinal.exists());
        assertFalse("Stopping sentinal file was created by dispatch",
                    stopSentinal.exists());

        sdisp.stopDispatch();
        assertTrue("Starting sentinal file was destroyed by stop",
                    startSentinal.exists());
        assertTrue("Stopping sentinal file was not created by stop",
                    stopSentinal.exists());

        assertEquals("Total dispatched events was not incremented",
                     1, sdisp.getTotalDispatchedEvents());
    }

    /**
     * Main routine which runs tests in standalone mode.
     *
     * @param args the arguments with which to execute this method.
     */
    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
