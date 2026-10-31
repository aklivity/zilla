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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfiguration;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.card.AguiA2aAgentCard;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.card.AguiA2aAgentCardDecoder;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.card.AguiA2aExtension;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.card.AguiA2aOperation;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.config.AguiA2aBindingConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.config.AguiA2aRouteConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.event.AguiA2aEventContext;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aBodySink;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aMessageAssembler;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aMessageAssembler.ResumeStatus;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRejection;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRequestDecoder;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Kind;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetention;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.response.A2aEventDecoder;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.response.A2aEventRoot;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.response.AguiFrameSink;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.response.AguiReset;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.response.AguiResponseHost;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.response.AguiResponseMapper;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.response.InterruptHandle;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aCompaction;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aHandleKind;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aKeyHash;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aLocalOwnerState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aOwnerState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aThreadKey;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aAbortExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aEndExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aOutcome;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiAbortExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.AguiResetExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.RedirectFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class AguiA2aProxyFactory implements BindingHandler
{
    private static final String AGUI_TYPE_NAME = "agui";
    private static final String A2A_TYPE_NAME = "a2a";

    private static final int STATUS_INTERNAL_ERROR = 500;
    private static final int STATUS_BAD_GATEWAY = 502;
    private static final int STATUS_CONFLICT = 409;
    private static final String CODE_HANDLE_UNKNOWN = "handle_unknown";
    private static final String MESSAGE_HANDLE_UNKNOWN = "Interrupt no longer available";
    private static final String MESSAGE_INTERNAL_ERROR = "Internal error";
    private static final String MESSAGE_BAD_GATEWAY = "Bad Gateway";
    private static final int STATUS_UNSUPPORTED_MEDIA = 415;
    private static final String CODE_UNSUPPORTED_MEDIA = "unsupported_media";
    private static final String MESSAGE_UNSUPPORTED_MEDIA = "Unsupported media type";

    private static final int RECORD_HEADER = 3 * Integer.BYTES;

    private static final byte[] SNAPSHOT_OPEN = "[".getBytes();
    private static final byte[] SNAPSHOT_SEPARATOR = ",".getBytes();
    private static final byte[] SNAPSHOT_CLOSE = "]".getBytes();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final AguiBeginExFW aguiBeginExRO = new AguiBeginExFW();
    private final A2aResetExFW a2aResetExRO = new A2aResetExFW();
    private final A2aBeginExFW a2aBeginExRO = new A2aBeginExFW();
    private final A2aEndExFW a2aEndExRO = new A2aEndExFW();
    private final A2aDataExFW a2aDataExRO = new A2aDataExFW();
    private final A2aAbortExFW a2aAbortExRO = new A2aAbortExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final RedirectFW.Builder redirectRW = new RedirectFW.Builder();
    private final AguiBeginExFW.Builder aguiBeginExRW = new AguiBeginExFW.Builder();
    private final AguiEndExFW.Builder aguiEndExRW = new AguiEndExFW.Builder();
    private final AguiAbortExFW.Builder aguiAbortExRW = new AguiAbortExFW.Builder();
    private final AguiResetExFW.Builder aguiResetExRW = new AguiResetExFW.Builder();
    private final A2aBeginExFW.Builder a2aBeginExRW = new A2aBeginExFW.Builder();

    private final UnsafeBufferEx emptyRO = new UnsafeBufferEx(new byte[0]);

    private final AguiDecoder decodeRequest = this::decodeRequest;
    private final AguiDecoder decodeIgnore = this::decodeIgnore;

    private final AguiA2aConfiguration config;
    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx bodyBuffer;
    private final BufferPool decodePool;
    private final BufferPool retentionPool;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Long2ObjectHashMap<AguiA2aBindingConfig> bindings;
    private final AguiA2aEventContext events;
    private final AguiA2aOwnerState owners;
    private final int aguiTypeId;
    private final int a2aTypeId;
    private final long affinity;
    private final long handleTtlMillis;

    public AguiA2aProxyFactory(
        AguiA2aConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.retentionPool = context.bufferPool().duplicate();
        this.encodePool = context.bufferPool().duplicate();
        this.bodyBuffer = new UnsafeBufferEx(new byte[context.bufferPool().slotCapacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.events = new AguiA2aEventContext(context);
        this.aguiTypeId = context.supplyTypeId(AGUI_TYPE_NAME);
        this.a2aTypeId = context.supplyTypeId(A2A_TYPE_NAME);
        this.affinity = context.affinity();
        this.handleTtlMillis = config.handleTtl().toMillis();
        this.owners = new AguiA2aLocalOwnerState(
            context.signaler(),
            () -> context.clock().millis(),
            config.handleIdSupplier(),
            config.threadTtl().toMillis(),
            handleTtlMillis,
            config.threadCapacity());
    }

    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new AguiA2aBindingConfig(binding, context::supplyGuard));
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
        final long beginAffinity = begin.affinity();
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final AguiBeginExFW beginEx = aguiBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final AguiBeginExFW aguiBeginEx = beginEx != null && beginEx.typeId() == aguiTypeId ? beginEx : null;

        final AguiA2aBindingConfig binding = bindings.get(routedId);
        final String agent = aguiBeginEx != null && aguiBeginEx.agent() != null ? aguiBeginEx.agent().asString() : null;
        final AguiA2aRouteConfig route = binding != null && aguiBeginEx != null
            ? binding.resolveRoute(authorization, agent)
            : null;

        if (route != null)
        {
            final String identity = route.resolveIdentity(authorization);
            final String slug = route.resolveAgent(agent);
            final long keyHash = AguiA2aKeyHash.hash(
                identity,
                slug,
                aguiBeginEx.threadId().value(),
                0,
                aguiBeginEx.threadId().length());
            final long ownerAffinity = context.resolveAffinityId(keyHash);

            if (ownerAffinity != affinity)
            {
                newStream = new AguiA2aRedirectHandler(sender, binding.id, agent, ownerAffinity)::onAppMessage;
            }
            else
            {
                final String threadId = aguiBeginEx.threadId().asString();
                final String runId = aguiBeginEx.runId().asString();
                final String parentRunId = aguiBeginEx.parentRunId() != null
                    ? aguiBeginEx.parentRunId().asString()
                    : null;

                newStream = new AguiProxy(binding, route, sender, originId, routedId, initialId, authorization,
                    beginAffinity, new AguiA2aThreadKey(identity, slug, threadId), slug, threadId, runId,
                    parentRunId)::onAguiMessage;
            }
        }

        return newStream;
    }

    @FunctionalInterface
    private interface AguiDecoder
    {
        int decode(
            AguiProxy proxy,
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    private int decodeRequest(
        AguiProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final AguiA2aRequestDecoder requestDecoder = proxy.requestDecoder;
        final int progress = requestDecoder.decode(buffer, offset, limit, AguiA2aState.initialClosing(proxy.state));

        switch (requestDecoder.status())
        {
        case COMPLETE:
            proxy.decoder = decodeIgnore;
            proxy.onRequestDecoded(traceId);
            break;
        case REJECTED:
            proxy.decoder = decodeIgnore;
            proxy.onRequestRejected(traceId, requestDecoder.rejection());
            break;
        default:
            break;
        }

        return progress;
    }

    private int decodeIgnore(
        AguiProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    private final class AguiProxy implements AguiFrameSink, AguiResponseHost
    {
        private final AguiA2aBindingConfig binding;
        private final AguiA2aRouteConfig route;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long proxyAffinity;
        private final AguiA2aThreadKey key;
        private final String agent;
        private final String threadId;
        private final String runId;
        private final String parentRunId;
        private final AguiA2aRetention retention;
        private final AguiA2aRequestDecoder requestDecoder;
        private final List<A2aStream> a2aStreams = new ArrayList<>(2);

        private AguiDecoder decoder = decodeRequest;
        private AguiA2aMessageAssembler assembler;
        private AguiResponseMapper mapper;
        private A2aEventDecoder eventDecoder;
        private String rememberedContextId;
        private AguiA2aExtension offered;
        private AguiA2aExtension active;
        private String resumeTaskId;
        private String resumeInterruptId;
        private long eventTraceId;
        private int state;

        private Runnable replyEnd = this::noReplyEnd;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int recordProgress;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private AguiProxy(
            AguiA2aBindingConfig binding,
            AguiA2aRouteConfig route,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long proxyAffinity,
            AguiA2aThreadKey key,
            String agent,
            String threadId,
            String runId,
            String parentRunId)
        {
            this.binding = binding;
            this.route = route;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.proxyAffinity = proxyAffinity;
            this.key = key;
            this.agent = agent;
            this.threadId = threadId;
            this.runId = runId;
            this.parentRunId = parentRunId;
            this.retention = new AguiA2aRetention(retentionPool);
            this.requestDecoder = new AguiA2aRequestDecoder(initialId, threadId, runId, parentRunId, retention);
        }

        private void onAguiMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onAguiBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onAguiData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onAguiEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onAguiAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                flushRO.wrap(buffer, index, index + length);
                break;
            case WindowFW.TYPE_ID:
                onAguiWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onAguiReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                challengeRO.wrap(buffer, index, index + length);
                break;
            default:
                break;
            }
        }

        private void onAguiBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = AguiA2aState.openingInitial(state);

            doAguiWindow(traceId);
        }

        private void onAguiData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + data.reserved();

            if (payload != null)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeAgui(traceId, payload.buffer(), payload.offset(), payload.limit());
                }
                else
                {
                    appendDecodeSlot(traceId, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotBuffered(traceId);
                }
            }

            flushAguiWindow(traceId);
        }

        private void onAguiEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            initialSeq = end.sequence();
            state = AguiA2aState.closingInitial(state);

            if (decoder != decodeIgnore)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeAgui(traceId, emptyRO, 0, 0);
                }
                else
                {
                    decodeSlotBuffered(traceId);
                }
            }

            cleanupDecodeSlot();
        }

        private void onAguiAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = AguiA2aState.closeInitial(state);
            cancelTask(traceId);
        }

        private void onAguiWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();

            flushReply(window.traceId());

            a2aStreams.forEach(s -> s.flushReplyWindow(window.traceId()));
        }

        private void onAguiReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = AguiA2aState.closeReply(state);
            cancelTask(traceId);
        }

        private void cancelTask(
            long traceId)
        {
            String taskId = null;

            for (int index = 0; taskId == null && index < a2aStreams.size(); index++)
            {
                taskId = a2aStreams.get(index).cancelableTaskId();
            }

            cleanup(traceId);

            if (taskId != null)
            {
                final A2aCancelAbort cancel = new A2aCancelAbort(this, taskId);
                cancel.doA2aBegin(traceId);
            }
        }

        private void decodeAgui(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            AguiDecoder previous = null;
            int progress = offset;

            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, buffer, progress, limit);
            }

            if (progress < limit && decoder != decodeIgnore)
            {
                appendDecodeSlot(traceId, buffer, progress, limit - progress);
            }
        }

        private void decodeSlotBuffered(
            long traceId)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
            final int limit = decodeSlotOffset;

            decodeSlotOffset = 0;
            int progress = 0;
            AguiDecoder previous = null;

            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, slot, progress, limit);
            }

            if (progress < limit && decoder != decodeIgnore)
            {
                decodeSlotOffset = limit - progress;
                slot.putBytes(0, slot, progress, decodeSlotOffset);
            }
            else if (decodeSlot != NO_SLOT && decodeSlotOffset == 0)
            {
                cleanupDecodeSlot();
            }
        }

        private void appendDecodeSlot(
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
                cleanup(traceId);
            }
            else
            {
                decodePool.buffer(decodeSlot).putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;
            }
        }

        private void onRequestRejected(
            long traceId,
            AguiA2aRejection rejection)
        {
            doAguiReset(traceId, rejection.status(), rejection.code(), rejection.message());
            cleanup(traceId);
        }

        private void onRequestDecoded(
            long traceId)
        {
            final AguiA2aRetained retained = requestDecoder.retained();
            final int resumes = retained.resumeCount();

            boolean cancelled = false;
            boolean rejected = false;

            if (resumes > 0)
            {
                for (int index = 0; resumeTaskId == null && index < resumes; index++)
                {
                    final String interruptId = retained.resumeInterruptId(index);
                    final String taskId = owners.resolveHandle(key, AguiA2aHandleKind.INTERRUPT, interruptId);

                    if (taskId != null)
                    {
                        resumeTaskId = taskId;
                        resumeInterruptId = interruptId;
                        cancelled = retained.resumeStatus(index) == ResumeStatus.CANCELLED;
                    }
                }

                rejected = resumeTaskId == null;
            }
            else
            {
                rememberedContextId = owners.lookupContext(key);
            }

            if (rejected)
            {
                events.handleRejected(traceId, binding.id, agent, CODE_HANDLE_UNKNOWN);
                doAguiReset(traceId, STATUS_CONFLICT, CODE_HANDLE_UNKNOWN, MESSAGE_HANDLE_UNKNOWN);
                cleanup(traceId);
            }
            else
            {
                this.assembler = new AguiA2aMessageAssembler();
                openReply(cancelled);

                if (cancelled)
                {
                    a2aStreams.add(new A2aCancel(this));
                }
                else
                {
                    a2aStreams.add(new A2aCard(this));
                }

                a2aStreams.forEach(s -> s.doA2aBegin(traceId));
            }
        }

        private void onRequestSent()
        {
            if (!requestDecoder.priorTurn() || rememberedContextId != null)
            {
                retention.release();
            }
        }

        private void openReply(
            boolean cancelled)
        {
            this.mapper = new AguiResponseMapper(this, this, aguiTypeId, decodePool.slotCapacity());
            this.eventDecoder = new A2aEventDecoder(mapper, decodePool.slotCapacity(),
                cancelled ? A2aEventRoot.TASK : A2aEventRoot.EVENT);
        }

        private void onCardDecoded(
            long traceId,
            AguiA2aAgentCard card)
        {
            final String contextId = resumeTaskId == null ? rememberedContextId : null;
            assembler.wrap(requestDecoder.retained(), runId, contextId, resumeTaskId, resumeInterruptId,
                card.acceptsAdvisory())
                .accepting(card::acceptsInput);

            final int contentLength = assembler.contentLength();

            this.offered = card.extension();

            if (card.refused())
            {
                doAguiReset(traceId, STATUS_BAD_GATEWAY, null, null);
                cleanup(traceId);
            }
            else if (assembler.unacceptedMediaType() != null)
            {
                doAguiReset(traceId, STATUS_UNSUPPORTED_MEDIA, CODE_UNSUPPORTED_MEDIA, MESSAGE_UNSUPPORTED_MEDIA);
                cleanup(traceId);
            }
            else
            {
                final A2aMessage message = new A2aMessage(this, card.operation(), contentLength);
                a2aStreams.add(message);
                message.doA2aBegin(traceId);
            }
        }

        private void onExtensionEcho(
            OctetsFW extension)
        {
            final A2aBeginExFW beginEx = extension.sizeof() > 0
                ? a2aBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            if (offered != null && beginEx != null && beginEx.typeId() == a2aTypeId)
            {
                final String echoed = switch (beginEx.kind())
                {
                case A2aBeginExFW.KIND_MESSAGE_STREAM -> beginEx.messageStream().extensions().asString();
                case A2aBeginExFW.KIND_MESSAGE_SEND -> beginEx.messageSend().extensions().asString();
                default -> null;
                };

                if (offered.uri().equals(echoed))
                {
                    active = offered;
                    eventDecoder.activate(active);
                }
            }
        }

        String extensions()
        {
            return offered != null ? offered.uri() : null;
        }

        private void onA2aReset(
            long traceId,
            AguiReset reset)
        {
            runError(traceId, reset.status(), reset.code(), reset.message());
            cleanup(traceId);
        }

        @Override
        public byte[] snapshot(
            String contextId)
        {
            final AguiA2aCompaction compaction =
                AguiA2aCompaction.decide(requestDecoder.priorTurn(), rememberedContextId, contextId);

            byte[] json = null;

            if (compaction == AguiA2aCompaction.MESSAGES_SNAPSHOT_FIRST)
            {
                events.contextReset(eventTraceId, binding.id, agent, contextId);
                json = snapshotOf(requestDecoder.retained());
            }

            owners.rememberContext(key, contextId);
            retention.release();

            return json;
        }

        @Override
        public void tagDropped(
            String tag)
        {
            events.tagDropped(eventTraceId, binding.id, agent, mapper.taskId(), tag);
        }

        @Override
        public void tagMalformed(
            String tag,
            String detail)
        {
            events.runFailed(eventTraceId, binding.id, agent, mapper.taskId(), AguiResponseMapper.CODE_PROTOCOL,
                String.format("Malformed %s tag: %s", tag, detail));
        }

        @Override
        public InterruptHandle interruptHandle(
            String taskId,
            String contextId)
        {
            final String handle = owners.issueHandle(key, AguiA2aHandleKind.INTERRUPT, taskId);

            return new InterruptHandle(handle, context.clock().millis() + handleTtlMillis);
        }

        @Override
        public void message(
            int flags,
            AguiDataExFW dataEx,
            DirectBufferEx payload,
            int offset,
            int limit)
        {
            doAguiBegin(eventTraceId);

            final int exLength = dataEx != null ? dataEx.sizeof() : 0;
            final int length = limit - offset;
            final int record = RECORD_HEADER + exLength + length;

            if (acquireEncodeSlot() && encodeSlotOffset + record <= encodePool.slotCapacity())
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);

                slot.putInt(encodeSlotOffset, flags);
                slot.putInt(encodeSlotOffset + Integer.BYTES, exLength);
                slot.putInt(encodeSlotOffset + 2 * Integer.BYTES, length);

                if (exLength > 0)
                {
                    slot.putBytes(encodeSlotOffset + RECORD_HEADER, dataEx.buffer(), dataEx.offset(), exLength);
                }

                slot.putBytes(encodeSlotOffset + RECORD_HEADER + exLength, payload, offset, length);
                encodeSlotOffset += record;

                flushReply(eventTraceId);
            }
            else
            {
                cleanup(eventTraceId);
            }
        }

        @Override
        public void runFinished(
            AguiOutcomeType outcome,
            String detail)
        {
            doAguiBegin(eventTraceId);

            replyEnd = () -> doAguiEnd(eventTraceId, outcome, detail);

            flushReply(eventTraceId);
        }

        @Override
        public void runError(
            int status,
            String code,
            String message)
        {
            runError(eventTraceId, status, code, message);
        }

        private void runError(
            long traceId,
            int status,
            String code,
            String message)
        {
            if (AguiA2aState.replyOpened(state))
            {
                cleanupEncodeSlot();
                doAguiAbort(traceId, status, code, message);
            }
            else
            {
                doAguiReset(traceId, status, code, message);
            }
        }

        private void doAguiBegin(
            long traceId)
        {
            if (!AguiA2aState.replyOpened(state))
            {
                final AguiBeginExFW beginEx = aguiBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(aguiTypeId)
                    .threadId(threadId)
                    .runId(runId)
                    .parentRunId(parentRunId)
                    .build();

                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    proxyAffinity, beginEx);
                state = AguiA2aState.openReply(state);
            }
        }

        private void doAguiEnd(
            long traceId,
            AguiOutcomeType outcome,
            String detail)
        {
            replyEnd = this::noReplyEnd;

            if (!AguiA2aState.replyClosed(state))
            {
                final AguiEndExFW.Builder builder = aguiEndExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(aguiTypeId)
                    .outcome(o -> o.set(outcome));

                if (detail != null)
                {
                    final byte[] bytes = detail.getBytes();
                    builder.detailLength(bytes.length).detail(new UnsafeBufferEx(bytes), 0, bytes.length);
                }

                final AguiEndExFW endEx = builder.build();

                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    endEx);
                state = AguiA2aState.closeReply(state);
            }

            cleanupEncodeSlot();
            retention.release();
        }

        private void noReplyEnd()
        {
        }

        private boolean acquireEncodeSlot()
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            return encodeSlot != NO_SLOT;
        }

        private void flushReply(
            long traceId)
        {
            if (AguiA2aState.replyOpened(state) && !AguiA2aState.replyClosed(state))
            {
                if (encodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                    int progress = 0;
                    boolean blocked = false;

                    while (!blocked && progress < encodeSlotOffset)
                    {
                        final int flags = slot.getInt(progress);
                        final int exLength = slot.getInt(progress + Integer.BYTES);
                        final int length = slot.getInt(progress + 2 * Integer.BYTES);
                        final int remaining = length - recordProgress;
                        final int available = replyMax - (int) (replySeq - replyAck) - replyPad;
                        final int piece = Math.min(remaining, Math.max(available, 0));

                        if (available < 0 || remaining > 0 && piece == 0)
                        {
                            blocked = true;
                        }
                        else
                        {
                            final boolean first = recordProgress == 0;
                            final int frameFlags = (first ? flags & INIT : 0) | (piece == remaining ? flags & FIN : 0);
                            final int dataOffset = progress + RECORD_HEADER + exLength + recordProgress;

                            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                                authorization, frameFlags, 0L, piece + replyPad,
                                first && exLength > 0 ? slot : null, progress + RECORD_HEADER, exLength,
                                slot, dataOffset, piece);
                            replySeq += piece + replyPad;
                            recordProgress += piece;

                            if (recordProgress == length)
                            {
                                progress += RECORD_HEADER + exLength + length;
                                recordProgress = 0;
                            }
                        }
                    }

                    if (progress > 0)
                    {
                        encodeSlotOffset -= progress;
                        slot.putBytes(0, slot, progress, encodeSlotOffset);
                    }
                }

                if (encodeSlotOffset == 0)
                {
                    replyEnd.run();
                }
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                recordProgress = 0;
            }
        }

        private void doAguiAbort(
            long traceId,
            int status,
            String code,
            String message)
        {
            if (AguiA2aState.replyOpened(state) && !AguiA2aState.replyClosed(state))
            {
                final AguiAbortExFW abortEx = aguiAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(aguiTypeId)
                    .error(e -> e.status(status).code(code).message(message))
                    .build();

                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    abortEx);
                state = AguiA2aState.closeReply(state);
            }
        }

        private void doAguiReset(
            long traceId,
            int status,
            String code,
            String message)
        {
            if (!AguiA2aState.initialClosed(state))
            {
                final String text = message != null || status != STATUS_BAD_GATEWAY ? message : MESSAGE_BAD_GATEWAY;
                final AguiResetExFW resetEx = aguiResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(aguiTypeId)
                    .error(e -> e.status(status).code(code).message(text))
                    .build();

                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    authorization, resetEx);
                state = AguiA2aState.closeInitial(state);
            }
        }

        private void doAguiWindow(
            long traceId)
        {
            initialMax = decodePool.slotCapacity();
            initialAck = initialSeq;

            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                authorization, 0L, 0);
        }

        private void flushAguiWindow(
            long traceId)
        {
            final long newInitialAck = Math.max(initialAck, initialSeq - decodeSlotOffset);

            if (newInitialAck > initialAck && !AguiA2aState.initialClosing(state))
            {
                initialAck = newInitialAck;

                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    authorization, 0L, 0);
            }
        }

        private void cleanup(
            long traceId)
        {
            doAguiReset(traceId, STATUS_INTERNAL_ERROR, null, MESSAGE_INTERNAL_ERROR);
            doAguiAbort(traceId, STATUS_INTERNAL_ERROR, null, MESSAGE_INTERNAL_ERROR);

            a2aStreams.forEach(s -> s.cleanup(traceId));

            cleanupDecodeSlot();
            cleanupEncodeSlot();
            retention.release();
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

        private byte[] snapshotOf(
            AguiA2aRetained retained)
        {
            int length = SNAPSHOT_OPEN.length + SNAPSHOT_CLOSE.length;
            int count = 0;

            for (int index = retained.indexOf(Kind.MESSAGE); index != -1; index = retained.indexOf(Kind.MESSAGE, index + 1))
            {
                length += retained.length(index) + (count++ > 0 ? SNAPSHOT_SEPARATOR.length : 0);
            }

            final byte[] json = new byte[length];
            System.arraycopy(SNAPSHOT_OPEN, 0, json, 0, SNAPSHOT_OPEN.length);

            int progress = SNAPSHOT_OPEN.length;
            int messages = 0;

            for (int index = retained.indexOf(Kind.MESSAGE); index != -1; index = retained.indexOf(Kind.MESSAGE, index + 1))
            {
                if (messages++ > 0)
                {
                    System.arraycopy(SNAPSHOT_SEPARATOR, 0, json, progress, SNAPSHOT_SEPARATOR.length);
                    progress += SNAPSHOT_SEPARATOR.length;
                }

                retained.buffer().getBytes(retained.offset(index), json, progress, retained.length(index));
                progress += retained.length(index);
            }

            System.arraycopy(SNAPSHOT_CLOSE, 0, json, progress, SNAPSHOT_CLOSE.length);

            return json;
        }
    }

    private abstract class A2aStream
    {
        final AguiProxy proxy;
        final long originId;
        final long routedId;
        final long initialId;
        final long replyId;

        MessageConsumer receiver;
        int state;

        long initialSeq;
        long initialAck;
        int initialMax;
        int initialPad;

        long replySeq;
        long replyAck;
        int replyMax;

        private A2aStream(
            AguiProxy proxy)
        {
            this.proxy = proxy;
            this.originId = proxy.routedId;
            this.routedId = proxy.route.id;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        abstract Flyweight beginEx();

        abstract void onA2aReply(
            DataFW data);

        abstract void onA2aReplyEnd(
            EndFW end);

        abstract void onA2aReplyAbort(
            AbortFW abort);

        abstract void flushInitial(
            long traceId);

        abstract int replyBacklog();

        String cancelableTaskId()
        {
            return null;
        }

        void doA2aBegin(
            long traceId)
        {
            state = AguiA2aState.openingInitial(state);

            receiver = newStream(this::onA2aMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, proxy.authorization, proxy.proxyAffinity, beginEx());
        }

        void onA2aMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onA2aBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onA2aReply(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onA2aReplyEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onA2aReplyAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                flushRO.wrap(buffer, index, index + length);
                break;
            case WindowFW.TYPE_ID:
                onA2aWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onA2aReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        void onA2aBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            replySeq = begin.sequence();
            replyAck = begin.acknowledge();
            state = AguiA2aState.openReply(state);

            doA2aWindow(traceId);
        }

        void onA2aWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();

            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialPad = window.padding();


            flushInitial(traceId);
        }

        void onA2aReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();
            final A2aResetExFW resetEx = a2aResetExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());

            proxy.onA2aReset(traceId, AguiReset.of(resetEx != null && resetEx.typeId() == a2aTypeId ? resetEx : null));
            state = AguiA2aState.closeInitial(state);
        }

        void flushReplyWindow(
            long traceId)
        {
            if (AguiA2aState.replyOpened(state) && !AguiA2aState.replyClosed(state))
            {
                doA2aWindow(traceId);
            }
        }

        void doA2aWindow(
            long traceId)
        {
            final long replyAckMax = Math.max(replySeq - replyBacklog(), 0L);
            final int replyMaxMin = decodePool.slotCapacity();

            if (replyAckMax > replyAck || replyMaxMin > replyMax)
            {
                replyAck = replyAckMax;
                replyMax = Math.max(replyMaxMin, replyMax);

                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    proxy.authorization, 0L, 0);
            }
        }

        void doA2aData(
            long traceId,
            int flags,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final int reserved = length + initialPad;

            doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                proxy.authorization, flags, 0L, reserved, null, 0, 0, buffer, offset, length);
            initialSeq += reserved;
        }

        void doA2aEnd(
            long traceId)
        {
            if (!AguiA2aState.initialClosing(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    proxy.authorization, null);
                state = AguiA2aState.closingInitial(state);
            }
        }

        void cleanup(
            long traceId)
        {
            if (AguiA2aState.initialOpening(state) && !AguiA2aState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId,
                    proxy.authorization, null);
                state = AguiA2aState.closeInitial(state);
            }

            if (AguiA2aState.replyOpened(state) && !AguiA2aState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    proxy.authorization);
                state = AguiA2aState.closeReply(state);
            }
        }
    }

    private final class A2aCard extends A2aStream
    {
        private final AguiA2aAgentCardDecoder decoder = new AguiA2aAgentCardDecoder();

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private A2aCard(
            AguiProxy proxy)
        {
            super(proxy);
        }

        @Override
        Flyweight beginEx()
        {
            return a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .agentCard(c -> c.agent(proxy.agent))
                .build();
        }

        @Override
        void flushInitial(
            long traceId)
        {
        }

        @Override
        int replyBacklog()
        {
            return decodeSlotOffset;
        }

        @Override
        void onA2aReply(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + data.reserved();

            if (payload != null)
            {
                decodeCard(traceId, payload.buffer(), payload.offset(), payload.sizeof());
            }

            doA2aWindow(traceId);
        }

        @Override
        void onA2aReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            replySeq = end.sequence();
            state = AguiA2aState.closeReply(state);

            doA2aEnd(traceId);

            decodeCard(traceId, emptyRO, 0, 0);
            cleanupDecodeSlot();

            if (decoder.complete() && !decoder.failed())
            {
                proxy.onCardDecoded(traceId, decoder.card());
            }
            else
            {
                proxy.onA2aReset(traceId, new AguiReset(STATUS_BAD_GATEWAY, null, null));
            }
        }

        @Override
        void onA2aReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = AguiA2aState.closeReply(state);
            cleanupDecodeSlot();
            proxy.onA2aReset(traceId, new AguiReset(STATUS_BAD_GATEWAY, null, null));
        }

        @Override
        void cleanup(
            long traceId)
        {
            super.cleanup(traceId);
            cleanupDecodeSlot();
        }

        private void decodeCard(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final boolean last = AguiA2aState.replyClosed(state);

            if (decodeSlot == NO_SLOT)
            {
                final int progress = decoder.decode(buffer, offset, offset + length, last);

                if (progress < offset + length && !last)
                {
                    appendDecodeSlot(traceId, buffer, progress, offset + length - progress);
                }
            }
            else
            {
                appendDecodeSlot(traceId, buffer, offset, length);

                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                final int progress = decoder.decode(slot, 0, decodeSlotOffset, last);

                decodeSlotOffset -= progress;
                slot.putBytes(0, slot, progress, decodeSlotOffset);
            }
        }

        private void appendDecodeSlot(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(replyId);
            }

            if (decodeSlot == NO_SLOT || decodeSlotOffset + length > decodePool.slotCapacity())
            {
                proxy.cleanup(traceId);
            }
            else
            {
                decodePool.buffer(decodeSlot).putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;
            }
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
    }

    private abstract class A2aRun extends A2aStream
    {
        private A2aRun(
            AguiProxy proxy)
        {
            super(proxy);
        }

        @Override
        void onA2aBegin(
            BeginFW begin)
        {
            proxy.doAguiBegin(begin.traceId());
            proxy.onExtensionEcho(begin.extension());

            super.onA2aBegin(begin);
        }

        @Override
        int replyBacklog()
        {
            return proxy.encodeSlotOffset;
        }

        @Override
        String cancelableTaskId()
        {
            return proxy.mapper.terminal() ? null : proxy.mapper.taskId();
        }

        @Override
        void onA2aReply(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + data.reserved();
            proxy.eventTraceId = traceId;

            if (payload != null)
            {
                final OctetsFW extension = data.extension();
                final A2aDataExFW dataEx = extension.sizeof() > 0
                    ? a2aDataExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                    : null;

                proxy.eventDecoder.decode(data.flags(), dataEx, payload.buffer(), payload.offset(), payload.limit());
            }

            proxy.flushReply(traceId);

            doA2aWindow(traceId);
        }

        @Override
        void onA2aReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();
            final A2aEndExFW endEx = a2aEndExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final boolean error = endEx != null && endEx.typeId() == a2aTypeId && endEx.outcome().get() == A2aOutcome.ERROR;

            replySeq = end.sequence();
            state = AguiA2aState.closeReply(state);
            proxy.eventTraceId = traceId;

            doA2aEnd(traceId);

            proxy.eventDecoder.finish();
            proxy.mapper.streamEnded(error);
        }

        @Override
        void onA2aReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();
            final A2aAbortExFW abortEx = a2aAbortExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final String reason = abortEx != null && abortEx.typeId() == a2aTypeId && abortEx.reason() != null
                ? abortEx.reason().asString()
                : null;

            state = AguiA2aState.closeReply(state);
            proxy.eventTraceId = traceId;

            proxy.mapper.streamAborted(reason);
        }
    }

    private final class A2aMessage extends A2aRun
    {
        private final AguiA2aOperation operation;
        private final int bodyLength;
        private final BodyChunk chunk = new BodyChunk();

        private int bodyProgress;

        private A2aMessage(
            AguiProxy proxy,
            AguiA2aOperation operation,
            int bodyLength)
        {
            super(proxy);
            this.operation = operation;
            this.bodyLength = bodyLength;
        }

        @Override
        Flyweight beginEx()
        {
            final A2aBeginExFW.Builder builder = a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity()).typeId(a2aTypeId);

            return operation == AguiA2aOperation.MESSAGE_STREAM
                ? builder.messageStream(m -> m.agent(proxy.agent)
                    .extensions(proxy.extensions())
                    .contentLength(bodyLength)).build()
                : builder.messageSend(m -> m.agent(proxy.agent)
                    .extensions(proxy.extensions())
                    .contentLength(bodyLength)).build();
        }

        @Override
        void flushInitial(
            long traceId)
        {
            int available = initialMax - (int) (initialSeq - initialAck) - initialPad;

            while (!AguiA2aState.initialClosing(state) && !proxy.assembler.emitted() && available > 0)
            {
                chunk.reset();

                final int length = proxy.assembler.emit(chunk, Math.min(available, bodyBuffer.capacity()));
                final int flags = (bodyProgress == 0 ? INIT : 0) | (proxy.assembler.emitted() ? FIN : 0);

                doA2aData(traceId, flags, bodyBuffer, 0, length);

                bodyProgress += length;
                available = initialMax - (int) (initialSeq - initialAck) - initialPad;
            }

            if (proxy.assembler.emitted())
            {
                proxy.onRequestSent();
            }
        }
    }

    private final class A2aCancel extends A2aRun
    {
        private A2aCancel(
            AguiProxy proxy)
        {
            super(proxy);
        }

        @Override
        Flyweight beginEx()
        {
            return a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .tasksCancel(t -> t.agent(proxy.agent)
                    .extensions(proxy.extensions())
                    .taskId(proxy.resumeTaskId))
                .build();
        }

        @Override
        String cancelableTaskId()
        {
            return null;
        }

        @Override
        void flushInitial(
            long traceId)
        {
        }
    }

    private final class A2aCancelAbort extends A2aStream
    {
        private final String taskId;

        private A2aCancelAbort(
            AguiProxy proxy,
            String taskId)
        {
            super(proxy);
            this.taskId = taskId;
        }

        @Override
        Flyweight beginEx()
        {
            return a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .tasksCancel(t -> t.agent(proxy.agent).extensions(proxy.extensions()).taskId(taskId))
                .build();
        }

        @Override
        void flushInitial(
            long traceId)
        {
        }

        @Override
        int replyBacklog()
        {
            return 0;
        }

        @Override
        void onA2aReply(
            DataFW data)
        {
            replySeq = data.sequence() + data.reserved();

            doA2aWindow(data.traceId());
        }

        @Override
        void onA2aReplyEnd(
            EndFW end)
        {
            replySeq = end.sequence();
            state = AguiA2aState.closeReply(state);

            doA2aEnd(end.traceId());
        }

        @Override
        void onA2aReplyAbort(
            AbortFW abort)
        {
            state = AguiA2aState.closeReply(state);

            cleanup(abort.traceId());
        }

        @Override
        void onA2aReset(
            ResetFW reset)
        {
            cleanup(reset.traceId());
            state = AguiA2aState.closeInitial(state);
        }
    }

    private final class BodyChunk implements AguiA2aBodySink
    {
        private int position;

        private void reset()
        {
            position = 0;
        }

        @Override
        public void write(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            bodyBuffer.putBytes(position, buffer, offset, length);
            position += length;
        }
    }

    private final class AguiA2aRedirectHandler
    {
        private final MessageConsumer sender;
        private final long bindingId;
        private final String agent;
        private final long ownerAffinity;

        private AguiA2aRedirectHandler(
            MessageConsumer sender,
            long bindingId,
            String agent,
            long ownerAffinity)
        {
            this.sender = sender;
            this.bindingId = bindingId;
            this.agent = agent;
            this.ownerAffinity = ownerAffinity;
        }

        private void onAppMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case AbortFW.TYPE_ID:
            case DataFW.TYPE_ID:
            case FlushFW.TYPE_ID:
            case EndFW.TYPE_ID:
            case ResetFW.TYPE_ID:
            case WindowFW.TYPE_ID:
            case ChallengeFW.TYPE_ID:
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            doRedirect(sender, begin.originId(), begin.routedId(), begin.streamId(), begin.sequence(),
                begin.acknowledge(), traceId, begin.authorization(), ownerAffinity, begin.extension());

            events.runRedirected(traceId, bindingId, agent, ownerAffinity);
        }
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
        int flags,
        long budgetId,
        int reserved,
        DirectBufferEx extension,
        int extensionOffset,
        int extensionLength,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            .payload(buffer, offset, length);

        if (extension != null)
        {
            builder.extension(extension, extensionOffset, extensionLength);
        }

        final DataFW data = builder.build();

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
        Flyweight extension)
    {
        final EndFW.Builder builder = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final EndFW end = builder.build();

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
        Flyweight extension)
    {
        final AbortFW.Builder builder = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final AbortFW abort = builder.build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
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
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
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

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doRedirect(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final RedirectFW redirect = redirectRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        sender.accept(redirect.typeId(), redirect.buffer(), redirect.offset(), redirect.sizeof());
    }
}
