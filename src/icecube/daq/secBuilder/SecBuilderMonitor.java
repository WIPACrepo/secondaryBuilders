package icecube.daq.secBuilder;

/**
 * SecBuilderMonitor
 * Date: Oct 28, 2005 1:26:43 PM
 * 
 * (c) 2005 IceCube Collaboration
 */

import java.nio.ByteBuffer;
import java.util.*;
import java.text.SimpleDateFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import icecube.daq.splicer.Splicer;
import icecube.daq.io.SpliceablePayloadInputEngine;
import icecube.daq.payload.splicer.Payload;
import icecube.icebucket.monitor.ScalarFlowMonitor;
import icecube.icebucket.monitor.simple.ScalarFlowMonitorImpl;

public class SecBuilderMonitor {
    private Log log = LogFactory.getLog(SecBuilderMonitor.class);

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss z");
    private boolean initialized = false;
    private Splicer splicer = null;

    //private static final int DEFAULT_DEADDOM_MON_THRESH = 4;
    public String startRunBoundaryTag;
    public String stopRunBoundaryTag;
    public String startRunLocalTime;
    public String stopRunLocalTime;
    public long startRunBoundaryDispatchWrites;
    public long stopRunBoundaryDispatchWrites;
    public ScalarFlowMonitor byteDispatchWriteMonitor;
    public ScalarFlowMonitor eventDispatchWriteMonitor;
    private boolean destroyed = false;
    private SpliceablePayloadInputEngine payloadInputEngine;
    private String secBuilderType;

    public SecBuilderMonitor(String secBuilderType) {
        initialized = false;
        this.secBuilderType = secBuilderType;
    }

    public void setPayloadInputEngine(SpliceablePayloadInputEngine payloadInputEngine){
        this.payloadInputEngine = payloadInputEngine;
    }

    public String getSecBuilderType(){
        return secBuilderType;
    }

    public void initializeMonitor() {
        // this should not ever happen, but....
        if (destroyed) {
            return;
        }
        startRunBoundaryTag = "none";
        stopRunBoundaryTag = "none";
        startRunLocalTime = "unknown";
        stopRunLocalTime = "unknown";
        startRunBoundaryDispatchWrites = 0;
        stopRunBoundaryDispatchWrites = 0;
        eventDispatchWriteMonitor = new ScalarFlowMonitorImpl();
        eventDispatchWriteMonitor.reset();
        byteDispatchWriteMonitor = new ScalarFlowMonitorImpl();
        byteDispatchWriteMonitor.reset();

        // prepare time classes for later use
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        // remember that we have been initialized
        initialized = true;
    }



    public void unregisterWithTcalMgr() {
        if (!destroyed) {
            this.destroy();
            eventDispatchWriteMonitor = null;
            byteDispatchWriteMonitor = null;
        }
        initialized = false;
    }

    public void registerSplicer(Splicer splicer) {
        if (!destroyed) {
        this.splicer = splicer;
        }
    }

    public void updateStartRun(String tag) {
        // this should not ever happen, but....
        if (destroyed) {
            return;
        }
        if (initialized) {
            // only perform if initialized
            startRunBoundaryTag = tag;
            startRunLocalTime = sdf.format(new Date());
            startRunBoundaryDispatchWrites++;
            byteDispatchWriteMonitor.reset();
            eventDispatchWriteMonitor.reset();
        }
    }

    public void updateStopRun(String tag) {
        // this should not ever happen, but....
        if (destroyed) {
            return;
        }
        if (initialized) {
            // only perform if initialized
            stopRunBoundaryTag = tag;
            stopRunLocalTime = sdf.format(new Date());
            stopRunBoundaryDispatchWrites++;
        }
    }

    public void updateEventStatisitcs(ByteBuffer buf) {
        // this should not ever happen, but....
        if (destroyed) {
            return;
        }
        if (initialized) {
            // only perform if initialized
            if (buf != null) {
                byteDispatchWriteMonitor.measure(buf.getInt(0));
                eventDispatchWriteMonitor.measure(1);
            }
        }
    }

    public String splicerState() {
        if (splicer != null) {
            return splicer.getStateString();
        } else {
            return "no splicer registered";
        }
    }

    public void reduceBytesCommittedToSplicer(Payload payload) {
        // this should not ever happen, but....
        if (destroyed) {
            return;
        }

        ByteBuffer buf = payload.getPayloadBacking();
        if (buf == null) {
            return;
        }
        if (payloadInputEngine != null) {
            payloadInputEngine.bytesCommittedToSplicer -= buf.capacity();
            if (payloadInputEngine.bytesCommittedToSplicer < 0) {
                payloadInputEngine.bytesCommittedToSplicer = 0;
            }
        }
    }

    public void resetAllCounters() {
        // this should not ever happen, but....
        if (destroyed) {
            return;
        }
        if (initialized) {
            // only perform if initialized
            startRunBoundaryTag = "none";
            stopRunBoundaryTag = "none";
            startRunLocalTime = "unknown";
            stopRunLocalTime = "unknown";
            startRunBoundaryDispatchWrites = 0;
            stopRunBoundaryDispatchWrites = 0;
            eventDispatchWriteMonitor.reset();
            byteDispatchWriteMonitor.reset();
            if (payloadInputEngine != null) {
                payloadInputEngine.bytesCommittedToSplicer = 0;
            }
        }
    }

    public void destroy() {
        byteDispatchWriteMonitor.dispose();
        eventDispatchWriteMonitor.dispose();
        destroyed = true;
    }

    public void printSummary() {
        // this should not ever happen, but....
        if (destroyed) {
            return;
        }
        if (initialized) {
            System.out.println("\t\tStartRunBoundaryDispatchWrites: " +
                    startRunBoundaryDispatchWrites);
            System.out.println("\t\tStartRunBoundaryTag: " +
                    startRunBoundaryTag);
            System.out.println("\t\tStartRunLocalTime: " +
                    startRunLocalTime);
            System.out.println("\t\tStopRunBoundaryDispatchWrites: " +
                    stopRunBoundaryDispatchWrites);
            System.out.println("\t\tStoptRunBoundaryTag: " +
                    stopRunBoundaryTag);
            System.out.println("\t\tStopRunLocalTime: " +
                    stopRunLocalTime);
            System.out.println("\t\tEventDispatchWrites: " +
                    eventDispatchWriteMonitor.getTotal());
            System.out.println("\t\tEventDispatchWriteRate: " +
                    eventDispatchWriteMonitor.getRate());
            System.out.println("\t\tByteDispatchWrites: " +
                    byteDispatchWriteMonitor.getTotal());
            System.out.println("\t\tByteDispatchWriteRate: " +
                    byteDispatchWriteMonitor.getRate());
        }
    }
}