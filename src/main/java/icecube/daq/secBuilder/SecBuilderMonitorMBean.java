package icecube.daq.secBuilder;

/**
 * Provides the methods for monitoring the secondaryBuilders
 */
public interface SecBuilderMonitorMBean
{
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

    /**
     * Return the number of events and the last event time as a list.
     *
     * @return event data
     */
    long[] getEventData();

    /**
     * Get the amount of dispatched data for the current run
     * @return a long value
     */
    long getNumDispatchedData();

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
}
