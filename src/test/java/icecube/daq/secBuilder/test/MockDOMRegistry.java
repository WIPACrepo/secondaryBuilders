package icecube.daq.secBuilder.test;

import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.DeployedDOM;


import java.util.HashMap;
import java.util.Set;

public class MockDOMRegistry
    implements IDOMRegistry
{
    private HashMap<String, DeployedDOM> doms =
        new HashMap<String, DeployedDOM>();

    public void addDom(long mbId, int string, int position)
    {
        DeployedDOM dom = new DeployedDOM(mbId, string, position);
        doms.put(dom.getMainboardId(), dom);
    }

    public short getChannelId(String mbid)
    {
        throw new Error("Unimplemented");
    }

    public DeployedDOM getDom(String mbId)
    {
        return doms.get(mbId);
    }

    public DeployedDOM getDom(short chanid)
    {
        throw new Error("Unimplemented");
    }

    public int getStringMajor(String mbid)
    {
        throw new Error("Unimplemented");
    }

    public Set<String> keys()
    {
        throw new Error("Unimplemented");
    }

    public double distanceBetweenDOMs(String mbid0, String mbid1)
    {
        throw new Error("Unimplemented");
    }
}
