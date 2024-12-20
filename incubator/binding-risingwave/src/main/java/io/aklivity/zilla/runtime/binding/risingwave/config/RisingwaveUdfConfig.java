/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.config;

import java.util.function.Function;

public class RisingwaveUdfConfig
{
    public final String server;
    public final String language;

    public static RisingwaveUdfConfigBuilder<RisingwaveUdfConfig> builder()
    {
        return new RisingwaveUdfConfigBuilder<>(RisingwaveUdfConfig.class::cast);
    }

    public static <T> RisingwaveUdfConfigBuilder<T> builder(
        Function<RisingwaveUdfConfig, T> mapper)
    {
        return new RisingwaveUdfConfigBuilder<>(mapper);
    }

    RisingwaveUdfConfig(
        String server,
        String language)
    {
        this.server = server;
        this.language = language;
    }
}
