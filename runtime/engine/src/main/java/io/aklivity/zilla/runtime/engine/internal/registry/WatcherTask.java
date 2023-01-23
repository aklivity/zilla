package io.aklivity.zilla.runtime.engine.internal.registry;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Consumer;


public abstract class WatcherTask extends ForkJoinTask<Void>
{
    protected static final String CONFIG_TEXT_DEFAULT = "{\n  \"name\": \"default\"\n}\n";
    protected final CountDownLatch initConfigLatch = new CountDownLatch(1);
    private Thread thread;
    protected final URL configURL;
    protected final Consumer<String> configChangeListener;

    protected WatcherTask(
        URL configURL,
        Consumer<String> configChangeListener)
    {
        this.configURL = configURL;
        this.configChangeListener = configChangeListener;
    }

    @Override
    protected boolean exec()
    {
        this.thread = Thread.currentThread();
        return run();
    }

    @Override
    public Void getRawResult()
    {
        return null;
    }

    @Override
    protected void setRawResult(Void value)
    {
    }

    protected abstract boolean run();

    public void interrupt()
    {
        if (thread != null)
        {
            thread.interrupt();
        }
    }

    public void awaitInitConfig() throws InterruptedException
    {
        initConfigLatch.await();
    }
}
