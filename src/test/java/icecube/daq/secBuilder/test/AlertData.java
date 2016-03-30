package icecube.daq.secBuilder.test;

import icecube.daq.juggler.alert.Alerter;

import java.util.Map;

public class AlertData
{
    private String varname;
    private Alerter.Priority priority;
    private String dateStr;
    private Map<String, Object> values;

    public AlertData(String varname, Alerter.Priority priority, String dateStr,
                     Map<String, Object> values)
    {
        this.varname = varname;
        this.priority = priority;
        this.dateStr = dateStr;
        this.values = values;
    }

    public String getName() { return varname; }
    public Alerter.Priority getPriority() { return priority; }
    public String getDate() { return dateStr; }

    public Map<String, Double> getMap(String fieldName)
    {
        return (Map<String, Double>) values.get(fieldName);
    }

    public Map<String, Object> getValues() { return values; }

    public String toString()
    {
        return "AlertData[name=" + varname + ",prio=" + priority +
            ",date=" + dateStr + ",values=(" + values + ")]";
    }
}
