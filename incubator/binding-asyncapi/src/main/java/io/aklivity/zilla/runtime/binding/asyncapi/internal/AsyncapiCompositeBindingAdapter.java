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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public class AsyncapiCompositeBindingAdapter
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final String INLINE_CATALOG_TYPE = "inline";
    protected static final String VERSION_LATEST = "latest";
    protected static final String APPLICATION_JSON = "application/json";

    protected Asyncapi asyncapi;
    protected Map<String, Asyncapi> asyncApis;
    protected boolean isTlsEnabled;
    protected AsyncapiProtocol protocol;
    protected String qname;
    protected String qvault;


    protected AsyncapiProtocol resolveProtocol(
        String protocolName,
        AsyncapiOptionsConfig options)
    {
        Pattern pattern = Pattern.compile("(http|mqtt|kafka)");
        Matcher matcher = pattern.matcher(protocolName);
        AsyncapiProtocol protocol = null;
        if (matcher.find())
        {
            switch (matcher.group())
            {
            case "http":
                protocol = new AsyncapiHttpProtocol(qname, asyncapi, options);
                break;
            case "mqtt":
                protocol = new AyncapiMqttProtocol(qname, asyncapi);
                break;
            case "kafka":
            case "kafka-secure":
                protocol = new AyncapiKafkaProtocol(qname, asyncapi, options, protocolName);
                break;
            }
        }
        else
        {
            // TODO: should we do something?
        }
        return protocol;
    }

    protected <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace,
        Asyncapi asyncapi)
    {
        if (asyncapi.components != null && asyncapi.components.schemas != null && !asyncapi.components.schemas.isEmpty())
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
            for (Map.Entry<String, AsyncapiSchema> entry : asyncapi.components.schemas.entrySet())
            {
                AsyncapiSchemaView schema = AsyncapiSchemaView.of(asyncapi.components.schemas, entry.getValue());
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
        Object schema)
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
