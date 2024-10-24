/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class RisingwaveOptionConfigBuilder<T> extends ConfigBuilder<T, RisingwaveOptionConfigBuilder<T>>
{
    private final Function<RisingwaveOptionsConfig, T> mapper;

    private RisingwaveKafkaConfig kafka;
    private List<RisingwaveUdfConfig> udfs;

    RisingwaveOptionConfigBuilder(
        Function<RisingwaveOptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<RisingwaveOptionConfigBuilder<T>> thisType()
    {
        return (Class<RisingwaveOptionConfigBuilder<T>>) getClass();
    }

    public RisingwaveOptionConfigBuilder<T> kafka(
        RisingwaveKafkaConfig kafka)
    {
        this.kafka = kafka;
        return this;
    }

    public RisingwaveKafkaConfigBuilder<RisingwaveOptionConfigBuilder<T>> kafka()
    {
        return RisingwaveKafkaConfig.builder(this::kafka);
    }

    public RisingwaveOptionConfigBuilder<T> udf(
        RisingwaveUdfConfig udf)
    {
        if (udfs == null)
        {
            udfs = new ArrayList<>();
        }
        udfs.add(udf);
        return this;
    }

    public RisingwaveUdfConfigBuilder<RisingwaveOptionConfigBuilder<T>> udf()
    {
        return RisingwaveUdfConfig.builder(this::udf);
    }

    public T build()
    {
        return mapper.apply(new RisingwaveOptionsConfig(kafka, udfs));
    }
}
