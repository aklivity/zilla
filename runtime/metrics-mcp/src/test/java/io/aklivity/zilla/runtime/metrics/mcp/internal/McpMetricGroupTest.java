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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpEndExFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpOutcome;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.ResetFW;

public class McpMetricGroupTest
{
    private final Configuration config = new Configuration();

    @Test
    public void shouldReturnMetricNames()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        Collection<String> metricNames = metricGroup.metricNames();

        assertThat(metricNames, containsInAnyOrder(
            "mcp.initialize", "mcp.initialize.duration",
            "mcp.tools.list", "mcp.tools.list.duration",
            "mcp.tools.call", "mcp.tools.call.duration",
            "mcp.resources.list", "mcp.resources.list.duration",
            "mcp.resources.read", "mcp.resources.read.duration",
            "mcp.prompts.list", "mcp.prompts.list.duration",
            "mcp.prompts.get", "mcp.prompts.get.duration"));
    }

    @Test
    public void shouldResolveUnknownMetricAsNull()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        assertThat(metricGroup.supply("mcp.unknown"), equalTo(null));
        assertThat(metricGroup.name(), equalTo("mcp"));
        assertThat(metricGroup.type(), notNullValue());
    }

    @Test
    public void shouldResolveToolsCallCounter()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        Metric metric = metricGroup.supply("mcp.tools.call");

        assertThat(metric, instanceOf(McpMetric.class));
        assertThat(metric.name(), equalTo("mcp.tools.call"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("MCP tool invocations"));
    }

    @Test
    public void shouldResolveToolsCallDuration()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        Metric metric = metricGroup.supply("mcp.tools.call.duration");

        assertThat(metric.name(), equalTo("mcp.tools.call.duration"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.NANOSECONDS));
        assertThat(metric.description(), equalTo("Duration of MCP tool invocations"));
    }

    @Test
    public void shouldResolveContext()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);
        Metric metric = metricGroup.supply("mcp.tools.call");

        MetricContext context = metric.supply(mock(EngineContext.class));

        assertThat(context.group(), equalTo("mcp"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
        assertThat(((McpMetricContext) context).name(), equalTo("mcp.tools.call"));
    }

    @Test
    public void shouldCountToolsCallOnExchangeClose()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call", recorder);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        end(handler, 1L, 0L);
        verify(recorder, never()).accept(anyLong());
        end(handler, 0L, 0L);

        verify(recorder, times(1)).accept(1L);
    }

    @Test
    public void shouldNotCountWhenMethodDoesNotMatch()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call", recorder);

        begin(handler, 1L, 0L, b -> b.lifecycle(l -> l.sessionId("s").capabilities(0)));
        end(handler, 1L, 0L);
        end(handler, 0L, 0L);

        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldNotCountWhenExtensionAbsent()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call", recorder);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        BeginFW begin = new BeginFW.Builder().wrap(buffer, 0, buffer.capacity())
            .originId(0L).routedId(0L).streamId(1L)
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, begin.buffer(), begin.offset(), begin.sizeof());
        end(handler, 1L, 0L);
        end(handler, 0L, 0L);

        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldRecordToolsCallDurationOnExchangeClose()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call.duration", recorder);

        begin(handler, 1L, 1000L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        end(handler, 1L, 3000L);
        end(handler, 0L, 5000L);

        verify(recorder, times(1)).accept(4000L);
    }

    @Test
    public void shouldNotRecordDurationWhenStartTimestampMissing()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call.duration", recorder);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        end(handler, 1L, 3000L);
        end(handler, 0L, 5000L);

        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldCountToolsCallErrorOnAbort()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call", recorder);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        abort(handler, 1L);

        verify(recorder, times(1)).accept(1L);
    }

    @Test
    public void shouldCountToolsCallErrorOnReset()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call", recorder);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        reset(handler, 0L);

        verify(recorder, times(1)).accept(1L);
    }

    @Test
    public void shouldIgnoreAbortWhenNotTracked()
    {
        LongConsumer recorder = mock(LongConsumer.class);
        MessageConsumer handler = handler("mcp.tools.call", recorder);

        abort(handler, 1L);

        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldLabelToolsCallWithToolAndOutcomeOk()
    {
        EngineContext engineContext = mock(EngineContext.class);
        when(engineContext.supplyTypeId(anyString())).thenReturn(7);
        int[] capturedAttr = { -1 };
        LongConsumer recorder = mock(LongConsumer.class);
        IntFunction<LongConsumer> recorderByAttr = attr ->
        {
            capturedAttr[0] = attr;
            return recorder;
        };
        List<AttributeConfig> attributes = List.of(
            AttributeConfig.builder().name("tool").value("${mcp.tool}").build(),
            AttributeConfig.builder().name("outcome").value("${mcp.outcome}").build());

        MetricContext context = new McpMetricGroup(config).supply("mcp.tools.call").supply(engineContext);
        MessageConsumer handler = context.supply(recorderByAttr, attributes);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        end(handler, 1L, 0L);
        end(handler, 0L, 0L);

        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(engineContext).supplyTypeId(label.capture());
        assertThat(label.getValue(), equalTo("outcome=ok,tool=weather"));
        assertThat(capturedAttr[0], equalTo(7));
        verify(recorder, times(1)).accept(1L);
    }

    @Test
    public void shouldLabelToolsCallWithOutcomeErrorOnEndExtension()
    {
        EngineContext engineContext = mock(EngineContext.class);
        when(engineContext.supplyTypeId(anyString())).thenReturn(11);
        LongConsumer recorder = mock(LongConsumer.class);
        IntFunction<LongConsumer> recorderByAttr = attr -> recorder;
        List<AttributeConfig> attributes = List.of(
            AttributeConfig.builder().name("tool").value("${mcp.tool}").build(),
            AttributeConfig.builder().name("outcome").value("${mcp.outcome}").build());

        MetricContext context = new McpMetricGroup(config).supply("mcp.tools.call").supply(engineContext);
        MessageConsumer handler = context.supply(recorderByAttr, attributes);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        endWithOutcome(handler, 0L, 0L, McpOutcome.ERROR);
        end(handler, 1L, 0L);

        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(engineContext).supplyTypeId(label.capture());
        assertThat(label.getValue(), equalTo("outcome=error,tool=weather"));
        verify(recorder, times(1)).accept(1L);
    }

    @Test
    public void shouldRecordOkWhenEndOutcomeOmitted()
    {
        EngineContext engineContext = mock(EngineContext.class);
        when(engineContext.supplyTypeId(anyString())).thenReturn(13);
        LongConsumer recorder = mock(LongConsumer.class);
        IntFunction<LongConsumer> recorderByAttr = attr -> recorder;
        List<AttributeConfig> attributes = List.of(
            AttributeConfig.builder().name("outcome").value("${mcp.outcome}").build());

        MetricContext context = new McpMetricGroup(config).supply("mcp.tools.call").supply(engineContext);
        MessageConsumer handler = context.supply(recorderByAttr, attributes);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        end(handler, 0L, 0L);
        end(handler, 1L, 0L);

        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(engineContext).supplyTypeId(label.capture());
        assertThat(label.getValue(), equalTo("outcome=ok"));
        verify(recorder, times(1)).accept(1L);
    }

    @Test
    public void shouldLabelToolsCallWithOutcomeErrorOnAbort()
    {
        EngineContext engineContext = mock(EngineContext.class);
        when(engineContext.supplyTypeId(anyString())).thenReturn(9);
        LongConsumer recorder = mock(LongConsumer.class);
        IntFunction<LongConsumer> recorderByAttr = attr -> recorder;
        List<AttributeConfig> attributes = List.of(
            AttributeConfig.builder().name("tool").value("${mcp.tool}").build(),
            AttributeConfig.builder().name("outcome").value("${mcp.outcome}").build());

        MetricContext context = new McpMetricGroup(config).supply("mcp.tools.call").supply(engineContext);
        MessageConsumer handler = context.supply(recorderByAttr, attributes);

        begin(handler, 1L, 0L, b -> b.toolsCall(t -> t.sessionId("s").name("weather").contentLength(-1)));
        abort(handler, 1L);

        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(engineContext).supplyTypeId(label.capture());
        assertThat(label.getValue(), equalTo("outcome=error,tool=weather"));
        verify(recorder, times(1)).accept(1L);
    }

    private MessageConsumer handler(
        String metricName,
        LongConsumer recorder)
    {
        MetricContext context = new McpMetricGroup(config).supply(metricName).supply(mock(EngineContext.class));
        return context.supply(recorder);
    }

    private static void begin(
        MessageConsumer handler,
        long streamId,
        long timestamp,
        Consumer<McpBeginExFW.Builder> mutator)
    {
        McpBeginExFW.Builder exBuilder = new McpBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[256]), 0, 256)
            .typeId(0);
        mutator.accept(exBuilder);
        McpBeginExFW beginEx = exBuilder.build();

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);
        BeginFW begin = new BeginFW.Builder().wrap(buffer, 0, buffer.capacity())
            .originId(0L).routedId(0L).streamId(streamId)
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(timestamp)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(beginEx.buffer(), beginEx.offset(), beginEx.limit())
            .build();
        handler.accept(BeginFW.TYPE_ID, begin.buffer(), begin.offset(), begin.sizeof());
    }

    private static void end(
        MessageConsumer handler,
        long streamId,
        long timestamp)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        EndFW end = new EndFW.Builder().wrap(buffer, 0, buffer.capacity())
            .originId(0L).routedId(0L).streamId(streamId)
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(timestamp)
            .traceId(0L).authorization(0L)
            .build();
        handler.accept(EndFW.TYPE_ID, end.buffer(), end.offset(), end.sizeof());
    }

    private static void endWithOutcome(
        MessageConsumer handler,
        long streamId,
        long timestamp,
        McpOutcome outcome)
    {
        McpEndExFW endEx = new McpEndExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[64]), 0, 64)
            .typeId(0)
            .outcome(o -> o.set(outcome))
            .build();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        EndFW end = new EndFW.Builder().wrap(buffer, 0, buffer.capacity())
            .originId(0L).routedId(0L).streamId(streamId)
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(timestamp)
            .traceId(0L).authorization(0L)
            .extension(endEx.buffer(), endEx.offset(), endEx.limit())
            .build();
        handler.accept(EndFW.TYPE_ID, end.buffer(), end.offset(), end.sizeof());
    }

    private static void abort(
        MessageConsumer handler,
        long streamId)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        AbortFW abort = new AbortFW.Builder().wrap(buffer, 0, buffer.capacity())
            .originId(0L).routedId(0L).streamId(streamId)
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L)
            .build();
        handler.accept(AbortFW.TYPE_ID, abort.buffer(), abort.offset(), abort.sizeof());
    }

    private static void reset(
        MessageConsumer handler,
        long streamId)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        ResetFW reset = new ResetFW.Builder().wrap(buffer, 0, buffer.capacity())
            .originId(0L).routedId(0L).streamId(streamId)
            .sequence(0L).acknowledge(0L).maximum(0)
            .traceId(0L)
            .build();
        handler.accept(ResetFW.TYPE_ID, reset.buffer(), reset.offset(), reset.sizeof());
    }
}
