/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.sse.config;

import static io.aklivity.zilla.runtime.engine.config.WithConfig.NO_COMPOSITE_ID;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class SseWithConfigBuilder<T> extends ConfigBuilder<T, SseWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private long compositeId = NO_COMPOSITE_ID;

    SseWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SseWithConfigBuilder<T>> thisType()
    {
        return (Class<SseWithConfigBuilder<T>>) getClass();
    }

    public SseWithConfigBuilder<T> compositeId(
        long compositeId)
    {
        this.compositeId = compositeId;
        return this;
    }

    public T build()
    {
        return mapper.apply(new SseWithConfig(compositeId));
    }
}
