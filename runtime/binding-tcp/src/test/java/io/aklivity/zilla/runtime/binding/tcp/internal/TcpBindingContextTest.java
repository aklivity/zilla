/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKER_CAPACITY_LIMIT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Properties;
import java.util.function.LongConsumer;

import org.agrona.collections.Int2ObjectHashMap;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public class TcpBindingContextTest
{
    private static final int BUFFER_CAPACITY = 1024;

    private final Int2ObjectHashMap<TcpBindingContext> peers = new Int2ObjectHashMap<>();

    @Test
    public void shouldRecordRebalancedWhenHandingOffToPeer() throws IOException
    {
        final EngineContext fullContext = engineContext(0);
        final LongConsumer recordRebalanced = mock(LongConsumer.class);
        when(fullContext.supplyRebalancedMetric()).thenReturn(recordRebalanced);
        final EngineContext peerContext = engineContext(1);

        final TcpBindingContext full = bindingContext(fullContext, 0);
        final TcpBindingContext peer = bindingContext(peerContext, 8);
        peers.put(0, full);
        peers.put(1, peer);

        try (SocketChannel channel = SocketChannel.open())
        {
            assertTrue(full.spill(1L, channel));
        }

        verify(recordRebalanced).accept(1L);
        verify(peerContext).dispatch(any(Runnable.class));
    }

    @Test
    public void shouldNotRecordRebalancedWhenKeepingConnection() throws IOException
    {
        final EngineContext keepContext = engineContext(0);
        final LongConsumer recordRebalanced = mock(LongConsumer.class);
        when(keepContext.supplyRebalancedMetric()).thenReturn(recordRebalanced);
        final EngineContext peerContext = engineContext(1);

        final TcpBindingContext keep = bindingContext(keepContext, 8);
        final TcpBindingContext peer = bindingContext(peerContext, 8);
        peers.put(0, keep);
        peers.put(1, peer);

        try (SocketChannel channel = SocketChannel.open())
        {
            assertFalse(keep.spill(1L, channel));
        }

        verify(recordRebalanced, never()).accept(anyLong());
        verify(peerContext, never()).dispatch(any(Runnable.class));
    }

    private TcpBindingContext bindingContext(
        EngineContext context,
        int capacity)
    {
        final Properties properties = new Properties();
        properties.setProperty(ENGINE_WORKER_CAPACITY_LIMIT.name(), Integer.toString(capacity));

        return new TcpBindingContext(new TcpConfiguration(new Configuration(properties)), context, peers);
    }

    private static EngineContext engineContext(
        int index)
    {
        final EngineContext context = mock(EngineContext.class);
        final BufferPool bufferPool = mock(BufferPool.class);

        when(bufferPool.slotCapacity()).thenReturn(BUFFER_CAPACITY);
        when(context.index()).thenReturn(index);
        when(context.affinityMask(anyLong())).thenReturn(-1L);
        when(context.writeBuffer()).thenReturn(new UnsafeBufferEx(ByteBuffer.allocateDirect(BUFFER_CAPACITY)));
        when(context.bufferPool()).thenReturn(bufferPool);
        when(context.supplyUtilizationMetric()).thenReturn(mock(LongConsumer.class));
        when(context.supplyRebalancedMetric()).thenReturn(mock(LongConsumer.class));

        return context;
    }
}
