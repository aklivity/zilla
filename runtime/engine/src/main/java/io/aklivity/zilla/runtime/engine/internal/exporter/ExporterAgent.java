package io.aklivity.zilla.runtime.engine.internal.exporter;

import org.agrona.concurrent.Agent;

import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;

public class ExporterAgent implements Agent
{
    private final String agentName;
    private final ExporterHandler handler;

    public ExporterAgent(
        long exporterId,
        ExporterHandler handler)
    {
        this.agentName = String.format("engine/exporter#%d", exporterId);
        this.handler = handler;
    }

    @Override
    public void onStart()
    {
        handler.start();
    }

    @Override
    public int doWork()
    {
        return handler.export();
    }

    @Override
    public void onClose()
    {
        handler.stop();
    }

    @Override
    public String roleName()
    {
        return agentName;
    }
}
