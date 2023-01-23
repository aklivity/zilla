package io.aklivity.zilla.runtime.engine.internal.registry;

import java.net.URL;
import java.util.concurrent.ForkJoinTask;


public abstract class WatcherTask extends ForkJoinTask<Void>
{
    private Thread thread;
    protected final URL configURL;
    protected final Runnable configChangeListener;

    protected WatcherTask(
        URL configURL,
        Runnable configChangeListener)
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
}
