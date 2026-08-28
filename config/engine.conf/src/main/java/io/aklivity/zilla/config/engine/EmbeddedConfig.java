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
package io.aklivity.zilla.config.engine;

import java.util.Map;
import java.util.function.Function;

public final class EmbeddedConfig extends NamedConfig
{
    EmbeddedConfig(
        String name,
        Map<String, Config> extensions)
    {
        super(name, extensions);
    }

    public static <T> EmbeddedConfigBuilder<T> builder(
        Function<EmbeddedConfig, T> mapper)
    {
        return new EmbeddedConfigBuilder<>(mapper);
    }

    public static EmbeddedConfigBuilder<EmbeddedConfig> builder()
    {
        return new EmbeddedConfigBuilder<>(EmbeddedConfig.class::cast);
    }
}
