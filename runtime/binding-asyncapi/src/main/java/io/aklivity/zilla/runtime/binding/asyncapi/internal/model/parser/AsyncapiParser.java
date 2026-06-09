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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model.parser;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableMap;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.config.ConfigException;

public class AsyncapiParser
{
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?!\\.)(\\d+(\\.\\d+)+)(?:[-.][a-zA-Z]+)?(?![\\d.])$");

    private final Map<String, JsonSchema> schemas;

    public AsyncapiParser()
    {
        Map<String, JsonSchema> schemas = new Object2ObjectHashMap<>();
        schemas.put("2.6.0", schema("2.6.0"));
        schemas.put("3.0.0", schema("3.0.1"));
        this.schemas = unmodifiableMap(schemas);
    }

    public Asyncapi parse(
        String asyncapiText)
    {
        Asyncapi asyncapi = null;

        List<Exception> errors = new LinkedList<>();

        try
        {
            JsonObject asyncapiObject = readAsyncapiObject(asyncapiText);
            String asyncApiVersion = detectAsyncApiVersion(asyncapiObject);

            JsonProvider provider = YamlJson.provider();
            JsonSchema schema = schemas.get(asyncApiVersion);

            List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
            boolean valid;
            try (JsonParser parser = provider.createParser(new StringReader(asyncapiText)))
            {
                valid = schema.validate(parser, diagnostics::add);
            }
            if (!valid)
            {
                diagnostics.forEach(problem -> errors.add(new ConfigException(problem.toString())));
            }

            Jsonb jsonb = JsonbBuilder.create();

            asyncapi = jsonb.fromJson(asyncapiObject.toString(), Asyncapi.class);
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
        final String schemaName = String.format("schema/asyncapi.%s.schema.json", version);
        try (InputStream schemaInput = AsyncapiBinding.class.getResourceAsStream(schemaName))
        {
            return JsonSchema.of(new String(schemaInput.readAllBytes(), UTF_8));
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Unable to read AsyncApi schema: " + schemaName, ex);
        }
    }

    private JsonObject readAsyncapiObject(
        String asyncapiText)
    {
        try (JsonReader reader = YamlJson.createReader(new StringReader(asyncapiText)))
        {
            return reader.readObject();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error reading AsyncApi document.", e);
        }
    }

    private String detectAsyncApiVersion(
        JsonObject json)
    {
        if (json.containsKey("asyncapi"))
        {
            final String versionString = json.getString("asyncapi");
            final Matcher matcher = VERSION_PATTERN.matcher(versionString);

            return matcher.matches() ? matcher.group(0) : null;
        }
        else
        {
            throw new IllegalArgumentException("Unable to determine AsyncApi version.");
        }
    }
}
