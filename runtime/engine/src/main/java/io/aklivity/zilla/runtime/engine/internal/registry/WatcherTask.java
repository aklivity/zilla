package io.aklivity.zilla.runtime.engine.internal.registry;

import java.util.concurrent.ForkJoinTask;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;


public abstract class WatcherTask extends ForkJoinTask<Void>
{

    protected NamespaceConfig rootNamespace;
    private Thread thread;

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
    public void setRootNamespace(
        NamespaceConfig rootNamespace)
    {
        this.rootNamespace = rootNamespace;
    }
}
