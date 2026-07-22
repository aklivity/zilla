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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite;

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig.MqttConnectProperty;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

/**
 * Kept as a unit test rather than converted to a k3po IT (AsyncapiClientIT). The kafka/http/mqtt tests here
 * assert on generated client config wiring (synthesized SASL/HTTP/MQTT authorization, TLS-vs-TCP exit
 * selection, host/port parsed from {@code serverOverride}) that only takes effect on the generated client's
 * real outbound TCP/TLS connection or SASL handshake. {@code AsyncapiClientIT}'s existing scenario
 * ({@code shouldProduceMessage}) connects the composite-side script directly to the internal merged-Kafka
 * entry point via {@code option zilla:ephemeral "test:composite0/kafka_api"}, bypassing {@code kafka_client0}'s
 * entire exit chain (no real TCP/TLS handshake, no SASL negotiation, no host/port resolution ever happens) —
 * intentionally, since simulating genuine Kafka SASL handshake bytes and a real TLS-terminating listener
 * has no established pattern anywhere in this spec module and is a materially larger, more fragile
 * undertaking than proving generation-time config correctness directly here.
 */
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

    private KafkaOptionsConfig kafkaClientOptions(
        AsyncapiCompositeConfig composite)
    {
        BindingConfig kafkaClient = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "kafka_client0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return (KafkaOptionsConfig) kafkaClient.options;
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

    private BindingConfig bindingWithServerOverrideAndSecurity(
        String server,
        Map<String, String> security)
    {
        AsyncapiSpecificationConfigBuilder<AsyncapiSpecificationConfig> specBuilder = AsyncapiSpecificationConfig.builder();
        specBuilder
            .label("kafka_api")
            .serverOverride(server)
            .catalog(new AsyncapiCatalogConfig("catalog0", "test", "latest"));
        security.forEach(specBuilder::security);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(CLIENT)
            .options(AsyncapiOptionsConfig.builder()
                .spec(specBuilder.build())
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
        assertThat(tlsClient.get().options, nullValue());
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

    private static final String HTTP_BEARER_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "api": { "host": "api.example.com:8080", "protocol": "http" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } },
            "securitySchemes": { "bearerAuth": { "type": "http", "scheme": "bearer" } }
          }
        }
        """;

    private static final String KAFKA_SCRAM_SPEC =
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
            "messages": { "event": { "payload": { "type": "object" } } },
            "securitySchemes": { "saslScram": { "type": "scramSha256" } }
          }
        }
        """;

    private static final String MQTT_USER_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "broker.example.com:1883", "protocol": "mqtt" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } },
            "securitySchemes": { "mqttUser": { "type": "apiKey", "in": "user" } }
          }
        }
        """;

    @Test
    public void shouldSynthesizeHttpClientAuthorizationFromBearerScheme()
    {
        lenient().when(catalog.resolve(anyInt())).thenReturn(HTTP_BEARER_SPEC);

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context,
            bindingWithServerOverrideAndSecurity("http://api.example.com:8080", Map.of("bearerAuth", "kafkaGuard0"))));

        HttpOptionsConfig options = (HttpOptionsConfig) bindingByName(composite, "http_client0").orElseThrow().options;

        assertThat(options.authorization, notNullValue());
        assertThat(options.authorization.name, equalTo("test:kafkaGuard0"));
        assertThat(options.authorization.credentials.headers, hasSize(1));
        assertThat(options.authorization.credentials.headers.get(0).name, equalTo("authorization"));
        assertThat(options.authorization.credentials.headers.get(0).pattern, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldSynthesizeKafkaAuthorizationFromScramScheme()
    {
        lenient().when(catalog.resolve(anyInt())).thenReturn(KAFKA_SCRAM_SPEC);

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context,
            bindingWithServerOverrideAndSecurity("kafka-secure://localhost:9092", Map.of("saslScram", "kafkaGuard0"))));

        KafkaOptionsConfig options = kafkaClientOptions(composite);

        assertThat(options.authorization, notNullValue());
        assertThat(options.authorization.name, equalTo("test:kafkaGuard0"));
        assertThat(options.authorization.credentials.mechanism, equalTo("scram-sha-256"));
        assertThat(options.authorization.credentials.username, equalTo("{identity}"));
        assertThat(options.authorization.credentials.password, equalTo("{credentials}"));
    }

    @Test
    public void shouldNotSynthesizeKafkaAuthorizationWhenSecurityMapAbsent()
    {
        lenient().when(catalog.resolve(anyInt())).thenReturn(KAFKA_SCRAM_SPEC);

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context,
            bindingWithServerOverride("kafka-secure://localhost:9092")));

        KafkaOptionsConfig options = kafkaClientOptions(composite);

        assertThat(options.authorization, nullValue());
    }

    @Test
    public void shouldSynthesizeMqttClientAuthorizationFromUserScheme()
    {
        lenient().when(catalog.resolve(anyInt())).thenReturn(MQTT_USER_SPEC);

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context,
            bindingWithServerOverrideAndSecurity("mqtt://broker.example.com:1883", Map.of("mqttUser", "kafkaGuard0"))));

        MqttOptionsConfig options = (MqttOptionsConfig) bindingByName(composite, "mqtt_client0").orElseThrow().options;

        assertThat(options.authorization, notNullValue());
        assertThat(options.authorization.name, equalTo("test:kafkaGuard0"));
        assertThat(options.authorization.credentials.connect, hasSize(1));
        assertThat(options.authorization.credentials.connect.get(0).property, equalTo(MqttConnectProperty.USERNAME));
        assertThat(options.authorization.credentials.connect.get(0).pattern, equalTo("{credentials}"));
    }
}
