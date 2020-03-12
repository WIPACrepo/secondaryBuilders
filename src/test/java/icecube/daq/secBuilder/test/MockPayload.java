package icecube.daq.secBuilder.test;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadFormatException;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class MockPayload
    implements Comparable, ILoadablePayload, IWriteablePayload
{
    private PayloadFormatException loadPFException;
    private IOException loadIOException;

    public MockPayload()
    {
    }

    @Override
    public int compareTo(Object obj)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public Object deepCopy()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void dispose()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public boolean equals(Object obj)
    {
        return compareTo(obj) == 0;
    }

    @Override
    public ByteBuffer getPayloadBacking()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public IUTCTime getPayloadTimeUTC()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getPayloadType()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getUTCTime()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int hashCode()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int length()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void loadPayload()
        throws IOException, PayloadFormatException
    {
        if (loadPFException != null) {
            throw loadPFException;
        } else if (loadIOException != null) {
            throw loadIOException;
        }

        // do nothing
    }

    @Override
    public void recycle()
    {
        // do nothing
    }

    @Override
    public void setCache(IByteBufferCache bufCache)
    {
        throw new Error("Unimplemented");
    }

    public void setLoadPayloadException(Exception ex)
    {
        if (ex instanceof PayloadFormatException) {
            loadPFException = (PayloadFormatException) ex;
        } else if (ex instanceof IOException) {
            loadIOException = (IOException) ex;
        } else {
            throw new Error("Unknown exception type " +
                            ex.getClass().getName() + ": " + ex);
        }
    }

    @Override
    public int writePayload(boolean writeLoaded, int offset, ByteBuffer buf)
        throws IOException
    {
        throw new Error("Unimplemented");
    }
}
