/**
 * Class: SBSplicedAnalysis
 *
 * Date: Nov 28, 2006 1:28:20 PM
 * (c) 2005 IceCube collaboration
 */
package icecube.daq.secBuilder;

import icecube.daq.io.DispatchException;
import icecube.daq.io.Dispatcher;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.splicer.Spliceable;
import icecube.daq.splicer.SpliceableFactory;
import icecube.daq.splicer.SplicedAnalysis;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerChangedEvent;
import icecube.daq.splicer.SplicerListener;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is the the SplicedAnalysis that can be used with
 * all secondary builders (monitor, sn, and tcal)
 *
 * @author artur
 * @version $Id: SBSplicedAnalysis.java,v 1.0 2006/03/24 13:28:20 artur Exp $
 */
public class SBSplicedAnalysis implements SplicedAnalysis, SplicerListener
{

    private SpliceableFactory spliceableFactory;
    private Dispatcher dispatcher;
    private Splicer splicer;
    private int start;
    private int lastInputListSize;
    private int runNumber;
    private boolean reportedError;
    private String streamName = "noname";
    private boolean preScaling = false;
    private long preScale = 1;
    private long preScaleCount = 1;

    private Log log = LogFactory.getLog(SBSplicedAnalysis.class);

    public SBSplicedAnalysis(SpliceableFactory factory, Dispatcher dispatcher,
                             SecBuilderMonitor secBuilderMonitor)
    {
        if (factory == null) {
            log.error("SpliceableFactory is null");
            throw new IllegalArgumentException(
                "SpliceableFactory cannot be null");
        }
        this.spliceableFactory = factory;

        if (dispatcher == null) {
            log.error("Dispatcher is null");
            throw new IllegalArgumentException("Dispatcher cannot be null");
        }
        this.dispatcher = dispatcher;
    }

    /**
     * Called by the {@link icecube.daq.splicer.Splicer Splicer} to analyze the
     * List of Spliceable objects provided.
     *
     * @param splicedObjects a List of Spliceable objects.
     * @param decrement the decrement of the indices in the List since the last
     * invocation.
     */
    public void execute(List splicedObjects, int decrement)
    {
        // Loop over the new objects in the splicer
        int numberOfObjectsInSplicer = splicedObjects.size();
        lastInputListSize = numberOfObjectsInSplicer - (start - decrement);

        if (log.isDebugEnabled()) {
            log.debug("Splicer " + streamName + " contains: [" +
                lastInputListSize + ":" + numberOfObjectsInSplicer + "]");
        }

        for (int index = start - decrement; index < numberOfObjectsInSplicer;
            index++)
        {

            IPayload payload = (IPayload) splicedObjects.get(index);
            ByteBuffer buf  = payload.getPayloadBacking();
            buf.limit(buf.getInt(0));
            if (log.isDebugEnabled()) {
                int recl = buf.getInt(0);
                int limit = buf.limit();
                int capacity = buf.capacity();
                log.debug("Writing " + streamName + " byte buffer - RECL=" +
                        recl + " LIMIT=" +
                        limit + " CAP=" +
                        capacity
                );
            }
            try {
                dispatchEvent(buf);
            } catch (DispatchException de) {
                if (log.isErrorEnabled() && !reportedError) {
                    log.error("couldn't dispatch the " + streamName +
                        " payload: ", de);
                    reportedError = true;
                }
            }
        }

        start = numberOfObjectsInSplicer;

        // call truncate on splicer
        if (splicedObjects.size() > 0) {
            Spliceable update = (Spliceable) splicedObjects.get(start - 1);
            if (null != update) {
                if (log.isDebugEnabled()) {
                    log.debug("Truncating " + streamName + " splicer: " +
                        update);
                }
                splicer.truncate(update);
            }
        }
    }

    /**
     * Set the prescale factor - let through only every 'preScale'
     * events (default=1)
     *
     * @param preScale the number of events to discard between letting
     * one through.
     */
    void setPreScale(long preScale)
    {
        if (preScale <= 0L) {
            throw new IllegalArgumentException("Bad " + streamName +
                " prescale value: " + preScale);
        }
        // preScale is now >= 1

        if (log.isInfoEnabled()) {
            log.info("Setting " + streamName + " prescale to " + preScale);
        }

        // Setting to 1 => turning off preScaling
        this.preScaling = (preScale > 1);
        this.preScale = preScale;
        this.preScaleCount = 1;
    }


    /**
     * Dispatch an event taking into account any prescale settings
     * (meaning it might not dispatch it).
     *
     * @param buffer the ByteBuffer containg the event.
     * @throws DispatchException is there is a problem in the Dispatch system.
     */
    private void dispatchEvent(ByteBuffer buf) throws DispatchException
    {

        if (preScaling) {
            if (preScaleCount < preScale) {
                if (log.isDebugEnabled()) {
                    log.debug("Discarding " + streamName + " prescaled event " +
                        preScaleCount + " out of " + preScale);
                }
                preScaleCount++;
                return;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Resetting " + streamName +
                        " prescale count, dispatching event.");
                }
                preScaleCount = 1;
            }
        }

        synchronized (dispatcher) {
            dispatcher.dispatchEvent(buf);
        }
    }


    /**
     * Set the name of the secondary builder stream for this
     * spliced analysis engine.
     *
     * @param name - the name of the stream
     */
    void setStreamName(String streamName)
    {
        this.streamName = streamName;
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the disposed state.
     *
     * @param event the event encapsulating this state change.
     */
    public void disposed(SplicerChangedEvent event)
    {
        if (log.isInfoEnabled()) {
            log.info("Splicer " + streamName + " entered DISPOSED state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the failed state.
     *
     * @param event the event encapsulating this state change.
     */
    public void failed(SplicerChangedEvent event)
    {
        if (log.isInfoEnabled()) {
            log.info("Splicer " + streamName + " entered FAILED state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the starting state.
     *
     * @param event the event encapsulating this state change.
     */
    public void starting(SplicerChangedEvent event)
    {
        try {
            // insert data boundary at begin of run
            dispatcher.dataBoundary(Dispatcher.START_PREFIX + runNumber);
            if (log.isInfoEnabled()) {
                log.info("entered " + streamName +
                    " starting state and calling dispatcher.dataBoundary()");
            }
        } catch (DispatchException de) {
            if (log.isErrorEnabled()) {
                log.error("failed on " + streamName +
                    " dispatcher.dataBoundary(): ", de);
            }
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the started state.
     *
     * @param event the event encapsulating this state change.
     */
    public void started(SplicerChangedEvent event)
    {
        if (log.isInfoEnabled()) {
            log.info("Splicer " + streamName + " entered STARTED state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the stopped state.
     *
     * @param event the event encapsulating this state change.
     */
    public void stopped(SplicerChangedEvent event)
    {
        try {
            dispatcher.dataBoundary(Dispatcher.STOP_PREFIX + runNumber);
            if (log.isInfoEnabled()) {
                log.info("entered " + streamName +
                    " stopped state and calling dispatcher.dataBoundary()");
            }
        } catch (DispatchException de) {
            if (log.isErrorEnabled()) {
                log.error("failed on " + streamName +
                    " dispatcher.dataBoundary(): ", de);
            }
        }
        // tell manager that we have stopped
        if (log.isInfoEnabled()) {
            log.info("entered stopped state. Splicer " + streamName +
                " state is: " +
                    splicer.getState() + ": " +
                    splicer.getStateString());
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the stopping state.
     *
     * @param event the event encapsulating this state change.
     */
    public void stopping(SplicerChangedEvent event)
    {
        if (log.isInfoEnabled()) {
            log.info("Splicer " + streamName + " entered STOPPING state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} has
     * truncated its "rope". This
     * method is called whenever the "rope" is cut, for example to make a clean
     * start from the frayed beginning of a "rope", and not jsut the the {@link
     * Splicer#truncate(Spliceable)} method is invoked. This enables the client
     * to be notified as to which Spliceable are nover going to be accessed
     * again by the Splicer.
     *
     * @param event the event encapsulating this truncation.
     */
    public void truncated(SplicerChangedEvent event)
    {
        if (log.isDebugEnabled()) {
            log.debug("Splicer " + streamName + " truncated: " +
                event.getSpliceable());
        }
        Iterator iter = event.getAllSpliceables().iterator();
        while (iter.hasNext()) {
            ILoadablePayload payload = (ILoadablePayload) iter.next();
            if (log.isDebugEnabled()) {
                log.debug("Recycle " + streamName + " payload " + payload);
            }
            // reduce memory commitment to splicer
            payload.recycle();
        }
    }

    // set the splicer and add this listener to the splicer
    public void setSplicer(Splicer splicer)
    {
        if (splicer == null) {
            log.error("Splicer " + streamName + " cannot be null");
            throw new IllegalArgumentException("Splicer " + streamName +
                " cannot be null");
        }
        this.splicer = splicer;
        this.splicer.addSplicerListener(this);
    }

    public void setRunNumber(int runNumber)
    {
        this.runNumber = runNumber;
    }

    /**
     * Switch to a new run.
     *
     * @return number of events dispatched before the run was switched
     *
     * @param runNumber new run number
     */
    public long switchToNewRun(int runNumber) {
        long numEvents = 0;
        try {
            synchronized (dispatcher) {
                numEvents = dispatcher.getNumDispatchedEvents();
                dispatcher.dataBoundary(Dispatcher.SWITCH_PREFIX + runNumber);
                if (log.isInfoEnabled()) {
                    log.info("switched " + streamName + " to run " +
                             runNumber);
                }
                this.runNumber = runNumber;
            }
        } catch (DispatchException de) {
            if (log.isErrorEnabled()) {
                log.error("failed to switch " + streamName, de);
            }
        }

        return numEvents;
    }
}
