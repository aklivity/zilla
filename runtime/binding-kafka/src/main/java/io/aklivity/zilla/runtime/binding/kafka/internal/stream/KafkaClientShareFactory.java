/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.ProxyAddressProtocol.STREAM;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static java.lang.System.currentTimeMillis;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.LongArrayList;
import org.agrona.collections.LongLongConsumer;

import io.aklivity.zilla.config.binding.kafka.KafkaSaslConfig;
import io.aklivity.zilla.config.binding.kafka.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.events.KafkaEventContext;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.api_versions.ApiVersionsResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.api_versions.ApiVersionsResponseKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator_v6.CoordinatorResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator_v6.FindCoordinatorRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator_v6.FindCoordinatorRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator_v6.FindCoordinatorResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator_v6.FindCoordinatorResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator_v6.KeyRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.MessageHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.RecordBatchFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.RecordHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.RecordTrailerFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.AcknowledgePartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.AcknowledgeTopicResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.AcknowledgeTopicResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.ShareAcknowledgeRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.ShareAcknowledgeRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.ShareAcknowledgeResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.ShareAcknowledgeResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_acknowledge_v1.ShareAcknowledgeResponsePart3FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.AcknowledgeTypeRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.AcknowledgementBatchRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.AcknowledgementBatchRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.AcquiredRecordResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ForgottenPartitionRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ForgottenTopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ForgottenTopicRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.PartitionRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.PartitionRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.PartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.PartitionResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.PartitionResponsePart3FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ShareFetchRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ShareFetchRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ShareFetchRequestPart3FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ShareFetchResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ShareFetchResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.ShareFetchResponsePart3FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.TopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.TopicRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.TopicResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_fetch_v1.TopicResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.AssignedPartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.AssignmentResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.AssignmentResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.ShareGroupHeartbeatRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.ShareGroupHeartbeatRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.ShareGroupHeartbeatResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.ShareGroupHeartbeatResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.SubscribedTopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.TopicPartitionsResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.share_group_heartbeat_v1.TopicPartitionsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.BrokerMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.MetadataResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.MetadataResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.PartitionMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.TopicMetadataPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata_v13.MetadataRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata_v13.MetadataRequestPart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata_v13.MetadataResponsePart3FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata_v13.MetadataTopicRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata_v13.TopicMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareAckType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareAcquiredFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaShareFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class KafkaClientShareFactory extends KafkaClientSaslHandshaker implements BindingHandler
{
    private static final int ERROR_NONE = 0;
    private static final int ERROR_UNKNOWN_SERVER_ERROR = -1;
    private static final int ERROR_NOT_LEADER_OR_FOLLOWER = 6;
    private static final int ERROR_MESSAGE_TOO_LARGE = 10;
    private static final int ERROR_INVALID_RECORD_STATE = 121;
    private static final int ERROR_COORDINATOR_LOAD_IN_PROGRESS = 14;
    private static final int ERROR_COORDINATOR_NOT_AVAILABLE = 15;
    private static final int ERROR_NOT_COORDINATOR = 16;
    private static final int ERROR_UNKNOWN_MEMBER_ID = 25;
    private static final int ERROR_UNSUPPORTED_VERSION = 35;
    private static final int ERROR_FENCED_MEMBER_EPOCH = 110;
    private static final int ERROR_SHARE_SESSION_NOT_FOUND = 122;
    private static final int ERROR_INVALID_SHARE_SESSION_EPOCH = 123;

    private static final int SIGNAL_NEXT_REQUEST = 1;
    private static final int SIGNAL_HEARTBEAT_REQUEST = 2;

    private static final short API_VERSIONS_API_KEY = 18;
    private static final short API_VERSIONS_API_VERSION = 0;
    private static final short METADATA_API_KEY = 3;
    private static final short METADATA_API_VERSION = 13;
    private static final short FIND_COORDINATOR_API_KEY = 10;
    private static final short FIND_COORDINATOR_API_VERSION = 6;
    private static final short SHARE_GROUP_HEARTBEAT_API_KEY = 76;
    private static final short SHARE_GROUP_HEARTBEAT_API_VERSION = 1;
    private static final short SHARE_FETCH_API_KEY = 78;
    private static final short SHARE_FETCH_API_VERSION = 1;
    private static final short SHARE_ACKNOWLEDGE_API_KEY = 79;
    private static final short SHARE_ACKNOWLEDGE_API_VERSION = 1;

    private static final short[] BOOTSTRAP_API_KEYS = {METADATA_API_KEY, FIND_COORDINATOR_API_KEY};
    private static final short[] BOOTSTRAP_API_VERSIONS = {METADATA_API_VERSION, FIND_COORDINATOR_API_VERSION};
    private static final short[] COORDINATOR_API_KEYS = {SHARE_GROUP_HEARTBEAT_API_KEY};
    private static final short[] COORDINATOR_API_VERSIONS = {SHARE_GROUP_HEARTBEAT_API_VERSION};
    private static final short[] LEADER_API_KEYS = {SHARE_FETCH_API_KEY, SHARE_ACKNOWLEDGE_API_KEY};
    private static final short[] LEADER_API_VERSIONS = {SHARE_FETCH_API_VERSION, SHARE_ACKNOWLEDGE_API_VERSION};

    private static final int RESPONSE_HEADER_SIZE_CORRELATION_ID = 4;

    private static final int MEMBER_EPOCH_JOIN = 0;
    private static final int MEMBER_EPOCH_LEAVE = -1;
    private static final int SHARE_SESSION_EPOCH_INITIAL = 0;
    private static final int SHARE_SESSION_EPOCH_FINAL = -1;

    private static final int ACKNOWLEDGE_TYPE_ACCEPT = 1;
    private static final int ACKNOWLEDGE_TYPE_RELEASE = 2;
    private static final int ACKNOWLEDGE_TYPE_REJECT = 3;

    private static final int DEBITOR_CLAIM_MINIMUM = 1024;
    private static final long NO_CORRELATION_ID = -1L;
    private static final int TOPIC_ID_SIZE = 16;
    private static final int ATTRIBUTES_COMPRESSION_MASK = 0x07;
    private static final int ATTRIBUTES_CONTROL_MASK = 0x20;

    private static final int FIELD_LIMIT_RECORD_BATCH_LENGTH = RecordBatchFW.FIELD_OFFSET_LEADER_EPOCH;

    private static final DirectBufferEx UNKNOWN_TOPIC_ID = new UnsafeBufferEx(new byte[16]);
    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();
    private final ProxyBeginExFW.Builder proxyBeginExRW = new ProxyBeginExFW.Builder();

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final MetadataRequestFW.Builder metadataRequestRW = new MetadataRequestFW.Builder();
    private final MetadataTopicRequestFW.Builder metadataTopicRequestRW = new MetadataTopicRequestFW.Builder();
    private final MetadataRequestPart2FW.Builder metadataRequestPart2RW = new MetadataRequestPart2FW.Builder();
    private final FindCoordinatorRequestFW.Builder findCoordinatorRequestRW = new FindCoordinatorRequestFW.Builder();
    private final KeyRequestFW.Builder keyRequestRW = new KeyRequestFW.Builder();
    private final FindCoordinatorRequestPart2FW.Builder findCoordinatorRequestPart2RW =
        new FindCoordinatorRequestPart2FW.Builder();
    private final ShareGroupHeartbeatRequestFW.Builder heartbeatRequestRW = new ShareGroupHeartbeatRequestFW.Builder();
    private final SubscribedTopicRequestFW.Builder subscribedTopicRequestRW = new SubscribedTopicRequestFW.Builder();
    private final ShareGroupHeartbeatRequestPart2FW.Builder heartbeatRequestPart2RW =
        new ShareGroupHeartbeatRequestPart2FW.Builder();
    private final ShareFetchRequestFW.Builder shareFetchRequestRW = new ShareFetchRequestFW.Builder();
    private final TopicRequestFW.Builder shareTopicRequestRW = new TopicRequestFW.Builder();
    private final PartitionRequestFW.Builder sharePartitionRequestRW = new PartitionRequestFW.Builder();
    private final AcknowledgementBatchRequestFW.Builder acknowledgementBatchRequestRW =
        new AcknowledgementBatchRequestFW.Builder();
    private final AcknowledgeTypeRequestFW.Builder acknowledgeTypeRequestRW = new AcknowledgeTypeRequestFW.Builder();
    private final AcknowledgementBatchRequestPart2FW.Builder acknowledgementBatchRequestPart2RW =
        new AcknowledgementBatchRequestPart2FW.Builder();
    private final PartitionRequestPart2FW.Builder sharePartitionRequestPart2RW = new PartitionRequestPart2FW.Builder();
    private final TopicRequestPart2FW.Builder shareTopicRequestPart2RW = new TopicRequestPart2FW.Builder();
    private final ShareFetchRequestPart2FW.Builder shareFetchRequestPart2RW = new ShareFetchRequestPart2FW.Builder();
    private final ForgottenTopicRequestFW.Builder forgottenTopicRequestRW = new ForgottenTopicRequestFW.Builder();
    private final ForgottenPartitionRequestFW.Builder forgottenPartitionRequestRW =
        new ForgottenPartitionRequestFW.Builder();
    private final ForgottenTopicRequestPart2FW.Builder forgottenTopicRequestPart2RW =
        new ForgottenTopicRequestPart2FW.Builder();
    private final ShareFetchRequestPart3FW.Builder shareFetchRequestPart3RW = new ShareFetchRequestPart3FW.Builder();
    private final ShareAcknowledgeRequestFW.Builder shareAcknowledgeRequestRW = new ShareAcknowledgeRequestFW.Builder();
    private final ShareAcknowledgeRequestPart2FW.Builder shareAcknowledgeRequestPart2RW =
        new ShareAcknowledgeRequestPart2FW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final ApiVersionsResponseFW apiVersionsResponseRO = new ApiVersionsResponseFW();
    private final ApiVersionsResponseKeyFW apiVersionsResponseKeyRO = new ApiVersionsResponseKeyFW();
    private final MetadataResponseFW metadataResponseRO = new MetadataResponseFW();
    private final BrokerMetadataFW brokerMetadataRO = new BrokerMetadataFW();
    private final MetadataResponsePart2FW metadataResponsePart2RO = new MetadataResponsePart2FW();
    private final TopicMetadataFW topicMetadataRO = new TopicMetadataFW();
    private final PartitionMetadataFW partitionMetadataRO = new PartitionMetadataFW();
    private final TopicMetadataPart2FW topicMetadataPart2RO = new TopicMetadataPart2FW();
    private final MetadataResponsePart3FW metadataResponsePart3RO = new MetadataResponsePart3FW();
    private final FindCoordinatorResponseFW findCoordinatorResponseRO = new FindCoordinatorResponseFW();
    private final CoordinatorResponseFW coordinatorResponseRO = new CoordinatorResponseFW();
    private final FindCoordinatorResponsePart2FW findCoordinatorResponsePart2RO = new FindCoordinatorResponsePart2FW();
    private final ShareGroupHeartbeatResponseFW heartbeatResponseRO = new ShareGroupHeartbeatResponseFW();
    private final AssignmentResponseFW assignmentResponseRO = new AssignmentResponseFW();
    private final TopicPartitionsResponseFW topicPartitionsResponseRO = new TopicPartitionsResponseFW();
    private final AssignedPartitionResponseFW assignedPartitionResponseRO = new AssignedPartitionResponseFW();
    private final TopicPartitionsResponsePart2FW topicPartitionsResponsePart2RO = new TopicPartitionsResponsePart2FW();
    private final AssignmentResponsePart2FW assignmentResponsePart2RO = new AssignmentResponsePart2FW();
    private final ShareGroupHeartbeatResponsePart2FW heartbeatResponsePart2RO = new ShareGroupHeartbeatResponsePart2FW();
    private final ShareFetchResponseFW shareFetchResponseRO = new ShareFetchResponseFW();
    private final TopicResponseFW shareTopicResponseRO = new TopicResponseFW();
    private final PartitionResponseFW sharePartitionResponseRO = new PartitionResponseFW();
    private final PartitionResponsePart2FW sharePartitionResponsePart2RO = new PartitionResponsePart2FW();
    private final AcquiredRecordResponseFW acquiredRecordResponseRO = new AcquiredRecordResponseFW();
    private final PartitionResponsePart3FW sharePartitionResponsePart3RO = new PartitionResponsePart3FW();
    private final TopicResponsePart2FW shareTopicResponsePart2RO = new TopicResponsePart2FW();
    private final ShareFetchResponsePart2FW shareFetchResponsePart2RO = new ShareFetchResponsePart2FW();
    private final ShareFetchResponsePart3FW shareFetchResponsePart3RO = new ShareFetchResponsePart3FW();
    private final ShareAcknowledgeResponseFW shareAcknowledgeResponseRO = new ShareAcknowledgeResponseFW();
    private final AcknowledgeTopicResponseFW acknowledgeTopicResponseRO = new AcknowledgeTopicResponseFW();
    private final AcknowledgePartitionResponseFW acknowledgePartitionResponseRO = new AcknowledgePartitionResponseFW();
    private final AcknowledgeTopicResponsePart2FW acknowledgeTopicResponsePart2RO = new AcknowledgeTopicResponsePart2FW();
    private final ShareAcknowledgeResponsePart2FW shareAcknowledgeResponsePart2RO = new ShareAcknowledgeResponsePart2FW();
    private final ShareAcknowledgeResponsePart3FW shareAcknowledgeResponsePart3RO = new ShareAcknowledgeResponsePart3FW();

    private final RecordBatchFW recordBatchRO = new RecordBatchFW();
    private final Varint32FW recordLengthRO = new Varint32FW();
    private final RecordHeaderFW recordHeaderRO = new RecordHeaderFW();
    private final RecordTrailerFW recordTrailerRO = new RecordTrailerFW();
    private final MessageHeaderFW messageHeaderRO = new MessageHeaderFW();
    private final OctetsFW valueRO = new OctetsFW();
    private final UnsafeBufferEx headersRO = new UnsafeBufferEx();

    private final KafkaShareClientDecoder decodeSaslHandshakeResponse = this::decodeSaslHandshakeResponse;
    private final KafkaShareClientDecoder decodeSaslHandshake = this::decodeSaslHandshake;
    private final KafkaShareClientDecoder decodeSaslHandshakeMechanisms = this::decodeSaslHandshakeMechanisms;
    private final KafkaShareClientDecoder decodeSaslHandshakeMechanism = this::decodeSaslHandshakeMechanism;
    private final KafkaShareClientDecoder decodeSaslAuthenticateResponse = this::decodeSaslAuthenticateResponse;
    private final KafkaShareClientDecoder decodeSaslAuthenticate = this::decodeSaslAuthenticate;
    private final KafkaShareClientDecoder decodeApiVersionsResponse = this::decodeApiVersionsResponse;
    private final KafkaShareClientDecoder decodeMetadataResponse = this::decodeMetadataResponse;
    private final KafkaShareClientDecoder decodeFindCoordinatorResponse = this::decodeFindCoordinatorResponse;
    private final KafkaShareClientDecoder decodeHeartbeatResponse = this::decodeHeartbeatResponse;
    private final KafkaShareClientDecoder decodeHeartbeatLeaveResponse = this::decodeHeartbeatLeaveResponse;
    private final KafkaShareClientDecoder decodeShareFetchResponse = this::decodeShareFetchResponse;
    private final KafkaShareClientDecoder decodeShareFetch = this::decodeShareFetch;
    private final KafkaShareClientDecoder decodeShareFetchTopics = this::decodeShareFetchTopics;
    private final KafkaShareClientDecoder decodeShareFetchTopic = this::decodeShareFetchTopic;
    private final KafkaShareClientDecoder decodeShareFetchPartitions = this::decodeShareFetchPartitions;
    private final KafkaShareClientDecoder decodeShareFetchPartition = this::decodeShareFetchPartition;
    private final KafkaShareClientDecoder decodeShareRecordBatches = this::decodeShareRecordBatches;
    private final KafkaShareClientDecoder decodeShareRecordBatch = this::decodeShareRecordBatch;
    private final KafkaShareClientDecoder decodeShareRecords = this::decodeShareRecords;
    private final KafkaShareClientDecoder decodeShareRecordLength = this::decodeShareRecordLength;
    private final KafkaShareClientDecoder decodeShareRecord = this::decodeShareRecord;
    private final KafkaShareClientDecoder decodeShareRecordInit = this::decodeShareRecordInit;
    private final KafkaShareClientDecoder decodeShareRecordValue = this::decodeShareRecordValue;
    private final KafkaShareClientDecoder decodeShareRecordSkip = this::decodeShareRecordSkip;
    private final KafkaShareClientDecoder decodeShareRecordBatchSkip = this::decodeShareRecordBatchSkip;
    private final KafkaShareClientDecoder decodeShareRecordSetSkip = this::decodeShareRecordSetSkip;
    private final KafkaShareClientDecoder decodeShareAcquired = this::decodeShareAcquired;
    private final KafkaShareClientDecoder decodeShareAcquiredRanges = this::decodeShareAcquiredRanges;
    private final KafkaShareClientDecoder decodeShareAcquiredRange = this::decodeShareAcquiredRange;
    private final KafkaShareClientDecoder decodeShareFetchPartitionTail = this::decodeShareFetchPartitionTail;
    private final KafkaShareClientDecoder decodeShareFetchTopicTail = this::decodeShareFetchTopicTail;
    private final KafkaShareClientDecoder decodeShareFetchTail = this::decodeShareFetchTail;
    private final KafkaShareClientDecoder decodeShareFetchEndpoints = this::decodeShareFetchEndpoints;
    private final KafkaShareClientDecoder decodeShareFetchEndpoint = this::decodeShareFetchEndpoint;
    private final KafkaShareClientDecoder decodeShareFetchTrailer = this::decodeShareFetchTrailer;
    private final KafkaShareClientDecoder decodeShareAcknowledgeResponse = this::decodeShareAcknowledgeResponse;
    private final KafkaShareClientDecoder decodeSkipResponse = this::decodeSkipResponse;
    private final KafkaShareClientDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final KafkaShareClientDecoder decodeReject = this::decodeReject;

    private final int kafkaTypeId;
    private final int proxyTypeId;
    private final int shareFetchMaxWaitMillis;
    private final int shareFetchMinBytes;
    private final int shareFetchMaxBytes;
    private final int shareFetchMaxRecords;
    private final int decodeMaxBytes;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final Signaler signaler;
    private final Clock clock;
    private final BindingHandler streamFactory;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final KafkaEventContext event;

    public KafkaClientShareFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        LongFunction<BudgetDebitor> supplyDebitor)
    {
        super(config, context);
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.shareFetchMaxWaitMillis = config.clientShareFetchMaxWaitMillis();
        this.shareFetchMinBytes = config.clientShareFetchMinBytes();
        this.shareFetchMaxBytes = config.clientShareFetchMaxBytes();
        this.shareFetchMaxRecords = config.clientShareFetchMaxRecords();
        this.signaler = context.signaler();
        this.clock = context.clock();
        this.streamFactory = context.streamFactory();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.decodeMaxBytes = decodePool.slotCapacity();
        this.encodePool = context.bufferPool();
        this.supplyBinding = supplyBinding;
        this.supplyDebitor = supplyDebitor;
        this.event = new KafkaEventContext(context);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
            kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_SHARE;
        final KafkaShareBeginExFW kafkaShareBeginEx = kafkaBeginEx.share();
        final String groupId = kafkaShareBeginEx.groupId().asString();
        final String memberId = kafkaShareBeginEx.memberId().asString();
        final String topic = kafkaShareBeginEx.topic().asString();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved = binding != null ? binding.resolve(authorization, topic) : null;

        if (resolved != null)
        {
            newStream = new KafkaShareStream(
                application,
                originId,
                routedId,
                initialId,
                affinity,
                resolved.id,
                groupId,
                memberId,
                topic,
                binding.servers(),
                binding.sasl(),
                binding.guard)::onApplication;
        }

        return newStream;
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<OctetsFW.Builder> extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension)
            .build();

        final MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    @FunctionalInterface
    private interface KafkaShareClientDecoder
    {
        int decode(
            KafkaShareClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int responseLimitOf(
        KafkaShareClient client,
        long traceId,
        DirectBufferEx buffer,
        int progress,
        int limit)
    {
        int responseLimit = -1;

        final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
        if (responseHeader != null)
        {
            final int candidateLimit = responseHeader.limit() + responseHeader.length();

            if (candidateLimit - progress > decodePool.slotCapacity())
            {
                client.onResponseTooLarge(traceId);
            }
            else if (candidateLimit <= limit)
            {
                responseLimit = candidateLimit;
            }
        }

        return responseLimit;
    }

    private int decodeApiVersionsResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final int responseLimit = responseLimitOf(client, traceId, buffer, progress, limit);

            if (responseLimit != -1)
            {
                final int correlationIdLimit = responseHeaderRO.limit() + RESPONSE_HEADER_SIZE_CORRELATION_ID;
                final ApiVersionsResponseFW response = apiVersionsResponseRO.tryWrap(buffer, correlationIdLimit, responseLimit);

                progress = responseLimit;

                if (response != null && response.errorCode() == ERROR_NONE)
                {
                    int keyProgress = response.limit();
                    for (int key = 0; key < response.apiKeyCount(); key++)
                    {
                        final ApiVersionsResponseKeyFW apiVersionsKey =
                            apiVersionsResponseKeyRO.tryWrap(buffer, keyProgress, responseLimit);
                        if (apiVersionsKey != null)
                        {
                            client.onDecodeApiVersionsKey(
                                apiVersionsKey.apiKey(), apiVersionsKey.minVersion(), apiVersionsKey.maxVersion());
                            keyProgress = apiVersionsKey.limit();
                        }
                    }

                    client.onDecodeApiVersions(traceId);
                }
                else
                {
                    final int errorCode = response != null ? response.errorCode() : ERROR_UNKNOWN_SERVER_ERROR;

                    client.onDecodeApiVersionsError(traceId, errorCode);
                }
            }
        }

        return progress;
    }

    private int decodeMetadataResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final BootstrapClient bootstrap = (BootstrapClient) client;
        final int length = limit - progress;

        if (length != 0)
        {
            final int responseLimit = responseLimitOf(client, traceId, buffer, progress, limit);

            if (responseLimit != -1)
            {
                final int responseProgress = responseHeaderRO.limit();

                progress = responseLimit;

                final boolean parsed = decodeMetadata(bootstrap, buffer, responseProgress, responseLimit);

                if (parsed)
                {
                    bootstrap.onDecodeMetadata(traceId);
                }
                else
                {
                    bootstrap.cleanupNetwork(traceId);
                }
            }
        }

        return progress;
    }

    private boolean decodeMetadata(
        BootstrapClient bootstrap,
        DirectBufferEx buffer,
        int progress,
        int limit)
    {
        boolean parsed = false;

        final MetadataResponseFW response = metadataResponseRO.tryWrap(buffer, progress, limit);

        parse:
        if (response != null)
        {
            progress = response.limit();

            for (int broker = 0; broker < response.brokerCount(); broker++)
            {
                final BrokerMetadataFW brokerMetadata = brokerMetadataRO.tryWrap(buffer, progress, limit);
                if (brokerMetadata == null)
                {
                    break parse;
                }

                bootstrap.onDecodeBroker(brokerMetadata.nodeId(), brokerMetadata.host().asString(), brokerMetadata.port());
                progress = brokerMetadata.limit();
            }

            final MetadataResponsePart2FW cluster = metadataResponsePart2RO.tryWrap(buffer, progress, limit);
            if (cluster == null)
            {
                break parse;
            }

            progress = cluster.limit();

            for (int topic = 0; topic < cluster.topicCount(); topic++)
            {
                final TopicMetadataFW topicMetadata = topicMetadataRO.tryWrap(buffer, progress, limit);
                if (topicMetadata == null)
                {
                    break parse;
                }

                bootstrap.onDecodeTopic(topicMetadata.errorCode(), topicMetadata.topicId());
                progress = topicMetadata.limit();

                for (int partition = 0; partition < topicMetadata.partitionCount(); partition++)
                {
                    final PartitionMetadataFW partitionMetadata = partitionMetadataRO.tryWrap(buffer, progress, limit);
                    if (partitionMetadata == null)
                    {
                        break parse;
                    }

                    bootstrap.onDecodePartition(partitionMetadata.partitionId(), partitionMetadata.leader());
                    progress = partitionMetadata.limit();
                }

                final TopicMetadataPart2FW topicPart2 = topicMetadataPart2RO.tryWrap(buffer, progress, limit);
                if (topicPart2 == null)
                {
                    break parse;
                }

                progress = topicPart2.limit();
            }

            parsed = metadataResponsePart3RO.tryWrap(buffer, progress, limit) != null;
        }

        return parsed;
    }

    private int decodeFindCoordinatorResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final BootstrapClient bootstrap = (BootstrapClient) client;
        final int length = limit - progress;

        if (length != 0)
        {
            final int responseLimit = responseLimitOf(client, traceId, buffer, progress, limit);

            if (responseLimit != -1)
            {
                final int responseProgress = responseHeaderRO.limit();

                progress = responseLimit;

                final boolean parsed = decodeFindCoordinator(bootstrap, buffer, responseProgress, responseLimit);

                if (parsed)
                {
                    bootstrap.onDecodeFindCoordinator(traceId);
                }
                else
                {
                    bootstrap.cleanupNetwork(traceId);
                }
            }
        }

        return progress;
    }

    private boolean decodeFindCoordinator(
        BootstrapClient bootstrap,
        DirectBufferEx buffer,
        int progress,
        int limit)
    {
        boolean parsed = false;

        final FindCoordinatorResponseFW response = findCoordinatorResponseRO.tryWrap(buffer, progress, limit);

        parse:
        if (response != null)
        {
            progress = response.limit();

            for (int coordinator = 0; coordinator < response.coordinatorCount(); coordinator++)
            {
                final CoordinatorResponseFW coordinatorResponse = coordinatorResponseRO.tryWrap(buffer, progress, limit);
                if (coordinatorResponse == null)
                {
                    break parse;
                }

                bootstrap.onDecodeCoordinator(
                    coordinatorResponse.error(),
                    coordinatorResponse.nodeId(),
                    coordinatorResponse.host().asString(),
                    coordinatorResponse.port());
                progress = coordinatorResponse.limit();
            }

            parsed = findCoordinatorResponsePart2RO.tryWrap(buffer, progress, limit) != null;
        }

        return parsed;
    }

    private int decodeHeartbeatResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final CoordinatorClient coordinator = (CoordinatorClient) client;
        final int length = limit - progress;

        if (length != 0)
        {
            final int responseLimit = responseLimitOf(client, traceId, buffer, progress, limit);

            if (responseLimit != -1)
            {
                final int responseProgress = responseHeaderRO.limit();

                progress = responseLimit;

                final boolean parsed = decodeHeartbeat(coordinator, traceId, buffer, responseProgress, responseLimit);

                if (!parsed)
                {
                    coordinator.cleanupNetwork(traceId);
                }
            }
        }

        return progress;
    }

    private boolean decodeHeartbeat(
        CoordinatorClient coordinator,
        long traceId,
        DirectBufferEx buffer,
        int progress,
        int limit)
    {
        boolean parsed = false;

        final ShareGroupHeartbeatResponseFW response = heartbeatResponseRO.tryWrap(buffer, progress, limit);

        parse:
        if (response != null)
        {
            progress = response.limit();

            final int error = response.error();
            final int memberEpoch = response.memberEpoch();
            final int heartbeatInterval = response.heartbeatIntervalMillis();
            final boolean assigned = response.assignment() == 1;

            if (error != ERROR_NONE)
            {
                parsed = true;
                coordinator.onDecodeHeartbeatError(traceId, error);
            }
            else
            {
                if (assigned)
                {
                    final AssignmentResponseFW assignment = assignmentResponseRO.tryWrap(buffer, progress, limit);
                    if (assignment == null)
                    {
                        break parse;
                    }

                    progress = assignment.limit();

                    coordinator.delegate.onAssignmentBegin();

                    for (int topic = 0; topic < assignment.topicCount(); topic++)
                    {
                        final TopicPartitionsResponseFW topicPartitions =
                            topicPartitionsResponseRO.tryWrap(buffer, progress, limit);
                        if (topicPartitions == null)
                        {
                            break parse;
                        }

                        progress = topicPartitions.limit();

                        for (int partition = 0; partition < topicPartitions.partitionCount(); partition++)
                        {
                            final AssignedPartitionResponseFW assignedPartition =
                                assignedPartitionResponseRO.tryWrap(buffer, progress, limit);
                            if (assignedPartition == null)
                            {
                                break parse;
                            }

                            coordinator.delegate.onAssignedPartition(assignedPartition.partitionIndex());
                            progress = assignedPartition.limit();
                        }

                        final TopicPartitionsResponsePart2FW topicPart2 =
                            topicPartitionsResponsePart2RO.tryWrap(buffer, progress, limit);
                        if (topicPart2 == null)
                        {
                            break parse;
                        }

                        progress = topicPart2.limit();
                    }

                    final AssignmentResponsePart2FW assignmentPart2 = assignmentResponsePart2RO.tryWrap(buffer, progress, limit);
                    if (assignmentPart2 == null)
                    {
                        break parse;
                    }

                    progress = assignmentPart2.limit();
                }

                parsed = heartbeatResponsePart2RO.tryWrap(buffer, progress, limit) != null;

                if (parsed)
                {
                    coordinator.onDecodeHeartbeat(traceId, memberEpoch, heartbeatInterval, assigned);
                }
            }
        }

        return parsed;
    }

    private int decodeHeartbeatLeaveResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final CoordinatorClient coordinator = (CoordinatorClient) client;
        final int length = limit - progress;

        if (length != 0)
        {
            final int responseLimit = responseLimitOf(client, traceId, buffer, progress, limit);

            if (responseLimit != -1)
            {
                progress = responseLimit;
                coordinator.onDecodeHeartbeatLeft(traceId);
            }
        }

        return progress;
    }

    private int decodeShareAcknowledgeResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final int length = limit - progress;

        if (length != 0)
        {
            final int responseLimit = responseLimitOf(client, traceId, buffer, progress, limit);

            if (responseLimit != -1)
            {
                final int responseProgress = responseHeaderRO.limit();

                progress = responseLimit;

                final boolean parsed = decodeShareAcknowledge(leader, traceId, buffer, responseProgress, responseLimit);

                if (!parsed)
                {
                    leader.cleanupNetwork(traceId);
                }
            }
        }

        return progress;
    }

    private boolean decodeShareAcknowledge(
        LeaderClient leader,
        long traceId,
        DirectBufferEx buffer,
        int progress,
        int limit)
    {
        boolean parsed = false;

        final ShareAcknowledgeResponseFW response = shareAcknowledgeResponseRO.tryWrap(buffer, progress, limit);

        parse:
        if (response != null)
        {
            progress = response.limit();

            final int error = response.error();

            for (int topic = 0; error == ERROR_NONE && topic < response.topicCount(); topic++)
            {
                final AcknowledgeTopicResponseFW topicResponse = acknowledgeTopicResponseRO.tryWrap(buffer, progress, limit);
                if (topicResponse == null)
                {
                    break parse;
                }

                progress = topicResponse.limit();

                for (int partition = 0; partition < topicResponse.partitionCount(); partition++)
                {
                    final AcknowledgePartitionResponseFW partitionResponse =
                        acknowledgePartitionResponseRO.tryWrap(buffer, progress, limit);
                    if (partitionResponse == null)
                    {
                        break parse;
                    }

                    leader.onDecodeAcknowledged(traceId, partitionResponse.partitionIndex(), partitionResponse.error());
                    progress = partitionResponse.limit();
                }

                final AcknowledgeTopicResponsePart2FW topicPart2 =
                    acknowledgeTopicResponsePart2RO.tryWrap(buffer, progress, limit);
                if (topicPart2 == null)
                {
                    break parse;
                }

                progress = topicPart2.limit();
            }

            parsed = true;

            if (error == ERROR_NONE)
            {
                final ShareAcknowledgeResponsePart2FW part2 = shareAcknowledgeResponsePart2RO.tryWrap(buffer, progress, limit);
                parsed = part2 != null;

                if (parsed)
                {
                    progress = part2.limit();

                    for (int node = 0; parsed && node < part2.nodeEndpointCount(); node++)
                    {
                        final BrokerMetadataFW endpoint = brokerMetadataRO.tryWrap(buffer, progress, limit);
                        parsed = endpoint != null;

                        if (parsed)
                        {
                            leader.delegate.onBrokerEndpoint(endpoint.nodeId(), endpoint.host().asString(), endpoint.port());
                            progress = endpoint.limit();
                        }
                    }
                }

                parsed = parsed && shareAcknowledgeResponsePart3RO.tryWrap(buffer, progress, limit) != null;
            }

            if (parsed)
            {
                leader.onDecodeAcknowledge(traceId, error);
            }
        }

        return parsed;
    }

    private int decodeSkipResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final int skip = Math.min(client.decodableResponseBytes, limit - progress);

        progress += skip;
        client.decodableResponseBytes -= skip;

        if (client.decodableResponseBytes == 0)
        {
            client.onResponseComplete(traceId);
        }

        return progress;
    }

    private int decodeIgnoreAll(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private int decodeReject(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (progress < limit)
        {
            client.decoder = decodeIgnoreAll;
            client.cleanupNetwork(traceId);
            progress = limit;
        }

        return progress;
    }

    private int decodeShareFetchResponse(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);

        if (responseHeader != null)
        {
            progress = responseHeader.limit();

            client.decodableResponseBytes = responseHeader.length();
            client.decoder = decodeShareFetch;
        }

        return progress;
    }

    private int decodeShareFetch(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final ShareFetchResponseFW response = shareFetchResponseRO.tryWrap(buffer, progress, limit);

        if (response != null)
        {
            progress = response.limit();

            client.decodableResponseBytes -= response.sizeof();

            final int error = response.error();
            if (error == ERROR_NONE)
            {
                leader.lockTimeoutMillis = response.acquisitionLockTimeoutMillis();
                leader.decodableTopics = response.topicCount();
                leader.onDecodeFetchAccepted(traceId);
                client.decoder = decodeShareFetchTopics;
            }
            else
            {
                leader.onDecodeFetchError(traceId, error);
            }
        }

        return progress;
    }

    private int decodeShareFetchTopics(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;

        client.decoder = leader.decodableTopics == 0 ? decodeShareFetchTail : decodeShareFetchTopic;

        return progress;
    }

    private int decodeShareFetchTopic(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final TopicResponseFW topicResponse = shareTopicResponseRO.tryWrap(buffer, progress, limit);

        if (topicResponse != null)
        {
            progress = topicResponse.limit();

            leader.decodablePartitions = topicResponse.partitionCount();
            client.decoder = decodeShareFetchPartitions;
        }

        return progress;
    }

    private int decodeShareFetchPartitions(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;

        client.decoder = leader.decodablePartitions == 0 ? decodeShareFetchTopicTail : decodeShareFetchPartition;

        return progress;
    }

    private int decodeShareFetchPartition(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final PartitionResponseFW partition = sharePartitionResponseRO.tryWrap(buffer, progress, limit);

        if (partition != null)
        {
            final int error = partition.error();
            final int recordSetBytes = Math.max(partition.recordSetLength(), 0);

            progress = partition.limit();

            final boolean continued = leader.onDecodePartition(traceId, partition.partitionIndex(), error,
                partition.acknowledgeError(), partition.currentLeaderId());

            if (continued)
            {
                leader.decodableRecordSetBytes = recordSetBytes;

                if (recordSetBytes == 0)
                {
                    client.decoder = decodeShareAcquired;
                }
                else
                {
                    client.decoder = error == ERROR_NONE && leader.decodePartition != null
                        ? decodeShareRecordBatches
                        : decodeShareRecordSetSkip;
                }
            }
        }

        return progress;
    }

    private int decodeShareRecordSetSkip(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final int skip = Math.min(leader.decodableRecordSetBytes, limit - progress);

        progress += skip;
        leader.decodableRecordSetBytes -= skip;

        if (leader.decodableRecordSetBytes == 0)
        {
            client.decoder = decodeShareAcquired;
        }

        return progress;
    }

    private int decodeShareRecordBatches(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;

        client.decoder = leader.decodableRecordSetBytes == 0 ? decodeShareAcquired : decodeShareRecordBatch;

        return progress;
    }

    private int decodeShareRecordBatch(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final int maxLimit = Math.min(progress + leader.decodableRecordSetBytes, limit);
        final RecordBatchFW recordBatch = recordBatchRO.tryWrap(buffer, progress, maxLimit);

        if (recordBatch != null)
        {
            final int attributes = recordBatch.attributes();
            final int recordSetProgress = recordBatch.sizeof();
            final int recordBatchProgress = recordSetProgress - FIELD_LIMIT_RECORD_BATCH_LENGTH;

            progress += recordSetProgress;

            leader.decodableRecordSetBytes -= recordSetProgress;
            leader.decodableRecordBatchBytes = recordBatch.length() - recordBatchProgress;
            leader.decodeBatchOffset = recordBatch.baseOffset();
            leader.decodeBatchTimestamp = recordBatch.firstTimestamp();
            leader.decodableRecords = recordBatch.recordCount();

            if (leader.decodableRecordBatchBytes < 0 || leader.decodableRecordBatchBytes > leader.decodableRecordSetBytes)
            {
                client.decoder = decodeReject;
            }
            else if ((attributes & ATTRIBUTES_COMPRESSION_MASK) != 0)
            {
                leader.onDecodeBatchRejected(recordBatch.baseOffset(), recordBatch.baseOffset() + recordBatch.lastOffsetDelta());
                client.decoder = decodeShareRecordBatchSkip;
            }
            else if ((attributes & ATTRIBUTES_CONTROL_MASK) != 0)
            {
                client.decoder = decodeShareRecordBatchSkip;
            }
            else
            {
                client.decoder = decodeShareRecords;
            }
        }
        else if (limit - progress >= leader.decodableRecordSetBytes)
        {
            client.decoder = decodeShareRecordSetSkip;
        }

        return progress;
    }

    private int decodeShareRecordBatchSkip(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final int skip = Math.min(leader.decodableRecordBatchBytes, limit - progress);

        progress += skip;
        leader.decodableRecordBatchBytes -= skip;
        leader.decodableRecordSetBytes -= skip;

        if (leader.decodableRecordBatchBytes == 0)
        {
            client.decoder = decodeShareRecordBatches;
        }

        return progress;
    }

    private int decodeShareRecords(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;

        client.decoder = leader.decodableRecords == 0 ? decodeShareRecordBatches : decodeShareRecordLength;

        return progress;
    }

    private int decodeShareRecordLength(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final Varint32FW recordLength = recordLengthRO.tryWrap(buffer, progress, limit);

        if (recordLength != null)
        {
            final int sizeofRecord = recordLength.sizeof() + recordLength.value();

            if (recordLength.value() < 0 || sizeofRecord > leader.decodableRecordBatchBytes)
            {
                client.decoder = decodeReject;
            }
            else
            {
                leader.decodableRecordBytes = sizeofRecord;

                if (limit - progress >= sizeofRecord)
                {
                    client.decoder = decodeShareRecord;
                }
                else if (sizeofRecord > decodeMaxBytes)
                {
                    client.decoder = decodeShareRecordInit;
                }
            }
        }

        return progress;
    }

    private int decodeShareRecord(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final KafkaShareStream stream = leader.delegate;
        final RecordHeaderFW recordHeader = recordHeaderRO.tryWrap(buffer, progress, limit);

        if (recordHeader == null)
        {
            client.decoder = decodeReject;
        }
        else if (stream.leaving())
        {
            client.decoder = decodeShareRecordSkip;
        }
        else
        {
            final int sizeofRecord = leader.decodableRecordBytes;
            final int recordLimit = progress + sizeofRecord;
            final long recordOffset = leader.decodeBatchOffset + recordHeader.offsetDelta();
            final OctetsFW key = recordHeader.key();
            final int valueLength = recordHeader.valueLength();
            final int valueOffset = recordHeader.limit();
            final int valueSize = Math.max(valueLength, 0);
            final int deliverable = stream.replyClaim(traceId, valueSize);

            if (deliverable == valueSize + stream.replyPad)
            {
                final long timestamp = leader.decodeBatchTimestamp + recordHeader.timestampDelta();
                final OctetsFW value = valueLength != -1 ? valueRO.wrap(buffer, valueOffset, valueOffset + valueLength) : null;
                final RecordTrailerFW recordTrailer = recordTrailerRO.wrap(buffer, valueOffset + valueSize, recordLimit);
                final int headerCount = recordTrailer.headerCount();
                final DirectBufferEx headers = wrapHeaders(buffer, recordTrailer.limit(), recordLimit - recordTrailer.limit());

                stream.doAppRecord(traceId, deliverable, leader.decodePartition.id, recordOffset, timestamp,
                    key, value, headerCount, headers);

                progress = recordLimit;
                leader.onDecodeRecordDelivered(recordOffset, sizeofRecord);
                client.decoder = decodeShareRecords;
            }
            else if (deliverable > stream.replyPad)
            {
                progress = doDecodeShareRecordInit(leader, traceId, deliverable, buffer, progress, limit, recordHeader);
            }
        }

        return progress;
    }

    private int decodeShareRecordInit(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final KafkaShareStream stream = leader.delegate;

        if (stream.leaving())
        {
            client.decoder = decodeShareRecordSkip;
        }
        else if (limit - progress >= Math.min(decodeMaxBytes, leader.decodableRecordBytes))
        {
            final RecordHeaderFW recordHeader = recordHeaderRO.tryWrap(buffer, progress, limit);
            final int valueSize = recordHeader != null ? Math.max(recordHeader.valueLength(), 0) : 0;
            final int valueAvailable = recordHeader != null ? Math.min(valueSize, limit - recordHeader.limit()) : 0;

            if (recordHeader == null || valueSize > 0 && valueAvailable == 0)
            {
                client.decoder = decodeReject;
            }
            else
            {
                final int deliverable = stream.replyClaim(traceId, valueAvailable);

                if (deliverable > stream.replyPad)
                {
                    progress = doDecodeShareRecordInit(leader, traceId, deliverable, buffer, progress, limit, recordHeader);
                }
            }
        }

        return progress;
    }

    private int doDecodeShareRecordInit(
        LeaderClient leader,
        long traceId,
        int deliverable,
        DirectBufferEx buffer,
        int progress,
        int limit,
        RecordHeaderFW recordHeader)
    {
        final KafkaShareStream stream = leader.delegate;
        final long recordOffset = leader.decodeBatchOffset + recordHeader.offsetDelta();
        final long timestamp = leader.decodeBatchTimestamp + recordHeader.timestampDelta();
        final OctetsFW key = recordHeader.key();
        final int valueOffset = recordHeader.limit();
        final int valueSize = Math.max(recordHeader.valueLength(), 0);
        final int fragment = deliverable - stream.replyPad;
        final OctetsFW valueInit = valueRO.wrap(buffer, valueOffset, valueOffset + fragment);
        final int recordProgress = recordHeader.sizeof() + fragment;

        stream.doAppRecordInit(traceId, deliverable, valueSize - fragment, leader.decodePartition.id, recordOffset,
            timestamp, key, valueInit);

        leader.decodeRecordOffset = recordOffset;
        leader.decodableRecordValueBytes = valueSize - fragment;
        leader.onDecodeRecordProgress(recordProgress);
        leader.decoder = decodeShareRecordValue;

        return progress + recordProgress;
    }

    private int decodeShareRecordValue(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final KafkaShareStream stream = leader.delegate;
        final int length = limit - progress;

        if (stream.leaving())
        {
            client.decoder = decodeShareRecordSkip;
        }
        else if (leader.decodableRecordValueBytes == 0 && length >= decodeMaxBytes && length < leader.decodableRecordBytes)
        {
            client.decoder = decodeReject;
        }
        else if (length >= Math.min(decodeMaxBytes, leader.decodableRecordBytes))
        {
            final int valueRemaining = leader.decodableRecordValueBytes;
            final int valueAvailable = Math.min(length, valueRemaining);
            final int deliverable = stream.replyClaim(traceId, valueAvailable);
            final int fragment = deliverable - stream.replyPad;

            if (fragment >= 0 && (fragment > 0 || valueAvailable == 0))
            {
                if (fragment == valueRemaining && length >= leader.decodableRecordBytes)
                {
                    final int recordProgress = leader.decodableRecordBytes;
                    final int recordLimit = progress + recordProgress;
                    final OctetsFW value = valueRO.wrap(buffer, progress, progress + fragment);
                    final RecordTrailerFW recordTrailer = recordTrailerRO.wrap(buffer, progress + fragment, recordLimit);
                    final int headersOffset = recordTrailer.limit();
                    final DirectBufferEx headers = wrapHeaders(buffer, headersOffset, recordLimit - headersOffset);

                    stream.doAppRecordFin(traceId, deliverable, leader.decodePartition.id, leader.decodeRecordOffset,
                        value, recordTrailer.headerCount(), headers);

                    progress = recordLimit;
                    leader.onDecodeRecordDelivered(leader.decodeRecordOffset, recordProgress);
                    client.decoder = decodeShareRecords;
                }
                else
                {
                    final OctetsFW value = valueRO.wrap(buffer, progress, progress + fragment);

                    stream.doAppRecordCont(traceId, deliverable, value);

                    progress += fragment;
                    leader.decodableRecordValueBytes -= fragment;
                    leader.onDecodeRecordProgress(fragment);
                }
            }
        }

        return progress;
    }

    private int decodeShareRecordSkip(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final int skip = Math.min(leader.decodableRecordBytes, limit - progress);

        progress += skip;
        leader.onDecodeRecordProgress(skip);

        if (leader.decodableRecordBytes == 0)
        {
            leader.decodableRecords--;
            client.decoder = decodeShareRecords;
        }

        return progress;
    }

    private int decodeShareAcquired(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final PartitionResponsePart2FW part2 = sharePartitionResponsePart2RO.tryWrap(buffer, progress, limit);

        if (part2 != null)
        {
            progress = part2.limit();

            leader.decodableAcquired = part2.acquiredRecordCount();
            client.decoder = decodeShareAcquiredRanges;
        }

        return progress;
    }

    private int decodeShareAcquiredRanges(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;

        client.decoder = leader.decodableAcquired == 0 ? decodeShareFetchPartitionTail : decodeShareAcquiredRange;

        return progress;
    }

    private int decodeShareAcquiredRange(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final AcquiredRecordResponseFW acquiredRecord = acquiredRecordResponseRO.tryWrap(buffer, progress, limit);

        if (acquiredRecord != null)
        {
            progress = acquiredRecord.limit();

            leader.onDecodeAcquired(acquiredRecord.firstOffset(), acquiredRecord.lastOffset(), acquiredRecord.deliveryCount());
            leader.decodableAcquired--;
            client.decoder = decodeShareAcquiredRanges;
        }

        return progress;
    }

    private int decodeShareFetchPartitionTail(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final PartitionResponsePart3FW part3 = sharePartitionResponsePart3RO.tryWrap(buffer, progress, limit);

        if (part3 != null)
        {
            progress = part3.limit();

            leader.onDecodePartitionComplete(traceId);
            leader.decodablePartitions--;
            client.decoder = decodeShareFetchPartitions;
        }

        return progress;
    }

    private int decodeShareFetchTopicTail(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final TopicResponsePart2FW part2 = shareTopicResponsePart2RO.tryWrap(buffer, progress, limit);

        if (part2 != null)
        {
            progress = part2.limit();

            leader.decodableTopics--;
            client.decoder = decodeShareFetchTopics;
        }

        return progress;
    }

    private int decodeShareFetchTail(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final ShareFetchResponsePart2FW part2 = shareFetchResponsePart2RO.tryWrap(buffer, progress, limit);

        if (part2 != null)
        {
            progress = part2.limit();

            leader.decodableEndpoints = part2.nodeEndpointCount();
            client.decoder = decodeShareFetchEndpoints;
        }

        return progress;
    }

    private int decodeShareFetchEndpoints(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;

        client.decoder = leader.decodableEndpoints == 0 ? decodeShareFetchTrailer : decodeShareFetchEndpoint;

        return progress;
    }

    private int decodeShareFetchEndpoint(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final BrokerMetadataFW endpoint = brokerMetadataRO.tryWrap(buffer, progress, limit);

        if (endpoint != null)
        {
            progress = endpoint.limit();

            leader.delegate.onBrokerEndpoint(endpoint.nodeId(), endpoint.host().asString(), endpoint.port());
            leader.decodableEndpoints--;
            client.decoder = decodeShareFetchEndpoints;
        }

        return progress;
    }

    private int decodeShareFetchTrailer(
        KafkaShareClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final LeaderClient leader = (LeaderClient) client;
        final ShareFetchResponsePart3FW part3 = shareFetchResponsePart3RO.tryWrap(buffer, progress, limit);

        if (part3 != null)
        {
            progress = part3.limit();

            client.decoder = decodeReject;
            leader.onDecodeFetch(traceId);
        }

        return progress;
    }

    private DirectBufferEx wrapHeaders(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        DirectBufferEx headers = EMPTY_BUFFER;
        if (length != 0)
        {
            headersRO.wrap(buffer, offset, length);
            headers = headersRO;
        }
        return headers;
    }

    private static final class SharePartition
    {
        private final int id;
        private final KafkaShareRanges inflight;
        private final KafkaShareRanges accepted;
        private final KafkaShareRanges released;
        private final KafkaShareRanges rejected;

        private int leaderId;

        SharePartition(
            int id,
            int leaderId)
        {
            this.id = id;
            this.leaderId = leaderId;
            this.inflight = new KafkaShareRanges();
            this.accepted = new KafkaShareRanges();
            this.released = new KafkaShareRanges();
            this.rejected = new KafkaShareRanges();
        }

        private int acknowledgementBatchCount()
        {
            return accepted.size() + released.size() + rejected.size();
        }

        private boolean acknowledged()
        {
            return acknowledgementBatchCount() != 0;
        }

        private void releaseInflight()
        {
            for (int index = 0; index < inflight.size(); index++)
            {
                released.add(inflight.first(index), inflight.last(index));
            }
            inflight.clear();
        }

        private void clearAcknowledgements()
        {
            accepted.clear();
            released.clear();
            rejected.clear();
        }
    }

    private final class KafkaShareStream
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long resolvedId;
        private final String groupId;
        private final String memberId;
        private final String topic;
        private final List<KafkaServerConfig> servers;
        private final KafkaSaslConfig sasl;
        private final GuardHandler guard;
        private final Int2ObjectHashMap<KafkaServerConfig> brokers;
        private final Int2IntHashMap leaderIds;
        private final MutableDirectBufferEx topicId;
        private final List<SharePartition> partitions;
        private final List<LeaderClient> leaders;
        private final IntArrayList assignment;
        private final IntArrayList fetched;
        private final BootstrapClient bootstrap;

        private CoordinatorClient coordinator;
        private String coordinatorHost;
        private int coordinatorPort;

        private int state;
        private long authorization;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private BudgetDebitor replyDeb;
        private long replyDebIndex = NO_DEBITOR_INDEX;

        KafkaShareStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long resolvedId,
            String groupId,
            String memberId,
            String topic,
            List<KafkaServerConfig> servers,
            KafkaSaslConfig sasl,
            GuardHandler guard)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.resolvedId = resolvedId;
            this.groupId = groupId;
            this.memberId = memberId;
            this.topic = topic;
            this.servers = servers;
            this.sasl = sasl;
            this.guard = guard;
            this.brokers = new Int2ObjectHashMap<>();
            this.leaderIds = new Int2IntHashMap(-1);
            this.topicId = new UnsafeBufferEx(new byte[TOPIC_ID_SIZE]);
            this.partitions = new ArrayList<>();
            this.leaders = new ArrayList<>();
            this.assignment = new IntArrayList();
            this.fetched = new IntArrayList();
            this.bootstrap = new BootstrapClient(this, originId, resolvedId, servers, sasl, guard);
        }

        private int replyBudget()
        {
            return replyMax - (int) (replySeq - replyAck);
        }

        private int replyClaim(
            long traceId,
            int valueSize)
        {
            final int maximum = valueSize + replyPad;
            int claimed = Math.min(maximum, replyBudget());

            if (claimed < maximum && claimed <= replyPad)
            {
                claimed = 0;
            }

            if (claimed > 0 && replyDebIndex != NO_DEBITOR_INDEX)
            {
                claimed = replyDeb.claim(traceId, replyDebIndex, replyId, Math.min(claimed, DEBITOR_CLAIM_MINIMUM), claimed, 0);
            }

            return claimed;
        }

        private boolean leaving()
        {
            return StreamState.initialClosed(state) || StreamState.replyClosed(state);
        }

        private SharePartition partition(
            int partitionId)
        {
            SharePartition found = null;
            for (int index = 0; found == null && index < partitions.size(); index++)
            {
                final SharePartition partition = partitions.get(index);
                if (partition.id == partitionId)
                {
                    found = partition;
                }
            }
            return found;
        }

        private LeaderClient leader(
            int nodeId)
        {
            LeaderClient found = null;
            for (int index = 0; found == null && index < leaders.size(); index++)
            {
                final LeaderClient leader = leaders.get(index);
                if (leader.nodeId == nodeId)
                {
                    found = leader;
                }
            }
            return found;
        }

        private void onApplication(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onApplicationBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onApplicationData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onApplicationFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onApplicationEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onApplicationAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onApplicationWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onApplicationReset(reset);
                break;
            default:
                break;
            }
        }

        private void onApplicationBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            authorization = begin.authorization();
            state = StreamState.openingInitial(state);

            doApplicationWindow(traceId, 0, decodePool.slotCapacity());

            bootstrap.doNetworkBegin(traceId, authorization, affinity);
        }

        private void onApplicationData(
            DataFW data)
        {
            final long traceId = data.traceId();

            cleanup(traceId, ERROR_UNKNOWN_SERVER_ERROR);
        }

        private void onApplicationFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            final ExtensionFW flushEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final KafkaFlushExFW kafkaFlushEx = flushEx != null && flushEx.typeId() == kafkaTypeId
                ? kafkaFlushExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            if (kafkaFlushEx != null && kafkaFlushEx.kind() == KafkaFlushExFW.KIND_SHARE)
            {
                final KafkaShareFlushExFW shareFlushEx = kafkaFlushEx.share();

                onAcknowledge(traceId,
                    shareFlushEx.partition().partitionId(),
                    shareFlushEx.partition().partitionOffset(),
                    shareFlushEx.type().get().value(),
                    shareFlushEx.correlationId());
            }
            else
            {
                cleanup(traceId, ERROR_UNKNOWN_SERVER_ERROR);
            }

            if (reserved > 0)
            {
                initialAck = initialSeq;
                doApplicationWindow(traceId, 0, initialMax);
            }
        }

        private void onApplicationEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = StreamState.closeInitial(state);

            onLeave(traceId);
        }

        private void onApplicationAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = StreamState.closeInitial(state);

            doApplicationAbort(traceId);

            onLeave(traceId);
        }

        private void onApplicationWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;
            this.replyBud = budgetId;

            assert replyAck <= replySeq;

            if (replyBud != NO_BUDGET_ID && replyDeb == null)
            {
                replyDeb = supplyDebitor.apply(replyBud);
                replyDebIndex = replyDeb.acquire(replyBud, replyId, this::onReplyBudget);
            }

            state = StreamState.openReply(state);

            onReplyBudget(traceId);
        }

        private void onApplicationReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = StreamState.closeReply(state);

            doApplicationReset(traceId, EMPTY_OCTETS);

            onLeave(traceId);
        }

        private void onReplyBudget(
            long traceId)
        {
            for (int index = 0; index < leaders.size(); index++)
            {
                leaders.get(index).onReplyBudget(traceId);
            }
        }

        private void onBrokerEndpoint(
            int nodeId,
            String host,
            int port)
        {
            brokers.put(nodeId, KafkaServerConfig.builder().host(host).port(port).build());
        }

        private void onCoordinator(
            long traceId,
            int nodeId,
            String host,
            int port)
        {
            final KafkaServerConfig server = KafkaServerConfig.builder().host(host).port(port).build();

            coordinatorHost = host;
            coordinatorPort = port;
            coordinator = new CoordinatorClient(this, originId, resolvedId, server, sasl, guard);
            coordinator.doNetworkBegin(traceId, authorization, affinity);
        }

        private void onNotCoordinator(
            long traceId)
        {
            final CoordinatorClient previous = coordinator;
            coordinator = null;

            if (previous != null)
            {
                previous.cleanupNetworkQuietly(traceId);
            }

            bootstrap.doFindCoordinator(traceId);
        }

        private void onAssignmentBegin()
        {
            assignment.clear();
        }

        private void onAssignedPartition(
            int partitionId)
        {
            assignment.addInt(partitionId);
        }

        private void onAssignment(
            long traceId)
        {
            if (!leaving())
            {
                for (int index = partitions.size() - 1; index >= 0; index--)
                {
                    final SharePartition partition = partitions.get(index);
                    if (!assignment.containsInt(partition.id))
                    {
                        final LeaderClient leader = leader(partition.leaderId);
                        if (leader != null)
                        {
                            leader.doConfirmUnsent(traceId, partition.id, ERROR_INVALID_RECORD_STATE);
                        }

                        partitions.remove(index);
                        fetched.removeInt(partition.id);
                    }
                }

                for (int index = 0; index < assignment.size(); index++)
                {
                    final int partitionId = assignment.getInt(index);
                    if (partition(partitionId) == null)
                    {
                        onPartitionAssigned(partitionId);
                    }
                }

                onPartitionsFetched(traceId);

                for (int index = 0; index < partitions.size(); index++)
                {
                    ensureLeader(traceId, partitions.get(index).leaderId);
                }

                for (int index = leaders.size() - 1; index >= 0; index--)
                {
                    leaders.get(index).onAssignmentChanged(traceId);
                }
            }
        }

        private void onPartitionAssigned(
            int partitionId)
        {
            final SharePartition partition = new SharePartition(partitionId, leaderIds.get(partitionId));

            int index = 0;
            while (index < partitions.size() && partitions.get(index).id < partitionId)
            {
                index++;
            }
            partitions.add(index, partition);
        }

        private void onPartitionFetched(
            int partitionId)
        {
            if (!fetched.containsInt(partitionId))
            {
                fetched.addInt(partitionId);
            }
        }

        private void onPartitionsFetched(
            long traceId)
        {
            if (!partitions.isEmpty() && fetched.size() == partitions.size())
            {
                doApplicationBegin(traceId);
            }
        }

        private void ensureLeader(
            long traceId,
            int nodeId)
        {
            final KafkaServerConfig server = brokers.get(nodeId);

            if (server == null)
            {
                cleanup(traceId, ERROR_UNKNOWN_SERVER_ERROR);
            }
            else if (leader(nodeId) == null)
            {
                final LeaderClient leader = new LeaderClient(this, originId, resolvedId, nodeId, server, sasl, guard);
                leaders.add(leader);
                leader.doNetworkBegin(traceId, authorization, affinity);
            }
        }

        private void onPartitionLeader(
            long traceId,
            int partitionId,
            int leaderId)
        {
            final SharePartition partition = partition(partitionId);

            if (partition != null && brokers.containsKey(leaderId))
            {
                partition.leaderId = leaderId;
                leaderIds.put(partitionId, leaderId);
                ensureLeader(traceId, leaderId);
            }
        }

        private void onAcknowledge(
            long traceId,
            int partitionId,
            long partitionOffset,
            int ackType,
            long correlationId)
        {
            final SharePartition partition = partition(partitionId);
            final LeaderClient leader = partition != null ? leader(partition.leaderId) : null;

            if (leader != null && partition.inflight.contains(partitionOffset))
            {
                partition.inflight.remove(partitionOffset);

                switch (ackType)
                {
                case ACKNOWLEDGE_TYPE_ACCEPT:
                    partition.accepted.add(partitionOffset, partitionOffset);
                    break;
                case ACKNOWLEDGE_TYPE_RELEASE:
                    partition.released.add(partitionOffset, partitionOffset);
                    break;
                default:
                    partition.rejected.add(partitionOffset, partitionOffset);
                    break;
                }

                if (correlationId != NO_CORRELATION_ID)
                {
                    leader.onConfirmPending(partitionId, partitionOffset, ackType, correlationId);
                }

                leader.doEncodeRequest(traceId);
            }
            else if (correlationId != NO_CORRELATION_ID)
            {
                doApplicationConfirm(traceId, partitionId, partitionOffset, ackType, correlationId, ERROR_INVALID_RECORD_STATE);
            }
        }

        private void onLeave(
            long traceId)
        {
            if (coordinator != null)
            {
                coordinator.cancelHeartbeat();
            }

            for (int index = leaders.size() - 1; index >= 0; index--)
            {
                leaders.get(index).onReplyBudget(traceId);
            }

            onLeaderClosed(traceId);
        }

        private void onLeaderClosed(
            long traceId)
        {
            boolean closed = true;
            for (int index = 0; closed && index < leaders.size(); index++)
            {
                closed = StreamState.closed(leaders.get(index).state);
            }

            if (closed && leaving() && coordinator != null)
            {
                coordinator.doLeave(traceId);
            }
            else if (closed && leaving())
            {
                onLeft(traceId);
            }
        }

        private void onLeft(
            long traceId)
        {
            if (!StreamState.replyClosed(state))
            {
                doApplicationEnd(traceId);
            }

            closeNetworks(traceId);
        }

        private void doApplicationBegin(
            long traceId)
        {
            if (!StreamState.replyOpening(state))
            {
                state = StreamState.openingReply(state);

                doBegin(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, affinity,
                    ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .share(s -> s.groupId(groupId)
                                     .memberId(memberId)
                                     .topic(topic)
                                     .host(coordinatorHost)
                                     .port(coordinatorPort))
                        .build()
                        .sizeof()));
            }
        }

        private void doApplicationRecord(
            long traceId,
            int flags,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                traceId, authorization, flags, replyBud, reserved, payload, extension);

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doAppRecord(
            long traceId,
            int reserved,
            int partitionId,
            long partitionOffset,
            long timestamp,
            OctetsFW key,
            OctetsFW value,
            int headerCount,
            DirectBufferEx headers)
        {
            final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .share(s -> setRecord(s, timestamp, partitionId, partitionOffset, key, headerCount, headers))
                .build();

            doApplicationRecord(traceId, COMPLETE, reserved, value, kafkaDataEx);
        }

        private void doAppRecordInit(
            long traceId,
            int reserved,
            int deferred,
            int partitionId,
            long partitionOffset,
            long timestamp,
            OctetsFW key,
            OctetsFW valueInit)
        {
            final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .share(s -> s.deferred(deferred)
                             .timestamp(timestamp)
                             .partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset))
                             .key(k -> setKey(k, key)))
                .build();

            doApplicationRecord(traceId, INIT, reserved, valueInit, kafkaDataEx);
        }

        private void doAppRecordCont(
            long traceId,
            int reserved,
            OctetsFW value)
        {
            doApplicationRecord(traceId, NONE, reserved, value, EMPTY_OCTETS);
        }

        private void doAppRecordFin(
            long traceId,
            int reserved,
            int partitionId,
            long partitionOffset,
            OctetsFW value,
            int headerCount,
            DirectBufferEx headers)
        {
            final KafkaDataExFW kafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .share(s -> setRecordFin(s, partitionId, partitionOffset, headerCount, headers))
                .build();

            doApplicationRecord(traceId, FIN, reserved, value, kafkaDataEx);
        }

        private void setRecord(
            KafkaShareDataExFW.Builder share,
            long timestamp,
            int partitionId,
            long partitionOffset,
            OctetsFW key,
            int headerCount,
            DirectBufferEx headers)
        {
            share.timestamp(timestamp)
                 .partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset))
                 .key(k -> setKey(k, key));

            setHeaders(share, headerCount, headers);
        }

        private void setRecordFin(
            KafkaShareDataExFW.Builder share,
            int partitionId,
            long partitionOffset,
            int headerCount,
            DirectBufferEx headers)
        {
            share.partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset));

            setHeaders(share, headerCount, headers);
        }

        private void setHeaders(
            KafkaShareDataExFW.Builder share,
            int headerCount,
            DirectBufferEx headers)
        {
            final int headersLimit = headers.capacity();
            int headerProgress = 0;
            for (int headerIndex = 0; headerIndex < headerCount; headerIndex++)
            {
                final MessageHeaderFW header = messageHeaderRO.wrap(headers, headerProgress, headersLimit);
                share.headersItem(i -> setHeader(i, header.key(), header.value()));
                headerProgress = header.limit();
            }
        }

        private void doApplicationAcquired(
            long traceId,
            int partitionId,
            int lockTimeoutMillis,
            LongArrayList firstOffsets,
            LongArrayList lastOffsets,
            IntArrayList deliveryCounts)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .shareAcquired(s -> setAcquired(s, partitionId, lockTimeoutMillis, firstOffsets, lastOffsets, deliveryCounts))
                .build();

            doFlush(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                traceId, authorization, 0, kafkaFlushEx);
        }

        private void setAcquired(
            KafkaShareAcquiredFlushExFW.Builder shareAcquired,
            int partitionId,
            int lockTimeoutMillis,
            LongArrayList firstOffsets,
            LongArrayList lastOffsets,
            IntArrayList deliveryCounts)
        {
            shareAcquired.partitionId(partitionId)
                .lockTimeoutMillis(lockTimeoutMillis);

            for (int index = 0; index < firstOffsets.size(); index++)
            {
                final long firstOffset = firstOffsets.getLong(index);
                final long lastOffset = lastOffsets.getLong(index);
                final int deliveryCount = deliveryCounts.getInt(index);

                shareAcquired.rangesItem(r -> r.firstOffset(firstOffset).lastOffset(lastOffset).deliveryCount(deliveryCount));
            }
        }

        private void doApplicationConfirm(
            long traceId,
            int partitionId,
            long partitionOffset,
            int ackType,
            long correlationId,
            int error)
        {
            final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .share(s -> s.partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset))
                             .type(t -> t.set(KafkaShareAckType.valueOf(ackType)))
                             .correlationId(correlationId)
                             .error(error))
                .build();

            doFlush(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                traceId, authorization, 0, kafkaFlushEx);
        }

        private void doApplicationEnd(
            long traceId)
        {
            cleanupApplicationDebitor();

            state = StreamState.closeReply(state);

            doEnd(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                traceId, authorization, EMPTY_EXTENSION);
        }

        private void doApplicationAbort(
            long traceId)
        {
            if (!StreamState.replyClosed(state))
            {
                cleanupApplicationDebitor();

                state = StreamState.closeReply(StreamState.abortingReply(state));

                doAbort(application, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }
        }

        private void doApplicationWindow(
            long traceId,
            int padding,
            int maximum)
        {
            initialMax = maximum;
            state = StreamState.openInitial(state);

            doWindow(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                traceId, authorization, NO_BUDGET_ID, padding);
        }

        private void doApplicationReset(
            long traceId,
            Flyweight extension)
        {
            if (!StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, authorization, extension);
            }
        }

        private void cleanup(
            long traceId,
            int error)
        {
            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .error(error)
                .build();

            doApplicationReset(traceId, kafkaResetEx);
            doApplicationAbort(traceId);

            cleanupNetworks(traceId);
        }

        private void closeNetworks(
            long traceId)
        {
            bootstrap.closeNetwork(traceId);

            if (coordinator != null)
            {
                coordinator.closeNetwork(traceId);
            }

            for (int index = leaders.size() - 1; index >= 0; index--)
            {
                leaders.get(index).closeNetwork(traceId);
            }

            cleanupApplicationDebitor();
        }

        private void cleanupNetworks(
            long traceId)
        {
            bootstrap.cleanupNetworkQuietly(traceId);

            if (coordinator != null)
            {
                coordinator.cleanupNetworkQuietly(traceId);
            }

            for (int index = leaders.size() - 1; index >= 0; index--)
            {
                leaders.get(index).cleanupNetworkQuietly(traceId);
            }

            cleanupApplicationDebitor();
        }

        private void cleanupApplicationDebitor()
        {
            if (replyDebIndex != NO_DEBITOR_INDEX)
            {
                replyDeb.release(replyDebIndex, replyId);
                replyDebIndex = NO_DEBITOR_INDEX;
                replyDeb = null;
            }
        }
    }

    private abstract class KafkaShareClient extends KafkaSaslClient
    {
        private final LongLongConsumer encodeApiVersionsRequest = this::doEncodeApiVersionsRequest;
        private final LongLongConsumer encodeSaslHandshakeRequest = this::doEncodeSaslHandshakeRequest;
        private final LongLongConsumer encodeSaslAuthenticateRequest = this::doEncodeSaslAuthenticateRequest;
        private final LongLongConsumer encodeShareRequest = this::doEncodeShareRequest;

        protected final KafkaShareStream delegate;
        private final short[] apiKeys;
        private final short[] apiVersions;

        protected MessageConsumer network;
        protected int state;
        protected long authorization;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialMin;
        private int initialPad;
        private long initialBudgetId = NO_BUDGET_ID;
        private long initialDebIndex = NO_DEBITOR_INDEX;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        protected int nextResponseId;
        protected int decodableResponseBytes;
        private int apiVersionsSupported;

        private BudgetDebitor initialDeb;
        protected KafkaShareClientDecoder decoder;
        private LongLongConsumer encoder;

        protected KafkaShareClient(
            KafkaShareStream delegate,
            long originId,
            long routedId,
            List<KafkaServerConfig> servers,
            KafkaSaslConfig sasl,
            GuardHandler guard,
            short[] apiKeys,
            short[] apiVersions)
        {
            super(servers, sasl, guard, originId, routedId);
            this.delegate = delegate;
            this.apiKeys = apiKeys;
            this.apiVersions = apiVersions;
            this.encoder = encodeApiVersionsRequest;
            this.decoder = decodeReject;
        }

        protected KafkaShareClient(
            KafkaShareStream delegate,
            long originId,
            long routedId,
            KafkaServerConfig server,
            KafkaSaslConfig sasl,
            GuardHandler guard,
            short[] apiKeys,
            short[] apiVersions)
        {
            super(server, sasl, guard, originId, routedId);
            this.delegate = delegate;
            this.apiKeys = apiKeys;
            this.apiVersions = apiVersions;
            this.encoder = encodeApiVersionsRequest;
            this.decoder = decodeReject;
        }

        protected abstract void doEncodeShareRequest(
            long traceId,
            long budgetId);

        protected void onSignal(
            int signalId,
            long traceId)
        {
        }

        protected final void doEncodeRequest(
            long traceId)
        {
            if (nextRequestId == nextResponseId && StreamState.initialOpened(state) && !StreamState.initialClosed(state))
            {
                encoder.accept(traceId, initialBudgetId);
            }
        }

        private void onNetwork(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetworkBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetworkData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetworkEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetworkAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetworkWindow(window);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetworkSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onNetworkBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            authorization = begin.authorization();
            state = StreamState.openingReply(state);

            doNetworkWindow(traceId, 0L, 0, 0, decodePool.slotCapacity());
        }

        private void onNetworkData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + data.reserved();
            authorization = data.authorization();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                cleanupNetwork(traceId);
            }
            else
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId);
                }
                else
                {
                    final OctetsFW payload = data.payload();
                    int reserved = data.reserved();
                    int offset = payload.offset();
                    int limit = payload.limit();

                    final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                    buffer.putBytes(decodeSlotOffset, payload.buffer(), offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;

                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;

                    decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
                }
            }
        }

        private void onNetworkEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = StreamState.closeReply(state);

            cleanupDecodeSlot();

            if (!StreamState.initialClosed(state))
            {
                cleanupNetwork(traceId);
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = StreamState.closeReply(state);

            cleanupNetwork(traceId);
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = StreamState.closeInitial(state);

            cleanupNetwork(traceId);
        }

        private void onNetworkWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int minimum = window.minimum();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialPad = padding;
            this.initialMin = minimum;
            this.initialBudgetId = budgetId;

            assert initialAck <= initialSeq;

            this.authorization = window.authorization();

            state = StreamState.openInitial(state);

            if (initialBudgetId != NO_BUDGET_ID && initialDebIndex == NO_DEBITOR_INDEX)
            {
                initialDeb = supplyDebitor.apply(initialBudgetId);
                initialDebIndex = initialDeb.acquire(initialBudgetId, initialId, this::doNetworkDataFlush);
                assert initialDebIndex != NO_DEBITOR_INDEX;
            }

            doNetworkDataFlush(traceId);

            doEncodeRequest(traceId);
        }

        private void doNetworkDataFlush(
            long traceId)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNetwork(encodeSlotTraceId, authorization, initialBudgetId, buffer, 0, limit);
            }
        }

        private void onNetworkSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final int signalId = signal.signalId();

            if (signalId == SIGNAL_NEXT_REQUEST)
            {
                doEncodeRequest(traceId);
            }
            else
            {
                onSignal(signalId, traceId);
            }
        }

        protected final void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            saslAuthorization = authorization;
            state = StreamState.openingInitial(state);

            Consumer<OctetsFW.Builder> extension = EMPTY_EXTENSION;

            if (server != null)
            {
                extension = e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                    .typeId(proxyTypeId)
                    .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                               .source("0.0.0.0")
                                               .destination(server.host)
                                               .sourcePort(0)
                                               .destinationPort(server.port)))
                    .infos(i -> i.item(ii -> ii.authority(server.host)))
                    .build()
                    .sizeof());
            }

            network = newStream(this::onNetwork, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                traceId, authorization, affinity, extension);
        }

        @Override
        protected void doNetworkData(
            long traceId,
            long budgetId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotTraceId = traceId;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNetwork(traceId, authorization, budgetId, buffer, offset, limit);
        }

        private void doNetworkEnd(
            long traceId)
        {
            if (!StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(state);

                cleanupEncodeSlot();
                cleanupBudget();

                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            deauthorizeGuardSession();
        }

        private void doNetworkAbort(
            long traceId)
        {
            if (!StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(StreamState.abortingInitial(state));

                doAbort(network, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            cleanupEncodeSlot();
            cleanupBudget();

            deauthorizeGuardSession();
        }

        private void doNetworkReset(
            long traceId)
        {
            if (!StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);

                doReset(network, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }

            cleanupDecodeSlot();

            deauthorizeGuardSession();
        }

        private void doNetworkWindow(
            long traceId,
            long budgetId,
            int minReplyNoAck,
            int minReplyPad,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !StreamState.replyOpened(state))
            {
                replyAck = newReplyAck;

                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = StreamState.openReply(state);

                doWindow(network, originId, routedId, replyId, replySeq, replyAck, state, replyMax,
                    traceId, authorization, budgetId, minReplyPad);
            }
        }

        private void encodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            final int initialBudget = Math.max(initialMax - (int) (initialSeq - initialAck), 0);
            final int reservedMax = Math.max(Math.min(length + initialPad, initialBudget), initialMin);

            int reserved = reservedMax;

            flush:
            if (reserved > 0)
            {
                if (initialDebIndex != NO_DEBITOR_INDEX)
                {
                    reserved = initialDeb.claim(traceId, initialDebIndex, initialId, reserved, reserved, 0);
                }

                if (reserved < initialPad || reserved == initialPad && length > 0)
                {
                    break flush;
                }

                doData(network, originId, routedId, initialId, initialSeq, initialAck, state, initialMax,
                    traceId, authorization, budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

                initialSeq += reserved;

                assert initialAck <= initialSeq;
            }

            final int flushed = Math.max(reserved - initialPad, 0);
            final int remaining = length - flushed;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId);
                }
                else
                {
                    final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + flushed, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();
            }
        }

        protected final void decodeNetwork(
            long traceId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);

                decodeNetwork(traceId, authorization, 0L, decodeSlotReserved, buffer, 0, decodeSlotOffset);
            }
        }

        private void decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            KafkaShareClientDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId);
                }
                else
                {
                    final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (limit - progress) * reserved / (limit - offset);

                    doNetworkWindow(traceId, budgetId, decodeSlotOffset, 0, replyMax);
                }
            }
            else
            {
                cleanupDecodeSlot();

                if (reserved > 0)
                {
                    doNetworkWindow(traceId, budgetId, 0, 0, replyMax);
                }
            }
        }

        private void doEncodeApiVersionsRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader =
                doEncodeRequestHeader(encodeBuffer, encodeProgress, API_VERSIONS_API_KEY, API_VERSIONS_API_VERSION);

            encodeProgress = requestHeader.limit();

            completeRequestHeader(encodeBuffer, encodeOffset, encodeProgress, requestHeader);

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeApiVersionsResponse;
        }

        protected final void completeRequestHeader(
            MutableDirectBufferEx encodeBuffer,
            int encodeOffset,
            int encodeProgress,
            RequestHeaderFW requestHeader)
        {
            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(requestId)
                .clientId(requestHeader.clientId())
                .build();
        }

        protected final RequestHeaderFW doEncodeRequestHeader(
            MutableDirectBufferEx encodeBuffer,
            int encodeOffset,
            short apiKey,
            short apiVersion)
        {
            return requestHeaderRW.wrap(encodeBuffer, encodeOffset, encodeBuffer.capacity())
                .length(0)
                .apiKey(apiKey)
                .apiVersion(apiVersion)
                .correlationId(0)
                .clientId(clientId)
                .build();
        }

        private void onDecodeApiVersionsKey(
            short apiKey,
            short minVersion,
            short maxVersion)
        {
            for (int index = 0; index < apiKeys.length; index++)
            {
                if (apiKeys[index] == apiKey && apiVersions[index] >= minVersion && apiVersions[index] <= maxVersion)
                {
                    apiVersionsSupported |= 1 << index;
                }
            }
        }

        private void onDecodeApiVersions(
            long traceId)
        {
            final int supportedAll = (1 << apiKeys.length) - 1;

            if (apiVersionsSupported == supportedAll)
            {
                encoder = sasl != null ? encodeSaslHandshakeRequest : encodeShareRequest;
                onResponseComplete(traceId);
            }
            else
            {
                final int unsupported = Integer.numberOfTrailingZeros(~apiVersionsSupported);

                event.apiVersionRejected(traceId, delegate.routedId, apiKeys[unsupported], apiVersions[unsupported]);

                decoder = decodeIgnoreAll;
                delegate.cleanup(traceId, ERROR_UNSUPPORTED_VERSION);
            }
        }

        private void onDecodeApiVersionsError(
            long traceId,
            int errorCode)
        {
            onDecodeResponseErrorCode(traceId, delegate.routedId, API_VERSIONS_API_KEY, API_VERSIONS_API_VERSION, errorCode);

            decoder = decodeIgnoreAll;
            delegate.cleanup(traceId, errorCode);
        }

        protected void onResponseComplete(
            long traceId)
        {
            nextResponseId++;

            decoder = decodeReject;

            signaler.signalNow(originId, routedId, initialId, traceId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void onResponseTooLarge(
            long traceId)
        {
            decoder = decodeIgnoreAll;
            delegate.cleanup(traceId, ERROR_MESSAGE_TOO_LARGE);
        }

        @Override
        protected void doDecodeSaslHandshakeResponse(
            long traceId)
        {
            decoder = decodeSaslHandshakeResponse;
        }

        @Override
        protected void doDecodeSaslHandshake(
            long traceId)
        {
            decoder = decodeSaslHandshake;
        }

        @Override
        protected void doDecodeSaslHandshakeMechanisms(
            long traceId)
        {
            decoder = decodeSaslHandshakeMechanisms;
        }

        @Override
        protected void doDecodeSaslHandshakeMechansim(
            long traceId)
        {
            decoder = decodeSaslHandshakeMechanism;
        }

        @Override
        protected void doDecodeSaslAuthenticateResponse(
            long traceId)
        {
            decoder = decodeSaslAuthenticateResponse;
        }

        @Override
        protected void doDecodeSaslAuthenticate(
            long traceId)
        {
            decoder = decodeSaslAuthenticate;
        }

        @Override
        protected void onDecodeSaslHandshakeResponse(
            long traceId,
            long authorization,
            int errorCode)
        {
            switch (errorCode)
            {
            case ERROR_NONE:
                encoder = encodeSaslAuthenticateRequest;
                decoder = decodeSaslAuthenticateResponse;
                break;
            default:
                delegate.cleanup(traceId, errorCode);
                break;
            }
        }

        @Override
        protected void onDecodeSaslAuthenticateResponse(
            long traceId,
            long authorization,
            int errorCode)
        {
            switch (errorCode)
            {
            case ERROR_NONE:
                encoder = encodeShareRequest;
                decoder = decodeReject;
                break;
            default:
                delegate.cleanup(traceId, errorCode);
                break;
            }
        }

        @Override
        protected void onDecodeSaslResponse(
            long traceId)
        {
            nextResponseId++;

            signaler.signalNow(originId, routedId, initialId, traceId, SIGNAL_NEXT_REQUEST, 0);
        }

        protected final void closeNetwork(
            long traceId)
        {
            doNetworkEnd(traceId);
            doNetworkReset(traceId);
        }

        protected final void cleanupNetworkQuietly(
            long traceId)
        {
            doNetworkAbort(traceId);
            doNetworkReset(traceId);
        }

        protected final void cleanupNetwork(
            long traceId)
        {
            cleanupNetworkQuietly(traceId);

            delegate.cleanup(traceId, ERROR_UNKNOWN_SERVER_ERROR);
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void cleanupBudget()
        {
            if (initialDebIndex != NO_DEBITOR_INDEX)
            {
                initialDeb.release(initialDebIndex, initialId);
                initialDebIndex = NO_DEBITOR_INDEX;
            }
        }
    }

    private final class BootstrapClient extends KafkaShareClient
    {
        private final LongLongConsumer encodeMetadataRequest = this::doEncodeMetadataRequest;
        private final LongLongConsumer encodeFindCoordinatorRequest = this::doEncodeFindCoordinatorRequest;
        private final LongLongConsumer encodeNothing = this::doEncodeNothing;

        private LongLongConsumer request;

        private int decodeTopicError;
        private int decodeCoordinatorError;
        private int decodeCoordinatorNodeId;
        private String decodeCoordinatorHost;
        private int decodeCoordinatorPort;

        BootstrapClient(
            KafkaShareStream delegate,
            long originId,
            long routedId,
            List<KafkaServerConfig> servers,
            KafkaSaslConfig sasl,
            GuardHandler guard)
        {
            super(delegate, originId, routedId, servers, sasl, guard, BOOTSTRAP_API_KEYS, BOOTSTRAP_API_VERSIONS);
            this.request = encodeMetadataRequest;
        }

        @Override
        protected void doEncodeShareRequest(
            long traceId,
            long budgetId)
        {
            request.accept(traceId, budgetId);
        }

        private void doFindCoordinator(
            long traceId)
        {
            request = encodeFindCoordinatorRequest;

            signaler.signalNow(originId, routedId, initialId, traceId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void doEncodeNothing(
            long traceId,
            long budgetId)
        {
        }

        private void doEncodeMetadataRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader =
                doEncodeRequestHeader(encodeBuffer, encodeProgress, METADATA_API_KEY, METADATA_API_VERSION);

            encodeProgress = requestHeader.limit();

            final MetadataRequestFW metadataRequest = metadataRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .taggedFields(0)
                .topicCount(1)
                .build();

            encodeProgress = metadataRequest.limit();

            final MetadataTopicRequestFW topicRequest = metadataTopicRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .topicId(UNKNOWN_TOPIC_ID, 0, TOPIC_ID_SIZE)
                .name(delegate.topic)
                .taggedFields(0)
                .build();

            encodeProgress = topicRequest.limit();

            final MetadataRequestPart2FW metadataRequestPart2 =
                metadataRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .allowAutoTopicCreation(0)
                    .includeTopicAuthorizedOperations(0)
                    .taggedFields(0)
                    .build();

            encodeProgress = metadataRequestPart2.limit();

            completeRequestHeader(encodeBuffer, encodeOffset, encodeProgress, requestHeader);

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeMetadataResponse;
        }

        private void doEncodeFindCoordinatorRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader =
                doEncodeRequestHeader(encodeBuffer, encodeProgress, FIND_COORDINATOR_API_KEY, FIND_COORDINATOR_API_VERSION);

            encodeProgress = requestHeader.limit();

            final FindCoordinatorRequestFW findCoordinatorRequest =
                findCoordinatorRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .keyType((byte) 0)
                    .keyCount(1)
                    .build();

            encodeProgress = findCoordinatorRequest.limit();

            final KeyRequestFW keyRequest = keyRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .key(delegate.groupId)
                .build();

            encodeProgress = keyRequest.limit();

            final FindCoordinatorRequestPart2FW findCoordinatorRequestPart2 =
                findCoordinatorRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .build();

            encodeProgress = findCoordinatorRequestPart2.limit();

            completeRequestHeader(encodeBuffer, encodeOffset, encodeProgress, requestHeader);

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeFindCoordinatorResponse;
        }

        private void onDecodeBroker(
            int nodeId,
            String host,
            int port)
        {
            delegate.onBrokerEndpoint(nodeId, host, port);
        }

        private void onDecodeTopic(
            int errorCode,
            OctetsFW topicId)
        {
            decodeTopicError = errorCode;

            if (errorCode == ERROR_NONE)
            {
                delegate.topicId.putBytes(0, topicId.buffer(), topicId.offset(), TOPIC_ID_SIZE);
            }
        }

        private void onDecodePartition(
            int partitionId,
            int leaderId)
        {
            delegate.leaderIds.put(partitionId, leaderId);
        }

        private void onDecodeMetadata(
            long traceId)
        {
            if (decodeTopicError != ERROR_NONE)
            {
                onDecodeResponseErrorCode(traceId, delegate.routedId, METADATA_API_KEY, METADATA_API_VERSION,
                    decodeTopicError, delegate.topic);

                decoder = decodeIgnoreAll;
                delegate.cleanup(traceId, decodeTopicError);
            }
            else
            {
                request = encodeFindCoordinatorRequest;
                onResponseComplete(traceId);
            }
        }

        private void onDecodeCoordinator(
            int errorCode,
            int nodeId,
            String host,
            int port)
        {
            decodeCoordinatorError = errorCode;
            decodeCoordinatorNodeId = nodeId;
            decodeCoordinatorHost = host;
            decodeCoordinatorPort = port;
        }

        private void onDecodeFindCoordinator(
            long traceId)
        {
            switch (decodeCoordinatorError)
            {
            case ERROR_NONE:
                request = encodeNothing;
                delegate.onCoordinator(traceId, decodeCoordinatorNodeId, decodeCoordinatorHost, decodeCoordinatorPort);
                onResponseComplete(traceId);
                break;
            case ERROR_COORDINATOR_NOT_AVAILABLE:
            case ERROR_COORDINATOR_LOAD_IN_PROGRESS:
                request = encodeFindCoordinatorRequest;
                onResponseComplete(traceId);
                break;
            default:
                onDecodeResponseErrorCode(traceId, delegate.routedId, FIND_COORDINATOR_API_KEY, FIND_COORDINATOR_API_VERSION,
                    decodeCoordinatorError);

                decoder = decodeIgnoreAll;
                delegate.cleanup(traceId, decodeCoordinatorError);
                break;
            }
        }
    }

    private final class CoordinatorClient extends KafkaShareClient
    {
        private final LongLongConsumer encodeHeartbeatRequest = this::doEncodeHeartbeatRequest;
        private final LongLongConsumer encodeHeartbeatLeaveRequest = this::doEncodeHeartbeatLeaveRequest;
        private final LongLongConsumer encodeNothing = this::doEncodeNothing;

        private LongLongConsumer request;
        private int memberEpoch;
        private long heartbeatId = NO_CANCEL_ID;

        CoordinatorClient(
            KafkaShareStream delegate,
            long originId,
            long routedId,
            KafkaServerConfig server,
            KafkaSaslConfig sasl,
            GuardHandler guard)
        {
            super(delegate, originId, routedId, server, sasl, guard, COORDINATOR_API_KEYS, COORDINATOR_API_VERSIONS);
            this.request = encodeHeartbeatRequest;
            this.memberEpoch = MEMBER_EPOCH_JOIN;
        }

        @Override
        protected void doEncodeShareRequest(
            long traceId,
            long budgetId)
        {
            request.accept(traceId, budgetId);
        }

        @Override
        protected void onSignal(
            int signalId,
            long traceId)
        {
            if (signalId == SIGNAL_HEARTBEAT_REQUEST)
            {
                heartbeatId = NO_CANCEL_ID;

                if (!delegate.leaving())
                {
                    request = encodeHeartbeatRequest;
                    doEncodeRequest(traceId);
                }
            }
        }

        private void doLeave(
            long traceId)
        {
            cancelHeartbeat();

            request = encodeHeartbeatLeaveRequest;
            doEncodeRequest(traceId);
        }

        private void cancelHeartbeat()
        {
            if (heartbeatId != NO_CANCEL_ID)
            {
                signaler.cancel(heartbeatId);
                heartbeatId = NO_CANCEL_ID;
            }
        }

        private void doEncodeNothing(
            long traceId,
            long budgetId)
        {
        }

        private void doEncodeHeartbeatRequest(
            long traceId,
            long budgetId)
        {
            doEncodeHeartbeat(traceId, budgetId, memberEpoch);

            decoder = decodeHeartbeatResponse;
        }

        private void doEncodeHeartbeatLeaveRequest(
            long traceId,
            long budgetId)
        {
            doEncodeHeartbeat(traceId, budgetId, MEMBER_EPOCH_LEAVE);

            decoder = decodeHeartbeatLeaveResponse;
        }

        private void doEncodeHeartbeat(
            long traceId,
            long budgetId,
            int epoch)
        {
            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = doEncodeRequestHeader(encodeBuffer, encodeProgress,
                SHARE_GROUP_HEARTBEAT_API_KEY, SHARE_GROUP_HEARTBEAT_API_VERSION);

            encodeProgress = requestHeader.limit();

            final boolean subscribe = epoch == MEMBER_EPOCH_JOIN;

            final ShareGroupHeartbeatRequestFW heartbeatRequest =
                heartbeatRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .groupId(delegate.groupId)
                    .memberId(delegate.memberId)
                    .memberEpoch(epoch)
                    .subscribedTopicCount(subscribe ? 1 : -1)
                    .build();

            encodeProgress = heartbeatRequest.limit();

            if (subscribe)
            {
                final SubscribedTopicRequestFW subscribedTopic =
                    subscribedTopicRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .name(delegate.topic)
                        .build();

                encodeProgress = subscribedTopic.limit();
            }

            final ShareGroupHeartbeatRequestPart2FW heartbeatRequestPart2 =
                heartbeatRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .build();

            encodeProgress = heartbeatRequestPart2.limit();

            completeRequestHeader(encodeBuffer, encodeOffset, encodeProgress, requestHeader);

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);
        }

        private void onDecodeHeartbeatError(
            long traceId,
            int errorCode)
        {
            switch (errorCode)
            {
            case ERROR_UNKNOWN_MEMBER_ID:
            case ERROR_FENCED_MEMBER_EPOCH:
                memberEpoch = MEMBER_EPOCH_JOIN;
                request = encodeHeartbeatRequest;
                delegate.onAssignmentBegin();
                delegate.onAssignment(traceId);
                onResponseComplete(traceId);
                break;
            case ERROR_NOT_COORDINATOR:
            case ERROR_COORDINATOR_NOT_AVAILABLE:
            case ERROR_COORDINATOR_LOAD_IN_PROGRESS:
                decoder = decodeIgnoreAll;
                delegate.onNotCoordinator(traceId);
                break;
            default:
                onDecodeResponseErrorCode(traceId, delegate.routedId, SHARE_GROUP_HEARTBEAT_API_KEY,
                    SHARE_GROUP_HEARTBEAT_API_VERSION, errorCode, delegate.topic);

                decoder = decodeIgnoreAll;
                delegate.cleanup(traceId, errorCode);
                break;
            }
        }

        private void onDecodeHeartbeat(
            long traceId,
            int newMemberEpoch,
            int heartbeatInterval,
            boolean assigned)
        {
            memberEpoch = newMemberEpoch;
            request = encodeNothing;

            if (assigned)
            {
                delegate.onAssignment(traceId);
            }

            if (!delegate.leaving())
            {
                heartbeatId = signaler.signalAt(currentTimeMillis() + heartbeatInterval,
                    originId, routedId, initialId, traceId, SIGNAL_HEARTBEAT_REQUEST, 0);
            }

            onResponseComplete(traceId);

            if (delegate.leaving())
            {
                delegate.onLeaderClosed(traceId);
            }
        }

        private void onDecodeHeartbeatLeft(
            long traceId)
        {
            decoder = decodeIgnoreAll;

            closeNetwork(traceId);

            delegate.onLeft(traceId);
        }
    }

    private final class LeaderClient extends KafkaShareClient
    {
        private final int nodeId;
        private final IntArrayList sessionPartitions;
        private final LongArrayList confirmKeys;
        private final LongArrayList confirmOffsets;
        private final LongArrayList confirmCorrelations;
        private final LongArrayList acquiredFirsts;
        private final LongArrayList acquiredLasts;
        private final IntArrayList acquiredCounts;
        private final KafkaShareRanges decodeStreamed;
        private final KafkaShareRanges decodeRejected;
        private final IntArrayList leaderMoves;

        private int sessionEpoch;
        private int confirmSent;

        private int decodableTopics;
        private int lockTimeoutMillis;
        private long fetchSentMillis;
        private int decodablePartitions;
        private int decodableRecordSetBytes;
        private int decodableRecordBatchBytes;
        private int decodableRecords;
        private int decodableRecordBytes;
        private int decodableRecordValueBytes;
        private int decodableAcquired;
        private int decodableEndpoints;
        private long decodeBatchOffset;
        private long decodeBatchTimestamp;
        private long decodeRecordOffset;
        private SharePartition decodePartition;

        LeaderClient(
            KafkaShareStream delegate,
            long originId,
            long routedId,
            int nodeId,
            KafkaServerConfig server,
            KafkaSaslConfig sasl,
            GuardHandler guard)
        {
            super(delegate, originId, routedId, server, sasl, guard, LEADER_API_KEYS, LEADER_API_VERSIONS);
            this.nodeId = nodeId;
            this.sessionPartitions = new IntArrayList();
            this.confirmKeys = new LongArrayList();
            this.confirmOffsets = new LongArrayList();
            this.confirmCorrelations = new LongArrayList();
            this.acquiredFirsts = new LongArrayList();
            this.acquiredLasts = new LongArrayList();
            this.acquiredCounts = new IntArrayList();
            this.decodeStreamed = new KafkaShareRanges();
            this.decodeRejected = new KafkaShareRanges();
            this.leaderMoves = new IntArrayList();
            this.sessionEpoch = SHARE_SESSION_EPOCH_INITIAL;
        }

        @Override
        protected void doEncodeShareRequest(
            long traceId,
            long budgetId)
        {
            if (delegate.leaving())
            {
                if (sessionEpoch > SHARE_SESSION_EPOCH_INITIAL)
                {
                    doEncodeShareAcknowledge(traceId, budgetId);
                }
                else
                {
                    closeNetwork(traceId);
                    delegate.onLeaderClosed(traceId);
                }
            }
            else if (fetchable())
            {
                doEncodeShareFetch(traceId, budgetId);
            }
        }

        private boolean assigned(
            int partitionId)
        {
            final SharePartition partition = delegate.partition(partitionId);
            return partition != null && partition.leaderId == nodeId;
        }

        private int assignedCount()
        {
            int count = 0;
            for (int index = 0; index < delegate.partitions.size(); index++)
            {
                if (delegate.partitions.get(index).leaderId == nodeId)
                {
                    count++;
                }
            }
            return count;
        }

        private boolean fetchable()
        {
            return assignedCount() != 0 &&
                (sessionEpoch == SHARE_SESSION_EPOCH_INITIAL || StreamState.replyOpening(delegate.state));
        }

        private void onAssignmentChanged(
            long traceId)
        {
            if (assignedCount() == 0)
            {
                closeNetwork(traceId);
                delegate.leaders.remove(this);
            }
            else
            {
                doEncodeRequest(traceId);
            }
        }

        private void onReplyBudget(
            long traceId)
        {
            decodeNetwork(traceId);
            doEncodeRequest(traceId);
        }

        private void onConfirmPending(
            int partitionId,
            long offset,
            int ackType,
            long correlationId)
        {
            confirmKeys.addLong(((long) partitionId << Integer.SIZE) | ackType);
            confirmOffsets.addLong(offset);
            confirmCorrelations.addLong(correlationId);
        }

        private void doConfirm(
            long traceId,
            int partitionId,
            int error)
        {
            for (int index = 0; index < confirmSent; )
            {
                final long key = confirmKeys.getLong(index);

                if ((int) (key >> Integer.SIZE) == partitionId)
                {
                    delegate.doApplicationConfirm(traceId, partitionId, confirmOffsets.getLong(index), (int) key,
                        confirmCorrelations.getLong(index), error);

                    confirmKeys.removeAt(index);
                    confirmOffsets.removeAt(index);
                    confirmCorrelations.removeAt(index);
                    confirmSent--;
                }
                else
                {
                    index++;
                }
            }
        }

        private void doConfirmUnsent(
            long traceId,
            int partitionId,
            int error)
        {
            for (int index = confirmSent; index < confirmKeys.size(); )
            {
                final long key = confirmKeys.getLong(index);

                if ((int) (key >> Integer.SIZE) == partitionId)
                {
                    delegate.doApplicationConfirm(traceId, partitionId, confirmOffsets.getLong(index), (int) key,
                        confirmCorrelations.getLong(index), error);

                    confirmKeys.removeAt(index);
                    confirmOffsets.removeAt(index);
                    confirmCorrelations.removeAt(index);
                }
                else
                {
                    index++;
                }
            }
        }

        private void doConfirmRemaining(
            long traceId,
            int error)
        {
            while (confirmSent > 0)
            {
                final long key = confirmKeys.getLong(0);

                doConfirm(traceId, (int) (key >> Integer.SIZE), error);
            }
        }

        private void doEncodeShareFetch(
            long traceId,
            long budgetId)
        {
            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader =
                doEncodeRequestHeader(encodeBuffer, encodeProgress, SHARE_FETCH_API_KEY, SHARE_FETCH_API_VERSION);

            encodeProgress = requestHeader.limit();

            int entryCount = 0;
            for (int index = 0; index < delegate.partitions.size(); index++)
            {
                final SharePartition partition = delegate.partitions.get(index);
                if (partition.leaderId == nodeId && (!sessionPartitions.containsInt(partition.id) || partition.acknowledged()))
                {
                    entryCount++;
                }
            }

            int forgottenCount = 0;
            for (int index = 0; index < sessionPartitions.size(); index++)
            {
                if (!assigned(sessionPartitions.getInt(index)))
                {
                    forgottenCount++;
                }
            }

            final ShareFetchRequestFW shareFetchRequest = shareFetchRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .taggedFields(0)
                .groupId(delegate.groupId)
                .memberId(delegate.memberId)
                .shareSessionEpoch(sessionEpoch)
                .maxWaitMillis(shareFetchMaxWaitMillis)
                .minBytes(shareFetchMinBytes)
                .maxBytes(shareFetchMaxBytes)
                .maxRecords(shareFetchMaxRecords)
                .batchSize(shareFetchMaxRecords)
                .topicCount(entryCount != 0 ? 1 : 0)
                .build();

            encodeProgress = shareFetchRequest.limit();

            if (entryCount != 0)
            {
                encodeProgress = doEncodeTopic(encodeBuffer, encodeProgress, encodeLimit, entryCount, false);
            }

            final ShareFetchRequestPart2FW shareFetchRequestPart2 =
                shareFetchRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .forgottenTopicCount(forgottenCount != 0 ? 1 : 0)
                    .build();

            encodeProgress = shareFetchRequestPart2.limit();

            if (forgottenCount != 0)
            {
                encodeProgress = doEncodeForgotten(encodeBuffer, encodeProgress, encodeLimit, forgottenCount);
            }

            final ShareFetchRequestPart3FW shareFetchRequestPart3 =
                shareFetchRequestPart3RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .build();

            encodeProgress = shareFetchRequestPart3.limit();

            completeRequestHeader(encodeBuffer, encodeOffset, encodeProgress, requestHeader);

            sessionEpoch++;
            confirmSent = confirmKeys.size();
            fetchSentMillis = clock.millis();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeShareFetchResponse;
        }

        private int doEncodeTopic(
            MutableDirectBufferEx encodeBuffer,
            int encodeProgress,
            int encodeLimit,
            int entryCount,
            boolean acknowledgeOnly)
        {
            final TopicRequestFW topicRequest = shareTopicRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .topicId(delegate.topicId, 0, TOPIC_ID_SIZE)
                .partitionCount(entryCount)
                .build();

            encodeProgress = topicRequest.limit();

            for (int index = 0; index < delegate.partitions.size(); index++)
            {
                final SharePartition partition = delegate.partitions.get(index);
                final boolean entry = partition.leaderId == nodeId &&
                    (partition.acknowledged() || !acknowledgeOnly && !sessionPartitions.containsInt(partition.id));

                if (entry)
                {
                    encodeProgress = doEncodePartition(encodeBuffer, encodeProgress, encodeLimit, partition);

                    if (!sessionPartitions.containsInt(partition.id))
                    {
                        sessionPartitions.addInt(partition.id);
                    }
                }
            }

            final TopicRequestPart2FW topicRequestPart2 = shareTopicRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .taggedFields(0)
                .build();

            return topicRequestPart2.limit();
        }

        private int doEncodePartition(
            MutableDirectBufferEx encodeBuffer,
            int encodeProgress,
            int encodeLimit,
            SharePartition partition)
        {
            final PartitionRequestFW partitionRequest = sharePartitionRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .partitionIndex(partition.id)
                .acknowledgementBatchCount(partition.acknowledgementBatchCount())
                .build();

            encodeProgress = partitionRequest.limit();
            encodeProgress = doEncodeAcknowledgements(encodeBuffer, encodeProgress, encodeLimit,
                partition.accepted, ACKNOWLEDGE_TYPE_ACCEPT);
            encodeProgress = doEncodeAcknowledgements(encodeBuffer, encodeProgress, encodeLimit,
                partition.released, ACKNOWLEDGE_TYPE_RELEASE);
            encodeProgress = doEncodeAcknowledgements(encodeBuffer, encodeProgress, encodeLimit,
                partition.rejected, ACKNOWLEDGE_TYPE_REJECT);

            partition.clearAcknowledgements();

            final PartitionRequestPart2FW partitionRequestPart2 =
                sharePartitionRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .build();

            return partitionRequestPart2.limit();
        }

        private int doEncodeAcknowledgements(
            MutableDirectBufferEx encodeBuffer,
            int encodeProgress,
            int encodeLimit,
            KafkaShareRanges ranges,
            int type)
        {
            for (int index = 0; index < ranges.size(); index++)
            {
                final AcknowledgementBatchRequestFW batch =
                    acknowledgementBatchRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .firstOffset(ranges.first(index))
                        .lastOffset(ranges.last(index))
                        .acknowledgeTypeCount(1)
                        .build();

                final AcknowledgeTypeRequestFW acknowledgeType =
                    acknowledgeTypeRequestRW.wrap(encodeBuffer, batch.limit(), encodeLimit)
                        .type((byte) type)
                        .build();

                final AcknowledgementBatchRequestPart2FW batchPart2 =
                    acknowledgementBatchRequestPart2RW.wrap(encodeBuffer, acknowledgeType.limit(), encodeLimit)
                        .taggedFields(0)
                        .build();

                encodeProgress = batchPart2.limit();
            }

            return encodeProgress;
        }

        private int doEncodeForgotten(
            MutableDirectBufferEx encodeBuffer,
            int encodeProgress,
            int encodeLimit,
            int forgottenCount)
        {
            final ForgottenTopicRequestFW forgottenTopic = forgottenTopicRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .topicId(delegate.topicId, 0, TOPIC_ID_SIZE)
                .partitionCount(forgottenCount)
                .build();

            encodeProgress = forgottenTopic.limit();

            for (int index = sessionPartitions.size() - 1; index >= 0; index--)
            {
                final int partitionId = sessionPartitions.getInt(index);
                if (!assigned(partitionId))
                {
                    final ForgottenPartitionRequestFW forgottenPartition =
                        forgottenPartitionRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                            .partitionIndex(partitionId)
                            .build();

                    encodeProgress = forgottenPartition.limit();

                    sessionPartitions.removeAt(index);
                }
            }

            final ForgottenTopicRequestPart2FW forgottenTopicPart2 =
                forgottenTopicRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .build();

            return forgottenTopicPart2.limit();
        }

        private void doEncodeShareAcknowledge(
            long traceId,
            long budgetId)
        {
            final MutableDirectBufferEx encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = doEncodeRequestHeader(encodeBuffer, encodeProgress,
                SHARE_ACKNOWLEDGE_API_KEY, SHARE_ACKNOWLEDGE_API_VERSION);

            encodeProgress = requestHeader.limit();

            int entryCount = 0;
            for (int index = 0; index < delegate.partitions.size(); index++)
            {
                final SharePartition partition = delegate.partitions.get(index);
                if (partition.leaderId == nodeId)
                {
                    partition.releaseInflight();

                    if (partition.acknowledged())
                    {
                        entryCount++;
                    }
                }
            }

            final ShareAcknowledgeRequestFW shareAcknowledgeRequest =
                shareAcknowledgeRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .groupId(delegate.groupId)
                    .memberId(delegate.memberId)
                    .shareSessionEpoch(SHARE_SESSION_EPOCH_FINAL)
                    .topicCount(entryCount != 0 ? 1 : 0)
                    .build();

            encodeProgress = shareAcknowledgeRequest.limit();

            if (entryCount != 0)
            {
                encodeProgress = doEncodeTopic(encodeBuffer, encodeProgress, encodeLimit, entryCount, true);
            }

            final ShareAcknowledgeRequestPart2FW shareAcknowledgeRequestPart2 =
                shareAcknowledgeRequestPart2RW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .taggedFields(0)
                    .build();

            encodeProgress = shareAcknowledgeRequestPart2.limit();

            completeRequestHeader(encodeBuffer, encodeOffset, encodeProgress, requestHeader);

            sessionEpoch = SHARE_SESSION_EPOCH_FINAL;
            confirmSent = confirmKeys.size();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeShareAcknowledgeResponse;
        }

        private void onDecodeFetchError(
            long traceId,
            int errorCode)
        {
            switch (errorCode)
            {
            case ERROR_SHARE_SESSION_NOT_FOUND:
            case ERROR_INVALID_SHARE_SESSION_EPOCH:
                sessionEpoch = SHARE_SESSION_EPOCH_INITIAL;
                sessionPartitions.clear();
                doConfirmRemaining(traceId, ERROR_INVALID_RECORD_STATE);
                decoder = decodeSkipResponse;
                break;
            default:
                onDecodeResponseErrorCode(traceId, delegate.routedId, SHARE_FETCH_API_KEY, SHARE_FETCH_API_VERSION,
                    errorCode, delegate.topic);

                decoder = decodeIgnoreAll;
                delegate.cleanup(traceId, errorCode);
                break;
            }
        }

        private void onDecodeFetchAccepted(
            long traceId)
        {
            for (int index = 0; index < delegate.partitions.size(); index++)
            {
                final SharePartition partition = delegate.partitions.get(index);
                if (partition.leaderId == nodeId)
                {
                    delegate.onPartitionFetched(partition.id);
                }
            }

            delegate.onPartitionsFetched(traceId);
        }

        private boolean onDecodePartition(
            long traceId,
            int partitionId,
            int errorCode,
            int acknowledgeError,
            int currentLeaderId)
        {
            boolean continued = true;

            decodePartition = delegate.partition(partitionId);
            acquiredFirsts.clear();
            acquiredLasts.clear();
            acquiredCounts.clear();
            decodeStreamed.clear();
            decodeRejected.clear();

            doConfirm(traceId, partitionId, acknowledgeError);

            switch (errorCode)
            {
            case ERROR_NONE:
                break;
            case ERROR_NOT_LEADER_OR_FOLLOWER:
                leaderMoves.addInt(partitionId);
                leaderMoves.addInt(currentLeaderId);
                break;
            default:
                onDecodeResponseErrorCode(traceId, delegate.routedId, SHARE_FETCH_API_KEY, SHARE_FETCH_API_VERSION,
                    errorCode, delegate.topic);

                continued = false;
                decoder = decodeIgnoreAll;
                delegate.cleanup(traceId, errorCode);
                break;
            }

            return continued;
        }

        private void onDecodeAcquired(
            long firstOffset,
            long lastOffset,
            int deliveryCount)
        {
            if (decodePartition != null)
            {
                acquiredFirsts.addLong(firstOffset);
                acquiredLasts.addLong(lastOffset);
                acquiredCounts.addInt(deliveryCount);

                decodePartition.inflight.add(firstOffset, lastOffset);
            }
        }

        private void onDecodeBatchRejected(
            long firstOffset,
            long lastOffset)
        {
            decodeRejected.add(firstOffset, lastOffset);
        }

        private void onDecodeRecordProgress(
            int sizeofRecord)
        {
            decodableRecordSetBytes -= sizeofRecord;
            decodableRecordBatchBytes -= sizeofRecord;
            decodableRecordBytes -= sizeofRecord;
        }

        private void onDecodeRecordDelivered(
            long offset,
            int sizeofRecord)
        {
            onDecodeRecordProgress(sizeofRecord);

            decodableRecords--;
            decodeStreamed.add(offset, offset);
        }

        private void onDecodePartitionComplete(
            long traceId)
        {
            if (decodePartition != null)
            {
                for (int index = 0; index < acquiredFirsts.size(); index++)
                {
                    for (long offset = acquiredFirsts.getLong(index); offset <= acquiredLasts.getLong(index); offset++)
                    {
                        if (decodeRejected.contains(offset))
                        {
                            decodePartition.inflight.remove(offset);
                            decodePartition.rejected.add(offset, offset);
                        }
                        else if (!decodeStreamed.contains(offset) && !delegate.leaving())
                        {
                            decodePartition.inflight.remove(offset);
                            decodePartition.accepted.add(offset, offset);
                        }
                    }
                }

                if (!decodeStreamed.isEmpty() && !delegate.leaving())
                {
                    delegate.doApplicationAcquired(traceId, decodePartition.id,
                        KafkaShareLock.remainingMillis(lockTimeoutMillis, fetchSentMillis, clock.millis()),
                        acquiredFirsts, acquiredLasts, acquiredCounts);
                }
            }

            decodePartition = null;
        }

        private void onDecodeFetch(
            long traceId)
        {
            doConfirmRemaining(traceId, ERROR_NONE);

            for (int index = 0; index < leaderMoves.size(); index += 2)
            {
                delegate.onPartitionLeader(traceId, leaderMoves.getInt(index), leaderMoves.getInt(index + 1));
            }
            leaderMoves.clear();

            onResponseComplete(traceId);

            if (assignedCount() == 0)
            {
                onAssignmentChanged(traceId);
            }
        }

        private void onDecodeAcknowledged(
            long traceId,
            int partitionId,
            int errorCode)
        {
            doConfirm(traceId, partitionId, errorCode);
        }

        private void onDecodeAcknowledge(
            long traceId,
            int errorCode)
        {
            doConfirmRemaining(traceId, errorCode);

            decoder = decodeIgnoreAll;
            closeNetwork(traceId);
            delegate.onLeaderClosed(traceId);
        }
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<OctetsFW.Builder> extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension)
            .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx payload,
        int offset,
        int length,
        Consumer<OctetsFW.Builder> extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload, offset, length)
            .extension(extension)
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        int reserved,
        Flyweight extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(NO_BUDGET_ID)
            .reserved(reserved)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension)
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int state,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .state(state)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private static KafkaKeyFW.Builder setKey(
        KafkaKeyFW.Builder key,
        OctetsFW value)
    {
        if (value == null)
        {
            key.length(-1).value((OctetsFW) null);
        }
        else
        {
            final int sizeOfValue = value.sizeof();
            key.length(sizeOfValue).value(value.buffer(), value.offset(), sizeOfValue);
        }

        return key;
    }

    private static KafkaHeaderFW.Builder setHeader(
        KafkaHeaderFW.Builder header,
        OctetsFW name,
        OctetsFW value)
    {
        final int sizeOfName = name.sizeof();
        header.nameLen(sizeOfName).name(name.buffer(), name.offset(), sizeOfName);

        if (value == null)
        {
            header.valueLen(-1).value((OctetsFW) null);
        }
        else
        {
            final int sizeOfValue = value.sizeof();
            header.valueLen(sizeOfValue).value(value.buffer(), value.offset(), sizeOfValue);
        }

        return header;
    }
}
