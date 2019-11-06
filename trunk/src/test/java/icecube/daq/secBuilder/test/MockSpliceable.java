package icecube.daq.secBuilder.test;

import icecube.daq.splicer.Spliceable;

import java.nio.ByteBuffer;

public class MockSpliceable
    implements Spliceable
{
    private static int nextId = 1;

    private int id;
    private ByteBuffer bBuf;

    public MockSpliceable(ByteBuffer bBuf)
    {
        id = nextId++;
        this.bBuf = bBuf;
    }

    @Override
    public int compareSpliceable(Spliceable spl)
    {
        if (spl == null) {
            return -1;
        }

        if (!(spl instanceof MockSpliceable)) {
            return getClass().getName().compareTo(spl.getClass().getName());
        }

        return id - ((MockSpliceable) spl).id;
    }

    public ByteBuffer getByteBuffer()
    {
        return bBuf;
    }
}
