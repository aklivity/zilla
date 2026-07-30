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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.http.McpHttpBodyConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpConditionConfig;
import io.aklivity.zilla.config.binding.mcp.http.McpHttpWithConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

// Regression coverage for the crash fixed alongside issue #2216: a reply-direction WINDOW arriving
// on a tools/call stream that was rejected before ever opening the upstream HTTP request used to
// forward straight to HttpProxy.doWindow with a still-null receiver (HttpProxy.receiver is only
// assigned once doHttpBegin actually opens the upstream stream), crashing the engine worker.
public class McpHttpProxyFactoryTest
{
    private static final int MCP_TYPE_ID = 1;
    private static final long BINDING_ID = 1L;
    private static final long ROUTE_ID = 2L;
    private static final long ORIGIN_ID = 3L;
    private static final long LIFECYCLE_INITIAL_ID = 9L;
    private static final long AFFINITY = 0L;
    private static final long AUTHORIZATION = 0L;

    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[65536]);
    private final MutableDirectBufferEx scratch = new UnsafeBufferEx(new byte[65536]);
    private final MutableDirectBufferEx extScratch = new UnsafeBufferEx(new byte[65536]);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final BeginFW beginRO = new BeginFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final McpResetExFW mcpResetExRO = new McpResetExFW();

    private final AtomicLong supplyId = new AtomicLong(100L);

    private final EngineContext context = mock(EngineContext.class);
    private final BindingHandler streamFactory = mock(BindingHandler.class);
    private final MessageConsumer mcp = mock(MessageConsumer.class);

    private final List<Recorded> mcpSent = new ArrayList<>();

    private McpHttpProxyFactory factory;

    @Before
    public void setup() throws Exception
    {
        when(context.writeBuffer()).thenReturn(writeBuffer);
        when(context.streamFactory()).thenReturn(streamFactory);
        when(context.supplyTypeId("mcp")).thenReturn(MCP_TYPE_ID);
        when(context.supplyTypeId("http")).thenReturn(2);
        when(context.supplyInitialId(anyLong())).thenAnswer(inv -> supplyId.getAndIncrement());
        when(context.supplyReplyId(anyLong())).thenAnswer(inv -> ((long) inv.getArgument(0)) | 0x01L);
        when(context.isLocalIndex(anyLong(), anyInt())).thenReturn(true);
        when(context.bufferPool()).thenReturn(new TestBufferPool(65536));

        doAnswer(inv -> record(mcpSent, inv)).when(mcp).accept(anyInt(), any(), anyInt(), anyInt());

        this.factory = new McpHttpProxyFactory(new McpHttpConfiguration(), context);
    }

    private static Void record(
        List<Recorded> sink,
        org.mockito.invocation.InvocationOnMock invocation)
    {
        final int typeId = invocation.getArgument(0);
        final DirectBufferEx buffer = invocation.getArgument(1);
        final int offset = invocation.getArgument(2);
        final int length = invocation.getArgument(3);

        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes, 0, length);
        sink.add(new Recorded(typeId, bytes));

        return null;
    }

    private static final class Recorded
    {
        private final int typeId;
        private final byte[] bytes;

        private Recorded(
            int typeId,
            byte[] bytes)
        {
            this.typeId = typeId;
            this.bytes = bytes;
        }
    }

    private BindingConfig newBinding()
    {
        final McpHttpWithConfig with = McpHttpWithConfig.builder()
            .header(":method", "PATCH")
            .header(":path", "/repos/${args.owner}/${args.repo}/issues")
            .body(McpHttpBodyConfig.builder()
                .template(Map.of("state", "${args.state}"))
                .build())
            .build();

        final RouteConfig route = RouteConfig.builder()
            .exit("http0")
            .when(McpHttpConditionConfig.builder()
                .tool("update_issue")
                .build())
            .with(with)
            .build();
        route.id = ROUTE_ID;
        route.authorized = (authorization, credentials) -> true;

        final BindingConfig binding = GenericBindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp_http")
            .kind(KindConfig.PROXY)
            .routes(List.of(route))
            .build();
        binding.id = BINDING_ID;

        return binding;
    }

    private MessageConsumer beginLifecycle()
    {
        final McpBeginExFW beginEx = mcpBeginExRW.wrap(extScratch, 0, extScratch.capacity())
            .typeId(MCP_TYPE_ID)
            .lifecycle(l -> l.capabilities(0))
            .build();

        final BeginFW begin = beginRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(BINDING_ID)
            .streamId(LIFECYCLE_INITIAL_ID)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(1L)
            .authorization(AUTHORIZATION)
            .affinity(AFFINITY)
            .extension(beginEx.buffer(), beginEx.offset(), beginEx.sizeof())
            .build();

        final MessageConsumer stream = factory.newStream(
            begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), mcp);

        if (stream != null)
        {
            stream.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        return stream;
    }

    private String capturedSessionId()
    {
        final Recorded recorded = nthOf(mcpSent, BeginFW.TYPE_ID, 1);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(recorded.bytes);
        final BeginFW begin = beginRO.wrap(buffer, 0, recorded.bytes.length);
        final McpBeginExFW beginEx = mcpBeginExRO.wrap(begin.extension().buffer(), begin.extension().offset(),
            begin.extension().limit());

        return beginEx.lifecycle().sessionId().asString();
    }

    private MessageConsumer beginToolsCall(
        String sessionId,
        int contentLength)
    {
        final McpBeginExFW beginEx = mcpBeginExRW.wrap(extScratch, 0, extScratch.capacity())
            .typeId(MCP_TYPE_ID)
            .toolsCall(t -> t
                .sessionId(sessionId)
                .name("update_issue")
                .contentLength(contentLength))
            .build();

        final long initialId = supplyId.get();
        final BeginFW begin = beginRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(BINDING_ID)
            .streamId(initialId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(1L)
            .authorization(AUTHORIZATION)
            .affinity(AFFINITY)
            .extension(beginEx.buffer(), beginEx.offset(), beginEx.sizeof())
            .build();

        final MessageConsumer stream = factory.newStream(
            begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), mcp);

        if (stream != null)
        {
            stream.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        return stream;
    }

    private void data(
        MessageConsumer stream,
        long streamId,
        String payload)
    {
        final byte[] bytes = payload.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

        final DataFW data = dataRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(BINDING_ID)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(1L)
            .authorization(AUTHORIZATION)
            .flags(0x03)
            .budgetId(0L)
            .reserved(bytes.length)
            .payload(buffer, 0, bytes.length)
            .build();

        stream.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void window(
        MessageConsumer stream,
        long streamId,
        long acknowledge,
        int maximum)
    {
        final WindowFW window = windowRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(ROUTE_ID)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(1L)
            .authorization(AUTHORIZATION)
            .budgetId(0L)
            .padding(0)
            .build();

        stream.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private long countOf(
        List<Recorded> sink,
        int typeId)
    {
        return sink.stream().filter(r -> r.typeId == typeId).count();
    }

    private Recorded nthOf(
        List<Recorded> sink,
        int typeId,
        int occurrence)
    {
        return sink.stream().filter(r -> r.typeId == typeId).skip(occurrence - 1).findFirst().orElse(null);
    }

    private McpResetExFW mcpResetEx(
        Recorded recorded)
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(recorded.bytes);
        final ResetFW reset = resetRO.wrap(buffer, 0, recorded.bytes.length);

        return mcpResetExRO.wrap(reset.extension().buffer(), reset.extension().offset(), reset.extension().limit());
    }

    @Test
    public void shouldNotForwardReplyWindowToUpstreamHttpBeforeRequestIsEverBegun() throws Exception
    {
        factory.attach(newBinding());

        beginLifecycle();
        final String sessionId = capturedSessionId();

        final String body = "{\"name\":\"update_issue\",\"arguments\":{\"owner\":\"acme\",\"state\":\"open\"}}";
        final MessageConsumer stream = beginToolsCall(sessionId, body.length());
        final long initialId = supplyId.get() - 1L;

        data(stream, initialId, body);

        // rejected before ever reaching the upstream HTTP request -- the path arg "repo" never arrives
        assertEquals(1, countOf(mcpSent, ResetFW.TYPE_ID));
        final McpResetExFW resetEx = mcpResetEx(nthOf(mcpSent, ResetFW.TYPE_ID, 1));
        assertEquals(-32602, resetEx.error().code());

        verify(streamFactory, never()).newStream(eq(BeginFW.TYPE_ID), any(), anyInt(), anyInt(), any());

        // a routine reply-direction WINDOW grant, arriving after rejection, must not crash the worker
        window(stream, initialId | 0x01L, 0L, 8192);
    }
}
