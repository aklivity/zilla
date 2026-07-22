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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.BOTH;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.McpMeasure.DURATION;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.McpUtils.initialId;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToIntFunction;

import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpEndExFW;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpOutcome;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.ResetFW;

public final class McpMetricContext implements MetricContext
{
    private static final String OUTCOME_OK = "ok";
    private static final String OUTCOME_ERROR = "error";
    private static final long OUTCOME_OK_CODE = 0L;
    private static final long OUTCOME_ERROR_CODE = 1L;

    private final String group;
    private final String name;
    private final Metric.Kind kind;
    private final McpMethod method;
    private final McpMeasure measure;
    private final ToIntFunction<String> supplyLabelId;
    private final FrameFW frameRO = new FrameFW();

    McpMetricContext(
        String group,
        String name,
        Metric.Kind kind,
        McpMethod method,
        McpMeasure measure,
        ToIntFunction<String> supplyLabelId)
    {
        this.group = group;
        this.name = name;
        this.kind = kind;
        this.method = method;
        this.measure = measure;
        this.supplyLabelId = supplyLabelId;
    }

    String name()
    {
        return name;
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
        return new McpMetricHandler(attrId -> recorder, null);
    }

    @Override
    public MessageConsumer supply(
        IntFunction<LongConsumer> recorder,
        List<AttributeConfig> attributes)
    {
        McpAttributeHelper helper = new McpAttributeHelper(attributes, supplyLabelId);
        return new McpMetricHandler(recorder, helper);
    }

    private final class McpMetricHandler implements MessageConsumer
    {
        private static final long NOT_TRACKED = -1L;
        private static final long INITIAL_TIMESTAMP = 0L;
        private static final long EXCHANGE_CLOSED = 0b11L;

        private final IntFunction<LongConsumer> recorder;
        private final McpAttributeHelper helper;
        private final Long2LongHashMap masks;
        private final Long2LongHashMap timestamps;
        private final Long2LongHashMap outcomes;
        private final Long2ObjectHashMap<Map<String, String>> pending;
        private final BeginFW beginRO = new BeginFW();
        private final EndFW endRO = new EndFW();
        private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
        private final McpEndExFW mcpEndExRO = new McpEndExFW();

        private McpMetricHandler(
            IntFunction<LongConsumer> recorder,
            McpAttributeHelper helper)
        {
            this.recorder = recorder;
            this.helper = helper;
            this.masks = new Long2LongHashMap(NOT_TRACKED);
            this.timestamps = new Long2LongHashMap(INITIAL_TIMESTAMP);
            this.outcomes = new Long2LongHashMap(OUTCOME_OK_CODE);
            this.pending = new Long2ObjectHashMap<>();
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            final long exchangeId = initialId(streamId);
            final long streamDirection = McpUtils.direction(streamId);
            final long timestamp = frame.timestamp();

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                if (streamDirection == 1L)
                {
                    onRequestBegin(exchangeId, timestamp, buffer, index, length);
                }
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                final McpEndExFW mcpEndEx = end.extension().get(mcpEndExRO::tryWrap);
                final boolean errored = mcpEndEx != null && mcpEndEx.outcome().get() == McpOutcome.ERROR;
                onClose(exchangeId, streamDirection, timestamp, errored);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                onAbort(exchangeId, timestamp);
                break;
            }
        }

        private void onRequestBegin(
            long exchangeId,
            long timestamp,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final BeginFW begin = beginRO.wrap(buffer, index, index + length);
            final OctetsFW extension = begin.extension();
            final McpBeginExFW mcpBeginEx = extension.get(mcpBeginExRO::tryWrap);
            if (mcpBeginEx != null && mcpBeginEx.kind() == method.kind())
            {
                masks.put(exchangeId, 0L);
                if (timestamp != INITIAL_TIMESTAMP)
                {
                    timestamps.put(exchangeId, timestamp);
                }
                if (helper != null)
                {
                    pending.put(exchangeId, helper.extractBeginAttributes(mcpBeginEx, method));
                }
            }
        }

        private void onClose(
            long exchangeId,
            long streamDirection,
            long timestamp,
            boolean errored)
        {
            final long current = masks.get(exchangeId);
            if (current != NOT_TRACKED)
            {
                if (errored)
                {
                    outcomes.put(exchangeId, OUTCOME_ERROR_CODE);
                }
                final long status = current | 1L << streamDirection;
                if (status == EXCHANGE_CLOSED)
                {
                    String outcome = outcomes.get(exchangeId) == OUTCOME_ERROR_CODE ? OUTCOME_ERROR : OUTCOME_OK;
                    record(exchangeId, outcome, timestamp);
                    cleanup(exchangeId);
                }
                else
                {
                    masks.put(exchangeId, status);
                }
            }
        }

        private void onAbort(
            long exchangeId,
            long timestamp)
        {
            if (masks.get(exchangeId) != NOT_TRACKED)
            {
                record(exchangeId, OUTCOME_ERROR, timestamp);
                cleanup(exchangeId);
            }
        }

        private void record(
            long exchangeId,
            String outcome,
            long timestamp)
        {
            int attrId = 0;
            if (helper != null)
            {
                Map<String, String> attributes = pending.getOrDefault(exchangeId, new Object2ObjectHashMap<>());
                helper.applyOutcome(attributes, outcome);
                attrId = helper.computeAttributesId(attributes);
            }

            if (measure == DURATION)
            {
                final long start = timestamps.get(exchangeId);
                if (start != INITIAL_TIMESTAMP)
                {
                    recorder.apply(attrId).accept(timestamp - start);
                }
            }
            else
            {
                recorder.apply(attrId).accept(1L);
            }
        }

        private void cleanup(
            long exchangeId)
        {
            masks.remove(exchangeId);
            timestamps.remove(exchangeId);
            outcomes.remove(exchangeId);
            pending.remove(exchangeId);
        }
    }
}
