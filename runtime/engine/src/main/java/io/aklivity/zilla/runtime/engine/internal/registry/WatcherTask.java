package io.aklivity.zilla.runtime.engine.internal.registry;

import java.net.URL;
import java.util.concurrent.ForkJoinTask;
import java.util.function.BiConsumer;


public abstract class WatcherTask extends ForkJoinTask<Void>
{
    private Thread thread;
    protected final BiConsumer<URL, String> configChangeListener;

    protected WatcherTask(
        BiConsumer<URL, String> configChangeListener)
    {
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
    protected void setRawResult(
        Void value)
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

    public void onURLDiscovered(
        URL configURL)
    {
        doInitialConfiguration(configURL);
    }

    protected abstract void doInitialConfiguration(
        URL configURL);
}
