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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.spi.JsonProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.engine.internal.config.NamespaceAdapter;

public final class ConfigWriter
{
    private final ConfigAdapterContext context;

    public ConfigWriter(
        ConfigAdapterContext context)
    {
        this.context = context;
    }

    public void write(
        NamespaceConfig namespace,
        Writer writer)
    {
        write0(namespace, writer);
    }

    public String write(
        NamespaceConfig namespace)
    {
        StringWriter writer = new StringWriter();
        write0(namespace, writer);
        return writer.toString();
    }

    private void write0(
        NamespaceConfig namespace,
        Writer writer)
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

            String jsonText = jsonb.toJson(namespace, NamespaceConfig.class);
            JsonNode json = new ObjectMapper().readTree(jsonText);
            new YAMLMapper().writeValue(writer, json);

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
}
