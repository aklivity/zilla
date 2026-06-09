/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.specs.engine.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public final class ConfigSchemaRule implements TestRule
{
    private JsonProvider provider;
    private JsonSchema schema;

    private String schemaName = "io/aklivity/zilla/specs/engine/schema/engine.schema.json";
    private List<String> schemaPatchNames = new ArrayList<>();

    private String configurationRoot;

    private Function<String, InputStream> findConfig;

    public ConfigSchemaRule schema(
        String schemaName)
    {
        this.schemaName = schemaName;
        return this;
    }

    public ConfigSchemaRule schemaPatch(
        String schemaPatchName)
    {
        this.schemaPatchNames.add(schemaPatchName);
        return this;
    }

    public ConfigSchemaRule configurationRoot(
        String configurationRoot)
    {
        this.configurationRoot = configurationRoot;
        return this;
    }

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        Objects.requireNonNull(schemaName, "schema");
        schemaPatchNames.forEach(n -> Objects.requireNonNull(n, "schemaPatch"));

        Function<String, InputStream> findResource = description.getTestClass().getClassLoader()::getResourceAsStream;

        InputStream schemaInput = findResource.apply(schemaName);

        JsonProvider schemaProvider = YamlJson.provider();
        JsonReader schemaReader = schemaProvider.createReader(schemaInput);
        JsonObject schemaObject = schemaReader.readObject();

        for (String schemaPatchName : schemaPatchNames)
        {
            InputStream schemaPatchInput = findResource.apply(schemaPatchName);

            Objects.requireNonNull(schemaPatchInput, "schemaPatch");

            JsonReader schemaPatchReader = schemaProvider.createReader(schemaPatchInput);
            JsonArray schemaPatchArray = schemaPatchReader.readArray();
            JsonPatch schemaPatch = schemaProvider.createPatch(schemaPatchArray);

            schemaObject = schemaPatch.apply(schemaObject);
        }

        schema = JsonSchema.of(schemaObject.toString());
        provider = YamlJson.provider();

        if (configurationRoot != null)
        {
            String configFormat = String.format("%s/%%s", configurationRoot);
            findConfig = configName -> findResource.apply(String.format(configFormat, configName));
        }
        else
        {
            Class<?> testClass = description.getTestClass();
            String configFormat = String.format("%s-%%s", testClass.getSimpleName());
            findConfig = configName -> testClass.getResourceAsStream(String.format(configFormat, configName));
        }

        return base;
    }

    public JsonObject validate(
        String configName)
    {
        String configText = read(findConfig.apply(configName));

        JsonObject config;
        try (JsonReader reader = provider.createReader(new StringReader(configText)))
        {
            config = reader.readObject();
        }

        try (JsonParser parser = schema.newParser(true, provider.createParser(new StringReader(configText))))
        {
            while (parser.hasNext())
            {
                parser.next();
            }
        }

        return config;
    }

    private static String read(
        InputStream input)
    {
        try
        {
            return new String(input.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Unable to read configuration", ex);
        }
    }
}
