package icecube.daq.secBuilder;

/**
 * Provides the methods for monitoring the secondaryBuilders
 */
public interface SecBuilderMonitorMBean {

    /**
     * Get the type of data (i.e. tcal, sn, moni)
     * @return a String object
     */
    public String getDataType();

    /**
     * Get the state of the input processor for the secondary builder
     * @return a String object
     */
    public String getInputProcessorState();

    /**
     * Get the state of the Splicer
     * @return a String object
     */
    public String getSplicerState();

    /**
     * Get the number of Strands connected to the input channels
     * @return an int value
     */
    public int getStrandCount();

    /**
     * Get the total of the dispatched data
     * @return a long value
     */ 
    public long getTotalDispatchedData();
}