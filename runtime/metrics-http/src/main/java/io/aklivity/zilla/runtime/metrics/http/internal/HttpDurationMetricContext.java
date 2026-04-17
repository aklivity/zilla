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

import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.BOTH;
import static io.aklivity.zilla.runtime.metrics.http.internal.HttpUtils.initialId;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToIntFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.ResetFW;

public final class HttpDurationMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final FrameFW frameRO = new FrameFW();

    public HttpDurationMetricContext(
        String group,
        Metric.Kind kind)
    {
        this.group = group;
        this.kind = kind;
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
        return BOTH;
    }

    @Override
    public MessageConsumer supply(
        LongConsumer recorder)
    {
        return new HttpDurationHandler(recorder);
    }

    @Override
    public MessageConsumer supply(
        IntFunction<LongConsumer> recorder,
        List<AttributeConfig> attributes,
        ToIntFunction<String> supplyLabelId)
    {
        HttpAttributeHelper helper = new HttpAttributeHelper(attributes, supplyLabelId);
        return new HttpDurationWithAttributesHandler(recorder, helper);
    }

    private final class HttpDurationWithAttributesHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;
        private static final long EXCHANGE_CLOSED = 0b11L;

        private final IntFunction<LongConsumer> recorder;
        private final HttpAttributeHelper attributeHelper;
        private final Long2LongHashMap exchanges;
        private final Long2LongHashMap timestamps;
        private final Long2ObjectHashMap<Map<String, String>> pendingAttributes;
        private final BeginFW beginRO = new BeginFW();

        private HttpDurationWithAttributesHandler(
            IntFunction<LongConsumer> recorder,
            HttpAttributeHelper attributeHelper)
        {
            this.recorder = recorder;
            this.attributeHelper = attributeHelper;
            this.exchanges = new Long2LongHashMap(INITIAL_VALUE);
            this.timestamps = new Long2LongHashMap(INITIAL_VALUE);
            this.pendingAttributes = new Long2ObjectHashMap<>();
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            final long exchangeId = initialId(streamId);
            long direction = HttpUtils.direction(streamId);
            long timestamp = frame.timestamp();
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                if (direction == 1L) // received (request)
                {
                    if (timestamp != INITIAL_VALUE)
                    {
                        timestamps.put(exchangeId, timestamp);
                    }
                    Map<String, String> reqAttrs = attributeHelper.extractRequestAttributes(begin);
                    pendingAttributes.put(exchangeId, reqAttrs);
                }
                else // sent (response)
                {
                    Map<String, String> attrs = pendingAttributes.getOrDefault(exchangeId,
                        new org.agrona.collections.Object2ObjectHashMap<>());
                    Map<String, String> respAttrs = attributeHelper.extractResponseAttributes(begin);
                    attrs.putAll(respAttrs);
                    pendingAttributes.put(exchangeId, attrs);
                }
                break;
            case ResetFW.TYPE_ID:
            case AbortFW.TYPE_ID:
                exchanges.remove(exchangeId);
                timestamps.remove(exchangeId);
                pendingAttributes.remove(exchangeId);
                break;
            case EndFW.TYPE_ID:
                final long mask = 1L << direction;
                final long status = exchanges.get(exchangeId) | mask;
                if (status == EXCHANGE_CLOSED)
                {
                    exchanges.remove(exchangeId);
                    long start = timestamps.remove(exchangeId);
                    Map<String, String> attrs = pendingAttributes.remove(exchangeId);
                    if (start != INITIAL_VALUE)
                    {
                        int attrId = attrs != null ? attributeHelper.computeAttributesId(attrs) : 0;
                        long dur = timestamp - start;
                        recorder.apply(attrId).accept(dur);
                    }
                }
                else if (timestamps.containsKey(exchangeId))
                {
                    exchanges.put(exchangeId, status);
                }
                break;
            }
        }
    }

    private final class HttpDurationHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;
        private static final long EXCHANGE_CLOSED = 0b11L;

        private final LongConsumer recorder;
        private final Long2LongHashMap exchanges;
        private final Long2LongHashMap timestamps;

        private HttpDurationHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
            this.exchanges = new Long2LongHashMap(INITIAL_VALUE);
            this.timestamps = new Long2LongHashMap(INITIAL_VALUE);
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            final long exchangeId = initialId(streamId);
            long direction = HttpUtils.direction(streamId);
            long timestamp = frame.timestamp();
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                if (direction == 1L && timestamp != INITIAL_VALUE) //received stream
                {
                    timestamps.put(exchangeId, timestamp);
                }
                break;
            case ResetFW.TYPE_ID:
            case AbortFW.TYPE_ID:
                exchanges.remove(exchangeId);
                timestamps.remove(exchangeId);
                break;
            case EndFW.TYPE_ID:
                final long mask = 1L << direction;
                final long status = exchanges.get(exchangeId) | mask; // mark current direction as closed
                if (status == EXCHANGE_CLOSED) // both received and sent streams are closed
                {
                    exchanges.remove(exchangeId);
                    long start = timestamps.remove(exchangeId);
                    if (start != INITIAL_VALUE)
                    {
                        long duration = timestamp - start;
                        recorder.accept(duration);
                    }
                }
                else if (timestamps.containsKey(exchangeId)) // prevent memory leak if already aborted
                {
                    exchanges.put(exchangeId, status);
                }
                break;
            }
        }
    }
}
