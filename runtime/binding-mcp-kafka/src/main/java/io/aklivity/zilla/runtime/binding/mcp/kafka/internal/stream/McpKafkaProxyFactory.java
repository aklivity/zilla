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
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaArguments;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaConsumeResult;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaResourceType;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaMergedFetchDataExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpEndExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpOutcome;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpResetExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class McpKafkaProxyFactory implements BindingHandler
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

    private static final String[] TOOL_NAMES =
    {
        TOOL_PRODUCE,
        TOOL_CONSUME,
        TOOL_LIST_TOPICS,
        TOOL_DESCRIBE_TOPIC,
        TOOL_CREATE_TOPIC,
        TOOL_DELETE_TOPIC,
        TOOL_LIST_CONSUMER_GROUPS,
        TOOL_DESCRIBE_CONSUMER_GROUP,
        TOOL_RESET_OFFSETS,
        TOOL_LIST_BROKERS,
        TOOL_DESCRIBE_CLUSTER,
        TOOL_CLUSTER_OVERVIEW,
        TOOL_DESCRIBE_CONFIGS,
        TOOL_ALTER_CONFIGS
    };

    private static final int CAPABILITIES_TOOLS = 1;
    private static final int FLAGS_INIT = 0x01;
    private static final int FLAGS_FIN = 0x02;
    private static final int FLAGS_COMPLETE = 0x03;

    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final String ERROR_MESSAGE_INVALID_PARAMS = "Invalid params";

    private static final int CONSUME_TIMEOUT_SIGNAL_ID = 1;
    private static final long DEFAULT_CONSUME_TIMEOUT_MILLIS = 30_000L;

    private static final int KAFKA_ERROR_INVALID_RECORD = 87;

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(0L, 0), 0, 0);
    private final DirectBufferEx emptyDecodeRO = new UnsafeBufferEx(0L, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();
    private final McpEndExFW.Builder mcpEndExRW = new McpEndExFW.Builder();
    private final McpResetExFW.Builder mcpResetExRW = new McpResetExFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Supplier<String> supplySessionId;
    private final Signaler signaler;
    private final int mcpTypeId;
    private final int kafkaTypeId;
    private final byte[] toolsListPayload;
    private final UnsafeBufferEx toolsListBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;

    protected final Long2ObjectHashMap<McpKafkaBindingConfig> bindings;

    public McpKafkaProxyFactory(
        McpKafkaConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplySessionId = config.sessionIdSupplier();
        this.signaler = context.signaler();
        this.bindings = new Long2ObjectHashMap<>();
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.toolsListPayload = buildToolsList();
        this.toolsListBuffer = new UnsafeBufferEx(toolsListPayload);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
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
        DirectBufferEx buffer,
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
            final McpBeginExFW mcpBeginEx = mcpBeginExRO.tryWrap(begin.extension().buffer(),
                begin.extension().offset(), begin.extension().limit());

            if (mcpBeginEx != null)
            {
                switch (mcpBeginEx.kind())
                {
                case KIND_LIFECYCLE:
                {
                    final int capabilities = mcpBeginEx.lifecycle().capabilities();
                    final McpLifecycleProxy lifecycle = new McpLifecycleProxy(
                        sender, originId, routedId, initialId, authorization, affinity, capabilities);
                    newStream = lifecycle::onMcpMessage;
                    break;
                }
                case KIND_TOOLS_LIST:
                {
                    final McpToolsListProxy toolsList = new McpToolsListProxy(
                        sender, originId, routedId, initialId, authorization, affinity);
                    newStream = toolsList::onMcpMessage;
                    break;
                }
                case KIND_TOOLS_CALL:
                {
                    final String tool = mcpBeginEx.toolsCall().name().asString();
                    final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                    if (route != null)
                    {
                        final McpProxy mcpProxy = new McpProxy(
                            sender,
                            originId,
                            routedId,
                            initialId,
                            binding,
                            affinity,
                            authorization,
                            tool,
                            mcpBeginEx.toolsCall().contentLength(),
                            mcpBeginEx.toolsCall().timeout());
                        newStream = mcpProxy::onMcpMessage;
                    }
                    break;
                }
                default:
                    break;
                }
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
        DirectBufferEx payload,
        int offset,
        int length)
    {
        doData(receiver, originId, routedId, streamId, traceId, authorization,
            budgetId, flags, reserved, payload, offset, length, emptyRO);
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
        DirectBufferEx payload,
        int offset,
        int length,
        Flyweight extension)
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
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload, offset, length)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        doEnd(receiver, originId, routedId, streamId, traceId, authorization, emptyRO);
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        doReset(receiver, originId, routedId, streamId, traceId, authorization, emptyRO);
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private MessageConsumer newKafkaStream(
        MessageConsumer sender,
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

        final MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
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

    private byte[] buildToolsList()
    {
        final JsonArrayBuilder tools = Json.createArrayBuilder();
        for (String tool : TOOL_NAMES)
        {
            final JsonObject inputSchema = buildToolInputSchema(tool);
            tools.add(inputSchema == null
                ? Json.createObjectBuilder().add("name", tool)
                : Json.createObjectBuilder().add("name", tool).add("inputSchema", inputSchema));
        }
        final JsonObject toolsList = Json.createObjectBuilder()
            .add("tools", tools)
            .build();

        return toolsList.toString().getBytes(UTF_8);
    }

    private JsonObject buildToolInputSchema(
        String tool)
    {
        JsonObject schema = null;

        switch (tool)
        {
        case TOOL_PRODUCE:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topic", Json.createObjectBuilder().add("type", "string"))
                    .add("value", Json.createObjectBuilder().add("type", "string"))
                    .add("key", Json.createObjectBuilder().add("type", "string"))
                    .add("partition", Json.createObjectBuilder().add("type", "integer")))
                .add("required", Json.createArrayBuilder().add("topic").add("value"))
                .build();
            break;
        case TOOL_CONSUME:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topic", Json.createObjectBuilder().add("type", "string"))
                    .add("partition", Json.createObjectBuilder().add("type", "integer"))
                    .add("offset", Json.createObjectBuilder().add("type", "integer"))
                    .add("limit", Json.createObjectBuilder().add("type", "integer")))
                .add("required", Json.createArrayBuilder().add("topic"))
                .build();
            break;
        default:
            break;
        }

        return schema;
    }

    private static McpKafkaToolArgs buildToolArgs(
        String tool,
        Map<String, String> captured)
    {
        return TOOL_PRODUCE.equals(tool) ? buildProduceArgs(captured) : buildConsumeArgs(captured);
    }

    private static McpKafkaToolArgs buildProduceArgs(
        Map<String, String> captured)
    {
        McpKafkaToolArgs args = null;
        final String topic = captured.get("arguments.topic");
        final String value = captured.get("arguments.value");

        if (topic != null && value != null)
        {
            args = new McpKafkaToolArgs();
            args.topic = topic;
            args.value = value;
            args.key = captured.get("arguments.key");
            args.partitionId = parseInt(captured.get("arguments.partition"), -1);
        }

        return args;
    }

    private static McpKafkaToolArgs buildConsumeArgs(
        Map<String, String> captured)
    {
        McpKafkaToolArgs args = null;
        final String topic = captured.get("arguments.topic");

        if (topic != null)
        {
            args = new McpKafkaToolArgs();
            args.topic = topic;
            args.partitionId = parseInt(captured.get("arguments.partition"), -1);
            args.partitionOffset = parseLong(captured.get("arguments.offset"), -2L);
            args.limit = Math.max(1, Math.min(100, parseInt(captured.get("arguments.limit"), 10)));
        }

        return args;
    }

    private static int parseInt(
        String value,
        int defaultValue)
    {
        int parsed = defaultValue;
        if (value != null)
        {
            try
            {
                parsed = Integer.parseInt(value);
            }
            catch (NumberFormatException ex)
            {
            }
        }
        return parsed;
    }

    private static long parseLong(
        String value,
        long defaultValue)
    {
        long parsed = defaultValue;
        if (value != null)
        {
            try
            {
                parsed = Long.parseLong(value);
            }
            catch (NumberFormatException ex)
            {
            }
        }
        return parsed;
    }

    private byte[] buildToolResult(
        String text,
        boolean isError)
    {
        final StringBuilder result = new StringBuilder()
            .append("{\"content\":[{\"type\": \"text\",\"text\": \"")
            .append(escapeJson(text))
            .append("\"}],\"isError\": ")
            .append(isError)
            .append('}');

        return result.toString().getBytes(UTF_8);
    }

    private static String octetsAsString(
        int length,
        OctetsFW value)
    {
        return length == -1 ? null : value.buffer().getStringWithoutLengthUtf8(value.offset(), value.sizeof());
    }

    private static String escapeJson(
        String value)
    {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private KafkaBeginExFW buildKafkaBeginEx(
        String tool,
        McpKafkaToolArgs args)
    {
        final String resource = args != null && args.topic != null ? args.topic : "";
        final KafkaBeginExFW.Builder builder = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
            .typeId(kafkaTypeId);

        switch (tool != null ? tool : "")
        {
        case TOOL_PRODUCE:
            builder.merged(m -> m
                .capabilities(c -> c.set(PRODUCE_ONLY))
                .topic(resource)
                .partitionsItem(p -> p
                    .partitionId(args != null ? args.partitionId : -1)
                    .partitionOffset(-2L)));
            break;
        case TOOL_CONSUME:
            builder.merged(m -> m
                .capabilities(c -> c.set(FETCH_ONLY))
                .topic(resource)
                .partitionsItem(p -> p
                    .partitionId(args != null ? args.partitionId : -1)
                    .partitionOffset(args != null ? args.partitionOffset : -2L)));
            break;
        case TOOL_LIST_TOPICS:
        case TOOL_DESCRIBE_TOPIC:
            builder.meta(m -> m.topic(resource));
            break;
        case TOOL_CREATE_TOPIC:
            builder.request(r -> r.createTopics(ct -> ct
                .topicsItem(t -> t
                    .name(resource)
                    .partitionCount(1)
                    .replicas((short) 1))
                .timeout(0)
                .validateOnly(0)));
            break;
        case TOOL_DELETE_TOPIC:
            builder.request(r -> r.deleteTopics(dt -> dt
                .namesItem(n -> n.set(resource, UTF_8))
                .timeout(0)));
            break;
        case TOOL_LIST_CONSUMER_GROUPS:
            builder.request(r -> r.listGroups(lg ->
            {
            }));
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP:
            builder.request(r -> r.describeGroups(dg -> dg
                .groupIdsItem(g -> g.set(resource, UTF_8))
                .includeAuthorizedOperations(0)));
            break;
        case TOOL_RESET_OFFSETS:
            builder.request(r -> r.alterConsumerGroupOffsets(aco -> aco
                .groupId(resource)));
            break;
        case TOOL_LIST_BROKERS:
        case TOOL_DESCRIBE_CLUSTER:
        case TOOL_CLUSTER_OVERVIEW:
            builder.request(r -> r.describeCluster(dc -> dc.includeAuthorizedOperations(0)));
            break;
        case TOOL_DESCRIBE_CONFIGS:
            builder.describe(d -> d.name(resource));
            break;
        case TOOL_ALTER_CONFIGS:
            builder.request(r -> r.alterConfigs(ac -> ac
                .resourcesItem(res -> res
                    .type(t -> t.set(KafkaResourceType.TOPIC))
                    .name(resource))
                .validateOnly(0)));
            break;
        default:
            builder.merged(m -> m
                .capabilities(c -> c.set(PRODUCE_ONLY))
                .topic(resource)
                .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L)));
            break;
        }

        return builder.build();
    }

    private static final class McpKafkaToolArgs
    {
        private String topic;
        private String key;
        private String value;
        private int partitionId = -1;
        private long partitionOffset = -2L;
        private int limit = 10;

        private void key(
            KafkaKeyFW.Builder builder)
        {
            if (key != null)
            {
                final byte[] bytes = key.getBytes(UTF_8);
                builder.length(bytes.length).value(new UnsafeBufferEx(bytes), 0, bytes.length);
            }
        }
    }

    private static final class PendingRecord
    {
        private final String key;
        private final List<String[]> headers;
        private final String value;

        private PendingRecord(
            String key,
            List<String[]> headers,
            String value)
        {
            this.key = key;
            this.headers = headers;
            this.value = value;
        }
    }

    private final class McpLifecycleProxy
    {
        private final MessageConsumer mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;
        private final int capabilities;

        private int state;

        private McpLifecycleProxy(
            MessageConsumer mcp,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity,
            int capabilities)
        {
            this.mcp = mcp;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
            this.capabilities = capabilities;
        }

        private void onMcpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onMcpBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onMcpEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onMcpAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onMcpReset(resetRO.wrap(buffer, index, index + length));
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

            doWindow(mcp, originId, routedId, initialId, traceId, authorization, 0L, writeBuffer.capacity(), 0);
            doLifecycleReply(traceId);
        }

        private void onMcpEnd(
            EndFW end)
        {
            state = McpKafkaState.closedInitial(state);
            doReplyEnd(end.traceId());
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            state = McpKafkaState.closedInitial(state);
            doReplyAbort(abort.traceId());
        }

        private void onMcpReset(
            ResetFW reset)
        {
            state = McpKafkaState.closedReply(state);
        }

        private void doLifecycleReply(
            long traceId)
        {
            final String sessionId = supplySessionId.get();
            final McpBeginExFW lifecycleEx = mcpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(l -> l
                    .sessionId(sessionId)
                    .capabilities(capabilities != 0 ? capabilities : CAPABILITIES_TOOLS))
                .build();

            doBegin(mcp, originId, routedId, replyId, traceId, authorization, affinity, lifecycleEx);
            state = McpKafkaState.openedReply(state);
        }

        private void doReplyEnd(
            long traceId)
        {
            if (!McpKafkaState.replyClosed(state))
            {
                doEnd(mcp, originId, routedId, replyId, traceId, authorization);
                state = McpKafkaState.closedReply(state);
            }
        }

        private void doReplyAbort(
            long traceId)
        {
            if (!McpKafkaState.replyClosed(state))
            {
                doAbort(mcp, originId, routedId, replyId, traceId, authorization);
                state = McpKafkaState.closedReply(state);
            }
        }
    }

    private final class McpToolsListProxy
    {
        private final MessageConsumer mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;

        private int state;

        private McpToolsListProxy(
            MessageConsumer mcp,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            this.mcp = mcp;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
        }

        private void onMcpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onMcpBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onMcpEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onMcpAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onMcpReset(resetRO.wrap(buffer, index, index + length));
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

            doWindow(mcp, originId, routedId, initialId, traceId, authorization, 0L, writeBuffer.capacity(), 0);
            doToolsListReply(traceId);
        }

        private void onMcpEnd(
            EndFW end)
        {
            state = McpKafkaState.closedInitial(state);
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            state = McpKafkaState.closedInitial(state);
        }

        private void onMcpReset(
            ResetFW reset)
        {
            state = McpKafkaState.closedReply(state);
        }

        private void doToolsListReply(
            long traceId)
        {
            doBegin(mcp, originId, routedId, replyId, traceId, authorization, affinity, emptyRO);
            doData(mcp, originId, routedId, replyId, traceId, authorization, 0L, FLAGS_COMPLETE,
                toolsListPayload.length, toolsListBuffer, 0, toolsListPayload.length);
            doEnd(mcp, originId, routedId, replyId, traceId, authorization);
            state = McpKafkaState.closedReply(state);
        }
    }

    private final class McpProxy
    {
        private final MessageConsumer mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final McpKafkaBindingConfig binding;
        private final long affinity;
        private final long authorization;
        private final String tool;
        private final boolean awaitingArgs;
        private final long timeout;

        private long resolvedId;
        private JsonPipeline argsPipeline;
        private Map<String, String> capturedArgs;
        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private KafkaProxy kafka;
        private int state;

        private McpProxy(
            MessageConsumer mcp,
            long originId,
            long routedId,
            long initialId,
            McpKafkaBindingConfig binding,
            long affinity,
            long authorization,
            String tool,
            int contentLength,
            long timeout)
        {
            this.mcp = mcp;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.binding = binding;
            this.affinity = affinity;
            this.authorization = authorization;
            this.tool = tool;
            this.timeout = timeout;
            this.awaitingArgs = TOOL_PRODUCE.equals(tool) || TOOL_CONSUME.equals(tool);
        }

        private void onMcpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
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

            if (awaitingArgs)
            {
                capturedArgs = new HashMap<>();
                argsPipeline = JsonEx.stream(JsonEx.createParser()).into(new McpKafkaArguments(capturedArgs));
                argsPipeline.reset();
                doMcpWindow(traceId, 0, decodePool.slotCapacity(), 0);
            }
            else
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;
                    final KafkaBeginExFW kafkaBeginEx = buildKafkaBeginEx(tool, null);

                    kafka = new KafkaProxy(this, originId, resolvedId, affinity, authorization, tool, null, timeout);
                    kafka.doKafkaBegin(traceId, kafkaBeginEx, null);
                }
                else
                {
                    doMcpReset(traceId);
                }

                doMcpWindow(traceId, 0, writeBuffer.capacity(), 0);
            }
        }

        private void onMcpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            if (awaitingArgs)
            {
                if (payload != null)
                {
                    appendArgs(traceId, payload.buffer(), payload.offset(), payload.sizeof());
                }
            }
            else if (kafka != null && payload != null)
            {
                kafka.doKafkaData(traceId, budgetId, flags, reserved, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpKafkaState.closedInitial(state);

            if (awaitingArgs && kafka == null)
            {
                pumpArgs(traceId);
            }

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

            cleanupDecodeSlot();

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

        private void appendArgs(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            if (decodeSlot == NO_SLOT || decodeSlotOffset + length > decodePool.slotCapacity())
            {
                cleanupDecodeSlot();
                doMcpReset(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;
                pumpArgs(traceId);
            }
        }

        private void pumpArgs(
            long traceId)
        {
            final DirectBufferEx buffer = decodeSlot != NO_SLOT ? decodePool.buffer(decodeSlot) : emptyDecodeRO;
            final boolean last = McpKafkaState.initialClosed(state);
            final Status status = argsPipeline.transform(buffer, 0, decodeSlotOffset, last);

            final int consumed = decodeSlotOffset - argsPipeline.remaining();
            if (consumed > 0 && decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(0, slot, consumed, decodeSlotOffset - consumed);
            }
            decodeSlotOffset -= consumed;

            switch (status)
            {
            case STARVED:
                doMcpWindow(traceId, 0, decodePool.slotCapacity() - decodeSlotOffset, 0);
                break;
            case COMPLETED:
                cleanupDecodeSlot();
                onArgsCaptured(traceId);
                break;
            case REJECTED:
                cleanupDecodeSlot();
                doMcpReset(traceId, invalidParamsResetEx());
                break;
            default:
                break;
            }
        }

        private void onArgsCaptured(
            long traceId)
        {
            final McpKafkaToolArgs args = buildToolArgs(tool, capturedArgs);

            if (args != null)
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, args.topic);
                if (route != null)
                {
                    resolvedId = route.id;
                    final KafkaBeginExFW kafkaBeginEx = buildKafkaBeginEx(tool, args);

                    kafka = new KafkaProxy(this, originId, resolvedId, affinity, authorization, tool, args, timeout);
                    kafka.doKafkaBegin(traceId, kafkaBeginEx, args);
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, invalidParamsResetEx());
            }
        }

        private McpResetExFW invalidParamsResetEx()
        {
            return mcpResetExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .error(e -> e.code(ERROR_CODE_INVALID_PARAMS).message(ERROR_MESSAGE_INVALID_PARAMS))
                .build();
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
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
            DirectBufferEx payload,
            int offset,
            int length)
        {
            doData(mcp, originId, routedId, replyId, traceId, authorization,
                budgetId, flags, reserved, payload, offset, length);
        }

        private void doMcpResult(
            long traceId,
            String text,
            boolean isError)
        {
            final byte[] bytes = buildToolResult(text, isError);
            final UnsafeBufferEx result = new UnsafeBufferEx(bytes);

            doMcpData(traceId, 0L, FLAGS_COMPLETE, bytes.length, result, 0, bytes.length);

            if (isError)
            {
                final McpEndExFW endEx = mcpEndExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(mcpTypeId)
                    .outcome(o -> o.set(McpOutcome.ERROR))
                    .build();
                doMcpEnd(traceId, endEx);
            }
            else
            {
                doMcpEnd(traceId);
            }
        }

        private void doMcpEnd(
            long traceId)
        {
            doMcpEnd(traceId, emptyRO);
        }

        private void doMcpEnd(
            long traceId,
            Flyweight extension)
        {
            if (!McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doEnd(mcp, originId, routedId, replyId, traceId, authorization, extension);
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
            doWindow(mcp, originId, routedId, initialId, traceId, authorization, budgetId, credit, padding);
        }

        private void doMcpReset(
            long traceId)
        {
            doMcpReset(traceId, emptyRO);
        }

        private void doMcpReset(
            long traceId,
            Flyweight extension)
        {
            if (!McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doReset(mcp, originId, routedId, initialId, traceId, authorization, extension);
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
        private final boolean produce;
        private final boolean consume;
        private final String topic;
        private final int consumeLimit;
        private final long consumeTimeoutMillis;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private McpKafkaToolArgs pendingProduceArgs;
        private boolean produceDone;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private JsonGeneratorEx consumeGenerator;
        private JsonSink consumeSink;
        private McpKafkaConsumeResult consumeResult;
        private final Deque<PendingRecord> consumeQueue = new ArrayDeque<>();
        private int consumeCount;
        private boolean consumeSuspended;
        private boolean consumeClosing;
        private boolean consumeDone;
        private boolean consumeIsError;
        private boolean consumeStarted;
        private long consumeTimeoutId = Signaler.NO_CANCEL_ID;

        private KafkaProxy(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            String tool,
            McpKafkaToolArgs args,
            long timeout)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.produce = TOOL_PRODUCE.equals(tool);
            this.consume = TOOL_CONSUME.equals(tool);
            this.topic = args != null ? args.topic : null;
            this.consumeLimit = args != null ? args.limit : 0;
            this.consumeTimeoutMillis = timeout > 0L ? timeout : DEFAULT_CONSUME_TIMEOUT_MILLIS;
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBufferEx buffer,
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
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onKafkaSignal(signal);
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
            final OctetsFW payload = data.payload();

            if (consume)
            {
                final OctetsFW extension = data.extension();
                final KafkaMergedFetchDataExFW fetchDataEx = extension.sizeof() != 0 &&
                    kafkaDataExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) != null
                    ? kafkaDataExRO.merged().fetch()
                    : null;
                onKafkaConsumeRecord(traceId, fetchDataEx, payload);
            }
            else if (payload != null)
            {
                peer.doMcpData(traceId, budgetId, flags, reserved, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void onKafkaConsumeRecord(
            long traceId,
            KafkaMergedFetchDataExFW fetchDataEx,
            OctetsFW payload)
        {
            if (!consumeClosing && !consumeDone && payload != null)
            {
                final String key;
                final List<String[]> headers = new ArrayList<>();

                if (fetchDataEx != null)
                {
                    final KafkaKeyFW keyEx = fetchDataEx.key();
                    key = octetsAsString(keyEx.length(), keyEx.value());
                    fetchDataEx.headers().forEach(h -> headers.add(new String[]
                    {
                        octetsAsString(h.nameLen(), h.name()),
                        octetsAsString(h.valueLen(), h.value())
                    }));
                }
                else
                {
                    key = null;
                }

                final String value = payload.buffer().getStringWithoutLengthUtf8(payload.offset(), payload.sizeof());

                consumeQueue.add(new PendingRecord(key, headers, value));
                consumeCount++;

                if (consumeCount >= consumeLimit)
                {
                    finishConsume(traceId, false);
                }
                else
                {
                    pumpConsume(traceId);
                }
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpKafkaState.closedReply(state);

            if (produce)
            {
                finishProduce(traceId, true);
            }

            peer.doMcpEnd(traceId);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpKafkaState.closedReply(state);

            if (consume)
            {
                cancelConsumeTimeout();
                cleanupEncodeSlot();
            }

            peer.doMcpAbort(traceId);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            initialAck = window.acknowledge();

            if (produce && pendingProduceArgs != null && initialMax == 0 && credit > 0)
            {
                final McpKafkaToolArgs args = pendingProduceArgs;
                pendingProduceArgs = null;
                doKafkaProduce(traceId, args);
            }

            initialMax = credit;

            if (produce && pendingProduceArgs == null && initialAck >= initialSeq)
            {
                doKafkaEnd(traceId);
            }

            peer.doMcpWindow(traceId, budgetId, credit, padding);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            if (produce)
            {
                finishProduce(traceId, false, kafkaResetError(reset));
                doKafkaAbort(traceId);
                doKafkaReset(traceId);
            }
            else if (consume)
            {
                finishConsume(traceId, consumeCount == 0);
            }
            else
            {
                state = McpKafkaState.closedInitial(state);
                peer.doMcpReset(traceId);
            }
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            if (consume && signal.signalId() == CONSUME_TIMEOUT_SIGNAL_ID)
            {
                final long traceId = signal.traceId();
                consumeTimeoutId = Signaler.NO_CANCEL_ID;
                finishConsume(traceId, false);
            }
        }

        private void finishProduce(
            long traceId,
            boolean success)
        {
            finishProduce(traceId, success, 0);
        }

        private void finishProduce(
            long traceId,
            boolean success,
            int error)
        {
            if (!produceDone)
            {
                produceDone = true;
                final String text = success
                    ? "Produced record to " + topic + " topic"
                    : error == KAFKA_ERROR_INVALID_RECORD
                        ? "Record for " + topic + " topic failed schema validation"
                        : "Failed to produce record to " + topic + " topic";
                peer.doMcpResult(traceId, text, !success);
            }
        }

        private int kafkaResetError(
            ResetFW reset)
        {
            final OctetsFW extension = reset.extension();
            final KafkaResetExFW kafkaResetEx = extension.sizeof() != 0
                ? kafkaResetExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            return kafkaResetEx != null ? kafkaResetEx.error() : 0;
        }

        private void finishConsume(
            long traceId,
            boolean isError)
        {
            if (!consumeClosing && !consumeDone)
            {
                consumeClosing = true;
                consumeIsError = isError;
                cancelConsumeTimeout();
                doKafkaEnd(traceId);
                pumpConsume(traceId);
            }
        }

        private void cancelConsumeTimeout()
        {
            if (consumeTimeoutId != Signaler.NO_CANCEL_ID)
            {
                signaler.cancel(consumeTimeoutId);
                consumeTimeoutId = Signaler.NO_CANCEL_ID;
            }
        }

        private void pumpConsume(
            long traceId)
        {
            boolean progress = true;
            while (progress)
            {
                progress = false;

                if (consumeSuspended)
                {
                    flushConsume(traceId);
                    final Status status = withEncodeSlot(consumeResult::resume);
                    progress = applyConsumeStatus(traceId, status);
                }
                else if (!consumeQueue.isEmpty())
                {
                    final PendingRecord next = consumeQueue.poll();
                    final Status status = withEncodeSlot(() -> consumeResult.record(next.key, next.headers, next.value));
                    progress = applyConsumeStatus(traceId, status);
                }
                else if (consumeClosing && !consumeDone)
                {
                    final String text = "Consumed %d messages from topic %s".formatted(consumeCount, topic);
                    final Status status = withEncodeSlot(() -> consumeResult.close(consumeCount, text, consumeIsError));
                    progress = applyConsumeStatus(traceId, status);
                }
            }
        }

        private boolean applyConsumeStatus(
            long traceId,
            Status status)
        {
            boolean progress;
            switch (status)
            {
            case SUSPENDED:
                consumeSuspended = true;
                progress = true;
                break;
            case COMPLETED:
                consumeSuspended = false;
                consumeDone = true;
                flushConsume(traceId);
                peer.doMcpEnd(traceId);
                cleanupEncodeSlot();
                progress = false;
                break;
            case REJECTED:
                consumeSuspended = false;
                cleanupConsume(traceId);
                progress = false;
                break;
            default:
                consumeSuspended = false;
                progress = true;
                break;
            }
            return progress;
        }

        private Status withEncodeSlot(
            Supplier<Status> step)
        {
            Status status;
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(kafkaReplyId);
            }

            if (encodeSlot == NO_SLOT)
            {
                status = Status.REJECTED;
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                consumeGenerator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                status = step.get();
                encodeSlotOffset += consumeGenerator.length();
            }
            return status;
        }

        private void flushConsume(
            long traceId)
        {
            if (encodeSlot != NO_SLOT && encodeSlotOffset > 0)
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fin = consumeDone;
                final int flags = !consumeStarted
                    ? (fin ? FLAGS_COMPLETE : FLAGS_INIT)
                    : (fin ? FLAGS_FIN : 0x00);
                consumeStarted = true;

                peer.doMcpData(traceId, 0L, flags, encodeSlotOffset, slot, 0, encodeSlotOffset);
                encodeSlotOffset = 0;
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }

        private void cleanupConsume(
            long traceId)
        {
            consumeDone = true;
            cancelConsumeTimeout();
            cleanupEncodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
        }

        private void doKafkaBegin(
            long traceId,
            KafkaBeginExFW extension,
            McpKafkaToolArgs args)
        {
            state = McpKafkaState.openingInitial(state);
            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, extension);

            if (produce)
            {
                pendingProduceArgs = args;
            }
            else if (consume)
            {
                consumeGenerator = JsonEx.createGenerator();
                consumeSink = JsonEx.createSink(consumeGenerator);
                consumeResult = new McpKafkaConsumeResult(consumeSink);
                final Status status = withEncodeSlot(() -> consumeResult.open(topic));
                applyConsumeStatus(traceId, status);
                pumpConsume(traceId);
                consumeTimeoutId = signaler.signalAt(System.currentTimeMillis() + consumeTimeoutMillis,
                    originId, resolvedId, kafkaInitialId, traceId, CONSUME_TIMEOUT_SIGNAL_ID, 0);
            }
        }

        private void doKafkaProduce(
            long traceId,
            McpKafkaToolArgs args)
        {
            final byte[] value = args.value.getBytes(UTF_8);
            final UnsafeBufferEx valueBuffer = new UnsafeBufferEx(value);

            final KafkaDataExFW kafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.produce(p -> p
                    .deferred(0)
                    .timestamp(System.currentTimeMillis())
                    .partition(pt -> pt.partitionId(-1).partitionOffset(-1))
                    .key(args::key)))
                .build();

            doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                0L, FLAGS_COMPLETE, value.length, valueBuffer, 0, value.length, kafkaDataEx);

            initialSeq += value.length;
        }

        private void doKafkaData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBufferEx payload,
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
