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
package io.aklivity.zilla.runtime.validator.json.config;

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
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfigAdapterSpi;

public final class JsonValidatorConfigAdapter implements ValidatorConfigAdapterSpi, JsonbAdapter<ValidatorConfig, JsonValue>
{
    private static final String JSON = "json";
    private static final String TYPE_NAME = "type";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    @Override
    public String type()
    {
        return JSON;
    }

    @Override
    public JsonValue adaptToJson(
        ValidatorConfig config)
    {
        JsonValidatorConfig validatorConfig = (JsonValidatorConfig) config;
        JsonObjectBuilder validator = Json.createObjectBuilder();
        validator.add(TYPE_NAME, JSON);
        if (validatorConfig.cataloged != null && !validatorConfig.cataloged.isEmpty())
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (CatalogedConfig catalog : validatorConfig.cataloged)
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                for (SchemaConfig schemaItem: catalog.schemas)
                {
                    array.add(schema.adaptToJson(schemaItem));
                }
                catalogs.add(catalog.name, array);
            }
            validator.add(CATALOG_NAME, catalogs);
        }
        return validator.build();
    }

    @Override
    public ValidatorConfig adaptFromJson(
        JsonValue value)
    {
        JsonObject object = (JsonObject) value;
        ValidatorConfig result = null;
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

            String subject = object.containsKey(SUBJECT_NAME)
                    ? object.getString(SUBJECT_NAME)
                    : null;

            result = new JsonValidatorConfig(catalogs, subject);
        }
        return result;
    }
}
