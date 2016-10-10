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

    public double distanceBetweenDOMs(DOMInfo dom0, DOMInfo dom1)
    {
        throw new Error("Unimplemented");
    }

    public double distanceBetweenDOMs(long mbid0, long mbid1)
    {
        throw new Error("Unimplemented");
    }

    public short getChannelId(long mbId)
    {
        throw new Error("Unimplemented");
    }

    public DOMInfo getDom(long mbId)
    {
        return doms.get(mbId);
    }

    public DOMInfo getDom(int major, int minor)
    {
        throw new Error("Unimplemented");
    }

    public DOMInfo getDom(short chanid)
    {
        throw new Error("Unimplemented");
    }

    public Set<DOMInfo> getDomsOnHub(int hubId)
    {
        throw new Error("Unimplemented");
    }

    public Set<DOMInfo> getDomsOnString(int string)
    {
        throw new Error("Unimplemented");
    }

    public String getName(long mbid)
    {
        throw new Error("Unimplemented");
    }

    public String getProductionId(long mbid)
    {
        throw new Error("Unimplemented");
    }

    public int getStringMajor(long mbid)
    {
        throw new Error("Unimplemented");
    }

    public int getStringMinor(long mbid)
    {
        throw new Error("Unimplemented");
    }

    public Set<Long> keys()
    {
        throw new Error("Unimplemented");
    }

    public int size()
    {
        throw new Error("Unimplemented");
    }
}
