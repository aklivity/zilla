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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.serializer;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.engine.catalog.ParsedSchema;

public class RegisterSchemaRequest
{
    private static final String SCHEMA = "schema";
    private static final String TYPE = "schemaType";
    private static final String ID = "id";

    public String buildBody(
        String type,
        String schema)
    {
        JsonObjectBuilder register = Json.createObjectBuilder();

        if (schema != null)
        {
            register.add(SCHEMA, schema);
        }

        if (type != null)
        {
            register.add(TYPE, type.toUpperCase());
        }

        return register.build().toString();
    }

    public int resolveResponse(
        String response)
    {
        try
        {
            JsonReader reader = Json.createReader(new StringReader(response));
            JsonObject object = reader.readObject();

            return object.containsKey(ID) ? object.getInt(ID) : NO_SCHEMA_ID;
        }
        catch (JsonParsingException ex)
        {
            return NO_SCHEMA_ID;
        }
    }

    public ParsedSchema resolveSchemaResponse(
        String response)
    {
        try
        {
            JsonReader reader = Json.createReader(new StringReader(response));
            JsonObject object = reader.readObject();

            int id = object.containsKey(ID) ? object.getInt(ID) : NO_SCHEMA_ID;

            String schema = object.containsKey(SCHEMA) ? object.getString(SCHEMA) : null;

            return new ParsedSchema(id, schema);
        }
        catch (JsonParsingException ex)
        {
            return null;
        }
    }

    public ParsedSchema resolveSchemaResponse(
        int id,
        String response)
    {
        try
        {
            JsonReader reader = Json.createReader(new StringReader(response));
            JsonObject object = reader.readObject();

            String schema = object.containsKey(SCHEMA) ? object.getString(SCHEMA) : null;

            return new ParsedSchema(id, schema);
        }
        catch (JsonParsingException ex)
        {
            return null;
        }
    }
}
