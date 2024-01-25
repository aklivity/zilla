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
package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.CatalogRefConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfigAdapter;

public class CatalogRefAdapter implements JsonbAdapter<CatalogRefConfig, JsonObject>
{
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT = "subject";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    public CatalogRefAdapter()
    {
    }

    @Override
    public JsonObject adaptToJson(
        CatalogRefConfig config)
    {
        JsonObjectBuilder catalogBuilder = Json.createObjectBuilder();
        if (config.catalogs != null && !config.catalogs.isEmpty())
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (CatalogedConfig catalog : config.catalogs)
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                for (SchemaConfig schemaItem: catalog.schemas)
                {
                    array.add(schema.adaptToJson(schemaItem));
                }
                catalogs.add(catalog.name, array);
            }
            catalogBuilder.add(CATALOG_NAME, catalogs);
        }
        return catalogBuilder.build();
    }


    @Override
    public CatalogRefConfig adaptFromJson(
        JsonObject object)
    {
        CatalogRefConfig result = null;
        if (object.containsKey(CATALOG_NAME))
        {
            JsonObject catalogsJson = object.getJsonObject(CATALOG_NAME);
            List<CatalogedConfig> catalogs = new LinkedList<>();
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

            String subject = object.containsKey(SUBJECT)
                ? object.getString(SUBJECT)
                : null;

            result = new CatalogRefConfig(catalogs, subject);
        }
        return result;
    }
}
