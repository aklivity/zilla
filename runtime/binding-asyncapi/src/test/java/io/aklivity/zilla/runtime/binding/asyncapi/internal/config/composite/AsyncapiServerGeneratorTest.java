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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

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
    private static final String SECURE_SPEC =
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

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private EngineContext context;

    @Mock
    private CatalogHandler catalog;

    private final AsyncapiServerGenerator generator = new AsyncapiServerGenerator();

    @Test
    public void shouldPopulateTlsAuthorityForSniRouting()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(catalog.resolve(eq("secure"), eq("latest"))).thenReturn(8);
        lenient().when(catalog.resolve(8)).thenReturn(SECURE_SPEC);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(SERVER)
            .options(AsyncapiOptionsConfig.builder()
                .spec(AsyncapiSpecificationConfig.builder()
                    .label("mqtt_api")
                    .catalog(new AsyncapiCatalogConfig("catalog0", "secure", "latest"))
                    .build())
                .build())
            .exit("asyncapi0")
            .build();
        binding.resolveId = name -> switch (name)
        {
        case "catalog0" -> 1L;
        default -> 3L;
        };

        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding));

        BindingConfig tlsServer = composite.namespaces.get(0).bindings.stream()
            .filter(b -> "tls_server0".equals(b.name))
            .findFirst()
            .orElseThrow();

        TlsConditionConfig when = (TlsConditionConfig) tlsServer.routes.get(0).when.get(0);

        assertThat(when.authority, equalTo("broker.example.com"));
    }
}
