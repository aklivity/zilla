/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.risingwave.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public class RisingwaveKafkaConfigBuilder<T> extends ConfigBuilder<T, RisingwaveKafkaConfigBuilder<T>>
{
    private final Function<RisingwaveKafkaConfig, T> mapper;

    private RisingwaveKafkaPropertiesConfig properties;
    private ModelConfig format;

    RisingwaveKafkaConfigBuilder(
        Function<RisingwaveKafkaConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<RisingwaveKafkaConfigBuilder<T>> thisType()
    {
        return (Class<RisingwaveKafkaConfigBuilder<T>>) getClass();
    }

    public RisingwaveKafkaConfigBuilder<T> properties(
        RisingwaveKafkaPropertiesConfig properties)
    {
        this.properties = properties;
        return this;
    }

    public RisingwaveKafkaConfigBuilder<T> format(
        ModelConfig format)
    {
        this.format = format;
        return this;
    }

    public T build()
    {
        return mapper.apply(new RisingwaveKafkaConfig(properties, format));
    }
}
