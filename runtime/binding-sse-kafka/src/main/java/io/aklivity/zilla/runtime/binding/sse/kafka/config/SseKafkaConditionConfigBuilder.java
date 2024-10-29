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
package io.aklivity.zilla.runtime.binding.sse.kafka.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class SseKafkaConditionConfigBuilder<T> extends ConfigBuilder<T, SseKafkaConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;
    private String path;

    SseKafkaConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public SseKafkaConditionConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }


    @Override
    @SuppressWarnings("unchecked")
    protected Class<SseKafkaConditionConfigBuilder<T>> thisType()
    {
        return (Class<SseKafkaConditionConfigBuilder<T>>) getClass();
    }


    @Override
    public T build()
    {
        return mapper.apply(new SseKafkaConditionConfig(path));
    }
}
