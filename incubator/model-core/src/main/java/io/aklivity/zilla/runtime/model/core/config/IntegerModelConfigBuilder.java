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

public class IntegerModelConfigBuilder<T> extends ConfigBuilder<T, IntegerModelConfigBuilder<T>>
{
    public static final int DEFAULT_MULTIPLE = 1;
    public static final String DEFAULT_FORMAT = "text";

    private final Function<IntegerModelConfig, T> mapper;

    private String format;
    private int max;
    private int min;
    private int multiple;
    private boolean exclusiveMax;
    private boolean exclusiveMin;

    IntegerModelConfigBuilder(
        Function<IntegerModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<IntegerModelConfigBuilder<T>> thisType()
    {
        return (Class<IntegerModelConfigBuilder<T>>) getClass();
    }

    public IntegerModelConfigBuilder<T> format(
        String format)
    {
        this.format = format;
        return this;
    }

    public IntegerModelConfigBuilder<T> max(
        int max)
    {
        this.max = max;
        return this;
    }

    public IntegerModelConfigBuilder<T> min(
        int min)
    {
        this.min = min;
        return this;
    }

    public IntegerModelConfigBuilder<T> multiple(
        int multiple)
    {
        this.multiple = multiple;
        return this;
    }

    public IntegerModelConfigBuilder<T> exclusiveMax(
        boolean exclusiveMax)
    {
        this.exclusiveMax = exclusiveMax;
        return this;
    }

    public IntegerModelConfigBuilder<T> exclusiveMin(
        boolean exclusiveMin)
    {
        this.exclusiveMin = exclusiveMin;
        return this;
    }

    @Override
    public T build()
    {
        String format = this.format != null ? this.format : DEFAULT_FORMAT;
        int max = this.max != 0 ? this.max : Integer.MAX_VALUE;
        int min = this.min != 0 ? this.min : Integer.MIN_VALUE;
        int multiple = this.multiple != 0 ? this.multiple : DEFAULT_MULTIPLE;
        return mapper.apply(new IntegerModelConfig(format, max, min, exclusiveMax, exclusiveMin, multiple));
    }
}
