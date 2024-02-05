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
package io.aklivity.zilla.runtime.engine.test.internal.catalog.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class TestCatalogOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    private static final String SUBJECT = "subject";
    private static final String SCHEMA = "schema";
    private static final String ID = "id";

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

        if (config.schema != null &&
            !config.schema.isEmpty())
        {
            catalog.add(SCHEMA, config.schema);
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
            if (object.containsKey(SUBJECT))
            {
                config.subject(object.getString(SUBJECT));
            }

            if (object.containsKey(SCHEMA))
            {
                config.schema(object.getString(SCHEMA));
            }

            config.id(object.containsKey(ID)
                ? object.getInt(ID)
                : 0);
        }
        return config.build();
    }
}
