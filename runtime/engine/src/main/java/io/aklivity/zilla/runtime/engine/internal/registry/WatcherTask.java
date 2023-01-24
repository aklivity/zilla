package io.aklivity.zilla.runtime.engine.internal.registry;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinTask;
import java.util.function.BiConsumer;


public abstract class WatcherTask extends ForkJoinTask<Void>
{
    protected static final String CONFIG_TEXT_DEFAULT = "{\n  \"name\": \"default\"\n}\n";
    protected final CountDownLatch initConfigLatch = new CountDownLatch(1);
    private Thread thread;
    protected final BiConsumer<URL, String> configChangeListener;
    protected final Map<Path, URL> configURLs;

    protected WatcherTask(
        BiConsumer<URL, String> configChangeListener)
    {
        this.configChangeListener = configChangeListener;
        this.configURLs = new HashMap<>();
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

    public void onURLDiscovered(URL configURL)
    {
        doInitialConfiguration(configURL);
    }

    protected abstract void doInitialConfiguration(URL configURL);
}
