package io.aklivity.zilla.runtime.binding.risingwave.config;

public class RisingwavePropertiesConfig
{
    public final String bootstrapServer;

    public RisingwavePropertiesConfig(
        String bootstrapServer)
    {
        this.bootstrapServer = bootstrapServer;
    }
}
