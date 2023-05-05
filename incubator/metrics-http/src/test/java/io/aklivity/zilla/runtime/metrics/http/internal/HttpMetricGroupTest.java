/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.http.internal;

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
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpBeginExFW;

public class HttpMetricGroupTest
{
    @Test
    public void shouldReturnMetricNames()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Collection<String> metricNames = metricGroup.metricNames();

        // THEN
        assertThat(metricNames, containsInAnyOrder(
            "http.request.size",
            "http.response.size",
            "http.active.requests",
            "http.duration"
        ));
    }

    @Test
    public void shouldResolveHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.request.size");

        // THEN
        assertThat(metric, instanceOf(HttpRequestSizeMetric.class));
        assertThat(metric.name(), equalTo("http.request.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("HTTP request content length"));
    }

    @Test
    public void shouldResolveHttpRequestSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.request.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpSizeMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldRecordFixedHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame with header Content-Length = 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(42L);
    }

    @Test
    public void shouldRecordDynamicHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame without Content-Length
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
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
    public void shouldRecordZeroHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame when header Content-Length is empty
        HttpBeginExFW httpBeginEx1 = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value(""))
                .build();
        AtomicBuffer beginBuffer1 = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer1, 0, beginBuffer1.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx1.buffer(), 0, httpBeginEx1.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer1, 0, beginBuffer1.capacity());

        // end frame
        AtomicBuffer endBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer1, 0, endBuffer1.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer1, 0, endBuffer1.capacity());

        // begin frame when header Content-Length is 0
        HttpBeginExFW httpBeginEx2 = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("0"))
                .build();
        AtomicBuffer beginBuffer2 = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer2, 0, beginBuffer2.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx2.buffer(), 0, httpBeginEx2.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer2, 0, beginBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer2 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer2, 0, endBuffer2.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer2, 0, endBuffer2.capacity());

        // THEN
        verify(recorder, times(2)).accept(0L);
    }

    @Test
    public void shouldNotRecordAbortedHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame with header Content-Length = 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .originId(0L).routedId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
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
    public void shouldResolveHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.response.size");

        // THEN
        assertThat(metric, instanceOf(HttpResponseSizeMetric.class));
        assertThat(metric.name(), equalTo("http.response.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("HTTP response content length"));
    }

    @Test
    public void shouldResolveHttpResponseSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.response.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpSizeMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldRecordFixedHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.response.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame with Content-Length of 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(recorder, times(1)).accept(42L);
    }

    @Test
    public void shouldRecordDynamicHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.response.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame without Content-Length
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
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
    public void shouldRecordZeroHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.response.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame when header Content-Length is empty
        HttpBeginExFW httpBeginEx1 = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value(""))
                .build();
        AtomicBuffer beginBuffer1 = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer1, 0, beginBuffer1.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx1.buffer(), 0, httpBeginEx1.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer1, 0, beginBuffer1.capacity());

        // end frame
        AtomicBuffer endBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer1, 0, endBuffer1.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer1, 0, endBuffer1.capacity());

        // begin frame when header Content-Length is 0
        HttpBeginExFW httpBeginEx2 = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("0"))
                .build();
        AtomicBuffer beginBuffer2 = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer2, 0, beginBuffer2.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx2.buffer(), 0, httpBeginEx2.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer2, 0, beginBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer2 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer2, 0, endBuffer2.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer2, 0, endBuffer2.capacity());

        // THEN
        verify(recorder, times(2)).accept(0L);
    }

    @Test
    public void shouldNotRecordAbortedHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.request.size");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame with header Content-Length = 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .originId(0L).routedId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
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
    public void shouldResolveHttpActiveRequests()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.active.requests");

        // THEN
        assertThat(metric, instanceOf(HttpActiveRequestsMetric.class));
        assertThat(metric.name(), equalTo("http.active.requests"));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of active HTTP requests"));
    }

    @Test
    public void shouldResolveHttpActiveRequestsContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.active.requests");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpActiveRequestsMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }

    @Test
    public void shouldRecordHttpActiveRequests()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.active.requests");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .typeId(0)
            .headersItem(h -> h.name(":status").value("200"))
            .headersItem(h -> h.name("content-length").value("42"))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
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
    public void shouldResolveHttpDuration()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.duration");

        // THEN
        assertThat(metric, instanceOf(HttpDurationMetric.class));
        assertThat(metric.name(), equalTo("http.duration"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.SECONDS));
        assertThat(metric.description(), equalTo("Duration of HTTP requests"));
    }

    @Test
    public void shouldResolveHttpDurationContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.duration");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpDurationMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }

    @Test
    public void shouldRecordHttpDuration()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext engineContext = mock(EngineContext.class);
        LongConsumer recorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.supply("http.duration");
        MetricContext context = metric.supply(engineContext);
        MessageConsumer handler = context.supply(recorder);

        // begin frame
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .typeId(0)
            .headersItem(h -> h.name(":status").value("200"))
            .headersItem(h -> h.name("content-length").value("42"))
            .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(42_000L)
            .traceId(0L).authorization(0L).affinity(0L)
            .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.accept(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frames
        AtomicBuffer endBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer1, 0, endBuffer1.capacity())
            .originId(0L).routedId(0L).streamId(1L) // received
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(72_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer1, 0, endBuffer1.capacity());
        AtomicBuffer endBuffer2 = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer2, 0, endBuffer2.capacity())
            .originId(0L).routedId(0L).streamId(0L) // sent
            .sequence(0L).acknowledge(0L).maximum(0).timestamp(77_000L)
            .traceId(0L).authorization(0L).build();
        handler.accept(EndFW.TYPE_ID, endBuffer2, 0, endBuffer2.capacity());

        // THEN
        verify(recorder, times(1)).accept(35L);
    }
}
