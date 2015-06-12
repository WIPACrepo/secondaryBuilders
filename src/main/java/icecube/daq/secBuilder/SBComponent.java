/**
 * Class: SBComponent
 *
 * Date: Nov 28, 2006 2:27:09 PM
 *
 * (c) 2005 IceCube collaboration
 */
package icecube.daq.secBuilder;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.FileDispatcher;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQCompServer;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.mbean.MemoryStatistics;
import icecube.daq.juggler.mbean.SystemStatistics;
import icecube.daq.payload.impl.PayloadFactory;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.PrioritySplicer;
import icecube.daq.splicer.Spliceable;
import icecube.daq.splicer.SpliceableComparator;
import icecube.daq.splicer.SpliceableFactory;
import icecube.daq.splicer.SpliceableFactory;
import icecube.daq.splicer.SplicedAnalysis;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerException;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.IDOMRegistry;

import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

/**
 * This is the place where we initialize all the IO engines, splicers
 * and monitoring classes for secondary builders
 *
 * @version $Id: SBComponent.java 15570 2015-06-12 16:19:32Z dglo $
 */
public class SBComponent extends DAQComponent
{
    /**
     * Event totals for a run.
     */
    class SBRunData
    {
        private long numTCal;
        private long numSN;
        private long numMoni;

        /**
         * Create an object holding the event totals for a run.
         *
         * @param numTCal - number of time calibration events dispatched
         * @param numSN - number of supernova events dispatched
         * @param numMoni - number of monitoring events dispatched
         */
        SBRunData(long numTCal, long numSN, long numMoni)
        {
            this.numTCal = numTCal;
            this.numSN = numSN;
            this.numMoni = numMoni;
        }

        /**
         * Return run data as an array of <tt>long</tt> values.
         *
         * @return array of <tt>long</tt> values
         */
        public long[] toArray()
        {
            return new long[] { numTCal, numSN, numMoni };
        }
    }

    private static final Log LOG = LogFactory.getLog(SBComponent.class);

    private static final boolean disableIceTopFastMoni =
        !parseBoolean(System.getProperty("enableIceTopFastMoni", "true"));

    private static final boolean USE_PRIO_SPLICER =
        System.getProperty("usePrioritySplicer") != null;

    private static final Spliceable LAST_SPLICEABLE =
        SpliceableFactory.LAST_POSSIBLE_SPLICEABLE;

    private final SpliceableComparator splicerCmp =
        new SpliceableComparator(LAST_SPLICEABLE);

    private IByteBufferCache tcalBufferCache;
    private IByteBufferCache snBufferCache;
    private IByteBufferCache moniBufferCache;

    private SpliceableFactory tcalFactory;
    private SpliceableFactory snFactory;
    private SpliceableFactory moniFactory;

    private Splicer<Spliceable> tcalSplicer;
    private Splicer<Spliceable> snSplicer;
    private Splicer<Spliceable> moniSplicer;

    private TCalAnalysis tcalSplicedAnalysis;
    private SBSplicedAnalysis snSplicedAnalysis;
    private MoniAnalysis moniSplicedAnalysis;

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

    private static final String COMP_NAME =
        DAQCmdInterface.DAQ_SECONDARY_BUILDERS;
    private static final int COMP_ID = 0;

    private String configDirName;

    private int runNumber;

    /** Map used to track event counts for each run */
    private HashMap<Integer, SBRunData> runData =
        new HashMap<Integer, SBRunData>();

    public SBComponent(SBCompConfig compConfig)
        throws DAQCompException
    {
        super(COMP_NAME, COMP_ID);

        boolean isMonitoring = compConfig.isMonitoring();
        isTcalEnabled = compConfig.isTcalEnabled();
        isSnEnabled = compConfig.isSnEnabled();
        isMoniEnabled = compConfig.isMoniEnabled();

        // init tcalBuilder classes
        if (isTcalEnabled) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Constructing TcalBuilder");
            }
            tcalBufferCache = new VitreousBufferCache("SBTCal", 350000000);
            tcalDispatcher = new FileDispatcher("tcal", tcalBufferCache);
            addCache(DAQConnector.TYPE_TCAL_DATA, tcalBufferCache);
            //addMBean("tcalCache", tcalBufferCache);
            tcalFactory = new PayloadFactory(tcalBufferCache);
            tcalSplicedAnalysis = new TCalAnalysis(tcalDispatcher);
            tcalSplicer = createSplicer(tcalSplicedAnalysis);
            addSplicer(tcalSplicer);

            tcalSplicedAnalysis.setSplicer(tcalSplicer);
            tcalSplicedAnalysis.setStreamName("tcal");
            try {
                tcalInputEngine = new SpliceablePayloadReader(
                    "tcalInputEngine", 50000, tcalSplicer, tcalFactory);
                addMonitoredEngine(DAQConnector.TYPE_TCAL_DATA,
                    tcalInputEngine);

                if (isMonitoring) {
                    tcalBuilderMonitor = new SecBuilderMonitor("TcalBuilder",
                        tcalInputEngine, tcalSplicer, tcalDispatcher);
                    addMBean("tcalBuilder", tcalBuilderMonitor);
                }
            } catch (IOException iox) {
                // TODO - this is going to be hidden unless the
                // operator starts up in verbose mode.  Fix.
                LOG.error(iox);
                System.exit(1);
            }
        }

        // init snBuilder
        if (isSnEnabled) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Constructing SNBuilder");
            }
            snBufferCache = new VitreousBufferCache("SBSN", 250000000);
            snDispatcher = new FileDispatcher("sn", snBufferCache);
            addCache(DAQConnector.TYPE_SN_DATA, snBufferCache);
            //addMBean("snCache", snBufferCache);
            snFactory = new PayloadFactory(snBufferCache);
            snSplicedAnalysis = new SBSplicedAnalysis(snDispatcher);
            snSplicer = createSplicer(snSplicedAnalysis);
            addSplicer(snSplicer);

            snSplicedAnalysis.setSplicer(snSplicer);
            snSplicedAnalysis.setStreamName("sn");
            try {
                snInputEngine = new SpliceablePayloadReader("stringHubSnInput",
                    25000, snSplicer, snFactory);
                addMonitoredEngine(DAQConnector.TYPE_SN_DATA, snInputEngine);

                if (isMonitoring) {
                    snBuilderMonitor = new SecBuilderMonitor("SnBuilder",
                        snInputEngine, snSplicer, snDispatcher);
                    addMBean("snBuilder", snBuilderMonitor);
                }
            } catch (IOException iox) {
                // TODO see tcal comment
                LOG.error(iox);
                System.exit(1);
            }
        }

        // init moniBuilder classes
        if (isMoniEnabled) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Constructing MoniBuilder");
            }
            moniBufferCache = new VitreousBufferCache("SBMoni", 350000000);
            moniDispatcher = new FileDispatcher("moni", moniBufferCache);
            addCache(DAQConnector.TYPE_MONI_DATA, moniBufferCache);
            //addMBean("moniCache", moniBufferCache);
            moniFactory = new PayloadFactory(moniBufferCache);
            moniSplicedAnalysis = new MoniAnalysis(moniDispatcher);
            moniSplicer = createSplicer(moniSplicedAnalysis);
            addSplicer(moniSplicer);

            if (disableIceTopFastMoni) {
                moniSplicedAnalysis.disableIceTopFastMoni();
            }

            moniSplicedAnalysis.setSplicer(moniSplicer);
            moniSplicedAnalysis.setStreamName("moni");
            try {
                moniInputEngine = new SpliceablePayloadReader(
                    "stringHubMoniInput", 5000, moniSplicer, moniFactory);
                addMonitoredEngine(DAQConnector.TYPE_MONI_DATA,
                    moniInputEngine);

                if (isMonitoring) {
                    moniBuilderMonitor = new SecBuilderMonitor("MoniBuilder",
                        moniInputEngine, moniSplicer, moniDispatcher);
                    addMBean("moniBuilder", moniBuilderMonitor);
                }
            } catch (IOException iox) {
                LOG.error(iox);
                System.exit(1);
            }
        }

        addMBean("jvm", new MemoryStatistics());
        addMBean("system", new SystemStatistics());
    }

    private Splicer<Spliceable> createSplicer(SplicedAnalysis<Spliceable> a)
        throws DAQCompException
    {
        if (!USE_PRIO_SPLICER) {
            return new HKN1Splicer<Spliceable>(a, splicerCmp, LAST_SPLICEABLE);
        }

        final int totChannels = DAQCmdInterface.DAQ_MAX_NUM_STRINGS +
            DAQCmdInterface.DAQ_MAX_NUM_IDH;
        try {
            return new PrioritySplicer<Spliceable>("SBSorter", a, splicerCmp,
                                                   LAST_SPLICEABLE,
                                                   totChannels);
        } catch (SplicerException se) {
            throw new DAQCompException("Cannot create splicer", se);
        }
    }

    /**
     * Parse string as a boolean value.
     * Allow any of "true", "yes", or "1" (case-insensitive)
     *
     * @return <tt>true</tt> if the string has a "true" value
     */
    private static final boolean parseBoolean(String str)
    {
        if (str == null) {
            return false;
        }

        return str.equalsIgnoreCase("true") || str.equalsIgnoreCase("yes") ||
            str == "1";
    }

    /**
     * Receive the name of the directory holding the XML configuration
     * tree.
     *
     * @param dirName directory name
     */
    public void setGlobalConfigurationDir(String dirName)
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Setting global config dir to: " + dirName);
        }

        configDirName = dirName;

        IDOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistry.loadRegistry(dirName);
        } catch (ParserConfigurationException pce) {
            LOG.error("Cannot load DOM registry", pce);
            domRegistry = null;
        } catch (SAXException se) {
            LOG.error("Cannot load DOM registry", se);
            domRegistry = null;
        } catch (IOException ioe) {
            LOG.error("Cannot load DOM registry", ioe);
            domRegistry = null;
        }

        if (domRegistry != null) {
            moniSplicedAnalysis.setDOMRegistry(domRegistry);
            tcalSplicedAnalysis.setDOMRegistry(domRegistry);
        }
    }

    /**
     * Configure a component using the specified configuration name.
     *
     * @param configName configuration name
     *
     * @throws DAQCompException if there is a problem configuring
     */
    public void configuring(String configName) throws DAQCompException
    {
        String runConfigFileName = configDirName + "/" + configName;

        if (!configName.endsWith(".xml")) {
            runConfigFileName += ".xml";
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Configuring with config: " + runConfigFileName);
        }

        parseConfigFile(runConfigFileName);

        moniSplicedAnalysis.setAlertQueue(getAlertQueue());
        tcalSplicedAnalysis.setAlertQueue(getAlertQueue());
    }

    /**
     * Parse the run config file looking for prescale values for each
     * stream, chaining exceptions, etc.
     *
     * @param runConfigFileName - the run config filename to parse.
     */
    private void parseConfigFile(String runConfigFileName)
        throws DAQCompException
    {

        try {
            Document doc = DocumentBuilderFactory.newInstance().
                newDocumentBuilder().parse(runConfigFileName);

            XPath xpath = XPathFactory.newInstance().newXPath();

            String sb_expr =
                "/runConfig/runComponent[@name='secondaryBuilders']";
            NodeList sbNodes = (NodeList) xpath.evaluate(sb_expr, doc,
                XPathConstants.NODESET);
            if (sbNodes.getLength() != 1) {
                throw new DAQCompException("Found " + sbNodes.getLength() +
                     " secondaryBuilder runComponents rather than 1 in " +
                          runConfigFileName);
            }
            Node sbNode = sbNodes.item(0);

            if (isTcalEnabled) {
                long tcal_prescale = parsePrescale("tcal", sbNode, xpath);
                if (tcal_prescale > 0L) {
                    tcalSplicedAnalysis.setPreScale(tcal_prescale);
                }
            }

            if (isSnEnabled) {
                long sn_prescale = parsePrescale("sn", sbNode, xpath);
                if (sn_prescale > 0L) {
                    snSplicedAnalysis.setPreScale(sn_prescale);
                }
            }

            if (isMoniEnabled) {
                long moni_prescale = parsePrescale("moni", sbNode, xpath);
                if (moni_prescale > 0L) {
                    moniSplicedAnalysis.setPreScale(moni_prescale);
                }
            }

        } catch (ParserConfigurationException ex) {
            throw new DAQCompException(ex);
        } catch (SAXException ex) {
            throw new DAQCompException(ex);
        } catch (IOException ex) {
            throw new DAQCompException(ex);
        } catch (XPathExpressionException ex) {
            throw new DAQCompException(ex);
        }

    }

    /**
     * Helper function to parse the specified stream prescale value from
     * the given DOM Node using the provided XPath interace.
     *
     * @param stream - the name of the stream (tcal, sn, moni)
     * @param sbNode - the DOM Node of the secondaryBuilder Element
     * from the run config file.
     * @param xpath - the XPath interface to use
     *
     * @return the prescale value as a Long or null if not specified.
     */
    private long parsePrescale(String stream, Node sbNode, XPath xpath)
        throws XPathExpressionException, DAQCompException
    {

        String prescale_expr = "stream[@name='" + stream + "']/prescale";

        String prescale = (String) xpath.evaluate(prescale_expr, sbNode,
                                                  XPathConstants.STRING);
        if (prescale.length() == 0) {
            return Long.MIN_VALUE;
        }

        long ps;
        try {
            ps = Long.valueOf(prescale);
        } catch (NumberFormatException nfe) {
            throw new DAQCompException(nfe);
        }

        if (ps <= 0L) {
            throw new DAQCompException("Bad " + stream + " prescale \"" +
                                       prescale + "\"");
        }

        return ps;
    }

    /**
     * Set the destination directory where the dispatch files will be saved.
     *
     * @param dirName The absolute path of directory where the dispatch
     * files will be stored.
     */
    public void setDispatchDestStorage(String dirName)
    {
        if (isTcalEnabled) {
            tcalDispatcher.setDispatchDestStorage(dirName);
        }
        if (isSnEnabled) {
            snDispatcher.setDispatchDestStorage(dirName);
        }
        if (isMoniEnabled) {
            moniDispatcher.setDispatchDestStorage(dirName);
        }
    }

    /**
     * Set the maximum size of the dispatch file.
     *
     * @param maxFileSize the maximum size of the dispatch file.
     */
    public void setMaxFileSize(long maxFileSize)
    {
        if (isTcalEnabled) {
            tcalDispatcher.setMaxFileSize(maxFileSize);
        }
        if (isSnEnabled) {
            snDispatcher.setMaxFileSize(maxFileSize);
        }
        if (isMoniEnabled) {
            moniDispatcher.setMaxFileSize(maxFileSize);
        }
    }

    /**
     * Return this component's svn version id as a String.
     *
     * @return svn version id as a String
     */
    public String getVersionInfo()
    {
        return "$Id: SBComponent.java 15570 2015-06-12 16:19:32Z dglo $";
    }

    /**
     * Set the run number at the start of the run.
     *
     * @param runNumber run number
     */
    public void starting(int runNumber)
    {
        if (LOG.isInfoEnabled()) {
            LOG.info("Setting runNumber = " + runNumber);
        }
        if (isTcalEnabled) {
            tcalSplicedAnalysis.setRunNumber(runNumber);
        }
        if (isSnEnabled) {
            snSplicedAnalysis.setRunNumber(runNumber);
        }
        if (isMoniEnabled) {
            moniSplicedAnalysis.setRunNumber(runNumber);
        }

        this.runNumber = runNumber;
    }

    /**
     * Save final event counts.
     */
    public void stopped()
    {
        moniSplicedAnalysis.finishMonitoring();
        snSplicedAnalysis.finishMonitoring();
        tcalSplicedAnalysis.finishMonitoring();

        runData.put(runNumber,
                    new SBRunData(tcalDispatcher.getNumDispatchedEvents(),
                                  snDispatcher.getNumDispatchedEvents(),
                                  moniDispatcher.getNumDispatchedEvents()));
    }

    /**
     * Perform any actions related to switching to a new run.
     *
     * @param runNumber new run number
     *
     * @throws DAQCompException if there is a problem switching the component
     */
    public void switching(int runNumber)
        throws DAQCompException
    {
        long numTCal = 0;
        long numSN = 0;
        long numMoni = 0;

        if (LOG.isInfoEnabled()){
            LOG.info("Setting runNumber = " + runNumber);
        }
        if (isTcalEnabled) {
            numTCal = tcalSplicedAnalysis.switchToNewRun(runNumber);
        }
        if (isSnEnabled) {
            numSN = snSplicedAnalysis.switchToNewRun(runNumber);
        }
        if (isMoniEnabled) {
            numMoni = moniSplicedAnalysis.switchToNewRun(runNumber);
        }

        // save run data for later retrieval
        runData.put(this.runNumber,
                    new SBRunData(numTCal, numSN, numMoni));

        this.runNumber = runNumber;
    }

    /**
     * Get the run data for the specified run.
     *
     * @return array of <tt>long</tt> values:<ol>
     *    <li>number of time calibration events
     *    <li>number of supernova events
     *    <li>number of monitoring events
     *    </ol>
     */
    public long[] getRunData(int runNum)
        throws DAQCompException
    {
        if (!runData.containsKey(runNum)) {
            throw new DAQCompException("No final counts found for run " +
                                       runNum + "; state is " + getState());
        }

        return runData.get(runNum).toArray();
    }

    /**
     * Save run counts after
    /**
     * Get the current run number.
     *
     * @return current run number
     */
    public int getRunNumber()
    {
        return runNumber;
    }

    /**
     * Run a DAQ component server.
     *
     * @param args command-line arguments
     *
     */
    public static void main(String[] args) throws Exception
    {
        SecBuilderCompConfig cfg = new SecBuilderCompConfig();

        DAQCompServer srvr;
        try {
            srvr = new DAQCompServer(new SBComponent(cfg), args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
            return;
        }
        srvr.startServing();
    }
}
