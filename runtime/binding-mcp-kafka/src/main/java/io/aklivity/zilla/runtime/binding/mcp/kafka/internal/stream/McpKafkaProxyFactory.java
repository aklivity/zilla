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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAclTypes;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsResponseV2FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsResponse.Result;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsResponseV2FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsResponse.Kind;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsResponse.Topic;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsResponseV7FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsResponseV2FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteTopicsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteTopicsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteTopicsResponseV6FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeAclsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeAclsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeAclsResponseV2FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeClusterRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeClusterResponse.Broker;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeClusterResponseV0FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsResponseV4FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeGroupsResponseV5FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaFindCoordinatorRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaFindCoordinatorResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaFindCoordinatorResponseV3FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListGroupsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListGroupsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListGroupsResponseV4FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListOffsetsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListOffsetsResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaListOffsetsResponseV6FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaMetadataRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaMetadataResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaMetadataResponseV9FW;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetFetchRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetFetchResponse;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaOffsetFetchResponseV6FW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaArguments;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaConsumeResult;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolAllTopicsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolAlterConfigsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolCreateAclsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolCreateTopicsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolDeleteAclsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolDeleteTopicsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolDescribeConfigsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolDescribeTopicSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform.McpKafkaToolListAclsSource;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.types.stream.KafkaFlushExFW;
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

public class McpKafkaProxyFactory implements BindingHandler
{
    private static final String MCP_TYPE_NAME = "mcp";
    private static final String KAFKA_TYPE_NAME = "kafka";

    private static final String TOOL_PRODUCE = "produce";
    private static final String TOOL_CONSUME = "consume";
    private static final String TOOL_CREATE_TOPICS = "create_topics";
    private static final String TOOL_DELETE_TOPICS = "delete_topics";
    private static final String TOOL_DESCRIBE_CONFIGS = "describe_configs";
    private static final String TOOL_ALTER_CONFIGS = "alter_configs";
    private static final String TOOL_LIST_ACLS = "list_acls";
    private static final String TOOL_CREATE_ACLS = "create_acls";
    private static final String TOOL_DELETE_ACLS = "delete_acls";
    private static final String TOOL_LIST_TOPICS = "list_topics";
    private static final String TOOL_DESCRIBE_TOPIC = "describe_topic";
    private static final String TOOL_CLUSTER_OVERVIEW = "cluster_overview";
    private static final String TOOL_LIST_BROKERS = "list_brokers";
    private static final String TOOL_DESCRIBE_CLUSTER = "describe_cluster";
    private static final String TOOL_LIST_CONSUMER_GROUPS = "list_consumer_groups";
    private static final String TOOL_DESCRIBE_CONSUMER_GROUP = "describe_consumer_group";
    private static final String TOOL_DESCRIBE_CONSUMER_GROUP_LAG = "describe_consumer_group_lag";
    private static final String TOOL_RESET_OFFSETS = "reset_offsets";

    private static final String[] TOOL_NAMES =
    {
        TOOL_PRODUCE,
        TOOL_CONSUME,
        TOOL_CREATE_TOPICS,
        TOOL_DELETE_TOPICS,
        TOOL_DESCRIBE_CONFIGS,
        TOOL_ALTER_CONFIGS,
        TOOL_LIST_ACLS,
        TOOL_CREATE_ACLS,
        TOOL_DELETE_ACLS,
        TOOL_LIST_TOPICS,
        TOOL_DESCRIBE_TOPIC,
        TOOL_CLUSTER_OVERVIEW,
        TOOL_LIST_BROKERS,
        TOOL_DESCRIBE_CLUSTER,
        TOOL_LIST_CONSUMER_GROUPS,
        TOOL_DESCRIBE_CONSUMER_GROUP,
        TOOL_DESCRIBE_CONSUMER_GROUP_LAG,
        TOOL_RESET_OFFSETS
    };

    /**
     * Tools that bypass the local Kafka cache and talk directly to the plain Kafka client
     * (routed via {@code composite.clientExitId}, see {@link McpKafkaClientFactory#attach}),
     * using {@code KafkaApiClient}/{@code KafkaApiDeleteTopicsClient}/{@code KafkaApiDescribeConfigsClient}/
     * {@code KafkaApiAlterConfigsClient}/{@code KafkaApiListAclsClient}/{@code KafkaApiCreateAclsClient}/
     * {@code KafkaApiDeleteAclsClient}/{@code KafkaApiMetadataClient}/{@code KafkaApiDescribeClusterClient}
     * instead of the merged-capability {@code KafkaProxy}. A {@code Set} so future tools are a one-line addition.
     */
    protected static final Set<String> API_TOOLS = Set.of(
        TOOL_CREATE_TOPICS,
        TOOL_DELETE_TOPICS,
        TOOL_DESCRIBE_CONFIGS,
        TOOL_ALTER_CONFIGS,
        TOOL_LIST_ACLS,
        TOOL_CREATE_ACLS,
        TOOL_DELETE_ACLS,
        TOOL_LIST_TOPICS,
        TOOL_DESCRIBE_TOPIC,
        TOOL_CLUSTER_OVERVIEW,
        TOOL_LIST_BROKERS,
        TOOL_DESCRIBE_CLUSTER,
        TOOL_LIST_CONSUMER_GROUPS,
        TOOL_DESCRIBE_CONSUMER_GROUP,
        TOOL_DESCRIBE_CONSUMER_GROUP_LAG,
        TOOL_RESET_OFFSETS);

    private static final short CREATE_TOPICS_API_KEY = 19;
    private static final short CREATE_TOPICS_API_VERSION = 7;
    private static final short DELETE_TOPICS_API_KEY = 20;
    private static final short DELETE_TOPICS_API_VERSION = 6;
    private static final short DESCRIBE_CONFIGS_API_KEY = 32;
    private static final short DESCRIBE_CONFIGS_API_VERSION = 4;
    private static final short ALTER_CONFIGS_API_KEY = 33;
    private static final short ALTER_CONFIGS_API_VERSION = 2;
    private static final short DESCRIBE_ACLS_API_KEY = 29;
    private static final short DESCRIBE_ACLS_API_VERSION = 2;
    private static final short CREATE_ACLS_API_KEY = 30;
    private static final short CREATE_ACLS_API_VERSION = 2;
    private static final short DELETE_ACLS_API_KEY = 31;
    private static final short DELETE_ACLS_API_VERSION = 2;
    private static final short METADATA_API_KEY = 3;
    private static final short METADATA_API_VERSION = 9;
    private static final short DESCRIBE_CLUSTER_API_KEY = 60;
    private static final short DESCRIBE_CLUSTER_API_VERSION = 0;
    private static final short LIST_GROUPS_API_KEY = 16;
    private static final short LIST_GROUPS_API_VERSION = 4;
    private static final short DESCRIBE_GROUPS_API_KEY = 15;
    private static final short DESCRIBE_GROUPS_API_VERSION = 5;
    private static final short FIND_COORDINATOR_API_KEY = 10;
    private static final short FIND_COORDINATOR_API_VERSION = 3;
    private static final short OFFSET_FETCH_API_KEY = 9;
    private static final short OFFSET_FETCH_API_VERSION = 6;
    private static final short LIST_OFFSETS_API_KEY = 2;
    private static final short LIST_OFFSETS_API_VERSION = 6;

    // Kafka group states for which a group is safe to reset offsets on directly - no active members
    // are consuming, whether the group never existed ("Dead", auto-created Empty on commit) or all
    // members have left ("Empty"). Any other state (Stable/PreparingRebalance/CompletingRebalance)
    // has active members and must be rejected.
    private static final Set<String> RESETTABLE_GROUP_STATES = Set.of("Empty", "Dead");

    private static final int CAPABILITIES_TOOLS = 1;
    private static final int FLAGS_INIT = 0x01;
    private static final int FLAGS_FIN = 0x02;
    private static final int FLAGS_COMPLETE = 0x03;

    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final String ERROR_MESSAGE_INVALID_PARAMS = "Invalid params";

    private static final int CONSUME_TIMEOUT_SIGNAL_ID = 1;
    private static final long DEFAULT_CONSUME_TIMEOUT_MILLIS = 30_000L;
    private static final long CONSUME_CAUGHT_UP_GRACE_MILLIS = 250L;

    private static final int KAFKA_ERROR_INVALID_RECORD = 87;

    private static final String RESOURCE_TYPE_NAME_TOPIC = "topic";
    private static final String RESOURCE_TYPE_NAME_BROKER = "broker";
    private static final byte CONFIG_SOURCE_DEFAULT = 5;

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(0L, 0), 0, 0);
    private final DirectBufferEx emptyDecodeRO = new UnsafeBufferEx(0L, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
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
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Supplier<String> supplySessionId;
    private final Signaler signaler;
    private final int mcpTypeId;
    private final int kafkaTypeId;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final KafkaCreateTopicsRequest.Generator createTopicsRequestGenerator;
    private final KafkaDeleteTopicsRequest.Generator deleteTopicsRequestGenerator;
    private final KafkaDescribeConfigsRequest.Generator describeConfigsRequestGenerator;
    private final KafkaAlterConfigsRequest.Generator alterConfigsRequestGenerator;
    private final KafkaDescribeAclsRequest.Generator describeAclsRequestGenerator;
    private final KafkaCreateAclsRequest.Generator createAclsRequestGenerator;
    private final KafkaDeleteAclsRequest.Generator deleteAclsRequestGenerator;
    private final KafkaMetadataRequest.Generator metadataRequestGenerator;
    private final KafkaDescribeClusterRequest.Generator describeClusterRequestGenerator;
    private final KafkaListGroupsRequest.Generator listGroupsRequestGenerator;
    private final KafkaDescribeGroupsRequest.Generator describeGroupsRequestGenerator;
    private final KafkaFindCoordinatorRequest.Generator findCoordinatorRequestGenerator;
    private final KafkaOffsetFetchRequest.Generator offsetFetchRequestGenerator;
    private final KafkaListOffsetsRequest.Generator listOffsetsRequestGenerator;
    private final JsonGeneratorEx apiResultGenerator;
    private final KafkaCreateTopicsResponseV7FW createTopicsResponseRO;
    private final KafkaDeleteTopicsResponseV6FW deleteTopicsResponseRO;
    private final KafkaDescribeConfigsResponseV4FW describeConfigsResponseRO;
    private final KafkaAlterConfigsResponseV2FW alterConfigsResponseRO;
    private final KafkaDescribeAclsResponseV2FW describeAclsResponseRO;
    private final KafkaCreateAclsResponseV2FW createAclsResponseRO;
    private final KafkaDeleteAclsResponseV2FW deleteAclsResponseRO;
    private final KafkaMetadataResponseV9FW metadataResponseRO;
    private final KafkaDescribeClusterResponseV0FW describeClusterResponseRO;
    private final KafkaListGroupsResponseV4FW listGroupsResponseRO;
    private final KafkaDescribeGroupsResponseV5FW describeGroupsResponseRO;
    private final KafkaFindCoordinatorResponseV3FW findCoordinatorResponseRO;
    private final KafkaOffsetFetchResponseV6FW offsetFetchResponseRO;
    private final KafkaListOffsetsResponseV6FW listOffsetsResponseRO;
    private final int createTopicsRequestTimeoutMs;

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
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.createTopicsRequestGenerator = new KafkaCreateTopicsRequest.Generator();
        this.deleteTopicsRequestGenerator = new KafkaDeleteTopicsRequest.Generator();
        this.describeConfigsRequestGenerator = new KafkaDescribeConfigsRequest.Generator();
        this.alterConfigsRequestGenerator = new KafkaAlterConfigsRequest.Generator();
        this.describeAclsRequestGenerator = new KafkaDescribeAclsRequest.Generator();
        this.createAclsRequestGenerator = new KafkaCreateAclsRequest.Generator();
        this.deleteAclsRequestGenerator = new KafkaDeleteAclsRequest.Generator();
        this.metadataRequestGenerator = new KafkaMetadataRequest.Generator();
        this.describeClusterRequestGenerator = new KafkaDescribeClusterRequest.Generator();
        this.listGroupsRequestGenerator = new KafkaListGroupsRequest.Generator();
        this.describeGroupsRequestGenerator = new KafkaDescribeGroupsRequest.Generator();
        this.findCoordinatorRequestGenerator = new KafkaFindCoordinatorRequest.Generator();
        this.offsetFetchRequestGenerator = new KafkaOffsetFetchRequest.Generator();
        this.listOffsetsRequestGenerator = new KafkaListOffsetsRequest.Generator();
        this.apiResultGenerator = JsonEx.createGenerator();
        this.createTopicsResponseRO = new KafkaCreateTopicsResponseV7FW();
        this.deleteTopicsResponseRO = new KafkaDeleteTopicsResponseV6FW();
        this.describeConfigsResponseRO = new KafkaDescribeConfigsResponseV4FW();
        this.alterConfigsResponseRO = new KafkaAlterConfigsResponseV2FW();
        this.describeAclsResponseRO = new KafkaDescribeAclsResponseV2FW();
        this.createAclsResponseRO = new KafkaCreateAclsResponseV2FW();
        this.deleteAclsResponseRO = new KafkaDeleteAclsResponseV2FW();
        this.metadataResponseRO = new KafkaMetadataResponseV9FW();
        this.describeClusterResponseRO = new KafkaDescribeClusterResponseV0FW();
        this.listGroupsResponseRO = new KafkaListGroupsResponseV4FW();
        this.describeGroupsResponseRO = new KafkaDescribeGroupsResponseV5FW();
        this.findCoordinatorResponseRO = new KafkaFindCoordinatorResponseV3FW();
        this.offsetFetchResponseRO = new KafkaOffsetFetchResponseV6FW();
        this.listOffsetsResponseRO = new KafkaListOffsetsResponseV6FW();
        this.createTopicsRequestTimeoutMs = (int) config.requestTimeout().toMillis();
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

    /**
     * Whether {@code route} should bypass the local Kafka cache — matches at least one tool in
     * {@link #API_TOOLS} and none of the merged-capability tools ({@code produce}/{@code consume}),
     * so an unconditioned catch-all route (matching every tool) keeps going through the cache
     * exactly as it does today.
     */
    protected static boolean bypassesCache(
        McpKafkaRouteConfig route)
    {
        final boolean matchesApiTool = API_TOOLS.stream().anyMatch(tool -> route.matches(tool, null));
        final boolean matchesMergedTool = route.matches(TOOL_PRODUCE, null) || route.matches(TOOL_CONSUME, null);

        return matchesApiTool && !matchesMergedTool;
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
                        sender, originId, routedId, initialId, binding, authorization, affinity);
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

    /**
     * Windowed variant of {@link #doBegin}, threading real {@code sequence}/{@code acknowledge}/
     * {@code maximum} through instead of hardcoding zero - required by any reply the caller's
     * granted window might not fit in a single frame, e.g. {@link McpToolsListProxy}. Mirrors
     * {@code McpProxyListFactory}'s own {@code doBegin} in {@code binding-mcp}.
     */
    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
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

    /**
     * Windowed variant of {@link #doData}, threading real {@code sequence}/{@code acknowledge}/
     * {@code maximum} through instead of hardcoding zero - see {@link #doBegin} above.
     */
    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int flags,
        int reserved,
        DirectBufferEx payload,
        int offset,
        int length)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload, offset, length)
            .extension(emptyRO.buffer(), emptyRO.offset(), emptyRO.sizeof())
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

    /**
     * Windowed variant of {@link #doEnd}, threading real {@code sequence}/{@code acknowledge}/
     * {@code maximum} through instead of hardcoding zero - see {@link #doBegin} above.
     */
    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(emptyRO.buffer(), emptyRO.offset(), emptyRO.sizeof())
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

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(0L)
            .reserved(0)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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

    private byte[] toolsList(
        McpKafkaBindingConfig binding)
    {
        byte[] json = binding.toolsListJson();
        if (json == null)
        {
            json = buildToolsList(binding);
            binding.toolsListJson(json);
        }
        return json;
    }

    private byte[] buildToolsList(
        McpKafkaBindingConfig binding)
    {
        final JsonArrayBuilder tools = Json.createArrayBuilder();
        for (String tool : TOOL_NAMES)
        {
            final JsonObjectBuilder item = Json.createObjectBuilder().add("name", tool);
            item.add("title", buildToolTitle(tool));
            item.add("description", buildToolDescription(tool));
            final JsonObject inputSchema = buildToolInputSchema(tool);
            if (inputSchema != null)
            {
                item.add("inputSchema", inputSchema);
            }
            final JsonObject outputSchema = buildToolOutputSchema(tool);
            if (outputSchema != null)
            {
                item.add("outputSchema", outputSchema);
            }
            final JsonArrayBuilder toolSchemes = securitySchemes(binding.toolGuarded(tool));
            if (toolSchemes != null)
            {
                item.add("securitySchemes", toolSchemes);
            }
            final JsonObject annotations = buildToolAnnotations(tool);
            if (annotations != null)
            {
                item.add("annotations", annotations);
            }
            tools.add(item);
        }
        final JsonObject toolsList = Json.createObjectBuilder()
            .add("tools", tools)
            .build();

        return toolsList.toString().getBytes(UTF_8);
    }

    private static JsonArrayBuilder securitySchemes(
        List<GuardedConfig> guarded)
    {
        JsonArrayBuilder schemes = null;
        for (GuardedConfig g : guarded)
        {
            if (!g.roles.isEmpty())
            {
                if (schemes == null)
                {
                    schemes = Json.createArrayBuilder();
                }
                final JsonArrayBuilder scopes = Json.createArrayBuilder();
                for (String role : g.roles)
                {
                    scopes.add(role);
                }
                schemes.add(Json.createObjectBuilder()
                    .add("type", "oauth2")
                    .add("scopes", scopes));
            }
        }
        return schemes;
    }

    private static String buildToolTitle(
        String tool)
    {
        String title = null;

        switch (tool)
        {
        case TOOL_PRODUCE:
            title = "Produce Message";
            break;
        case TOOL_CONSUME:
            title = "Consume Messages";
            break;
        case TOOL_CREATE_TOPICS:
            title = "Create Topics";
            break;
        case TOOL_DELETE_TOPICS:
            title = "Delete Topics";
            break;
        case TOOL_DESCRIBE_CONFIGS:
            title = "Describe Configs";
            break;
        case TOOL_ALTER_CONFIGS:
            title = "Alter Configs";
            break;
        case TOOL_LIST_TOPICS:
            title = "List Topics";
            break;
        case TOOL_DESCRIBE_TOPIC:
            title = "Describe Topic";
            break;
        case TOOL_CLUSTER_OVERVIEW:
            title = "Cluster Overview";
            break;
        case TOOL_LIST_BROKERS:
            title = "List Brokers";
            break;
        case TOOL_DESCRIBE_CLUSTER:
            title = "Describe Cluster";
            break;
        case TOOL_LIST_CONSUMER_GROUPS:
            title = "List Consumer Groups";
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP:
            title = "Describe Consumer Group";
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP_LAG:
            title = "Describe Consumer Group Lag";
            break;
        case TOOL_RESET_OFFSETS:
            title = "Reset Consumer Group Offsets";
            break;
        case TOOL_LIST_ACLS:
            title = "List ACLs";
            break;
        case TOOL_CREATE_ACLS:
            title = "Create ACLs";
            break;
        case TOOL_DELETE_ACLS:
            title = "Delete ACLs";
            break;
        default:
            break;
        }

        return title;
    }

    private static String buildToolDescription(
        String tool)
    {
        String description = null;

        switch (tool)
        {
        case TOOL_PRODUCE:
            description = "Produce a message to a Kafka topic.";
            break;
        case TOOL_CONSUME:
            description = "Consume messages from a Kafka topic partition.";
            break;
        case TOOL_CREATE_TOPICS:
            description = "Create one or more Kafka topics.";
            break;
        case TOOL_DELETE_TOPICS:
            description = "Delete one or more Kafka topics.";
            break;
        case TOOL_DESCRIBE_CONFIGS:
            description = "Describe the configuration of a Kafka topic or broker resource.";
            break;
        case TOOL_ALTER_CONFIGS:
            description = "Alter the configuration of a Kafka topic or broker resource.";
            break;
        case TOOL_LIST_TOPICS:
            description = "List all Kafka topics in the cluster.";
            break;
        case TOOL_DESCRIBE_TOPIC:
            description = "Describe the partitions and replicas of a Kafka topic.";
            break;
        case TOOL_CLUSTER_OVERVIEW:
            description = "Summarize the health and size of the Kafka cluster.";
            break;
        case TOOL_LIST_BROKERS:
            description = "List the brokers in the Kafka cluster.";
            break;
        case TOOL_DESCRIBE_CLUSTER:
            description = "Describe the Kafka cluster id, controller, and authorized operations.";
            break;
        case TOOL_LIST_CONSUMER_GROUPS:
            description = "List the Kafka consumer groups in the cluster.";
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP:
            description = "Describe the members and state of a Kafka consumer group.";
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP_LAG:
            description = "Describe the per-partition lag of a Kafka consumer group.";
            break;
        case TOOL_RESET_OFFSETS:
            description = "Reset the committed offset for a Kafka consumer group topic partition.";
            break;
        case TOOL_LIST_ACLS:
            description = "List Kafka ACL bindings matching a filter.";
            break;
        case TOOL_CREATE_ACLS:
            description = "Create one or more Kafka ACL bindings.";
            break;
        case TOOL_DELETE_ACLS:
            description = "Delete Kafka ACL bindings matching a filter.";
            break;
        default:
            break;
        }

        return description;
    }

    // Shared enum value lists for the three ACL tools' JSON schemas - built fresh per call since a
    // JsonArrayBuilder is single-use, mirroring KafkaAclTypes' wire-value name lists.
    private static JsonArrayBuilder aclResourceTypeEnum()
    {
        return Json.createArrayBuilder()
            .add("topic").add("group").add("cluster").add("transactional_id").add("delegation_token")
            .add("user").add("any");
    }

    private static JsonArrayBuilder aclPatternTypeEnum()
    {
        return Json.createArrayBuilder().add("literal").add("prefixed").add("match").add("any");
    }

    private static JsonArrayBuilder aclOperationEnum()
    {
        return Json.createArrayBuilder()
            .add("any").add("all").add("read").add("write").add("create").add("delete").add("alter")
            .add("describe").add("cluster_action").add("describe_configs").add("alter_configs")
            .add("idempotent_write").add("create_tokens").add("describe_tokens").add("two_phase_commit");
    }

    // create_acls only ever creates an explicit grant or denial - never a wildcard "any" permission
    private static JsonArrayBuilder aclCreatePermissionTypeEnum()
    {
        return Json.createArrayBuilder().add("allow").add("deny");
    }

    // list_acls/delete_acls additionally accept "any" to match either permission type
    private static JsonArrayBuilder aclFilterPermissionTypeEnum()
    {
        return Json.createArrayBuilder().add("allow").add("deny").add("any");
    }

    // The seven fields identifying a single ACL binding, shared by list_acls'/create_acls'/delete_acls'
    // output shapes; create_acls and delete_acls each add their own error/error_message on top.
    private static JsonObjectBuilder aclBindingProperties()
    {
        return Json.createObjectBuilder()
            .add("resource_type", Json.createObjectBuilder().add("type", "string"))
            .add("resource_name", Json.createObjectBuilder().add("type", "string"))
            .add("pattern_type", Json.createObjectBuilder().add("type", "string"))
            .add("principal", Json.createObjectBuilder().add("type", "string"))
            .add("host", Json.createObjectBuilder().add("type", "string"))
            .add("operation", Json.createObjectBuilder().add("type", "string"))
            .add("permission_type", Json.createObjectBuilder().add("type", "string"));
    }

    private static JsonObject aclBindingSchema()
    {
        return Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", aclBindingProperties())
            .add("required", Json.createArrayBuilder()
                .add("resource_type").add("resource_name").add("pattern_type")
                .add("principal").add("host").add("operation").add("permission_type"))
            .build();
    }

    private JsonObject buildToolInputSchema(
        String tool)
    {
        JsonObject schema = null;

        switch (tool)
        {
        case TOOL_LIST_TOPICS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder())
                .add("additionalProperties", false)
                .build();
            break;
        case TOOL_CLUSTER_OVERVIEW:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder())
                .add("additionalProperties", false)
                .build();
            break;
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
        case TOOL_CREATE_TOPICS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topics", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("name", Json.createObjectBuilder().add("type", "string"))
                                .add("partitions", Json.createObjectBuilder().add("type", "integer"))
                                .add("replicas", Json.createObjectBuilder().add("type", "integer"))
                                .add("assignments", Json.createObjectBuilder().add("type", "array"))
                                .add("configs", Json.createObjectBuilder().add("type", "object")))
                            .add("required", Json.createArrayBuilder().add("name").add("partitions").add("replicas"))))
                    .add("timeout", Json.createObjectBuilder().add("type", "integer"))
                    .add("validate_only", Json.createObjectBuilder().add("type", "boolean")))
                .add("required", Json.createArrayBuilder().add("topics"))
                .build();
            break;
        case TOOL_DELETE_TOPICS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topics", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder().add("type", "string")))
                    .add("timeout", Json.createObjectBuilder().add("type", "integer")))
                .add("required", Json.createArrayBuilder().add("topics"))
                .build();
            break;
        case TOOL_DESCRIBE_CONFIGS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("resource_type", Json.createObjectBuilder()
                        .add("type", "string")
                        .add("enum", Json.createArrayBuilder().add("topic").add("broker")))
                    .add("resource_name", Json.createObjectBuilder().add("type", "string")))
                .add("required", Json.createArrayBuilder().add("resource_type").add("resource_name"))
                .build();
            break;
        case TOOL_ALTER_CONFIGS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("resource_type", Json.createObjectBuilder()
                        .add("type", "string")
                        .add("enum", Json.createArrayBuilder().add("topic").add("broker")))
                    .add("resource_name", Json.createObjectBuilder().add("type", "string"))
                    .add("configs", Json.createObjectBuilder()
                        .add("type", "object")
                        .add("additionalProperties", Json.createObjectBuilder().add("type", "string"))))
                .add("required", Json.createArrayBuilder().add("resource_type").add("resource_name").add("configs"))
                .build();
            break;
        case TOOL_LIST_ACLS:
        case TOOL_CREATE_ACLS:
        case TOOL_DELETE_ACLS:
            schema = buildAclToolInputSchema(tool);
            break;
        case TOOL_DESCRIBE_TOPIC:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topic", Json.createObjectBuilder().add("type", "string")))
                .add("required", Json.createArrayBuilder().add("topic"))
                .build();
            break;
        case TOOL_LIST_BROKERS:
        case TOOL_DESCRIBE_CLUSTER:
        case TOOL_LIST_CONSUMER_GROUPS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder())
                .build();
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP:
        case TOOL_DESCRIBE_CONSUMER_GROUP_LAG:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("group_id", Json.createObjectBuilder().add("type", "string")))
                .add("required", Json.createArrayBuilder().add("group_id"))
                .build();
            break;
        case TOOL_RESET_OFFSETS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("group_id", Json.createObjectBuilder().add("type", "string"))
                    .add("topic", Json.createObjectBuilder().add("type", "string"))
                    .add("partition", Json.createObjectBuilder().add("type", "integer"))
                    .add("offset", Json.createObjectBuilder().add("type", "integer")))
                .add("required", Json.createArrayBuilder().add("group_id").add("topic").add("partition").add("offset"))
                .build();
            break;
        default:
            break;
        }

        return schema;
    }

    private JsonObject buildAclToolInputSchema(
        String tool)
    {
        JsonObject schema = null;

        switch (tool)
        {
        case TOOL_LIST_ACLS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("resource_type", Json.createObjectBuilder().add("type", "string").add("enum", aclResourceTypeEnum()))
                    .add("resource_name", Json.createObjectBuilder().add("type", "string"))
                    .add("pattern_type", Json.createObjectBuilder().add("type", "string").add("enum", aclPatternTypeEnum()))
                    .add("principal", Json.createObjectBuilder().add("type", "string"))
                    .add("host", Json.createObjectBuilder().add("type", "string"))
                    .add("operation", Json.createObjectBuilder().add("type", "string").add("enum", aclOperationEnum()))
                    .add("permission_type", Json.createObjectBuilder()
                        .add("type", "string").add("enum", aclFilterPermissionTypeEnum())))
                .build();
            break;
        case TOOL_CREATE_ACLS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("acls", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("resource_type", Json.createObjectBuilder()
                                    .add("type", "string").add("enum", aclResourceTypeEnum()))
                                .add("resource_name", Json.createObjectBuilder().add("type", "string"))
                                .add("pattern_type", Json.createObjectBuilder()
                                    .add("type", "string")
                                    .add("enum", Json.createArrayBuilder().add("literal").add("prefixed")))
                                .add("principal", Json.createObjectBuilder().add("type", "string"))
                                .add("host", Json.createObjectBuilder().add("type", "string"))
                                .add("operation", Json.createObjectBuilder()
                                    .add("type", "string").add("enum", aclOperationEnum()))
                                .add("permission_type", Json.createObjectBuilder()
                                    .add("type", "string").add("enum", aclCreatePermissionTypeEnum())))
                            .add("required", Json.createArrayBuilder()
                                .add("resource_type").add("resource_name").add("principal")
                                .add("operation").add("permission_type")))))
                .add("required", Json.createArrayBuilder().add("acls"))
                .build();
            break;
        case TOOL_DELETE_ACLS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("acls", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("resource_type", Json.createObjectBuilder()
                                    .add("type", "string").add("enum", aclResourceTypeEnum()))
                                .add("resource_name", Json.createObjectBuilder().add("type", "string"))
                                .add("pattern_type", Json.createObjectBuilder()
                                    .add("type", "string").add("enum", aclPatternTypeEnum()))
                                .add("principal", Json.createObjectBuilder().add("type", "string"))
                                .add("host", Json.createObjectBuilder().add("type", "string"))
                                .add("operation", Json.createObjectBuilder()
                                    .add("type", "string").add("enum", aclOperationEnum()))
                                .add("permission_type", Json.createObjectBuilder()
                                    .add("type", "string").add("enum", aclFilterPermissionTypeEnum()))))))
                .add("required", Json.createArrayBuilder().add("acls"))
                .build();
            break;
        default:
            break;
        }

        return schema;
    }

    private JsonObject buildToolOutputSchema(
        String tool)
    {
        JsonObject schema = null;

        switch (tool)
        {
        case TOOL_CREATE_TOPICS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topics", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("name", Json.createObjectBuilder().add("type", "string"))
                                .add("error", Json.createObjectBuilder().add("type", "integer"))
                                .add("error_message", Json.createObjectBuilder().add("type", "string")))
                            .add("required", Json.createArrayBuilder().add("name").add("error")))))
                .add("required", Json.createArrayBuilder().add("topics"))
                .build();
            break;
        case TOOL_DELETE_TOPICS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topics", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("name", Json.createObjectBuilder().add("type", "string"))
                                .add("error", Json.createObjectBuilder().add("type", "integer"))
                                .add("error_message", Json.createObjectBuilder().add("type", "string")))
                            .add("required", Json.createArrayBuilder().add("name").add("error")))))
                .add("required", Json.createArrayBuilder().add("topics"))
                .build();
            break;
        case TOOL_DESCRIBE_CONFIGS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("configs", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("name", Json.createObjectBuilder().add("type", "string"))
                                .add("value", Json.createObjectBuilder().add("type", "string"))
                                .add("is_default", Json.createObjectBuilder().add("type", "boolean"))
                                .add("is_sensitive", Json.createObjectBuilder().add("type", "boolean")))
                            .add("required", Json.createArrayBuilder()
                                .add("name").add("is_default").add("is_sensitive")))))
                .add("required", Json.createArrayBuilder().add("configs"))
                .build();
            break;
        case TOOL_ALTER_CONFIGS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("resource_type", Json.createObjectBuilder()
                        .add("type", "string")
                        .add("enum", Json.createArrayBuilder().add("topic").add("broker")))
                    .add("resource_name", Json.createObjectBuilder().add("type", "string"))
                    .add("updated", Json.createObjectBuilder().add("type", "boolean")))
                .add("required", Json.createArrayBuilder().add("resource_type").add("resource_name").add("updated"))
                .build();
            break;
        case TOOL_LIST_ACLS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("acls", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", aclBindingSchema())))
                .add("required", Json.createArrayBuilder().add("acls"))
                .build();
            break;
        case TOOL_CREATE_ACLS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("acls", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", aclBindingProperties()
                                .add("error", Json.createObjectBuilder().add("type", "integer"))
                                .add("error_message", Json.createObjectBuilder().add("type", "string")))
                            .add("required", Json.createArrayBuilder()
                                .add("resource_type").add("resource_name").add("principal")
                                .add("operation").add("permission_type").add("error")))))
                .add("required", Json.createArrayBuilder().add("acls"))
                .build();
            break;
        case TOOL_DELETE_ACLS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("deleted", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", aclBindingProperties()
                                .add("error", Json.createObjectBuilder().add("type", "integer"))
                                .add("error_message", Json.createObjectBuilder().add("type", "string")))
                            .add("required", Json.createArrayBuilder()
                                .add("resource_type").add("resource_name").add("principal")
                                .add("operation").add("permission_type").add("error")))))
                .add("required", Json.createArrayBuilder().add("deleted"))
                .build();
            break;
        case TOOL_LIST_TOPICS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("topics", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("name", Json.createObjectBuilder().add("type", "string"))
                                .add("partition_count", Json.createObjectBuilder().add("type", "integer"))
                                .add("replication_factor", Json.createObjectBuilder().add("type", "integer")))
                            .add("required", Json.createArrayBuilder()
                                .add("name").add("partition_count").add("replication_factor")))))
                .add("required", Json.createArrayBuilder().add("topics"))
                .build();
            break;
        case TOOL_DESCRIBE_TOPIC:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("name", Json.createObjectBuilder().add("type", "string"))
                    .add("partitions", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("partition_id", Json.createObjectBuilder().add("type", "integer"))
                                .add("leader", Json.createObjectBuilder().add("type", "integer"))
                                .add("replicas", Json.createObjectBuilder()
                                    .add("type", "array")
                                    .add("items", Json.createObjectBuilder().add("type", "integer")))
                                .add("isr", Json.createObjectBuilder()
                                    .add("type", "array")
                                    .add("items", Json.createObjectBuilder().add("type", "integer"))))
                            .add("required", Json.createArrayBuilder()
                                .add("partition_id").add("leader").add("replicas").add("isr")))))
                .add("required", Json.createArrayBuilder().add("name").add("partitions"))
                .build();
            break;
        case TOOL_CLUSTER_OVERVIEW:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("broker_count", Json.createObjectBuilder().add("type", "integer"))
                    .add("controller_id", Json.createObjectBuilder().add("type", "integer"))
                    .add("under_replicated_partitions", Json.createObjectBuilder().add("type", "integer"))
                    .add("offline_partitions", Json.createObjectBuilder().add("type", "integer"))
                    .add("topic_count", Json.createObjectBuilder().add("type", "integer")))
                .add("required", Json.createArrayBuilder()
                    .add("broker_count").add("controller_id").add("under_replicated_partitions")
                    .add("offline_partitions").add("topic_count"))
                .build();
            break;
        case TOOL_LIST_BROKERS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("brokers", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("broker_id", Json.createObjectBuilder().add("type", "integer"))
                                .add("host", Json.createObjectBuilder().add("type", "string"))
                                .add("port", Json.createObjectBuilder().add("type", "integer"))
                                .add("rack", Json.createObjectBuilder().add("type", "string")))
                            .add("required", Json.createArrayBuilder().add("broker_id").add("host").add("port")))))
                .add("required", Json.createArrayBuilder().add("brokers"))
                .build();
            break;
        case TOOL_DESCRIBE_CLUSTER:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("cluster_id", Json.createObjectBuilder().add("type", "string"))
                    .add("controller_id", Json.createObjectBuilder().add("type", "integer"))
                    .add("authorized_operations", Json.createObjectBuilder().add("type", "integer")))
                .add("required", Json.createArrayBuilder().add("controller_id").add("authorized_operations"))
                .build();
            break;
        default:
            schema = buildConsumerGroupToolOutputSchema(tool);
            break;
        }

        return schema;
    }

    private JsonObject buildConsumerGroupToolOutputSchema(
        String tool)
    {
        JsonObject schema = null;

        switch (tool)
        {
        case TOOL_LIST_CONSUMER_GROUPS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("groups", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("group_id", Json.createObjectBuilder().add("type", "string"))
                                .add("state", Json.createObjectBuilder().add("type", "string")))
                            .add("required", Json.createArrayBuilder().add("group_id").add("state")))))
                .add("required", Json.createArrayBuilder().add("groups"))
                .build();
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("group_id", Json.createObjectBuilder().add("type", "string"))
                    .add("state", Json.createObjectBuilder().add("type", "string"))
                    .add("members", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("member_id", Json.createObjectBuilder().add("type", "string"))
                                .add("client_id", Json.createObjectBuilder().add("type", "string"))
                                .add("assignments", Json.createObjectBuilder()
                                    .add("type", "array")
                                    .add("items", Json.createObjectBuilder()
                                        .add("type", "object")
                                        .add("properties", Json.createObjectBuilder()
                                            .add("topic", Json.createObjectBuilder().add("type", "string"))
                                            .add("partition", Json.createObjectBuilder().add("type", "integer")))
                                        .add("required", Json.createArrayBuilder().add("topic").add("partition")))))
                            .add("required", Json.createArrayBuilder().add("member_id").add("client_id")))))
                .add("required", Json.createArrayBuilder().add("group_id").add("state").add("members"))
                .build();
            break;
        case TOOL_DESCRIBE_CONSUMER_GROUP_LAG:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("group_id", Json.createObjectBuilder().add("type", "string"))
                    .add("partitions", Json.createObjectBuilder()
                        .add("type", "array")
                        .add("items", Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                .add("topic", Json.createObjectBuilder().add("type", "string"))
                                .add("partition", Json.createObjectBuilder().add("type", "integer"))
                                .add("committed_offset", Json.createObjectBuilder().add("type", "integer"))
                                .add("end_offset", Json.createObjectBuilder().add("type", "integer"))
                                .add("lag", Json.createObjectBuilder().add("type", "integer")))
                            .add("required", Json.createArrayBuilder()
                                .add("topic").add("partition").add("committed_offset").add("end_offset").add("lag")))))
                .add("required", Json.createArrayBuilder().add("group_id").add("partitions"))
                .build();
            break;
        case TOOL_RESET_OFFSETS:
            schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                    .add("group_id", Json.createObjectBuilder().add("type", "string"))
                    .add("topic", Json.createObjectBuilder().add("type", "string"))
                    .add("partition", Json.createObjectBuilder().add("type", "integer"))
                    .add("offset", Json.createObjectBuilder().add("type", "integer"))
                    .add("reset", Json.createObjectBuilder().add("type", "boolean")))
                .add("required", Json.createArrayBuilder()
                    .add("group_id").add("topic").add("partition").add("offset").add("reset"))
                .build();
            break;
        default:
            break;
        }

        return schema;
    }

    // omits any hint that equals the MCP spec's own default (readOnlyHint: false, destructiveHint: true,
    // idempotentHint: false, openWorldHint: true) -- asserting a default-equal value costs bytes on every
    // tools/list response for zero information a compliant client wouldn't already assume; produce matches
    // every default, so it emits no annotations object at all, and create_topics only deviates on
    // destructiveHint. delete_topics, alter_configs and reset_offsets previously relied on this same
    // omission but shipped with a missing or incorrect destructiveHint as a result (see #2247) -- all three
    // now declare their hints explicitly.
    private JsonObject buildToolAnnotations(
        String tool)
    {
        JsonObject annotations = null;

        switch (tool)
        {
        case TOOL_CONSUME:
            annotations = Json.createObjectBuilder()
                .add("readOnlyHint", true)
                .add("destructiveHint", false)
                .add("idempotentHint", true)
                .build();
            break;
        case TOOL_CREATE_TOPICS:
            annotations = Json.createObjectBuilder()
                .add("destructiveHint", false)
                .build();
            break;
        case TOOL_DELETE_TOPICS:
            annotations = Json.createObjectBuilder()
                .add("destructiveHint", true)
                .add("idempotentHint", true)
                .build();
            break;
        case TOOL_ALTER_CONFIGS:
            annotations = Json.createObjectBuilder()
                .add("readOnlyHint", false)
                .add("destructiveHint", true)
                .build();
            break;
        case TOOL_RESET_OFFSETS:
            annotations = Json.createObjectBuilder()
                .add("readOnlyHint", false)
                .add("destructiveHint", true)
                .add("idempotentHint", true)
                .build();
            break;
        case TOOL_DESCRIBE_CONFIGS:
        case TOOL_LIST_ACLS:
        case TOOL_LIST_TOPICS:
        case TOOL_DESCRIBE_TOPIC:
        case TOOL_CLUSTER_OVERVIEW:
        case TOOL_LIST_BROKERS:
        case TOOL_DESCRIBE_CLUSTER:
        case TOOL_LIST_CONSUMER_GROUPS:
        case TOOL_DESCRIBE_CONSUMER_GROUP:
        case TOOL_DESCRIBE_CONSUMER_GROUP_LAG:
            annotations = Json.createObjectBuilder()
                .add("readOnlyHint", true)
                .add("destructiveHint", false)
                .add("idempotentHint", true)
                .build();
            break;
        // create_acls/delete_acls are intentionally absent here: both already match the MCP spec's own
        // default (destructiveHint: true) -- KIP-1318 classifies create_acls as destructive-mutate (a
        // wrongly-scoped ALLOW grant is itself a security incident), and delete_acls is unambiguously
        // destructive, so neither should override the default toward the softer create_topics treatment.
        default:
            break;
        }

        return annotations;
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

    private static String resourceTypeName(
        byte type)
    {
        String name = null;
        if (type == KafkaAlterConfigsRequest.RESOURCE_TYPE_TOPIC)
        {
            name = RESOURCE_TYPE_NAME_TOPIC;
        }
        else if (type == KafkaAlterConfigsRequest.RESOURCE_TYPE_BROKER)
        {
            name = RESOURCE_TYPE_NAME_BROKER;
        }
        return name;
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
        final StringBuilder result = new StringBuilder().append('{');
        result.append("\"content\":[{\"type\": \"text\",\"text\": \"")
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

    /**
     * Common surface {@code McpProxy} needs from its Kafka-side peer, shared by the
     * merged-capability {@code KafkaProxy} (produce/consume) and the direct-client
     * {@code KafkaApiClient} (create_topics and future admin-style tools).
     */
    private interface KafkaDownstream
    {
        void doKafkaEnd(
            long traceId);

        void doKafkaAbort(
            long traceId);

        void doKafkaWindow(
            long traceId,
            long budgetId,
            int credit,
            int padding);

        void doKafkaReset(
            long traceId);
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

    /**
     * Streams {@link #toolsListPayload} to the caller across as many {@code DATA} frames as the
     * caller's granted reply window requires, rather than assuming the whole payload always fits
     * one frame - mirrors {@code McpProxyListFactory}'s real {@code sequence}/{@code acknowledge}/
     * {@code maximum} tracking in {@code binding-mcp}, via the windowed {@link #doBegin}/{@link
     * #doData}/{@link #doEnd} overloads above.
     */
    private final class McpToolsListProxy
    {
        private final MessageConsumer mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;
        private final byte[] toolsListPayload;
        private final UnsafeBufferEx toolsListBuffer;

        private int state;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int toolsListProgress;

        private McpToolsListProxy(
            MessageConsumer mcp,
            long originId,
            long routedId,
            long initialId,
            McpKafkaBindingConfig binding,
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
            this.toolsListPayload = toolsList(binding);
            this.toolsListBuffer = new UnsafeBufferEx(toolsListPayload);
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
            case WindowFW.TYPE_ID:
                onMcpWindow(windowRO.wrap(buffer, index, index + length));
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
            doToolsListReplyBegin(traceId);
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

        private void onMcpWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();

            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyBud = window.budgetId();
            replyPad = window.padding();

            flushToolsList(traceId);
        }

        private void onMcpReset(
            ResetFW reset)
        {
            state = McpKafkaState.closedReply(state);
        }

        private void doToolsListReplyBegin(
            long traceId)
        {
            state = McpKafkaState.openingReply(state);
            doBegin(mcp, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, affinity,
                emptyRO);
        }

        /**
         * Sends as much of {@link #toolsListPayload} (starting at {@link #toolsListProgress}) as the
         * most recently granted reply window allows, advancing {@link #replySeq}/{@link
         * #toolsListProgress} by however much fit; waits for the next {@code WINDOW} if that isn't
         * the whole remainder. Every fragment uses {@code FLAGS_COMPLETE} - matching
         * {@code McpProxyListFactory#encode} in {@code binding-mcp} - since this transport
         * concatenates reply payload bytes across frames rather than reassembling by INIT/FIN flag.
         */
        private void flushToolsList(
            long traceId)
        {
            final int replyWin = replyMax - (int) (replySeq - replyAck) - replyPad;
            final int remaining = toolsListPayload.length - toolsListProgress;
            final int length = Math.min(Math.max(replyWin, 0), remaining);

            if (length > 0)
            {
                final boolean last = toolsListProgress + length == toolsListPayload.length;

                doData(mcp, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    replyBud, FLAGS_COMPLETE, length, toolsListBuffer, toolsListProgress, length);

                replySeq += length;
                toolsListProgress += length;

                if (last)
                {
                    doEnd(mcp, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                    state = McpKafkaState.closedReply(state);
                }
            }
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
        private McpKafkaToolCreateTopicsSource createTopicsSource;
        private McpKafkaToolDeleteTopicsSource deleteTopicsSource;
        private McpKafkaToolDescribeConfigsSource describeConfigsSource;
        private McpKafkaToolAlterConfigsSource alterConfigsSource;
        private McpKafkaToolListAclsSource listAclsSource;
        private McpKafkaToolCreateAclsSource createAclsSource;
        private McpKafkaToolDeleteAclsSource deleteAclsSource;
        private McpKafkaToolDescribeTopicSource describeTopicSource;
        private McpKafkaToolAllTopicsSource allTopicsSource;
        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private KafkaDownstream kafka;
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
            this.awaitingArgs = TOOL_PRODUCE.equals(tool) || TOOL_CONSUME.equals(tool) || API_TOOLS.contains(tool);
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
                if (TOOL_CREATE_TOPICS.equals(tool))
                {
                    createTopicsSource = new McpKafkaToolCreateTopicsSource(createTopicsRequestTimeoutMs);
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(createTopicsSource);
                }
                else if (TOOL_DELETE_TOPICS.equals(tool))
                {
                    deleteTopicsSource = new McpKafkaToolDeleteTopicsSource(createTopicsRequestTimeoutMs);
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(deleteTopicsSource);
                }
                else if (TOOL_DESCRIBE_CONFIGS.equals(tool))
                {
                    describeConfigsSource = new McpKafkaToolDescribeConfigsSource();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(describeConfigsSource);
                }
                else if (TOOL_ALTER_CONFIGS.equals(tool))
                {
                    alterConfigsSource = new McpKafkaToolAlterConfigsSource();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(alterConfigsSource);
                }
                else if (TOOL_LIST_ACLS.equals(tool))
                {
                    listAclsSource = new McpKafkaToolListAclsSource();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(listAclsSource);
                }
                else if (TOOL_CREATE_ACLS.equals(tool))
                {
                    createAclsSource = new McpKafkaToolCreateAclsSource();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(createAclsSource);
                }
                else if (TOOL_DELETE_ACLS.equals(tool))
                {
                    deleteAclsSource = new McpKafkaToolDeleteAclsSource();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(deleteAclsSource);
                }
                else if (TOOL_DESCRIBE_TOPIC.equals(tool))
                {
                    describeTopicSource = new McpKafkaToolDescribeTopicSource();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(describeTopicSource);
                }
                else if (TOOL_LIST_TOPICS.equals(tool) || TOOL_CLUSTER_OVERVIEW.equals(tool))
                {
                    allTopicsSource = new McpKafkaToolAllTopicsSource();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(allTopicsSource);
                }
                else
                {
                    capturedArgs = new HashMap<>();
                    argsPipeline = JsonEx.stream(JsonEx.createParser()).into(new McpKafkaArguments(capturedArgs));
                }
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

                    final KafkaProxy proxy = new KafkaProxy(
                        this, originId, resolvedId, affinity, authorization, tool, null, timeout);
                    proxy.doKafkaBegin(traceId, kafkaBeginEx, null);
                    kafka = proxy;
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
            else if (kafka instanceof KafkaProxy proxy && payload != null)
            {
                proxy.doKafkaData(traceId, budgetId, flags, reserved, payload.buffer(), payload.offset(), payload.sizeof());
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
                completeArgs(traceId);
                break;
            case REJECTED:
                cleanupDecodeSlot();
                doMcpReset(traceId, buildInvalidParamsResetEx());
                break;
            default:
                break;
            }
        }

        private void completeArgs(
            long traceId)
        {
            if (TOOL_CREATE_TOPICS.equals(tool))
            {
                completeCreateTopicsArgs(traceId);
            }
            else if (TOOL_DELETE_TOPICS.equals(tool))
            {
                completeDeleteTopicsArgs(traceId);
            }
            else if (TOOL_DESCRIBE_CONFIGS.equals(tool))
            {
                completeDescribeConfigsArgs(traceId);
            }
            else if (TOOL_ALTER_CONFIGS.equals(tool))
            {
                completeAlterConfigsArgs(traceId);
            }
            else if (TOOL_LIST_ACLS.equals(tool))
            {
                completeListAclsArgs(traceId);
            }
            else if (TOOL_CREATE_ACLS.equals(tool))
            {
                completeCreateAclsArgs(traceId);
            }
            else if (TOOL_DELETE_ACLS.equals(tool))
            {
                completeDeleteAclsArgs(traceId);
            }
            else if (TOOL_DESCRIBE_TOPIC.equals(tool) || TOOL_LIST_TOPICS.equals(tool) || TOOL_CLUSTER_OVERVIEW.equals(tool))
            {
                completeMetadataArgs(traceId);
            }
            else if (TOOL_LIST_BROKERS.equals(tool) || TOOL_DESCRIBE_CLUSTER.equals(tool))
            {
                completeDescribeClusterArgs(traceId);
            }
            else if (TOOL_LIST_CONSUMER_GROUPS.equals(tool))
            {
                completeListConsumerGroupsArgs(traceId);
            }
            else if (TOOL_DESCRIBE_CONSUMER_GROUP.equals(tool))
            {
                completeDescribeConsumerGroupArgs(traceId);
            }
            else if (TOOL_DESCRIBE_CONSUMER_GROUP_LAG.equals(tool))
            {
                completeDescribeConsumerGroupLagArgs(traceId);
            }
            else if (TOOL_RESET_OFFSETS.equals(tool))
            {
                completeResetOffsetsArgs(traceId);
            }
            else
            {
                completeToolArgs(traceId);
            }
        }

        private void completeToolArgs(
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

                    final KafkaProxy proxy = new KafkaProxy(
                        this, originId, resolvedId, affinity, authorization, tool, args, timeout);
                    proxy.doKafkaBegin(traceId, kafkaBeginEx, args);
                    kafka = proxy;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeCreateTopicsArgs(
            long traceId)
        {
            if (createTopicsSource.completed())
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiClient client = new KafkaApiClient(
                        this, originId, resolvedId, affinity, authorization, createTopicsSource);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeDeleteTopicsArgs(
            long traceId)
        {
            if (deleteTopicsSource.completed())
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiDeleteTopicsClient client = new KafkaApiDeleteTopicsClient(
                        this, originId, resolvedId, affinity, authorization, deleteTopicsSource);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeDescribeConfigsArgs(
            long traceId)
        {
            if (describeConfigsSource.completed())
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiDescribeConfigsClient client = new KafkaApiDescribeConfigsClient(
                        this, originId, resolvedId, affinity, authorization, describeConfigsSource);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeAlterConfigsArgs(
            long traceId)
        {
            if (alterConfigsSource.completed())
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiAlterConfigsClient client = new KafkaApiAlterConfigsClient(
                        this, originId, resolvedId, affinity, authorization, alterConfigsSource);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeListAclsArgs(
            long traceId)
        {
            if (listAclsSource.completed())
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiListAclsClient client = new KafkaApiListAclsClient(
                        this, originId, resolvedId, affinity, authorization, listAclsSource);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeCreateAclsArgs(
            long traceId)
        {
            if (createAclsSource.completed())
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiCreateAclsClient client = new KafkaApiCreateAclsClient(
                        this, originId, resolvedId, affinity, authorization, createAclsSource);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeDeleteAclsArgs(
            long traceId)
        {
            if (deleteAclsSource.completed())
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiDeleteAclsClient client = new KafkaApiDeleteAclsClient(
                        this, originId, resolvedId, affinity, authorization, deleteAclsSource);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        /**
         * Shared completion for {@code list_topics}/{@code describe_topic}/{@code cluster_overview} -
         * all three drive the same {@code KafkaApiMetadataClient} wire request/response, differing
         * only in which {@link KafkaMetadataRequest.Source} was parsed and in the result JSON
         * {@code KafkaApiMetadataClient} assembles for {@link #tool}.
         */
        private void completeMetadataArgs(
            long traceId)
        {
            final boolean describeTopic = TOOL_DESCRIBE_TOPIC.equals(tool);
            final boolean completed = describeTopic ? describeTopicSource.completed() : allTopicsSource.completed();

            if (completed)
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaMetadataRequest.Source source = describeTopic ? describeTopicSource : allTopicsSource;
                    final KafkaApiMetadataClient client = new KafkaApiMetadataClient(
                        this, originId, resolvedId, affinity, authorization, tool, source);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeDescribeClusterArgs(
            long traceId)
        {
            final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
            if (route != null)
            {
                resolvedId = route.id;

                final KafkaApiDescribeClusterClient client = new KafkaApiDescribeClusterClient(
                    this, originId, resolvedId, affinity, authorization, tool);
                client.doKafkaBegin(traceId);
                kafka = client;
            }
            else
            {
                doMcpReset(traceId);
            }
        }

        private void completeListConsumerGroupsArgs(
            long traceId)
        {
            final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
            if (route != null)
            {
                resolvedId = route.id;

                final KafkaApiListGroupsClient client = new KafkaApiListGroupsClient(
                    this, originId, resolvedId, affinity, authorization);
                client.doKafkaBegin(traceId);
                kafka = client;
            }
            else
            {
                doMcpReset(traceId);
            }
        }

        private void completeDescribeConsumerGroupArgs(
            long traceId)
        {
            final String groupId = capturedArgs.get("arguments.group_id");
            if (groupId != null)
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiDescribeGroupsClient client = new KafkaApiDescribeGroupsClient(
                        this, originId, resolvedId, affinity, authorization, groupId);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeDescribeConsumerGroupLagArgs(
            long traceId)
        {
            final String groupId = capturedArgs.get("arguments.group_id");
            if (groupId != null)
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiDescribeConsumerGroupLagClient client = new KafkaApiDescribeConsumerGroupLagClient(
                        this, originId, resolvedId, affinity, authorization, groupId);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private void completeResetOffsetsArgs(
            long traceId)
        {
            final String groupId = capturedArgs.get("arguments.group_id");
            final String topic = capturedArgs.get("arguments.topic");
            final int partition = parseInt(capturedArgs.get("arguments.partition"), -1);
            final long offset = parseLong(capturedArgs.get("arguments.offset"), -1L);

            if (groupId != null && topic != null && partition >= 0 && offset >= 0)
            {
                final McpKafkaRouteConfig route = binding.resolve(authorization, tool, null);
                if (route != null)
                {
                    resolvedId = route.id;

                    final KafkaApiResetOffsetsClient client = new KafkaApiResetOffsetsClient(
                        this, originId, resolvedId, affinity, authorization, groupId, topic, partition, offset);
                    client.doKafkaBegin(traceId);
                    kafka = client;
                }
                else
                {
                    doMcpReset(traceId);
                }
            }
            else
            {
                doMcpReset(traceId, buildInvalidParamsResetEx());
            }
        }

        private McpResetExFW buildInvalidParamsResetEx()
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

            doMcpResult(traceId, bytes.length, result, isError);
        }

        private void doMcpResult(
            long traceId,
            int length,
            MutableDirectBufferEx buffer,
            boolean isError)
        {
            doMcpData(traceId, 0L, FLAGS_COMPLETE, length, buffer, 0, length);

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

    private final class KafkaProxy implements KafkaDownstream
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
        private long consumeDeadlineMillis;
        private long consumeScheduledMillis;

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
                    rescheduleConsumeTimeout(traceId, fetchDataEx);
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

            doKafkaAbort(traceId);
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

        private void scheduleConsumeTimeout(
            long traceId,
            long deadlineMillis)
        {
            cancelConsumeTimeout();
            consumeTimeoutId = signaler.signalAt(deadlineMillis,
                originId, resolvedId, kafkaInitialId, traceId, CONSUME_TIMEOUT_SIGNAL_ID, 0);
            consumeScheduledMillis = deadlineMillis;
        }

        private void rescheduleConsumeTimeout(
            long traceId,
            KafkaMergedFetchDataExFW fetchDataEx)
        {
            final KafkaOffsetFW partition = fetchDataEx != null ? fetchDataEx.partition() : null;
            final boolean caughtUp = partition != null && partition.latestOffset() >= 0 &&
                partition.partitionOffset() + 1 >= partition.latestOffset();
            final long deadlineMillis = caughtUp
                ? Math.min(consumeDeadlineMillis, System.currentTimeMillis() + CONSUME_CAUGHT_UP_GRACE_MILLIS)
                : consumeDeadlineMillis;

            if (deadlineMillis != consumeScheduledMillis)
            {
                scheduleConsumeTimeout(traceId, deadlineMillis);
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
                    final Status status;
                    if (acquireEncodeSlot())
                    {
                        final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                        consumeGenerator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                        status = consumeResult.resume();
                        encodeSlotOffset += consumeGenerator.length();
                    }
                    else
                    {
                        status = Status.REJECTED;
                    }
                    progress = applyConsumeStatus(traceId, status);
                }
                else if (!consumeQueue.isEmpty())
                {
                    final PendingRecord next = consumeQueue.poll();
                    final Status status;
                    if (acquireEncodeSlot())
                    {
                        final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                        consumeGenerator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                        status = consumeResult.record(next.key, next.headers, next.value);
                        encodeSlotOffset += consumeGenerator.length();
                    }
                    else
                    {
                        status = Status.REJECTED;
                    }
                    progress = applyConsumeStatus(traceId, status);
                }
                else if (consumeClosing && !consumeDone)
                {
                    final String text = "Consumed %d messages from topic %s".formatted(consumeCount, topic);
                    final Status status;
                    if (acquireEncodeSlot())
                    {
                        final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                        consumeGenerator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                        status = consumeResult.close(consumeCount, text, consumeIsError);
                        encodeSlotOffset += consumeGenerator.length();
                    }
                    else
                    {
                        status = Status.REJECTED;
                    }
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

        private boolean acquireEncodeSlot()
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(kafkaReplyId);
            }
            return encodeSlot != NO_SLOT;
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

                final Status status;
                if (acquireEncodeSlot())
                {
                    final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                    consumeGenerator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                    status = consumeResult.open(topic);
                    encodeSlotOffset += consumeGenerator.length();
                }
                else
                {
                    status = Status.REJECTED;
                }
                applyConsumeStatus(traceId, status);
                pumpConsume(traceId);
                consumeDeadlineMillis = System.currentTimeMillis() + consumeTimeoutMillis;
                scheduleConsumeTimeout(traceId, consumeDeadlineMillis);
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

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final McpKafkaToolCreateTopicsSource createTopicsSource;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            McpKafkaToolCreateTopicsSource createTopicsSource)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.createTopicsSource = createTopicsSource;
            this.requestLength = KafkaCreateTopicsRequest.sizeof(createTopicsSource, CREATE_TOPICS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(CREATE_TOPICS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupCreateTopics(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaCreateTopicsResponseV7FW response = createTopicsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupCreateTopics(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final StringBuilder text = new StringBuilder();
                boolean isError = false;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .writeStartArray("topics");

                while (response.hasNext())
                {
                    if (response.next() == Kind.TOPIC)
                    {
                        final Topic topic = response.topic();
                        final String name = topic.buffer().getStringWithoutLengthUtf8(topic.nameOffset(), topic.nameLength());
                        final short error = topic.error();

                        if (text.length() != 0)
                        {
                            text.append(", ");
                        }
                        text.append(name);

                        apiResultGenerator.writeStartObject()
                            .write("name", name)
                            .write("error", error);

                        if (error != 0)
                        {
                            text.append(" (error ").append(error).append(')');
                            isError = true;
                        }
                        if (topic.messageLength() != -1)
                        {
                            final String message = topic.buffer()
                                .getStringWithoutLengthUtf8(topic.messageOffset(), topic.messageLength());
                            apiResultGenerator.write("error_message", message);
                        }
                        apiResultGenerator.writeEnd();
                    }
                }

                cleanupDecodeSlot();

                final String prefix = isError ? "Failed to create topic(s): " : "Created topic(s): ";

                apiResultGenerator
                    .writeEnd()
                    .writeEnd()
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", prefix + text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendCreateTopicsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendCreateTopicsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupCreateTopics(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                createTopicsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && createTopicsRequestGenerator.generate(createTopicsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupCreateTopics(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupCreateTopics(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(CREATE_TOPICS_API_KEY)
                    .version(CREATE_TOPICS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiDeleteTopicsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final McpKafkaToolDeleteTopicsSource deleteTopicsSource;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiDeleteTopicsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            McpKafkaToolDeleteTopicsSource deleteTopicsSource)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.deleteTopicsSource = deleteTopicsSource;
            this.requestLength = KafkaDeleteTopicsRequest.sizeof(deleteTopicsSource, DELETE_TOPICS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(DELETE_TOPICS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupDeleteTopics(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaDeleteTopicsResponseV6FW response = deleteTopicsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDeleteTopics(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final StringBuilder text = new StringBuilder();
                boolean isError = false;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .writeStartArray("topics");

                while (response.hasNext())
                {
                    final KafkaDeleteTopicsResponse.Topic topic = response.next();
                    final String name = topic.buffer().getStringWithoutLengthUtf8(topic.nameOffset(), topic.nameLength());
                    final short error = topic.error();

                    if (text.length() != 0)
                    {
                        text.append(", ");
                    }
                    text.append(name);

                    apiResultGenerator.writeStartObject()
                        .write("name", name)
                        .write("error", error);

                    if (error != 0)
                    {
                        text.append(" (error ").append(error).append(')');
                        isError = true;
                    }
                    if (topic.messageLength() != -1)
                    {
                        final String message = topic.buffer()
                            .getStringWithoutLengthUtf8(topic.messageOffset(), topic.messageLength());
                        apiResultGenerator.write("error_message", message);
                    }
                    apiResultGenerator.writeEnd();
                }

                cleanupDecodeSlot();

                final String prefix = isError ? "Failed to delete topic(s): " : "Deleted topic(s): ";

                apiResultGenerator
                    .writeEnd()
                    .writeEnd()
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", prefix + text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendDeleteTopicsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendDeleteTopicsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDeleteTopics(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                deleteTopicsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && deleteTopicsRequestGenerator.generate(deleteTopicsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupDeleteTopics(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupDeleteTopics(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(DELETE_TOPICS_API_KEY)
                    .version(DELETE_TOPICS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiDescribeConfigsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final McpKafkaToolDescribeConfigsSource describeConfigsSource;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiDescribeConfigsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            McpKafkaToolDescribeConfigsSource describeConfigsSource)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.describeConfigsSource = describeConfigsSource;
            this.requestLength = KafkaDescribeConfigsRequest.sizeof(describeConfigsSource, DESCRIBE_CONFIGS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(DESCRIBE_CONFIGS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupDescribeConfigs(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaDescribeConfigsResponseV4FW response = describeConfigsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDescribeConfigs(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                boolean isError = false;
                String errorMessage = null;
                int configCount = 0;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .writeStartArray("configs");

                while (response.hasNext())
                {
                    switch (response.next())
                    {
                    case RESOURCE:
                        final KafkaDescribeConfigsResponse.Resource resource = response.resource();
                        if (resource.error() != 0)
                        {
                            isError = true;
                            if (resource.messageLength() != -1)
                            {
                                errorMessage = resource.buffer()
                                    .getStringWithoutLengthUtf8(resource.messageOffset(), resource.messageLength());
                            }
                        }
                        break;
                    case CONFIG:
                        final KafkaDescribeConfigsResponse.Config config = response.config();
                        final String configName = config.buffer()
                            .getStringWithoutLengthUtf8(config.nameOffset(), config.nameLength());

                        apiResultGenerator.writeStartObject()
                            .write("name", configName);
                        if (config.valueLength() != -1)
                        {
                            final String configValue = config.buffer()
                                .getStringWithoutLengthUtf8(config.valueOffset(), config.valueLength());
                            apiResultGenerator.write("value", configValue);
                        }
                        apiResultGenerator
                            .write("is_default", config.configSource() == CONFIG_SOURCE_DEFAULT)
                            .write("is_sensitive", config.isSensitive())
                            .writeEnd();

                        configCount++;
                        break;
                    default:
                        break;
                    }
                }

                cleanupDecodeSlot();

                apiResultGenerator
                    .writeEnd()
                    .writeEnd();

                final String text = isError
                    ? "Failed to describe configs" + (errorMessage != null ? " (" + errorMessage + ")" : "")
                    : "Described " + configCount + " config(s)";

                apiResultGenerator
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendDescribeConfigsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendDescribeConfigsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDescribeConfigs(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                describeConfigsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && describeConfigsRequestGenerator.generate(describeConfigsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupDescribeConfigs(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupDescribeConfigs(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(DESCRIBE_CONFIGS_API_KEY)
                    .version(DESCRIBE_CONFIGS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiAlterConfigsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final McpKafkaToolAlterConfigsSource alterConfigsSource;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiAlterConfigsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            McpKafkaToolAlterConfigsSource alterConfigsSource)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.alterConfigsSource = alterConfigsSource;
            this.requestLength = KafkaAlterConfigsRequest.sizeof(alterConfigsSource, ALTER_CONFIGS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(ALTER_CONFIGS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupAlterConfigs(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaAlterConfigsResponseV2FW response = alterConfigsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupAlterConfigs(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final String resourceTypeName = resourceTypeName(alterConfigsSource.type());
                final String resourceName = alterConfigsSource.name();
                short error = 0;
                String errorMessage = null;

                while (response.hasNext())
                {
                    final KafkaAlterConfigsResponse.Resource resource = response.next();
                    error = resource.error();
                    if (resource.messageLength() != -1)
                    {
                        errorMessage = resource.buffer()
                            .getStringWithoutLengthUtf8(resource.messageOffset(), resource.messageLength());
                    }
                }

                cleanupDecodeSlot();

                final boolean isError = error != 0;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .write("resource_type", resourceTypeName)
                    .write("resource_name", resourceName)
                    .write("updated", !isError)
                    .writeEnd();

                final String text = isError
                    ? "Failed to alter configs for " + resourceTypeName + " " + resourceName +
                        (errorMessage != null ? " (" + errorMessage + ")" : " (error " + error + ")")
                    : "Updated configs for " + resourceTypeName + " " + resourceName;

                apiResultGenerator
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendAlterConfigsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendAlterConfigsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupAlterConfigs(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                alterConfigsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && alterConfigsRequestGenerator.generate(alterConfigsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupAlterConfigs(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupAlterConfigs(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(ALTER_CONFIGS_API_KEY)
                    .version(ALTER_CONFIGS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiListAclsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final McpKafkaToolListAclsSource listAclsSource;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiListAclsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            McpKafkaToolListAclsSource listAclsSource)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.listAclsSource = listAclsSource;
            this.requestLength = KafkaDescribeAclsRequest.sizeof(listAclsSource, DESCRIBE_ACLS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(DESCRIBE_ACLS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupListAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaDescribeAclsResponseV2FW response = describeAclsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupListAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final boolean isError = response.error() != 0;
                int aclCount = 0;
                String resourceType = null;
                String resourceName = null;
                String patternType = null;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .writeStartArray("acls");

                while (response.hasNext())
                {
                    switch (response.next())
                    {
                    case RESOURCE:
                        final KafkaDescribeAclsResponse.Resource resource = response.resource();
                        resourceType = KafkaAclTypes.resourceTypeName(resource.type()).toLowerCase(Locale.ROOT);
                        resourceName = resource.buffer()
                            .getStringWithoutLengthUtf8(resource.nameOffset(), resource.nameLength());
                        patternType = KafkaAclTypes.patternTypeName(resource.patternType()).toLowerCase(Locale.ROOT);
                        break;
                    case ACL:
                        final KafkaDescribeAclsResponse.Acl acl = response.acl();
                        final String principal = acl.buffer()
                            .getStringWithoutLengthUtf8(acl.principalOffset(), acl.principalLength());
                        final String host = acl.buffer().getStringWithoutLengthUtf8(acl.hostOffset(), acl.hostLength());
                        final String operation = KafkaAclTypes.operationName(acl.operation()).toLowerCase(Locale.ROOT);
                        final String permissionType = KafkaAclTypes.permissionTypeName(acl.permissionType())
                            .toLowerCase(Locale.ROOT);

                        apiResultGenerator.writeStartObject()
                            .write("resource_type", resourceType)
                            .write("resource_name", resourceName)
                            .write("pattern_type", patternType)
                            .write("principal", principal)
                            .write("host", host)
                            .write("operation", operation)
                            .write("permission_type", permissionType)
                            .writeEnd();

                        aclCount++;
                        break;
                    default:
                        break;
                    }
                }

                cleanupDecodeSlot();

                apiResultGenerator
                    .writeEnd()
                    .writeEnd();

                final String text;
                if (isError)
                {
                    final String errorMessage = response.messageLength() != -1
                        ? response.buffer().getStringWithoutLengthUtf8(response.messageOffset(), response.messageLength())
                        : null;
                    text = "Failed to list ACLs" + (errorMessage != null ? " (" + errorMessage + ")" : "");
                }
                else
                {
                    text = "Found " + aclCount + " ACL(s)";
                }

                apiResultGenerator
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendListAclsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendListAclsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupListAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                describeAclsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && describeAclsRequestGenerator.generate(listAclsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupListAcls(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupListAcls(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(DESCRIBE_ACLS_API_KEY)
                    .version(DESCRIBE_ACLS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiCreateAclsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final McpKafkaToolCreateAclsSource createAclsSource;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiCreateAclsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            McpKafkaToolCreateAclsSource createAclsSource)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.createAclsSource = createAclsSource;
            this.requestLength = KafkaCreateAclsRequest.sizeof(createAclsSource, CREATE_ACLS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(CREATE_ACLS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupCreateAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaCreateAclsResponseV2FW response = createAclsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupCreateAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final List<KafkaCreateAclsRequest.Source.Creation> creations = new ArrayList<>();
                createAclsSource.forEach(creations::add);

                boolean isError = false;
                int index = 0;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .writeStartArray("acls");

                while (response.hasNext())
                {
                    final Result result = response.next();
                    final KafkaCreateAclsRequest.Source.Creation creation = creations.get(index++);

                    apiResultGenerator.writeStartObject()
                        .write("resource_type", KafkaAclTypes.resourceTypeName(creation.resourceType()).toLowerCase(Locale.ROOT))
                        .write("resource_name", creation.resourceName())
                        .write("pattern_type",
                            KafkaAclTypes.patternTypeName(creation.resourcePatternType()).toLowerCase(Locale.ROOT))
                        .write("principal", creation.principal())
                        .write("host", creation.host())
                        .write("operation", KafkaAclTypes.operationName(creation.operation()).toLowerCase(Locale.ROOT))
                        .write("permission_type",
                            KafkaAclTypes.permissionTypeName(creation.permissionType()).toLowerCase(Locale.ROOT))
                        .write("error", result.error());

                    if (result.error() != 0)
                    {
                        isError = true;
                        if (result.messageLength() != -1)
                        {
                            final String message = result.buffer()
                                .getStringWithoutLengthUtf8(result.messageOffset(), result.messageLength());
                            apiResultGenerator.write("error_message", message);
                        }
                    }

                    apiResultGenerator.writeEnd();
                }

                cleanupDecodeSlot();

                apiResultGenerator
                    .writeEnd()
                    .writeEnd();

                final String text = isError ? "Failed to create ACL(s)" : "Created " + index + " ACL(s)";

                apiResultGenerator
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendCreateAclsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendCreateAclsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupCreateAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                createAclsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && createAclsRequestGenerator.generate(createAclsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupCreateAcls(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupCreateAcls(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(CREATE_ACLS_API_KEY)
                    .version(CREATE_ACLS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiDeleteAclsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final McpKafkaToolDeleteAclsSource deleteAclsSource;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiDeleteAclsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            McpKafkaToolDeleteAclsSource deleteAclsSource)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.deleteAclsSource = deleteAclsSource;
            this.requestLength = KafkaDeleteAclsRequest.sizeof(deleteAclsSource, DELETE_ACLS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(DELETE_ACLS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupDeleteAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaDeleteAclsResponseV2FW response = deleteAclsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDeleteAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                boolean isError = false;
                String filterErrorMessage = null;
                int deletedCount = 0;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .writeStartArray("deleted");

                while (response.hasNext())
                {
                    switch (response.next())
                    {
                    case FILTER_RESULT:
                        final KafkaDeleteAclsResponse.FilterResult filterResult = response.filterResult();
                        if (filterResult.error() != 0)
                        {
                            isError = true;
                            if (filterResult.messageLength() != -1)
                            {
                                filterErrorMessage = filterResult.buffer()
                                    .getStringWithoutLengthUtf8(filterResult.messageOffset(), filterResult.messageLength());
                            }
                        }
                        break;
                    case MATCHING_ACL:
                        final KafkaDeleteAclsResponse.MatchingAcl acl = response.matchingAcl();
                        final String resourceType = KafkaAclTypes.resourceTypeName(acl.resourceType()).toLowerCase(Locale.ROOT);
                        final String resourceName = acl.buffer()
                            .getStringWithoutLengthUtf8(acl.resourceNameOffset(), acl.resourceNameLength());
                        final String patternType = KafkaAclTypes.patternTypeName(acl.patternType()).toLowerCase(Locale.ROOT);
                        final String principal = acl.buffer()
                            .getStringWithoutLengthUtf8(acl.principalOffset(), acl.principalLength());
                        final String host = acl.buffer().getStringWithoutLengthUtf8(acl.hostOffset(), acl.hostLength());
                        final String operation = KafkaAclTypes.operationName(acl.operation()).toLowerCase(Locale.ROOT);
                        final String permissionType = KafkaAclTypes.permissionTypeName(acl.permissionType())
                            .toLowerCase(Locale.ROOT);

                        apiResultGenerator.writeStartObject()
                            .write("resource_type", resourceType)
                            .write("resource_name", resourceName)
                            .write("pattern_type", patternType)
                            .write("principal", principal)
                            .write("host", host)
                            .write("operation", operation)
                            .write("permission_type", permissionType)
                            .write("error", acl.error());

                        if (acl.error() != 0)
                        {
                            isError = true;
                            if (acl.messageLength() != -1)
                            {
                                final String message = acl.buffer()
                                    .getStringWithoutLengthUtf8(acl.messageOffset(), acl.messageLength());
                                apiResultGenerator.write("error_message", message);
                            }
                        }

                        apiResultGenerator.writeEnd();
                        deletedCount++;
                        break;
                    default:
                        break;
                    }
                }

                cleanupDecodeSlot();

                apiResultGenerator
                    .writeEnd()
                    .writeEnd();

                final String text = isError
                    ? "Failed to delete ACL(s)" + (filterErrorMessage != null ? " (" + filterErrorMessage + ")" : "")
                    : "Deleted " + deletedCount + " ACL(s)";

                apiResultGenerator
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendDeleteAclsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendDeleteAclsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDeleteAcls(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                deleteAclsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && deleteAclsRequestGenerator.generate(deleteAclsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupDeleteAcls(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupDeleteAcls(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(DELETE_ACLS_API_KEY)
                    .version(DELETE_ACLS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    /**
     * Shared Kafka-stream-facing downstream for {@code list_topics}, {@code describe_topic}, and
     * {@code cluster_overview} - all three drive the same Metadata request/response, differing only
     * in the {@link KafkaMetadataRequest.Source} that shaped the request and in the result JSON
     * {@link #completeResponse} assembles for {@link #tool}. One class instead of three avoids
     * tripling the connection/buffering/challenge-flush plumbing that {@link KafkaApiClient} and
     * {@link KafkaApiDeleteTopicsClient} otherwise duplicate.
     */
    private final class KafkaApiMetadataClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final String tool;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final KafkaMetadataRequest.Source source;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiMetadataClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            String tool,
            KafkaMetadataRequest.Source source)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.tool = tool;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.source = source;
            this.requestLength = KafkaMetadataRequest.sizeof(source, METADATA_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(METADATA_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupMetadata(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaMetadataResponse response = metadataResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupMetadata(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final boolean isError;
                if (TOOL_DESCRIBE_TOPIC.equals(tool))
                {
                    isError = writeDescribeTopicResult(response);
                }
                else if (TOOL_CLUSTER_OVERVIEW.equals(tool))
                {
                    writeClusterOverviewResult(response);
                    isError = false;
                }
                else
                {
                    writeListTopicsResult(response);
                    isError = false;
                }

                cleanupDecodeSlot();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
            }
        }

        private void writeListTopicsResult(
            KafkaMetadataResponse response)
        {
            while (response.hasNextBroker())
            {
                response.nextBroker();
            }

            apiResultGenerator.writeStartObject()
                .writeStartObject("structuredContent")
                .writeStartArray("topics");

            int topicCount = 0;
            boolean awaitingFactor = false;

            while (response.hasNext())
            {
                if (response.next() == KafkaMetadataResponse.Kind.TOPIC)
                {
                    final KafkaMetadataResponse.Topic topic = response.topic();
                    final String name =
                        response.buffer().getStringWithoutLengthUtf8(topic.nameOffset(), topic.nameLength());
                    topicCount++;

                    apiResultGenerator.writeStartObject()
                        .write("name", name)
                        .write("partition_count", topic.partitionCount());

                    awaitingFactor = topic.partitionCount() != 0;
                    if (!awaitingFactor)
                    {
                        apiResultGenerator.write("replication_factor", 0).writeEnd();
                    }
                }
                else if (awaitingFactor)
                {
                    final KafkaMetadataResponse.Partition partition = response.partition();
                    apiResultGenerator.write("replication_factor", partition.replicaCount()).writeEnd();
                    awaitingFactor = false;
                }
            }

            apiResultGenerator
                .writeEnd()
                .writeEnd()
                .writeStartArray("content")
                .writeStartObject()
                .write("type", "text")
                .write("text", "Found " + topicCount + " topic(s)")
                .writeEnd()
                .writeEnd()
                .write("isError", false)
                .writeEnd();
        }

        private boolean writeDescribeTopicResult(
            KafkaMetadataResponse response)
        {
            while (response.hasNextBroker())
            {
                response.nextBroker();
            }

            apiResultGenerator.writeStartObject()
                .writeStartObject("structuredContent");

            String name = null;
            boolean isError = false;
            boolean partitionsOpen = false;

            while (response.hasNext())
            {
                if (response.next() == KafkaMetadataResponse.Kind.TOPIC)
                {
                    final KafkaMetadataResponse.Topic topic = response.topic();
                    name = response.buffer().getStringWithoutLengthUtf8(topic.nameOffset(), topic.nameLength());
                    apiResultGenerator.write("name", name);

                    isError = topic.error() != 0;
                    if (!isError)
                    {
                        apiResultGenerator.writeStartArray("partitions");
                        partitionsOpen = true;
                    }
                }
                else if (partitionsOpen)
                {
                    final KafkaMetadataResponse.Partition partition = response.partition();
                    apiResultGenerator.writeStartObject()
                        .write("partition_id", partition.partitionId())
                        .write("leader", partition.leader())
                        .writeStartArray("replicas");

                    final PrimitiveIterator.OfInt replicas = partition.replicas();
                    while (replicas.hasNext())
                    {
                        apiResultGenerator.write(replicas.nextInt());
                    }

                    apiResultGenerator.writeEnd()
                        .writeStartArray("isr");

                    final PrimitiveIterator.OfInt isr = partition.isr();
                    while (isr.hasNext())
                    {
                        apiResultGenerator.write(isr.nextInt());
                    }

                    apiResultGenerator.writeEnd()
                        .writeEnd();
                }
            }

            if (partitionsOpen)
            {
                apiResultGenerator.writeEnd();
            }

            final String text = isError ? "Failed to describe topic " + name : "Described topic " + name;

            apiResultGenerator
                .writeEnd()
                .writeStartArray("content")
                .writeStartObject()
                .write("type", "text")
                .write("text", text)
                .writeEnd()
                .writeEnd()
                .write("isError", isError)
                .writeEnd();

            return isError;
        }

        private void writeClusterOverviewResult(
            KafkaMetadataResponse response)
        {
            while (response.hasNextBroker())
            {
                response.nextBroker();
            }

            int underReplicated = 0;
            int offline = 0;

            while (response.hasNext())
            {
                if (response.next() == KafkaMetadataResponse.Kind.PARTITION)
                {
                    final KafkaMetadataResponse.Partition partition = response.partition();
                    if (partition.isrCount() < partition.replicaCount())
                    {
                        underReplicated++;
                    }
                    if (partition.offlineReplicaCount() > 0)
                    {
                        offline++;
                    }
                }
            }

            final int brokerCount = response.brokerCount();
            final int controllerId = response.controllerId();
            final int topicCount = response.topicCount();

            apiResultGenerator.writeStartObject()
                .writeStartObject("structuredContent")
                .write("broker_count", brokerCount)
                .write("controller_id", controllerId)
                .write("under_replicated_partitions", underReplicated)
                .write("offline_partitions", offline)
                .write("topic_count", topicCount)
                .writeEnd()
                .writeStartArray("content")
                .writeStartObject()
                .write("type", "text")
                .write("text", "Cluster overview: " + topicCount + " topic(s), " + brokerCount + " broker(s)")
                .writeEnd()
                .writeEnd()
                .write("isError", false)
                .writeEnd();
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendMetadataRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendMetadataRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupMetadata(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                metadataRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && metadataRequestGenerator.generate(source);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupMetadata(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupMetadata(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(METADATA_API_KEY)
                    .version(METADATA_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    /**
     * Shared by both {@code list_brokers} and {@code describe_cluster} - they issue the identical
     * DescribeCluster wire request and differ only in how the response is shaped into MCP result JSON,
     * so a single client (parameterized by {@code tool}) avoids duplicating the full request/response
     * lifecycle {@link KafkaApiClient}/{@link KafkaApiDeleteTopicsClient} each carry for their own,
     * differently-shaped APIs.
     */
    private final class KafkaApiDescribeClusterClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final String tool;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiDescribeClusterClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            String tool)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.tool = tool;
            this.requestLength = KafkaDescribeClusterRequest.sizeof(DESCRIBE_CLUSTER_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(DESCRIBE_CLUSTER_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupDescribeCluster(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaDescribeClusterResponseV0FW response = describeClusterResponseRO.wrap(slot, 0, responseLength);

            // captured up front, while `slot` is still held - cleanupDecodeSlot() below releases it
            // back to the pool, so nothing may read from `slot` after that point
            final short error = response.error();
            final boolean isError = error != 0;
            final boolean listBrokers = TOOL_LIST_BROKERS.equals(tool);
            final String message = response.messageLength() != -1
                ? slot.getStringWithoutLengthUtf8(response.messageOffset(), response.messageLength())
                : null;
            final String clusterId = response.clusterIdLength() != -1
                ? slot.getStringWithoutLengthUtf8(response.clusterIdOffset(), response.clusterIdLength())
                : null;
            final int controllerId = response.controllerId();

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDescribeCluster(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent");

                if (listBrokers)
                {
                    apiResultGenerator.writeStartArray("brokers");
                }

                final StringBuilder text = new StringBuilder();

                while (response.hasNext())
                {
                    final Broker broker = response.next();

                    if (listBrokers)
                    {
                        final String host = broker.buffer()
                            .getStringWithoutLengthUtf8(broker.hostOffset(), broker.hostLength());

                        apiResultGenerator.writeStartObject()
                            .write("broker_id", broker.brokerId())
                            .write("host", host)
                            .write("port", broker.port());

                        if (broker.rackLength() != -1)
                        {
                            final String rack = broker.buffer()
                                .getStringWithoutLengthUtf8(broker.rackOffset(), broker.rackLength());
                            apiResultGenerator.write("rack", rack);
                        }

                        apiResultGenerator.writeEnd();

                        if (text.length() != 0)
                        {
                            text.append(", ");
                        }
                        text.append(broker.brokerId()).append('@').append(host).append(':').append(broker.port());
                    }
                }

                if (listBrokers)
                {
                    apiResultGenerator.writeEnd();
                }
                else
                {
                    apiResultGenerator.write("controller_id", controllerId)
                        .write("authorized_operations", response.authorizedOperations());

                    if (clusterId != null)
                    {
                        apiResultGenerator.write("cluster_id", clusterId);
                    }

                    text.append("cluster ")
                        .append(clusterId != null ? clusterId : "(unknown)")
                        .append(", controller ")
                        .append(controllerId);
                }

                cleanupDecodeSlot();

                final String prefix;
                if (isError)
                {
                    prefix = (listBrokers ? "Failed to list brokers (error " : "Failed to describe cluster (error ") +
                        error + (message != null ? "): " + message : ")");
                }
                else
                {
                    prefix = listBrokers ? "Brokers: " : "Described ";
                }

                apiResultGenerator
                    .writeEnd()
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", isError ? prefix : prefix + text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendDescribeClusterRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendDescribeClusterRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDescribeCluster(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                describeClusterRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && describeClusterRequestGenerator.generate(true);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupDescribeCluster(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupDescribeCluster(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(DESCRIBE_CLUSTER_API_KEY)
                    .version(DESCRIBE_CLUSTER_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiListGroupsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiListGroupsClient(
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
            this.requestLength = KafkaListGroupsRequest.sizeof(LIST_GROUPS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(LIST_GROUPS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupListGroups(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaListGroupsResponseV4FW response = listGroupsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupListGroups(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final boolean isError = response.error() != 0;
                final StringBuilder text = new StringBuilder();

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .writeStartArray("groups");

                while (response.hasNext())
                {
                    final KafkaListGroupsResponse.Group group = response.next();
                    final String groupId = group.buffer()
                        .getStringWithoutLengthUtf8(group.groupIdOffset(), group.groupIdLength());
                    final String groupState = group.buffer()
                        .getStringWithoutLengthUtf8(group.groupStateOffset(), group.groupStateLength());

                    if (text.length() != 0)
                    {
                        text.append(", ");
                    }
                    text.append(groupId).append(" (").append(groupState).append(')');

                    apiResultGenerator.writeStartObject()
                        .write("group_id", groupId)
                        .write("state", groupState)
                        .writeEnd();
                }

                cleanupDecodeSlot();

                final String summary = isError
                    ? "Failed to list consumer groups (error " + response.error() + ")"
                    : text.length() == 0 ? "No consumer groups found" : "Consumer groups: " + text;

                apiResultGenerator
                    .writeEnd()
                    .writeEnd()
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", summary)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendListGroupsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendListGroupsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupListGroups(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                listGroupsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && listGroupsRequestGenerator.generate();

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupListGroups(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupListGroups(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(LIST_GROUPS_API_KEY)
                    .version(LIST_GROUPS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    private final class KafkaApiDescribeGroupsClient implements KafkaDownstream
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long kafkaInitialId;
        private final long kafkaReplyId;
        private final String groupId;
        private final KafkaDescribeGroupsRequest.Source source;
        private final int requestLength;

        private MessageConsumer kafka;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiDescribeGroupsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            String groupId)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            this.groupId = groupId;
            this.source = new SingleGroupSource(groupId);
            this.requestLength = KafkaDescribeGroupsRequest.sizeof(source, DESCRIBE_GROUPS_API_VERSION);
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();

            doKafkaFlush(traceId);
        }

        private void doKafkaFlush(
            long traceId)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(DESCRIBE_GROUPS_API_VERSION))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            peer.doMcpBegin(traceId);
            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupDescribeGroups(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    completeResponse(traceId);
                }
            }
        }

        private void completeResponse(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaDescribeGroupsResponseV5FW response = describeGroupsResponseRO.wrap(slot, 0, responseLength);

            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDescribeGroups(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                boolean isError = false;
                String groupState = "";
                boolean membersOpen = false;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent");

                while (response.hasNext())
                {
                    if (response.next() == KafkaDescribeGroupsResponse.Kind.GROUP)
                    {
                        final KafkaDescribeGroupsResponse.Group group = response.group();
                        groupState = group.buffer()
                            .getStringWithoutLengthUtf8(group.groupStateOffset(), group.groupStateLength());
                        isError = group.error() != 0;

                        apiResultGenerator.write("group_id", groupId)
                            .write("state", groupState)
                            .writeStartArray("members");
                        membersOpen = true;
                    }
                    else
                    {
                        writeMember(response.member());
                    }
                }

                if (membersOpen)
                {
                    apiResultGenerator.writeEnd();
                }

                cleanupDecodeSlot();

                final String summary = isError
                    ? "Failed to describe consumer group: " + groupId
                    : "Consumer group " + groupId + " is " + groupState;

                apiResultGenerator
                    .writeEnd()
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", summary)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
            }
        }

        private void writeMember(
            KafkaDescribeGroupsResponse.Member member)
        {
            final String memberId = member.buffer()
                .getStringWithoutLengthUtf8(member.memberIdOffset(), member.memberIdLength());
            final String clientId = member.buffer()
                .getStringWithoutLengthUtf8(member.clientIdOffset(), member.clientIdLength());

            apiResultGenerator.writeStartObject()
                .write("member_id", memberId)
                .write("client_id", clientId)
                .writeStartArray("assignments");

            writeAssignments(member);

            apiResultGenerator.writeEnd()
                .writeEnd();
        }

        /**
         * Decodes the consumer-protocol {@code MemberAssignment} bytes (version int16, then
         * {@code [topic string16, [partition int32]]} entries) - the standard client-side assignor
         * wire format, opaque to the broker and to {@code protocol.idl}, so it is parsed by hand here
         * rather than modeled as a flyweight type.
         */
        private void writeAssignments(
            KafkaDescribeGroupsResponse.Member member)
        {
            final DirectBufferEx buffer = member.buffer();
            final int assignmentLength = member.memberAssignmentLength();

            if (assignmentLength >= 6)
            {
                final int assignmentLimit = member.memberAssignmentOffset() + assignmentLength;
                int progress = member.memberAssignmentOffset() + Short.BYTES;
                final int topicCount = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
                progress += Integer.BYTES;

                for (int t = 0; t < topicCount && progress + Short.BYTES <= assignmentLimit; t++)
                {
                    final short topicLength = buffer.getShort(progress, ByteOrder.BIG_ENDIAN);
                    progress += Short.BYTES;
                    final String topic = buffer.getStringWithoutLengthUtf8(progress, topicLength);
                    progress += topicLength;

                    final int partitionCount = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
                    progress += Integer.BYTES;

                    for (int p = 0; p < partitionCount && progress + Integer.BYTES <= assignmentLimit; p++)
                    {
                        final int partition = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
                        progress += Integer.BYTES;

                        apiResultGenerator.writeStartObject()
                            .write("topic", topic)
                            .write("partition", partition)
                            .writeEnd();
                    }
                }
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

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                sendDescribeGroupsRequest(traceId, budgetId);
            }

            initialMax = credit;
        }

        private void sendDescribeGroupsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupDescribeGroups(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = requestLength <= slot.capacity();
                describeGroupsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && describeGroupsRequestGenerator.generate(source);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, requestLength, slot, 0, requestLength);
                    initialSeq += requestLength;
                }
                else
                {
                    cleanupDescribeGroups(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void cleanupDescribeGroups(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(requestLength)
                    .api(DESCRIBE_GROUPS_API_KEY)
                    .version(DESCRIBE_GROUPS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    /**
     * Resets a consumer group's committed offset for one topic-partition. Three sequential Kafka
     * round trips against the same broker connection ({@code resolvedId}): FindCoordinator (the
     * group coordinator is a specific broker, not any broker), DescribeGroups (reject if the group
     * has active members), then a bare {@code offsetCommit} stream (kind 8) with
     * {@code generationId=-1}/{@code memberId=""} - the same "admin commit" real Kafka brokers
     * accept from {@code AdminClient.alterConsumerGroupOffsets()} against an inactive group.
     */
    private final class KafkaApiResetOffsetsClient implements KafkaDownstream
    {
        private static final int STAGE_FIND_COORDINATOR = 0;
        private static final int STAGE_DESCRIBE_GROUPS = 1;
        private static final int STAGE_OFFSET_COMMIT = 2;

        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final String groupId;
        private final String topic;
        private final int partition;
        private final long offset;
        private final KafkaDescribeGroupsRequest.Source describeGroupsSource;
        private final int findCoordinatorRequestLength;
        private final int describeGroupsRequestLength;

        private long kafkaInitialId;
        private long kafkaReplyId;
        private MessageConsumer kafka;
        private int state;
        private int stage;
        private boolean mcpBegun;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private String coordinatorHost;
        private int coordinatorPort;

        private KafkaApiResetOffsetsClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            String groupId,
            String topic,
            int partition,
            long offset)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.groupId = groupId;
            this.topic = topic;
            this.partition = partition;
            this.offset = offset;
            this.describeGroupsSource = new SingleGroupSource(groupId);
            this.findCoordinatorRequestLength = KafkaFindCoordinatorRequest.sizeof(groupId, FIND_COORDINATOR_API_VERSION);
            this.describeGroupsRequestLength =
                KafkaDescribeGroupsRequest.sizeof(describeGroupsSource, DESCRIBE_GROUPS_API_VERSION);
            this.stage = STAGE_FIND_COORDINATOR;
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final short version = stage == STAGE_FIND_COORDINATOR ? FIND_COORDINATOR_API_VERSION : DESCRIBE_GROUPS_API_VERSION;

            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(version))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            if (stage == STAGE_OFFSET_COMMIT)
            {
                state = McpKafkaState.openedReply(state);
                emitResult(traceId, true, null);
            }
            else
            {
                final OctetsFW extension = begin.extension();
                final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                    ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                    : null;

                responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                    ? kafkaBeginEx.apiResponse().length()
                    : -1;

                state = McpKafkaState.openedReply(state);
            }

            if (!mcpBegun)
            {
                mcpBegun = true;
                peer.doMcpBegin(traceId);
            }

            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            if (stage != STAGE_OFFSET_COMMIT)
            {
                final long traceId = data.traceId();
                final OctetsFW payload = data.payload();

                if (payload != null)
                {
                    appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
                }
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupResetOffsets(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    if (stage == STAGE_FIND_COORDINATOR)
                    {
                        completeFindCoordinator(traceId);
                    }
                    else
                    {
                        completeDescribeGroups(traceId);
                    }
                }
            }
        }

        private void completeFindCoordinator(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaFindCoordinatorResponse response = findCoordinatorResponseRO.wrap(slot, 0, responseLength);

            cleanupDecodeSlot();

            if (response.error() != 0)
            {
                emitResult(traceId, false, "Group coordinator not found for " + groupId + " (error " + response.error() + ")");
            }
            else
            {
                this.coordinatorHost = response.buffer().getStringWithoutLengthUtf8(response.hostOffset(), response.hostLength());
                this.coordinatorPort = response.port();
                advanceToDescribeGroups(traceId);
            }
        }

        private void completeDescribeGroups(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaDescribeGroupsResponseV5FW response = describeGroupsResponseRO.wrap(slot, 0, responseLength);

            short groupError = 0;
            String groupState = "";

            while (response.hasNext())
            {
                if (response.next() == KafkaDescribeGroupsResponse.Kind.GROUP)
                {
                    final KafkaDescribeGroupsResponse.Group describedGroup = response.group();
                    groupError = describedGroup.error();
                    groupState = describedGroup.buffer()
                        .getStringWithoutLengthUtf8(describedGroup.groupStateOffset(), describedGroup.groupStateLength());
                }
            }

            cleanupDecodeSlot();

            if (groupError != 0)
            {
                emitResult(traceId, false, "Failed to describe consumer group " + groupId + " (error " + groupError + ")");
            }
            else if (!RESETTABLE_GROUP_STATES.contains(groupState))
            {
                emitResult(traceId, false,
                    "Consumer group " + groupId + " has active members (state " + groupState + "); cannot reset offsets");
            }
            else
            {
                advanceToOffsetCommit(traceId);
            }
        }

        private void advanceToDescribeGroups(
            long traceId)
        {
            doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);

            stage = STAGE_DESCRIBE_GROUPS;
            requestSent = false;
            responseLength = -1;

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(describeGroupsRequestLength)
                    .api(DESCRIBE_GROUPS_API_KEY)
                    .version(DESCRIBE_GROUPS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        private void advanceToOffsetCommit(
            long traceId)
        {
            doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);

            stage = STAGE_OFFSET_COMMIT;
            requestSent = false;

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .offsetCommit(o -> o
                    .groupId(groupId)
                    .memberId("")
                    .instanceId("")
                    .host(coordinatorHost)
                    .port(coordinatorPort))
                .build();

            kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpKafkaState.closedReply(state);

            if (stage == STAGE_OFFSET_COMMIT && mcpBegun)
            {
                peer.doMcpEnd(traceId);
            }
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpKafkaState.closedReply(state);

            cleanupDecodeSlot();
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

            if (!requestSent && credit > 0)
            {
                requestSent = true;
                switch (stage)
                {
                case STAGE_FIND_COORDINATOR:
                    sendFindCoordinatorRequest(traceId, budgetId);
                    break;
                case STAGE_DESCRIBE_GROUPS:
                    sendDescribeGroupsRequest(traceId, budgetId);
                    break;
                default:
                    sendOffsetCommit(traceId, budgetId);
                    break;
                }
            }

            initialMax = credit;
        }

        private void sendFindCoordinatorRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupResetOffsets(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = findCoordinatorRequestLength <= slot.capacity();
                findCoordinatorRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && findCoordinatorRequestGenerator.generate(groupId);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, findCoordinatorRequestLength, slot, 0, findCoordinatorRequestLength);
                    initialSeq += findCoordinatorRequestLength;
                }
                else
                {
                    cleanupResetOffsets(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void sendDescribeGroupsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupResetOffsets(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = describeGroupsRequestLength <= slot.capacity();
                describeGroupsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && describeGroupsRequestGenerator.generate(describeGroupsSource);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, describeGroupsRequestLength, slot, 0, describeGroupsRequestLength);
                    initialSeq += describeGroupsRequestLength;
                }
                else
                {
                    cleanupResetOffsets(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void sendOffsetCommit(
            long traceId,
            long budgetId)
        {
            final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .offsetCommit(o -> o
                    .topic(topic)
                    .progress(p -> p.partitionId(partition).partitionOffset(offset).metadata(""))
                    .generationId(-1)
                    .leaderEpoch(-1))
                .build();

            doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                budgetId, FLAGS_COMPLETE, 0, emptyDecodeRO, 0, 0, kafkaDataEx);

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, emptyRO);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();

            if (stage == STAGE_OFFSET_COMMIT)
            {
                final OctetsFW extension = reset.extension();
                final KafkaResetExFW kafkaResetEx = extension.sizeof() != 0
                    ? kafkaResetExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                    : null;
                final int error = kafkaResetEx != null ? kafkaResetEx.error() : 0;

                emitResult(traceId, false, "Failed to reset offset for group " + groupId + " (error " + error + ")");
            }
            else
            {
                peer.doMcpReset(traceId);
            }
        }

        private void emitResult(
            long traceId,
            boolean success,
            String failureMessage)
        {
            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupResetOffsets(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final String text = success
                    ? "Reset offset for group " + groupId + " topic " + topic + " partition " + partition + " to " + offset
                    : failureMessage;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .write("group_id", groupId)
                    .write("topic", topic)
                    .write("partition", partition)
                    .write("offset", offset)
                    .write("reset", success)
                    .writeEnd()
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", !success)
                    .writeEnd();

                if (!mcpBegun)
                {
                    mcpBegun = true;
                    peer.doMcpBegin(traceId);
                }

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, !success);

                encodePool.release(encodeSlot);
            }
        }

        private void cleanupResetOffsets(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(findCoordinatorRequestLength)
                    .api(FIND_COORDINATOR_API_KEY)
                    .version(FIND_COORDINATOR_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    /**
     * Reports per-partition consumer lag ({@code lag = endOffset - committedOffset}) for a consumer
     * group. Two sequential Kafka round trips against the same broker connection ({@code resolvedId}),
     * both through the generic api envelope: OffsetFetch for every topic-partition the group has
     * committed an offset on, then ListOffsets at the latest timestamp for exactly those
     * topic-partitions. A partition missing from either response contributes no lag entry.
     */
    private final class KafkaApiDescribeConsumerGroupLagClient implements KafkaDownstream
    {
        private static final int STAGE_OFFSET_FETCH = 0;
        private static final int STAGE_LIST_OFFSETS = 1;

        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final String groupId;
        private final ConsumerGroupLagSource source;
        private final int offsetFetchRequestLength;

        private long kafkaInitialId;
        private long kafkaReplyId;
        private MessageConsumer kafka;
        private int state;
        private int stage;
        private boolean mcpBegun;
        private int listOffsetsRequestLength;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private boolean requestSent;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int responseLength = -1;

        private KafkaApiDescribeConsumerGroupLagClient(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization,
            String groupId)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.groupId = groupId;
            this.source = new ConsumerGroupLagSource();
            this.offsetFetchRequestLength = KafkaOffsetFetchRequest.sizeof(groupId, OFFSET_FETCH_API_VERSION);
            this.stage = STAGE_OFFSET_FETCH;
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onKafkaChallenge(challenge);
                break;
            default:
                break;
            }
        }

        private void onKafkaChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final short version = stage == STAGE_OFFSET_FETCH ? OFFSET_FETCH_API_VERSION : LIST_OFFSETS_API_VERSION;

            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiFlush(f -> f.version(version))
                .build();

            doFlush(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization, kafkaFlushEx);
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();
            final KafkaBeginExFW kafkaBeginEx = extension.sizeof() != 0
                ? kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            responseLength = kafkaBeginEx != null && kafkaBeginEx.kind() == KafkaBeginExFW.KIND_API_RESPONSE
                ? kafkaBeginEx.apiResponse().length()
                : -1;

            state = McpKafkaState.openedReply(state);

            if (!mcpBegun)
            {
                mcpBegun = true;
                peer.doMcpBegin(traceId);
            }

            doKafkaWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            if (payload != null)
            {
                appendResponse(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void appendResponse(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(kafkaReplyId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupConsumerGroupLag(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;

                if (responseLength >= 0 && decodeSlotOffset >= responseLength)
                {
                    if (stage == STAGE_OFFSET_FETCH)
                    {
                        completeOffsetFetch(traceId);
                    }
                    else
                    {
                        completeListOffsets(traceId);
                    }
                }
            }
        }

        /**
         * Captures every committed topic-partition into {@link #source}. The response's own
         * {@code Topic}/{@code Partition} views are only valid until the next {@code next()} call, so the
         * topic name is copied out eagerly and the partitions are accumulated as they are visited.
         */
        private void completeOffsetFetch(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaOffsetFetchResponseV6FW response = offsetFetchResponseRO.wrap(slot, 0, responseLength);

            String topicName = null;
            TopicLag topic = null;

            while (response.hasNext())
            {
                if (response.next() == KafkaOffsetFetchResponse.Kind.TOPIC)
                {
                    final KafkaOffsetFetchResponse.Topic fetched = response.topic();
                    topicName = fetched.buffer().getStringWithoutLengthUtf8(fetched.nameOffset(), fetched.nameLength());
                    topic = null;
                }
                else
                {
                    final KafkaOffsetFetchResponse.Partition partition = response.partition();
                    if (partition.errorCode() == 0 && topicName != null)
                    {
                        if (topic == null)
                        {
                            topic = source.topic(topicName);
                        }
                        topic.partition(partition.partitionIndex(), partition.committedOffset());
                    }
                }
            }

            final short error = response.error();

            cleanupDecodeSlot();

            if (error != 0)
            {
                emitResult(traceId,
                    "Failed to fetch committed offsets for consumer group " + groupId + " (error " + error + ")");
            }
            else if (source.topicCount() == 0)
            {
                emitResult(traceId, null);
            }
            else
            {
                advanceToListOffsets(traceId);
            }
        }

        private void completeListOffsets(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final KafkaListOffsetsResponseV6FW response = listOffsetsResponseRO.wrap(slot, 0, responseLength);

            TopicLag topic = null;

            while (response.hasNext())
            {
                if (response.next() == KafkaListOffsetsResponse.Kind.TOPIC)
                {
                    final KafkaListOffsetsResponse.Topic listed = response.topic();
                    final String name = listed.buffer()
                        .getStringWithoutLengthUtf8(listed.nameOffset(), listed.nameLength());
                    topic = source.find(name);
                }
                else if (topic != null)
                {
                    final KafkaListOffsetsResponse.Partition partition = response.partition();
                    if (partition.errorCode() == 0)
                    {
                        topic.endOffset(partition.partitionIndex(), partition.endOffset());
                    }
                }
            }

            cleanupDecodeSlot();

            emitResult(traceId, null);
        }

        private void advanceToListOffsets(
            long traceId)
        {
            doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);

            stage = STAGE_LIST_OFFSETS;
            requestSent = false;
            responseLength = -1;
            listOffsetsRequestLength = KafkaListOffsetsRequest.sizeof(source, LIST_OFFSETS_API_VERSION);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(listOffsetsRequestLength)
                    .api(LIST_OFFSETS_API_KEY)
                    .version(LIST_OFFSETS_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        /**
         * Only the reply of the stage currently in flight closes the MCP reply; the previous stage's
         * reply END arrives after {@link #advanceToListOffsets} has already opened the next stream.
         */
        private void onKafkaEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            if (end.streamId() == kafkaReplyId)
            {
                state = McpKafkaState.closedReply(state);

                if (mcpBegun)
                {
                    peer.doMcpEnd(traceId);
                }
            }
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpKafkaState.closedReply(state);

            cleanupDecodeSlot();
            peer.doMcpAbort(traceId);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();

            initialAck = window.acknowledge();

            if (!requestSent && credit > 0)
            {
                requestSent = true;

                if (stage == STAGE_OFFSET_FETCH)
                {
                    sendOffsetFetchRequest(traceId, budgetId);
                }
                else
                {
                    sendListOffsetsRequest(traceId, budgetId);
                }
            }

            initialMax = credit;
        }

        private void sendOffsetFetchRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupConsumerGroupLag(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = offsetFetchRequestLength <= slot.capacity();
                offsetFetchRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && offsetFetchRequestGenerator.generate(groupId);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, offsetFetchRequestLength, slot, 0, offsetFetchRequestLength);
                    initialSeq += offsetFetchRequestLength;
                }
                else
                {
                    cleanupConsumerGroupLag(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void sendListOffsetsRequest(
            long traceId,
            long budgetId)
        {
            final int encodeSlot = encodePool.acquire(kafkaInitialId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupConsumerGroupLag(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final boolean fits = listOffsetsRequestLength <= slot.capacity();
                listOffsetsRequestGenerator.wrap(slot, 0, slot.capacity());
                final boolean built = fits && listOffsetsRequestGenerator.generate(source);

                if (built)
                {
                    doData(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization,
                        budgetId, FLAGS_COMPLETE, listOffsetsRequestLength, slot, 0, listOffsetsRequestLength);
                    initialSeq += listOffsetsRequestLength;
                }
                else
                {
                    cleanupConsumerGroupLag(traceId);
                }

                encodePool.release(encodeSlot);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpKafkaState.closedInitial(state);

            cleanupDecodeSlot();
            peer.doMcpReset(traceId);
        }

        private void emitResult(
            long traceId,
            String failureMessage)
        {
            final int encodeSlot = encodePool.acquire(kafkaReplyId);
            if (encodeSlot == NO_SLOT)
            {
                cleanupConsumerGroupLag(traceId);
            }
            else
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                apiResultGenerator.reset();
                apiResultGenerator.wrap(encodeBuffer, 0, encodeBuffer.capacity());

                final boolean isError = failureMessage != null;

                apiResultGenerator.writeStartObject()
                    .writeStartObject("structuredContent")
                    .write("group_id", groupId)
                    .writeStartArray("partitions");

                final long totalLag = isError ? 0L : writePartitionLag();

                final String text = isError
                    ? failureMessage
                    : "Consumer group " + groupId + " has total lag " + totalLag;

                apiResultGenerator
                    .writeEnd()
                    .writeEnd()
                    .writeStartArray("content")
                    .writeStartObject()
                    .write("type", "text")
                    .write("text", text)
                    .writeEnd()
                    .writeEnd()
                    .write("isError", isError)
                    .writeEnd();

                if (!mcpBegun)
                {
                    mcpBegun = true;
                    peer.doMcpBegin(traceId);
                }

                peer.doMcpResult(traceId, apiResultGenerator.length(), encodeBuffer, isError);

                encodePool.release(encodeSlot);
            }
        }

        private long writePartitionLag()
        {
            long totalLag = 0L;

            for (TopicLag topic : source.topics)
            {
                for (PartitionLag partition : topic.partitions)
                {
                    if (partition.endOffset != PartitionLag.NO_END_OFFSET)
                    {
                        final long lag = partition.endOffset - partition.committedOffset;
                        totalLag += lag;

                        apiResultGenerator.writeStartObject()
                            .write("topic", topic.name)
                            .write("partition", partition.partition)
                            .write("committed_offset", partition.committedOffset)
                            .write("end_offset", partition.endOffset)
                            .write("lag", lag)
                            .writeEnd();
                    }
                }
            }

            return totalLag;
        }

        private void cleanupConsumerGroupLag(
            long traceId)
        {
            cleanupDecodeSlot();
            doKafkaAbort(traceId);
            doKafkaReset(traceId);
            peer.doMcpAbort(traceId);
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

        private void doKafkaBegin(
            long traceId)
        {
            state = McpKafkaState.openingInitial(state);

            final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .apiRequest(a -> a
                    .length(offsetFetchRequestLength)
                    .api(OFFSET_FETCH_API_KEY)
                    .version(OFFSET_FETCH_API_VERSION)
                    .clientId("zilla"))
                .build();

            kafkaInitialId = supplyInitialId.applyAsLong(resolvedId);
            kafkaReplyId = supplyReplyId.applyAsLong(kafkaInitialId);
            kafka = newKafkaStream(this::onKafkaMessage, originId, resolvedId, kafkaInitialId,
                traceId, authorization, affinity, kafkaBeginEx);
        }

        @Override
        public void doKafkaEnd(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doEnd(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaAbort(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.initialClosed(state))
            {
                state = McpKafkaState.closedInitial(state);
                doAbort(kafka, originId, resolvedId, kafkaInitialId, traceId, authorization);
            }
        }

        @Override
        public void doKafkaWindow(
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

        @Override
        public void doKafkaReset(
            long traceId)
        {
            if (kafka != null && !McpKafkaState.replyClosed(state))
            {
                state = McpKafkaState.closedReply(state);
                doReset(kafka, originId, resolvedId, kafkaReplyId, traceId, authorization);
            }
        }
    }

    /**
     * The topic-partitions an OffsetFetch response reported committed offsets for, in the shape the
     * follow-on ListOffsets request generator consumes, and the place the matching log end offsets
     * are recorded once that response arrives.
     */
    private static final class ConsumerGroupLagSource implements KafkaListOffsetsRequest.Source
    {
        private final List<TopicLag> topics;

        private ConsumerGroupLagSource()
        {
            this.topics = new ArrayList<>();
        }

        private TopicLag topic(
            String name)
        {
            final TopicLag topic = new TopicLag(name);
            topics.add(topic);
            return topic;
        }

        private TopicLag find(
            String name)
        {
            TopicLag found = null;

            for (TopicLag topic : topics)
            {
                if (topic.name.equals(name))
                {
                    found = topic;
                    break;
                }
            }

            return found;
        }

        @Override
        public int topicCount()
        {
            return topics.size();
        }

        @Override
        public void forEach(
            TopicConsumer consumer)
        {
            topics.forEach(consumer::accept);
        }
    }

    private static final class TopicLag implements KafkaListOffsetsRequest.Source.Topic
    {
        private final String name;
        private final List<PartitionLag> partitions;

        private TopicLag(
            String name)
        {
            this.name = name;
            this.partitions = new ArrayList<>();
        }

        private void partition(
            int partitionIndex,
            long committedOffset)
        {
            partitions.add(new PartitionLag(partitionIndex, committedOffset));
        }

        private void endOffset(
            int partitionIndex,
            long endOffset)
        {
            for (PartitionLag partition : partitions)
            {
                if (partition.partition == partitionIndex)
                {
                    partition.endOffset = endOffset;
                    break;
                }
            }
        }

        @Override
        public String name()
        {
            return name;
        }

        @Override
        public int partitionCount()
        {
            return partitions.size();
        }

        @Override
        public void forEachPartition(
            IntConsumer consumer)
        {
            partitions.forEach(partition -> consumer.accept(partition.partition));
        }
    }

    private static final class PartitionLag
    {
        private static final long NO_END_OFFSET = -1L;

        private final int partition;
        private final long committedOffset;

        private long endOffset;

        private PartitionLag(
            int partition,
            long committedOffset)
        {
            this.partition = partition;
            this.committedOffset = committedOffset;
            this.endOffset = NO_END_OFFSET;
        }
    }

    private static final class SingleGroupSource implements KafkaDescribeGroupsRequest.Source
    {
        private final String groupId;

        private SingleGroupSource(
            String groupId)
        {
            this.groupId = groupId;
        }

        @Override
        public int groupCount()
        {
            return 1;
        }

        @Override
        public void forEach(
            Consumer<String> consumer)
        {
            consumer.accept(groupId);
        }

        @Override
        public boolean includeAuthorizedOperations()
        {
            return false;
        }
    }
}
