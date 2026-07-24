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
package io.aklivity.zilla.config.binding.proxy;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.OptionsConfig;

public final class ProxyOptionsConfig extends OptionsConfig
{
    public static ProxyOptionsConfigBuilder<ProxyOptionsConfig> builder()
    {
        return new ProxyOptionsConfigBuilder<>(ProxyOptionsConfig.class::cast);
    }

    public static <T> ProxyOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new ProxyOptionsConfigBuilder<>(mapper);
    }

    ProxyOptionsConfig()
    {
    }
}
