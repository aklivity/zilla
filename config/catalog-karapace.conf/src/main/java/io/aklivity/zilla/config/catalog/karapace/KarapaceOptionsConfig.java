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
package io.aklivity.zilla.config.catalog.karapace;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.aklivity.zilla.config.catalog.schema.registry.AbstractSchemaRegistryOptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class KarapaceOptionsConfig extends AbstractSchemaRegistryOptionsConfig
{
    public static final String TYPE = "karapace-schema-registry";
    public static final Set<String> TYPE_ALIASES = Set.of("karapace");

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
        String authorization)
    {
        super(url, context, maxAge, keys, trust, trustcacerts, authorization);
    }
}
