/**
 * SecBuilderMonitor
 * Date: Oct 28, 2005 1:26:43 PM
 *
 * (c) 2005 IceCube Collaboration
 */
package icecube.daq.secBuilder;

import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.StreamMetaData;
import icecube.daq.splicer.Splicer;

public class SecBuilderMonitor implements SecBuilderMonitorMBean
{

    private String dataType;
    private DAQComponentInputProcessor inputProcessor;
    private Splicer splicer;
    private Dispatcher dispatcher;

    public SecBuilderMonitor(String dataType,
        DAQComponentInputProcessor inputProcessor,
            Splicer splicer, Dispatcher dispatcher)
    {

        if (dataType == null) {
            throw new RuntimeException("dataType should not be null!!!");
        }
        this.dataType = dataType;

        if (inputProcessor == null) {
            throw new RuntimeException("inputProcessor should not be null!!!");
        }
        this.inputProcessor = inputProcessor;

        if (splicer == null) {
            throw new RuntimeException("splicer should not be null!!!");
        }
        this.splicer = splicer;

        if (dispatcher == null) {
            throw new RuntimeException("dispatcher should not be null!!!");
        }
        this.dispatcher = dispatcher;
    }

    @Override
    public long[] getEventData()
    {
        int runNum = dispatcher.getRunNumber();
        StreamMetaData metadata = dispatcher.getMetaData();
        return new long[] { runNum, metadata.getCount(), metadata.getTicks() };
    }

    /**
     * Get the number of Strands connected to the input channels
     * @return an int value
     */
    @Override
    public int getStrandCount()
    {
        return splicer.getStrandCount();
    }

    /**
     * Get the amount of dispatched data for the current run
     * @return a long value
     */
    @Override
    public long getNumDispatchedData()
    {
        return dispatcher.getNumDispatchedEvents();
    }

    /**
     * Get the total dispatched data since this component has started
     * @return a long value
     */
    @Override
    public long getTotalDispatchedData()
    {
        return dispatcher.getTotalDispatchedEvents();
    }

    /**
     * Returns the number of units still available in the disk (measured in MB)
     * If it fails to check the disk space, then it returns -1.
     *
     * @return the number of units still available in the disk.
     */
    @Override
    public long getDiskAvailable()
    {
        return dispatcher.getDiskAvailable();
    }

    /**
     * Returns the total number of units in the disk (measured in MB).
     * If it fails to check the disk space, then it returns -1.
     *
     * @return the total number of units in the disk.
     */
    @Override
    public long getDiskSize()
    {
        return dispatcher.getDiskSize();
    }
}
