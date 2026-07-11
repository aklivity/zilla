/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslCredentialsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class AsyncapiClientGeneratorTest
{
    private static final String SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "localhost:9092", "protocol": "kafka" } },
          "channels": {
            "events": { "address": "events", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/events" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } }
          }
        }
        """;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private CatalogHandler catalog;

    private final AsyncapiClientGenerator generator = new AsyncapiClientGenerator();

    private final ToLongFunction<String> resolveId = name -> switch (name)
    {
    case "catalog0" -> 1L;
    case "kafkaGuard0" -> 2L;
    default -> 3L;
    };

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyTypeId(any())).thenReturn(9);
        lenient().when(context.supplyQName(eq(2L))).thenReturn("test:kafkaGuard0");
        lenient().when(catalog.resolve(eq("test"), eq("latest"))).thenReturn(7);
        lenient().when(catalog.resolve(anyInt())).thenReturn(SPEC);
    }

    private BindingConfig binding(
        KafkaAuthorizationConfig authorization)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(CLIENT)
            .options(AsyncapiOptionsConfig.builder()
                .spec(AsyncapiSpecificationConfig.builder()
                    .label("kafka_api")
                    .catalog(new AsyncapiCatalogConfig("catalog0", "test", "latest"))
                    .build())
                .kafka(KafkaOptionsConfig.builder()
                    .authorization(authorization)
                    .build())
                .build())
            .exit("asyncapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private KafkaOptionsConfig kafkaClientOptions(
        AsyncapiCompositeConfig composite)
    {
        BindingConfig kafkaClient = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "kafka_client0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return (KafkaOptionsConfig) kafkaClient.options;
    }

    private KafkaOptionsConfig kafkaCacheClientOptions(
        AsyncapiCompositeConfig composite)
    {
        BindingConfig kafkaCacheClient = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "kafka_cache_client0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return (KafkaOptionsConfig) kafkaCacheClient.options;
    }

    private BindingConfig bindingWithTopicDefaults()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(CLIENT)
            .options(AsyncapiOptionsConfig.builder()
                .spec(AsyncapiSpecificationConfig.builder()
                    .label("kafka_api")
                    .catalog(new AsyncapiCatalogConfig("catalog0", "test", "latest"))
                    .build())
                .kafka(KafkaOptionsConfig.builder()
                    .topic()
                        .name("events")
                        .defaultOffset(KafkaOffsetType.HISTORICAL)
                        .deltaType(KafkaDeltaType.JSON_PATCH)
                        .build()
                    .build())
                .build())
            .exit("asyncapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    @Test
    public void shouldWireKafkaAuthorizationFromOptions()
    {
        KafkaAuthorizationConfig authorization = KafkaAuthorizationConfig.builder()
            .name("kafkaGuard0")
            .credentials(KafkaSaslCredentialsConfig.builder()
                .mechanism("plain")
                .username("alice")
                .password("secret")
                .build())
            .build();

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding(authorization)));

        KafkaOptionsConfig options = kafkaClientOptions(composite);

        assertThat(options.authorization, notNullValue());
        assertThat(options.authorization.name, equalTo("test:kafkaGuard0"));
        assertThat(options.authorization.credentials.username, equalTo("alice"));
    }

    @Test
    public void shouldNotNpeWhenKafkaAuthorizationNotConfigured()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding(null)));

        KafkaOptionsConfig options = kafkaClientOptions(composite);

        assertThat(options.authorization, nullValue());
    }

    @Test
    public void shouldWireKafkaTopicDefaultOffsetAndDeltaTypeFromOptions()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, bindingWithTopicDefaults()));

        KafkaOptionsConfig options = kafkaCacheClientOptions(composite);
        KafkaTopicConfig topic = options.topics.stream()
            .filter(t -> "events".equals(t.name))
            .findFirst()
            .orElseThrow();

        assertThat(topic.defaultOffset, equalTo(KafkaOffsetType.HISTORICAL));
        assertThat(topic.deltaType, equalTo(KafkaDeltaType.JSON_PATCH));
    }

    private static final String PLAIN_KAFKA_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "localhost:9092", "protocol": "kafka" } },
          "channels": {
            "events": { "address": "events", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/events" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } }
          }
        }
        """;

    private static final String SECURE_KAFKA_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "localhost:9092", "protocol": "kafka-secure" } },
          "channels": {
            "events": { "address": "events", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/events" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } }
          }
        }
        """;

    private BindingConfig bindingWithServerOverride(
        String server)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(CLIENT)
            .options(AsyncapiOptionsConfig.builder()
                .spec(AsyncapiSpecificationConfig.builder()
                    .label("kafka_api")
                    .serverOverride(server)
                    .catalog(new AsyncapiCatalogConfig("catalog0", "test", "latest"))
                    .build())
                .build())
            .exit("asyncapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private Optional<BindingConfig> bindingByName(
        AsyncapiCompositeConfig composite,
        String name)
    {
        return composite.namespaces.get(0).bindings.stream()
            .filter(b -> name.equals(b.name))
            .findFirst();
    }

    @Test
    public void shouldExitToTlsClientWhenServerOverrideIsSecureAndSpecProtocolPlain()
    {
        lenient().when(catalog.resolve(anyInt())).thenReturn(PLAIN_KAFKA_SPEC);

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context,
            bindingWithServerOverride("kafka-secure://broker.example.com:9093")));

        Optional<BindingConfig> tlsClient = bindingByName(composite, "tls_client0");
        BindingConfig kafkaClient = bindingByName(composite, "kafka_client0").orElseThrow();

        assertThat(tlsClient.isPresent(), equalTo(true));
        assertThat(kafkaClient.routes.get(0).exit, equalTo("tls_client0"));
    }

    @Test
    public void shouldExitToTcpClientWhenServerOverrideIsPlainAndSpecProtocolSecure()
    {
        lenient().when(catalog.resolve(anyInt())).thenReturn(SECURE_KAFKA_SPEC);

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context,
            bindingWithServerOverride("kafka://broker.example.com:9092")));

        Optional<BindingConfig> tlsClient = bindingByName(composite, "tls_client0");
        BindingConfig kafkaClient = bindingByName(composite, "kafka_client0").orElseThrow();

        assertThat(tlsClient.isPresent(), equalTo(false));
        assertThat(kafkaClient.routes.get(0).exit, equalTo("sys:tcp_client"));
    }

    @Test
    public void shouldSourceKafkaBrokerHostAndPortFromServerOverride()
    {
        lenient().when(catalog.resolve(anyInt())).thenReturn(PLAIN_KAFKA_SPEC);

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context,
            bindingWithServerOverride("kafka://broker.example.com:9093")));

        KafkaOptionsConfig options = kafkaClientOptions(composite);
        List<String> hosts = options.servers.stream().map(s -> s.host).toList();

        assertThat(options.servers, hasSize(1));
        assertThat(hosts, equalTo(List.of("broker.example.com")));
        assertThat(options.servers.get(0).port, equalTo(9093));
        assertThat(generator.deniedOperations(), empty());
    }
}
