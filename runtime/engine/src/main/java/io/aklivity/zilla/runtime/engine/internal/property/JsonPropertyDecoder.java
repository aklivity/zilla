/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.property;

import static java.util.stream.Collectors.joining;

import java.io.StringReader;
import java.lang.reflect.InaccessibleObjectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonConfig;
import jakarta.json.JsonException;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public final class JsonPropertyDecoder<T>
{
    private final Class<T> kind;
    private final String name;
    private final JsonSchema schema;

    public JsonPropertyDecoder(
        Class<T> kind,
        String name,
        JsonSchema schema)
    {
        this.kind = kind;
        this.name = name;
        this.schema = schema;
    }

    public T decode(
        String value)
    {
        validate(value);

        T decoded = null;
        try
        {
            decoded = Binder.JSONB.fromJson(new StringReader(value), kind);
        }
        catch (JsonbException ex)
        {
            throw new IllegalArgumentException(decodeFailure(ex), ex);
        }

        return decoded;
    }

    private void validate(
        String value)
    {
        final List<String> problems = new ArrayList<>();

        try (JsonParser parser = Binder.PROVIDER.createParser(new StringReader(value)))
        {
            schema.validate(parser, problem -> problems.add(describe(problem)));
        }
        catch (JsonException ex)
        {
            throw new IllegalArgumentException(String.format("Invalid property %s: %s", name, ex.getMessage()), ex);
        }

        if (!problems.isEmpty())
        {
            throw new IllegalArgumentException(problems.stream().collect(joining("; ")));
        }
    }

    private String describe(
        JsonSchemaDiagnostic problem)
    {
        final StringBuilder location = new StringBuilder(name);
        final String pointer = problem.pointer();

        if (!pointer.isEmpty())
        {
            for (String token : pointer.substring(1).split("/", -1))
            {
                final String segment = token.replace("~1", "/").replace("~0", "~");

                if (!segment.isEmpty() && segment.chars().allMatch(Character::isDigit))
                {
                    location.append('[').append(segment).append(']');
                }
                else
                {
                    location.append('.').append(segment);
                }
            }
        }

        return String.format("Invalid property %s: %s", location, problem.message());
    }

    private String decodeFailure(
        JsonbException ex)
    {
        String failure = String.format("Unable to decode property %s: %s", name, ex.getMessage());

        for (Throwable cause = ex; cause != null; cause = cause.getCause())
        {
            if (cause instanceof IllegalAccessException || cause instanceof InaccessibleObjectException)
            {
                Class<?> type = kind;
                while (type.isArray())
                {
                    type = type.getComponentType();
                }

                final Module module = type.getModule();
                failure = String.format("Unable to decode property %s: package %s of module %s must be opened",
                    name, type.getPackageName(), module.getName());
                break;
            }
        }

        return failure;
    }

    private static final class Binder
    {
        private static final JsonProvider PROVIDER =
            YamlJson.provider(Map.of(JsonConfig.KEY_STRATEGY, JsonConfig.KeyStrategy.NONE));
        private static final Jsonb JSONB = JsonbBuilder.newBuilder().withProvider(PROVIDER).build();

        private Binder()
        {
        }
    }
}
