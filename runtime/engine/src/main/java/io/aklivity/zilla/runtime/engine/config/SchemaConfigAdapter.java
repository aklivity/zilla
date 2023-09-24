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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public class SchemaConfigAdapter implements JsonbAdapter<SchemaConfig, JsonObject>
{
    private static final String SCHEMA_NAME = "schema";
    private static final String STRATEGY_NAME = "strategy";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String ID_NAME = "id";

    @Override
    public JsonObject adaptToJson(
        SchemaConfig schema)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (schema.schema != null)
        {
            object.add(SCHEMA_NAME, schema.schema);
        }
        if (schema.strategy != null)
        {
            object.add(STRATEGY_NAME, schema.strategy);
        }
        if (schema.subject != null)
        {
            object.add(SUBJECT_NAME, schema.subject);
        }
        if (schema.version != null)
        {
            object.add(VERSION_NAME, schema.version);
        }
        if (schema.id != 0)
        {
            object.add(ID_NAME, schema.id);
        }
        return object.build();
    }

    @Override
    public SchemaConfig adaptFromJson(
        JsonObject object)
    {
        String schema = null;
        if (object.containsKey(SCHEMA_NAME))
        {
            schema = object.getString(SCHEMA_NAME);
        }
        String strategy = null;
        if (object.containsKey(STRATEGY_NAME))
        {
            strategy = object.getString(STRATEGY_NAME);
        }
        String subject = null;
        if (object.containsKey(SUBJECT_NAME))
        {
            subject = object.getString(SUBJECT_NAME);
        }
        String version = null;
        if (object.containsKey(VERSION_NAME))
        {
            version = object.getString(VERSION_NAME);
        }
        int id = 0;
        if (object.containsKey(ID_NAME))
        {
            id = object.getInt(ID_NAME);
        }
        return new SchemaConfig(schema, strategy, subject, version, id);
    }
}
