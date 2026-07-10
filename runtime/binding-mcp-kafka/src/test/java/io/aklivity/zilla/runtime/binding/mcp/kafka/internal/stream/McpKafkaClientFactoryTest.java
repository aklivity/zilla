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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaBindingConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class McpKafkaClientFactoryTest
{
    private static final long BINDING_ID = 1L;
    private static final long CACHE_CLIENT_BINDING_ID = 42L;

    private final EngineContext context = mock(EngineContext.class);
    private final BindingHandler streamFactory = mock(BindingHandler.class);
    private final Signaler signaler = mock(Signaler.class);
    private final AtomicLong supplyId = new AtomicLong(100L);

    private McpKafkaClientFactory factory;

    @Before
    public void setup()
    {
        when(context.writeBuffer()).thenReturn(new UnsafeBufferEx(new byte[65536]));
        when(context.streamFactory()).thenReturn(streamFactory);
        when(context.signaler()).thenReturn(signaler);
        when(context.supplyTypeId("mcp")).thenReturn(1);
        when(context.supplyTypeId("kafka")).thenReturn(2);
        when(context.supplyInitialId(anyLong())).thenAnswer(inv -> supplyId.getAndIncrement());
        when(context.supplyReplyId(anyLong())).thenAnswer(inv -> ((long) inv.getArgument(0)) | 0x01L);
        when(context.supplyBindingId(any(NamespaceConfig.class), any(BindingConfig.class)))
            .thenAnswer(inv -> "kafka_cache_client0".equals(((BindingConfig) inv.getArgument(1)).name)
                ? CACHE_CLIENT_BINDING_ID
                : 0L);
        when(context.bufferPool()).thenReturn(new TestBufferPool(65536));

        this.factory = new McpKafkaClientFactory(new McpKafkaConfiguration(), context);
    }

    @Test
    public void shouldPatchRouteIdOnAttach()
    {
        BindingConfig binding = newBinding();

        factory.attach(binding);

        McpKafkaBindingConfig attached = factory.bindings.get(BINDING_ID);
        assertThat(attached.routes.get(0).id, equalTo(CACHE_CLIENT_BINDING_ID));

        verify(context, times(1)).attachComposite(any(NamespaceConfig.class));
    }

    @Test
    public void shouldDetachComposite()
    {
        BindingConfig binding = newBinding();

        factory.attach(binding);
        factory.detach(BINDING_ID);

        verify(context, times(1)).detachComposite(any(NamespaceConfig.class));
        assertThat(factory.bindings.get(BINDING_ID), nullValue());
    }

    private BindingConfig newBinding()
    {
        McpKafkaOptionsConfig options = McpKafkaOptionsConfig.builder()
            .server()
                .host("localhost")
                .port(9092)
                .build()
            .build();

        RouteConfig route = RouteConfig.builder()
            .when(new McpKafkaConditionConfig("produce", null, null))
            .build();

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.CLIENT)
            .options(options)
            .routes(List.of(route))
            .build();
        binding.id = BINDING_ID;

        return binding;
    }
}
