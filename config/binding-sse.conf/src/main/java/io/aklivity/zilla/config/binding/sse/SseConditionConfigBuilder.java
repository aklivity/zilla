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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public class SseConditionConfigBuilder<T> extends ConfigBuilder<T, SseConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    public String path;

    SseConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SseConditionConfigBuilder<T>> thisType()
    {
        return (Class<SseConditionConfigBuilder<T>>) getClass();
    }


    public SseConditionConfigBuilder<T> path(
        String path)
    {
        this.path = path;
        return this;
    }

    public T build()
    {
        return mapper.apply(new SseConditionConfig(path));
    }
}
