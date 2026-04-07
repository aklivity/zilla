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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaCapabilities.PRODUCE_ONLY;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpKafkaProxyFactory implements BindingHandler
{
    private static final String MCP_TYPE_NAME = "mcp";
    private static final String KAFKA_TYPE_NAME = "kafka";

    private static final String TOOL_PRODUCE = "produce";
    private static final String TOOL_CONSUME = "consume";
    private static final String TOOL_LIST_TOPICS = "list_topics";
    private static final String TOOL_DESCRIBE_TOPIC = "describe_topic";
    private static final String TOOL_CREATE_TOPIC = "create_topic";
    private static final String TOOL_DELETE_TOPIC = "delete_topic";
    private static final String TOOL_LIST_CONSUMER_GROUPS = "list_consumer_groups";
    private static final String TOOL_DESCRIBE_CONSUMER_GROUP = "describe_consumer_group";
    private static final String TOOL_RESET_OFFSETS = "reset_offsets";
    private static final String TOOL_LIST_BROKERS = "list_brokers";
    private static final String TOOL_DESCRIBE_CLUSTER = "describe_cluster";
    private static final String TOOL_CLUSTER_OVERVIEW = "cluster_overview";
    private static final String TOOL_DESCRIBE_CONFIGS = "describe_configs";
    private static final String TOOL_ALTER_CONFIGS = "alter_configs";

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int mcpTypeId;
    private final int kafkaTypeId;

    private final Long2ObjectHashMap<McpKafkaBindingConfig> bindings;

    public McpKafkaProxyFactory(
        McpKafkaConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
    }

    @Override
    public int originTypeId()
    {
        return mcpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return kafkaTypeId;
    }

    public void attach(
        BindingConfig binding)
    {
        McpKafkaBindingConfig newBinding = new McpKafkaBindingConfig(binding);
        bindings.put(binding.id, newBinding);
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        final McpKafkaBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            String tool = null;

            final McpBeginExFW mcpBeginEx = mcpBeginExRO.tryWrap(begin.extension().buffer(),
                begin.extension().offset(), begin.extension().limit());

            if (mcpBeginEx != null)
            {
                tool = mcpBeginEx.kind().asString();
            }

            final McpKafkaRouteConfig route = binding.resolve(authorization, tool);

            if (route != null)
            {
                final McpProxy mcpProxy = new McpProxy(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    route.id,
                    affinity,
                    authorization,
                    tool);
                newStream = mcpProxy::onMcpMessage;
            }
        }

        return newStream;
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        long budgetId,
        int flags,
        int reserved,
        DirectBuffer payload,
        int offset,
        int length)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .flags(flags)
            .payload(payload, offset, length)
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        long budgetId,
        int credit,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(credit)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private KafkaBeginExFW buildKafkaBeginEx(
        String tool,
        String topic)
    {
        final KafkaBeginExFW.Builder builder = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
            .typeId(kafkaTypeId);

        switch (tool != null ? tool : "")
        {
        case TOOL_PRODUCE:
            builder.merged(m -> m
                .capabilities(c -> c.set(PRODUCE_ONLY))
                .topic(topic != null ? topic : "")
                .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L)));
            break;
        case TOOL_CONSUME:
            builder.merged(m -> m
                .capabilities(c -> c.set(FETCH_ONLY))
                .topic(topic != null ? topic : "")
                .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L)));
            break;
        case TOOL_LIST_TOPICS:
        case TOOL_DESCRIBE_TOPIC:
            builder.meta(m -> m
                .topic(topic != null ? topic : ""));
            break;
        case TOOL_CREATE_TOPIC:
            builder.request(r -> r.createTopics(ct -> ct
                .topicsItem(t -> t
                    .name(topic != null ? topic : "")
                    .numPartitions(1)
                    .replicationFactor((short) 1))));
            break;
        case TOOL_DELETE_TOPIC:
            builder.request(r -> r.deleteTopics(dt -> dt
                .topicsItem(t -> t.name(topic != null ? topic : ""))));
            break;
        case TOOL_LIST_CONSUMER_GROUPS:
            builder.request(r -> r.listGroups(lg -> {}));
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP:
            builder.request(r -> r.describeGroups(dg -> dg
                .groupsItem(g -> g.value(topic != null ? topic : ""))));
            break;
        case TOOL_RESET_OFFSETS:
            builder.request(r -> r.alterConsumerGroupOffsets(aco -> aco
                .groupId(topic != null ? topic : "")));
            break;
        case TOOL_LIST_BROKERS:
        case TOOL_DESCRIBE_CLUSTER:
        case TOOL_CLUSTER_OVERVIEW:
            builder.request(r -> r.describeCluster(dc -> {}));
            break;
        case TOOL_DESCRIBE_CONFIGS:
            builder.describe(d -> d
                .name(topic != null ? topic : ""));
            break;
        case TOOL_ALTER_CONFIGS:
            builder.request(r -> r.alterConfigs(ac -> ac
                .resourcesItem(res -> res
                    .type((byte) 2)
                    .name(topic != null ? topic : ""))));
            break;
        default:
            builder.merged(m -> m
                .capabilities(c -> c.set(PRODUCE_ONLY))
                .topic(topic != null ? topic : "")
                .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L)));
            break;
        }

        return builder.build();
    }

    private final class McpProxy
    {
        private final MessageConsumer mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final String tool;

        private KafkaProxy kafka;
        private int state;

        private McpProxy(
            MessageConsumer mcp,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String tool)
        {
            this.mcp = mcp;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.tool = tool;
        }

        private void onMcpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMcpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMcpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMcpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMcpAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMcpWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMcpReset(reset);
                break;
            default:
                break;
            }
        }

        private void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = buildKafkaBeginEx(tool, null);

            kafka = new KafkaProxy(this, originId, resolvedId, affinity, authorization);
            kafka.doKafkaBegin(traceId, kafkaBeginEx);

            doMcpWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onMcpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final DirectBuffer payload = data.payload();

            if (kafka != null && payload != null)
            {
                kafka.doKafkaData(traceId, budgetId, flags, reserved, payload, 0, payload.capacity());
            }
        }

        private void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpKafkaState.closedInitial(state);

            if (kafka != null)
            {
                kafka.doKafkaEnd(traceId);
            }
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpKafkaState.closedInitial(state);

            if (kafka != null)
            {
                kafka.doKafkaAbort(traceId);
            }
        }

        private void onMcpWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            if (kafka != null)
            {
                kafka.doKafkaWindow(traceId, budgetId, credit, padding);
            }
        }

        private void onMcpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedReply(state);

            if (kafka != null)
            {
                kafka.doKafkaReset(traceId);
            }
        }

        private void doMcpBegin(
            long traceId)
        {
            if (!McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.openedReply(state);
                doBegin(mcp, originId, routedId, replyId, traceId, authorization, affinity, emptyRO);
            }
        }

        private void doMcpData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            doData(mcp, originId, routedId, replyId, traceId, authorization,
                budgetId, flags, reserved, payload, offset, length);
        }

        private void doMcpEnd(
            long traceId)
        {
            if (!McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doEnd(mcp, originId, routedId, replyId, traceId, authorization);
            }
        }

        private void doMcpAbort(
            long traceId)
        {
            if (!McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doAbort(mcp, originId, routedId, replyId, traceId, authorization);
            }
        }

        private void doMcpWindow(
            long traceId,
            long budgetId,
            int credit,
            int padding)
        {
            doWindow(mcp, originId, routedId, replyId, traceId, authorization, budgetId, credit, padding);
        }

        private void doMcpReset(
            long traceId)
        {
            if (!McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doReset(mcp, originId, routedId, replyId, traceId, authorization);
            }
        }
    }

    private final class KafkaProxy
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final MessageConsumer kafka;

        private int state;

        private KafkaProxy(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.kafka = streamFactory.newStream(BeginFW.TYPE_ID, writeBuffer, 0, 0, this::onKafkaMessage);
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            default:
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final DirectBuffer payload = data.payload();

            if (payload != null)
            {
                peer.doMcpData(traceId, budgetId, flags, reserved, payload, 0, payload.capacity());
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpKafkaState.closedReply(state);

            peer.doMcpEnd(traceId);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpKafkaState.closedReply(state);

            peer.doMcpAbort(traceId);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            peer.doMcpWindow(traceId, budgetId, credit, padding);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            peer.doMcpReset(traceId);
        }

        private void doKafkaBegin(
            long traceId,
            KafkaBeginExFW extension)
        {
            if (kafka != null)
            {
                state = McpKafkaState.openingInitial(state);
                doBegin(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, affinity, extension);
            }
        }

        private void doKafkaData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            if (kafka != null)
            {
                doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                    budgetId, flags, reserved, payload, offset, length);
            }
        }

        private void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long budgetId,
            int credit,
            int padding)
        {
            if (kafka != null)
            {
                doWindow(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization, budgetId, credit, padding);
            }
        }

        private void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }
}
