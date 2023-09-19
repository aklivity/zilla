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
package io.aklivity.zilla.runtime.binding.kafka.internal.validator.config;

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class AvroValidatorConfigAdapter implements ValidatorConfigAdapterSpi, JsonbAdapter<ValidatorConfig, JsonObject>
{
    private static final String AVRO = "avro";
    private static final String TYPE_NAME = "type";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT = "subject";

    private final SchemaAdapter schema = new SchemaAdapter();

    @Override
    public String type()
    {
        return AVRO;
    }

    @Override
    public JsonObject adaptToJson(
        ValidatorConfig config)
    {
        AvroValidatorConfig validatorConfig = (AvroValidatorConfig) config;
        JsonObjectBuilder validator = Json.createObjectBuilder();
        validator.add(TYPE_NAME, AVRO);
        if (validatorConfig.catalogs != null && !validatorConfig.catalogs.isEmpty())
        {
            for (KafkaCatalogConfig catalog : validatorConfig.catalogs)
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                for (SchemaConfig schemaItem: catalog.schemas)
                {
                    array.add(schema.adaptToJson(schemaItem));
                }
                validator.add(catalog.name, array);
            }
        }
        return validator.build();
    }

    @Override
    public ValidatorConfig adaptFromJson(
        JsonObject object)
    {
        ValidatorConfig result = null;
        if (object.containsKey(CATALOG_NAME))
        {
            JsonObject catalogsJson = object.getJsonObject(CATALOG_NAME);
            List<KafkaCatalogConfig> catalogs = new LinkedList<>();
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
                catalogs.add(new KafkaCatalogConfig(catalogName, schemas));
            }

            String subject = object.containsKey(SUBJECT)
                    ? object.getString(SUBJECT)
                    : null;

            result = new AvroValidatorConfig(catalogs, subject);
        }
        return result;
    }
}
