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
package io.aklivity.zilla.runtime.command.generate.internal.airline;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.AsyncApi;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Message;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Schema;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.MessageView;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.validator.core.config.IntegerValidatorConfig;
import io.aklivity.zilla.runtime.validator.core.config.StringValidatorConfig;

public abstract class ConfigGenerator
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final String INLINE_CATALOG_TYPE = "inline";
    protected static final String APPLICATION_JSON = "application/json";
    protected static final String VERSION_LATEST = "latest";
    protected static final Matcher JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$").matcher("");

    protected final Map<String, ValidatorConfig> validators = Map.of(
        "string", StringValidatorConfig.builder().build(),
        "integer", IntegerValidatorConfig.builder().build()
    );

    protected AsyncApi asyncApi;

    public abstract String generate();

    protected boolean jsonContentType()
    {
        boolean result = false;
        String contentType = resolveContentType();
        if (contentType != null)
        {
            result = JSON_CONTENT_TYPE.reset(contentType).matches();
        }
        return result;
    }

    private String resolveContentType()
    {
        String contentType = null;
        if (asyncApi.components != null && asyncApi.components.messages != null && !asyncApi.components.messages.isEmpty())
        {
            Message firstMessage = asyncApi.components.messages.entrySet().stream().findFirst().get().getValue();
            contentType = MessageView.of(asyncApi.components.messages, firstMessage).contentType();
        }
        return contentType;
    }

    protected NamespaceConfigBuilder<NamespaceConfig> injectCatalog(
        NamespaceConfigBuilder<NamespaceConfig> namespace)
    {
        if (asyncApi.components != null && asyncApi.components.schemas != null && !asyncApi.components.schemas.isEmpty())
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

    private <C> InlineSchemaConfigBuilder<C> injectSubjects(
        InlineSchemaConfigBuilder<C> subjects)
    {
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            YAMLMapper yaml = YAMLMapper.builder()
                .disable(WRITE_DOC_START_MARKER)
                .enable(MINIMIZE_QUOTES)
                .build();
            for (Map.Entry<String, Schema> entry : asyncApi.components.schemas.entrySet())
            {
                subjects
                    .subject(entry.getKey())
                        .version(VERSION_LATEST)
                        .schema(writeSchemaYaml(jsonb, yaml, entry.getValue()))
                        .build();
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return subjects;
    }

    private static String writeSchemaYaml(
        Jsonb jsonb,
        YAMLMapper yaml,
        Schema schema)
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

    protected final String unquoteEnvVars(
        String yaml,
        List<String> unquotedEnvVars)
    {
        for (String envVar : unquotedEnvVars)
        {
            yaml = yaml.replaceAll(
                Pattern.quote(String.format("\"${{env.%s}}\"", envVar)),
                String.format("\\${{env.%s}}", envVar)
            );
        }
        return yaml;
    }
}
