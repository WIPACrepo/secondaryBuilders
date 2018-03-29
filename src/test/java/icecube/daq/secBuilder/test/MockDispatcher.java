package icecube.daq.secBuilder.test;

import icecube.daq.io.DispatchException;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.StreamMetaData;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IEventPayload;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MockDispatcher
    implements Dispatcher
{
    private int numSeen;
    private int numBad;
    private boolean dispatchError;
    private boolean readOnly;
    private boolean started;
    private File dispatchDir;

    public MockDispatcher()
    {
    }

    public void close()
        throws DispatchException
    {
        // do nothing
    }

    public void dataBoundary()
        throws DispatchException
    {
        throw new Error("Unimplemented");
    }

    public void dataBoundary(String msg)
        throws DispatchException
    {
        if (msg.startsWith(START_PREFIX)) {
            if (started) {
                throw new Error("Dispatcher has already been started");
            }

            started = true;
        } else if (msg.startsWith(STOP_PREFIX)) {
            if (!started) {
                throw new Error("Dispatcher is already stopped");
            }

            started = false;
        }
    }

    public void dispatchEvent(ByteBuffer buf, long ticks)
        throws DispatchException
    {
        throw new Error("Unimplemented");
    }

    public void dispatchEvent(IWriteablePayload pay)
        throws DispatchException
    {
        numSeen++;

        if (!PayloadChecker.validateEvent((IEventPayload) pay, true)) {
            numBad++;
        }

        if (readOnly) {
            IOException ioe = new IOException("Read-only file system");
            throw new DispatchException("Could not dispatch event", ioe);
        }

        if (dispatchError) {
            IOException ioe = new IOException("Bad file channel");
            throw new DispatchException("Could not dispatch event", ioe);
        }
    }

    public IByteBufferCache getByteBufferCache()
    {
        throw new Error("Unimplemented");
    }

    public File getDispatchDestStorage()
    {
        return dispatchDir;
    }

    public long getDiskAvailable()
    {
        return 0;
    }

    public long getDiskSize()
    {
        return 0;
    }

    public long getFirstDispatchedTime()
    {
        return Long.MIN_VALUE;
    }

    public StreamMetaData getMetaData()
    {
        return new StreamMetaData(numSeen, 0L);
    }

    public long getNumBytesWritten()
    {
        return 0;
    }

    public long getNumDispatchedEvents()
    {
        return numSeen;
    }

    public int getNumberOfBadEvents()
    {
        return numBad;
    }

    public int getRunNumber()
    {
        throw new Error("Unimplemented");
    }

    public long getTotalDispatchedEvents()
    {
        return numSeen;
    }

    public boolean isStarted()
    {
        return started;
    }

    public void setDispatchDestStorage(String destDir)
    {
        dispatchDir = new File(destDir);
    }

    public void setDispatchError(boolean dispatchError)
    {
        this.dispatchError = dispatchError;
    }

    public void setMaxFileSize(long x0)
    {
        throw new Error("Unimplemented");
    }

    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    public String toString()
    {
        if (numBad == 0) {
            return "Dispatcher saw " + numSeen + " payloads";
        }

        return "Dispatcher saw " + numBad + " bad payloads (of " + numSeen +
            ")";
    }
}
