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
package io.aklivity.zilla.config.model.core;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class RangeConfigBuilder<T> extends ConfigBuilder<T, RangeConfigBuilder<T>>
{
    private final Function<RangeConfig, T> mapper;
    private String max;
    private String min;
    private boolean exclusiveMax;
    private boolean exclusiveMin;

    RangeConfigBuilder(
        Function<RangeConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<RangeConfigBuilder<T>> thisType()
    {
        return (Class<RangeConfigBuilder<T>>) getClass();
    }

    public RangeConfigBuilder<T> max(
        String max)
    {
        this.max = max;
        return this;
    }

    public RangeConfigBuilder<T> min(
        String min)
    {
        this.min = min;
        return this;
    }

    public RangeConfigBuilder<T> exclusiveMax(
        boolean exclusiveMax)
    {
        this.exclusiveMax = exclusiveMax;
        return this;
    }

    public RangeConfigBuilder<T> exclusiveMin(
        boolean exclusiveMin)
    {
        this.exclusiveMin = exclusiveMin;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new RangeConfig(max, min, exclusiveMax, exclusiveMin));
    }
}
