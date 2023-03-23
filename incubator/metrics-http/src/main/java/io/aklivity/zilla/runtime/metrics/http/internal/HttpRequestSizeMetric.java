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
import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;
import io.aklivity.zilla.runtime.metrics.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpBeginExFW;

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
        private static final long ALREADY_PROCESSED_STREAM = -1L;

        private final Long2LongHashMap requestSize = new Long2LongHashMap(0L);
        private final BeginFW beginRO = new BeginFW();
        private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
        private final DataFW dataRO = new DataFW();
        private final EndFW endRO = new EndFW();

        @Override
        public Metric metric()
        {
            return HttpRequestSizeMetric.this;
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
            long streamId;
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                streamId = begin.streamId();
                if (!isInitial(streamId)) // not a received stream
                {
                    break;
                }
                final HttpHeaderFW contentLength = getContentLength(begin);
                if (isContentLengthValid(contentLength))
                {
                    recorder.accept(getContentLengthValue(contentLength));
                    requestSize.put(streamId, ALREADY_PROCESSED_STREAM);
                }
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                streamId = data.streamId();
                if (!isInitial(streamId)) // not a received stream
                {
                    break;
                }
                if (requestSize.get(streamId) != ALREADY_PROCESSED_STREAM)
                {
                    requestSize.put(streamId, requestSize.get(streamId) + data.length());
                }
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                streamId = end.streamId();
                if (!isInitial(streamId)) // not a received stream
                {
                    break;
                }
                if (requestSize.get(streamId) == ALREADY_PROCESSED_STREAM)
                {
                    requestSize.remove(streamId);
                }
                else
                {
                    recorder.accept(requestSize.get(streamId));
                }
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
            return contentLength != null && contentLength.value() != null && contentLength.value().asString() != null;
        }

        private long getContentLengthValue(HttpHeaderFW contentLength)
        {
            return Long.parseLong(contentLength.value().asString());
        }
    }

    private static boolean isInitial(
        long streamId)
    {
        return (streamId & 0x0000_0000_0000_0001L) != 0L;
    }
}
