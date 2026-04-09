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
import org.agrona.collections.Long2LongCounterMap;
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

public final class HttpActiveRequestsMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final FrameFW frameRO = new FrameFW();

    public HttpActiveRequestsMetricContext(
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
        return new HttpActiveRequestsHandler(recorder);
    }

    @Override
    public MessageConsumer supply(
        IntFunction<LongConsumer> recorder,
        List<AttributeConfig> attributes,
        ToIntFunction<String> supplyLabelId)
    {
        HttpAttributeHelper helper = new HttpAttributeHelper(attributes, supplyLabelId);
        return new HttpActiveRequestsWithAttributesHandler(recorder, helper);
    }

    private final class HttpActiveRequestsWithAttributesHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;
        private static final long EXCHANGE_CLOSED = 0b11L;

        private final IntFunction<LongConsumer> recorder;
        private final HttpAttributeHelper attributeHelper;
        private final Long2LongHashMap exchanges;
        private final Long2ObjectHashMap<Map<String, String>> pendingAttributes;
        private final Long2LongCounterMap attributeIds;
        private final BeginFW beginRO = new BeginFW();

        private HttpActiveRequestsWithAttributesHandler(
            IntFunction<LongConsumer> recorder,
            HttpAttributeHelper attributeHelper)
        {
            this.recorder = recorder;
            this.attributeHelper = attributeHelper;
            this.exchanges = new Long2LongHashMap(INITIAL_VALUE);
            this.pendingAttributes = new Long2ObjectHashMap<>();
            this.attributeIds = new Long2LongCounterMap(-1);
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
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                if (direction == 1L) // received (request)
                {
                    Map<String, String> reqAttrs = attributeHelper.extractRequestAttributes(begin);
                    pendingAttributes.put(exchangeId, reqAttrs);
                    int attrId = attributeHelper.computeAttributesId(reqAttrs);
                    attributeIds.put(exchangeId, attrId);
                    recorder.apply(attrId).accept(1L);
                }
                else // sent (response)
                {
                    Map<String, String> attrs = pendingAttributes.getOrDefault(exchangeId,
                        new org.agrona.collections.Object2ObjectHashMap<>());
                    Map<String, String> respAttrs = attributeHelper.extractResponseAttributes(begin);
                    attrs.putAll(respAttrs);
                    int attrId = attributeHelper.computeAttributesId(attrs);
                    attributeIds.put(exchangeId, attrId);
                }
                break;
            case ResetFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case EndFW.TYPE_ID:
                final long mask = 1L << direction;
                final long status = exchanges.get(exchangeId) | mask;
                if (status == EXCHANGE_CLOSED)
                {
                    exchanges.remove(exchangeId);
                    pendingAttributes.remove(exchangeId);
                    int attrId = (int) attributeIds.remove(exchangeId);
                    recorder.apply(attrId).accept(-1L);
                }
                else
                {
                    exchanges.put(exchangeId, status);
                }
                break;
            }
        }
    }

    private final class HttpActiveRequestsHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;
        private static final long EXCHANGE_CLOSED = 0b11L;

        private final LongConsumer recorder;
        private final Long2LongHashMap exchanges;

        private HttpActiveRequestsHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
            this.exchanges = new Long2LongHashMap(INITIAL_VALUE);
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
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                if (direction == 1L) //received
                {
                    recorder.accept(1L);
                }
                break;
            case ResetFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case EndFW.TYPE_ID:
                final long mask = 1L << direction;
                final long status = exchanges.get(exchangeId) | mask;
                if (status == EXCHANGE_CLOSED)
                {
                    exchanges.remove(exchangeId);
                    recorder.accept(-1L);
                }
                else
                {
                    exchanges.put(exchangeId, status);
                }
                break;
            }
        }
    }
}
