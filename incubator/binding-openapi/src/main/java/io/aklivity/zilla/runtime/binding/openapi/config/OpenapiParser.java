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
package io.aklivity.zilla.runtime.binding.openapi.config;

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

import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.engine.config.ConfigException;

public class OpenapiParser
{
    private final Map<String, JsonSchema> schemas;

    public OpenapiParser()
    {
        Map<String, JsonSchema> schemas = new Object2ObjectHashMap<>();
        schemas.put("3.0.0", schema("3.0.0"));
        schemas.put("3.1.0", schema("3.1.0"));
        this.schemas = unmodifiableMap(schemas);
    }

    public Openapi parse(
        String openapiText)
    {
        Openapi openApi = null;

        List<Exception> errors = new LinkedList<>();

        try
        {
            String openApiVersion = detectOpenApiVersion(openapiText);

            JsonValidationService service = JsonValidationService.newInstance();
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));
            JsonSchema schema = schemas.get(openApiVersion);

            service.createReader(new StringReader(openapiText), schema, handler).read();

            Jsonb jsonb = JsonbBuilder.create();

            openApi = jsonb.fromJson(openapiText, Openapi.class);
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

        return openApi;
    }

    private JsonSchema schema(
        String version)
    {
        InputStream schemaInput = null;
        boolean detect = true;

        if (version.startsWith("3.0"))
        {
            schemaInput = OpenapiBinding.class.getResourceAsStream("schema/openapi.3.0.schema.json");
        }
        else if (version.startsWith("3.1"))
        {
            schemaInput =  OpenapiBinding.class.getResourceAsStream("schema/openapi.3.1.schema.json");
            detect = false;
        }

        JsonValidationService service = JsonValidationService.newInstance();

        return service.createSchemaReaderFactoryBuilder()
                .withSpecVersionDetection(detect)
                .build()
                .createSchemaReader(schemaInput)
                .read();
    }

    private String detectOpenApiVersion(
        String openapiText)
    {
        try (JsonReader reader = Json.createReader(new StringReader(openapiText)))
        {
            JsonObject json = reader.readObject();
            if (json.containsKey("openapi"))
            {
                return json.getString("openapi");
            }
            else
            {
                throw new IllegalArgumentException("Unable to determine OpenAPI version.");
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error reading OpenAPI document.", e);
        }
    }
}
