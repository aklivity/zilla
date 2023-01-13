package io.aklivity.zilla.runtime.engine.internal.registry;

import java.util.concurrent.Callable;

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;


public interface WatcherTask extends Callable<Void>
{
    void setRootNamespace(NamespaceConfig rootNamespace);
}
