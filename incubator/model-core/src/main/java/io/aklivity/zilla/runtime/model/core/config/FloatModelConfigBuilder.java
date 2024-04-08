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

public class FloatModelConfigBuilder<T> extends ConfigBuilder<T, FloatModelConfigBuilder<T>>
{
    public static final String DEFAULT_FORMAT = "text";

    private final Function<FloatModelConfig, T> mapper;

    private String format;
    private Float max;
    private Float min;
    private Float multiple;
    private Boolean exclusiveMax;
    private Boolean exclusiveMin;

    FloatModelConfigBuilder(
        Function<FloatModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<FloatModelConfigBuilder<T>> thisType()
    {
        return (Class<FloatModelConfigBuilder<T>>) getClass();
    }

    public FloatModelConfigBuilder<T> format(
        String format)
    {
        this.format = format;
        return this;
    }

    public FloatModelConfigBuilder<T> max(
        float max)
    {
        this.max = max;
        return this;
    }

    public FloatModelConfigBuilder<T> min(
        float min)
    {
        this.min = min;
        return this;
    }

    public FloatModelConfigBuilder<T> multiple(
        float multiple)
    {
        this.multiple = multiple;
        return this;
    }

    public FloatModelConfigBuilder<T> exclusiveMax(
        boolean exclusiveMax)
    {
        this.exclusiveMax = exclusiveMax;
        return this;
    }

    public FloatModelConfigBuilder<T> exclusiveMin(
        boolean exclusiveMin)
    {
        this.exclusiveMin = exclusiveMin;
        return this;
    }

    @Override
    public T build()
    {
        String format = this.format != null ? this.format : DEFAULT_FORMAT;
        float max = this.max != null ? this.max : Float.POSITIVE_INFINITY;
        float min = this.min != null ? this.min : Float.NEGATIVE_INFINITY;
        boolean exclusiveMax = this.exclusiveMax != null ? this.exclusiveMax : false;
        boolean exclusiveMin = this.exclusiveMin != null ? this.exclusiveMin : false;
        return mapper.apply(new FloatModelConfig(format, max, min, exclusiveMax, exclusiveMin, multiple));
    }
}
