/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.binding.kafka;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class KafkaWithConfigBuilder<T> extends ConfigBuilder<T, KafkaWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String defaultOffset;
    private String deltaType;
    private String ackMode;

    KafkaWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaWithConfigBuilder<T>> thisType()
    {
        return (Class<KafkaWithConfigBuilder<T>>) getClass();
    }

    public KafkaWithConfigBuilder<T> defaultOffset(
        String defaultOffset)
    {
        this.defaultOffset = defaultOffset;
        return this;
    }

    public KafkaWithConfigBuilder<T> deltaType(
        String deltaType)
    {
        this.deltaType = deltaType;
        return this;
    }

    public KafkaWithConfigBuilder<T> ackMode(
        String ackMode)
    {
        this.ackMode = ackMode;
        return this;
    }

    public T build()
    {
        return mapper.apply(new KafkaWithConfig(defaultOffset, deltaType, ackMode));
    }
}
