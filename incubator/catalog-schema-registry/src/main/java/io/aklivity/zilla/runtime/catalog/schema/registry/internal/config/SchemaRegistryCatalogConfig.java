/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class SchemaRegistryCatalogConfig extends OptionsConfig
{
    public final String url;
    public final String context;

    public static SchemaRegistryCatalogConfigBuilder<SchemaRegistryCatalogConfig> builder()
    {
        return new SchemaRegistryCatalogConfigBuilder<>(SchemaRegistryCatalogConfig.class::cast);
    }

    public static <T> SchemaRegistryCatalogConfigBuilder<T> builder(
            Function<OptionsConfig, T> mapper)
    {
        return new SchemaRegistryCatalogConfigBuilder<>(mapper);
    }

    public SchemaRegistryCatalogConfig(
        String url,
        String context)
    {
        this.url = url;
        this.context = context;
    }
}
