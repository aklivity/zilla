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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.List;
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
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.StoreConfig;

public class AsyncapiServerGeneratorTest
{
    private static final String SPEC =
        """
        {
          "asyncapi": "3.0.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": { "plain": { "host": "localhost:1883", "protocol": "mqtt" } },
          "channels": {
            "sensors": { "address": "sensors", "messages": { "event": { "$ref": "#/components/messages/event" } } }
          },
          "operations": {
            "send": { "action": "send", "channel": { "$ref": "#/channels/sensors" } },
            "receive": { "action": "receive", "channel": { "$ref": "#/channels/sensors" } }
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

    private final ToLongFunction<String> resolveId = name -> switch (name)
    {
    case "catalog0" -> 1L;
    case "cluster0" -> 2L;
    default -> 3L;
    };

    @Before
    public void initMocks()
    {
        lenient().when(context.supplyCatalog(eq(1L))).thenReturn(catalog);
        lenient().when(context.supplyBindingId(any(), any())).thenReturn(42L);
        lenient().when(context.supplyQName(eq(2L))).thenReturn("cluster0");
        lenient().when(catalog.resolve(eq("test"), eq("latest"))).thenReturn(7);
        lenient().when(catalog.resolve(anyInt())).thenReturn(SPEC);
    }

    private BindingConfig binding(
        String store)
    {
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("composite0")
            .type("asyncapi")
            .kind(SERVER)
            .options(AsyncapiOptionsConfig.builder()
                .spec(specWithStore(store))
                .build())
            .exit("asyncapi0")
            .build();
        binding.resolveId = resolveId;
        return binding;
    }

    private AsyncapiSpecificationConfig specWithStore(
        String store)
    {
        AsyncapiSpecificationConfigBuilder<AsyncapiSpecificationConfig> spec =
            AsyncapiSpecificationConfig.builder()
                .label("mqtt_api")
                .catalog(new AsyncapiCatalogConfig("catalog0", "test", "latest"));

        if (store != null)
        {
            spec.store(store);
        }

        return spec.build();
    }

    private BindingConfig mqttServerFor(
        AsyncapiCompositeConfig composite)
    {
        return composite.namespaces.get(0).bindings.stream()
            .filter(b -> "mqtt_server0".equals(b.name))
            .findFirst()
            .orElseThrow();
    }

    @Test
    public void shouldUseConfiguredStore()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding("cluster0")));

        BindingConfig mqttServer = mqttServerFor(composite);
        MqttOptionsConfig options = (MqttOptionsConfig) mqttServer.options;

        assertThat(options.store, equalTo("cluster0"));

        NamespaceConfig namespace = composite.namespaces.get(0);
        List<StoreConfig> autoStores = namespace.stores.stream()
            .filter(s -> "mqtt_store0".equals(s.name))
            .toList();
        assertThat(autoStores, empty());
    }

    @Test
    public void shouldDefaultToInMemoryStoreWhenNotConfigured()
    {
        AsyncapiCompositeConfig composite = generator.generate(new AsyncapiBindingConfig(context, binding(null)));

        BindingConfig mqttServer = mqttServerFor(composite);
        MqttOptionsConfig options = (MqttOptionsConfig) mqttServer.options;

        assertThat(options.store, equalTo("mqtt_store0"));

        NamespaceConfig namespace = composite.namespaces.get(0);
        List<StoreConfig> autoStores = namespace.stores.stream()
            .filter(s -> "mqtt_store0".equals(s.name))
            .toList();
        assertThat(autoStores, hasSize(1));
        assertThat(autoStores.get(0).type, equalTo("memory"));
    }
}
