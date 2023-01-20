package io.aklivity.zilla.runtime.engine.internal.registry;

import java.io.Closeable;
import java.util.concurrent.Callable;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;


public abstract class WatcherTask implements Closeable, Callable<Void>
{

    protected NamespaceConfig rootNamespace;

    public void setRootNamespace(
        NamespaceConfig rootNamespace)
    {
        this.rootNamespace = rootNamespace;
    }
}
