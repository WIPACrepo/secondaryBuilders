package icecube.daq.secBuilder.test;

import icecube.daq.payload.PayloadInterfaceRegistry;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayloadDestination;
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

    public int compareTo(Object obj)
    {
        throw new Error("Unimplemented");
    }

    public Object deepCopy()
    {
        throw new Error("Unimplemented");
    }

    public void dispose()
    {
        throw new Error("Unimplemented");
    }

    public boolean equals(Object obj)
    {
        return compareTo(obj) == 0;
    }

    public ByteBuffer getPayloadBacking()
    {
        throw new Error("Unimplemented");
    }

    public int getPayloadLength()
    {
        return length();
    }

    public IUTCTime getPayloadTimeUTC()
    {
        throw new Error("Unimplemented");
    }

    public int getPayloadType()
    {
        throw new Error("Unimplemented");
    }

    public long getUTCTime()
    {
        throw new Error("Unimplemented");
    }

    public int hashCode()
    {
        throw new Error("Unimplemented");
    }

    public int length()
    {
        throw new Error("Unimplemented");
    }

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

    public void recycle()
    {
        // do nothing
    }

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

    public int writePayload(boolean writeLoaded, IPayloadDestination dest)
        throws IOException
    {
        throw new Error("Unimplemented");
    }

    public int writePayload(boolean writeLoaded, int offset, ByteBuffer buf)
        throws IOException
    {
        throw new Error("Unimplemented");
    }
}
