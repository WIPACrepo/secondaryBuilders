package icecube.daq.secBuilder.test;

import icecube.daq.io.DispatchException;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.StreamMetaData;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IEventPayload;
import icecube.daq.payload.IPayload;
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

    @Override
    public void close()
        throws DispatchException
    {
        // do nothing
    }

    @Override
    public void dataBoundary()
        throws DispatchException
    {
        throw new Error("Unimplemented");
    }

    @Override
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

    @Override
    public void dispatchEvent(IPayload pay)
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

    @Override
    public IByteBufferCache getByteBufferCache()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getDiskAvailable()
    {
        return 0;
    }

    @Override
    public long getDiskSize()
    {
        return 0;
    }

    @Override
    public File getDispatchDestStorage()
    {
        return dispatchDir;
    }

    @Override
    public long getFirstDispatchedTime()
    {
        return Long.MIN_VALUE;
    }

    @Override
    public StreamMetaData getMetaData()
    {
        return new StreamMetaData(numSeen, 0L);
    }

    @Override
    public long getNumBytesWritten()
    {
        return 0;
    }

    @Override
    public long getNumDispatchedEvents()
    {
        return numSeen;
    }

    public int getNumberOfBadEvents()
    {
        return numBad;
    }

    @Override
    public int getRunNumber()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getTotalDispatchedEvents()
    {
        return numSeen;
    }

    @Override
    public boolean isStarted()
    {
        return started;
    }

    @Override
    public void setDispatchDestStorage(String destDir)
    {
        dispatchDir = new File(destDir);
    }

    public void setDispatchError(boolean dispatchError)
    {
        this.dispatchError = dispatchError;
    }

    @Override
    public void setMaxFileSize(long x0)
    {
        throw new Error("Unimplemented");
    }

    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    @Override
    public String toString()
    {
        if (numBad == 0) {
            return "Dispatcher saw " + numSeen + " payloads";
        }

        return "Dispatcher saw " + numBad + " bad payloads (of " + numSeen +
            ")";
    }
}
