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

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.config.binding.tls.TlsConditionConfig;
import io.aklivity.zilla.config.binding.tls.TlsOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttPatternConfig.MqttConnectProperty;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

/**
 * {@code shouldUseConfiguredStore}, {@code shouldDefaultToInMemoryStoreWhenNotConfigured}, and
 * {@code shouldApplyOverlayBeforeGeneratingRoutes} have been replaced by k3po ITs in
 * {@code AsyncapiServerIT} ({@code shouldPublishAndSubscribe}, {@code shouldPublishAndSubscribeWithStore},
 * {@code shouldPublishToChannelAddedByOverlay}). This test is kept as a unit test because proving TLS SNI
 * authority routing via IT would require a genuine terminating-TLS k3po handshake (a fresh PKCS12 trust
 * store and the {@code tls:} k3po transport, unused elsewhere in this spec module) for a single
 * field-population assertion whose underlying SNI-matching mechanics are already exhaustively covered by
 * binding-tls's own test suite.
 */
public class AsyncapiServerGeneratorTest
{
    private static final String SECURE_MQTT_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "secure": { "host": "broker.example.com:8883", "protocol": "mqtts" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } }
          }
        }
        """;

    private static final String SECURE_HTTP_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "secure": { "host": "api.example.com:443", "protocol": "https" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } }
          }
        }
        """;

    private static final String HTTP_BEARER_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "secure": { "host": "api.example.com:8080", "protocol": "http" } },
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

    private static final String HTTP_API_KEY_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "secure": { "host": "api.example.com:8080", "protocol": "http" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } },
            "securitySchemes": {
              "apiKeyAuth": { "type": "httpApiKey", "name": "X-Api-Key", "in": "header" }
            }
          }
        }
        """;

    private static final String UNSUPPORTED_SCHEME_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "secure": { "host": "api.example.com:8080", "protocol": "http" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } },
            "securitySchemes": { "gssapiAuth": { "type": "gssapi" } }
          }
        }
        """;

    private static final String MQTT_USER_PASSWORD_SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "secure": { "host": "broker.example.com:8883", "protocol": "mqtts" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } }
          },
          "components": {
            "messages": { "event": { "payload": { "type": "object" } } },
            "securitySchemes": {
              "mqttUser": { "type": "apiKey", "in": "user" },
              "mqttPassword": { "type": "apiKey", "in": "password" }
            }
          }
        }
        """;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private CatalogHandler catalog;

    private final AsyncapiServerGenerator generator = new AsyncapiServerGenerator();

    private AsyncapiCompositeConfig generateSecure(
        String specJson,
        String serverOverride)
    {
        return generateSecure(specJson, serverOverride, null);
    }

    private AsyncapiCompositeConfig generateSecure(
        String specJson,
        String serverOverride,
        Map<String, String> security)
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(catalog.resolve(eq("secure"), eq("latest"))).thenReturn(8);
        lenient().when(catalog.resolve(8)).thenReturn(specJson);
        lenient().when(context.supplyQName(eq(3L))).thenReturn("guard0");

        AsyncapiSpecificationConfigBuilder<AsyncapiSpecificationConfig> specBuilder = AsyncapiSpecificationConfig.builder();
        specBuilder
            .label("api")
            .catalog(new AsyncapiCatalogConfig("catalog0", "secure", "latest"))
            .serverOverride(serverOverride);

        if (security != null)
        {
            security.forEach(specBuilder::security);
        }

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(SERVER)
            .options(AsyncapiOptionsConfig.builder()
                .spec(specBuilder.build())
                .build())
            .exit("asyncapi0")
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        default -> 3L;
        };

        return generator.generate(new AsyncapiBindingConfig(context, binding));
    }

    private BindingConfig tlsServerOf(
        AsyncapiCompositeConfig composite)
    {
        return composite.namespaces.get(0).bindings.stream()
            .filter(b -> "tls_server0".equals(b.name))
            .findFirst()
            .orElseThrow();
    }

    private BindingConfig httpServerOf(
        AsyncapiCompositeConfig composite)
    {
        return composite.namespaces.get(0).bindings.stream()
            .filter(b -> "http_server0".equals(b.name))
            .findFirst()
            .orElseThrow();
    }

    private BindingConfig mqttServerOf(
        AsyncapiCompositeConfig composite)
    {
        return composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mqtt_server0".equals(b.name))
            .findFirst()
            .orElseThrow();
    }

    @Test
    public void shouldPopulateTlsAuthorityForSniRouting()
    {
        AsyncapiCompositeConfig composite = generateSecure(SECURE_MQTT_SPEC, "mqtts://broker.example.com:8883");

        BindingConfig tlsServer = tlsServerOf(composite);
        TlsConditionConfig when = (TlsConditionConfig) tlsServer.routes.get(0).when.get(0);

        assertThat(when.authority, equalTo("broker.example.com"));
    }

    @Test
    public void shouldNotSetTlsAlpnForMqttOnlySpec()
    {
        AsyncapiCompositeConfig composite = generateSecure(SECURE_MQTT_SPEC, "mqtts://broker.example.com:8883");

        TlsOptionsConfig options = (TlsOptionsConfig) tlsServerOf(composite).options;

        assertThat(options.alpn, nullValue());
    }

    @Test
    public void shouldComputeTlsAlpnForHttpsSpec()
    {
        AsyncapiCompositeConfig composite = generateSecure(SECURE_HTTP_SPEC, "https://api.example.com:443");

        TlsOptionsConfig options = (TlsOptionsConfig) tlsServerOf(composite).options;

        assertThat(options.alpn, contains("h2", "http/1.1"));
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromBearerScheme()
    {
        AsyncapiCompositeConfig composite = generateSecure(HTTP_BEARER_SPEC, "http://api.example.com:8080",
            Map.of("bearerAuth", "guard0"));

        HttpAuthorizationConfig authorization = ((HttpOptionsConfig) httpServerOf(composite).options).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.name, equalTo("guard0"));
        assertThat(authorization.credentials.headers, hasSize(1));
        assertThat(authorization.credentials.headers.get(0).name, equalTo("authorization"));
        assertThat(authorization.credentials.headers.get(0).pattern, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldSynthesizeHttpAuthorizationFromApiKeyScheme()
    {
        AsyncapiCompositeConfig composite = generateSecure(HTTP_API_KEY_SPEC, "http://api.example.com:8080",
            Map.of("apiKeyAuth", "guard0"));

        HttpAuthorizationConfig authorization = ((HttpOptionsConfig) httpServerOf(composite).options).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.name, equalTo("guard0"));
        assertThat(authorization.credentials.headers, hasSize(1));
        assertThat(authorization.credentials.headers.get(0).name, equalTo("X-Api-Key"));
        assertThat(authorization.credentials.headers.get(0).pattern, equalTo("{credentials}"));
    }

    @Test
    public void shouldNotSynthesizeHttpAuthorizationWhenSecurityMapAbsent()
    {
        AsyncapiCompositeConfig composite = generateSecure(HTTP_BEARER_SPEC, "http://api.example.com:8080");

        assertThat(((HttpOptionsConfig) httpServerOf(composite).options).authorization, nullValue());
    }

    @Test
    public void shouldSynthesizeMqttAuthorizationFromUserAndPasswordSchemes()
    {
        AsyncapiCompositeConfig composite = generateSecure(MQTT_USER_PASSWORD_SPEC, "mqtts://broker.example.com:8883",
            Map.of("mqttUser", "guard0", "mqttPassword", "guard0"));

        MqttAuthorizationConfig authorization = ((MqttOptionsConfig) mqttServerOf(composite).options).authorization;

        assertThat(authorization, notNullValue());
        assertThat(authorization.name, equalTo("guard0"));
        assertThat(authorization.credentials.connect, hasSize(2));
        assertThat(authorization.credentials.connect.stream().map(p -> p.property).toList(),
            containsInAnyOrder(MqttConnectProperty.USERNAME, MqttConnectProperty.PASSWORD));
        assertThat(authorization.credentials.connect.stream().map(p -> p.pattern).distinct().toList(),
            equalTo(List.of("{credentials}")));
    }

    @Test
    public void shouldNotSynthesizeMqttAuthorizationWhenSecurityMapAbsent()
    {
        AsyncapiCompositeConfig composite = generateSecure(MQTT_USER_PASSWORD_SPEC, "mqtts://broker.example.com:8883");

        assertThat(((MqttOptionsConfig) mqttServerOf(composite).options).authorization, nullValue());
    }

    @Test
    public void shouldDenyUnsupportedSecuritySchemeType()
    {
        generateSecure(UNSUPPORTED_SCHEME_SPEC, "http://api.example.com:8080", Map.of("gssapiAuth", "guard0"));

        assertThat(generator.deniedOperations(), hasSize(1));
    }

    @Test
    public void shouldDenyUnresolvableSecurityScheme()
    {
        generateSecure(HTTP_BEARER_SPEC, "http://api.example.com:8080", Map.of("missingScheme", "guard0"));

        assertThat(generator.deniedOperations(), hasSize(1));
    }
}
