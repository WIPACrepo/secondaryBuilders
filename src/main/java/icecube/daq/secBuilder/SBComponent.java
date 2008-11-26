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
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.SpliceableFactory;
import icecube.daq.splicer.Splicer;

import java.io.IOException;

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
 * @version $Id: SBComponent.java 3672 2008-11-26 00:05:16Z ksb $
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

    private String configDirName;

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
            //addMBean("tcalCache", tcalBufferCache);
            tcalFactory = new MasterPayloadFactory(tcalBufferCache);
            tcalSplicedAnalysis = new SBSplicedAnalysis(tcalFactory,
                    tcalDispatcher,
                    tcalBuilderMonitor);
            tcalSplicer = new HKN1Splicer(tcalSplicedAnalysis);
            addSplicer(tcalSplicer);

            tcalSplicedAnalysis.setSplicer(tcalSplicer);
            tcalSplicedAnalysis.setStreamName("tcal");
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
            //addMBean("snCache", snBufferCache);
            snFactory = new MasterPayloadFactory(snBufferCache);
            snSplicedAnalysis = new SBSplicedAnalysis(snFactory,
                    snDispatcher,
                    snBuilderMonitor);
            snSplicer = new HKN1Splicer(snSplicedAnalysis);
            addSplicer(snSplicer);

            snSplicedAnalysis.setSplicer(snSplicer);
            snSplicedAnalysis.setStreamName("sn");
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
            //addMBean("moniCache", moniBufferCache);
            moniFactory = new MasterPayloadFactory(moniBufferCache);
            moniSplicedAnalysis = new SBSplicedAnalysis(moniFactory,
                    moniDispatcher,
                    moniBuilderMonitor);
            moniSplicer = new HKN1Splicer(moniSplicedAnalysis);
            addSplicer(moniSplicer);

            moniSplicedAnalysis.setSplicer(moniSplicer);
            moniSplicedAnalysis.setStreamName("moni");
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

        addMBean("jvm", new MemoryStatistics());
        addMBean("system", new SystemStatistics());
    }

    /**
     * Receive the name of the directory holding the XML configuration
     * tree.
     *
     * @param dirName directory name
     */
    public void setGlobalConfigurationDir(String dirName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Setting global config dir to: " + dirName);
        }
        configDirName = dirName;
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
        String runConfigFileName = configDirName + "/" + configName;;

        if (!configName.endsWith(".xml")) {
            runConfigFileName += ".xml";
        }

        if (log.isDebugEnabled()) {
            log.debug("Configuring with config: " + runConfigFileName);
        }

        parseConfigFile(runConfigFileName);
    }


    /**
     * Parse the run config file looking for prescale values for each
     * stream, chaining exceptions, etc.
     *
     * @param runConfigFileName - the run config filename to parse.
     */
    private void parseConfigFile(String runConfigFileName)
        throws DAQCompException {

        try {
            Document doc = DocumentBuilderFactory.newInstance().
                newDocumentBuilder().parse(runConfigFileName);

            XPath xpath = XPathFactory.newInstance().newXPath();

            String sb_expr = "/runConfig/runComponent[@name='secondaryBuilders']";
            NodeList sbNodes = (NodeList) xpath.evaluate(sb_expr, doc, XPathConstants.NODESET);
            if(sbNodes.getLength() != 1) {
                throw new DAQCompException("Found " + sbNodes.getLength() +
                                           " secondaryBuilder runComponents rather than 1 in " +
                                           runConfigFileName);
            }
            Node sbNode = sbNodes.item(0);

            if (isTcalEnabled) {
                Long tcal_prescale = parsePrescale("tcal", sbNode, xpath);
                if(tcal_prescale != null) {
                    tcalSplicedAnalysis.setPreScale(tcal_prescale);
                }
            }

            if (isSnEnabled) {
                Long sn_prescale = parsePrescale("sn", sbNode, xpath);
                if(sn_prescale != null) {
                    snSplicedAnalysis.setPreScale(sn_prescale);
                }
            }

            if (isMoniEnabled) {
                Long moni_prescale = parsePrescale("moni", sbNode, xpath);
                if(moni_prescale != null) {
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
    private Long parsePrescale(String stream, Node sbNode, XPath xpath)
        throws XPathExpressionException, DAQCompException {

        String prescale_expr = "stream[@name='" + stream + "']/prescale";
        
        String prescale = (String) xpath.evaluate(prescale_expr, sbNode,
                                                  XPathConstants.STRING);
        if(prescale.length() == 0) {
            return null;
        }

        long ps = 0L;
        try {
            ps = Long.valueOf(prescale);
        } catch (NumberFormatException nfe) {
            throw new DAQCompException(nfe);
        }
        return ps;
    }

    public void setRunNumber(int runNumber) {
        if (log.isInfoEnabled()){
            log.info("Setting runNumber = " + runNumber);
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
     * Return this component's svn version id as a String.
     *
     * @return svn version id as a String
     */
    public String getVersionInfo()
    {
        return "$Id: SBComponent.java 3672 2008-11-26 00:05:16Z ksb $";
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
            return; // without this, compiler whines about uninitialized 'srvr'
        }
        srvr.startServing();
    }
}
