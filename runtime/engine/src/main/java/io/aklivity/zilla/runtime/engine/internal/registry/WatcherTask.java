package io.aklivity.zilla.runtime.engine.internal.registry;

import java.io.Closeable;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;


public abstract class WatcherTask implements Callable<Void>, Closeable
{
    protected final BiFunction<URL, String, NamespaceConfig> changeListener;

    protected WatcherTask(
        BiFunction<URL, String, NamespaceConfig> changeListener)
    {
        this.changeListener = changeListener;
    }
    public abstract String readURL(
        URL configURL);
    public abstract CompletableFuture<NamespaceConfig> watch(
        URL configURL);

}
