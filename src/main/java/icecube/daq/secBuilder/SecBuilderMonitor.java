/**
 * SecBuilderMonitor
 * Date: Oct 28, 2005 1:26:43 PM
 *
 * (c) 2005 IceCube Collaboration
 */
package icecube.daq.secBuilder;

import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.Dispatcher;
import icecube.daq.splicer.Splicer;

public class SecBuilderMonitor implements SecBuilderMonitorMBean {

    private String dataType;
    private DAQComponentInputProcessor inputProcessor;
    private Splicer splicer;
    private Dispatcher dispatcher;

    public SecBuilderMonitor(String dataType, DAQComponentInputProcessor inputProcessor,
                             Splicer splicer, Dispatcher dispatcher) {

        if (dataType == null){
            throw new RuntimeException("dataType should not be null!!!");
        }
        this.dataType = dataType;

        if (inputProcessor == null){
            throw new RuntimeException("inputProcessor should not be null!!!");
        }
        this.inputProcessor = inputProcessor;

        if (splicer == null){
            throw new RuntimeException("splicer should not be null!!!");
        }
        this.splicer = splicer;

        if (dispatcher == null){
            throw new RuntimeException("dispatcher should not be null!!!");
        }
        this.dispatcher = dispatcher;
    }

    /**
     * Get the type of data (i.e. tcal, sn, moni)
     * @return a String object
     */
    public String getDataType(){
        return dataType;
    }

    /**
     * Get the state of the input processor for the secondary builder
     * @return a String object
     */
    public String getInputProcessorState(){
        return inputProcessor.getPresentState();
    }

    /**
     * Get the state of the Splicer
     * @return a String object
     */
    public String getSplicerState(){
        return splicer.getStateString();
    }

    /**
     * Get the number of Strands connected to the input channels
     * @return an int value
     */
    public int getStrandCount(){
        return splicer.getStrandCount();
    }

    /**
     * Get the total of the dispatched data
     * @return a long value
     */
    public long getTotalDispatchedData(){
        return dispatcher.getTotalDispatchedEvents();
    }

    /**
     * Returns the number of units still available in the disk (measured in MB).
     * If it fails to check the disk space, then it returns -1.
     *
     * @return the number of units still available in the disk.
     */
    public long getDiskAvailable(){
        return dispatcher.getDiskAvailable();
    }

    /**
     * Returns the total number of units in the disk (measured in MB).
     * If it fails to check the disk space, then it returns -1.
     *
     * @return the total number of units in the disk.
     */
    public long getDiskSize(){
        return dispatcher.getDiskSize();
    }
}
