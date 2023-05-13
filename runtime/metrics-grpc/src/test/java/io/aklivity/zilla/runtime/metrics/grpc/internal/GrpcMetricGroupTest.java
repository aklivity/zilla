/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.grpc.internal;

import static io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.GrpcType.TEXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.GrpcTypeFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.ResetFW;

public class GrpcMetricGroupTest
{
    @Test
    public void shouldReturnMetricNames()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Collection<String> metricNames = metricGroup.metricNames();

        // THEN
        assertThat(metricNames, containsInAnyOrder(
            "grpc.request.size",
            "grpc.response.size",
            "grpc.active.requests",
            "grpc.duration",
            "grpc.requests.per.rpc",
            "grpc.responses.per.rpc"
        ));
    }

    @Test
    public void shouldResolveGrpcRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.request.size");

        // THEN
        assertThat(metric, instanceOf(GrpcRequestSizeMetric.class));
        assertThat(metric.name(), equalTo("grpc.request.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("gRPC request content length"));
    }

    @Test
    public void shouldResolveGrpcRequestSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.request.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcSizeMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldRecordGrpcRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        String name = "answer";
        String value = "forty two";
        Consumer<GrpcTypeFW.Builder> text = t -> t.set(TEXT).build();
        OctetsFW nameOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(name.getBytes())
            .build();
        OctetsFW valueOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(value.getBytes())
            .build();
        GrpcBeginExFW grpcBeginEx = new GrpcBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[128]), 0, 128)
            .typeId(0)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoStream")
            .metadataItem(h -> h.type(text)
                .nameLen(name.length())
                .name(nameOctets)
                .valueLen(value.length())
                .value(valueOctets))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(grpcBeginEx.buffer(), 0, grpcBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // data frame with 33 bytes length
        AtomicBuffer dataBuffer1 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload1 = new UnsafeBuffer(new byte[33], 0, 33);
        new DataFW.Builder().wrap(dataBuffer1, 0, dataBuffer1.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload1, 0, 33).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer1, 0, dataBuffer1.capacity());

        // data frame with 44 bytes length
        AtomicBuffer dataBuffer2 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload2 = new UnsafeBuffer(new byte[44], 0, 44);
        new DataFW.Builder().wrap(dataBuffer2, 0, dataBuffer2.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload2, 0, 44).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer2, 0, dataBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(77L);
    }

    @Test
    public void shouldRecordZeroGrpcRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        String name = "answer";
        String value = "forty two";
        Consumer<GrpcTypeFW.Builder> text = t -> t.set(TEXT).build();
        OctetsFW nameOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(name.getBytes())
            .build();
        OctetsFW valueOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(value.getBytes())
            .build();
        GrpcBeginExFW grpcBeginEx = new GrpcBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[128]), 0, 128)
            .typeId(0)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoStream")
            .metadataItem(h -> h.type(text)
                .nameLen(name.length())
                .name(nameOctets)
                .valueLen(value.length())
                .value(valueOctets))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(grpcBeginEx.buffer(), 0, grpcBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer1, 0, endBuffer1.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer1, 0, endBuffer1.capacity());

        // THEN
        verify(recorder, times(1)).accept(0L);
    }

    @Test
    public void shouldNotRecordAbortedGrpcRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        String name = "answer";
        String value = "forty two";
        Consumer<GrpcTypeFW.Builder> text = t -> t.set(TEXT).build();
        OctetsFW nameOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(name.getBytes())
            .build();
        OctetsFW valueOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(value.getBytes())
            .build();
        GrpcBeginExFW grpcBeginEx = new GrpcBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[128]), 0, 128)
            .typeId(0)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoStream")
            .metadataItem(h -> h.type(text)
                .nameLen(name.length())
                .name(nameOctets)
                .valueLen(value.length())
                .value(valueOctets))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(grpcBeginEx.buffer(), 0, grpcBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // abort frame
        AtomicBuffer abortBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new AbortFW.Builder().wrap(abortBuffer, 0, abortBuffer.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(AbortFW.TYPE_ID, abortBuffer, 0, abortBuffer.capacity());

        // THEN
        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldResolveGrpcResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.response.size");

        // THEN
        assertThat(metric, instanceOf(GrpcResponseSizeMetric.class));
        assertThat(metric.name(), equalTo("grpc.response.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("gRPC response content length"));
    }

    @Test
    public void shouldResolveGrpcResponseSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.response.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcSizeMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldRecordGrpcResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.response.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        String name = "answer";
        String value = "forty two";
        Consumer<GrpcTypeFW.Builder> text = t -> t.set(TEXT).build();
        OctetsFW nameOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(name.getBytes())
            .build();
        OctetsFW valueOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(value.getBytes())
            .build();
        GrpcBeginExFW grpcBeginEx = new GrpcBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[128]), 0, 128)
            .typeId(0)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoStream")
            .metadataItem(h -> h.type(text)
                .nameLen(name.length())
                .name(nameOctets)
                .valueLen(value.length())
                .value(valueOctets))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(grpcBeginEx.buffer(), 0, grpcBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // data frame with 33 bytes length
        AtomicBuffer dataBuffer1 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload1 = new UnsafeBuffer(new byte[33], 0, 33);
        new DataFW.Builder().wrap(dataBuffer1, 0, 128)
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload1, 0, 33).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer1, 0, dataBuffer1.capacity());

        // data frame with 44 bytes length
        AtomicBuffer dataBuffer2 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload2 = new UnsafeBuffer(new byte[44], 0, 44);
        new DataFW.Builder().wrap(dataBuffer2, 0, dataBuffer2.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload2, 0, 44).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer2, 0, dataBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(77L);
    }

    @Test
    public void shouldRecordZeroGrpcResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.response.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        String name = "answer";
        String value = "forty two";
        Consumer<GrpcTypeFW.Builder> text = t -> t.set(TEXT).build();
        OctetsFW nameOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(name.getBytes())
            .build();
        OctetsFW valueOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(value.getBytes())
            .build();
        GrpcBeginExFW grpcBeginEx = new GrpcBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[128]), 0, 128)
            .typeId(0)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoStream")
            .metadataItem(h -> h.type(text)
                .nameLen(name.length())
                .name(nameOctets)
                .valueLen(value.length())
                .value(valueOctets))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(grpcBeginEx.buffer(), 0, grpcBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(0L);
    }

    @Test
    public void shouldNotRecordAbortedGrpcResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.response.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        String name = "answer";
        String value = "forty two";
        Consumer<GrpcTypeFW.Builder> text = t -> t.set(TEXT).build();
        OctetsFW nameOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(name.getBytes())
            .build();
        OctetsFW valueOctets = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .set(value.getBytes())
            .build();
        GrpcBeginExFW grpcBeginEx = new GrpcBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[128]), 0, 128)
            .typeId(0)
            .scheme("http")
            .authority("localhost:8080")
            .service("example.EchoService")
            .method("EchoStream")
            .metadataItem(h -> h.type(text)
                .nameLen(name.length())
                .name(nameOctets)
                .valueLen(value.length())
                .value(valueOctets))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(grpcBeginEx.buffer(), 0, grpcBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // abort frame
        AtomicBuffer abortBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new AbortFW.Builder().wrap(abortBuffer, 0, abortBuffer.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(AbortFW.TYPE_ID, abortBuffer, 0, abortBuffer.capacity());

        // THEN
        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldResolveGrpcActiveRequests()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.active.requests");

        // THEN
        assertThat(metric, instanceOf(GrpcActiveRequestsMetric.class));
        assertThat(metric.name(), equalTo("grpc.active.requests"));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of active gRPC requests"));
    }

    @Test
    public void shouldResolveGrpcActiveRequestsContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.active.requests");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcActiveRequestsMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }

    @Test
    public void shouldRecordGrpcActiveRequests()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.active.requests");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frames
        AtomicBuffer endBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer1, 0, endBuffer1.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer1, 0, endBuffer1.capacity());
        AtomicBuffer endBuffer2 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer2, 0, endBuffer2.capacity())
            .originId(0L).routedId(0L).streamId(0L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer2, 0, endBuffer2.capacity());

        // THEN
        verify(recorder, times(1)).accept(1L);
        verify(recorder, times(1)).accept(-1L);
    }

    @Test
    public void shouldResolveGrpcDuration()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.duration");

        // THEN
        assertThat(metric, instanceOf(GrpcDurationMetric.class));
        assertThat(metric.name(), equalTo("grpc.duration"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.NANOSECONDS));
        assertThat(metric.description(), equalTo("Duration of gRPC requests"));
    }

    @Test
    public void shouldResolveGrpcDurationContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.duration");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcDurationMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }

    @Test
    public void shouldRecordGrpcDuration()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.duration");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(42_000_000_000L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame received
        AtomicBuffer endBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer1, 0, endBuffer1.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(72_000_000_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer1, 0, endBuffer1.capacity());

        // end frame sent
        AtomicBuffer endBuffer2 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer2, 0, endBuffer2.capacity())
            .originId(0L).routedId(0L).streamId(0L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(77_000_000_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer2, 0, endBuffer2.capacity());

        // THEN
        verify(recorder, times(1)).accept(35_000_000_000L);
    }

    @Test
    public void shouldNotRecordGrpcDurationIfAborted()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.duration");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(42_000_000_000L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // abort frame received
        AtomicBuffer abortBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new AbortFW.Builder().wrap(abortBuffer, 0, abortBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(72_000_000_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(AbortFW.TYPE_ID, abortBuffer, 0, abortBuffer.capacity());

        // end frame sent
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
            .originId(0L).routedId(0L).streamId(0L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(77_000_000_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldNotRecordGrpcDurationIfReset()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.duration");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(42_000_000_000L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame received
        AtomicBuffer endBuffer0 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer0, 0, endBuffer0.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(72_000_000_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer0, 0, endBuffer0.capacity());

        // reset frame sent
        AtomicBuffer resetBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new ResetFW.Builder().wrap(resetBuffer, 0, resetBuffer.capacity())
            .originId(0L).routedId(0L).streamId(0L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(77_000_000_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(ResetFW.TYPE_ID, resetBuffer, 0, resetBuffer.capacity());

        // THEN
        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldResolveGrpcRequestsPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.requests.per.rpc");

        // THEN
        assertThat(metric, instanceOf(GrpcRequestsPerRpcMetric.class));
        assertThat(metric.name(), equalTo("grpc.requests.per.rpc"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of gRPC requests per RPC"));
    }

    @Test
    public void shouldResolveGrpcRequestsPerRpcContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.requests.per.rpc");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcCountPerRpcContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldRecordGrpcRequestsPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.requests.per.rpc");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // data frame 1
        AtomicBuffer dataBuffer1 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload1 = new UnsafeBuffer(new byte[32], 0, 32);
        new DataFW.Builder().wrap(dataBuffer1, 0, dataBuffer1.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
            .payload(payload1, 0, 32).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer1, 0, dataBuffer1.capacity());

        // data frame 2
        AtomicBuffer dataBuffer2 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload2 = new UnsafeBuffer(new byte[32], 0, 32);
        new DataFW.Builder().wrap(dataBuffer2, 0, dataBuffer2.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
            .payload(payload2, 0, 32).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer2, 0, dataBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(2L);
    }

    @Test
    public void shouldRecordZeroGrpcRequestsPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.requests.per.rpc");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer0 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer0, 0, endBuffer0.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer0, 0, endBuffer0.capacity());

        // THEN
        verify(recorder, times(1)).accept(0L);
    }

    @Test
    public void shouldNotRecordAbortedGrpcRequestsPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.requests.per.rpc");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // abort frame
        AtomicBuffer abortBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new AbortFW.Builder().wrap(abortBuffer, 0, abortBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(AbortFW.TYPE_ID, abortBuffer, 0, abortBuffer.capacity());

        // THEN
        verify(recorder, never()).accept(anyLong());
    }

    @Test
    public void shouldResolveGrpcResponsesPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.responses.per.rpc");

        // THEN
        assertThat(metric, instanceOf(GrpcResponsesPerRpcMetric.class));
        assertThat(metric.name(), equalTo("grpc.responses.per.rpc"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of gRPC responses per RPC"));
    }

    @Test
    public void shouldResolveGrpcResponsesPerRpcContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.responses.per.rpc");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcCountPerRpcContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldRecordGrpcResponsesPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.responses.per.rpc");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // data frame 1
        AtomicBuffer dataBuffer1 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload1 = new UnsafeBuffer(new byte[32], 0, 32);
        new DataFW.Builder().wrap(dataBuffer1, 0, dataBuffer1.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
            .payload(payload1, 0, 32).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer1, 0, dataBuffer1.capacity());

        // data frame 2
        AtomicBuffer dataBuffer2 = new UnsafeBuffer(new byte[256], 0, 256);
        AtomicBuffer payload2 = new UnsafeBuffer(new byte[32], 0, 32);
        new DataFW.Builder().wrap(dataBuffer2, 0, dataBuffer2.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
            .payload(payload2, 0, 32).build();
        handler.accept(DataFW.TYPE_ID, dataBuffer2, 0, dataBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(2L);
    }

    @Test
    public void shouldRecordZeroGrpcResponsesPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.responses.per.rpc");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(0L);
    }

    @Test
    public void shouldNotRecordAbortedGrpcResponsesPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("grpc.responses.per.rpc");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // abort frame
        AtomicBuffer abortBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new AbortFW.Builder().wrap(abortBuffer, 0, abortBuffer.capacity())
            .originId(0L).routedId(0L).streamId(2L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).build();
        handler.accept(AbortFW.TYPE_ID, abortBuffer, 0, abortBuffer.capacity());

        // THEN
        verify(recorder, never()).accept(anyLong());
    }
}
