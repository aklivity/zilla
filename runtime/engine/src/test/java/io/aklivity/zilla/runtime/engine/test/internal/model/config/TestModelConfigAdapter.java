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
package io.aklivity.zilla.runtime.engine.test.internal.model.config;

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfigAdapter;

public class TestModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String TEST = "test";
    private static final String LENGTH = "length";
    private static final String CAPABILITY = "capability";
    private static final String READ = "read";
    private static final String CATALOG_NAME = "catalog";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    @Override
    public String type()
    {
        return TEST;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        return Json.createValue(TEST);
    }

    @Override
    public TestModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonObject object = (JsonObject) value;

        int length = object.containsKey(LENGTH)
            ? object.getInt(LENGTH)
            : 0;

        boolean read = object.containsKey(CAPABILITY)
            ? object.getString(CAPABILITY).equals(READ)
            : false;

        List<CatalogedConfig> catalogs = new LinkedList<>();
        if (object.containsKey(CATALOG_NAME))
        {
            JsonObject catalogsJson = object.getJsonObject(CATALOG_NAME);
            for (String catalogName: catalogsJson.keySet())
            {
                JsonArray schemasJson = catalogsJson.getJsonArray(catalogName);
                List<SchemaConfig> schemas = new LinkedList<>();
                for (JsonValue item : schemasJson)
                {
                    JsonObject schemaJson = (JsonObject) item;
                    SchemaConfig schemaElement = schema.adaptFromJson(schemaJson);
                    schemas.add(schemaElement);
                }
                catalogs.add(new CatalogedConfig(catalogName, schemas));
            }
        }

        return new TestModelConfig(length, catalogs, read);
    }
}
