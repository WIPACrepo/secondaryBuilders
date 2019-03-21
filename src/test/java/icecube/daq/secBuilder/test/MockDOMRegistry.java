package icecube.daq.secBuilder.test;

import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.DOMInfo;


import java.util.HashMap;
import java.util.Set;

public class MockDOMRegistry
    implements IDOMRegistry
{
    private HashMap<Long, DOMInfo> doms =
        new HashMap<Long, DOMInfo>();

    public void addDom(long mbId, int string, int position)
    {
        addDom(mbId, string, position, string);
    }

    public void addDom(long mbId, int string, int position, int hub)
    {
        DOMInfo dom = new DOMInfo(mbId, string, position, hub);
        doms.put(mbId, dom);
    }

    @Override
    public Iterable<DOMInfo> allDOMs()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public double distanceBetweenDOMs(DOMInfo dom0, DOMInfo dom1)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public double distanceBetweenDOMs(short chan0, short chan1)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public short getChannelId(long mbId)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public DOMInfo getDom(long mbId)
    {
        return doms.get(mbId);
    }

    @Override
    public DOMInfo getDom(int major, int minor)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public DOMInfo getDom(short chanid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public Set<DOMInfo> getDomsOnHub(int hubId)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public Set<DOMInfo> getDomsOnString(int string)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public String getName(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public String getProductionId(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getStringMajor(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getStringMinor(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int size()
    {
        throw new Error("Unimplemented");
    }
}
