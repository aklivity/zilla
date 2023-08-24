/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongLongConsumer;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.config.ResourceRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.config.ResourceResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.AssignmentFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.FindCoordinatorRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.FindCoordinatorResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.HeartbeatRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.HeartbeatResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.JoinGroupRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.JoinGroupResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.LeaveGroupRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.LeaveGroupResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.LeaveMemberFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.MemberMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.ProtocolMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.SyncGroupRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.group.SyncGroupResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaGroupBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaClientGroupFactory extends KafkaClientSaslHandshaker implements BindingHandler
{
    private static final short ERROR_EXISTS = -1;
    private static final short ERROR_NONE = 0;
    private static final short ERROR_COORDINATOR_NOT_AVAILABLE = 15;
    private static final short ERROR_NOT_COORDINATOR_FOR_CONSUMER = 16;
    private static final short ERROR_UNKNOWN_MEMBER = 25;
    private static final short ERROR_MEMBER_ID_REQUIRED = 79;
    private static final short ERROR_REBALANCE_IN_PROGRESS = 27;
    private static final short SIGNAL_NEXT_REQUEST = 1;
    private static final short FIND_COORDINATOR_API_KEY = 10;
    private static final short FIND_COORDINATOR_API_VERSION = 1;
    private static final short JOIN_GROUP_API_KEY = 11;
    private static final short JOIN_GROUP_VERSION = 5;
    private static final short SYNC_GROUP_API_KEY = 14;
    private static final short SYNC_GROUP_VERSION = 3;
    private static final short LEAVE_GROUP_API_KEY = 13;
    private static final short LEAVE_GROUP_VERSION = 3;
    private static final short HEARTBEAT_API_KEY = 12;
    private static final short HEARTBEAT_VERSION = 3;

    private static final String UNKNOWN_MEMBER_ID = "";
    private static final String HIGHLANDER_PROTOCOL = "highlander";
    private static final byte GROUP_KEY_TYPE = 0x00;
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaResetExFW.Builder kafkaResetExRW = new KafkaResetExFW.Builder();
    private final ProxyBeginExFW.Builder proxyBeginExRW = new ProxyBeginExFW.Builder();

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final FindCoordinatorRequestFW.Builder findCoordinatorRequestRW = new FindCoordinatorRequestFW.Builder();
    private final JoinGroupRequestFW.Builder joinGroupRequestRW = new JoinGroupRequestFW.Builder();
    private final ProtocolMetadataFW.Builder protocolMetadataRW = new ProtocolMetadataFW.Builder();
    private final SyncGroupRequestFW.Builder syncGroupRequestRW = new SyncGroupRequestFW.Builder();
    private final AssignmentFW.Builder assignmentRW = new AssignmentFW.Builder();
    private final HeartbeatRequestFW.Builder heartbeatRequestRW = new HeartbeatRequestFW.Builder();
    private final LeaveGroupRequestFW.Builder leaveGroupRequestRW = new LeaveGroupRequestFW.Builder();
    private final LeaveMemberFW.Builder leaveMemberRW = new LeaveMemberFW.Builder();
    private final ResourceRequestFW.Builder resourceRequestRW = new ResourceRequestFW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final FindCoordinatorResponseFW findCoordinatorResponseRO = new FindCoordinatorResponseFW();
    private final JoinGroupResponseFW joinGroupResponseRO = new JoinGroupResponseFW();
    private final MemberMetadataFW memberMetadataRO = new MemberMetadataFW();
    private final SyncGroupResponseFW syncGroupResponseRO = new SyncGroupResponseFW();
    private final HeartbeatResponseFW heartbeatResponseRO = new HeartbeatResponseFW();
    private final LeaveGroupResponseFW leaveGroupResponseRO = new LeaveGroupResponseFW();
    private final LeaveMemberFW leaveMemberRO = new LeaveMemberFW();
    private final ResourceResponseFW resourceResponseRO = new ResourceResponseFW();

    private final KafkaGroupClusterClientDecoder decodeClusterSaslHandshakeResponse = this::decodeSaslHandshakeResponse;
    private final KafkaGroupClusterClientDecoder decodeClusterSaslHandshake = this::decodeSaslHandshake;
    private final KafkaGroupClusterClientDecoder decodeClusterSaslHandshakeMechanisms = this::decodeSaslHandshakeMechanisms;
    private final KafkaGroupClusterClientDecoder decodeClusterSaslHandshakeMechanism = this::decodeSaslHandshakeMechanism;
    private final KafkaGroupClusterClientDecoder decodeClusterSaslAuthenticateResponse = this::decodeSaslAuthenticateResponse;
    private final KafkaGroupClusterClientDecoder decodeClusterSaslAuthenticate = this::decodeSaslAuthenticate;
    private final KafkaGroupClusterClientDecoder decodeFindCoordinatorResponse = this::decodeFindCoordinatorResponse;
    private final KafkaGroupClusterClientDecoder decodeClusterReject = this::decodeClusterReject;
    private final KafkaGroupClusterClientDecoder decodeClusterIgnoreAll = this::decodeIgnoreAll;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorSaslHandshakeResponse =
        this::decodeSaslHandshakeResponse;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorSaslHandshake =
        this::decodeSaslHandshake;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorSaslHandshakeMechanisms =
        this::decodeSaslHandshakeMechanisms;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorSaslHandshakeMechanism =
        this::decodeSaslHandshakeMechanism;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorSaslAuthenticateResponse =
        this::decodeSaslAuthenticateResponse;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorSaslAuthenticate =
        this::decodeSaslAuthenticate;
    private final KafkaGroupCoordinatorClientDecoder decodeJoinGroupResponse =
        this::decodeJoinGroupResponse;
    private final KafkaGroupCoordinatorClientDecoder decodeSyncGroupResponse =
        this::decodeSyncGroupResponse;
    private final KafkaGroupCoordinatorClientDecoder decodeHeartbeatResponse =
        this::decodeHeartbeatResponse;
    private final KafkaGroupCoordinatorClientDecoder decodeLeaveGroupResponse =
        this::decodeLeaveGroupResponse;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorIgnoreAll = this::decodeIgnoreAll;
    private final KafkaGroupCoordinatorClientDecoder decodeCoordinatorReject = this::decodeCoordinatorReject;

    private final int kafkaTypeId;
    private final int proxyTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final Supplier<String> supplyInstanceId;
    private final Long2ObjectHashMap<GroupMembership> instanceIds;
    private final Object2ObjectHashMap<String, KafkaGroupStream> groupStreams;
    private final String clientId;
    private final Duration rebalanceTimeout;


    public KafkaClientGroupFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        LongFunction<BudgetDebitor> supplyDebitor)
    {
        super(config, context);
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool();
        this.supplyBinding = supplyBinding;
        this.rebalanceTimeout = config.clientGroupRebalanceTimeout();
        this.clientId = config.clientId();
        this.supplyInstanceId = config.clientInstanceIdSupplier();
        this.instanceIds = new Long2ObjectHashMap<>();
        this.groupStreams = new Object2ObjectHashMap<>();
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_GROUP;
        final KafkaGroupBeginExFW kafkaGroupBeginEx = kafkaBeginEx.group();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);
        final KafkaRouteConfig resolved;
        final int timeout = Math.min(kafkaGroupBeginEx.timeout(), 30_000);
        final String groupId = kafkaGroupBeginEx.groupId().asString();
        final String protocol = kafkaGroupBeginEx.protocol().asString();

        if (binding != null)
        {
            resolved = binding.resolve(authorization, null, groupId);

            if (resolved != null)
            {
                final long resolvedId = resolved.id;
                final KafkaSaslConfig sasl = binding.sasl();

                final GroupMembership groupMembership = instanceIds.get(binding.id);
                assert groupMembership != null;

                KafkaGroupStream stream = groupStreams.get(groupId);
                if (stream == null || HIGHLANDER_PROTOCOL.equals(protocol))
                {
                    if (stream != null)
                    {
                        stream.streamCleanup(traceId, traceId);
                    }

                    KafkaGroupStream group = new KafkaGroupStream(
                        application,
                        originId,
                        routedId,
                        initialId,
                        affinity,
                        resolvedId,
                        groupId,
                        protocol,
                        timeout,
                        groupMembership,
                        sasl);
                    newStream = group::onApplication;

                    groupStreams.put(groupId, group);
                }
            }
        }

        return newStream;
    }

    public void onAttached(
        long bindingId)
    {
        instanceIds.put(bindingId, new GroupMembership(supplyInstanceId.get()));
    }

    public void onDetached(
        long bindingId)
    {
        instanceIds.remove(bindingId);
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
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
        Consumer<OctetsFW.Builder> extension)
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
                .extension(extension)
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

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
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer payload,
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

    private void doDataNull(
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
        int reserved,
        Consumer<OctetsFW.Builder> extension)
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
                .budgetId(budgetId)
                .reserved(reserved)
                .extension(extension)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
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
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    @FunctionalInterface
    private interface KafkaGroupClusterClientDecoder
    {
        int decode(
            ClusterClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    @FunctionalInterface
    private interface KafkaGroupCoordinatorClientDecoder
    {
        int decode(
            CoordinatorClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeFindCoordinatorResponse(
        ClusterClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                client.decoder = decodeClusterIgnoreAll;
                break decode;
            }

            final int responseSize = responseHeader.length();

            if (length >= responseHeader.sizeof() + responseSize)
            {
                progress = responseHeader.limit();

                final FindCoordinatorResponseFW findCoordinatorResponse =
                        findCoordinatorResponseRO.tryWrap(buffer, progress, limit);

                if (findCoordinatorResponse == null)
                {
                    client.decoder = decodeClusterIgnoreAll;
                    break decode;
                }
                else if (findCoordinatorResponse.errorCode() == ERROR_COORDINATOR_NOT_AVAILABLE)
                {
                    client.onCoordinatorNotAvailable(traceId, authorization);
                }
                else if (findCoordinatorResponse.errorCode() == ERROR_NONE)
                {
                    client.onFindCoordinator(traceId, authorization,
                        findCoordinatorResponse.host(), findCoordinatorResponse.port());
                }
                else
                {
                    client.decoder = decodeClusterIgnoreAll;
                }

                progress = findCoordinatorResponse.limit();
            }
        }

        if (client.decoder == decodeClusterIgnoreAll)
        {
            client.onError(traceId);
        }

        return progress;
    }


    private int decodeClusterReject(
        ClusterClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.doNetworkReset(traceId);
        client.decoder = decodeClusterIgnoreAll;
        return limit;
    }

    private int decodeCoordinatorReject(
        CoordinatorClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        client.doNetworkReset(traceId);
        client.decoder = decodeCoordinatorIgnoreAll;
        return limit;
    }

    private int decodeIgnoreAll(
        KafkaSaslClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private int decodeJoinGroupResponse(
        CoordinatorClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                client.decoder = decodeJoinGroupResponse;
                progress = limit;
                break decode;
            }

            final int responseSize = responseHeader.length();

            if (length >= responseHeader.sizeof() + responseSize)
            {
                progress = responseHeader.limit();

                final JoinGroupResponseFW joinGroupResponse =
                    joinGroupResponseRO.tryWrap(buffer, progress, limit);

                final short errorCode = joinGroupResponse != null ? joinGroupResponse.errorCode() : ERROR_EXISTS;

                if (joinGroupResponse == null)
                {
                    client.decoder = decodeJoinGroupResponse;
                    progress = limit;
                    break decode;
                }
                else if (errorCode == ERROR_NOT_COORDINATOR_FOR_CONSUMER)
                {
                    client.onNotCoordinatorError(traceId, authorization);
                    progress = joinGroupResponse.limit();
                }
                else if (errorCode == ERROR_UNKNOWN_MEMBER)
                {
                    client.onJoinGroupMemberIdError(traceId, authorization, UNKNOWN_MEMBER_ID);
                    progress = joinGroupResponse.limit();
                }
                else if (errorCode == ERROR_MEMBER_ID_REQUIRED)
                {
                    client.onJoinGroupMemberIdError(traceId, authorization,
                        joinGroupResponse.memberId().asString());
                    progress = joinGroupResponse.limit();
                }
                else if (errorCode == ERROR_NONE)
                {
                    progress = joinGroupResponse.limit();
                    client.members.clear();

                    client.generationId = joinGroupResponse.generatedId();

                    metadata:
                    for (int i = 0; i < joinGroupResponse.memberCount(); i++)
                    {
                        final MemberMetadataFW memberMetadata = memberMetadataRO.tryWrap(buffer, progress, limit);
                        if (memberMetadata != null)
                        {
                            client.members.add(memberMetadata.memberId().asString());
                            progress = memberMetadata.limit();
                        }
                        else
                        {
                            break metadata;
                        }
                    }

                    client.onJoinGroupResponse(traceId, authorization, joinGroupResponse.leader().asString(),
                        joinGroupResponse.memberId().asString(), errorCode);
                }
                else
                {
                    client.decoder = decodeCoordinatorIgnoreAll;
                    break decode;
                }

            }
        }

        if (client.decoder == decodeCoordinatorIgnoreAll)
        {
            client.onError(traceId);
        }

        return progress;
    }

    private int decodeSyncGroupResponse(
        CoordinatorClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                client.decoder = decodeCoordinatorIgnoreAll;
                break decode;
            }

            final int responseSize = responseHeader.length();

            if (length >= responseHeader.sizeof() + responseSize)
            {
                progress = responseHeader.limit();

                final SyncGroupResponseFW syncGroupResponse =
                    syncGroupResponseRO.tryWrap(buffer, progress, limit);

                final short errorCode = syncGroupResponse != null ? syncGroupResponse.errorCode() : ERROR_EXISTS;

                if (syncGroupResponse == null)
                {
                    client.decoder = decodeCoordinatorIgnoreAll;
                    break decode;
                }
                else if (errorCode == ERROR_REBALANCE_IN_PROGRESS)
                {
                    client.onSynGroupRebalance(traceId, authorization);
                }
                else if (errorCode == ERROR_NONE)
                {
                    client.onSyncGroupResponse(traceId, authorization, syncGroupResponse.assignment());
                }
                else
                {
                    client.decoder = decodeCoordinatorIgnoreAll;
                    break decode;
                }

                progress = syncGroupResponse.limit();
            }
        }

        if (client.decoder == decodeCoordinatorIgnoreAll)
        {
            client.onError(traceId);
        }

        return progress;
    }

    private int decodeHeartbeatResponse(
        CoordinatorClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                client.decoder = decodeCoordinatorIgnoreAll;
                break decode;
            }

            final int responseSize = responseHeader.length();

            if (length >= responseHeader.sizeof() + responseSize)
            {
                progress = responseHeader.limit();

                final HeartbeatResponseFW heartbeatResponse =
                    heartbeatResponseRO.tryWrap(buffer, progress, limit);

                if (heartbeatResponse == null)
                {
                    client.decoder = decodeCoordinatorIgnoreAll;
                    break decode;
                }
                else if (heartbeatResponse.errorCode() == ERROR_REBALANCE_IN_PROGRESS)
                {
                    client.onRebalanceError(traceId, authorization);
                }
                else if (heartbeatResponse.errorCode() == ERROR_NONE)
                {
                    client.onHeartbeatResponse(traceId, authorization);
                }
                else
                {
                    client.decoder = decodeCoordinatorIgnoreAll;
                    break decode;
                }

                progress = heartbeatResponse.limit();
            }
        }

        if (client.decoder == decodeCoordinatorIgnoreAll)
        {
            client.onError(traceId);
        }

        return progress;
    }

    private int decodeLeaveGroupResponse(
        CoordinatorClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        decode:
        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader == null)
            {
                client.decoder = decodeCoordinatorIgnoreAll;
                break decode;
            }

            final int responseSize = responseHeader.length();

            if (length >= responseHeader.sizeof() + responseSize)
            {
                progress = responseHeader.limit();

                final LeaveGroupResponseFW leaveGroupResponse =
                    leaveGroupResponseRO.tryWrap(buffer, progress, limit);

                if (leaveGroupResponse == null)
                {
                    client.decoder = decodeCoordinatorIgnoreAll;
                    break decode;
                }
                else
                {
                    progress = leaveGroupResponse.limit();

                    members:
                    for (int i = 0; i < leaveGroupResponse.memberCount(); i++)
                    {
                        final LeaveMemberFW member = leaveMemberRO.tryWrap(buffer, progress, limit);
                        if (member != null)
                        {
                            progress = member.limit();
                        }
                        else
                        {
                            break members;
                        }
                    }

                    client.onLeaveGroupResponse(traceId, authorization);
                }
            }
        }

        if (client.decoder == decodeCoordinatorIgnoreAll)
        {
            client.onError(traceId);
        }

        return progress;
    }

    private final class KafkaGroupStream
    {
        private final MessageConsumer application;
        private final ClusterClient clusterClient;
        private final CoordinatorClient coordinatorClient;
        private final GroupMembership groupMembership;
        private final String groupId;
        private final String protocol;
        private final int timeout;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long resolvedId;
        private final KafkaSaslConfig sasl;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long replyBudgetId;

        KafkaGroupStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long resolvedId,
            String groupId,
            String protocol,
            int timeout,
            GroupMembership groupMembership,
            KafkaSaslConfig sasl)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.groupId = groupId;
            this.protocol = protocol;
            this.timeout = timeout;
            this.resolvedId = resolvedId;
            this.groupMembership = groupMembership;
            this.sasl = sasl;
            this.clusterClient = new ClusterClient(routedId, resolvedId, sasl, this);
            this.coordinatorClient = new CoordinatorClient(routedId, resolvedId, sasl, this);
        }

        private void onApplication(
            int msgTypeId,
            DirectBuffer buffer,
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
            final long authorization = begin.authorization();

            state = KafkaState.openingInitial(state);

            clusterClient.doNetworkBeginIfNecessary(traceId, authorization, affinity);
            doApplicationWindow(traceId, 0L, 0, 0, 0);
        }

        private void onApplicationData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();

            coordinatorClient.doSyncRequest(traceId, budgetId, data.payload());
        }

        private void onApplicationEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = KafkaState.closingInitial(state);
            coordinatorClient.doLeaveGroupRequest(traceId);
        }

        private void onApplicationFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();

            coordinatorClient.doHeartbeat(traceId);
        }

        private void onApplicationAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = KafkaState.closedInitial(state);

            clusterClient.doNetworkAbort(traceId);
            coordinatorClient.doNetworkAbort(traceId);

            cleanupApplication(traceId, EMPTY_OCTETS);
        }

        private void onApplicationWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;
            this.replyBudgetId = budgetId;

            assert replyAck <= replySeq;
        }

        private void onApplicationReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            clusterClient.doNetworkReset(traceId);
        }

        private boolean isApplicationReplyOpen()
        {
            return KafkaState.replyOpening(state);
        }

        private void doApplicationBeginIfNecessary(
            long traceId,
            long authorization)
        {
            if (!KafkaState.replyOpening(state))
            {
                doApplicationBegin(traceId, authorization);
            }
        }

        private void doApplicationBegin(
            long traceId,
            long authorization)
        {
            state = KafkaState.openingReply(state);

            final KafkaBeginExFW kafkaBeginEx =
                kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(kafkaTypeId)
                    .group(g -> g.groupId(groupId).protocol(protocol).timeout(timeout))
                    .build();

            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, kafkaBeginEx);
        }

        private void doApplicationData(
            long traceId,
            long authorization,
            OctetsFW payload,
            Consumer<OctetsFW.Builder> extension)
        {
            final int reserved = replyPad;

            if (payload.sizeof() > 0)
            {
                doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBudgetId, reserved,
                    payload.value(), payload.offset(), payload.sizeof(), extension);
            }
            else
            {
                doDataNull(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBudgetId, reserved, extension);
            }

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doApplicationEnd(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                state = KafkaState.closedReply(state);
                doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, 0, EMPTY_EXTENSION);
            }
        }

        private void doApplicationAbort(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                state = KafkaState.closedReply(state);
                doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, 0, EMPTY_EXTENSION);
            }
        }

        private void doApplicationWindow(
            long traceId,
            long budgetId,
            int minInitialNoAck,
            int minInitialPad,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !KafkaState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = KafkaState.openedInitial(state);

                doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, clusterClient.authorization, budgetId, minInitialPad);
            }
        }

        private void doApplicationReset(
            long traceId,
            Flyweight extension)
        {
            state = KafkaState.closedInitial(state);

            doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, clusterClient.authorization, extension);
        }

        private void doApplicationAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) && !KafkaState.replyClosed(state))
            {
                doApplicationAbort(traceId);
            }
        }

        private void doApplicationResetIfNecessary(
            long traceId,
            Flyweight extension)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doApplicationReset(traceId, extension);
            }
        }

        private void onNotCoordinatorError(
            long traceId,
            long authority)
        {
            clusterClient.doNetworkBeginIfNecessary(traceId, authority, affinity);
        }

        private void cleanupApplication(
            long traceId,
            int error)
        {
            final KafkaResetExFW kafkaResetEx = kafkaResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .error(error)
                .build();

            cleanupApplication(traceId, kafkaResetEx);
        }

        private void cleanupApplication(
            long traceId,
            Flyweight extension)
        {
            doApplicationResetIfNecessary(traceId, extension);
            doApplicationAbortIfNecessary(traceId);

            groupStreams.remove(groupId);
        }

        private void streamCleanup(
            long traceId,
            long authorizationId)
        {
            cleanupApplication(traceId, EMPTY_OCTETS);
            clusterClient.cleanupNetwork(traceId, authorizationId);
            coordinatorClient.cleanupNetwork(traceId, authorizationId);
        }
    }

    private final class ClusterClient extends KafkaSaslClient
    {
        private final LongLongConsumer encodeSaslHandshakeRequest = this::doEncodeSaslHandshakeRequest;
        private final LongLongConsumer encodeSaslAuthenticateRequest = this::doEncodeSaslAuthenticateRequest;
        private final LongLongConsumer encodeFindCoordinatorRequest = this::doEncodeFindCoordinatorRequest;
        private final KafkaGroupStream delegate;

        private MessageConsumer network;

        private int state;
        private long authorization;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int nextResponseId;

        private KafkaGroupClusterClientDecoder decoder;
        private LongLongConsumer encoder;

        ClusterClient(
            long originId,
            long routedId,
            KafkaSaslConfig sasl,
            KafkaGroupStream delegate)
        {
            super(sasl, originId, routedId);

            this.encoder = sasl != null ? encodeSaslHandshakeRequest : encodeFindCoordinatorRequest;
            this.delegate = delegate;
            this.decoder = decodeClusterReject;
        }

        private void onNetwork(
            int msgTypeId,
            DirectBuffer buffer,
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
            state = KafkaState.openingReply(state);

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
                onError(traceId);
            }
            else
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    onError(traceId);
                }
                else
                {
                    final OctetsFW payload = data.payload();
                    int reserved = data.reserved();
                    int offset = payload.offset();
                    int limit = payload.limit();

                    final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
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

            state = KafkaState.closedReply(state);

            cleanupDecodeSlotIfNecessary();

            if (!delegate.isApplicationReplyOpen())
            {
                onError(traceId);
            }
            else if (decodeSlot == NO_SLOT)
            {
                delegate.doApplicationEnd(traceId);
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            onError(traceId);
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            onError(traceId);
        }

        private void onNetworkWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
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
            this.initialBudgetId = budgetId;

            assert initialAck <= initialSeq;

            this.authorization = window.authorization();

            state = KafkaState.openedInitial(state);

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNetwork(encodeSlotTraceId, authorization, budgetId, buffer, 0, limit);
            }

            doEncodeRequestIfNecessary(traceId, budgetId);
        }

        private void onNetworkSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final int signalId = signal.signalId();

            if (signalId == SIGNAL_NEXT_REQUEST)
            {
                doEncodeRequestIfNecessary(traceId, initialBudgetId);
            }
        }

        private void doNetworkBeginIfNecessary(
            long traceId,
            long authorization,
            long affinity)
        {
            if (KafkaState.closed(state))
            {
                replyAck = 0;
                replySeq = 0;
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                doNetworkBegin(traceId, authorization, affinity);
            }
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            assert state == 0;

            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            state = KafkaState.openingInitial(state);

            network = newStream(this::onNetwork, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, EMPTY_EXTENSION);
        }

        @Override
        protected void doNetworkData(
            long traceId,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
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
            long traceId,
            long authorization)
        {
            if (!KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            cleanupEncodeSlotIfNecessary();
        }

        private void doNetworkAbort(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
                state = KafkaState.closedInitial(state);
            }

            cleanupEncodeSlotIfNecessary();
        }

        private void doNetworkReset(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doReset(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
                state = KafkaState.closedReply(state);
            }

            cleanupDecodeSlotIfNecessary();
        }

        private void doNetworkWindow(
            long traceId,
            long budgetId,
            int minReplyNoAck,
            int minReplyPad,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, minReplyPad);
            }
        }

        private void doEncodeRequestIfNecessary(
            long traceId,
            long budgetId)
        {
            if (nextRequestId == nextResponseId)
            {
                encoder.accept(traceId, budgetId);
            }
        }

        private void doEncodeFindCoordinatorRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(FIND_COORDINATOR_API_KEY)
                .apiVersion(FIND_COORDINATOR_API_VERSION)
                .correlationId(0)
                .clientId(clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final FindCoordinatorRequestFW findCoordinatorRequest =
                findCoordinatorRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .key(delegate.groupId)
                    .keyType(GROUP_KEY_TYPE)
                    .build();

            encodeProgress = findCoordinatorRequest.limit();

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(requestId)
                .clientId(requestHeader.clientId().asString())
                .build();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeFindCoordinatorResponse;
        }

        private void encodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int initialWin = initialMax - (int)(initialSeq - initialAck);
            final int length = Math.max(Math.min(initialWin - initialPad, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length + initialPad;

                doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

                initialSeq += reserved;

                assert initialAck <= initialSeq;
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    onError(traceId);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlotIfNecessary();
            }
        }

        private void decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int limit)
        {
            KafkaGroupClusterClientDecoder previous = null;
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
                    onError(traceId);
                }
                else
                {
                    final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (limit - progress) * reserved / (limit - offset);
                }

                doNetworkWindow(traceId, budgetId, decodeSlotOffset, 0, replyMax);
            }
            else
            {
                cleanupDecodeSlotIfNecessary();

                if (reserved > 0)
                {
                    doNetworkWindow(traceId, budgetId, 0, 0, replyMax);
                }
            }
        }

        @Override
        protected void doDecodeSaslHandshakeResponse(
            long traceId)
        {
            decoder = decodeClusterSaslHandshakeResponse;
        }

        @Override
        protected void doDecodeSaslHandshake(
            long traceId)
        {
            decoder = decodeClusterSaslHandshake;
        }

        @Override
        protected void doDecodeSaslHandshakeMechanisms(
            long traceId)
        {
            decoder = decodeClusterSaslHandshakeMechanisms;
        }

        @Override
        protected void doDecodeSaslHandshakeMechansim(
            long traceId)
        {
            decoder = decodeClusterSaslHandshakeMechanism;
        }

        @Override
        protected void doDecodeSaslAuthenticateResponse(
            long traceId)
        {
            decoder = decodeClusterSaslAuthenticateResponse;
        }

        @Override
        protected void doDecodeSaslAuthenticate(
            long traceId)
        {
            decoder = decodeClusterSaslAuthenticate;
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
                decoder = decodeClusterSaslAuthenticateResponse;
                break;
            default:
                delegate.cleanupApplication(traceId, errorCode);
                doNetworkEnd(traceId, authorization);
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
                encoder = encodeFindCoordinatorRequest;
                decoder = decodeFindCoordinatorResponse;
                break;
            default:
                delegate.cleanupApplication(traceId, errorCode);
                doNetworkEnd(traceId, authorization);
                break;
            }
        }

        @Override
        protected void onDecodeSaslResponse(
            long traceId)
        {
            nextResponseId++;
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void onCoordinatorNotAvailable(
            long traceId,
            long authorization)
        {
            nextResponseId++;

            encoder = encodeFindCoordinatorRequest;
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void onFindCoordinator(
            long traceId,
            long authorization,
            String16FW host,
            int port)
        {
            nextResponseId++;

            delegate.coordinatorClient.doNetworkBeginIfNecessary(traceId, authorization, 0, host, port);

            cleanupNetwork(traceId, authorization);
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            replySeq = 0;
            replyAck = 0;

            doNetworkEnd(traceId, authorization);
            doNetworkReset(traceId);
        }

        private void onError(
            long traceId)
        {
            doNetworkAbort(traceId);
            doNetworkReset(traceId);

            delegate.cleanupApplication(traceId, EMPTY_OCTETS);
        }

        private void cleanupDecodeSlotIfNecessary()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlotIfNecessary()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }
    }

    private final class CoordinatorClient extends KafkaSaslClient
    {
        private final LongLongConsumer encodeSaslHandshakeRequest = this::doEncodeSaslHandshakeRequest;
        private final LongLongConsumer encodeSaslAuthenticateRequest = this::doEncodeSaslAuthenticateRequest;
        private final LongLongConsumer encodeJoinGroupRequest = this::doEncodeJoinGroupRequest;
        private final LongLongConsumer encodeSyncGroupRequest = this::doEncodeSyncGroupRequest;
        private final LongLongConsumer encodeHeartbeatRequest = this::doEncodeHeartbeatRequest;
        private final LongLongConsumer encodeLeaveGroupRequest = this::doEncodeLeaveGroupRequest;
        private final List<String> members;
        private final KafkaGroupStream delegate;

        private MessageConsumer network;

        private int state;
        private long authorization;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int nextResponseId;
        private long heartbeatRequestId = NO_CANCEL_ID;

        private String leader;

        private int generationId;
        private KafkaGroupCoordinatorClientDecoder decoder;
        private LongLongConsumer encoder;
        private OctetsFW assignment = EMPTY_OCTETS;

        CoordinatorClient(
            long originId,
            long routedId,
            KafkaSaslConfig sasl,
            KafkaGroupStream delegate)
        {
            super(sasl, originId, routedId);

            this.encoder = sasl != null ? encodeSaslHandshakeRequest : encodeJoinGroupRequest;
            this.delegate = delegate;
            this.decoder = decodeCoordinatorReject;
            this.members = new ArrayList<>();
        }

        private void onNetwork(
            int msgTypeId,
            DirectBuffer buffer,
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
            state = KafkaState.openingReply(state);

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
                onError(traceId);
            }
            else
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    onError(traceId);
                }
                else
                {
                    final OctetsFW payload = data.payload();
                    int reserved = data.reserved();
                    int offset = payload.offset();
                    int limit = payload.limit();

                    final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
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

            state = KafkaState.closedReply(state);

            cleanupDecodeSlotIfNecessary();

            if (!delegate.isApplicationReplyOpen())
            {
                onError(traceId);
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            onError(traceId);
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            onError(traceId);
        }

        private void onNetworkWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
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
            this.initialBudgetId = budgetId;

            assert initialAck <= initialSeq;

            this.authorization = window.authorization();

            state = KafkaState.openedInitial(state);

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNetwork(encodeSlotTraceId, authorization, budgetId, buffer, 0, limit);
            }

            doEncodeRequestIfNecessary(traceId, budgetId);
        }

        private void onNetworkSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final int signalId = signal.signalId();

            if (signalId == SIGNAL_NEXT_REQUEST)
            {
                doEncodeRequestIfNecessary(traceId, initialBudgetId);
            }
        }

        private void doNetworkBeginIfNecessary(
            long traceId,
            long authorization,
            long affinity,
            String16FW host,
            int port)
        {
            if (KafkaState.closed(state))
            {
                replyAck = 0;
                replySeq = 0;
                state = 0;
            }

            if (!KafkaState.initialOpening(state))
            {
                doNetworkBegin(traceId, authorization, affinity, host, port);
            }
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity,
            String16FW host,
            int port)
        {
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            state = KafkaState.openingInitial(state);

            Consumer<OctetsFW.Builder> extension =  e -> e.set((b, o, l) -> proxyBeginExRW.wrap(b, o, l)
                .typeId(proxyTypeId)
                .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                    .source("0.0.0.0")
                    .destination(host)
                    .sourcePort(0)
                    .destinationPort(port)))
                .build()
                .sizeof());

            network = newStream(this::onNetwork, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, extension);
        }

        @Override
        protected void doNetworkData(
            long traceId,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
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
            long traceId,
            long authorization)
        {
            if (!KafkaState.initialClosed(state))
            {
                state = KafkaState.closedInitial(state);

                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
            }

            cleanupEncodeSlotIfNecessary();

        }

        private void doNetworkAbort(
            long traceId)
        {
            if (KafkaState.initialOpened(state) &&
                !KafkaState.initialClosed(state))
            {
                doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
                state = KafkaState.closedInitial(state);
            }

            cleanupEncodeSlotIfNecessary();
        }

        private void doNetworkReset(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doReset(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_OCTETS);
                state = KafkaState.closedReply(state);
            }

            cleanupDecodeSlotIfNecessary();
        }

        private void doNetworkWindow(
            long traceId,
            long budgetId,
            int minReplyNoAck,
            int minReplyPad,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, minReplyPad);
            }
        }

        private void doEncodeRequestIfNecessary(
            long traceId,
            long budgetId)
        {
            if (nextRequestId == nextResponseId)
            {
                encoder.accept(traceId, budgetId);
            }
        }

        private void doEncodeJoinGroupRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(JOIN_GROUP_API_KEY)
                .apiVersion(JOIN_GROUP_VERSION)
                .correlationId(0)
                .clientId(clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final String memberId = delegate.groupMembership.memberIds.getOrDefault(delegate.groupId, UNKNOWN_MEMBER_ID);

            final JoinGroupRequestFW joinGroupRequest =
                joinGroupRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .groupId(delegate.groupId)
                    .sessionTimeoutMillis(delegate.timeout)
                    .rebalanceTimeoutMillis((int) rebalanceTimeout.toMillis())
                    .memberId(memberId)
                    .groupInstanceId(delegate.groupMembership.instanceId)
                    .protocolType("consumer")
                    .protocolCount(1)
                    .build();

            encodeProgress = joinGroupRequest.limit();

            final ProtocolMetadataFW protocolMetadata =
                protocolMetadataRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .name(delegate.protocol)
                    .metadata(EMPTY_OCTETS)
                    .build();

            encodeProgress = protocolMetadata.limit();

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(requestId)
                .clientId(requestHeader.clientId().asString())
                .build();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeJoinGroupResponse;

            delegate.doApplicationBeginIfNecessary(traceId, authorization);
        }

        private void doEncodeSyncGroupRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(SYNC_GROUP_API_KEY)
                .apiVersion(SYNC_GROUP_VERSION)
                .correlationId(0)
                .clientId(clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final String memberId = delegate.groupMembership.memberIds.get(delegate.groupId);

            final boolean isLeader = leader.equals(memberId);

            final SyncGroupRequestFW syncGroupRequest =
                syncGroupRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .groupId(delegate.groupId)
                    .generatedId(generationId)
                    .memberId(memberId)
                    .groupInstanceId(delegate.groupMembership.instanceId)
                    .assignmentCount(isLeader ? members.size() : 0)
                    .build();

            encodeProgress = syncGroupRequest.limit();

            if (isLeader)
            {
                for (int i = 0; i < members.size(); i++)
                {
                    final AssignmentFW groupAssignment =
                        assignmentRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                            .memberId(members.get(i))
                            .value(assignment)
                            .build();

                    encodeProgress = groupAssignment.limit();
                }
            }

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(requestId)
                .clientId(requestHeader.clientId().asString())
                .build();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeSyncGroupResponse;
        }

        private void doEncodeHeartbeatRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(HEARTBEAT_API_KEY)
                .apiVersion(HEARTBEAT_VERSION)
                .correlationId(0)
                .clientId(clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final String memberId = delegate.groupMembership.memberIds.get(delegate.groupId);

            final HeartbeatRequestFW heartbeatRequest =
                heartbeatRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .groupId(delegate.groupId)
                    .generatedId(generationId)
                    .memberId(memberId)
                    .groupInstanceId(delegate.groupMembership.instanceId)
                    .build();

            encodeProgress = heartbeatRequest.limit();

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(requestId)
                .clientId(requestHeader.clientId().asString())
                .build();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeHeartbeatResponse;
        }

        private void doEncodeLeaveGroupRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .length(0)
                .apiKey(LEAVE_GROUP_API_KEY)
                .apiVersion(LEAVE_GROUP_VERSION)
                .correlationId(0)
                .clientId(clientId)
                .build();

            encodeProgress = requestHeader.limit();

            final LeaveGroupRequestFW leaveGroupRequest =
                leaveGroupRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .groupId(delegate.groupId)
                    .memberCount(1)
                    .build();

            encodeProgress = leaveGroupRequest.limit();

            final String memberId = delegate.groupMembership.memberIds.get(delegate.groupId);

            final LeaveMemberFW leaveMember = leaveMemberRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                .memberId(memberId)
                .groupInstanceId(delegate.groupMembership.instanceId)
                .build();

            encodeProgress = leaveMember.limit();

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                .length(requestSize)
                .apiKey(requestHeader.apiKey())
                .apiVersion(requestHeader.apiVersion())
                .correlationId(requestId)
                .clientId(requestHeader.clientId().asString())
                .build();

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            decoder = decodeLeaveGroupResponse;
        }

        private void doSyncRequest(
            long traceId,
            long budgetId,
            OctetsFW assignment)
        {
            this.assignment = assignment;
            doEncodeSyncGroupRequest(traceId, budgetId);
        }

        private void doHeartbeat(
            long traceId)
        {
            if (encoder != encodeJoinGroupRequest)
            {
                if (heartbeatRequestId != NO_CANCEL_ID)
                {
                    signaler.cancel(heartbeatRequestId);
                    heartbeatRequestId = NO_CANCEL_ID;
                }

                encoder = encodeHeartbeatRequest;
                signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
            }
        }

        private void doLeaveGroupRequest(
            long traceId)
        {
            if (heartbeatRequestId != NO_CANCEL_ID)
            {
                signaler.cancel(heartbeatRequestId);
                heartbeatRequestId = NO_CANCEL_ID;
            }

            encoder = encodeLeaveGroupRequest;
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void encodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int initialWin = initialMax - (int)(initialSeq - initialAck);
            final int length = Math.max(Math.min(initialWin - initialPad, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length + initialPad;

                doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

                initialSeq += reserved;

                assert initialAck <= initialSeq;
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    onError(traceId);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlotIfNecessary();
            }
        }

        private void decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int limit)
        {
            KafkaGroupCoordinatorClientDecoder previous = null;
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
                    onError(traceId);
                }
                else
                {
                    final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (limit - progress) * reserved / (limit - offset);
                }

                doNetworkWindow(traceId, budgetId, decodeSlotOffset, 0, replyMax);
            }
            else
            {
                cleanupDecodeSlotIfNecessary();

                if (reserved > 0)
                {
                    doNetworkWindow(traceId, budgetId, 0, 0, replyMax);
                }
            }
        }

        @Override
        protected void doDecodeSaslHandshakeResponse(
            long traceId)
        {
            decoder = decodeCoordinatorSaslHandshakeResponse;
        }

        @Override
        protected void doDecodeSaslHandshake(
            long traceId)
        {
            decoder = decodeCoordinatorSaslHandshake;
        }

        @Override
        protected void doDecodeSaslHandshakeMechanisms(
            long traceId)
        {
            decoder = decodeCoordinatorSaslHandshakeMechanisms;
        }

        @Override
        protected void doDecodeSaslHandshakeMechansim(
            long traceId)
        {
            decoder = decodeCoordinatorSaslHandshakeMechanism;
        }

        @Override
        protected void doDecodeSaslAuthenticateResponse(
            long traceId)
        {
            decoder = decodeCoordinatorSaslAuthenticateResponse;
        }

        @Override
        protected void doDecodeSaslAuthenticate(
            long traceId)
        {
            decoder = decodeCoordinatorSaslAuthenticate;
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
                decoder = decodeCoordinatorSaslAuthenticateResponse;
                break;
            default:
                delegate.cleanupApplication(traceId, errorCode);
                doNetworkEnd(traceId, authorization);
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
                encoder = encodeJoinGroupRequest;
                decoder = decodeJoinGroupResponse;
                break;
            default:
                delegate.cleanupApplication(traceId, errorCode);
                doNetworkEnd(traceId, authorization);
                break;
            }
        }

        @Override
        protected void onDecodeSaslResponse(
            long traceId)
        {
            nextResponseId++;
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void onNotCoordinatorError(
            long traceId,
            long authorization)
        {
            nextResponseId++;

            cleanupNetwork(traceId, authorization);

            delegate.onNotCoordinatorError(traceId, authorization);
        }

        private void onJoinGroupMemberIdError(
            long traceId,
            long authorization,
            String memberId)
        {
            nextResponseId++;

            delegate.groupMembership.memberIds.put(delegate.groupId, memberId);
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void onJoinGroupResponse(
            long traceId,
            long authorization,
            String leader,
            String memberId,
            int error)
        {
            nextResponseId++;

            this.leader = leader;

            delegate.groupMembership.memberIds.put(delegate.groupId, memberId);

            encoder = encodeSyncGroupRequest;
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void onSynGroupRebalance(
            long traceId,
            long authorization)
        {
            nextResponseId++;

            encoder = encodeJoinGroupRequest;
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void onSyncGroupResponse(
            long traceId,
            long authorization,
            OctetsFW assignment)
        {
            nextResponseId++;

            final String memberId = delegate.groupMembership.memberIds.get(delegate.groupId);

            delegate.doApplicationData(traceId, authorization, assignment,
                ex -> ex.set((b, o, l) -> kafkaDataExRW.wrap(b, o, l)
                    .typeId(kafkaTypeId)
                    .group(g -> g.leaderId(leader)
                        .memberId(memberId)
                        .members(m -> members.forEach(gp -> m.item(gm -> gm.set(gp, StandardCharsets.UTF_8)))))
                    .build()
                    .sizeof()));

            if (heartbeatRequestId != NO_CANCEL_ID)
            {
                encoder = encodeHeartbeatRequest;

                heartbeatRequestId = signaler.signalAt(currentTimeMillis() + delegate.timeout / 2,
                    originId, routedId, initialId,  SIGNAL_NEXT_REQUEST, 0);
            }
        }

        private void onHeartbeatResponse(
            long traceId,
            long authorization)
        {
            nextResponseId++;

            if (heartbeatRequestId != NO_CANCEL_ID)
            {
                signaler.cancel(heartbeatRequestId);
                heartbeatRequestId = NO_CANCEL_ID;
            }

            encoder = encodeHeartbeatRequest;

            heartbeatRequestId = signaler.signalAt(currentTimeMillis() + delegate.timeout / 2,
                originId, routedId, initialId,  SIGNAL_NEXT_REQUEST, 0);
        }

        private void onLeaveGroupResponse(
            long traceId,
            long authorization)
        {
            delegate.groupMembership.memberIds.remove(delegate.groupId);

            doNetworkEnd(traceId, authorization);
            doNetworkReset(traceId);

            delegate.doApplicationEnd(traceId);
            delegate.doApplicationResetIfNecessary(traceId, EMPTY_OCTETS);
        }

        private void onRebalanceError(
            long traceId,
            long authorization)
        {
            nextResponseId++;

            encoder = encodeJoinGroupRequest;
            signaler.signalNow(originId, routedId, initialId, SIGNAL_NEXT_REQUEST, 0);
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            doNetworkEnd(traceId, authorization);
            doNetworkReset(traceId);
        }

        private void onError(
            long traceId)
        {
            doNetworkAbort(traceId);
            doNetworkReset(traceId);

            delegate.cleanupApplication(traceId, EMPTY_OCTETS);
        }

        private void cleanupDecodeSlotIfNecessary()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlotIfNecessary()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }
    }

    private final class GroupMembership
    {
        public final String instanceId;
        public final Map<String, String> memberIds;

        GroupMembership(
            String instanceId)
        {
            this.instanceId = instanceId;
            this.memberIds = new Object2ObjectHashMap<>();
        }
    }
}
