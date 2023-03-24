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

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2LongCounterMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;
import io.aklivity.zilla.runtime.metrics.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.ResetFW;

public class HttpRequestSizeMetric implements Metric
{
    private static final String NAME = String.format("%s.%s", HttpMetricGroup.NAME, "request.size");

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.HISTOGRAM;
    }

    @Override
    public Unit unit()
    {
        return Unit.BYTES;
    }

    @Override
    public MetricContext supply(
        EngineContext context)
    {
        return new HttpRequestSizeMetricContext();
    }

    private final class HttpRequestSizeMetricContext implements MetricContext
    {
        private final Long2LongCounterMap requestSize = new Long2LongCounterMap(0L);
        private final Long2ObjectHashMap<HttpMetricConsumer> handlers = new Long2ObjectHashMap();
        private final FrameFW frameRO = new FrameFW();
        private final BeginFW beginRO = new BeginFW();
        private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
        private final DataFW dataRO = new DataFW();
        private final EndFW endRO = new EndFW();

        @Override
        public Metric.Kind kind()
        {
            return HttpRequestSizeMetric.this.kind();
        }

        @Override
        public MetricHandler supply(
            LongConsumer recorder)
        {
            return (t, b, i, l) -> handle(recorder, t, b, i, l);
        }

        private void handle(
            LongConsumer recorder,
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            if (isInitial(streamId)) // it's a received stream
            {
                handleInitial(recorder, streamId, msgTypeId, buffer, index, length);
            }
        }

        private void handleInitial(
            LongConsumer recorder,
            long streamId,
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                final HttpHeaderFW contentLength = getContentLength(begin);
                if (isContentLengthValid(contentLength))
                {
                    requestSize.put(streamId, getContentLengthValue(contentLength));
                    handlers.put(streamId, this::handleFixedLength);
                }
                else
                {
                    handlers.put(streamId, this::handleDynamicLength);
                }
                break;
            default:
                HttpMetricConsumer handler = handlers.getOrDefault(streamId, HttpMetricConsumer.NOOP);
                handler.accept(recorder, streamId, msgTypeId, buffer, index, length);
                break;
            }
        }

        private HttpHeaderFW getContentLength(BeginFW begin)
        {
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);
            final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();
            final String8FW httpContentLength = new String8FW("content-length");
            return headers.matchFirst(h -> httpContentLength.equals(h.name()));
        }

        private boolean isContentLengthValid(HttpHeaderFW contentLength)
        {
            return contentLength != null && contentLength.value() != null && contentLength.value().length() != -1;
        }

        private long getContentLengthValue(HttpHeaderFW contentLength)
        {
            DirectBuffer buffer = contentLength.value().value();
            return buffer.parseLongAscii(0, buffer.capacity());
        }

        private void handleDynamicLength(
            LongConsumer recorder,
            long streamId,
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                requestSize.getAndAdd(streamId, data.length());
                break;
            case EndFW.TYPE_ID:
                recorder.accept(requestSize.remove(streamId));
                handlers.remove(streamId);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                requestSize.remove(streamId);
                handlers.remove(streamId);
                break;
            }
        }

        private void handleFixedLength(
            LongConsumer recorder,
            long streamId,
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case EndFW.TYPE_ID:
                recorder.accept(requestSize.remove(streamId));
                handlers.remove(streamId);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                requestSize.remove(streamId);
                handlers.remove(streamId);
                break;
            }
        }
    }

    @FunctionalInterface
    private interface HttpMetricConsumer
    {
        HttpMetricConsumer NOOP = (r, s, m, b, i, l) -> {};

        void accept(LongConsumer recorder, long streamId, int msgTypeId, DirectBuffer buffer, int index, int length);
    }

    private static boolean isInitial(
        long streamId)
    {
        return (streamId & 0x0000_0000_0000_0001L) != 0L;
    }
}
