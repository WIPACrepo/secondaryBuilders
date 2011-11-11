package icecube.daq.secBuilder;

/**
 * Provides the methods for monitoring the secondaryBuilders
 */
public interface SecBuilderMonitorMBean
{

    /**
     * Get the type of data (i.e. tcal, sn, moni)
     * @return a String object
     */
    //String getDataType();

    /**
     * Get the state of the input processor for the secondary builder
     * @return a String object
     */
    //String getInputProcessorState();

    /**
     * Get the state of the Splicer
     * @return a String object
     */
    //String getSplicerState();

    /**
     * Get the number of Strands connected to the input channels
     * @return an int value
     */
    int getStrandCount();

    /**
     * Get the total of the dispatched data
     * @return a long value
     */
    long getTotalDispatchedData();

    /**
     * Returns the number of units still available in the disk (measured in MB).
     * If it fails to check the disk space, then it returns -1.
     *
     * @return the number of units still available in the disk.
     */
    long getDiskAvailable();

    /**
     * Returns the total number of units in the disk (measured in MB).
     * If it fails to check the disk space, then it returns -1.
     *
     * @return the total number of units in the disk.
     */
    long getDiskSize();
}
