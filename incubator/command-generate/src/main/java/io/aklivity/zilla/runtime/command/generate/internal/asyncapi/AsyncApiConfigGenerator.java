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
package io.aklivity.zilla.runtime.command.generate.internal.asyncapi;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.apache.commons.collections4.MapUtils;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.command.generate.internal.airline.ConfigGenerator;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.AsyncApi;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Message;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Schema;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.MessageView;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view.SchemaView;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public abstract class AsyncApiConfigGenerator extends ConfigGenerator
{
    protected AsyncApi asyncApi;

    protected boolean hasJsonContentType()
    {
        String contentType = null;
        if (asyncApi.components != null && MapUtils.isNotEmpty(asyncApi.components.messages))
        {
            Message firstMessage = asyncApi.components.messages.entrySet().stream().findFirst().get().getValue();
            contentType = MessageView.of(asyncApi.components.messages, firstMessage).contentType();
        }
        return contentType != null && jsonContentType.reset(contentType).matches();
    }

    protected NamespaceConfigBuilder<NamespaceConfig> injectCatalog(
        NamespaceConfigBuilder<NamespaceConfig> namespace)
    {
        if (asyncApi.components != null && MapUtils.isNotEmpty(asyncApi.components.schemas))
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
            for (Map.Entry<String, Schema> entry : asyncApi.components.schemas.entrySet())
            {
                SchemaView schema = SchemaView.of(asyncApi.components.schemas, entry.getValue());
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
}
