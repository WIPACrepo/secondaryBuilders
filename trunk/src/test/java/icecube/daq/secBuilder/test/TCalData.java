package icecube.daq.secBuilder.test;

import icecube.daq.payload.PayloadException;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.impl.TimeCalibration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TCalData
{
    private long utcTime;
    private long domId;
    private long dorTx;
    private long dorRx;
    private short[] dorWaveform;
    private long domTx;
    private long domRx;
    private short[] domWaveform;
    private byte format = (byte) 1;
    private String gpsStr;
    private byte quality = (byte) ' ';
    private long syncTime;

    private boolean secondsSet;
    private long seconds;

    public TCalData(long utcTime, long domId, long dorTx, long dorRx,
                    short[] dorWaveform, long domTx, long domRx,
                    short[] domWaveform, String gpsStr, long syncTime)
    {
        this.utcTime = utcTime;
        this.domId = domId;
        this.dorTx = dorTx;
        this.dorRx = dorRx;
        this.dorWaveform = copyWaveform(dorWaveform);
        this.domTx = domTx;
        this.domRx = domRx;
        this.domWaveform = copyWaveform(domWaveform);
        this.gpsStr = gpsStr;
        this.syncTime = syncTime;

        final String errmsg;
        if (dorTx > dorRx) {
            errmsg = String.format("DOR xmit time %d is later than DOR recv" +
                                   " time %d for %012x", dorTx, dorRx, domId);
        } else if (domRx > domTx) {
            errmsg = String.format("DOM recv time %d is later than DOM xmit" +
                                   " time %d for %012x", domRx, domTx, domId);
        } else {
            errmsg = null;
        }
        if (errmsg != null) {
            throw new Error(errmsg);
        }
    }

    TCalData copy()
    {
        return new TCalData(utcTime, domId, dorTx, dorRx, dorWaveform, domTx,
                            domRx, domWaveform, gpsStr, syncTime);
    }

    private static final short[] copyWaveform(short[] waveform)
    {
        short[] copy = new short[waveform.length];
        System.arraycopy(waveform, 0, copy, 0, waveform.length);
        return copy;
    }

    public TimeCalibration create()
        throws PayloadException
    {
        return createTCal(utcTime, domId, dorTx, dorRx, dorWaveform, domTx,
                          domRx, domWaveform, format, gpsStr, quality,
                          syncTime);
    }

    private static TimeCalibration createTCal(long utcTime, long domId,
                                              long dorTx, long dorRx,
                                              short[] dorWaveform, long domTx,
                                              long domRx, short[] domWaveform,
                                              byte format, String gpsStr,
                                              byte quality, long syncTime)
        throws PayloadException
    {
        final int bufLen = 338;

        ByteBuffer buf = ByteBuffer.allocate(bufLen);

        // payload header
        buf.putInt(0, bufLen);
        buf.putInt(4, PayloadRegistry.PAYLOAD_ID_TCAL);
        buf.putLong(8, utcTime);

        // tcal header
        buf.putLong(16, domId);

        final ByteOrder origOrder = buf.order();
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.position(24);

        buf.putShort((short) (buf.capacity() - buf.position()));
        buf.putShort((short) 1);

        // write DOR times
        buf.putLong(dorTx);
        buf.putLong(dorRx);
        for (int i = 0; i < dorWaveform.length; i++) {
            buf.putShort(dorWaveform[i]);
        }

        // write DOM times
        buf.putLong(domRx);
        buf.putLong(domTx);
        for (int i = 0; i < domWaveform.length; i++) {
            buf.putShort(domWaveform[i]);
        }

        buf.put(format);
        buf.put(gpsStr.getBytes());
        buf.put(quality);

        buf.order(ByteOrder.BIG_ENDIAN);

        buf.putLong(syncTime);

        if (bufLen != buf.position()) {
            throw new Error("Unexpected buffer length " + buf.position() +
                            ", expected " + bufLen);
        }

        buf.order(origOrder);
        buf.flip();

        return new TimeCalibration(buf, 0);
    }

    private static int extractInteger(String fldName, String str, int pos,
                                      int len)
        throws PayloadException
    {
        try {
            return Integer.parseInt(str.substring(pos, pos + len));
        } catch (NumberFormatException nfe) {
            throw new PayloadException("Could not extract " + fldName +
                                       " from \"" + str + "\"", nfe);
        }
    }

    public String getDateString() { return gpsStr; }

    public long getDomId() { return domId; }

    public long getDomRXTime() { return domRx; }
    public long getDomTXTime() { return domTx; }
    public short[] getDomWaveform() { return domWaveform; }

    public long getDorGpsSyncTime() { return syncTime; }

    public long getDorRXTime() { return dorRx; }
    public long getDorTXTime() { return dorTx; }
    public short[] getDorWaveform() { return dorWaveform; }

    // this was copied from icecube.daq.payload.TimeCalibration (and modified)
    public long getGpsSeconds()
        throws PayloadException
    {
        if (!secondsSet) {
            final int jday = extractInteger("Julian day", gpsStr, 0, 3);
            final int hour = extractInteger("Hour", gpsStr, 4, 2);
            final int minute = extractInteger("Minute", gpsStr, 7, 2);
            final int second = extractInteger("Second", gpsStr, 10, 2);

            seconds = ((((((jday - 1) * 24) + hour) * 60) + minute) * 60) +
                second;

            secondsSet = true;
        }

        return seconds;
    }

    public long getUTCTime() { return utcTime; }

    public void setDateString(String val) { gpsStr = val; }

    public void setDomId(long val) { domId = val; }

    public void setDomRXTime(long val) { domRx = val; }
    public void setDomTXTime(long val) { domTx = val; }
    public void setDomWaveform(short[] val) { domWaveform = val; }

    public void setDorGpsSyncTime(long val) { syncTime = val; }

    public void setDorRXTime(long val) { dorRx = val; }
    public void setDorTXTime(long val) { dorTx = val; }
    public void setDorWaveform(short[] val) { dorWaveform = val; }

    public void setFormat(int val) { format = (byte) val; }

    public void setQuality(char ch) { quality = (byte) ch; }
}
