package io.aklivity.zilla.runtime.engine.internal.registry;

import java.io.Closeable;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;


public abstract class WatcherTask implements Callable<Void>, Closeable
{
    protected final BiConsumer<URL, String> changeListener;

    protected WatcherTask(
        BiConsumer<URL, String> changeListener)
    {
        this.changeListener = changeListener;
    }

    public abstract void onURLDiscovered(
        URL configURL);

}
