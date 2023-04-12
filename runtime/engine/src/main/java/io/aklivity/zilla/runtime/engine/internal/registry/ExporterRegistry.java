package io.aklivity.zilla.runtime.engine.internal.registry;

import java.util.function.IntConsumer;

import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;

public class ExporterRegistry
{
    private final int id;
    private final ExporterHandler handler;
    private final IntConsumer attached;
    private final IntConsumer detached;

    public ExporterRegistry(
        int id,
        ExporterHandler handler,
        IntConsumer attached,
        IntConsumer detached)
    {
        this.id = id;
        this.handler = handler;
        this.attached = attached;
        this.detached = detached;
    }

    public void attach()
    {
        attached.accept(id);
    }

    public void detach()
    {
        detached.accept(id);
    }

    public ExporterHandler handler()
    {
        return handler;
    }
}
