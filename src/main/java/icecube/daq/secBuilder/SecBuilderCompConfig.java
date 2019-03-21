/**
 * Class: SecBuilderCompConfig
 *
 * Date: Dec 1, 2006 7:19:50 AM
 *
 * (c) 2005 IceCube collaboration
 */
package icecube.daq.secBuilder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class provides configuration for secondary builders
 *
 * @author artur
 * @version $Id: SecBuilderCompConfig.java,v 1.0 2006/12/01 07:19:50 artur Exp$
 */
public class SecBuilderCompConfig implements SBCompConfig
{

    private int granularity = 256;
    private long maxCacheByte = 30000000;
    private long maxAcquireBytes = 30000000;
    private boolean isTcalEnabled = true;
    private boolean isSnEnabled = true;
    private boolean isMoniEnabled = true;
    private boolean isMonitoring = true;

    private Log log = LogFactory.getLog(SecBuilderCompConfig.class);

    public SecBuilderCompConfig(int granularity, long maxCacheByte,
                                long maxAcquireBytes, boolean isTcalEnabled,
                                boolean isSnEnabled, boolean isMoniEnabled,
                                boolean isMonitoring)
    {

        this.granularity = granularity;
        this.maxCacheByte = maxCacheByte;
        this.maxAcquireBytes = maxAcquireBytes;
        this.isTcalEnabled = isTcalEnabled;
        this.isSnEnabled = isSnEnabled;
        this.isMoniEnabled = isMoniEnabled;
        this.isMonitoring = isMonitoring;

        if (log.isInfoEnabled()) {
            log.info("SBConfigComp parameters: \n" +
                     "granularity = " + granularity + "\n" +
                     "maxCacheByte = " + maxCacheByte + "\n" +
                     "maxAcquireBytes = " + maxAcquireBytes + "\n" +
                     "isTcalEnabled = " + isTcalEnabled + "\n" +
                     "isSnEnabled = " + isSnEnabled + "\n" +
                     "isMoniEnabled = " + isMoniEnabled);
        }
    }

    public SecBuilderCompConfig()
    {
        if (log.isInfoEnabled()) {
            log.info("SBConfigComp parameters: \n" +
                     "granularity = " + granularity + "\n" +
                     "maxCacheByte = " + maxCacheByte + "\n" +
                     "maxAcquireBytes = " + maxAcquireBytes + "\n" +
                     "isTcalEnabled = " + isTcalEnabled + "\n" +
                     "isSnEnabled = " + isSnEnabled + "\n" +
                     "isMoniEnabled = " + isMoniEnabled);
        }
    }

    @Override
    public int getGranularity()
    {
        return granularity;
    }

    @Override
    public long getMaxAcquireBytes()
    {
        return maxAcquireBytes;
    }

    @Override
    public long getMaxCacheBytes()
    {
        return maxCacheByte;
    }

    @Override
    public boolean isMoniEnabled()
    {
        return isMoniEnabled;
    }

    @Override
    public boolean isMonitoring()
    {
        return isMonitoring;
    }

    @Override
    public boolean isSnEnabled()
    {
        return isSnEnabled;
    }

    @Override
    public boolean isTcalEnabled()
    {
        return isTcalEnabled;
    }
}
