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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.spi.JsonProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.engine.internal.config.NamespaceAdapter;

public final class EngineConfigWriter
{
    private static final JsonPatch NOOP_PATCH = JsonProvider.provider().createPatch(JsonValue.EMPTY_JSON_ARRAY);

    private final ConfigAdapterContext context;

    public EngineConfigWriter(
        ConfigAdapterContext context)
    {
        this.context = context;
    }

    public void write(
        EngineConfig config,
        Writer writer)
    {
        write0(config, writer, NOOP_PATCH);
    }

    public void write(
        EngineConfig config,
        Writer writer,
        JsonPatch patch)
    {
        write0(config, writer, patch);
    }

    public String write(
        EngineConfig config)
    {
        StringWriter writer = new StringWriter();
        write0(config, writer, NOOP_PATCH);
        return writer.toString();
    }

    public String write(
        EngineConfig config,
        JsonPatch patch)
    {
        StringWriter writer = new StringWriter();
        write0(config, writer, patch);
        return writer.toString();
    }

    public String write(
        NamespaceConfig config)
    {
        StringWriter writer = new StringWriter();
        write0(config, writer, NOOP_PATCH);
        return writer.toString();
    }

    private void write0(
        EngineConfig engine,
        Writer writer,
        JsonPatch patch)
    {
        List<Exception> errors = new LinkedList<>();

        write:
        try
        {
            // TODO: YamlProvider (supporting YamlGenerator)
            JsonProvider provider = JsonProvider.provider();

            JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter(context))
                .withFormatting(true);
            Jsonb jsonb = JsonbBuilder.newBuilder()
                .withProvider(provider)
                .withConfig(config)
                .build();

            for (NamespaceConfig namespace : engine.namespaces)
            {
                write0(namespace, writer, patch, provider, jsonb);

                if (!errors.isEmpty())
                {
                    break write;
                }
            }
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
    }

    private void write0(
        NamespaceConfig namespace,
        Writer writer,
        JsonPatch patch)
    {
        List<Exception> errors = new LinkedList<>();

        write:
        try
        {
            // TODO: YamlProvider (supporting YamlGenerator)
            JsonProvider provider = JsonProvider.provider();

            JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter(context))
                .withFormatting(true);
            Jsonb jsonb = JsonbBuilder.newBuilder()
                .withProvider(provider)
                .withConfig(config)
                .build();

            write0(namespace, writer, patch, provider, jsonb);

            if (!errors.isEmpty())
            {
                break write;
            }
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
    }

    private void write0(
        NamespaceConfig config,
        Writer writer,
        JsonPatch patch,
        JsonProvider provider,
        Jsonb jsonb) throws Exception
    {
        String jsonText = jsonb.toJson(config, NamespaceConfig.class);

        JsonObject jsonObject = provider.createReader(new StringReader(jsonText)).readObject();
        JsonObject patched = patch.apply(jsonObject);
        StringWriter patchedText = new StringWriter();
        JsonWriter jsonWriter = provider.createWriter(patchedText);
        jsonWriter.write(patched);
        String patchedJson = patchedText.toString();

        JsonNode json = new ObjectMapper().readTree(patchedJson);
        YAMLMapper mapper = YAMLMapper.builder()
            .disable(WRITE_DOC_START_MARKER)
            .enable(MINIMIZE_QUOTES)
            .build();
        mapper.writeValue(writer, json);
    }
}
