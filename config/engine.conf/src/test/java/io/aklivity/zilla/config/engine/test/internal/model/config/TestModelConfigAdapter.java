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
package io.aklivity.zilla.config.engine.test.internal.model.config;

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.SchemaConfigAdapter;
import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateConfigAdapter;

public class TestModelConfigAdapter extends ConfigAdapter<ModelConfig, JsonValue>
{
    private static final String TEST = "test";
    private static final String LENGTH = "length";
    private static final String TRANSFORM = "transform";
    private static final String TRANSFORM_AUTHORIZATIONS = "transformAuthorizations";
    private static final String CAPABILITY = "capability";
    private static final String READ = "read";
    private static final String CATALOG_NAME = "catalog";
    private static final String FIELDS = "fields";
    private static final String REJECT = "reject";
    private static final String SUSPEND = "suspend";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();
    private final ValidateConfigAdapter validate = new ValidateConfigAdapter();

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

        int transformLength = object.containsKey(TRANSFORM)
            ? object.getJsonObject(TRANSFORM).getInt(LENGTH, -1)
            : -1;

        List<Long> transformAuthorizations = null;
        if (object.containsKey(TRANSFORM_AUTHORIZATIONS))
        {
            transformAuthorizations = new LinkedList<>();
            for (JsonValue item : object.getJsonArray(TRANSFORM_AUTHORIZATIONS))
            {
                transformAuthorizations.add(((JsonNumber) item).longValue());
            }
        }

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
                catalogs.add(CatalogedConfig.builder().name(catalogName).schemas(schemas).build());
            }
        }

        List<String> fields = null;
        if (object.containsKey(FIELDS))
        {
            fields = new LinkedList<>();
            for (JsonValue item : object.getJsonArray(FIELDS))
            {
                fields.add(((JsonString) item).getString());
            }
        }

        ValidateConfig validateConfig = validate.adaptFromJsonObject(object);

        List<String> reject = null;
        if (object.containsKey(REJECT))
        {
            reject = new LinkedList<>();
            for (JsonValue item : object.getJsonArray(REJECT))
            {
                reject.add(((JsonString) item).getString());
            }
        }

        boolean suspend = object.containsKey(SUSPEND) && object.getBoolean(SUSPEND);

        return new TestModelConfig(length, catalogs, read, transformLength, fields, validateConfig, transformAuthorizations,
            reject, suspend);
    }
}
