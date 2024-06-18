/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

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
