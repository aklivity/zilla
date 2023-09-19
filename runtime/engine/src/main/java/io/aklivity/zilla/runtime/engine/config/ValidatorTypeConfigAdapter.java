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
package io.aklivity.zilla.runtime.engine.config;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

public class ValidatorTypeConfigAdapter implements JsonbAdapter<ValidatorTypeConfig, JsonValue>
{
    private static final String TYPE_NAME = "type";
    private static final String CATALOG_NAME = "catalog";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    @Override
    public JsonValue adaptToJson(
        ValidatorTypeConfig validatorType)
    {
        JsonValue value;
        if (validatorType.catalog == null)
        {
            value = Json.createValue(validatorType.type.toString().toLowerCase());
        }
        else
        {
            JsonObjectBuilder object = Json.createObjectBuilder();
            object.add(TYPE_NAME, validatorType.type.toString().toLowerCase());
            JsonObjectBuilder catalogObject = Json.createObjectBuilder();
            for (String catalogName : validatorType.catalog.keySet())
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                for (SchemaConfig schemaItem: validatorType.catalog.get(catalogName))
                {
                    array.add(schema.adaptToJson(schemaItem));
                }
                catalogObject.add(catalogName, array);
            }
            object.add(CATALOG_NAME, catalogObject);
            value = object.build();
        }
        return value;
    }

    @Override
    public ValidatorTypeConfig adaptFromJson(
        JsonValue value)
    {
        ValidatorTypeConfig.Type type = null;
        LinkedHashMap<String, List<SchemaConfig>> catalog = null;
        if (value.getValueType() == JsonValue.ValueType.STRING)
        {
            type = ValidatorTypeConfig.Type.valueOf(((JsonString)value).getString().toUpperCase());
        }
        else if (value.getValueType() == JsonValue.ValueType.OBJECT)
        {
            JsonObject jsonObject = (JsonObject) value;
            if (jsonObject.containsKey(TYPE_NAME))
            {
                type = ValidatorTypeConfig.Type.valueOf(jsonObject.getString(TYPE_NAME).toUpperCase());
            }
            if (jsonObject.containsKey(CATALOG_NAME))
            {
                JsonObject catalogJson = jsonObject.getJsonObject(CATALOG_NAME);
                catalog = new LinkedHashMap<>();
                for (String catalogName: catalogJson.keySet())
                {
                    JsonArray schemasJson = catalogJson.getJsonArray(catalogName);
                    List<SchemaConfig> schemas = new LinkedList<>();
                    for (JsonValue item : schemasJson)
                    {
                        JsonObject schemaJson = (JsonObject) item;
                        SchemaConfig schemaElement = schema.adaptFromJson(schemaJson);
                        schemas.add(schemaElement);
                    }
                    catalog.put(catalogName, schemas);
                }
            }
        }
        return new ValidatorTypeConfig(type, catalog);
    }
}
