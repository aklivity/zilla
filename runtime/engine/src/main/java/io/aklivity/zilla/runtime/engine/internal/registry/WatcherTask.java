package io.aklivity.zilla.runtime.engine.internal.registry;

import java.io.Closeable;
import java.net.URL;
import java.util.concurrent.ForkJoinTask;
import java.util.function.BiConsumer;


public abstract class WatcherTask extends ForkJoinTask<Void> implements Closeable
{
    protected final BiConsumer<URL, String> changeListener;

    protected WatcherTask(
        BiConsumer<URL, String> changeListener)
    {
        this.changeListener = changeListener;
    }

    @Override
    protected boolean exec()
    {
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

    public abstract void onURLDiscovered(
        URL configURL);

}
