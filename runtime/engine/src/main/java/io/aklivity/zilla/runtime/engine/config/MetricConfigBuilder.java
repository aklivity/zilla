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
package io.aklivity.zilla.runtime.engine.config;

import java.util.function.Function;

public final class MetricConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<MetricConfig, T> mapper;

    private String group;
    private String name;

    MetricConfigBuilder(
        Function<MetricConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public MetricConfigBuilder<T> group(
        String group)
    {
        this.group = group;
        return this;
    }

    public MetricConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new MetricConfig(group, name));
    }
}
