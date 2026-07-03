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
package io.aklivity.zilla.runtime.common.openapi.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.model.OpenapiDeserializers;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class OpenapiParser
{
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d\\.\\d)\\.\\d+");
    private final Map<String, JsonSchema> schemas;
    private final Map<OpenapiExtensionScope, Map<String, Class<?>>> extensionTypes;
    private final Map<OpenapiExtensionScope, Map<String, Class<?>>> prefixExtensionTypes;

    public OpenapiParser()
    {
        this(emptyMap(), emptyMap());
    }

    OpenapiParser(
        Map<OpenapiExtensionScope, Map<String, Class<?>>> extensionTypes,
        Map<OpenapiExtensionScope, Map<String, Class<?>>> prefixExtensionTypes)
    {
        Map<String, JsonSchema> schemas = new Object2ObjectHashMap<>();
        schemas.put("3.0", schema("3.0.3"));
        schemas.put("3.1", schema("3.1.0"));
        this.schemas = unmodifiableMap(schemas);
        this.extensionTypes = extensionTypes;
        this.prefixExtensionTypes = prefixExtensionTypes;
    }

    public Openapi parse(
        String openapiText)
    {
        Openapi openapi = null;

        List<Exception> errors = new LinkedList<>();

        try
        {
            JsonProvider provider = YamlJson.provider();

            JsonObject document;
            try (JsonReader reader = YamlJson.createReader(new StringReader(openapiText)))
            {
                document = reader.readObject();
            }

            String openApiVersion = detectOpenApiVersion(document);
            JsonSchema schema = schemas.get(openApiVersion);

            try (JsonParser parser = provider.createParser(new StringReader(openapiText)))
            {
                schema.validate(parser, problem -> errors.add(new OpenapiException(problem.toString())));
            }

            List<JsonbDeserializer<?>> deserializers = OpenapiDeserializers.all(extensionTypes, prefixExtensionTypes);
            Jsonb jsonb = JsonbBuilder.newBuilder()
                    .withProvider(provider)
                    .withConfig(new JsonbConfig()
                        .withDeserializers(deserializers.toArray(JsonbDeserializer[]::new)))
                    .build();

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
        try (InputStream schemaInput = OpenapiParser.class.getResourceAsStream(schemaName))
        {
            return JsonSchema.of(new String(schemaInput.readAllBytes(), UTF_8));
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Unable to read OpenAPI schema: " + schemaName, ex);
        }
    }

    private String detectOpenApiVersion(
        JsonObject document)
    {
        if (!document.containsKey("openapi"))
        {
            throw new IllegalArgumentException("Unable to determine OpenAPI version.");
        }

        final String versionString = document.getString("openapi");
        final Matcher matcher = VERSION_PATTERN.matcher(versionString);

        return matcher.matches() ? matcher.group(1) : null;
    }
}
