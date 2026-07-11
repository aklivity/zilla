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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;
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
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiConditionConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiWithConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class AsyncapiProxyGeneratorTest
{
    private static final String SSE_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "app": { "host": "localhost:8080", "protocol": "http" } },
          "channels": {
            "mappedEvents": { "address": "/mapped", "messages": { "event": { "$ref": "#/components/messages/event" } } },
            "unmappedEvents": { "address": "/unmapped", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "mapped": {
              "action": "receive",
              "channel": { "$ref": "#/channels/mappedEvents" },
              "messages": [ { "$ref": "#/channels/mappedEvents/messages/event" } ],
              "security": [ { "$ref": "#/components/securitySchemes/bearerAuth" } ],
              "bindings": {
                "x-zilla-sse": {},
                "x-zilla-sse-kafka": { "filters": [ { "key": "{identity}" } ] }
              }
            },
            "unmapped": {
              "action": "receive",
              "channel": { "$ref": "#/channels/unmappedEvents" },
              "messages": [ { "$ref": "#/channels/unmappedEvents/messages/event" } ],
              "security": [ { "$ref": "#/components/securitySchemes/oauthScheme" } ],
              "bindings": {
                "x-zilla-sse": {},
                "x-zilla-sse-kafka": { "filters": [ { "key": "{identity}" } ] }
              }
            }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } },
            "securitySchemes": {
              "bearerAuth": { "type": "http", "scheme": "bearer" },
              "oauthScheme": { "type": "oauth2", "flows": {} }
            }
          }
        }
        """;

    private static final String KAFKA_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "localhost:9092", "protocol": "kafka" } },
          "channels": {
            "mapped": { "address": "mapped", "messages": { "event": { "$ref": "#/components/messages/event" } } },
            "unmapped": { "address": "unmapped", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "mapped": {
              "action": "receive",
              "channel": { "$ref": "#/channels/mapped" },
              "messages": [ { "$ref": "#/channels/mapped/messages/event" } ]
            },
            "unmapped": {
              "action": "receive",
              "channel": { "$ref": "#/channels/unmapped" },
              "messages": [ { "$ref": "#/channels/unmapped/messages/event" } ]
            }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } }
          }
        }
        """;

    private static final String MQTT_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "localhost:1883", "protocol": "mqtt" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "sendEvents": {
              "action": "send",
              "channel": { "$ref": "#/channels/sensors" },
              "messages": [ { "$ref": "#/channels/sensors/messages/event" } ]
            }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } }
          }
        }
        """;

    private static final String MQTT_KAFKA_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "broker": { "host": "localhost:9092", "protocol": "kafka" } },
          "channels": {
            "sensorData": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } },
            "mqttSessions": { "address": "mqtt-sessions", "x-zilla-mqtt-kafka": { "role": "sessions" } },
            "mqttMessages": { "address": "mqtt-messages", "x-zilla-mqtt-kafka": { "role": "messages" } },
            "mqttRetained": { "address": "mqtt-retained", "x-zilla-mqtt-kafka": { "role": "retained" } }
          },
          "operations": {
            "toSensorData": {
              "action": "send",
              "channel": { "$ref": "#/channels/sensorData" },
              "messages": [ { "$ref": "#/channels/sensorData/messages/event" } ]
            }
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
    private CatalogHandler sseCatalog;

    @Mock
    private CatalogHandler kafkaCatalog;

    @Mock
    private CatalogHandler mqttCatalog;

    @Mock
    private CatalogHandler mqttKafkaCatalog;

    private final AsyncapiProxyGenerator generator = new AsyncapiProxyGenerator();

    private final ToLongFunction<String> resolveId = name -> switch (name)
    {
    case "catalog0" -> 1L;
    case "catalog1" -> 5L;
    case "catalog2" -> 6L;
    case "catalog3" -> 10L;
    case "guard0" -> 2L;
    default -> 3L;
    };

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(sseCatalog);
        lenient().when(context.supplyCatalog(eq(5L))).thenReturn(kafkaCatalog);
        lenient().when(context.supplyCatalog(eq(6L))).thenReturn(mqttCatalog);
        lenient().when(context.supplyCatalog(eq(10L))).thenReturn(mqttKafkaCatalog);
        lenient().when(context.supplyTypeId(any())).thenReturn(9);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyQName(eq(2L))).thenReturn("guard0");
        lenient().when(sseCatalog.resolve(eq("test"), eq("latest"))).thenReturn(7);
        lenient().when(sseCatalog.resolve(anyInt())).thenReturn(SSE_SPEC);
        lenient().when(kafkaCatalog.resolve(eq("test"), eq("latest"))).thenReturn(8);
        lenient().when(kafkaCatalog.resolve(anyInt())).thenReturn(KAFKA_SPEC);
        lenient().when(mqttCatalog.resolve(eq("test"), eq("latest"))).thenReturn(11);
        lenient().when(mqttCatalog.resolve(anyInt())).thenReturn(MQTT_SPEC);
        lenient().when(mqttKafkaCatalog.resolve(eq("test"), eq("latest"))).thenReturn(12);
        lenient().when(mqttKafkaCatalog.resolve(anyInt())).thenReturn(MQTT_KAFKA_SPEC);
    }

    private BindingConfig binding(
        Map<String, String> security)
    {
        AsyncapiSpecificationConfigBuilder<AsyncapiSpecificationConfig> sseSpec = AsyncapiSpecificationConfig.builder()
            .label("sse-id")
            .catalog(new AsyncapiCatalogConfig("catalog0", "test", "latest"));
        if (security != null)
        {
            security.forEach(sseSpec::security);
        }

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(PROXY)
            .options(AsyncapiOptionsConfig.builder()
                .spec(sseSpec.build())
                .spec(AsyncapiSpecificationConfig.builder()
                    .label("kafka-id")
                    .catalog(new AsyncapiCatalogConfig("catalog1", "test", "latest"))
                    .build())
                .build())
            .route()
                .exit("kafka_client0")
                .when(new AsyncapiConditionConfig("sse-id", null, null))
                .with(new AsyncapiWithConfig("kafka-id", null))
                .build()
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private RouteConfig routeFor(
        AsyncapiCompositeConfig composite,
        String path)
    {
        BindingConfig sseKafka = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "sse_kafka_proxy0".equals(b.name))
            .findFirst()
            .orElseThrow();

        return sseKafka.routes.stream()
            .filter(r -> r.when.stream()
                .anyMatch(c -> path.equals(((SseKafkaConditionConfig) c).path)))
            .findFirst()
            .orElseThrow();
    }

    @Test
    public void shouldGuardMappedOperation()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/mapped");
        List<GuardedConfig> guarded = route.guarded;

        assertThat(guarded, hasSize(1));
        assertThat(guarded.get(0).name, equalTo("guard0"));
        assertThat(guarded.get(0).roles, empty());
    }

    @Test
    public void shouldResolveIdentityUsingMappedGuard()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/mapped");
        SseKafkaWithConfig with = (SseKafkaWithConfig) route.with;

        assertThat(with.filters.get(), hasSize(1));
        assertThat(with.filters.get().get(0).key.get(), equalTo("${guarded['guard0'].identity}"));
    }

    @Test
    public void shouldLeaveIdentityUnresolvedWhenSchemeNotMapped()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding(
            Map.of("bearerAuth", "guard0"))));

        RouteConfig route = routeFor(composite, "/unmapped");
        SseKafkaWithConfig with = (SseKafkaWithConfig) route.with;

        assertThat(route.guarded, empty());
        assertThat(with.filters.get().get(0).key.get(), equalTo("{identity}"));
    }

    @Test
    public void shouldAllowUnguardedWhenSecurityMapAbsent()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding(null)));

        RouteConfig mapped = routeFor(composite, "/mapped");
        RouteConfig unmapped = routeFor(composite, "/unmapped");

        assertThat(mapped.guarded, empty());
        assertThat(unmapped.guarded, empty());
        assertThat(generator.deniedOperations(), empty());
    }

    @Test
    public void shouldResolveMqttKafkaTopicsFromChannelRoles()
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(PROXY)
            .options(AsyncapiOptionsConfig.builder()
                .spec()
                    .label("mqtt-id")
                    .catalog()
                        .name("catalog2")
                        .subject("test")
                        .version("latest")
                        .build()
                    .build()
                .spec()
                    .label("kafka-mqtt-id")
                    .catalog()
                        .name("catalog3")
                        .subject("test")
                        .version("latest")
                        .build()
                    .build()
                .build())
            .route()
                .exit("kafka_client0")
                .when(new AsyncapiConditionConfig("mqtt-id", "sendEvents", null))
                .with(new AsyncapiWithConfig("kafka-mqtt-id", "toSensorData"))
                .build()
            .build();
        binding.resolveId = resolveId;

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding));

        BindingConfig mqttKafka = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mqtt_kafka_proxy0".equals(b.name))
            .findFirst()
            .orElseThrow();
        MqttKafkaOptionsConfig options = (MqttKafkaOptionsConfig) mqttKafka.options;

        assertThat(options.topics.sessions.asString(), equalTo("mqtt-sessions"));
        assertThat(options.topics.messages.asString(), equalTo("mqtt-messages"));
        assertThat(options.topics.retained.asString(), equalTo("mqtt-retained"));
    }
}
