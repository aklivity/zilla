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
package io.aklivity.zilla.runtime.catalog.inline.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class InlineOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SUBJECTS_NAME = "subjects";
    private static final String VERSION_NAME = "version";
    private static final String SCHEMA_NAME = "schema";
    private static final String VERSION_DEFAULT = "latest";

    @Override
    public Kind kind()
    {
        return Kind.CATALOG;
    }

    @Override
    public String type()
    {
        return "inline";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        InlineOptionsConfig config = (InlineOptionsConfig) options;
        JsonObjectBuilder subjects = Json.createObjectBuilder();

        if (config.subjects != null && !config.subjects.isEmpty())
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (InlineSchemaConfig schema : config.subjects)
            {
                JsonObjectBuilder schemaJson = Json.createObjectBuilder();
                if (schema.schema != null)
                {
                    schemaJson.add(SCHEMA_NAME, schema.schema);
                }

                if (schema.version != null)
                {
                    schemaJson.add(VERSION_NAME, schema.version);
                }
                catalogs.add(schema.subject, schemaJson);
            }
            subjects.add(SUBJECTS_NAME, catalogs);
        }
        return subjects.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        InlineOptionsConfigBuilder<InlineOptionsConfig> options = InlineOptionsConfig.builder();
        if (object != null)
        {
            if (object.containsKey(SUBJECTS_NAME))
            {
                JsonObject subjectsJson = object.getJsonObject(SUBJECTS_NAME);
                for (String subject: subjectsJson.keySet())
                {
                    JsonObject schemaJson = subjectsJson.getJsonObject(subject);

                    String version = schemaJson.containsKey(VERSION_NAME)
                            ? schemaJson.getString(VERSION_NAME)
                            : VERSION_DEFAULT;

                    String schema = schemaJson.containsKey(SCHEMA_NAME)
                            ? schemaJson.getString(SCHEMA_NAME)
                            : null;

                    options.subjects(new InlineSchemaConfig(subject, version, schema));
                }
            }
        }
        return options.build();
    }
}
