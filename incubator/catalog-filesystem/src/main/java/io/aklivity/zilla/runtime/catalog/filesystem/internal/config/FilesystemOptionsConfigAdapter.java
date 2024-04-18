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
package io.aklivity.zilla.runtime.catalog.filesystem.internal.config;

import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class FilesystemOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SUBJECTS_NAME = "subjects";
    private static final String URL_NAME = "url";
    private Function<String, String> readURL;

    @Override
    public Kind kind()
    {
        return Kind.CATALOG;
    }

    @Override
    public String type()
    {
        return "filesystem";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        FilesystemOptionsConfig config = (FilesystemOptionsConfig) options;
        JsonObjectBuilder subjects = Json.createObjectBuilder();

        if (config.subjects != null && !config.subjects.isEmpty())
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (FilesystemSchemaConfig schema : config.subjects)
            {
                JsonObjectBuilder schemaJson = Json.createObjectBuilder();

                schemaJson.add(URL_NAME, schema.url);

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
        FilesystemOptionsConfigBuilder<FilesystemOptionsConfig> options = FilesystemOptionsConfig.builder();
        if (object != null)
        {
            if (object.containsKey(SUBJECTS_NAME))
            {
                JsonObject subjectsJson = object.getJsonObject(SUBJECTS_NAME);
                for (String subject: subjectsJson.keySet())
                {
                    JsonObject schemaJson = subjectsJson.getJsonObject(subject);

                    String url = schemaJson.getString(URL_NAME);

                    options.subjects(new FilesystemSchemaConfig(subject, url));
                }
            }
        }
        options.readURL(readURL);

        return options.build();
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.readURL = context::readURL;
    }
}