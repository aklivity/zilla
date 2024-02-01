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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfigAdapter;

public class CatalogedAdapter implements JsonbAdapter<Collection<CatalogedConfig>, JsonObject>
{
    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    public CatalogedAdapter()
    {
    }

    @Override
    public JsonObject adaptToJson(
        Collection<CatalogedConfig> catalogs)
    {
        JsonObjectBuilder catalogsBuilder = Json.createObjectBuilder();
        for (CatalogedConfig catalog : catalogs)
        {
            JsonArrayBuilder array = Json.createArrayBuilder();
            for (SchemaConfig schemaItem: catalog.schemas)
            {
                array.add(schema.adaptToJson(schemaItem));
            }
            catalogsBuilder.add(catalog.name, array);
        }

        return catalogsBuilder.build();
    }

    @Override
    public Collection<CatalogedConfig> adaptFromJson(
        JsonObject catalogsJson)
    {
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

        return catalogs;
    }
}
