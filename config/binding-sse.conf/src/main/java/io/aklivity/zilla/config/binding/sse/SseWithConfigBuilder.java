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
package io.aklivity.zilla.config.binding.sse;

import static io.aklivity.zilla.config.engine.WithConfig.NO_COMPOSITE_ID;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

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
