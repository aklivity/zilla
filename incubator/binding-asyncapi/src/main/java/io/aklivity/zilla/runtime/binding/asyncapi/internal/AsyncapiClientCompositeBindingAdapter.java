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
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public class AsyncapiClientCompositeBindingAdapter extends AsyncapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private int[] mqttPorts;
    private int[] mqttsPorts;

    @Override
    public String type()
    {
        return AsyncapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        AsyncapiOptionsConfig options = (AsyncapiOptionsConfig) binding.options;
        AsyncapiConfig asyncapiConfig = options.specs.get(0);
        this.asyncApi = asyncapiConfig.asyncApi;

        this.allPorts = resolveAllPorts();
        this.mqttPorts = resolvePortsForScheme("mqtt");
        this.mqttsPorts = resolvePortsForScheme("mqtts");
        this.isPlainEnabled = mqttPorts != null;
        this.isTlsEnabled = mqttsPorts != null;



        return BindingConfig.builder(binding)
            .composite()
                .name(String.format(binding.qname, "$composite"))
                .binding()
                    .name("mqtt_client0")
                    .type("mqtt")
                    .kind(CLIENT)
                    .exit(isTlsEnabled ? "tls_client0" : "tcp_client0")
                    .build()
                .inject(n -> injectTlsClient(n, options))
                .binding()
                    .name("tcp_client0")
                    .type("tcp")
                    .kind(CLIENT)
                    .options(TcpOptionsConfig::builder)
                        .host(options.host)
                        .ports(options.ports)
                        .build()
                    .build()
                .inject(this::injectCatalog)
                .build()
            .build();
    }

    private <C> NamespaceConfigBuilder<C> injectTlsClient(
        NamespaceConfigBuilder<C> namespace,
        AsyncapiOptionsConfig options)
    {
        if (isTlsEnabled)
        {
            namespace
                .binding()
                    .name("tls_client0")
                    .type("tls")
                    .kind(CLIENT)
                    .options(TlsOptionsConfig::builder)
                        .trust(options.trust)
                        .sni(options.sni)
                        .alpn(options.alpn)
                        .trustcacerts(options.trustcacerts)
                        .build()
                    .vault("client")
                    .exit("tcp_client0")
                    .build();
        }
        return namespace;
    }

    private <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace)
    {
        if (asyncApi.asyncapiComponents != null && asyncApi.asyncapiComponents.schemas != null &&
            !asyncApi.asyncapiComponents.schemas.isEmpty())
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
            for (Map.Entry<String, AsyncapiSchema> entry : asyncApi.asyncapiComponents.schemas.entrySet())
            {
                AsyncapiSchemaView schema = AsyncapiSchemaView.of(asyncApi.asyncapiComponents.schemas, entry.getValue());
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

    private static String writeSchemaYaml(
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
