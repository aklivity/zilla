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

import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public class Int32ModelConfig extends ModelConfig
{
    public static final String INT_32 = "int32";

    public final String format;
    public final int max;
    public final int min;
    public final int multiple;
    public final boolean exclusiveMax;
    public final boolean exclusiveMin;

    public Int32ModelConfig(
        String format,
        int max,
        int min,
        boolean exclusiveMax,
        boolean exclusiveMin,
        int multiple)
    {
        super(INT_32);
        this.format = format;
        this.max = max;
        this.min = min;
        this.exclusiveMax = exclusiveMax;
        this.exclusiveMin = exclusiveMin;
        this.multiple = multiple;
    }

    public static <T> Int32ModelConfigBuilder<T> builder(
        Function<ModelConfig, T> mapper)
    {
        return new Int32ModelConfigBuilder<>(mapper::apply);
    }

    public static Int32ModelConfigBuilder<Int32ModelConfig> builder()
    {
        return new Int32ModelConfigBuilder<>(Int32ModelConfig.class::cast);
    }
}
