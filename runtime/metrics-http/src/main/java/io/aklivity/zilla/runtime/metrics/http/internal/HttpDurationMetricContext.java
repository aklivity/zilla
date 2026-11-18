/*
 * Copyright 2021-2026 Aklivity Inc
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

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.ResetFW;

public final class HttpDurationMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final ToIntFunction<String> supplyLabelId;
    private final FrameFW frameRO = new FrameFW();

    public HttpDurationMetricContext(
        String group,
        Metric.Kind kind,
        ToIntFunction<String> supplyLabelId)
    {
        this.group = group;
        this.kind = kind;
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
        List<AttributeConfig> attributes)
    {
        HttpAttributeHelper helper = new HttpAttributeHelper(attributes, supplyLabelId);
        return new HttpDurationWithAttributesHandler(recorder, helper);
    }

    private final class HttpDurationWithAttributesHandler implements MessageConsumer
    {
        private final IntFunction<LongConsumer> recorder;
        private final HttpAttributeHelper attributeHelper;
        private final HttpExchanges exchanges;
        private final Long2ObjectHashMap<Map<String, String>> pendingAttributes;
        private final BeginFW beginRO = new BeginFW();
        private final ResetFW resetRO = new ResetFW();

        private HttpDurationWithAttributesHandler(
            IntFunction<LongConsumer> recorder,
            HttpAttributeHelper attributeHelper)
        {
            this.recorder = recorder;
            this.attributeHelper = attributeHelper;
            this.exchanges = new HttpExchanges();
            this.pendingAttributes = new Long2ObjectHashMap<>();
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long exchangeId = initialId(frame.streamId());
            switch (exchanges.onFrame(msgTypeId, frame.streamId(), frame.timestamp()))
            {
            case REQUEST:
                pendingAttributes.put(exchangeId,
                    attributeHelper.extractRequestAttributes(beginRO.wrap(buffer, index, index + length)));
                break;
            case RESPONSE:
                Map<String, String> attributes = pendingAttributes.get(exchangeId);
                if (attributes != null)
                {
                    attributes.putAll(attributeHelper.extractResponseAttributes(beginRO.wrap(buffer, index, index + length)));
                }
                break;
            case COMPLETED:
                record(pendingAttributes.remove(exchangeId));
                break;
            case REJECTED:
                Map<String, String> rejected = pendingAttributes.remove(exchangeId);
                if (rejected != null)
                {
                    rejected.putAll(attributeHelper.extractRejectedAttributes(resetRO.wrap(buffer, index, index + length)));
                }
                record(rejected);
                break;
            case DISCARDED:
                pendingAttributes.remove(exchangeId);
                break;
            default:
                break;
            }
        }

        private void record(
            Map<String, String> attributes)
        {
            int attributesId = attributes != null ? attributeHelper.computeAttributesId(attributes) : 0;
            recorder.apply(attributesId).accept(exchanges.duration());
        }
    }

    private final class HttpDurationHandler implements MessageConsumer
    {
        private final LongConsumer recorder;
        private final HttpExchanges exchanges;

        private HttpDurationHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
            this.exchanges = new HttpExchanges();
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final HttpExchanges.Event event = exchanges.onFrame(msgTypeId, frame.streamId(), frame.timestamp());
            if (event == HttpExchanges.Event.COMPLETED || event == HttpExchanges.Event.REJECTED)
            {
                recorder.accept(exchanges.duration());
            }
        }
    }
}
