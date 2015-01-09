package icecube.daq.secBuilder.test;

import com.google.gson.Gson;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.payload.IUTCTime;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MockAlerter
    implements Alerter
{
    private HashMap<String, ArrayList<AlertData>> alerts =
        new HashMap<String, ArrayList<AlertData>>();
    private boolean closed;
    private boolean verbose;

    public MockAlerter()
    {
    }

    /**
     * Clear all cached alerts for the variable name
     *
     * @param varname alert variable name
     */
    public void clear(String varname)
    {
        alerts.remove(varname);
    }

    /**
     * Clear all cached alerts
     */
    public void clearAll()
    {
        alerts.clear();
    }

    /**
     * Close any open files/sockets.
     */
    public void close()
    {
        closed = true;
    }

    /**
     * Count the number of alerts received
     *
     * @param varname alert variable name
     *
     * @return number of alerts received
     */
    public int countAlerts(String varname)
    {
        if (!alerts.containsKey(varname)) {
            return 0;
        }

        return alerts.get(varname).size();
    }

    /**
     * Count the total number of alerts received
     *
     * @return total number of alerts received
     */
    public int countAllAlerts()
    {
        int total = 0;
        for (String key : alerts.keySet()) {
            total += alerts.get(key).size();
        }
        return total;
    }

    /**
     * Get the specified alert data
     *
     * @return alert data
     */
    public AlertData get(String varname, int index)
    {
        if (!alerts.containsKey(varname)) {
            return null;
        }

        ArrayList<AlertData> list = alerts.get(varname);
        if (index >= list.size()) {
            return null;
        }

        return list.get(index);
    }

    /**
     * Get the service name
     *
     * @return service name
     */
    public String getService()
    {
        return DEFAULT_SERVICE;
    }

    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    public boolean isActive()
    {
        return !closed;
    }

    /**
     * Send a Java object (as a JSON string) to a 0MQ server.
     *
     * @param obj object to send
     */
    public void sendObject(Object obj)
        throws AlertException
    {
        if (obj == null) {
            throw new Error("Cannot send null object");
        } else if (!(obj instanceof Map)) {
            throw new Error("Unexpected object type " +
                            obj.getClass().getName());
        }

        Map<String, Object> map = (Map<String, Object>) obj;

        String varname;
        if (!map.containsKey("varname")) {
            varname = null;
        } else {
            varname = (String) map.get("varname");
        }

        Alerter.Priority prio = Alerter.Priority.DEBUG;
        if (map.containsKey("prio")) {
            int tmpVal = (Integer) map.get("prio");
            for (Alerter.Priority p : Alerter.Priority.values()) {
                if (p.value() == tmpVal) {
                    prio = p;
                    break;
                }
            }
        }

        String dateStr;
        if (!map.containsKey("t")) {
            dateStr = null;
        } else {
            dateStr = (String) map.get("t");
        }

        Map<String, Object> values;
        if (!map.containsKey("value")) {
            values = null;
        } else {
            values = new HashMap<String, Object>();

            Map<String, Object> tmpVals =
                (Map<String, Object>) map.get("value");
            for (String key : tmpVals.keySet()) {
                values.put(key, tmpVals.get(key));
            }
        }

        AlertData alert = new AlertData(varname, prio, dateStr, values);
        if (verbose) {
            System.err.println(alert.toString());
        }

        if (!alerts.containsKey(varname)) {
            alerts.put(varname, new ArrayList<AlertData>());
        }

        Gson gson = new Gson();
        gson.toJson(obj);

        alerts.get(varname).add(alert);
    }

    /**
     * Set monitoring server host and port
     *
     * @param host - server host name
     * @param port - server port number
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void setAddress(String host, int port)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * If <tt>true</tt>, alerts will be printed to System.err
     *
     * @param val verbosity
     */
    public void setVerbose(boolean val)
    {
        verbose = val;
    }

    public String toString()
    {
        return "MockAlerter[" + alerts.size() +" alerts(" +
            alerts.keySet() + ")" + (closed ? ",closed" : "") + "]";
    }
}
