/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.engine.internal;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.SchemaConfigAdapter;

public class CatalogedAdapter implements JsonbAdapter<List<CatalogedConfig>, JsonObject>
{
    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    public CatalogedAdapter()
    {
    }

    @Override
    public JsonObject adaptToJson(
        List<CatalogedConfig> catalogs)
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
    public List<CatalogedConfig> adaptFromJson(
        JsonObject catalogsJson)
    {
        List<CatalogedConfig> catalogs = new ArrayList<>();
        for (String catalogName: catalogsJson.keySet())
        {
            JsonArray schemasJson = catalogsJson.getJsonArray(catalogName);
            List<SchemaConfig> schemas = new ArrayList<>();
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
