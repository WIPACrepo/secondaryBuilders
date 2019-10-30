package icecube.daq.secBuilder.test;

import icecube.daq.common.MockAppender;

import junit.framework.TestCase;

import org.apache.log4j.BasicConfigurator;

public class LoggingCase
    extends TestCase
{
    private MockAppender appender = new MockAppender();

    /**
     * Constructs an instance of this test.
     *
     * @param name the name of the test.
     */
    public LoggingCase(String name)
    {
        super(name);
    }

    public void assertLogMessage(String message)
    {
        appender.assertLogMessage(message);
    }

    public void assertNoLogMessages()
    {
        appender.assertNoLogMessages();
    }

    public void assertNoLogMessages(String description)
    {
        appender.assertNoLogMessages(description);
    }

    public void clearMessages()
    {
        appender.clear();
    }

    public MockAppender getAppender()
    {
        return appender;
    }

    public Object getMessage(int idx)
    {
        return appender.getMessage(idx);
    }

    public int getNumberOfMessages()
    {
        return appender.getNumberOfMessages();
    }

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    public void setVerbose(boolean val)
    {
        appender.setVerbose(val);
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        assertNoLogMessages();

        super.tearDown();
    }
}
