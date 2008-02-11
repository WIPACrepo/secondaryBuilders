/**
 * Class: SBSplicedAnalysis
 *
 * Date: Nov 28, 2006 1:28:20 PM
 * (c) 2005 IceCube collaboration
 */
package icecube.daq.secBuilder;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.io.DispatchException;
import icecube.daq.io.Dispatcher;
import icecube.daq.payload.splicer.Payload;
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
public class SBSplicedAnalysis implements SplicedAnalysis, SplicerListener {

    private SpliceableFactory spliceableFactory;
    private Dispatcher dispatcher;
    private Splicer splicer;
    private int start;
    private int lastInputListSize;
    private int runNumber;
    private boolean reportedError;

    private Log log = LogFactory.getLog(SBSplicedAnalysis.class);

    public SBSplicedAnalysis(SpliceableFactory factory, Dispatcher dispatcher,
                             SecBuilderMonitor secBuilderMonitor) {
        if (factory == null) {
            log.error("SpliceableFactory is null");
            throw new IllegalArgumentException("SpliceableFactory cannot be null");
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
     * @param decrement      the decrement of the indices in the List since the last
     *                       invocation.
     */
    public void execute(List splicedObjects, int decrement) {
        // Loop over the new objects in the splicer
        int numberOfObjectsInSplicer = splicedObjects.size();
        lastInputListSize = numberOfObjectsInSplicer - (start - decrement);

        if (log.isDebugEnabled()) {
            log.debug("Splicer contains: [" + lastInputListSize + ":" + numberOfObjectsInSplicer + "]");
        }

        for (int index = start - decrement; index < numberOfObjectsInSplicer; index++) {

            Payload payload = (Payload) splicedObjects.get(index);
            ByteBuffer buf  = payload.getPayloadBacking();
            buf.limit(buf.getInt(0));
            if (log.isDebugEnabled())
            {
                int recl = buf.getInt(0);
                int limit = buf.limit();
                int capacity = buf.capacity();
                log.debug("Writing byte buffer - RECL="
                        + recl + " LIMIT="
                        + limit + " CAP="
                        + capacity
                        );
            }
            try {
                dispatcher.dispatchEvent(buf);
            } catch (DispatchException de) {
                if (log.isErrorEnabled() && !reportedError) {
                    log.error("couldn't dispatch the payload: ", de);
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
                    log.debug("Truncating splicer: " + update);
                }
                splicer.truncate(update);
            }
        }
    }

    /**
     * Returns the {@link icecube.daq.splicer.SpliceableFactory} that should be used to create the
     * {@link icecube.daq.splicer.Spliceable Spliceable} objects used by this
     * object.
     *
     * @return the SpliceableFactory that creates Spliceable objects.
     */
    public SpliceableFactory getFactory() {
        return spliceableFactory;
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} enters the disposed state.
     *
     * @param event the event encapsulating this state change.
     */
    public void disposed(SplicerChangedEvent event) {
        if (log.isInfoEnabled()) {
            log.info("Splicer entered DISPOSED state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} enters the failed state.
     *
     * @param event the event encapsulating this state change.
     */
    public void failed(SplicerChangedEvent event) {
        if (log.isInfoEnabled()) {
            log.info("Splicer entered FAILED state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} enters the starting state.
     *
     * @param event the event encapsulating this state change.
     */
    public void starting(SplicerChangedEvent event) {
        try {
            // insert data boundary at begin of run
            dispatcher.dataBoundary(DAQCmdInterface.DAQ_ONLINE_RUNSTART_FLAG +
                    runNumber);
            if (log.isInfoEnabled()) {
                log.info("entered starting state and calling dispatcher.dataBoundary()");
            }
        } catch (DispatchException de) {
            if (log.isErrorEnabled()) {
                log.error("failed on dispatcher.dataBoundary(): ", de);
            }
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} enters the started state.
     *
     * @param event the event encapsulating this state change.
     */
    public void started(SplicerChangedEvent event) {
        if (log.isInfoEnabled()) {
            log.info("Splicer entered STARTED state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} enters the stopped state.
     *
     * @param event the event encapsulating this state change.
     */
    public void stopped(SplicerChangedEvent event) {
        try {
            dispatcher.dataBoundary(DAQCmdInterface.DAQ_ONLINE_RUNSTOP_FLAG +
                    runNumber);
            if (log.isInfoEnabled()) {
                log.info("entered stopped state and calling dispatcher.dataBoundary()");
            }
        } catch (DispatchException de) {
            if (log.isErrorEnabled()) {
                log.error("failed on dispatcher.dataBoundary(): ", de);
            }
        }
        // tell manager that we have stopped
        if (log.isInfoEnabled()) {
            log.info("entered stopped state. Splicer state is: " +
                    splicer.getState() + ": " +
                    splicer.getStateString());
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} enters the stopping state.
     *
     * @param event the event encapsulating this state change.
     */
    public void stopping(SplicerChangedEvent event) {
        if (log.isInfoEnabled()) {
            log.info("Splicer entered STOPPING state");
        }
    }

    /**
     * Called when the {@link icecube.daq.splicer.Splicer Splicer} has truncated its "rope". This
     * method is called whenever the "rope" is cut, for example to make a clean
     * start from the frayed beginning of a "rope", and not jsut the the {@link
     * Splicer#truncate(Spliceable)} method is invoked. This enables the client
     * to be notified as to which Spliceable are nover going to be accessed
     * again by the Splicer.
     *
     * @param event the event encapsulating this truncation.
     */
    public void truncated(SplicerChangedEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("Splicer truncated: " + event.getSpliceable());
        }
        Iterator iter = event.getAllSpliceables().iterator();
        while (iter.hasNext()) {
            Payload payload = (Payload) iter.next();
            if (log.isDebugEnabled()) {
                log.debug("Recycle payload " + payload);
            }
            // reduce memory commitment to splicer
            payload.recycle();
        }
    }

    // set the splicer and add this listener to the splicer
    public void setSplicer(Splicer splicer) {
        if (splicer == null) {
            log.error("Splicer cannot be null");
            throw new IllegalArgumentException("Splicer cannot be null");
        }
        this.splicer = splicer;
        this.splicer.addSplicerListener(this);
    }

    public void setRunNumber(int runNumber) {
        this.runNumber = runNumber;
    }
}
