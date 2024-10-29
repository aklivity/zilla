/*
 * Copyright 2021-2024 Aklivity Inc
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d\\.\\d)\\.\\d+");
    private final Map<String, JsonSchema> schemas;

    public OpenapiParser()
    {
        Map<String, JsonSchema> schemas = new Object2ObjectHashMap<>();
        schemas.put("3.0", schema("3.0.3"));
        schemas.put("3.1", schema("3.1.0"));
        this.schemas = unmodifiableMap(schemas);
    }

    public Openapi parse(
        String openapiText)
    {
        Openapi openapi = null;

        List<Exception> errors = new LinkedList<>();

        try
        {
            String openApiVersion = detectOpenApiVersion(openapiText);

            JsonValidationService service = JsonValidationService.newInstance();
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));
            JsonSchema schema = schemas.get(openApiVersion);

            service.createReader(new StringReader(openapiText), schema, handler).read();

            Jsonb jsonb = JsonbBuilder.create();

            openapi = jsonb.fromJson(openapiText, Openapi.class);
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

        return openapi;
    }

    private JsonSchema schema(
        String version)
    {
        final String schemaName = String.format("schema/openapi.%s.schema.json", version);
        final InputStream schemaInput = OpenapiBinding.class.getResourceAsStream(schemaName);
        final boolean detect = !version.startsWith("3.1");

        return JsonValidationService.newInstance()
                .createSchemaReaderFactoryBuilder()
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
                final String versionString = json.getString("openapi");

                final Matcher matcher = VERSION_PATTERN.matcher(versionString);

                final String majorMinorVersion = matcher.matches() ? matcher.group(1) : null;
                return majorMinorVersion;
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
