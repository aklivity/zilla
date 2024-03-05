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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import static java.util.Collections.unmodifiableMap;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.InputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.agrona.collections.Object2ObjectHashMap;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.engine.config.ConfigException;

public class AsyncapiParser
{
    private final Map<String, JsonSchema> schemas;

    public AsyncapiParser()
    {
        Map<String, JsonSchema> schemas = new Object2ObjectHashMap<>();
        schemas.put("2.6.0", schema("2.6.0"));
        schemas.put("3.0.0", schema("3.0.0"));
        this.schemas = unmodifiableMap(schemas);
    }

    public Asyncapi parse(
        String asyncapiText)
    {
        Asyncapi asyncapi = null;

        List<Exception> errors = new LinkedList<>();

        try
        {
            String asyncApiVersion = detectAsyncApiVersion(asyncapiText);

            JsonValidationService service = JsonValidationService.newInstance();
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));
            JsonSchema schema = schemas.get(asyncApiVersion);

            service.createReader(new StringReader(asyncapiText), schema, handler).read();

            Jsonb jsonb = JsonbBuilder.create();

            asyncapi = jsonb.fromJson(asyncapiText, Asyncapi.class);
        }
        catch (Exception ex)
        {
            errors.add(ex);
        }

        if (!errors.isEmpty())
        {
            Exception ex = errors.remove(0);
            errors.forEach(ex::addSuppressed);
            rethrowUnchecked(ex);
        }

        return asyncapi;
    }

    private JsonSchema schema(
        String version)
    {
        InputStream schemaInput = null;

        if (version.startsWith("2.6"))
        {
            schemaInput =  AsyncapiBinding.class.getResourceAsStream("schema/asyncapi.2.6.schema.json");
        }
        else if (version.startsWith("3.0"))
        {
            schemaInput = AsyncapiBinding.class.getResourceAsStream("schema/asyncapi.3.0.schema.json");
        }

        JsonValidationService service = JsonValidationService.newInstance();

        return service.createSchemaReaderFactoryBuilder()
                .withSpecVersionDetection(true)
                .build()
                .createSchemaReader(schemaInput)
                .read();
    }

    private String detectAsyncApiVersion(
        String openapiText)
    {
        try (JsonReader reader = Json.createReader(new StringReader(openapiText)))
        {
            JsonObject json = reader.readObject();
            if (json.containsKey("asyncapi"))
            {
                return json.getString("asyncapi");
            }
            else
            {
                throw new IllegalArgumentException("Unable to determine AsyncApi version.");
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error reading AsyncApi document.", e);
        }
    }
}