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
package io.aklivity.zilla.runtime.command.generate.internal.openapi;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.apache.commons.collections4.MapUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.command.generate.internal.airline.ConfigGenerator;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.MediaType;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.OpenApi;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.model.Schema;
import io.aklivity.zilla.runtime.command.generate.internal.openapi.view.SchemaView;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public abstract class OpenApiConfigGenerator extends ConfigGenerator
{
    protected OpenApi openApi;

    protected SchemaView resolveSchemaForJsonContentType(
        Map<String, MediaType> content)
    {
        MediaType mediaType = null;
        if (content != null)
        {
            for (String contentType : content.keySet())
            {
                if (jsonContentType.reset(contentType).matches())
                {
                    mediaType = content.get(contentType);
                    break;
                }
            }
        }
        return mediaType == null ? null : SchemaView.of(openApi.components.schemas, mediaType.schema);
    }

    protected NamespaceConfigBuilder<NamespaceConfig> injectCatalog(
        NamespaceConfigBuilder<NamespaceConfig> namespace)
    {
        if (openApi.components != null && MapUtils.isNotEmpty(openApi.components.schemas))
        {
            namespace
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .type(INLINE_CATALOG_TYPE)
                    .options(InlineOptionsConfig::builder)
                        .subjects()
                            .inject(this::injectSubjects)
                            .build()
                        .build()
                    .build();
        }
        return namespace;
    }

    protected <C> InlineSchemaConfigBuilder<C> injectSubjects(
        InlineSchemaConfigBuilder<C> subjects)
    {
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            YAMLMapper yaml = YAMLMapper.builder()
                .disable(WRITE_DOC_START_MARKER)
                .enable(MINIMIZE_QUOTES)
                .build();
            for (Map.Entry<String, Schema> entry : openApi.components.schemas.entrySet())
            {
                SchemaView schema = SchemaView.of(openApi.components.schemas, entry.getValue());
                subjects
                    .subject(entry.getKey())
                        .version(VERSION_LATEST)
                        .schema(writeSchemaYaml(jsonb, yaml, schema))
                        .build();
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return subjects;
    }

    protected static String writeSchemaYaml(
        Jsonb jsonb,
        YAMLMapper yaml,
        SchemaView schema)
    {
        String result = null;
        try
        {
            String schemaJson = jsonb.toJson(schema);
            JsonNode json = new ObjectMapper().readTree(schemaJson);
            result = yaml.writeValueAsString(json);
        }
        catch (JsonProcessingException ex)
        {
            rethrowUnchecked(ex);
        }
        return result;
    }
}
