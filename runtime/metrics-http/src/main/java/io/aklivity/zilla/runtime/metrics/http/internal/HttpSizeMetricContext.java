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
package io.aklivity.zilla.runtime.metrics.http.internal;

import static io.aklivity.zilla.runtime.metrics.http.internal.HttpUtils.INVALID_CONTENT_LENGTH;
import static io.aklivity.zilla.runtime.metrics.http.internal.HttpUtils.findContentLength;
import static io.aklivity.zilla.runtime.metrics.http.internal.HttpUtils.parseContentLength;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToIntFunction;

import org.agrona.collections.Long2LongCounterMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.ResetFW;

public final class HttpSizeMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final Direction direction;
    private final ToIntFunction<String> supplyLabelId;
    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();

    public HttpSizeMetricContext(
        String group,
        Metric.Kind kind,
        Direction direction,
        ToIntFunction<String> supplyLabelId)
    {
        this.group = group;
        this.kind = kind;
        this.direction = direction;
        this.supplyLabelId = supplyLabelId;
    }

    @Override
    public String group()
    {
        return group;
    }

    @Override
    public Metric.Kind kind()
    {
        return kind;
    }

    @Override
    public Direction direction()
    {
        return direction;
    }

    @Override
    public MessageConsumer supply(
        LongConsumer recorder)
    {
        return new HttpSizeHandler(recorder);
    }

    @Override
    public MessageConsumer supply(
        IntFunction<LongConsumer> recorder,
        List<AttributeConfig> attributes)
    {
        HttpAttributeHelper helper = new HttpAttributeHelper(attributes, supplyLabelId);
        return new HttpSizeWithAttributesHandler(recorder, helper);
    }

    private final class HttpSizeWithAttributesHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;

        private final IntFunction<LongConsumer> recorder;
        private final HttpAttributeHelper attributeHelper;
        private final Long2LongCounterMap requestSize = new Long2LongCounterMap(INITIAL_VALUE);
        private final Long2ObjectHashMap<HttpMetricConsumer> handlers = new Long2ObjectHashMap<>();
        private final Long2LongCounterMap attributeIds = new Long2LongCounterMap(-1);

        private HttpSizeWithAttributesHandler(
            IntFunction<LongConsumer> recorder,
            HttpAttributeHelper attributeHelper)
        {
            this.recorder = recorder;
            this.attributeHelper = attributeHelper;
        }

        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                boolean isRequest = direction == Direction.RECEIVED;
                Map<String, String> attrs = isRequest
                    ? attributeHelper.extractRequestAttributes(begin)
                    : attributeHelper.extractResponseAttributes(begin);
                int attrId = attributeHelper.computeAttributesId(attrs);
                attributeIds.put(streamId, attrId);
                final HttpHeaderFW contentLengthHeader = findContentLength(begin);
                long contentLength = parseContentLength(contentLengthHeader);
                if (contentLength == INVALID_CONTENT_LENGTH)
                {
                    handlers.put(streamId, this::handleDynamicLength);
                }
                else
                {
                    if (contentLength != INITIAL_VALUE)
                    {
                        requestSize.put(streamId, contentLength);
                    }
                    handlers.put(streamId, this::handleFixedLength);
                }
                break;
            default:
                HttpMetricConsumer handler = handlers.getOrDefault(streamId, HttpMetricConsumer.NOOP);
                handler.accept(null, streamId, msgTypeId, buffer, index, length);
                break;
            }
        }

        private void handleFixedLength(
            LongConsumer ignored,
            long streamId,
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case EndFW.TYPE_ID:
                int attrId = (int) attributeIds.remove(streamId);
                recorder.apply(attrId).accept(requestSize.remove(streamId));
                handlers.remove(streamId);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                requestSize.remove(streamId);
                attributeIds.remove(streamId);
                handlers.remove(streamId);
                break;
            }
        }

        private void handleDynamicLength(
            LongConsumer ignored,
            long streamId,
            int msgTypeId,
            DirectBufferEx buffer,
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
                int attrId = (int) attributeIds.remove(streamId);
                recorder.apply(attrId).accept(requestSize.remove(streamId));
                handlers.remove(streamId);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                requestSize.remove(streamId);
                attributeIds.remove(streamId);
                handlers.remove(streamId);
                break;
            }
        }
    }

    private final class HttpSizeHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;

        private final LongConsumer recorder;
        private final Long2LongCounterMap requestSize = new Long2LongCounterMap(INITIAL_VALUE);
        private final Long2ObjectHashMap<HttpMetricConsumer> handlers = new Long2ObjectHashMap<>();

        private HttpSizeHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
        }

        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                final HttpHeaderFW contentLengthHeader = findContentLength(begin);
                long contentLength = parseContentLength(contentLengthHeader);
                if (contentLength == INVALID_CONTENT_LENGTH)
                {
                    handlers.put(streamId, this::handleDynamicLength);
                }
                else
                {
                    if (contentLength != INITIAL_VALUE)
                    {
                        requestSize.put(streamId, contentLength);
                    }
                    handlers.put(streamId, this::handleFixedLength);
                }
                break;
            default:
                HttpMetricConsumer handler = handlers.getOrDefault(streamId, HttpMetricConsumer.NOOP);
                handler.accept(recorder, streamId, msgTypeId, buffer, index, length);
                break;
            }
        }

        private void handleFixedLength(
            LongConsumer recorder,
            long streamId,
            int msgTypeId,
            DirectBufferEx buffer,
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

        private void handleDynamicLength(
            LongConsumer recorder,
            long streamId,
            int msgTypeId,
            DirectBufferEx buffer,
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
    }
}
