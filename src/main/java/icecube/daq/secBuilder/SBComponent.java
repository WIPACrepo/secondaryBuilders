/**
 * Class: SBComponent
 *
 * Date: Nov 28, 2006 2:27:09 PM
 *
 * (c) 2005 IceCube collaboration
 */
package icecube.daq.secBuilder;

import java.io.IOException;

import java.util.HashMap;

import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQCompServer;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.VitreousBufferCache;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.splicer.SpliceableFactory;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerImpl;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.FileDispatcher;
import icecube.daq.io.SpliceablePayloadReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is the place where we initialize all the IO engines, splicers
 * and monitoring classes for secondary builders
 *
 * @version $Id: SBComponent.java 2146 2007-10-17 01:37:59Z ksb $
 */
public class SBComponent extends DAQComponent {

    private Log log = LogFactory.getLog(SBComponent.class);

    private IByteBufferCache tcalBufferCache;
    private IByteBufferCache snBufferCache;
    private IByteBufferCache moniBufferCache;

    private SBCompConfig compConfig;

    private SpliceableFactory tcalFactory;
    private SpliceableFactory snFactory;
    private SpliceableFactory moniFactory;

    private Splicer tcalSplicer;
    private Splicer snSplicer;
    private Splicer moniSplicer;

    private SBSplicedAnalysis tcalSplicedAnalysis;
    private SBSplicedAnalysis snSplicedAnalysis;
    private SBSplicedAnalysis moniSplicedAnalysis;

    private Dispatcher tcalDispatcher;
    private Dispatcher snDispatcher;
    private Dispatcher moniDispatcher;

    private SecBuilderMonitor tcalBuilderMonitor;
    private SecBuilderMonitor snBuilderMonitor;
    private SecBuilderMonitor moniBuilderMonitor;

    private SpliceablePayloadReader tcalInputEngine;
    private SpliceablePayloadReader snInputEngine;
    private SpliceablePayloadReader moniInputEngine;

    private boolean isTcalEnabled;
    private boolean isSnEnabled;
    private boolean isMoniEnabled;

    private static final String COMP_NAME = DAQCmdInterface.DAQ_SECONDARY_BUILDERS;
    private static final int COMP_ID = 0;

    /** svn version information */
    private static final HashMap SVN_VER_INFO;
    static {
	SVN_VER_INFO = new HashMap(4);
	SVN_VER_INFO.put("id",  "$Id: SBComponent.java 2146 2007-10-17 01:37:59Z ksb $");
	SVN_VER_INFO.put("url", "$URL: http://code.icecube.wisc.edu/daq/projects/secondaryBuilders/trunk/src/main/java/icecube/daq/secBuilder/SBComponent.java $");
    }

    public SBComponent(SBCompConfig compConfig) {
        super(COMP_NAME, COMP_ID);
        this.compConfig = compConfig;

        boolean isMonitoring = compConfig.isMonitoring();
        isTcalEnabled = compConfig.isTcalEnabled();
        isSnEnabled = compConfig.isSnEnabled();
        isMoniEnabled = compConfig.isMoniEnabled();

        // init tcalBuilder classes
        if (isTcalEnabled) {
            if (log.isInfoEnabled()){
                log.info("Constructing TcalBuilder");
            }
            //tcalBuilderMonitor = new SecBuilderMonitor("TcalBuilder");
            tcalBufferCache = new VitreousBufferCache();
            tcalDispatcher = new FileDispatcher("tcal", tcalBufferCache);
            addCache(DAQConnector.TYPE_TCAL_DATA, tcalBufferCache);
            tcalFactory = new MasterPayloadFactory(tcalBufferCache);
            tcalSplicedAnalysis = new SBSplicedAnalysis(tcalFactory,
                    tcalDispatcher,
                    tcalBuilderMonitor);
            tcalSplicer = new SplicerImpl(tcalSplicedAnalysis);
            addSplicer(tcalSplicer);

            tcalSplicedAnalysis.setSplicer(tcalSplicer);
            try 
            {
                tcalInputEngine = new SpliceablePayloadReader("tcalInputEngine", 5000, tcalSplicer, tcalFactory);
                addMonitoredEngine(DAQConnector.TYPE_TCAL_DATA, tcalInputEngine);
    
                if (isMonitoring){
                    tcalBuilderMonitor = new SecBuilderMonitor("TcalBuilder", tcalInputEngine,
                            tcalSplicer, tcalDispatcher);
                    addMBean("tcalBuilder", tcalBuilderMonitor);
                }
            }
            catch (IOException iox)
            {
                // TODO - this is going to be hidden unless the
                // operator starts up in verbose mode.  Fix.
                log.error(iox);
                System.exit(1);
            }
        }

        // init snBuilder
        if (isSnEnabled) {
            if (log.isInfoEnabled()){
                log.info("Constructing SNBuilder");
            }
            //snBuilderMonitor = new SecBuilderMonitor("SNBuilder");
            snBufferCache = new VitreousBufferCache();
            snDispatcher = new FileDispatcher("sn", snBufferCache);
            addCache(DAQConnector.TYPE_SN_DATA, snBufferCache);
            snFactory = new MasterPayloadFactory(snBufferCache);
            snSplicedAnalysis = new SBSplicedAnalysis(snFactory,
                    snDispatcher,
                    snBuilderMonitor);
            snSplicer = new SplicerImpl(snSplicedAnalysis);
            addSplicer(snSplicer);

            snSplicedAnalysis.setSplicer(snSplicer);
            try
            {
                snInputEngine = new SpliceablePayloadReader("stringHubSnInput", 10000, snSplicer, snFactory);
                addMonitoredEngine(DAQConnector.TYPE_SN_DATA, snInputEngine);
    
                if (isMonitoring){
                    snBuilderMonitor = new SecBuilderMonitor("SnBuilder", snInputEngine,
                            snSplicer, snDispatcher);
                    addMBean("snBuilder", snBuilderMonitor);
                }
            }
            catch (IOException iox)
            {
                // TODO see tcal comment
                log.error(iox);
                System.exit(1);
            }
        }

        // init moniBuilder classes
        if (isMoniEnabled) {
            if (log.isInfoEnabled()){
                log.info("Constructing MoniBuilder");
            }
            //moniBuilderMonitor = new SecBuilderMonitor("MoniBuilder");
            moniBufferCache = new VitreousBufferCache();
            moniDispatcher = new FileDispatcher("moni", moniBufferCache);
            addCache(DAQConnector.TYPE_MONI_DATA, moniBufferCache);
            moniFactory = new MasterPayloadFactory(moniBufferCache);
            moniSplicedAnalysis = new SBSplicedAnalysis(moniFactory,
                    moniDispatcher,
                    moniBuilderMonitor);
            moniSplicer = new SplicerImpl(moniSplicedAnalysis);
            addSplicer(moniSplicer);

            moniSplicedAnalysis.setSplicer(moniSplicer);
            try
            {
                moniInputEngine = new SpliceablePayloadReader("stringHubMoniInput", 5000, moniSplicer, moniFactory);
                addMonitoredEngine(DAQConnector.TYPE_MONI_DATA, moniInputEngine);
    
                if (isMonitoring){
                    moniBuilderMonitor = new SecBuilderMonitor("MoniBuilder", moniInputEngine,
                            moniSplicer, moniDispatcher);
                    addMBean("moniBuilder", moniBuilderMonitor);
                }
            }
            catch (IOException iox)
            {
                log.error(iox);
                System.exit(1);
            }
        }
    }

    public void setRunNumber(int runNumber) {
        if (log.isInfoEnabled()){
            log.info("Setting runNumber = " + runNumber);
        }
        if (compConfig.isTcalEnabled()) {
            tcalSplicedAnalysis.setRunNumber(runNumber);
        }
        if (compConfig.isSnEnabled()) {
            snSplicedAnalysis.setRunNumber(runNumber);
        }
        if (compConfig.isMoniEnabled()) {
            moniSplicedAnalysis.setRunNumber(runNumber);
        }
    }

    /**
     * Set the destination directory where the dispatch files will be saved.
     *
     * @param dirName The absolute path of directory where the dispatch files will be stored.
     */
    public void setDispatchDestStorage(String dirName) {
        if (isTcalEnabled){
            tcalDispatcher.setDispatchDestStorage(dirName);
        }
        if (isSnEnabled){
            snDispatcher.setDispatchDestStorage(dirName);
        }
        if (isMoniEnabled){
            moniDispatcher.setDispatchDestStorage(dirName);
        }
    }

    /**
     * Set the maximum size of the dispatch file.
     *
     * @param maxFileSize the maximum size of the dispatch file.
     */
    public void setMaxFileSize(long maxFileSize) {
        if (isTcalEnabled){
            tcalDispatcher.setMaxFileSize(maxFileSize);
        }
        if (isSnEnabled){
            snDispatcher.setMaxFileSize(maxFileSize);
        }
        if (isMoniEnabled){
            moniDispatcher.setMaxFileSize(maxFileSize);
        }
    }


    /**
     * Return this component's svn version info as a HashMap.
     *
     * @return svn version info (id, url) as a HashMap
     */
    public HashMap getVersionInfo()
    {
	return SVN_VER_INFO;
    }


    /**
     * Run a DAQ component server.
     *
     * @param args command-line arguments
     *
     */
    public static void main(String[] args) throws Exception
    {
        new DAQCompServer(new SBComponent(new SecBuilderCompConfig()), args);
    }
}
