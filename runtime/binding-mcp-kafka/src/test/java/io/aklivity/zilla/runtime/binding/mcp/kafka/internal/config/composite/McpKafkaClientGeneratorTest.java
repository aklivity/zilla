/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.composite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.kafka.KafkaOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.kafka.McpKafkaOptionsConfig;
import io.aklivity.zilla.config.binding.tcp.TcpOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaCompositeConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class McpKafkaClientGeneratorTest
{
    private static final long CACHE_CLIENT_BINDING_ID = 42L;

    private final EngineContext context = mock(EngineContext.class);
    private final McpKafkaClientGenerator generator = new McpKafkaClientGenerator("");

    @Test
    public void shouldGenerateKafkaClientPipeline()
    {
        when(context.supplyBindingId(any(NamespaceConfig.class), any(BindingConfig.class)))
            .thenAnswer(inv -> "kafka_cache_client0".equals(((BindingConfig) inv.getArgument(1)).name)
                ? CACHE_CLIENT_BINDING_ID
                : 0L);

        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .build();

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .options(options)
            .build();

        McpKafkaCompositeConfig composite = generator.generate(binding, context);

        assertThat(composite.exitId, equalTo(CACHE_CLIENT_BINDING_ID));
        assertThat(composite.namespaces.size(), equalTo(1));

        NamespaceConfig namespace = composite.namespaces.get(0);
        List<BindingConfig> bindings = namespace.bindings;
        assertThat(bindings.size(), equalTo(4));

        BindingConfig cacheClient = findBinding(bindings, "kafka_cache_client0");
        assertThat(cacheClient.type, equalTo("kafka"));
        assertThat(cacheClient.kind, equalTo(KindConfig.CACHE_CLIENT));
        assertThat(cacheClient.routes.get(0).exit, equalTo("kafka_cache_server0"));

        BindingConfig cacheServer = findBinding(bindings, "kafka_cache_server0");
        assertThat(cacheServer.type, equalTo("kafka"));
        assertThat(cacheServer.kind, equalTo(KindConfig.CACHE_SERVER));
        assertThat(cacheServer.routes.get(0).exit, equalTo("kafka_client0"));

        BindingConfig kafkaClient = findBinding(bindings, "kafka_client0");
        assertThat(kafkaClient.type, equalTo("kafka"));
        assertThat(kafkaClient.kind, equalTo(KindConfig.CLIENT));
        assertThat(kafkaClient.routes.get(0).exit, equalTo("tcp_client0"));
        KafkaOptionsConfig kafkaOptions = (KafkaOptionsConfig) kafkaClient.options;
        assertThat(kafkaOptions.servers.size(), equalTo(1));
        assertThat(kafkaOptions.servers.get(0).host, equalTo("localhost"));
        assertThat(kafkaOptions.servers.get(0).port, equalTo(9092));

        BindingConfig tcpClient = findBinding(bindings, "tcp_client0");
        assertThat(tcpClient.type, equalTo("tcp"));
        assertThat(tcpClient.kind, equalTo(KindConfig.CLIENT));
        TcpOptionsConfig tcpOptions = (TcpOptionsConfig) tcpClient.options;
        assertThat(tcpOptions.host, equalTo("localhost"));
        assertThat(tcpOptions.ports[0], equalTo(9092));
    }

    @Test
    public void shouldPropagateAuthorization()
    {
        when(context.supplyBindingId(any(NamespaceConfig.class), any(BindingConfig.class))).thenReturn(0L);

        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .authorization()
                .name("guard0")
                .credentials()
                    .mechanism("plain")
                    .username("username")
                    .password("password")
                    .build()
                .build()
            .build();

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .options(options)
            .build();

        McpKafkaCompositeConfig composite = generator.generate(binding, context);

        BindingConfig kafkaClient = findBinding(composite.namespaces.get(0).bindings, "kafka_client0");
        KafkaOptionsConfig kafkaOptions = (KafkaOptionsConfig) kafkaClient.options;

        assertThat(kafkaOptions.authorization, not(nullValue()));
        assertThat(kafkaOptions.authorization.name, equalTo("guard0"));
        assertThat(kafkaOptions.authorization.credentials.mechanism, equalTo("plain"));
    }

    @Test
    public void shouldOverrideCacheClientExit()
    {
        McpKafkaClientGenerator overridden = new McpKafkaClientGenerator("test:kafka0");

        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .build();

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .options(options)
            .build();
        binding.resolveId = name -> "test:kafka0".equals(name) ? CACHE_CLIENT_BINDING_ID : 0L;

        McpKafkaCompositeConfig composite = overridden.generate(binding, context);

        assertThat(composite.exitId, equalTo(CACHE_CLIENT_BINDING_ID));

        BindingConfig cacheClient = findBinding(composite.namespaces.get(0).bindings, "kafka_cache_client0");
        assertThat(cacheClient.routes.get(0).exit, equalTo("kafka_cache_server0"));
    }

    @Test
    public void shouldPropagateTopicModels()
    {
        when(context.supplyBindingId(any(NamespaceConfig.class), any(BindingConfig.class))).thenReturn(0L);

        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .topic()
                .name("orders")
                .value(TestModelConfig.builder().build())
                .build()
            .build();

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .options(options)
            .build();

        McpKafkaCompositeConfig composite = generator.generate(binding, context);

        List<BindingConfig> bindings = composite.namespaces.get(0).bindings;

        BindingConfig cacheClient = findBinding(bindings, "kafka_cache_client0");
        KafkaOptionsConfig cacheClientOptions = (KafkaOptionsConfig) cacheClient.options;
        assertThat(cacheClientOptions.topics.size(), equalTo(1));
        assertThat(cacheClientOptions.topics.get(0).name, equalTo("orders"));
        assertThat(cacheClientOptions.topics.get(0).value.model, equalTo("test"));

        BindingConfig cacheServer = findBinding(bindings, "kafka_cache_server0");
        KafkaOptionsConfig cacheServerOptions = (KafkaOptionsConfig) cacheServer.options;
        assertThat(cacheServerOptions.topics.size(), equalTo(1));
        assertThat(cacheServerOptions.topics.get(0).name, equalTo("orders"));
        assertThat(cacheServerOptions.topics.get(0).value.model, equalTo("test"));
    }

    @Test
    public void shouldGenerateTlsClientWhenAuthorizationConfigured()
    {
        when(context.supplyBindingId(any(NamespaceConfig.class), any(BindingConfig.class))).thenReturn(0L);

        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .authorization()
                .name("guard0")
                .credentials()
                    .mechanism("plain")
                    .username("username")
                    .password("password")
                    .build()
                .build()
            .build();

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .options(options)
            .build();

        McpKafkaCompositeConfig composite = generator.generate(binding, context);

        List<BindingConfig> bindings = composite.namespaces.get(0).bindings;
        assertThat(bindings.size(), equalTo(5));

        BindingConfig kafkaClient = findBinding(bindings, "kafka_client0");
        assertThat(kafkaClient.routes.get(0).exit, equalTo("tls_client0"));

        BindingConfig tlsClient = findBinding(bindings, "tls_client0");
        assertThat(tlsClient.type, equalTo("tls"));
        assertThat(tlsClient.kind, equalTo(KindConfig.CLIENT));
        assertThat(tlsClient.vault, nullValue());
        assertThat(tlsClient.routes.get(0).exit, equalTo("tcp_client0"));
    }

    @Test
    public void shouldGenerateTlsClientWhenVaultConfigured()
    {
        when(context.supplyBindingId(any(NamespaceConfig.class), any(BindingConfig.class))).thenReturn(0L);

        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .build();

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .vault("vault0")
            .options(options)
            .build();
        binding.qvault = "test:vault0";

        McpKafkaCompositeConfig composite = generator.generate(binding, context);

        List<BindingConfig> bindings = composite.namespaces.get(0).bindings;
        assertThat(bindings.size(), equalTo(5));

        BindingConfig kafkaClient = findBinding(bindings, "kafka_client0");
        assertThat(kafkaClient.routes.get(0).exit, equalTo("tls_client0"));

        BindingConfig tlsClient = findBinding(bindings, "tls_client0");
        assertThat(tlsClient.vault, equalTo("test:vault0"));
        assertThat(tlsClient.routes.get(0).exit, equalTo("tcp_client0"));
    }

    @Test
    public void shouldNotGenerateTlsClientWhenNeitherAuthorizationNorVaultConfigured()
    {
        when(context.supplyBindingId(any(NamespaceConfig.class), any(BindingConfig.class))).thenReturn(0L);

        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .build();

        BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .options(options)
            .build();

        McpKafkaCompositeConfig composite = generator.generate(binding, context);

        List<BindingConfig> bindings = composite.namespaces.get(0).bindings;
        assertThat(bindings.size(), equalTo(4));

        BindingConfig kafkaClient = findBinding(bindings, "kafka_client0");
        assertThat(kafkaClient.routes.get(0).exit, equalTo("tcp_client0"));
    }

    private static BindingConfig findBinding(
        List<BindingConfig> bindings,
        String name)
    {
        return bindings.stream()
            .filter(b -> name.equals(b.name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing binding: " + name));
    }
}
