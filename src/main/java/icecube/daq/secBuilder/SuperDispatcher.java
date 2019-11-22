package icecube.daq.secBuilder;

import icecube.daq.io.DispatchException;
import icecube.daq.io.FileDispatcher;
import icecube.daq.payload.IByteBufferCache;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * Enhanced file dispatcher which writes sentinal files so Process2ndBuild
 * knows it should save all secondary stream files for the run
 */
class SuperDispatcher
    extends FileDispatcher
{
    private static final Logger LOG = Logger.getLogger(SuperDispatcher.class);

    private boolean supersaver;

    public SuperDispatcher(String baseFileName, IByteBufferCache bufferCache) {
        super(baseFileName, bufferCache);
    }

    public SuperDispatcher(String destDir, String baseFileName,
                           IByteBufferCache bufferCache) {
        super(destDir, baseFileName, bufferCache);
    }

    /**
     * Create a sentinal file which signals to Process2ndBuild that it
     * should save the files from this run.  If the file exists and
     * 'update' is true, update the file's timestamp
     *
     * @param baseName base name of the sentinal file\
     * @param runNumber run number to use as sentinal name suffix
     * @param update if true, update an existing sentinal file's timestamp
     *
     * @throws DispatchException if the sentinal file could not be created
     */
    private void createSentinalFile(String baseName, int runNumber,
                                    boolean update)
        throws DispatchException
    {
        File sentinal = new File(getDispatchDestStorage(),
                                 baseName + "." + runNumber);
        try {
            if (!sentinal.exists()) {
                sentinal.createNewFile();
            } else if (update) {
                sentinal.setLastModified(System.currentTimeMillis());
            }
        } catch (IOException ioe) {
            throw new DispatchException("Cannot create SuperSaver sentinal" +
                                        " file " + sentinal, ioe);
        } catch (SecurityException sex) {
            throw new DispatchException("Cannot write to SuperSaver sentinal" +
                                        " file " + sentinal, sex);
        }
        LOG.error("Created " + sentinal.getName());
    }

    /**
     * If set to 'true', create sentinal files when starting and stopping
     * to signal that we've started or stopped writing data to be saved
     */
    public void setSuperSaver(boolean value)
    {
        supersaver = value;
    }

    @Override
    public void startDispatch(String runStr, boolean switching)
        throws DispatchException
    {
        if (supersaver) {
            int newNumber;
            try {
                newNumber = Integer.parseInt(runStr);
            } catch (java.lang.NumberFormatException nfe) {
                throw new DispatchException("Bad run number \"" + runStr +
                                            "\"");
            }

            createSentinalFile("supersaver", newNumber, false);

            try {
                Thread.sleep(20);
            } catch (InterruptedException iex) {
                // ignore interrupts
            }
        }

        super.startDispatch(runStr, switching);
    }

    @Override
    public void stopDispatch()
        throws DispatchException
    {
        super.stopDispatch();

        if (supersaver) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException iex) {
                // ignore interrupts
            }

            // create sentinal file or update the file's modification time
            createSentinalFile("supersaved", getRunNumber(), true);
        }
    }
}
