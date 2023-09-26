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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class SchemaRegistryOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String URL = "url";
    private static final String CONTEXT = "context";

    @Override
    public Kind kind()
    {
        return Kind.CATALOG;
    }

    @Override
    public String type()
    {
        return "schema-registry";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        SchemaRegistryOptionsConfig config = (SchemaRegistryOptionsConfig) options;
        JsonObjectBuilder catalog = Json.createObjectBuilder();

        if (config.url != null &&
            !config.url.isEmpty())
        {
            catalog.add(URL, config.url);
        }

        if (config.context != null &&
            !config.context.isEmpty())
        {
            catalog.add(CONTEXT, config.context);
        }

        return catalog.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        SchemaRegistryOptionsConfigBuilder<SchemaRegistryOptionsConfig> options = SchemaRegistryOptionsConfig.builder();

        if (object != null)
        {
            if (object.containsKey(URL))
            {
                options.url(object.getString(URL));
            }

            if (object.containsKey(CONTEXT))
            {
                options.context(object.getString(CONTEXT));
            }
        }

        return options.build();
    }
}
