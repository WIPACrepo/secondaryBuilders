package icecube.daq.secBuilder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class WaveformStats
{
    // the first NUM_SAMPLES samples are used to determine the baseline
    private static final int NUM_SAMPLES = 19;

    private static final int MAX_SAMPLES = 48;

    private int l1;
    private int l2;
    private double avg;
    private double rms;
    //private double[] height = new double[1];
    //private double[] width = new double[1];
    //private double[] area = new double[1];
    //private double[] rms_area = new double[1];
    //private int[] peak_sample = new int[1];

    WaveformStats(short[] waveform, int peakchan, double thresh)
    {
        avg = computeBaselineAverage(waveform);
        rms = computeBaselineRMS(waveform, avg);
        l1 = computeLimit1(waveform, avg, thresh);
        l2 = computeLimit2(waveform, peakchan, avg);
        // get pulse shape statistics for wf
        //pulseshape(waveform, l1, l2, avg, height,
        //           width, area, rms_area,
        //           peak_sample);
    }

    public double getAverage() { return avg; }
    public int getL1() { return l1; }
    public int getL2() { return l2; }
    public double getRMS() { return rms; }

    public boolean isValid() { return l1 - l2 < -7; }

    // calculate baseline for waveform
    private static double computeBaselineAverage(short[] waveform)
    {
        double sum = 0.0;
        for(int i = 0; i < NUM_SAMPLES; i++) {
            sum = sum + waveform[i];
        }
        return sum / NUM_SAMPLES;
    }

    // calculate baseline rms for waveform
    private static double computeBaselineRMS(short[] waveform, double avg)
    {
        double sum = 0.0;
        for(int i = 0; i < NUM_SAMPLES; i++) {
            sum = sum + (waveform[i] - avg) * (waveform[i] - avg);
        }
        return Math.sqrt(sum / NUM_SAMPLES);
    }

    /**
     * function to calculate the limit of the waveform's positive-going pulse.
     */
    private static int computeLimit1(short[] waveform, double avg,
                                     double std_dev)
    {
        int i = NUM_SAMPLES;
        int l1 = i;

        while (Math.abs(avg - waveform[i]) < std_dev) {
            l1 = i++;
            if(i >= MAX_SAMPLES-1) {
                break;
            }
        }

        return l1 + 1;
    }

    /**
     * function to calculate the limit of the waveform's positive-going pulse.
     */
    private static int computeLimit2(short[] waveform, int peakchan,
                                     double avg)
    {
        int i = peakchan;
        int l2 = i;

        while ((waveform[i] - avg) > 0) {
            l2 = i++;
            if(i >= MAX_SAMPLES - 2) {
                break;
            }
        }

        return l2;
    }

/*
    private static void pulseshape(short[] waveform, int l1, int l2,
                                   double avg, double pk_height[],
                                   double width[], double area[],
                                   double rms_area[], int peak_sample[])
    {
        int i, lpeak, upper,lower=0;
        double lf,height,a,b,sum,sumsq;
        lf=0.5;

        // find sample no of the pulse peak
        i=l1;
        while (waveform[i+1]>waveform[i]) {
            ++i;
            if(i>=MAX_SAMPLES-1) {
                break;
            }
        }
        lpeak=i;
        peak_sample[0]=lpeak;
        pk_height[0]=waveform[lpeak]-avg;
        height =lf*(pk_height[0]);

        // determine samples just below and just above the fractional height
        // on positive going slope of pulse
        for(i=l1;i<lpeak;i++) {
            if((waveform[i]-avg)>(height)) {
                lower=i-1;
                break;
            }
        }
        upper=lower+1;

        // added by DIMA
        upper=upper<0?0:upper>=MAX_SAMPLES?MAX_SAMPLES-1:upper;
        lower=lower<0?0:lower>=MAX_SAMPLES?MAX_SAMPLES-1:lower;

        double[] intercept = new double[2];

        a=waveform[upper]-waveform[lower];
        b=(waveform[lower]-avg)-a*(lower);
        intercept[0]=a>0?(height-b)/a:0;

        // determine samples just below and just above the fractional height
        // on negative going side of pulse
        for(i=l2;i>lpeak;i--) {
            if((waveform[i]-avg)>(height)) {
                lower=i+1;
                break;
            }
        }
        if(lower==0) lower=1;
        upper=lower-1;
        a=waveform[lower]-waveform[upper];
        b=(waveform[upper]-avg)-a*(upper);
        intercept[1]=a>0?(height-b)/a:0;

        width[0] = intercept[1] - intercept[0];
        sum=0.0;
        sumsq=0.0;
        for(i=l1;i<l2+1;i++) {
            sum=sum+waveform[i]-avg;
            sumsq=sumsq+(waveform[i]-avg)*(waveform[i]-avg);
        }
        area[0]=sum;
        rms_area[0]=l2>l1?Math.sqrt(sumsq/(l2-l1)):0;
    }
*/
}

abstract class WaveformFit
{
    // approx sample no where the peak is
    // - used for finding positive excursion limit of pulse
    public static final int peakchan = 31;
    // the number of counts above the baseline the first point in the
    // positive-going portion must be.
    public static final double thresh = 15;
    // number of counts above baseline the second fit point must be in
    // lead-edge by fit
    public static final int threshold = 60;
    // number of waveform sample points to be fit
    public static final int ndata = 5;

    private WaveformStats dor_stats;
    private WaveformStats dom_stats;

    private long domT0;
    private long delta;
    private long rtrip;

    private double dor_sample;
    private double dom_sample;

    public WaveformFit(long dor_tx, long dor_rx, short[] dor_wf,
                       long dom_rx, long dom_tx, short[] dom_wf,
                       boolean verbose)
        throws TCalException
    {
        dor_stats = new WaveformStats(dor_wf, peakchan, thresh);
        dom_stats = new WaveformStats(dom_wf, peakchan, thresh);

        if(verbose){
            System.out.printf("Baseline: domba %.3f dombr %.3f" +
                              " dorba %.3f dorbr %.3f\n",
                              dom_stats.getAverage(), dom_stats.getRMS(),
                              dor_stats.getAverage(), dor_stats.getRMS());
        }

        computeFit(dor_tx, dor_rx, dor_wf, dom_tx, dom_rx, dom_wf, verbose);

        if (verbose) {
            System.out.printf("%s: domT0 %d delta %d\n\trtrip %d\n" +
                              "\tsample #: dor %.4f dom %.4f\n", getName(),
                              domT0, delta, rtrip,
                              dor_sample, dom_sample);
        }
    }

    abstract void computeFit(long dor_tx, long dor_rx, short[] dor_wf,
                             long dom_tx, long dom_rx, short[] dom_wf,
                             boolean verbose);

    long getDOMT0() { return domT0; }
    long getDelta() { return delta; }
    long getRoundtrip() { return rtrip; }

    double getDomAverage() { return dom_stats.getAverage(); }
    int getDomLimit1() { return dom_stats.getL1(); }
    int getDomLimit2() { return dom_stats.getL2(); }
    double getDomSample() { return dom_sample; }

    double getDorAverage() { return dor_stats.getAverage(); }
    int getDorLimit1() { return dor_stats.getL1(); }
    int getDorLimit2() { return dor_stats.getL2(); }
    double getDorSample() { return dor_sample; }

    abstract String getName();

    boolean isValid() { return dor_stats.isValid() && dom_stats.isValid(); }

    void setDOMT0(long val) { domT0 = val; }
    void setDelta(long val) { delta = val; }
    void setRoundtrip(long val) { rtrip = val; }
    void setSamples(double dor_val, double dom_val)
    {
        dor_sample = dor_val;
        dom_sample = dom_val;
    }
}

/**
 * centroid
 * (center of gravity of the part of the pulse above the baseline)
 */
class WaveformFitCentroid
    extends WaveformFit
{
    public WaveformFitCentroid(long dor_tx, long dor_rx, short[] dor_wf,
                               long dom_rx, long dom_tx, short[] dom_wf,
                               boolean verbose)
        throws TCalException
    {
        super(dor_tx, dor_rx, dor_wf, dom_rx, dom_tx, dom_wf, verbose);
    }

    /**
     * function to calculate centroid
     * linear interpolation of point where wf crosses the base line
     * Centroid is calculated for positive going portion of pulse.
     */
    static double computeCentroid(short[] array1, int l1, int l2, double avg)
    {
        // calculate centroid
        double sum1=0;
        double sum2=0;
        for (int i = l1; i <= l2; i++) {
            sum1=sum1+i*(array1[i]-avg);
            sum2=sum2+array1[i]-avg;
        }
        return sum2 > 0 ? sum1 / sum2 : 0;
    }

    void computeFit(long dor_tx, long dor_rx, short[] dor_wf,
                    long dom_tx, long dom_rx, short[] dom_wf,
                    boolean verbose)
    {
        // calculate centroid and crossover
        double dor_centr = computeCentroid(dor_wf, getDorLimit1(),
                                           getDorLimit2(), getDorAverage());
        double dom_centr = computeCentroid(dom_wf, getDomLimit1(),
                                           getDomLimit2(), getDomAverage());

        setRoundtrip(500*(long)(dor_rx-dor_tx)-250*(long)(dom_tx-dom_rx)-
                     (long)(500*(47-dor_centr)+500*(47-dom_centr)));
        setDOMT0((250*(long)(dom_rx+dom_tx)-(long)(500* (47-dom_centr)))/2);
        setDelta((500*(long)(dor_rx+dor_tx)- 250*(long)(dom_tx+dom_rx)+
                  (long)(500*(dor_centr-dom_centr)))/2);

        setSamples(dor_centr, dom_centr);
    }

    String getName() { return "Centroid"; }
}

/**
 * crossover
 * (intercept of the downgoing part of the pulse with the baseline)
 */
class WaveformFitCrossover
    extends WaveformFit
{
    public WaveformFitCrossover(long dor_tx, long dor_rx, short[] dor_wf,
                                long dom_rx, long dom_tx, short[] dom_wf,
                                boolean verbose)
        throws TCalException
    {
        super(dor_tx, dor_rx, dor_wf, dom_rx, dom_tx, dom_wf, verbose);
    }

    /**
     * function to calculate crossover point where wf crosses the base line
     * crossw is a weighted crossover  (2*cross1+cross2)/3.
     */
    static double computeWeightedCrossover(short[] array1, int l1, int l2,
                                           double avg)
    {
        int y1 = array1[l2-1];
        int y2 = array1[l2];
        int y3 = array1[l2+1];
        int y4 = array1[l2+2];

        // compute sample just below and just above baseline
        double cross1 = l2 + ((y3!=y2)?(avg-y2)/(y3-y2):0);
        // compute samples just below and just above samples used in cross1
        double cross2 = l2-1 + ((y4!=y1)?(avg-y1) *3.0/(y4-y1):0);

        return (2.0 * cross1 + cross2) / 3.0;
    }

    void computeFit(long dor_tx, long dor_rx, short[] dor_wf,
                    long dom_tx, long dom_rx, short[] dom_wf,
                    boolean verbose)
    {
        // calculate crossover
        double dor_crossw =
            computeWeightedCrossover(dor_wf, getDorLimit1(),
                                     getDorLimit2(), getDorAverage());
        double dom_crossw =
            computeWeightedCrossover(dom_wf, getDomLimit1(),
                                     getDomLimit2(), getDomAverage());

        setRoundtrip(500*(long)(dor_rx-dor_tx)-250*(long)(dom_tx-dom_rx)-
                     (long)(500*(47-dor_crossw)+500*(47-dom_crossw)));
        setDOMT0((250*(long)(dom_rx+dom_tx)-(long)(500* (47-dom_crossw)))/2);
        setDelta((500*(long)(dor_rx+dor_tx)-250*(long)(dom_tx+dom_rx)+
                  (long)(500*(dor_crossw-dom_crossw)))/2);

        setSamples(dor_crossw, dom_crossw);
    }

    String getName() { return "Crossover"; }
}

/**
 * threshold (intercept with half height)
 */
class WaveformFitThreshold
    extends WaveformFit
{
    private static final int MAX_SAMPLES = 48;

    public WaveformFitThreshold(long dor_tx, long dor_rx, short[] dor_wf,
                                long dom_rx, long dom_tx, short[] dom_wf,
                                boolean verbose)
        throws TCalException
    {
        super(dor_tx, dor_rx, dor_wf, dom_rx, dom_tx, dom_wf, verbose);
    }

    void computeFit(long dor_tx, long dor_rx, short[] dor_wf,
                    long dom_tx, long dom_rx, short[] dom_wf,
                    boolean verbose)
    {
        // fraction of pulse height for leading edge threshold.
        final double lf = 0.5;
        int[] lower=new int[1], upper=new int[1];
        double[] dor_leadedge = new double[1], dom_leadedge = new double[1];

        // calculate leading edge by threshold intercept
        lead_edge_2(dor_wf, getDorLimit1(), getDorAverage(), lf, lower, upper,
                    dor_leadedge);
        lead_edge_2(dom_wf, getDomLimit1(), getDomAverage(), lf, lower, upper,
                    dom_leadedge);

        setRoundtrip(500*(long)(dor_rx-dor_tx)-250*(long)(dom_tx-dom_rx)-
                     (long)(500*(47-dor_leadedge[0])+500*(47-dom_leadedge[0])));
        setDOMT0((250*(long)(dom_rx+dom_tx)-
                  (long)(500* (47-dom_leadedge[0])))/2);
        setDelta((500*(long)(dor_rx+dor_tx)-250*(long)(dom_tx+dom_rx)+
                  (long)(500*(dor_leadedge[0]-dom_leadedge[0])))/2);
        setSamples(dor_leadedge[0], dom_leadedge[0]);
    }

    String getName() { return "Threshold"; }

  /* function_2 to calculate leading edge time by threshold intercept
     this function calculates the intercept of the leading edge with a level
     that is halfway (or some other fraction)  between the baseline and the
     peak.
   */
    private static void lead_edge_2(short[] array, int l1, double av,
                                    double lf, int[] lower, int[] upper,
                                    double[] leadedge)
    {
        int i, idelta, lpeak;
        double height,a,b,sum,weight;
        idelta=1;
        // find sample no of the pulse peak
        i=l1;
        while (array[i+1]>array[i]){ ++i; if(i>=MAX_SAMPLES-1) break; }
        lpeak=i;

        // determine samples just below and just above the fractional height
        height=lf*(array[lpeak]-av);
        for(i=l1;i<lpeak;i++){if((array[i]-av)>(height)){lower[0]=i-1;break;}}

        double[] intercept = new double[idelta];

        upper[0]=lower[0]+idelta;
        for(i=0;i<idelta;i++){
            a=((array[upper[0]+i]-av)-(array[lower[0]-i]-av))/(2*i+1.0);
            b=(array[lower[0]-i]-av)-a*(lower[0]-i);
            intercept[i]=a!=0?(height-b)/a:0;
        }

        sum=0.0;
        weight=0.0;
        for(i=0;i<idelta;i++){
            sum=sum+intercept[i]/(i+1);
            weight=weight+1.0/(i+1);
        }
        leadedge[0]=weight>0?sum/weight:0;
    }
}

/**
 * intercept (intercept with the baseline)
 */
class WaveformFitIntercept
    extends WaveformFit
{
    public WaveformFitIntercept(long dor_tx, long dor_rx, short[] dor_wf,
                                long dom_rx, long dom_tx, short[] dom_wf,
                                boolean verbose)
        throws TCalException
    {
        super(dor_tx, dor_rx, dor_wf, dom_rx, dom_tx, dom_wf, verbose);
    }

    void computeFit(long dor_tx, long dor_rx, short[] dor_wf,
                    long dom_tx, long dom_rx, short[] dom_wf,
                    boolean verbose)
    {
        int[] lower=new int[1], upper=new int[1];
        double[] dor_lead_edge_3 = new double[1],
            dom_lead_edge_3 = new double[1];

        // calculate leading edge by straight line fit to ndata data points
        lead_edge_3(dor_wf, getDorLimit1(), getDorAverage(), ndata, threshold,
                    peakchan, lower, upper, dor_lead_edge_3);
        lead_edge_3(dom_wf, getDomLimit1(), getDomAverage(), ndata, threshold,
                    peakchan, lower, upper, dom_lead_edge_3);

        setRoundtrip(500*(long)(dor_rx-dor_tx)-250*(long)(dom_tx-dom_rx)-
                     (long)(500*(47-dor_lead_edge_3[0])+500*
                            (47-dom_lead_edge_3[0])));
        setDOMT0((250*(long)(dom_rx+dom_tx)-
                  (long)(500* (47-dom_lead_edge_3[0])))/2);
        setDelta((500*(long)(dor_rx+dor_tx)-250*(long)(dom_tx+dom_rx)+
                  (long)(500*(dor_lead_edge_3[0]-dom_lead_edge_3[0])))/2);

        setSamples(dor_lead_edge_3[0], dom_lead_edge_3[0]);
    }

    private static void fit(int ndata, int[] xdata, double[] ydata,
                            double[] slope, double[] intercept)
    {
        int sumx=0,sumxx=0;
        double sumy=0,sumyy=0,sumxy=0;
        for (int i=0;i<ndata;i++){
            sumx +=xdata[i];
            sumxx += xdata[i]*xdata[i];
            sumy += ydata[i];
            sumyy += ydata[i]*ydata[i];
            sumxy += xdata[i]*ydata[i];
        }
        double denom=ndata*sumxx-sumx*sumx;
        slope[0]=denom!=0?(ndata*sumxy - sumx*sumy)/denom:0;
        intercept[0]=denom!=0?(sumxx*sumy - sumx*sumxy)/denom:0;
    }

    String getName() { return "Intercept"; }

    private static void lead_edge_3(short[] array, int l1, double av,
                                    int ndata, int threshold, int peakchan,
                                    int[] lower, int[] upper,
                                    double[] leadedge)
    {
        int[] xdata = new int[8];
        double[] ydata = new double[8];
        // determine first sample with counts =threshold above the baseline.
        // This will be first sample in the fit
        int first_sample=l1;
        for(int i=l1;i<peakchan;i++){
            if(array[i]-av > threshold){
                first_sample=i-1;
                break;
            }
        }
        lower[0]=first_sample;
        upper[0]=first_sample+ndata;

        for(int i=0;i<ndata;i++){
            xdata[i]=i;
            ydata[i]=array[i+first_sample]-av;
        }
        double[] slope = new double[1];
        double[] intercept = new double[1];
        fit(ndata,xdata,ydata,slope,intercept);
        leadedge[0]=first_sample-(slope[0]!=0?intercept[0]/slope[0]:0);
    }
}

/**
 * Program finds and fits COMM ADC WaveForm features (four algorithms).
 * Transcribed from FAT_reader/tcalfit.cxx which was:
 * Written by Bob Stokstad, reader interface routine tcalfit by DIMA.
 */
public class TCalFit
{
    private static final Log LOG = LogFactory.getLog(SBComponent.class);

    public static boolean tcalfit(int tcalalg, boolean verbose, long dor_tx,
                                  long dor_rx, short[] dor_wf, long dom_rx,
                                  long dom_tx, short[] dom_wf, long[] domT0Ptr,
                                  long[] deltaPtr, long[] rtripPtr)
        throws TCalException
    {
        WaveformFit wfit;
        switch (tcalalg) {
        case 1:
            wfit = new WaveformFitCentroid(dor_tx, dor_rx, dor_wf,
                                           dom_rx, dom_tx, dom_wf, verbose);
            break;
        case 2:
            wfit = new WaveformFitCrossover(dor_tx, dor_rx, dor_wf,
                                            dom_rx, dom_tx, dom_wf, verbose);
            break;
        case 3:
            wfit = new WaveformFitThreshold(dor_tx, dor_rx, dor_wf,
                                            dom_rx, dom_tx, dom_wf, verbose);
            break;
        case 4:
            wfit = new WaveformFitIntercept(dor_tx, dor_rx, dor_wf,
                                            dom_rx, dom_tx, dom_wf, verbose);
            break;
        default:
            throw new TCalException("Algorithm " + tcalalg + " is not valid");
        }

        domT0Ptr[0]=wfit.getDOMT0();
        deltaPtr[0]=wfit.getDelta();
        rtripPtr[0]=wfit.getRoundtrip();

        return wfit.isValid();
    }
}
