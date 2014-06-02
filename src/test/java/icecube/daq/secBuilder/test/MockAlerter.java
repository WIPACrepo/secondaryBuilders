package icecube.daq.secBuilder.test;

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
        throw new Error("Unimplemented");
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
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param values map of names to values
     */
    public void send(String varname, Alerter.Priority priority,
                     Map<String, Object> values)
        throws AlertException
    {
        send(varname, priority, Calendar.getInstance(), values);
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param dateTime date and time for message
     * @param values map of names to values
     */
    public void send(String varname, Alerter.Priority priority,
                     Calendar dateTime, Map<String, Object> values)
        throws AlertException
    {
        final String dateStr =
            String.format("%tF %tT.%tL000", dateTime, dateTime, dateTime);
        send(varname, priority, dateStr, values);
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param values map of names to values
     */
    public void send(String varname, Alerter.Priority priority,
                     IUTCTime daqTime, Map<String, Object> values)
        throws AlertException
    {
        send(varname, priority, daqTime.toDateString(), values);
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param date date and time for message
     * @param values map of variable names to values
     */
    private void send(String varname, Priority priority, String dateStr,
                      Map<String, Object> values)
        throws AlertException
    {
        AlertData alert = new AlertData(varname, priority, dateStr, values);
        if (verbose) {
            System.err.println(alert.toString());
        }

        if (!alerts.containsKey(varname)) {
            alerts.put(varname, new ArrayList<AlertData>());
        }

        alerts.get(varname).add(alert);
    }

    /**
     * Send an alert.
     *
     * @param priority priority level
     * @param condition I3Live condition
     * @param values map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(Alerter.Priority priority, String condition,
                          Map<String, Object> values)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send an alert.
     *
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param values map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(Alerter.Priority priority, String condition,
                          String notify, Map<String, Object> values)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send an alert.
     *
     * @param dateTime date and time for message
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param values map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(Calendar dateTime, Alerter.Priority priority,
                          String condition, String notify,
                          Map<String, Object> values)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send an alert.
     *
     * @param daqTime DAQ Time
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param values map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(IUTCTime daqTime, Alerter.Priority priority,
                          String condition, String notify,
                          Map<String, Object> values)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send a Java object (as a JSON string) to a 0MQ server.
     *
     * @param obj object to send
     */
    public void sendObject(Object obj)
        throws AlertException
    {
        throw new Error("Unimplemented");
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
}
