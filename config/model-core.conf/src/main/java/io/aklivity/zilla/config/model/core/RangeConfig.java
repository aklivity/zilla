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

public class RangeConfig
{
    public final String max;
    public final String min;
    public final boolean exclusiveMax;
    public final boolean exclusiveMin;

    public static RangeConfigBuilder<RangeConfig> builder()
    {
        return new RangeConfigBuilder<>(RangeConfig.class::cast);
    }

    public static <T> RangeConfigBuilder<T> builder(
        Function<RangeConfig, T> mapper)
    {
        return new RangeConfigBuilder<>(mapper);
    }

    RangeConfig(
        String max,
        String min,
        boolean exclusiveMax,
        boolean exclusiveMin)
    {
        this.max = max;
        this.min = min;
        this.exclusiveMax = exclusiveMax;
        this.exclusiveMin = exclusiveMin;
    }
}
