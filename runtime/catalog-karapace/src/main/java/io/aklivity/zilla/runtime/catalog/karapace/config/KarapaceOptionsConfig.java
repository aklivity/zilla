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
package io.aklivity.zilla.runtime.catalog.karapace.config;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.catalog.schema.registry.config.AbstractSchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class KarapaceOptionsConfig extends AbstractSchemaRegistryOptionsConfig
{
    public static KarapaceOptionsConfigBuilder<KarapaceOptionsConfig> builder()
    {
        return new KarapaceOptionsConfigBuilder<>(KarapaceOptionsConfig.class::cast);
    }

    public static <T> KarapaceOptionsConfigBuilder<T> builder(
            Function<OptionsConfig, T> mapper)
    {
        return new KarapaceOptionsConfigBuilder<>(mapper);
    }

    KarapaceOptionsConfig(
        String url,
        String context,
        Duration maxAge,
        List<String> keys,
        List<String> trust,
        boolean trustcacerts,
        String authorization,
        String username,
        String password)
    {
        super(url, context, maxAge, keys, trust, trustcacerts, authorization, username, password);
    }
}
