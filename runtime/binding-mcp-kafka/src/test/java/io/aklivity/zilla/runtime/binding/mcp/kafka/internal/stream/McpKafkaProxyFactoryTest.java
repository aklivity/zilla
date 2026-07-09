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

import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpEndExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpOutcome;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public class McpKafkaProxyFactoryTest
{
    private static final int MCP_TYPE_ID = 1;
    private static final int KAFKA_TYPE_ID = 2;
    private static final long BINDING_ID = 1L;
    private static final long ROUTE_ID = 2L;
    private static final long ORIGIN_ID = 3L;
    private static final long INITIAL_ID = 10L;
    private static final long AFFINITY = 0L;
    private static final long AUTHORIZATION = 0L;

    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[65536]);
    private final MutableDirectBufferEx scratch = new UnsafeBufferEx(new byte[65536]);
    private final MutableDirectBufferEx extScratch = new UnsafeBufferEx(new byte[65536]);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final SignalFW.Builder signalRW = new SignalFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final ResetFW resetRO = new ResetFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final McpResetExFW mcpResetExRO = new McpResetExFW();
    private final McpEndExFW mcpEndExRO = new McpEndExFW();

    private final AtomicLong supplyId = new AtomicLong(100L);

    private final EngineContext context = mock(EngineContext.class);
    private final BindingHandler streamFactory = mock(BindingHandler.class);
    private final Signaler signaler = mock(Signaler.class);
    private final MessageConsumer mcp = mock(MessageConsumer.class);
    private final MessageConsumer kafka = mock(MessageConsumer.class);

    private final List<Recorded> mcpSent = new ArrayList<>();
    private final List<Recorded> kafkaSent = new ArrayList<>();

    private McpKafkaProxyFactory factory;

    @Before
    public void setup() throws Exception
    {
        when(context.writeBuffer()).thenReturn(writeBuffer);
        when(context.streamFactory()).thenReturn(streamFactory);
        when(context.signaler()).thenReturn(signaler);
        when(context.supplyTypeId("mcp")).thenReturn(MCP_TYPE_ID);
        when(context.supplyTypeId("kafka")).thenReturn(KAFKA_TYPE_ID);
        when(context.supplyInitialId(anyLong())).thenAnswer(inv -> supplyId.getAndIncrement());
        when(context.supplyReplyId(anyLong())).thenAnswer(inv -> ((long) inv.getArgument(0)) | 0x01L);
        when(streamFactory.newStream(eq(BeginFW.TYPE_ID), any(), anyInt(), anyInt(), any())).thenReturn(kafka);

        doAnswer(inv -> record(mcpSent, inv)).when(mcp).accept(anyInt(), any(), anyInt(), anyInt());
        doAnswer(inv -> record(kafkaSent, inv)).when(kafka).accept(anyInt(), any(), anyInt(), anyInt());

        this.factory = new McpKafkaProxyFactory(new McpKafkaConfiguration(), context);
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

    private BindingConfig newBinding(
        String tool)
    {
        final RouteConfig route = RouteConfig.builder()
            .exit("kafka0")
            .when(new McpKafkaConditionConfig(tool, null))
            .build();
        route.id = ROUTE_ID;

        final BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("mcp0")
            .type("mcp_kafka")
            .kind(KindConfig.PROXY)
            .routes(List.of(route))
            .build();
        binding.id = BINDING_ID;

        return binding;
    }

    private MessageConsumer beginToolsCall(
        String tool,
        int contentLength,
        long timeout)
    {
        final McpBeginExFW beginEx = mcpBeginExRW.wrap(extScratch, 0, extScratch.capacity())
            .typeId(MCP_TYPE_ID)
            .toolsCall(t -> t
                .sessionId("session-1")
                .name(tool)
                .contentLength(contentLength)
                .timeout(timeout))
            .build();

        final BeginFW begin = beginRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(BINDING_ID)
            .streamId(INITIAL_ID)
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

    private void end(
        MessageConsumer stream,
        long streamId)
    {
        final EndFW end = endRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(ROUTE_ID)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(1L)
            .authorization(AUTHORIZATION)
            .build();

        stream.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void reset(
        MessageConsumer stream,
        long streamId)
    {
        final ResetFW reset = resetRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(ROUTE_ID)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(1L)
            .authorization(AUTHORIZATION)
            .build();

        stream.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void signal(
        MessageConsumer stream,
        long streamId,
        int signalId)
    {
        final SignalFW signal = signalRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(ROUTE_ID)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(1L)
            .cancelId(0L)
            .signalId(signalId)
            .contextId(0)
            .build();

        stream.accept(signal.typeId(), signal.buffer(), signal.offset(), signal.sizeof());
    }

    private void dataWithPayload(
        MessageConsumer stream,
        long streamId,
        String value)
    {
        final byte[] bytes = value.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

        final DataFW data = dataRW.wrap(scratch, 0, scratch.capacity())
            .originId(ORIGIN_ID)
            .routedId(ROUTE_ID)
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

    private MessageConsumer captureKafkaSender()
    {
        final ArgumentCaptor<MessageConsumer> captor = ArgumentCaptor.forClass(MessageConsumer.class);
        verify(streamFactory).newStream(eq(BeginFW.TYPE_ID), any(), anyInt(), anyInt(), captor.capture());
        return captor.getValue();
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

    private String payloadText(
        Recorded recorded)
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(recorded.bytes);
        final DataFW data = dataRO.wrap(buffer, 0, recorded.bytes.length);
        final byte[] bytes = new byte[data.payload().sizeof()];
        data.payload().buffer().getBytes(data.payload().offset(), bytes, 0, bytes.length);

        return new String(bytes, UTF_8);
    }

    private KafkaBeginExFW kafkaBeginEx(
        Recorded recorded)
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(recorded.bytes);
        final BeginFW begin = beginRO.wrap(buffer, 0, recorded.bytes.length);

        return kafkaBeginExRO.wrap(begin.extension().buffer(), begin.extension().offset(), begin.extension().limit());
    }

    private McpResetExFW mcpResetEx(
        Recorded recorded)
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(recorded.bytes);
        final ResetFW reset = resetRO.wrap(buffer, 0, recorded.bytes.length);

        return mcpResetExRO.wrap(reset.extension().buffer(), reset.extension().offset(), reset.extension().limit());
    }

    private McpEndExFW mcpEndEx(
        Recorded recorded)
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(recorded.bytes);
        final EndFW end = endRO.wrap(buffer, 0, recorded.bytes.length);

        return mcpEndExRO.wrap(end.extension().buffer(), end.extension().offset(), end.extension().limit());
    }

    @Test
    public void shouldParseValidProduceArgsAndOpenKafkaProduceStream() throws Exception
    {
        factory.attach(newBinding("produce"));

        final String body = "{\"name\":\"produce\",\"arguments\":{\"topic\":\"orders\",\"value\":\"hello\"}}";
        final MessageConsumer stream = beginToolsCall("produce", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        assertNotNull(captureKafkaSender());
        assertEquals(1, countOf(kafkaSent, BeginFW.TYPE_ID));

        final KafkaBeginExFW kafkaBeginEx = kafkaBeginEx(nthOf(kafkaSent, BeginFW.TYPE_ID, 1));
        assertEquals(PRODUCE_ONLY, kafkaBeginEx.merged().capabilities().get());
        assertEquals("orders", kafkaBeginEx.merged().topic().asString());

        final KafkaOffsetFW partition = kafkaBeginEx.merged().partitions().matchFirst(p -> true);
        assertEquals(-1, partition.partitionId());
        assertEquals(-2L, partition.partitionOffset());
    }

    @Test
    public void shouldRejectProduceArgsMissingRequiredFields() throws Exception
    {
        factory.attach(newBinding("produce"));

        final String body = "{\"name\":\"produce\",\"arguments\":{\"key\":\"missing-topic-and-value\"}}";
        final MessageConsumer stream = beginToolsCall("produce", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        assertEquals(1, countOf(mcpSent, ResetFW.TYPE_ID));

        final McpResetExFW resetEx = mcpResetEx(nthOf(mcpSent, ResetFW.TYPE_ID, 1));
        assertEquals(-32602, resetEx.error().code());
        assertEquals("Invalid params", resetEx.error().message().asString());

        assertEquals(0, countOf(kafkaSent, BeginFW.TYPE_ID));
    }

    @Test
    public void shouldRejectConsumeArgsMissingTopic() throws Exception
    {
        factory.attach(newBinding("consume"));

        final String body = "{\"name\":\"consume\",\"arguments\":{\"limit\":2}}";
        final MessageConsumer stream = beginToolsCall("consume", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        final McpResetExFW resetEx = mcpResetEx(nthOf(mcpSent, ResetFW.TYPE_ID, 1));
        assertEquals(-32602, resetEx.error().code());
    }

    @Test
    public void shouldClampConsumeLimitAndOpenFetchOnlyStream() throws Exception
    {
        factory.attach(newBinding("consume"));

        final String body = "{\"name\":\"consume\",\"arguments\":{\"topic\":\"orders\",\"limit\":500,\"offset\":7}}";
        final MessageConsumer stream = beginToolsCall("consume", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        final KafkaBeginExFW kafkaBeginEx = kafkaBeginEx(nthOf(kafkaSent, BeginFW.TYPE_ID, 1));
        assertEquals(FETCH_ONLY, kafkaBeginEx.merged().capabilities().get());
        assertEquals("orders", kafkaBeginEx.merged().topic().asString());

        final KafkaOffsetFW partition = kafkaBeginEx.merged().partitions().matchFirst(p -> true);
        assertEquals(-1, partition.partitionId());
        assertEquals(7L, partition.partitionOffset());
    }

    @Test
    public void shouldClampConsumeLimitToOneHundredWhenReceivingMoreRecords() throws Exception
    {
        factory.attach(newBinding("consume"));

        final String body = "{\"name\":\"consume\",\"arguments\":{\"topic\":\"orders\",\"limit\":500}}";
        final MessageConsumer stream = beginToolsCall("consume", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        final MessageConsumer kafkaSender = captureKafkaSender();
        final long kafkaReplyId = (supplyId.get() - 1L) | 0x01L;

        for (int i = 0; i < 99; i++)
        {
            dataWithPayload(kafkaSender, kafkaReplyId, "value-" + i);
        }

        assertEquals(0, countOf(kafkaSent, EndFW.TYPE_ID));

        dataWithPayload(kafkaSender, kafkaReplyId, "value-99");

        assertEquals(1, countOf(kafkaSent, EndFW.TYPE_ID));
    }

    @Test
    public void shouldCompleteProduceOnWindowDrainThenKafkaEnd() throws Exception
    {
        factory.attach(newBinding("produce"));

        final String body = "{\"name\":\"produce\",\"arguments\":{\"topic\":\"orders\",\"value\":\"hello\"}}";
        final MessageConsumer stream = beginToolsCall("produce", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        final MessageConsumer kafkaSender = captureKafkaSender();
        final long kafkaInitialId = supplyId.get() - 1L;

        // no data sent yet - waiting for the first window to grant real credit
        assertEquals(0, countOf(kafkaSent, DataFW.TYPE_ID));

        // first window grants credit -> deferred produce record is flushed
        window(kafkaSender, kafkaInitialId, 0L, 8192);
        assertEquals(1, countOf(kafkaSent, DataFW.TYPE_ID));

        // window not yet caught up with what we sent -> no end yet
        assertEquals(0, countOf(kafkaSent, EndFW.TYPE_ID));

        // window drains fully (acknowledge catches up to the 5 bytes written) -> fire-and-forget end
        window(kafkaSender, kafkaInitialId, 5L, 8192);
        assertEquals(1, countOf(kafkaSent, EndFW.TYPE_ID));

        // no result sent to the mcp client yet - waiting on kafka's own reply end
        assertEquals(0, countOf(mcpSent, DataFW.TYPE_ID));

        // kafka's reply end arrives -> synthesize the success envelope
        end(kafkaSender, kafkaInitialId | 0x01L);

        final String result = payloadText(nthOf(mcpSent, DataFW.TYPE_ID, 1));
        assertEquals("{\"content\":[{\"type\": \"text\",\"text\": \"Produced record to orders\"}],\"isError\": false}",
            result);

        assertEquals(1, countOf(mcpSent, EndFW.TYPE_ID));
    }

    @Test
    public void shouldSynthesizeProduceErrorAndAbortOnKafkaReset() throws Exception
    {
        factory.attach(newBinding("produce"));

        final String body = "{\"name\":\"produce\",\"arguments\":{\"topic\":\"orders\",\"value\":\"hello\"}}";
        final MessageConsumer stream = beginToolsCall("produce", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        final MessageConsumer kafkaSender = captureKafkaSender();
        final long kafkaInitialId = supplyId.get() - 1L;

        window(kafkaSender, kafkaInitialId, 0L, 8192);

        // kafka rejects the produce before ever draining the window
        reset(kafkaSender, kafkaInitialId | 0x01L);

        final String result = payloadText(nthOf(mcpSent, DataFW.TYPE_ID, 1));
        assertEquals("{\"content\":[{\"type\": \"text\",\"text\": \"Failed to produce record to orders\"}],\"isError\": true}",
            result);

        final McpEndExFW endEx = mcpEndEx(nthOf(mcpSent, EndFW.TYPE_ID, 1));
        assertEquals(McpOutcome.ERROR, endEx.outcome().get());

        assertEquals(1, countOf(kafkaSent, AbortFW.TYPE_ID));
        assertEquals(1, countOf(kafkaSent, ResetFW.TYPE_ID));
    }

    @Test
    public void shouldFinishConsumeOnceLimitIsReached() throws Exception
    {
        factory.attach(newBinding("consume"));

        final String body = "{\"name\":\"consume\",\"arguments\":{\"topic\":\"orders\",\"limit\":2}}";
        final MessageConsumer stream = beginToolsCall("consume", body.length(), 0L);

        data(stream, INITIAL_ID, body);

        final MessageConsumer kafkaSender = captureKafkaSender();
        final long kafkaReplyId = (supplyId.get() - 1L) | 0x01L;

        dataWithPayload(kafkaSender, kafkaReplyId, "value-1");
        assertEquals(0, countOf(kafkaSent, EndFW.TYPE_ID));

        dataWithPayload(kafkaSender, kafkaReplyId, "value-2");

        assertEquals(1, countOf(kafkaSent, EndFW.TYPE_ID));

        final String result = payloadText(nthOf(mcpSent, DataFW.TYPE_ID, 1));
        assertEquals("{\"content\":[{\"type\": \"text\",\"text\": \"value-1,value-2\"}],\"isError\": false}", result);

        // a third record arriving after the limit was already reached must be ignored
        dataWithPayload(kafkaSender, kafkaReplyId, "value-3");
        assertEquals(1, countOf(mcpSent, DataFW.TYPE_ID));
    }

    @Test
    public void shouldFinishConsumeOnTimeoutSignalWithPartialResults() throws Exception
    {
        factory.attach(newBinding("consume"));

        final String body = "{\"name\":\"consume\",\"arguments\":{\"topic\":\"orders\",\"limit\":5}}";
        final MessageConsumer stream = beginToolsCall("consume", body.length(), 200L);

        data(stream, INITIAL_ID, body);

        final MessageConsumer kafkaSender = captureKafkaSender();
        final long kafkaInitialId = supplyId.get() - 1L;
        final long kafkaReplyId = kafkaInitialId | 0x01L;

        final ArgumentCaptor<Integer> signalIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(signaler).signalAt(anyLong(), anyLong(), anyLong(), eq(kafkaInitialId), anyLong(),
            signalIdCaptor.capture(), anyInt());

        dataWithPayload(kafkaSender, kafkaReplyId, "value-1");

        signal(kafkaSender, kafkaInitialId, signalIdCaptor.getValue());

        assertEquals(1, countOf(kafkaSent, EndFW.TYPE_ID));

        final String result = payloadText(nthOf(mcpSent, DataFW.TYPE_ID, 1));
        assertEquals("{\"content\":[{\"type\": \"text\",\"text\": \"value-1\"}],\"isError\": false}", result);

        // a late-arriving record after timeout finalized the result must not be appended
        dataWithPayload(kafkaSender, kafkaReplyId, "value-2");
        assertEquals(1, countOf(mcpSent, DataFW.TYPE_ID));
    }

    @Test
    public void shouldFinishConsumeWithZeroRecordsOnImmediateTimeout() throws Exception
    {
        factory.attach(newBinding("consume"));

        final String body = "{\"name\":\"consume\",\"arguments\":{\"topic\":\"orders\",\"limit\":5}}";
        final MessageConsumer stream = beginToolsCall("consume", body.length(), 200L);

        data(stream, INITIAL_ID, body);

        final MessageConsumer kafkaSender = captureKafkaSender();
        final long kafkaInitialId = supplyId.get() - 1L;

        final ArgumentCaptor<Integer> signalIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(signaler).signalAt(anyLong(), anyLong(), anyLong(), eq(kafkaInitialId), anyLong(),
            signalIdCaptor.capture(), anyInt());

        signal(kafkaSender, kafkaInitialId, signalIdCaptor.getValue());

        final String result = payloadText(nthOf(mcpSent, DataFW.TYPE_ID, 1));
        assertEquals("{\"content\":[{\"type\": \"text\",\"text\": \"\"}],\"isError\": false}", result);
    }

    @Test
    public void shouldRouteOtherToolsWithoutBufferingArguments() throws Exception
    {
        factory.attach(newBinding("list_topics"));

        final MessageConsumer stream = beginToolsCall("list_topics", -1, 0L);

        assertNotNull(stream);
        assertEquals(1, countOf(kafkaSent, BeginFW.TYPE_ID));
    }

    @Test
    public void shouldRejectToolsCallWithNoMatchingRoute() throws Exception
    {
        factory.attach(newBinding("produce"));

        final MessageConsumer stream = beginToolsCall("consume", 10, 0L);

        assertNull(stream);
        assertEquals(0, countOf(kafkaSent, BeginFW.TYPE_ID));
    }
}
