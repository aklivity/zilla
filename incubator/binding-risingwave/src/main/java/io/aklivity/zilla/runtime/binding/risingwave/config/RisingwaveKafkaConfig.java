package io.aklivity.zilla.runtime.binding.risingwave.config;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public class RisingwaveKafkaConfig
{
    public final RisingwavePropertiesConfig properties;
    public final ModelConfig format;

    public RisingwaveKafkaConfig(
        RisingwavePropertiesConfig properties,
        ModelConfig format)
    {
        this.properties = properties;
        this.format = format;
    }
}
