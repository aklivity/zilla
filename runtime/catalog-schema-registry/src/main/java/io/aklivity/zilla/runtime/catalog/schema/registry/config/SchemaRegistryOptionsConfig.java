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
package io.aklivity.zilla.runtime.catalog.schema.registry.config;

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class SchemaRegistryOptionsConfig extends AbstractSchemaRegistryOptionsConfig
{
    public static SchemaRegistryOptionsConfigBuilder<SchemaRegistryOptionsConfig> builder()
    {
        return new SchemaRegistryOptionsConfigBuilder<>(SchemaRegistryOptionsConfig.class::cast);
    }

    public static <T> SchemaRegistryOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new SchemaRegistryOptionsConfigBuilder<>(mapper);
    }

    SchemaRegistryOptionsConfig(
        String url,
        String context,
        Duration maxAge)
    {
        super(url, context, maxAge);
    }
}
