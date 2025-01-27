/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.catalog.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class TestCatalogOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String SUBJECT_NAME = "subject";
    private static final String SCHEMA_NAME = "schema";
    private static final String ID_NAME = "id";
    private static final String PREFIX_NAME = "prefix";
    private static final String URL_NAME = "url";

    private static final int ID_DEFAULT = 0;

    @Override
    public Kind kind()
    {
        return Kind.CATALOG;
    }

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TestCatalogOptionsConfig config = (TestCatalogOptionsConfig) options;
        JsonObjectBuilder catalog = Json.createObjectBuilder();

        String subject = config.subject;
        if (subject != null && !subject.isEmpty())
        {
            catalog.add(SUBJECT_NAME, subject);
        }

        int id = config.id;
        if (id != ID_DEFAULT)
        {
            catalog.add(ID_NAME, id);
        }

        String schema = config.schema;
        if (schema != null && !schema.isEmpty())
        {
            catalog.add(SCHEMA_NAME, schema);
        }

        String prefix = config.prefix;
        if (prefix != null && !prefix.isEmpty())
        {
            catalog.add(PREFIX_NAME, prefix);
        }

        return catalog.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestCatalogOptionsConfigBuilder<TestCatalogOptionsConfig> config = TestCatalogOptionsConfig.builder();

        if (object != null)
        {
            if (object.containsKey(SUBJECT_NAME))
            {
                config.subject(object.getString(SUBJECT_NAME));
            }

            config.id(object.containsKey(ID_NAME)
                    ? object.getInt(ID_NAME)
                    : ID_DEFAULT);

            if (object.containsKey(SCHEMA_NAME))
            {
                config.schema(object.getString(SCHEMA_NAME));
            }

            if (object.containsKey(PREFIX_NAME))
            {
                config.prefix(object.getString(PREFIX_NAME));
            }

            if (object.containsKey(URL_NAME))
            {
                config.url(object.getString(URL_NAME));
            }
        }
        return config.build();
    }
}
