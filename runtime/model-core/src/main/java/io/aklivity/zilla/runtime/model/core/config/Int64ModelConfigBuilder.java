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
package io.aklivity.zilla.runtime.model.core.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class Int64ModelConfigBuilder<T> extends ConfigBuilder<T, Int64ModelConfigBuilder<T>>
{
    public static final long DEFAULT_MULTIPLE = 1L;
    public static final String DEFAULT_FORMAT = "text";

    private final Function<Int64ModelConfig, T> mapper;

    private String format;
    private Long max;
    private Long min;
    private Long multiple;
    private Boolean exclusiveMax;
    private Boolean exclusiveMin;

    Int64ModelConfigBuilder(
        Function<Int64ModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<Int64ModelConfigBuilder<T>> thisType()
    {
        return (Class<Int64ModelConfigBuilder<T>>) getClass();
    }

    public Int64ModelConfigBuilder<T> format(
        String format)
    {
        this.format = format;
        return this;
    }

    public Int64ModelConfigBuilder<T> max(
        long max)
    {
        this.max = max;
        return this;
    }

    public Int64ModelConfigBuilder<T> min(
        long min)
    {
        this.min = min;
        return this;
    }

    public Int64ModelConfigBuilder<T> multiple(
        long multiple)
    {
        this.multiple = multiple;
        return this;
    }

    public Int64ModelConfigBuilder<T> exclusiveMax(
        boolean exclusiveMax)
    {
        this.exclusiveMax = exclusiveMax;
        return this;
    }

    public Int64ModelConfigBuilder<T> exclusiveMin(
        boolean exclusiveMin)
    {
        this.exclusiveMin = exclusiveMin;
        return this;
    }

    @Override
    public T build()
    {
        String format = this.format != null ? this.format : DEFAULT_FORMAT;
        long max = this.max != null ? this.max : Long.MAX_VALUE;
        long min = this.min != null ? this.min : Long.MIN_VALUE;
        long multiple = this.multiple != null ? this.multiple : DEFAULT_MULTIPLE;
        boolean exclusiveMax = this.exclusiveMax != null ? this.exclusiveMax : false;
        boolean exclusiveMin = this.exclusiveMin != null ? this.exclusiveMin : false;
        return mapper.apply(new Int64ModelConfig(format, max, min, exclusiveMax, exclusiveMin, multiple));
    }
}
