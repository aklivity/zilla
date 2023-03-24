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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.LongConsumer;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpBeginExFW;

public class HttpMetricGroupTest
{
    @Test
    public void shouldResolveHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.resolve("http.request.size");

        // THEN
        assertThat(metric, instanceOf(HttpRequestSizeMetric.class));
        assertThat(metric.name(), equalTo("http.request.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
    }

    @Test
    public void shouldRecordFixedHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext mockEngineContext = mock(EngineContext.class);
        LongConsumer mockRecorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.resolve("http.request.size");
        MetricContext context = metric.supply(mockEngineContext);
        MetricHandler handler = context.supply(mockRecorder);

        // begin frame with header Content-Length = 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.onEvent(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.onEvent(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(mockRecorder, times(1)).accept(42L);
    }

    @Test
    public void shouldRecordDynamicHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext mockEngineContext = mock(EngineContext.class);
        LongConsumer mockRecorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.resolve("http.request.size");
        MetricContext context = metric.supply(mockEngineContext);
        MetricHandler handler = context.supply(mockRecorder);

        // begin frame without Content-Length
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.onEvent(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // data frame with 33 bytes length
        AtomicBuffer dataBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        AtomicBuffer payload1 = new UnsafeBuffer(new byte[33], 0, 33);
        new DataFW.Builder().wrap(dataBuffer1, 0, 128).routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload1, 0, 33).build();
        handler.onEvent(DataFW.TYPE_ID, dataBuffer1, 0, dataBuffer1.capacity());

        // data frame with 44 bytes length
        AtomicBuffer dataBuffer2 = new UnsafeBuffer(new byte[128], 0, 128);
        AtomicBuffer payload2 = new UnsafeBuffer(new byte[44], 0, 44);
        new DataFW.Builder().wrap(dataBuffer2, 0, 128).routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload2, 0, 44).build();
        handler.onEvent(DataFW.TYPE_ID, dataBuffer2, 0, dataBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[64], 0, 64);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.onEvent(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(mockRecorder, times(1)).accept(77L);
    }

    @Test
    public void shouldNotRecordAbortedHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext mockEngineContext = mock(EngineContext.class);
        LongConsumer mockRecorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.resolve("http.request.size");
        MetricContext context = metric.supply(mockEngineContext);
        MetricHandler handler = context.supply(mockRecorder);

        // begin frame with header Content-Length = 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.onEvent(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // abort frame
        AtomicBuffer abortBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new AbortFW.Builder().wrap(abortBuffer, 0, abortBuffer.capacity())
                .routeId(0L).streamId(1L) // received
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.onEvent(AbortFW.TYPE_ID, abortBuffer, 0, abortBuffer.capacity());

        // THEN
        verify(mockRecorder, never()).accept(anyLong());
    }

    @Test
    public void shouldResolveHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.resolve("http.response.size");

        // THEN
        assertThat(metric, instanceOf(HttpResponseSizeMetric.class));
        assertThat(metric.name(), equalTo("http.response.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
    }

    @Test
    public void shouldRecordFixedHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext mockEngineContext = mock(EngineContext.class);
        LongConsumer mockRecorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.resolve("http.response.size");
        MetricContext context = metric.supply(mockEngineContext);
        MetricHandler handler = context.supply(mockRecorder);

        // begin frame with Content-Length of 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.onEvent(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.onEvent(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(mockRecorder, times(1)).accept(42L);
    }

    @Test
    public void shouldRecordDynamicHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext mockEngineContext = mock(EngineContext.class);
        LongConsumer mockRecorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.resolve("http.response.size");
        MetricContext context = metric.supply(mockEngineContext);
        MetricHandler handler = context.supply(mockRecorder);

        // begin frame without Content-Length
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.onEvent(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // data frame with 33 bytes length
        AtomicBuffer dataBuffer1 = new UnsafeBuffer(new byte[128], 0, 128);
        AtomicBuffer payload1 = new UnsafeBuffer(new byte[33], 0, 33);
        new DataFW.Builder().wrap(dataBuffer1, 0, 128).routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload1, 0, 33).build();
        handler.onEvent(DataFW.TYPE_ID, dataBuffer1, 0, dataBuffer1.capacity());

        // data frame with 44 bytes length
        AtomicBuffer dataBuffer2 = new UnsafeBuffer(new byte[128], 0, 128);
        AtomicBuffer payload2 = new UnsafeBuffer(new byte[44], 0, 44);
        new DataFW.Builder().wrap(dataBuffer2, 0, 128).routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).budgetId(0L).reserved(0)
                .payload(payload2, 0, 44).build();
        handler.onEvent(DataFW.TYPE_ID, dataBuffer2, 0, dataBuffer2.capacity());

        // end frame
        AtomicBuffer endBuffer = new UnsafeBuffer(new byte[64], 0, 64);
        new EndFW.Builder().wrap(endBuffer, 0, endBuffer.capacity())
                .routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.onEvent(EndFW.TYPE_ID, endBuffer, 0, endBuffer.capacity());

        // THEN
        verify(mockRecorder, times(1)).accept(77L);
    }

    @Test
    public void shouldNotRecordAbortedHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        EngineContext mockEngineContext = mock(EngineContext.class);
        LongConsumer mockRecorder = mock(LongConsumer.class);

        // WHEN
        Metric metric = metricGroup.resolve("http.request.size");
        MetricContext context = metric.supply(mockEngineContext);
        MetricHandler handler = context.supply(mockRecorder);

        // begin frame with header Content-Length = 42
        HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .typeId(0)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-length").value("42"))
                .build();
        AtomicBuffer beginBuffer = new UnsafeBuffer(new byte[256], 0, 256);
        new BeginFW.Builder().wrap(beginBuffer, 0, beginBuffer.capacity())
                .routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).affinity(0L)
                .extension(httpBeginEx.buffer(), 0, httpBeginEx.buffer().capacity()).build();
        handler.onEvent(BeginFW.TYPE_ID, beginBuffer, 0, beginBuffer.capacity());

        // abort frame
        AtomicBuffer abortBuffer = new UnsafeBuffer(new byte[128], 0, 128);
        new AbortFW.Builder().wrap(abortBuffer, 0, abortBuffer.capacity())
                .routeId(0L).streamId(2L) // sent
                .sequence(0L).acknowledge(0L).maximum(0).timestamp(0L)
                .traceId(0L).authorization(0L).build();
        handler.onEvent(AbortFW.TYPE_ID, abortBuffer, 0, abortBuffer.capacity());

        // THEN
        verify(mockRecorder, never()).accept(anyLong());
    }
}
