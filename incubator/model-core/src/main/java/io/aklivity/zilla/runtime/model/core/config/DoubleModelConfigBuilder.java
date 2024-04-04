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

public class DoubleModelConfigBuilder<T> extends ConfigBuilder<T, DoubleModelConfigBuilder<T>>
{
    public static final String DEFAULT_FORMAT = "text";

    private final Function<DoubleModelConfig, T> mapper;

    private String format;
    private Double max;
    private Double min;
    private Double multiple;
    private Boolean exclusiveMax;
    private Boolean exclusiveMin;

    DoubleModelConfigBuilder(
        Function<DoubleModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<DoubleModelConfigBuilder<T>> thisType()
    {
        return (Class<DoubleModelConfigBuilder<T>>) getClass();
    }

    public DoubleModelConfigBuilder<T> format(
        String format)
    {
        this.format = format;
        return this;
    }

    public DoubleModelConfigBuilder<T> max(
        double max)
    {
        this.max = max;
        return this;
    }

    public DoubleModelConfigBuilder<T> min(
        double min)
    {
        this.min = min;
        return this;
    }

    public DoubleModelConfigBuilder<T> multiple(
        double multiple)
    {
        this.multiple = multiple;
        return this;
    }

    public DoubleModelConfigBuilder<T> exclusiveMax(
        boolean exclusiveMax)
    {
        this.exclusiveMax = exclusiveMax;
        return this;
    }

    public DoubleModelConfigBuilder<T> exclusiveMin(
        boolean exclusiveMin)
    {
        this.exclusiveMin = exclusiveMin;
        return this;
    }

    @Override
    public T build()
    {
        String format = this.format != null ? this.format : DEFAULT_FORMAT;
        double max = this.max != null ? this.max : Double.POSITIVE_INFINITY;
        double min = this.min != null ? this.min : Double.NEGATIVE_INFINITY;
        boolean exclusiveMax = this.exclusiveMax != null ? this.exclusiveMax : false;
        boolean exclusiveMin = this.exclusiveMin != null ? this.exclusiveMin : false;
        return mapper.apply(new DoubleModelConfig(format, max, min, exclusiveMax, exclusiveMin, multiple));
    }
}
