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
package io.aklivity.zilla.runtime.binding.a2a.http.internal.stream;

import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_TASKS_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpSkillConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.A2aHttpConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.config.A2aHttpBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.config.A2aHttpRouteConfig;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.skill.A2aHttpSkillVectors;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.skill.A2aHttpVectorCodec;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.a2a.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonStream;
import io.aklivity.zilla.runtime.common.json.JsonTransforms;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

public final class A2aHttpProxyFactory implements BindingHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String A2A_TYPE_NAME = "a2a";

    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String DEFAULT_CONTENT_TYPE = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ARTIFACT_ID = "result";
    private static final String TASK_ID_SEPARATOR = ".";

    private static final int JSON_RPC_INVALID_PARAMS = -32602;
    private static final int JSON_RPC_INTERNAL_ERROR = -32603;
    private static final int A2A_TASK_NOT_FOUND = -32001;
    private static final int A2A_PUSH_NOTIFICATION_NOT_SUPPORTED = -32003;
    private static final int A2A_UNSUPPORTED_OPERATION = -32004;
    private static final int A2A_EXTENDED_CARD_NOT_CONFIGURED = -32007;

    private static final int EVENT_RECORD_HEADER = Integer.BYTES;

    private static final byte[] JSON_SUFFIX = "}]}]}}".getBytes(UTF_8);
    private static final byte[] TEXT_SUFFIX = "\"}]}]}}".getBytes(UTF_8);
    private static final byte[] FAILED_SUFFIX = "\"}]}}}}".getBytes(UTF_8);
    private static final byte[] NULL_RESPONSE_VALUE = "null".getBytes(UTF_8);
    private static final byte[] BASE64_PAD = new byte[] {'='};

    private static final Map<String, Object> SINK_SEGMENTABLE = Map.of(JsonSink.DELIVERY, JsonSink.Delivery.SEGMENTABLE);
    private static final Map<String, Object> GENERATE_ESCAPED = Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true);

    private static final byte[] BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes(UTF_8);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final A2aBeginExFW a2aBeginExRO = new A2aBeginExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final A2aDataExFW.Builder a2aDataExRW = new A2aDataExFW.Builder();
    private final A2aResetExFW.Builder a2aResetExRW = new A2aResetExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongFunction<String> supplyTaskId;
    private final int httpTypeId;
    private final int a2aTypeId;
    private final EngineContext context;
    private final A2aHttpConfiguration config;
    private final Long2ObjectHashMap<A2aHttpBindingConfig> bindings;

    private final UnsafeBufferEx emptyRO = new UnsafeBufferEx(new byte[0]);
    private final UnsafeBufferEx bodyRO = new UnsafeBufferEx(new byte[0]);

    private final A2aMessageDecoder decodeRequest = this::decodeRequest;
    private final A2aMessageDecoder decodeIgnore = this::decodeIgnore;

    private final A2aResponseDecoder decodeResponseIgnore = this::decodeResponseIgnore;
    private final A2aResponseDecoder decodeJson = this::decodeJson;
    private final A2aResponseDecoder decodeText = this::decodeText;
    private final A2aResponseDecoder decodeFile = this::decodeFile;
    private final A2aResponseDecoder decodeEvents = this::decodeEvents;
    private final A2aResponseDecoder decodeEventsLastChunk = this::decodeEventsLastChunk;
    private final A2aResponseDecoder decodeEventsCompleted = this::decodeEventsCompleted;

    public A2aHttpProxyFactory(
        A2aHttpConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTaskId = config.taskIdSupplier();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.a2aTypeId = context.supplyTypeId(A2A_TYPE_NAME);
        this.bindings = new Long2ObjectHashMap<>();
    }

    public void attach(
        BindingConfig binding)
    {
        final A2aHttpBindingConfig attached = new A2aHttpBindingConfig(binding, context, config);
        attached.vectors.start();
        bindings.put(binding.id, attached);
    }

    public void detach(
        long bindingId)
    {
        final A2aHttpBindingConfig detached = bindings.remove(bindingId);
        if (detached != null)
        {
            detached.vectors.close();
        }
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
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final A2aHttpBindingConfig binding = bindings.get(routedId);
        final A2aBeginExFW beginEx = extension.get(a2aBeginExRO::tryWrap);

        if (binding != null && beginEx != null)
        {
            switch (beginEx.kind())
            {
            case KIND_AGENT_CARD:
                newStream = new A2aCardProxy(binding, sender, originId, routedId, initialId,
                    authorization, affinity)::onA2aMessage;
                break;
            case KIND_MESSAGE_SEND:
                newStream = new A2aMessageProxy(binding, false, beginEx.messageSend().contentLength(),
                    sender, originId, routedId, initialId, authorization, affinity)::onA2aMessage;
                break;
            case KIND_MESSAGE_STREAM:
                newStream = new A2aMessageProxy(binding, true, beginEx.messageStream().contentLength(),
                    sender, originId, routedId, initialId, authorization, affinity)::onA2aMessage;
                break;
            case KIND_TASKS_GET:
            case KIND_TASKS_LIST:
            case KIND_TASKS_CANCEL:
            case KIND_TASKS_SUBSCRIBE:
                newStream = new A2aRejectProxy(binding, A2A_UNSUPPORTED_OPERATION, "Unsupported operation",
                    sender, originId, routedId, initialId, authorization, affinity)::onA2aMessage;
                break;
            case KIND_PUSH_CONFIG_CREATE:
            case KIND_PUSH_CONFIG_GET:
            case KIND_PUSH_CONFIG_LIST:
            case KIND_PUSH_CONFIG_DELETE:
                newStream = new A2aRejectProxy(binding, A2A_PUSH_NOTIFICATION_NOT_SUPPORTED, "Push notification not supported",
                    sender, originId, routedId, initialId, authorization, affinity)::onA2aMessage;
                break;
            case KIND_EXTENDED_AGENT_CARD:
                newStream = new A2aRejectProxy(binding, A2A_EXTENDED_CARD_NOT_CONFIGURED, "Extended agent card not configured",
                    sender, originId, routedId, initialId, authorization, affinity)::onA2aMessage;
                break;
            default:
                break;
            }
        }

        return newStream;
    }

    @FunctionalInterface
    private interface A2aMessageDecoder
    {
        void decode(
            A2aMessageProxy proxy,
            long traceId,
            boolean last);
    }

    @FunctionalInterface
    private interface A2aResponseDecoder
    {
        JsonPipeline.Status decode(
            A2aMessageProxy proxy,
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last,
            boolean bodyReceived);
    }

    private abstract class A2aProxy
    {
        final A2aHttpBindingConfig binding;
        final MessageConsumer sender;
        final long originId;
        final long routedId;
        final long initialId;
        final long replyId;
        final long authorization;
        final long affinity;

        int state;

        int decodeSlot = NO_SLOT;
        int decodeSlotOffset;

        int encodeSlot = NO_SLOT;
        int encodeSlotOffset;
        DirectBufferEx replyExtension;
        int replyFlags = INIT;
        int replyFin = NONE;

        long initialSeq;
        long initialAck;
        int initialMax;

        long replySeq;
        long replyAck;
        int replyMax;
        int replyPad;

        private A2aProxy(
            A2aHttpBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            this.binding = binding;
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
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
                onA2aData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onA2aEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onA2aAbort(abortRO.wrap(buffer, index, index + length));
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

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            state = A2aHttpState.openingInitial(state);

            doA2aWindow(traceId);
            onA2aOpened(traceId);
        }

        abstract void onA2aOpened(
            long traceId);

        void onA2aData(
            DataFW data)
        {
            initialSeq = data.sequence() + data.reserved();
        }

        void onA2aEnd(
            EndFW end)
        {
            initialSeq = end.sequence();
            state = A2aHttpState.closeInitial(state);
            cleanupDecodeSlot();
        }

        void onA2aAbort(
            AbortFW abort)
        {
            state = A2aHttpState.closeInitial(A2aHttpState.closeReply(state));
            cleanupDecodeSlot();
            cleanupEncodeSlot();
        }

        void onA2aWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();

            flushReply(window.traceId());
        }

        void onA2aReset(
            ResetFW reset)
        {
            state = A2aHttpState.closeReply(state);
            cleanupEncodeSlot();
        }

        void doReply(
            long traceId,
            DirectBufferEx extension,
            byte[] reply)
        {
            beginReplyMessage(extension);
            doEncodeReply(reply, 0, reply.length);
            completeReplyMessage();
            completeReply(traceId);
        }

        void doEncodeReplyMessage(
            DirectBufferEx extension,
            String json)
        {
            final byte[] bytes = json.getBytes(UTF_8);
            beginReplyMessage(extension);
            doEncodeReply(bytes, 0, bytes.length);
            completeReplyMessage();
        }

        void beginReplyMessage(
            DirectBufferEx extension)
        {
            replyExtension = extension;
            replyFlags = INIT;
            replyFin = NONE;
        }

        void completeReplyMessage()
        {
            replyFin = FIN;
        }

        void completeReply(
            long traceId)
        {
            state = A2aHttpState.closingReply(state);
            doA2aBegin(traceId);
            flushReply(traceId);
        }

        boolean acquireEncodeSlot()
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            return encodeSlot != NO_SLOT;
        }

        void doEncodeReply(
            byte[] bytes,
            int offset,
            int length)
        {
            if (acquireEncodeSlot())
            {
                encodePool.buffer(encodeSlot).putBytes(encodeSlotOffset, bytes, offset, length);
                encodeSlotOffset += length;
            }
            else
            {
                cleanup(0L);
            }
        }

        int encodeFree()
        {
            return encodePool.slotCapacity() - encodeSlotOffset;
        }

        void ensureEncodeRoom(
            long traceId,
            int length)
        {
            if (encodeFree() < length)
            {
                flushReply(traceId);
            }
        }

        // Returns the resulting encodeSlotOffset (bytes still queued after this attempt) so callers reacting
        // to a SUSPENDED transform status can tell whether flushing actually freed any room before retrying.
        int flushReply(
            long traceId)
        {
            if (encodeSlot != NO_SLOT && A2aHttpState.replyOpened(state))
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                int maxPayload = replyMax - (int)(replySeq - replyAck) - replyPad;
                while (encodeSlotOffset > 0 && maxPayload > 0)
                {
                    final int length = Math.min(maxPayload, encodeSlotOffset);
                    final int reserved = length + replyPad;
                    final int flags = replyFlags | (length == encodeSlotOffset ? replyFin : NONE);
                    doA2aData(traceId, flags, reserved, slot, 0, length);
                    replyFlags = NONE;
                    final int remaining = encodeSlotOffset - length;
                    if (remaining > 0)
                    {
                        slot.putBytes(0, slot, length, remaining);
                    }
                    encodeSlotOffset = remaining;
                    maxPayload = replyMax - (int)(replySeq - replyAck) - replyPad;
                }

                if (A2aHttpState.replyClosing(state) && encodeSlotOffset == 0)
                {
                    doA2aEnd(traceId);
                }
            }

            return encodeSlotOffset;
        }

        void doA2aBegin(
            long traceId)
        {
            if (!A2aHttpState.replyOpened(state))
            {
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, emptyRO);
                state = A2aHttpState.openReply(state);
            }
        }

        void doA2aData(
            long traceId,
            int flags,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final DirectBufferEx extension = (flags & INIT) != 0 ? replyExtension : null;
            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, 0L, reserved, extension, buffer, offset, length);
            replySeq += reserved;
        }

        void doA2aEnd(
            long traceId)
        {
            if (!A2aHttpState.replyClosed(state))
            {
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = A2aHttpState.closeReply(state);
            }
            cleanupEncodeSlot();
        }

        void doA2aAbort(
            long traceId)
        {
            doA2aBegin(traceId);
            if (!A2aHttpState.replyClosed(state))
            {
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = A2aHttpState.closeReply(state);
            }
            cleanupEncodeSlot();
        }

        void doA2aWindow(
            long traceId)
        {
            initialMax = decodePool.slotCapacity();
            initialAck = initialSeq;
            state = A2aHttpState.openInitial(state);
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        // max stays pinned to the slot's full physical capacity; ack alone carries the backlog discount, so
        // ack + max lands exactly on the true remaining room without double-counting the backlog
        void flushA2aWindow(
            long traceId)
        {
            final long newInitialAck = Math.max(initialAck, initialSeq - decodeSlotOffset);

            if (newInitialAck > initialAck)
            {
                initialAck = newInitialAck;
                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0L, 0);
            }
        }

        void doA2aReset(
            long traceId,
            int code,
            String message)
        {
            if (!A2aHttpState.initialClosed(state))
            {
                final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(a2aTypeId)
                    .error(e -> e
                        .code(code)
                        .message(message))
                    .build();
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, resetEx);
                state = A2aHttpState.closeInitial(state);
            }
            cleanupDecodeSlot();
        }

        void doA2aBearerReset(
            long traceId,
            A2aBearerError error,
            String scopes)
        {
            if (!A2aHttpState.initialClosed(state))
            {
                final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(a2aTypeId)
                    .bearer(b -> b
                        .scopes(scopes)
                        .error(e -> e.set(error)))
                    .build();
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, resetEx);
                state = A2aHttpState.closeInitial(state);
            }
            cleanupDecodeSlot();
        }

        void cleanup(
            long traceId)
        {
            doA2aReset(traceId, JSON_RPC_INTERNAL_ERROR, "Internal error");
            doA2aAbort(traceId);
        }

        void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
        }

        void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }
    }

    private final class A2aCardProxy extends A2aProxy
    {
        private A2aCardProxy(
            A2aHttpBindingConfig binding,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, sender, originId, routedId, initialId, authorization, affinity);
        }

        @Override
        void onA2aOpened(
            long traceId)
        {
            doReply(traceId, null, binding.cardJson());
        }
    }

    private final class A2aRejectProxy extends A2aProxy
    {
        private final int code;
        private final String message;

        private A2aRejectProxy(
            A2aHttpBindingConfig binding,
            int code,
            String message,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, sender, originId, routedId, initialId, authorization, affinity);
            this.code = code;
            this.message = message;
        }

        @Override
        void onA2aOpened(
            long traceId)
        {
            doA2aReset(traceId, code, message);
        }
    }

    private final class A2aMessageProxy extends A2aProxy
        implements A2aHttpSkillVectors.Waiter, EmbeddingHandler.CompletionCallback
    {
        private final boolean streaming;
        private final int contentLength;

        private A2aMessageDecoder decoder;
        private A2aResponseDecoder responseDecoder;
        private float[][] phraseVectors;
        private HttpProxy delegate;

        private long traceId;
        private String utterance;
        private String requestTaskId;
        private String taskId;
        private String contextId;
        private A2aHttpSkillConfig skill;
        private A2aHttpRouteConfig route;
        private JsonObject data;
        private Map<String, String> args;
        private final Map<String, String> credentials = new HashMap<>();

        private JsonPipeline requestPipeline;
        private JsonGeneratorEx requestGenerator;
        private byte[] requestBody;

        private JsonPipeline responsePipeline;
        private JsonGeneratorEx responseGenerator;
        private JsonGeneratorEx escapeGenerator;
        private byte[] responseSuffix;
        private int responseConsumed;
        private int responseRemaining;
        private String artifactAppend;

        private A2aMessageProxy(
            A2aHttpBindingConfig binding,
            boolean streaming,
            int contentLength,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity)
        {
            super(binding, sender, originId, routedId, initialId, authorization, affinity);
            this.streaming = streaming;
            this.contentLength = contentLength;
            this.decoder = decodeRequest;
        }

        @Override
        void onA2aOpened(
            long traceId)
        {
            this.traceId = traceId;

            if (contentLength > decodePool.slotCapacity())
            {
                doA2aReset(traceId, JSON_RPC_INVALID_PARAMS, "Invalid params");
            }
        }

        @Override
        void onA2aData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + data.reserved();

            if (payload != null)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT || decodeSlotOffset + payload.sizeof() > decodePool.slotCapacity())
                {
                    doA2aReset(traceId, JSON_RPC_INVALID_PARAMS, "Invalid params");
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();
                    decoder.decode(this, traceId, false);
                }
            }
        }

        @Override
        void onA2aEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            initialSeq = end.sequence();
            state = A2aHttpState.closeInitial(state);
            decoder.decode(this, traceId, true);
        }

        @Override
        void onA2aAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            super.onA2aAbort(abort);
            decoder = decodeIgnore;
            if (delegate != null)
            {
                delegate.doHttpAbort(traceId);
            }
            cleanupResponse();
        }

        @Override
        void onA2aReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            super.onA2aReset(reset);
            if (delegate != null)
            {
                delegate.doHttpReset(traceId);
            }
            cleanupResponse();
        }

        @Override
        void onA2aWindow(
            WindowFW window)
        {
            super.onA2aWindow(window);

            if (responseActive())
            {
                delegate.resumeResponse(window.traceId());
            }
        }

        boolean requestComplete(
            boolean last)
        {
            return contentLength >= 0 ? decodeSlotOffset >= contentLength : last;
        }

        void onRequest(
            long traceId)
        {
            this.traceId = traceId;
            final String text = decodePool.buffer(decodeSlot).getStringWithoutLengthUtf8(0, decodeSlotOffset);
            cleanupDecodeSlot();

            final JsonObject message = parseMessage(text);

            if (message == null)
            {
                doA2aReset(traceId, JSON_RPC_INVALID_PARAMS, "Invalid params");
            }
            else
            {
                this.requestTaskId = message.getString("taskId", null);
                this.contextId = message.getString("contextId", null);
                readParts(message.get("parts"));
                onRequestParsed(traceId);
            }
        }

        private JsonObject parseMessage(
            String text)
        {
            JsonObject message = null;

            try (JsonReader reader = Json.createReader(new StringReader(text)))
            {
                final JsonObject request = reader.readObject();
                final JsonValue value = request.get("message");
                message = value != null && value.getValueType() == JsonValue.ValueType.OBJECT ? value.asJsonObject() : null;
            }
            catch (JsonException | ClassCastException ex)
            {
                message = null;
            }

            return message;
        }

        private void readParts(
            JsonValue parts)
        {
            final StringBuilder text = new StringBuilder();
            JsonObject data = null;

            if (parts != null && parts.getValueType() == JsonValue.ValueType.ARRAY)
            {
                for (JsonValue part : parts.asJsonArray())
                {
                    if (part.getValueType() == JsonValue.ValueType.OBJECT)
                    {
                        final JsonObject object = part.asJsonObject();
                        final JsonValue partText = object.get("text");
                        final JsonValue partData = object.get("data");
                        if (partText instanceof JsonString string)
                        {
                            text.append(text.length() > 0 ? "\n" : "").append(string.getString());
                        }
                        else if (data == null && partData != null && partData.getValueType() == JsonValue.ValueType.OBJECT)
                        {
                            data = partData.asJsonObject();
                        }
                    }
                }
            }

            this.utterance = text.toString();
            this.data = data;
        }

        private void onRequestParsed(
            long traceId)
        {
            if (requestTaskId != null)
            {
                final String skillId = skillIdOf(requestTaskId);
                final A2aHttpSkillConfig followup = skillId != null ? binding.skill(skillId) : null;
                if (followup != null)
                {
                    this.taskId = requestTaskId;
                    onSkill(traceId, followup);
                }
                else
                {
                    doA2aReset(traceId, A2A_TASK_NOT_FOUND, "Task not found");
                }
            }
            else if (utterance.isBlank())
            {
                onNoMatch(traceId);
            }
            else
            {
                binding.vectors.whenReady(this);
            }
        }

        @Override
        public void ready(
            float[][] vectors)
        {
            if (!A2aHttpState.replyClosed(state))
            {
                this.phraseVectors = vectors;
                binding.embedding.embed(traceId, binding.id, initialId, List.of(utterance), this);
            }
        }

        @Override
        public void completed(
            long contextId,
            float[][] results)
        {
            if (!A2aHttpState.replyClosed(state))
            {
                if (A2aHttpVectorCodec.valid(results, 1))
                {
                    final String skillId = binding.match(phraseVectors, results[0]);
                    final A2aHttpSkillConfig matched = skillId != null ? binding.skill(skillId) : null;
                    if (matched != null)
                    {
                        this.taskId = encodeTaskId(matched.id, affinity);
                        onSkill(traceId, matched);
                    }
                    else
                    {
                        onNoMatch(traceId);
                    }
                }
                else
                {
                    doA2aReset(traceId, JSON_RPC_INTERNAL_ERROR, "Internal error");
                }
            }
        }

        @Override
        public void failed(
            Throwable ex)
        {
            if (!A2aHttpState.replyClosed(state))
            {
                doA2aReset(traceId, JSON_RPC_INTERNAL_ERROR, "Internal error");
            }
        }

        @Override
        public void failed(
            long contextId,
            Throwable ex)
        {
            failed(ex);
        }

        private void onNoMatch(
            long traceId)
        {
            final String taskId = supplyTaskId.apply(affinity);
            final String contextId = this.contextId != null ? this.contextId : supplyTaskId.apply(affinity);
            final String parts = "[{\"text\":" + quote("No matching skill. Available skills: " + binding.skillsSummary()) + "}]";
            doReplyTask(traceId, A2aTaskState.REJECTED, "TASK_STATE_REJECTED", taskId, contextId, parts);
        }

        private void onSkill(
            long traceId,
            A2aHttpSkillConfig skill)
        {
            this.skill = skill;
            this.route = binding.resolveRoute(skill.id);

            if (contextId == null)
            {
                contextId = supplyTaskId.apply(affinity);
            }

            if (route == null)
            {
                doA2aReset(traceId, JSON_RPC_INTERNAL_ERROR, "Internal error");
            }
            else if (!binding.authorized(skill.id, authorization))
            {
                doA2aBearerReset(traceId, A2aBearerError.INSUFFICIENT_SCOPE, binding.scopes(skill.id));
            }
            else
            {
                final List<String> unsatisfied = binding.unsatisfiedAccessors(route);
                if (unsatisfied.isEmpty())
                {
                    onArguments(traceId);
                }
                else
                {
                    doA2aReset(traceId, JSON_RPC_INTERNAL_ERROR, "Internal error");
                }
            }
        }

        private void onArguments(
            long traceId)
        {
            final JsonObject arguments = data != null ? data : Json.createObjectBuilder().build();
            final List<String> missing = new ArrayList<>();
            final List<JsonSchemaDiagnostic> invalid = new ArrayList<>();

            final JsonSchema schema = binding.jsonSchema(skill.input);
            if (schema != null)
            {
                schema.validate(Json.createParser(new StringReader(arguments.toString())), diagnostic ->
                {
                    if ("required".equals(diagnostic.keyword()))
                    {
                        missing.addAll(requiredNames(diagnostic));
                    }
                    else
                    {
                        invalid.add(diagnostic);
                    }
                });
            }

            this.args = new HashMap<>();
            flatten(arguments, "", args);
            for (String accessor : route.argAccessors)
            {
                if (!args.containsKey(accessor) && !missing.contains(accessor))
                {
                    missing.add(accessor);
                }
            }

            if (missing.isEmpty() && invalid.isEmpty())
            {
                onDispatch(traceId, arguments);
            }
            else
            {
                onInputRequired(traceId, missing, invalid);
            }
        }

        private void onInputRequired(
            long traceId,
            List<String> missing,
            List<JsonSchemaDiagnostic> invalid)
        {
            final StringBuilder fields = new StringBuilder();
            missing.forEach(name -> fields.append(fields.length() > 0 ? ", " : "").append(name));
            invalid.forEach(diagnostic -> fields.append(fields.length() > 0 ? ", " : "").append(pointerName(diagnostic)));

            final StringBuilder detail = new StringBuilder("{\"schema\":");
            final String schemaText = skill.input != null ? binding.schemaText(skill.input) : null;
            detail.append(schemaText != null && !schemaText.isBlank() ? compact(schemaText) : "{}");
            detail.append(",\"missing\":[");
            for (int i = 0; i < missing.size(); i++)
            {
                detail.append(i > 0 ? "," : "").append(quote(missing.get(i)));
            }
            detail.append("],\"invalid\":[");
            for (int i = 0; i < invalid.size(); i++)
            {
                final JsonSchemaDiagnostic diagnostic = invalid.get(i);
                detail.append(i > 0 ? "," : "")
                    .append("{\"path\":").append(quote(diagnostic.pointer()))
                    .append(",\"message\":").append(quote(diagnostic.message())).append('}');
            }
            detail.append("]}");

            final String parts = "[{\"text\":" + quote("Input required for skill " + skill.id + ": " + fields) + "},{\"data\":" +
                detail + "}]";
            doReplyTask(traceId, A2aTaskState.INPUT_REQUIRED, "TASK_STATE_INPUT_REQUIRED", taskId, contextId, parts);
        }

        private void onDispatch(
            long traceId,
            JsonObject arguments)
        {
            this.delegate = new HttpProxy(this, route.id);
            this.decoder = decodeIgnore;

            final String path = route.resolvePath(args);
            final Map<String, String> headers = route.resolveHeaders(args);
            credentials.clear();
            binding.resolveCredentials(authorization, credentials);

            final JsonSchema bodySchema = route.with.body != null ? binding.jsonSchema(route.with.body) : null;
            if (bodySchema != null)
            {
                requestGenerator = JsonEx.createGenerator();
                final JsonStream stream = JsonEx.stream(JsonEx.createParser())
                    .transform(JsonTransforms.projector(bodySchema));
                requestPipeline = stream.into(JsonEx.createSink(requestGenerator, SINK_SEGMENTABLE));
                requestPipeline.reset();
                requestBody = arguments.toString().getBytes(UTF_8);
                bodyRO.wrap(requestBody);
            }

            delegate.doHttpBegin(traceId, headers, credentials, path, requestPipeline != null ? DEFAULT_CONTENT_TYPE : null);

            if (requestPipeline != null)
            {
                pumpRequest(traceId);
            }
            else
            {
                delegate.requestComplete();
                delegate.flushRequest(traceId);
            }
        }

        boolean requestPumpable()
        {
            return requestPipeline != null;
        }

        void pumpRequest(
            long traceId)
        {
            boolean progress = true;
            while (progress && requestPipeline != null)
            {
                final JsonPipeline.Status status =
                    delegate.requestStep(requestGenerator, requestPipeline, bodyRO, 0, requestBody.length, true);

                switch (status)
                {
                case SUSPENDED:
                    final int beforeFlush = delegate.encodeSlotOffset;
                    if (delegate.flushRequest(traceId) >= beforeFlush)
                    {
                        progress = false;
                    }
                    break;
                case COMPLETED:
                    requestPipeline = null;
                    requestGenerator = null;
                    delegate.requestComplete();
                    delegate.flushRequest(traceId);
                    progress = false;
                    break;
                case REJECTED:
                    requestPipeline = null;
                    requestGenerator = null;
                    delegate.doHttpAbort(traceId);
                    doA2aReset(traceId, JSON_RPC_INVALID_PARAMS, "Invalid params");
                    progress = false;
                    break;
                default:
                    progress = false;
                    break;
                }
            }
        }

        void onHttpBegin(
            long traceId,
            String status,
            String contentType)
        {
            final boolean ok = status != null && status.startsWith("2");
            final String mediaType = contentType.indexOf(';') >= 0
                ? contentType.substring(0, contentType.indexOf(';')).trim()
                : contentType.trim();

            if (!ok && binding.credentialed() && ("401".equals(status) || "403".equals(status)))
            {
                delegate.doHttpReset(traceId);
                doA2aBearerReset(traceId, "401".equals(status) ? A2aBearerError.INVALID_TOKEN : A2aBearerError.INSUFFICIENT_SCOPE,
                    null);
            }
            else if (ok && CONTENT_TYPE_EVENT_STREAM.equals(mediaType) && !streaming)
            {
                delegate.doHttpReset(traceId);
                doA2aReset(traceId, A2A_UNSUPPORTED_OPERATION, "Unsupported operation");
            }
            else if (ok && CONTENT_TYPE_EVENT_STREAM.equals(mediaType))
            {
                responseDecoder = decodeEvents;
                artifactAppend = "false";
                doReplyTaskStart(traceId, A2aTaskState.WORKING, "TASK_STATE_WORKING");
            }
            else if (ok)
            {
                final String prefix = completedPrefix();
                final DirectBufferEx extension = taskDataEx(A2aTaskState.COMPLETED);
                if (isJson(mediaType))
                {
                    final JsonSchema outputSchema = skill.output != null ? binding.jsonSchema(skill.output) : null;
                    responseGenerator = JsonEx.createGenerator();
                    JsonStream stream = JsonEx.stream(JsonEx.createParser());
                    if (outputSchema != null)
                    {
                        stream = stream.transform(outputSchema.validator()).transform(JsonTransforms.projector(outputSchema));
                    }
                    responsePipeline = stream.into(JsonEx.createSink(responseGenerator, SINK_SEGMENTABLE));
                    responsePipeline.reset();
                    responseDecoder = decodeJson;
                    responseSuffix = JSON_SUFFIX;
                    beginResponseMessage(traceId, extension, prefix + "\"data\":");
                }
                else if (mediaType.startsWith("text/"))
                {
                    escapeGenerator = JsonEx.createGenerator(GENERATE_ESCAPED);
                    responseDecoder = decodeText;
                    responseSuffix = TEXT_SUFFIX;
                    beginResponseMessage(traceId, extension, prefix + "\"text\":\"");
                }
                else
                {
                    responseDecoder = decodeFile;
                    responseSuffix = ("\",\"mediaType\":" + quote(mediaType) + "}]}]}}").getBytes(UTF_8);
                    beginResponseMessage(traceId, extension, prefix + "\"raw\":\"");
                }
            }
            else
            {
                escapeGenerator = JsonEx.createGenerator(GENERATE_ESCAPED);
                responseDecoder = decodeText;
                responseSuffix = FAILED_SUFFIX;
                final String prefix = failedPrefix();
                beginResponseMessage(traceId, taskDataEx(A2aTaskState.FAILED), prefix);
            }

            if (responseActive())
            {
                delegate.flushHttpWindow(traceId);
            }
        }

        private String completedPrefix()
        {
            return envelope(taskId, contextId, "TASK_STATE_COMPLETED") + "},\"artifacts\":[{\"artifactId\":\"" +
                ARTIFACT_ID + "\",\"parts\":[{";
        }

        private String failedPrefix()
        {
            return envelope(taskId, contextId, "TASK_STATE_FAILED") + ",\"message\":{\"messageId\":" +
                quote(taskId + "-status") + ",\"role\":\"ROLE_AGENT\",\"taskId\":" + quote(taskId) +
                ",\"contextId\":" + quote(contextId) + ",\"parts\":[{\"text\":\"";
        }

        private void beginResponseMessage(
            long traceId,
            DirectBufferEx extension,
            String prefix)
        {
            beginReplyMessage(extension);
            final byte[] bytes = prefix.getBytes(UTF_8);
            doEncodeReply(bytes, 0, bytes.length);
            doA2aBegin(traceId);
        }

        JsonPipeline.Status responseStep(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last,
            boolean bodyReceived)
        {
            return responseDecoder.decode(this, traceId, buffer, offset, length, last, bodyReceived);
        }

        int responseRemaining()
        {
            return responseRemaining;
        }

        boolean responseActive()
        {
            return responseDecoder != null && responseDecoder != decodeResponseIgnore;
        }

        void completeResponse(
            long traceId)
        {
            responseDecoder = decodeResponseIgnore;
            if (responseSuffix != null)
            {
                ensureEncodeRoom(traceId, responseSuffix.length);
                doEncodeReply(responseSuffix, 0, responseSuffix.length);
            }
            completeReplyMessage();
            completeReply(traceId);
        }

        void responseReject(
            long traceId)
        {
            cleanupResponse();
            cleanupEncodeSlot();
            doA2aAbort(traceId);
        }

        void onHttpAbort(
            long traceId)
        {
            cleanupResponse();
            cleanupEncodeSlot();
            doA2aAbort(traceId);
        }

        void cleanupResponse()
        {
            responsePipeline = null;
            responseGenerator = null;
            escapeGenerator = null;
            responseDecoder = decodeResponseIgnore;
            responseConsumed = 0;
            responseRemaining = 0;
        }

        private void doReplyTask(
            long traceId,
            A2aTaskState taskState,
            String stateName,
            String taskId,
            String contextId,
            String parts)
        {
            final String message = parts != null
                ? ",\"message\":{\"messageId\":" + quote(taskId + "-status") + ",\"role\":\"ROLE_AGENT\"" +
                    ",\"taskId\":" + quote(taskId) + ",\"contextId\":" + quote(contextId) + ",\"parts\":" + parts + "}"
                : "";
            final String reply = envelope(taskId, contextId, stateName) + message + "}}}";
            this.taskId = taskId;
            this.contextId = contextId;
            doReply(traceId, taskDataEx(taskState), reply.getBytes(UTF_8));
        }

        private void doReplyTaskStart(
            long traceId,
            A2aTaskState taskState,
            String stateName)
        {
            final String reply = envelope(taskId, contextId, stateName) + "}}}";
            beginReplyMessage(taskDataEx(taskState));
            final byte[] bytes = reply.getBytes(UTF_8);
            doEncodeReply(bytes, 0, bytes.length);
            completeReplyMessage();
            doA2aBegin(traceId);
            flushReply(traceId);
        }

        private String envelope(
            String taskId,
            String contextId,
            String stateName)
        {
            return "{\"task\":{\"id\":" + quote(taskId) + ",\"contextId\":" + quote(contextId) +
                ",\"status\":{\"state\":\"" + stateName + "\"";
        }

        private DirectBufferEx taskDataEx(
            A2aTaskState taskState)
        {
            return extensionOf(a2aDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .task(t -> t
                    .taskId(taskId)
                    .contextId(contextId)
                    .state(state -> state.set(taskState)))
                .build());
        }

        private DirectBufferEx statusDataEx(
            A2aTaskState taskState)
        {
            return extensionOf(a2aDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .statusUpdate(t -> t
                    .taskId(taskId)
                    .contextId(contextId)
                    .state(state -> state.set(taskState)))
                .build());
        }

        private DirectBufferEx artifactDataEx()
        {
            return extensionOf(a2aDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .artifactUpdate(t -> t
                    .taskId(taskId)
                    .contextId(contextId)
                    .artifactId(ARTIFACT_ID))
                .build());
        }

        @Override
        void cleanup(
            long traceId)
        {
            super.cleanup(traceId);
            if (delegate != null)
            {
                delegate.doHttpAbort(traceId);
                delegate.doHttpReset(traceId);
            }
            cleanupDecodeSlot();
            cleanupEncodeSlot();
        }
    }

    private final class HttpProxy
    {
        private final A2aMessageProxy a2a;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer receiver;
        private int state;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int requestFlags = INIT;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private String responseStatus;
        private String responseContentType = DEFAULT_CONTENT_TYPE;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private HttpProxy(
            A2aMessageProxy a2a,
            long resolvedId)
        {
            this.a2a = a2a;
            this.originId = a2a.routedId;
            this.routedId = resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onHttpBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onHttpData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onHttpEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onHttpAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                flushRO.wrap(buffer, index, index + length);
                break;
            case WindowFW.TYPE_ID:
                onHttpWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onHttpReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            replySeq = begin.sequence();
            replyAck = begin.acknowledge();
            state = A2aHttpState.openReply(state);

            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);
            if (httpBeginEx != null)
            {
                final HttpHeaderFW status =
                    httpBeginEx.headers().matchFirst(h -> HEADER_STATUS.equals(h.name().asString()));
                responseStatus = status != null ? status.value().asString() : null;
                final HttpHeaderFW contentType =
                    httpBeginEx.headers().matchFirst(h -> HEADER_CONTENT_TYPE.equals(h.name().asString()));
                responseContentType = contentType != null ? contentType.value().asString() : DEFAULT_CONTENT_TYPE;
            }

            a2a.onHttpBegin(traceId, responseStatus, responseContentType);
        }

        private void onHttpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + reserved;

            if (payload != null && a2a.responseDecoder == decodeEvents)
            {
                onHttpEvent(traceId, payload);
                pumpResponse(traceId);
            }
            else if (payload != null && a2a.responseActive())
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(replyId);
                }

                if (decodeSlot == NO_SLOT || decodeSlotOffset + payload.sizeof() > decodePool.slotCapacity())
                {
                    a2a.cleanup(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();

                    pumpResponse(traceId);
                }
            }
        }

        private void onHttpEvent(
            long traceId,
            OctetsFW payload)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(replyId);
            }

            if (decodeSlot == NO_SLOT || decodeSlotOffset + EVENT_RECORD_HEADER + payload.sizeof() > decodePool.slotCapacity())
            {
                a2a.cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putInt(decodeSlotOffset, payload.sizeof());
                slot.putBytes(decodeSlotOffset + EVENT_RECORD_HEADER, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += EVENT_RECORD_HEADER + payload.sizeof();
            }
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            replySeq = end.sequence();
            state = A2aHttpState.closeReply(state);

            if (a2a.responseActive())
            {
                pumpResponse(traceId);
            }
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            state = A2aHttpState.closeReply(state);
            a2a.onHttpAbort(traceId);
            cleanupDecodeSlot();
        }

        private void onHttpWindow(
            WindowFW window)
        {
            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialPad = window.padding();

            final long traceId = window.traceId();
            flushRequest(traceId);
            if (a2a.requestPumpable())
            {
                a2a.pumpRequest(traceId);
            }
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            state = A2aHttpState.closeInitial(state);
            cleanupEncodeSlot();
            a2a.onHttpAbort(traceId);
        }

        private void doHttpBegin(
            long traceId,
            Map<String, String> headers,
            Map<String, String> credentials,
            String path,
            String contentType)
        {
            final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headers(hs ->
                {
                    for (Map.Entry<String, String> entry : headers.entrySet())
                    {
                        final String header = entry.getKey();
                        final String value = HEADER_PATH.equals(header) ? path : entry.getValue();
                        hs.item(h -> h.name(header).value(value));
                    }
                    if (contentType != null)
                    {
                        hs.item(h -> h.name(HEADER_CONTENT_TYPE).value(contentType));
                    }
                    for (Map.Entry<String, String> entry : credentials.entrySet())
                    {
                        hs.item(h -> h.name(entry.getKey()).value(entry.getValue()));
                    }
                })
                .build();

            state = A2aHttpState.openingInitial(state);

            receiver = newStream(this::onHttpMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, a2a.authorization, a2a.affinity, httpBeginEx);
        }

        private void doHttpEnd(
            long traceId)
        {
            if (!A2aHttpState.initialClosed(state))
            {
                doEnd(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, a2a.authorization);
                state = A2aHttpState.closeInitial(state);
            }
            cleanupEncodeSlot();
        }

        private void doHttpAbort(
            long traceId)
        {
            if (A2aHttpState.initialOpening(state) && !A2aHttpState.initialClosed(state))
            {
                doAbort(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, a2a.authorization);
                state = A2aHttpState.closeInitial(state);
            }
            cleanupEncodeSlot();
        }

        private void doHttpReset(
            long traceId)
        {
            if (A2aHttpState.initialOpening(state) && !A2aHttpState.replyClosed(state))
            {
                doReset(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, a2a.authorization);
                state = A2aHttpState.closeReply(state);
            }
            cleanupDecodeSlot();
        }

        // ack alone carries the backlog discount: decodeSlotOffset is in raw upstream response bytes, the same
        // units as replySeq/replyAck, so ack + max lands exactly on the true remaining room; for event records
        // the framing header is granted as frame padding so the units still agree
        private void flushHttpWindow(
            long traceId)
        {
            if (A2aHttpState.replyOpening(state))
            {
                final int padding = a2a.responseDecoder == decodeEvents ? EVENT_RECORD_HEADER : 0;
                final long newReplyAck = Math.max(replyAck, replySeq - decodeSlotOffset);
                final int newReplyMax = Math.max(replyMax, decodePool.slotCapacity());

                if (newReplyAck > replyAck || newReplyMax > replyMax || !A2aHttpState.replyOpened(state))
                {
                    replyAck = newReplyAck;
                    replyMax = newReplyMax;
                    doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, a2a.authorization, 0L, padding);
                }
            }
        }

        private boolean acquireEncodeSlot()
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(initialId);
            }

            return encodeSlot != NO_SLOT;
        }

        private JsonPipeline.Status requestStep(
            JsonGeneratorEx generator,
            JsonPipeline pipeline,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            JsonPipeline.Status status;
            if (acquireEncodeSlot())
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                generator.wrap(slot, encodeSlotOffset, encodePool.slotCapacity());
                status = pipeline.transform(buffer, offset, offset + length, last);
                encodeSlotOffset += generator.length();
            }
            else
            {
                a2a.cleanup(0L);
                status = JsonPipeline.Status.REJECTED;
            }
            return status;
        }

        private void requestComplete()
        {
            state = A2aHttpState.closingInitial(state);
        }

        private int flushRequest(
            long traceId)
        {
            if (receiver != null && !A2aHttpState.initialClosed(state))
            {
                if (encodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                    int maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                    while (encodeSlotOffset > 0 && maxPayload > 0)
                    {
                        final int length = Math.min(maxPayload, encodeSlotOffset);
                        final int reserved = length + initialPad;
                        final boolean fin = A2aHttpState.initialClosing(state) && length == encodeSlotOffset;
                        final int flags = requestFlags | (fin ? FIN : NONE);
                        doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, a2a.authorization, flags, 0L, reserved, null, slot, 0, length);
                        initialSeq += reserved;
                        requestFlags = NONE;
                        final int remaining = encodeSlotOffset - length;
                        if (remaining > 0)
                        {
                            slot.putBytes(0, slot, length, remaining);
                        }
                        encodeSlotOffset = remaining;
                        maxPayload = initialMax - (int)(initialSeq - initialAck) - initialPad;
                    }
                }

                if (A2aHttpState.initialClosing(state) && encodeSlotOffset == 0)
                {
                    cleanupEncodeSlot();
                    doHttpEnd(traceId);
                }
            }

            return encodeSlotOffset;
        }

        private void pumpResponse(
            long traceId)
        {
            boolean progress = true;
            while (progress && a2a.responseActive())
            {
                final MutableDirectBufferEx slot = decodeSlot != NO_SLOT ? decodePool.buffer(decodeSlot) : emptyRO;
                final JsonPipeline.Status status = a2a.responseStep(traceId, slot, 0, decodeSlotOffset,
                    A2aHttpState.replyClosed(state), decodeSlot != NO_SLOT);
                if (status != JsonPipeline.Status.SUSPENDED)
                {
                    final int consumed = decodeSlotOffset - a2a.responseRemaining();
                    if (consumed > 0 && consumed < decodeSlotOffset)
                    {
                        slot.putBytes(0, slot, consumed, decodeSlotOffset - consumed);
                    }
                    decodeSlotOffset -= consumed;
                }
                switch (status)
                {
                case SUSPENDED:
                    final int beforeFlush = a2a.encodeSlotOffset;
                    if (a2a.flushReply(traceId) >= beforeFlush)
                    {
                        progress = false;
                    }
                    break;
                case ADVANCED:
                    a2a.flushReply(traceId);
                    break;
                case COMPLETED:
                    a2a.completeResponse(traceId);
                    progress = false;
                    break;
                case REJECTED:
                    a2a.responseReject(traceId);
                    progress = false;
                    break;
                default:
                    progress = false;
                    break;
                }
            }

            a2a.flushReply(traceId);

            if (!a2a.responseActive())
            {
                cleanupDecodeSlot();
            }
            else
            {
                flushHttpWindow(traceId);
            }
        }

        private void resumeResponse(
            long traceId)
        {
            if (a2a.responseActive())
            {
                pumpResponse(traceId);
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

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }
    }

    private void decodeRequest(
        A2aMessageProxy proxy,
        long traceId,
        boolean last)
    {
        if (proxy.decodeSlot != NO_SLOT && proxy.requestComplete(last))
        {
            proxy.decoder = decodeIgnore;
            proxy.onRequest(traceId);
        }
        else if (last)
        {
            proxy.decoder = decodeIgnore;
            proxy.doA2aReset(traceId, JSON_RPC_INVALID_PARAMS, "Invalid params");
        }
    }

    private void decodeIgnore(
        A2aMessageProxy proxy,
        long traceId,
        boolean last)
    {
    }

    private JsonPipeline.Status decodeResponseIgnore(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        boolean bodyReceived)
    {
        proxy.responseRemaining = 0;
        return JsonPipeline.Status.STARVED;
    }

    private JsonPipeline.Status decodeJson(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        boolean bodyReceived)
    {
        JsonPipeline.Status status;
        if (proxy.acquireEncodeSlot())
        {
            if (last && !bodyReceived)
            {
                status = decodeJsonEmpty(proxy);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(proxy.encodeSlot);
                proxy.responseGenerator.wrap(slot, proxy.encodeSlotOffset, encodePool.slotCapacity());
                status = proxy.responsePipeline.transform(buffer, offset, offset + length, last);
                proxy.encodeSlotOffset += proxy.responseGenerator.length();
                proxy.responseRemaining = proxy.responsePipeline.remaining();
            }
        }
        else
        {
            proxy.cleanup(0L);
            status = JsonPipeline.Status.REJECTED;
        }
        return status;
    }

    private JsonPipeline.Status decodeJsonEmpty(
        A2aMessageProxy proxy)
    {
        proxy.responseRemaining = 0;
        proxy.doEncodeReply(NULL_RESPONSE_VALUE, 0, NULL_RESPONSE_VALUE.length);

        return JsonPipeline.Status.COMPLETED;
    }

    private JsonPipeline.Status decodeText(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        boolean bodyReceived)
    {
        JsonPipeline.Status status;
        if (proxy.acquireEncodeSlot())
        {
            final MutableDirectBufferEx slot = encodePool.buffer(proxy.encodeSlot);
            proxy.escapeGenerator.wrap(slot, proxy.encodeSlotOffset, encodePool.slotCapacity());
            final int pending = length - proxy.responseConsumed;
            final Completion completion = last ? Completion.COMPLETE : Completion.INCOMPLETE;
            proxy.escapeGenerator.writeSegment(buffer, offset + proxy.responseConsumed, pending, completion);
            proxy.encodeSlotOffset += proxy.escapeGenerator.length();
            proxy.responseConsumed += proxy.escapeGenerator.consumed();
            proxy.responseRemaining = length - proxy.responseConsumed;

            if (proxy.responseRemaining > 0)
            {
                status = JsonPipeline.Status.SUSPENDED;
            }
            else
            {
                proxy.responseConsumed = 0;
                status = last ? JsonPipeline.Status.COMPLETED : JsonPipeline.Status.STARVED;
            }
        }
        else
        {
            proxy.cleanup(0L);
            status = JsonPipeline.Status.REJECTED;
        }
        return status;
    }

    private JsonPipeline.Status decodeFile(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        boolean bodyReceived)
    {
        JsonPipeline.Status status;
        if (proxy.acquireEncodeSlot())
        {
            final MutableDirectBufferEx slot = encodePool.buffer(proxy.encodeSlot);
            final int groups = Math.min(length / 3, proxy.encodeFree() / 4);
            int source = offset;
            int target = proxy.encodeSlotOffset;
            for (int i = 0; i < groups; i++)
            {
                final int triple = (buffer.getByte(source) & 0xff) << 16 |
                    (buffer.getByte(source + 1) & 0xff) << 8 |
                    buffer.getByte(source + 2) & 0xff;
                slot.putByte(target, BASE64_ALPHABET[triple >> 18 & 0x3f]);
                slot.putByte(target + 1, BASE64_ALPHABET[triple >> 12 & 0x3f]);
                slot.putByte(target + 2, BASE64_ALPHABET[triple >> 6 & 0x3f]);
                slot.putByte(target + 3, BASE64_ALPHABET[triple & 0x3f]);
                source += 3;
                target += 4;
            }
            proxy.encodeSlotOffset = target;
            proxy.responseRemaining = length - groups * 3;

            if (proxy.responseRemaining >= 3)
            {
                status = JsonPipeline.Status.SUSPENDED;
            }
            else if (last && proxy.encodeFree() >= 4)
            {
                final int tail = proxy.responseRemaining;
                if (tail > 0)
                {
                    final int b0 = buffer.getByte(source) & 0xff;
                    final int b1 = tail > 1 ? buffer.getByte(source + 1) & 0xff : 0;
                    final int triple = b0 << 16 | b1 << 8;
                    slot.putByte(proxy.encodeSlotOffset, BASE64_ALPHABET[triple >> 18 & 0x3f]);
                    slot.putByte(proxy.encodeSlotOffset + 1, BASE64_ALPHABET[triple >> 12 & 0x3f]);
                    slot.putByte(proxy.encodeSlotOffset + 2, tail > 1 ? BASE64_ALPHABET[triple >> 6 & 0x3f] : BASE64_PAD[0]);
                    slot.putByte(proxy.encodeSlotOffset + 3, BASE64_PAD[0]);
                    proxy.encodeSlotOffset += 4;
                    proxy.responseRemaining = 0;
                }
                status = JsonPipeline.Status.COMPLETED;
            }
            else
            {
                status = last ? JsonPipeline.Status.SUSPENDED : JsonPipeline.Status.STARVED;
            }
        }
        else
        {
            proxy.cleanup(0L);
            status = JsonPipeline.Status.REJECTED;
        }
        return status;
    }

    private JsonPipeline.Status decodeEvents(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        boolean bodyReceived)
    {
        JsonPipeline.Status status;
        final boolean record = length >= EVENT_RECORD_HEADER;
        final int size = record ? buffer.getInt(offset) : 0;

        if (record && length >= EVENT_RECORD_HEADER + size)
        {
            if (proxy.encodeSlotOffset == 0)
            {
                status = doEncodeEvent(proxy, traceId, buffer, offset + EVENT_RECORD_HEADER, size)
                    ? JsonPipeline.Status.ADVANCED
                    : JsonPipeline.Status.REJECTED;
                proxy.responseRemaining = length - EVENT_RECORD_HEADER - size;
            }
            else
            {
                proxy.responseRemaining = length;
                status = JsonPipeline.Status.SUSPENDED;
            }
        }
        else
        {
            proxy.responseRemaining = length;
            if (last)
            {
                proxy.responseDecoder = decodeEventsLastChunk;
                status = JsonPipeline.Status.ADVANCED;
            }
            else
            {
                status = JsonPipeline.Status.STARVED;
            }
        }
        return status;
    }

    private JsonPipeline.Status decodeEventsLastChunk(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        boolean bodyReceived)
    {
        JsonPipeline.Status status = JsonPipeline.Status.SUSPENDED;
        proxy.responseRemaining = 0;
        if (proxy.encodeSlotOffset == 0)
        {
            final String update = artifactUpdate(proxy, "[]", "true", "true");
            proxy.doEncodeReplyMessage(proxy.artifactDataEx(), update);
            proxy.flushReply(traceId);
            proxy.responseDecoder = decodeEventsCompleted;
            status = JsonPipeline.Status.ADVANCED;
        }
        return status;
    }

    private JsonPipeline.Status decodeEventsCompleted(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        boolean bodyReceived)
    {
        JsonPipeline.Status status = JsonPipeline.Status.SUSPENDED;
        proxy.responseRemaining = 0;
        if (proxy.encodeSlotOffset == 0)
        {
            final String update = "{\"statusUpdate\":{\"taskId\":" + quote(proxy.taskId) +
                ",\"contextId\":" + quote(proxy.contextId) + ",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}}}";
            proxy.doEncodeReplyMessage(proxy.statusDataEx(A2aTaskState.COMPLETED), update);
            proxy.responseSuffix = null;
            status = JsonPipeline.Status.COMPLETED;
        }
        return status;
    }

    private boolean doEncodeEvent(
        A2aMessageProxy proxy,
        long traceId,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final byte[] prefix = artifactUpdate(proxy, "[{\"text\":\"", null, null).getBytes(UTF_8);
        final byte[] suffix = ("\"}]},\"append\":" + proxy.artifactAppend + ",\"lastChunk\":false}}").getBytes(UTF_8);

        boolean encoded = false;
        if (proxy.acquireEncodeSlot())
        {
            proxy.beginReplyMessage(proxy.artifactDataEx());
            final MutableDirectBufferEx slot = encodePool.buffer(proxy.encodeSlot);
            slot.putBytes(0, prefix);
            final JsonGeneratorEx generator = JsonEx.createGenerator(GENERATE_ESCAPED);
            generator.wrap(slot, prefix.length, encodePool.slotCapacity() - suffix.length);
            generator.writeSegment(buffer, offset, length, Completion.COMPLETE);
            if (generator.consumed() == length)
            {
                final int written = prefix.length + generator.length();
                slot.putBytes(written, suffix);
                proxy.encodeSlotOffset = written + suffix.length;
                proxy.completeReplyMessage();
                proxy.artifactAppend = "true";
                proxy.flushReply(traceId);
                encoded = true;
            }
            else
            {
                proxy.cleanup(traceId);
            }
        }
        else
        {
            proxy.cleanup(traceId);
        }
        return encoded;
    }

    private String artifactUpdate(
        A2aMessageProxy proxy,
        String parts,
        String append,
        String lastChunk)
    {
        final String head = "{\"artifactUpdate\":{\"taskId\":" + quote(proxy.taskId) +
            ",\"contextId\":" + quote(proxy.contextId) +
            ",\"artifact\":{\"artifactId\":\"" + ARTIFACT_ID + "\",\"parts\":" + parts;
        return append != null ? head + "},\"append\":" + append + ",\"lastChunk\":" + lastChunk + "}}" : head;
    }

    private DirectBufferEx extensionOf(
        Flyweight flyweight)
    {
        final byte[] copy = new byte[flyweight.sizeof()];
        flyweight.buffer().getBytes(flyweight.offset(), copy);
        return new UnsafeBufferEx(copy);
    }

    private String encodeTaskId(
        String skillId,
        long affinity)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(skillId.getBytes(UTF_8)) +
            TASK_ID_SEPARATOR + supplyTaskId.apply(affinity);
    }

    private static String skillIdOf(
        String taskId)
    {
        String skillId = null;

        final int separator = taskId.indexOf(TASK_ID_SEPARATOR);
        if (separator > 0)
        {
            try
            {
                skillId = new String(Base64.getUrlDecoder().decode(taskId.substring(0, separator)), UTF_8);
            }
            catch (IllegalArgumentException ex)
            {
                skillId = null;
            }
        }

        return skillId;
    }

    private static boolean isJson(
        String mediaType)
    {
        return DEFAULT_CONTENT_TYPE.equals(mediaType) || mediaType.endsWith("+json");
    }

    private static String quote(
        String value)
    {
        return Json.createValue(value).toString();
    }

    private static String compact(
        String json)
    {
        String compacted = "{}";
        try (JsonReader reader = Json.createReader(new StringReader(json)))
        {
            compacted = reader.readValue().toString();
        }
        catch (JsonException ex)
        {
            compacted = "{}";
        }
        return compacted;
    }

    private static String pointerName(
        JsonSchemaDiagnostic diagnostic)
    {
        final String pointer = diagnostic.pointer();
        return pointer != null && pointer.startsWith("/") ? pointer.substring(1) : pointer;
    }

    private static List<String> requiredNames(
        JsonSchemaDiagnostic diagnostic)
    {
        final List<String> names = new ArrayList<>();
        final String message = diagnostic.message();
        final int open = message.indexOf('[');
        final int close = message.lastIndexOf(']');
        if (open >= 0 && close > open)
        {
            final String prefix = diagnostic.pointer() == null || diagnostic.pointer().isEmpty()
                ? ""
                : diagnostic.pointer().substring(1) + "/";
            for (String name : message.substring(open + 1, close).split(",\\s*"))
            {
                names.add(prefix + name);
            }
        }
        return names;
    }

    private static void flatten(
        JsonObject object,
        String prefix,
        Map<String, String> args)
    {
        for (Map.Entry<String, JsonValue> entry : object.entrySet())
        {
            final String name = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            final JsonValue value = entry.getValue();
            switch (value.getValueType())
            {
            case OBJECT:
                flatten(value.asJsonObject(), name, args);
                break;
            case STRING:
                args.put(name, ((JsonString) value).getString());
                break;
            case NUMBER:
            case TRUE:
            case FALSE:
                args.put(name, value.toString());
                break;
            default:
                break;
            }
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
        DirectBufferEx extension)
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
            .extension(extension, 0, extension.capacity())
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
            builder.extension(extension, 0, extension.capacity());
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
        long authorization)
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
            .build();

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
}
