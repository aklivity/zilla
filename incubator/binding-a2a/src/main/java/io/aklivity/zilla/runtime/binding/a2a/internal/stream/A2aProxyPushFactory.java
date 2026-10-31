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

package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_NOTIFY;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;

import java.io.ByteArrayInputStream;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyResolution;
import io.aklivity.zilla.runtime.binding.a2a.internal.push.A2aProxyPush;
import io.aklivity.zilla.runtime.binding.a2a.internal.push.A2aPushSeal;
import io.aklivity.zilla.runtime.binding.a2a.internal.push.A2aPushTaskScanner;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

final class A2aProxyPushFactory
{
    private static final String A2A_TYPE_NAME = "a2a";

    private static final int ERROR_CODE_NOT_FOUND = 404;
    private static final int ERROR_CODE_PAYLOAD_TOO_LARGE = 413;
    private static final int ERROR_CODE_BAD_GATEWAY = 502;
    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final String ERROR_MESSAGE_NOT_FOUND = "Not Found";
    private static final String ERROR_MESSAGE_PAYLOAD_TOO_LARGE = "Payload too large";
    private static final String ERROR_MESSAGE_BAD_GATEWAY = "Bad Gateway";
    private static final String ERROR_MESSAGE_INVALID_PARAMS = "Invalid params";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final A2aBeginExFW a2aBeginExRO = new A2aBeginExFW();

    private final A2aBeginExFW.Builder a2aBeginExRW = new A2aBeginExFW.Builder();
    private final A2aResetExFW.Builder a2aResetExRW = new A2aResetExFW.Builder();

    private final A2aPushTaskScanner scanner = new A2aPushTaskScanner();

    private final A2aProxyFactory proxy;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool decodePool;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final long affinity;
    private final int a2aTypeId;
    private final int decodeMax;

    private final A2aPushNotifyDecoder decodeNotifyTaskId = this::decodeNotifyTaskId;
    private final A2aPushNotifyDecoder decodeNotifyForward = this::decodeNotifyForward;
    private final A2aPushNotifyDecoder decodeNotifyReject = this::decodeNotifyReject;

    A2aProxyPushFactory(
        A2aProxyFactory proxy,
        EngineContext context)
    {
        this.proxy = proxy;
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.affinity = context.affinity();
        this.a2aTypeId = context.supplyTypeId(A2A_TYPE_NAME);
        this.decodeMax = decodePool.slotCapacity();
    }

    MessageConsumer newStream(
        A2aProxyBindingConfig binding,
        A2aBeginExFW beginEx,
        long authorization,
        MessageConsumer sender,
        long originId,
        long routedId,
        long initialId)
    {
        MessageConsumer stream = null;

        final A2aProxyPush push = binding.push;

        if (push != null)
        {
            switch (beginEx.kind())
            {
            case KIND_PUSH_NOTIFY:
                stream = new A2aPushNotify(sender, originId, routedId, initialId, push)::onAppMessage;
                break;
            case KIND_PUSH_CONFIG_CREATE:
                stream = newRelay(binding, beginEx.pushConfigCreate().agent().asString(), authorization,
                    sender, originId, routedId, initialId);
                break;
            case KIND_PUSH_CONFIG_GET:
                stream = newRelay(binding, beginEx.pushConfigGet().agent().asString(), authorization,
                    sender, originId, routedId, initialId);
                break;
            case KIND_PUSH_CONFIG_LIST:
                stream = newRelay(binding, beginEx.pushConfigList().agent().asString(), authorization,
                    sender, originId, routedId, initialId);
                break;
            case KIND_MESSAGE_SEND:
                stream = newRelay(binding, beginEx.messageSend().agent().asString(), authorization,
                    sender, originId, routedId, initialId);
                break;
            case KIND_MESSAGE_STREAM:
                stream = newRelay(binding, beginEx.messageStream().agent().asString(), authorization,
                    sender, originId, routedId, initialId);
                break;
            default:
                break;
            }
        }

        return stream;
    }

    private MessageConsumer newRelay(
        A2aProxyBindingConfig binding,
        String agent,
        long authorization,
        MessageConsumer sender,
        long originId,
        long routedId,
        long initialId)
    {
        final A2aProxyResolution resolution = binding.resolve(agent, authorization);

        return resolution.outcome == A2aProxyResolution.Outcome.ROUTED
            ? new A2aPushRelay(sender, originId, routedId, initialId, binding.push, resolution.route.id)::onAppMessage
            : null;
    }

    private abstract class A2aPushStream
    {
        protected final MessageConsumer sender;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final A2aProxyPush push;

        protected A2aPushClient client;
        protected int state;
        protected int decodeSlot = NO_SLOT;
        protected int decodeSlotOffset;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;
        protected long replyBud;
        protected int replyPad;

        A2aPushStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            A2aProxyPush push)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.push = push;
        }

        void onAppMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                state = A2aState.openingInitial(state);
                initialSeq = begin.sequence();
                initialAck = begin.acknowledge();
                onAppBegin(begin.traceId(), begin.authorization(), begin.extension());
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                initialSeq = data.sequence() + data.reserved();
                if (data.payload() != null)
                {
                    onAppPayload(data.traceId(), data.authorization(), data.payload());
                }
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                initialSeq = flush.sequence() + flush.reserved();
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                initialSeq = end.sequence();
                state = A2aState.closedInitial(state);
                onAppEnded(end.traceId(), end.authorization());
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                initialSeq = abort.sequence();
                state = A2aState.closedReply(A2aState.closedInitial(state));
                cleanup(abort.traceId(), abort.authorization());
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                state = A2aState.openedReply(state);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyBud = window.budgetId();
                replyPad = window.padding();
                onAppWindowed(window.traceId(), window.authorization(), window.budgetId(), window.padding(),
                    window.capabilities());
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                state = A2aState.closedReply(state);
                cleanup(reset.traceId(), reset.authorization());
                break;
            default:
                break;
            }
        }

        abstract void onAppBegin(
            long traceId,
            long authorization,
            OctetsFW extension);

        abstract void onAppPayload(
            long traceId,
            long authorization,
            OctetsFW payload);

        abstract void onAppEnded(
            long traceId,
            long authorization);

        abstract void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities);

        void onNetBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doAppBegin(traceId, authorization, extension);
        }

        void onNetData(
            DataFW data)
        {
            final OctetsFW payload = data.payload();
            final long sequence = replySeq;
            final int reserved = data.reserved();

            proxy.doData(sender, originId, routedId, replyId, sequence, replyAck, replyMax,
                data.traceId(), data.authorization(), data.flags(), data.budgetId(), reserved,
                payload != null ? payload.buffer() : null, payload != null ? payload.offset() : 0,
                payload != null ? payload.sizeof() : -1, data.extension());

            replySeq = sequence + reserved;
        }

        void onNetFlush(
            FlushFW flush)
        {
            proxy.doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(), flush.extension());

            replySeq += flush.reserved();
        }

        void onNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            cleanupDecodeSlot();
            doAppEnd(traceId, authorization, extension);
        }

        void onNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            cleanupDecodeSlot();
            doAppAbort(traceId, authorization, extension);
        }

        void onNetWindow(
            long traceId,
            long authorization,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
        }

        void onNetReset(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            cleanupDecodeSlot();
            doAppReset(traceId, authorization, extension);
        }

        void cleanup(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlot();

            if (client != null)
            {
                client.doNetAbort(traceId, authorization);
                client.doNetReset(traceId, authorization);
            }

            doAppAbort(traceId, authorization, null);
            doAppReset(traceId, authorization, null);
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

        boolean acquireDecodeSlot()
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            return decodeSlot != NO_SLOT;
        }

        void doAppBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            state = A2aState.openingReply(state);

            proxy.doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        int replyWindow()
        {
            return Math.max(replyMax - (int) (replySeq - replyAck) - replyPad, 0);
        }

        int doAppData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int length = Math.min(replyWindow(), limit - offset);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                proxy.doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, COMPLETE, replyBud, reserved, buffer, offset, length, null);

                replySeq += reserved;

                assert replySeq <= replyAck + replyMax;
            }

            return offset + length;
        }

        void doAppEnd(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                proxy.doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    extension);
            }
        }

        void doAppAbort(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (A2aState.replyOpening(state) && !A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                proxy.doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    extension);
            }
        }

        void doAppReset(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                proxy.doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
            }
        }

        void doAppWindow(
            long traceId,
            long authorization)
        {
            proxy.doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0, 0);
        }

        void doAppResetError(
            long traceId,
            long authorization,
            int code,
            String message)
        {
            cleanupDecodeSlot();

            final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .error(e -> e.code(code).message(message))
                .build();

            if (!A2aState.replyOpening(state))
            {
                doAppBegin(traceId, authorization, null);
                doAppWindow(traceId, authorization);
            }

            doAppReset(traceId, authorization, resetEx);
        }
    }

    private final class A2aPushRelay extends A2aPushStream
    {
        private final long exitId;

        private int kind;
        private String agent;
        private String taskId;
        private String extensions;
        private long timeout;
        private int contentLength;
        private OctetsFW beginExtension;

        private DirectBufferEx requestBuffer;
        private int requestLimit;
        private int requestForwarded;

        private DirectBufferEx responseBuffer;
        private int responseLimit;
        private int responseDelivered;

        A2aPushRelay(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            A2aProxyPush push,
            long exitId)
        {
            super(sender, originId, routedId, initialId, push);
            this.exitId = exitId;
        }

        @Override
        void onAppBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final A2aBeginExFW beginEx = a2aBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());

            kind = beginEx.kind();

            switch (kind)
            {
            case KIND_PUSH_CONFIG_CREATE:
                final var create = beginEx.pushConfigCreate();
                agent = create.agent().asString();
                taskId = create.taskId().asString();
                extensions = create.extensions().asString();
                timeout = create.timeout();
                contentLength = create.contentLength();
                break;
            case KIND_MESSAGE_SEND:
                final var send = beginEx.messageSend();
                agent = send.agent().asString();
                extensions = send.extensions().asString();
                timeout = send.timeout();
                contentLength = send.contentLength();
                break;
            case KIND_MESSAGE_STREAM:
                final var stream = beginEx.messageStream();
                agent = stream.agent().asString();
                extensions = stream.extensions().asString();
                timeout = stream.timeout();
                contentLength = stream.contentLength();
                break;
            default:
                contentLength = 0;
                break;
            }

            if (hasRequestBody())
            {
                if (contentLength < 0 || contentLength > decodeMax)
                {
                    doAppResetError(traceId, authorization, ERROR_CODE_PAYLOAD_TOO_LARGE, ERROR_MESSAGE_PAYLOAD_TOO_LARGE);
                }
                else if (!acquireDecodeSlot())
                {
                    doAppResetError(traceId, authorization, ERROR_CODE_BAD_GATEWAY, ERROR_MESSAGE_BAD_GATEWAY);
                }
                else
                {
                    initialMax = decodeMax;
                    doAppWindow(traceId, authorization);

                    if (contentLength == 0)
                    {
                        onRequestReady(traceId, authorization);
                    }
                }
            }
            else
            {
                client = new A2aPushClient(this, routedId, exitId);
                client.doNetBegin(traceId, authorization, extension);
            }
        }

        @Override
        void onAppPayload(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();

                initialAck = initialSeq;
                doAppWindow(traceId, authorization);

                if (decodeSlotOffset >= contentLength)
                {
                    onRequestReady(traceId, authorization);
                }
            }
        }

        @Override
        void onAppEnded(
            long traceId,
            long authorization)
        {
            if (client != null && requestBuffer == null)
            {
                client.doNetEnd(traceId, authorization);
            }
        }

        @Override
        void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            if (responseBuffer != null)
            {
                flushResponse(traceId, authorization);
            }
            else if (client != null && !bufferedResponse())
            {
                client.doNetWindow(traceId, authorization, replyAck, replyMax, budgetId, padding, capabilities);
            }
        }

        @Override
        void onNetBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!bufferedResponse())
            {
                super.onNetBegin(traceId, authorization, extension);
            }
            else if (acquireDecodeSlot())
            {
                client.doNetWindow(traceId, authorization, client.replySeq(), decodeMax, 0L, 0, 0);
            }
            else
            {
                cleanup(traceId, authorization);
            }
        }

        @Override
        void onNetData(
            DataFW data)
        {
            if (!bufferedResponse())
            {
                super.onNetData(data);
            }
            else if (data.payload() != null)
            {
                final OctetsFW payload = data.payload();
                final long traceId = data.traceId();
                final long authorization = data.authorization();

                if (decodeSlotOffset + payload.sizeof() > decodeMax)
                {
                    doAppResetError(traceId, authorization, ERROR_CODE_BAD_GATEWAY, ERROR_MESSAGE_BAD_GATEWAY);
                    client.doNetAbort(traceId, authorization);
                    client.doNetReset(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();

                    client.doNetWindow(traceId, authorization, client.replySeq(), decodeMax, 0L, 0, 0);
                }
            }
        }

        @Override
        void onNetFlush(
            FlushFW flush)
        {
            if (!bufferedResponse())
            {
                super.onNetFlush(flush);
            }
        }

        @Override
        void onNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!bufferedResponse())
            {
                super.onNetEnd(traceId, authorization, extension);
            }
            else if (decodeSlot != NO_SLOT)
            {
                final byte[] response = new byte[decodeSlotOffset];
                decodePool.buffer(decodeSlot).getBytes(0, response);
                cleanupDecodeSlot();

                final byte[] unsealed = unsealResponse(response);

                responseBuffer = new UnsafeBufferEx(unsealed);
                responseLimit = unsealed.length;

                doAppBegin(traceId, authorization, null);
            }
        }

        @Override
        void cleanup(
            long traceId,
            long authorization)
        {
            requestBuffer = null;
            responseBuffer = null;
            super.cleanup(traceId, authorization);
        }

        @Override
        void onNetWindow(
            long traceId,
            long authorization,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
            if (requestBuffer != null)
            {
                flushRequest(traceId, authorization);
            }
            else if (!hasRequestBody())
            {
                initialAck = acknowledge;
                initialMax = maximum;
                proxy.doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
            }
        }

        private boolean hasRequestBody()
        {
            return kind == KIND_PUSH_CONFIG_CREATE || kind == KIND_MESSAGE_SEND || kind == KIND_MESSAGE_STREAM;
        }

        private boolean bufferedResponse()
        {
            return kind != KIND_MESSAGE_SEND && kind != KIND_MESSAGE_STREAM;
        }

        private void onRequestReady(
            long traceId,
            long authorization)
        {
            final byte[] request = new byte[decodeSlotOffset];
            decodePool.buffer(decodeSlot).getBytes(0, request);
            cleanupDecodeSlot();

            final byte[] rewritten = rewriteRequest(request);

            if (rewritten == null)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_INVALID_PARAMS, ERROR_MESSAGE_INVALID_PARAMS);
            }
            else
            {
                requestBuffer = new UnsafeBufferEx(rewritten);
                requestLimit = rewritten.length;

                final A2aBeginExFW beginEx = rewrittenBeginEx(rewritten.length);

                client = new A2aPushClient(this, routedId, exitId);
                client.doNetBegin(traceId, authorization, beginEx);
            }
        }

        private A2aBeginExFW rewrittenBeginEx(
            int length)
        {
            final A2aBeginExFW.Builder builder = a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity()).typeId(a2aTypeId);

            switch (kind)
            {
            case KIND_PUSH_CONFIG_CREATE:
                builder.pushConfigCreate(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(taskId).contentLength(length).timeout(timeout);
                });
                break;
            case KIND_MESSAGE_SEND:
                builder.messageSend(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.contentLength(length).timeout(timeout);
                });
                break;
            default:
                builder.messageStream(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.contentLength(length).timeout(timeout);
                });
                break;
            }

            return builder.build();
        }

        private byte[] rewriteRequest(
            byte[] request)
        {
            byte[] rewritten = null;

            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(request)))
            {
                final JsonObject body = reader.readObject();
                final JsonObject sealed = kind == KIND_PUSH_CONFIG_CREATE
                    ? push.sealCreate(agent, taskId, body)
                    : push.sealSend(agent, body);

                rewritten = sealed != null ? A2aProxyPush.bytes(sealed) : null;
            }
            catch (JsonException | ClassCastException ex)
            {
                rewritten = null;
            }

            return rewritten;
        }

        private byte[] unsealResponse(
            byte[] response)
        {
            byte[] unsealed = response;

            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(response)))
            {
                final JsonValue value = reader.readValue();
                final JsonValue result = push.unsealResponse(value);

                if (result != value)
                {
                    unsealed = A2aProxyPush.bytes(result);
                }
            }
            catch (JsonException | ClassCastException ex)
            {
                unsealed = response;
            }

            return unsealed;
        }

        private void flushRequest(
            long traceId,
            long authorization)
        {
            requestForwarded = client.doNetData(traceId, authorization, requestBuffer, requestForwarded, requestLimit);

            if (requestForwarded == requestLimit)
            {
                requestBuffer = null;

                if (A2aState.initialClosed(state))
                {
                    client.doNetEnd(traceId, authorization);
                }
            }
        }

        private void flushResponse(
            long traceId,
            long authorization)
        {
            responseDelivered = doAppData(traceId, authorization, responseBuffer, responseDelivered, responseLimit);

            if (responseDelivered == responseLimit)
            {
                responseBuffer = null;
                doAppEnd(traceId, authorization, null);
            }
        }
    }

    @FunctionalInterface
    private interface A2aPushNotifyDecoder
    {
        void decode(
            A2aPushNotify notify,
            long traceId,
            long authorization);
    }

    private final class A2aPushNotify extends A2aPushStream
    {
        private A2aPushSeal seal;
        private A2aPushNotifyDecoder decoder;
        private String configId;
        private String taskId;
        private int contentLength;

        A2aPushNotify(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            A2aProxyPush push)
        {
            super(sender, originId, routedId, initialId, push);
        }

        @Override
        void onAppBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final var notify = a2aBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit()).pushNotify();

            configId = notify.configId().asString();
            contentLength = notify.contentLength();
            seal = push.unwrap(configId, notify.token().asString());
            seal = seal != null && authorized(traceId, notify.credentials().asString()) ? seal : null;

            if (contentLength < 0 || !acquireDecodeSlot())
            {
                doAppResetError(traceId, authorization, ERROR_CODE_BAD_GATEWAY, ERROR_MESSAGE_BAD_GATEWAY);
            }
            else
            {
                decoder = seal != null ? decodeNotifyTaskId : decodeNotifyReject;

                initialMax = decodeMax;
                doAppWindow(traceId, authorization);
                decoder.decode(this, traceId, authorization);
            }
        }

        @Override
        void onAppPayload(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();
            }

            decoder.decode(this, traceId, authorization);
        }

        @Override
        void onAppEnded(
            long traceId,
            long authorization)
        {
            if (decoder != null)
            {
                decoder.decode(this, traceId, authorization);
            }
        }

        @Override
        void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            if (client != null)
            {
                client.doNetWindow(traceId, authorization, replyAck, replyMax, budgetId, padding, capabilities);
            }
        }

        @Override
        void onNetWindow(
            long traceId,
            long authorization,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
            decoder.decode(this, traceId, authorization);
        }

        private boolean authorized(
            long traceId,
            String presented)
        {
            final GuardHandler guard = push.guard;
            boolean authorized = guard == null;

            if (guard != null && presented != null)
            {
                final long session = guard.reauthorize(traceId, routedId, initialId, presented);
                authorized = (session & GuardHandler.MASK_AUTHORIZED) != 0L;
                guard.deauthorize(session);
            }

            return authorized;
        }

        private void openDelivery(
            long traceId,
            long authorization)
        {
            final JsonObject authentication = seal.authentication();
            final String scheme = authentication != null ? schemeOf(authentication) : null;
            final String credentials = authentication != null ? string(authentication, "credentials") : null;
            final String id = seal.taskId() != null ? seal.taskId() : taskId;

            final A2aBeginExFW beginEx = a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .pushNotify(b ->
                {
                    ifPresent(seal.agent(), b::agent);
                    b.configId(configId);
                    ifPresent(id, b::taskId);
                    b.url(seal.url());
                    ifPresent(seal.token(), b::token);
                    ifPresent(scheme, b::scheme);
                    ifPresent(credentials, b::credentials);
                    b.contentLength(contentLength);
                })
                .build();

            client = new A2aPushClient(this, routedId, push.exitId);
            client.doNetBegin(traceId, authorization, beginEx);
        }
    }

    private void decodeNotifyTaskId(
        A2aPushNotify notify,
        long traceId,
        long authorization)
    {
        final boolean last = notify.initialSeq >= notify.contentLength;

        switch (scanner.scan(decodePool.buffer(notify.decodeSlot), 0, notify.decodeSlotOffset, last))
        {
        case FOUND:
            notify.taskId = scanner.taskId();
            if (notify.seal.taskId() == null || notify.seal.taskId().equals(notify.taskId))
            {
                notify.decoder = decodeNotifyForward;
                notify.openDelivery(traceId, authorization);
            }
            else
            {
                notify.decoder = decodeNotifyReject;
            }
            break;
        case INCOMPLETE:
            if (notify.decodeSlotOffset >= decodeMax)
            {
                notify.decoder = decodeNotifyReject;
            }
            break;
        default:
            notify.decoder = decodeNotifyReject;
            break;
        }

        if (notify.decoder != decodeNotifyTaskId)
        {
            notify.decoder.decode(notify, traceId, authorization);
        }
    }

    private void decodeNotifyForward(
        A2aPushNotify notify,
        long traceId,
        long authorization)
    {
        final A2aPushClient client = notify.client;

        if (notify.decodeSlot != NO_SLOT)
        {
            final MutableDirectBufferEx slot = decodePool.buffer(notify.decodeSlot);
            final int progress = client.doNetData(traceId, authorization, slot, 0, notify.decodeSlotOffset);

            if (progress > 0)
            {
                slot.putBytes(0, slot, progress, notify.decodeSlotOffset - progress);
                notify.decodeSlotOffset -= progress;
                notify.initialAck = notify.initialSeq - notify.decodeSlotOffset;
                notify.doAppWindow(traceId, authorization);
            }
        }

        if (notify.decodeSlotOffset == 0 && A2aState.initialClosed(notify.state))
        {
            notify.cleanupDecodeSlot();
            client.doNetEnd(traceId, authorization);
        }
    }

    private void decodeNotifyReject(
        A2aPushNotify notify,
        long traceId,
        long authorization)
    {
        notify.cleanupDecodeSlot();
        notify.initialAck = notify.initialSeq;

        if (!A2aState.initialClosed(notify.state))
        {
            notify.doAppWindow(traceId, authorization);
        }

        if (notify.initialSeq >= notify.contentLength)
        {
            notify.doAppResetError(traceId, authorization, ERROR_CODE_NOT_FOUND, ERROR_MESSAGE_NOT_FOUND);
        }
    }

    private final class A2aPushClient
    {
        private final A2aPushStream owner;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer net;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        A2aPushClient(
            A2aPushStream owner,
            long originId,
            long routedId)
        {
            this.owner = owner;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        long replySeq()
        {
            return replySeq;
        }

        void doNetBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            state = A2aState.openingInitial(state);

            net = proxy.newStream(this::onNetMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, extension);

            if (net == null)
            {
                owner.doAppResetError(traceId, authorization, ERROR_CODE_BAD_GATEWAY, ERROR_MESSAGE_BAD_GATEWAY);
            }
        }

        int doNetData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int window = Math.max(initialMax - (int) (initialSeq - initialAck) - initialPad, 0);
            final int length = Math.min(window, limit - offset);

            if (length > 0)
            {
                final int reserved = length + initialPad;

                proxy.doData(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, COMPLETE, 0L, reserved, buffer, offset, length, null);

                initialSeq += reserved;

                assert initialSeq <= initialAck + initialMax;
            }

            return offset + length;
        }

        void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                proxy.doEnd(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    null);
            }
        }

        void doNetAbort(
            long traceId,
            long authorization)
        {
            if (net != null && !A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                proxy.doAbort(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                    null);
            }
        }

        void doNetWindow(
            long traceId,
            long authorization,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = acknowledge;
            replyMax = maximum;

            proxy.doWindow(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        void doNetReset(
            long traceId,
            long authorization)
        {
            if (net != null && !A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                proxy.doReset(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, null);
            }
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                state = A2aState.openedReply(state);
                owner.onNetBegin(begin.traceId(), begin.authorization(), begin.extension());
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                replySeq = data.sequence() + data.reserved();
                owner.onNetData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                replySeq = flush.sequence() + flush.reserved();
                owner.onNetFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                state = A2aState.closedReply(state);
                owner.onNetEnd(end.traceId(), end.authorization(), end.extension());
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                state = A2aState.closedReply(state);
                owner.onNetAbort(abort.traceId(), abort.authorization(), abort.extension());
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                initialAck = window.acknowledge();
                initialMax = window.maximum();
                initialPad = window.padding();
                owner.onNetWindow(window.traceId(), window.authorization(), window.acknowledge(), window.maximum(),
                    window.budgetId(), window.padding(), window.capabilities());
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                state = A2aState.closedInitial(state);
                owner.onNetReset(reset.traceId(), reset.authorization(), reset.extension());
                break;
            default:
                break;
            }
        }
    }

    private static String schemeOf(
        JsonObject authentication)
    {
        String scheme = string(authentication, "scheme");

        if (scheme == null && authentication.get("schemes") instanceof JsonArray schemes && !schemes.isEmpty() &&
            schemes.get(0) instanceof JsonString first)
        {
            scheme = first.getString();
        }

        return scheme;
    }

    private static String string(
        JsonObject object,
        String name)
    {
        return object.get(name) instanceof JsonString value ? value.getString() : null;
    }

    private static void ifPresent(
        String value,
        Consumer<String> setter)
    {
        if (value != null)
        {
            setter.accept(value);
        }
    }
}
