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
package io.aklivity.zilla.runtime.model.json.internal.config;

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapterSpi;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.SchemaConfigAdapter;
import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateConfigAdapter;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfigBuilder;

public final class JsonModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String JSON = "json";
    private static final String MODEL_NAME = "model";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VALIDATE_NAME = "validate";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();
    private final ValidateConfigAdapter validate = new ValidateConfigAdapter();

    @Override
    public String type()
    {
        return JSON;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        JsonModelConfig model = (JsonModelConfig) config;
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(MODEL_NAME, JSON);
        if (model.cataloged != null && !model.cataloged.isEmpty())
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (CatalogedConfig catalog : model.cataloged)
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                for (SchemaConfig schemaItem: catalog.schemas)
                {
                    array.add(schema.adaptToJson(schemaItem));
                }
                catalogs.add(catalog.name, array);
            }
            builder.add(CATALOG_NAME, catalogs);
        }

        JsonValue validateJson = validate.adaptToJson(model.validate);
        if (validateJson != null)
        {
            builder.add(VALIDATE_NAME, validateJson);
        }

        return builder.build();
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonObject object = (JsonObject) value;

        assert object.containsKey(CATALOG_NAME);

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

        ValidateConfig validateConfig = validate.adaptFromJsonObject(object);

        JsonModelConfigBuilder<JsonModelConfig> builder = JsonModelConfig.builder()
            .subject(subject)
            .validate(validateConfig);
        catalogs.forEach(builder::catalog);

        return builder.build();
    }
}
