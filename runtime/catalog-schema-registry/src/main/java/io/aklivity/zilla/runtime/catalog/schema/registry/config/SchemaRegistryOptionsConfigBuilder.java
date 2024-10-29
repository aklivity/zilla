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

public final class SchemaRegistryOptionsConfigBuilder<T>
    extends AbstractSchemaRegistryOptionsConfigBuilder<T, SchemaRegistryOptionsConfigBuilder<T>>
{
    SchemaRegistryOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SchemaRegistryOptionsConfigBuilder<T>> thisType()
    {
        return (Class<SchemaRegistryOptionsConfigBuilder<T>>) getClass();
    }

    @Override
    protected SchemaRegistryOptionsConfig newOptionsConfig(
        String url,
        String context,
        Duration maxAge)
    {
        return new SchemaRegistryOptionsConfig(url, context, maxAge);
    }

}
