/**
 * Class: SBSplicedAnalysis
 *
 * Date: Nov 28, 2006 1:28:20 PM
 * (c) 2005 IceCube collaboration
 */
package icecube.daq.secBuilder;

import icecube.daq.io.DispatchException;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.StreamMetaData;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.impl.Monitor;
import icecube.daq.splicer.SpliceableFactory;
import icecube.daq.splicer.Spliceable;
import icecube.daq.splicer.SplicedAnalysis;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerChangedEvent;
import icecube.daq.splicer.SplicerListener;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.DOMInfo;

import java.nio.ByteBuffer;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * This is the the SplicedAnalysis that can be used with
 * all secondary builders (monitor, sn, and tcal)
 *
 * @author artur
 * @version $Id: SBSplicedAnalysis.java,v 1.0 2006/03/24 13:28:20 artur Exp $
 */
public class SBSplicedAnalysis
    implements SplicedAnalysis<Spliceable>, SplicerListener<Spliceable>
{
    private static final boolean STRIP_NONSTANDARD_MONI = true;

    /** Database of DOM info */
    private static IDOMRegistry domRegistry;
    /** Have we complained about a missing DOM registry yet? */
    private boolean warnedDomRegistry = false;

    private Dispatcher dispatcher;
    private Splicer splicer;
    private int runNumber;
    private boolean reportedError;
    private String streamName = "noname";
    private boolean preScaling = false;
    private long preScale = 1;
    private long preScaleCount = 1;

    private Logger log = Logger.getLogger(SBSplicedAnalysis.class);

    public SBSplicedAnalysis(Dispatcher dispatcher)
    {
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
     */
    @Override
    public void analyze(List<Spliceable> splicedObjects)
    {
        for (Spliceable spl : splicedObjects) {
            if (spl == SpliceableFactory.LAST_POSSIBLE_SPLICEABLE) {
                break;
            }

            // get the next payload
            ILoadablePayload payload = (ILoadablePayload) spl;

            // gather data for monitoring messages
            try {
                gatherMonitoring(payload);
            } catch (MoniException me) {
                log.error("Cannot process payload " + payload, me);
            } catch (Throwable thr) {
                log.error("Unexpected monitoring error from " + payload, thr);
            }

            // scintillator/IceACT monitoring payloads break
            // legacy IceTop software
            if (!STRIP_NONSTANDARD_MONI || !isNonStandardDOM(payload)) {
                // limit the byte buffer to the length specified in the header
                ByteBuffer buf  = payload.getPayloadBacking();
                buf.limit(buf.getInt(0));

                // write out the payload
                try {
                    dispatchEvent(buf, payload.getUTCTime());
                } catch (DispatchException de) {
                    if (!reportedError) {
                        log.error("couldn't dispatch the " + streamName +
                                  " payload: ", de);
                        reportedError = true;
                    }
                }
            }

            payload.recycle();
        }
    }

    /**
     * Gather data for monitoring messages
     *
     * @param payload payload
     *
     * @throws MoniException if there is a problem
     */
    public void gatherMonitoring(IPayload payload)
        throws MoniException
    {
        // override this method to do something with the payloads
    }

    /**
     * Get the dispatcher object.
     *
     * @return dispatcher
     */
    public Dispatcher getDispatcher()
    {
        return dispatcher;
    }

    /**
     * Get DOM info associated with the specified mainboard ID.
     *
     * @param mbid mainboard ID
     *
     * @return <tt>null</tt> if no DOM was found
     *         (or if no DOM registry has been set)
     */
    DOMInfo getDOM(long mbid)
    {
        if (domRegistry == null) {
            if (!warnedDomRegistry) {
                log.error("DOM registry has not been set");
                warnedDomRegistry = true;
            }
            return null;
        }

        return domRegistry.getDom(mbid);
    }

    /**
     * Has the dom registry been set?
     *
     * @return <tt>false</tt> if there's no DOM registry object
     */
    boolean hasDOMRegistry()
    {
        return domRegistry != null;
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
        this.preScaling = preScale > 1;
        this.preScale = preScale;
        this.preScaleCount = 1;
    }

    /**
     * Is this a monitoring payload from a scintillator or IceACT DOM?
     *
     * @param payload payload to check
     *
     * @return <tt>true</tt> if this is a non-standard DOM
     */
    boolean isNonStandardDOM(IPayload payload)
    {
        if (payload instanceof Monitor) {
            Monitor mon = (Monitor) payload;

            DOMInfo dom = getDOM(mon.getDomId());
            if (dom != null) {
                return dom.isScintillator() || dom.isIceACT();
            }
        }

        return false;
    }

    /**
     * Dispatch an event taking into account any prescale settings
     * (meaning it might not dispatch it).
     *
     * @param buffer the ByteBuffer containg the event.
     * @param ticks DAQ time for this payload
     *
     * @throws DispatchException is there is a problem in the Dispatch system.
     */
    private void dispatchEvent(ByteBuffer buf, long ticks)
        throws DispatchException
    {

        if (preScaling) {
            if (preScaleCount < preScale) {
                if (log.isDebugEnabled()) {
                    log.debug("Discarding " + streamName +
                              " prescaled event " + preScaleCount +
                              " out of " + preScale);
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
            dispatcher.dispatchEvent(buf, ticks);
        }
    }


    /**
     * Send any cached monitoring data
     *
     * @param stopTime time when the component's stopped() method was called
     *        (in DAQ ticks)
     */
    public void finishMonitoring(long stopTime)
    {
        // do nothing
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
    @Override
    public void disposed(SplicerChangedEvent<Spliceable> event)
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
    @Override
    public void failed(SplicerChangedEvent<Spliceable> event)
    {
        if (log.isInfoEnabled()) {
            log.info("Splicer " + streamName + " entered FAILED state");
        }
    }

    /**
     * Get the stream metadata (currently number of dispatched events and
     * last dispatched time)
     *
     * @return metadata object
     */
    public StreamMetaData getMetaData()
    {
        return dispatcher.getMetaData();
    }

    /**
     * Get the current run number
     *
     * @return current run number
     */
    public int getRunNumber()
    {
        return runNumber;
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the starting state.
     *
     * @param event the event encapsulating this state change.
     */
    @Override
    public void starting(SplicerChangedEvent<Spliceable> event)
    {
        try {
            // insert data boundary at begin of run
            dispatcher.dataBoundary(Dispatcher.START_PREFIX + runNumber);
            if (log.isInfoEnabled()) {
                log.info("entered " + streamName +
                    " starting state and calling dispatcher.dataBoundary()");
            }
        } catch (DispatchException de) {
            log.error("failed on " + streamName +
                      " dispatcher.dataBoundary(): ", de);
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the started state.
     *
     * @param event the event encapsulating this state change.
     */
    @Override
    public void started(SplicerChangedEvent<Spliceable> event)
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
    @Override
    public void stopped(SplicerChangedEvent<Spliceable> event)
    {
        try {
            dispatcher.dataBoundary(Dispatcher.STOP_PREFIX + runNumber);
            if (log.isInfoEnabled()) {
                log.info("entered " + streamName +
                    " stopped state and calling dispatcher.dataBoundary()");
            }
        } catch (DispatchException de) {
            log.error("failed on " + streamName +
                " dispatcher.dataBoundary(): ", de);
        }
        // tell manager that we have stopped
        if (log.isInfoEnabled()) {
            log.info("entered stopped state. Splicer " + streamName + " is " +
                     splicer.getState().name());
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer}
     * enters the stopping state.
     *
     * @param event the event encapsulating this state change.
     */
    @Override
    public void stopping(SplicerChangedEvent<Spliceable> event)
    {
        if (log.isInfoEnabled()) {
            log.info("Splicer " + streamName + " entered STOPPING state");
        }
    }

    /**
     * Set the DOM registry object
     *
     * @param reg registry
     */
    public static void setDOMRegistry(IDOMRegistry reg)
    {
        domRegistry = reg;
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

    /**
     * Set the run number
     *
     * @param runNumber current run number
     */
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
     *
     * @param switchTime time when the component's switching() method was
     *        called (in DAQ ticks)
     */
    public StreamMetaData switchToNewRun(int runNumber, long switchTime) {
        StreamMetaData metadata;
        try {
            synchronized (dispatcher) {
                finishMonitoring(switchTime);
                metadata = dispatcher.getMetaData();
                dispatcher.dataBoundary(Dispatcher.SWITCH_PREFIX + runNumber);
                if (log.isInfoEnabled()) {
                    log.info("switched " + streamName + " to run " +
                             runNumber);
                }
                this.runNumber = runNumber;
            }
        } catch (DispatchException de) {
            log.error("failed to switch " + streamName, de);
            metadata = null;
        }

        return metadata;
    }
}
