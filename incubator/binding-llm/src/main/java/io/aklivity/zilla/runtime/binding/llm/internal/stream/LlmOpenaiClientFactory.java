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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmOpenaiDialectFactorySpi.NAME;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.runtime.binding.llm.codec.LlmContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.codec.LlmContentDecoderOutput;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.codec.LlmContentCodecFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmResetExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmOpenaiClientFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_SCHEME = ":scheme";
    private static final String HEADER_AUTHORITY = ":authority";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String METHOD_POST = "POST";
    private static final String PATH_CHAT_COMPLETIONS = "/chat/completions";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ERROR_TYPE_INVALID_REQUEST = "invalid_request_error";
    private static final String MESSAGE_MODEL_MISSING = "Missing required parameter: 'model'.";
    private static final String MESSAGE_SECTION_INVALID = "Invalid request section.";
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_BAD_GATEWAY = 502;

    private static final int KIND_SYSTEM_INSTRUCTION = 1;
    private static final int KIND_USER_TEXT = 2;
    private static final int KIND_USER_IMAGE = 3;
    private static final int KIND_USER_DOCUMENT = 4;
    private static final int KIND_USER_AUDIO = 5;
    private static final int KIND_ASSISTANT_TEXT = 6;
    private static final int KIND_ASSISTANT_REFUSAL = 7;
    private static final int KIND_TOOL_DEFINITION = 8;
    private static final int KIND_TOOL_CALL = 9;
    private static final int KIND_TOOL_RESULT = 10;
    private static final int KIND_UNKNOWN = 11;

    private static final int CONTENT_NONE = 0;
    private static final int CONTENT_STRING = 1;
    private static final int CONTENT_ARRAY = 2;
    private static final int CONTENT_CLOSED = 3;

    private static final int NO_MESSAGE = -2;
    private static final int NOT_HELD = -1;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_SLACK = 256;
    private static final int CONVERT_THRESHOLD = ENCODE_SLACK + ESCAPE_EXPANSION * 1024;

    private static final byte[] REQUEST_START = "{\"model\":\"".getBytes(UTF_8);
    private static final byte[] MESSAGES_START = "\",\"messages\":[".getBytes(UTF_8);
    private static final byte[] MESSAGE_START = "{\"role\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALL_ID = ",\"tool_call_id\":\"".getBytes(UTF_8);
    private static final byte[] CONTENT_MEMBER = ",\"content\":".getBytes(UTF_8);
    private static final byte[] CONTENT_STRING_START = ",\"content\":\"".getBytes(UTF_8);
    private static final byte[] CONTENT_ARRAY_START = ",\"content\":[".getBytes(UTF_8);
    private static final byte[] CONTENT_NULL = ",\"content\":null".getBytes(UTF_8);
    private static final byte[] CONTENT_ARRAY_TEXT_START = "[{\"type\":\"text\",\"text\":".getBytes(UTF_8);
    private static final byte[] PART_TEXT_START = "{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] PART_TEXT_END = "\"}".getBytes(UTF_8);
    private static final byte[] REFUSAL_START = ",\"refusal\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALLS_START = ",\"tool_calls\":[".getBytes(UTF_8);
    private static final byte[] TOOLS_START = ",\"tools\":[".getBytes(UTF_8);

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();
    private final OctetsFW extensionRO = new OctetsFW();
    private final UnsafeBufferEx replyRO = new UnsafeBufferEx(new byte[0]);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmResetExFW.Builder llmResetExRW = new LlmResetExFW.Builder();
    private final LlmAbortExFW.Builder llmAbortExRW = new LlmAbortExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx convertBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final LlmContentCodecFactory codecs;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;
    private final int decodeMax;
    private final int encodeMax;
    private final int llmTypeId;
    private final int httpTypeId;

    public LlmOpenaiClientFactory(
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.convertBuffer = new UnsafeBufferEx(new byte[encodeMax]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.codecs = new LlmContentCodecFactory();
        this.bindings = new Long2ObjectHashMap<>();
        this.llmTypeId = context.supplyTypeId(LlmBinding.NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
    }

    public void attach(
        LlmBindingConfig binding)
    {
        bindings.put(binding.id, binding);
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
        MessageConsumer app)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();
        final LlmBeginExFW llmBeginEx = begin.extension().get(llmBeginExRO::tryWrap);
        final LlmBindingConfig binding = bindings.get(routedId);
        final LlmRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        MessageConsumer newStream = null;

        if (route != null && binding.options.server != null && llmBeginEx != null &&
            NAME.equals(llmBeginEx.dialect().asString()))
        {
            newStream = new LlmOpenaiStream(
                app,
                originId,
                routedId,
                initialId,
                route.id,
                authorization,
                affinity,
                binding,
                llmBeginEx.model().asString())::onAppMessage;
        }

        return newStream;
    }

    private static String header(
        JsonEnvelope headers,
        String name)
    {
        final DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    private static String credentials(
        LlmBindingConfig binding,
        long authorization)
    {
        String credentials = null;

        if (binding.guard != null && binding.credentials != null && (authorization & GuardHandler.MASK_AUTHORIZED) != 0L)
        {
            final String value = binding.guard.credentials(authorization);
            credentials = value != null
                ? binding.credentials.replace(LlmBindingConfig.CREDENTIALS_PLACEHOLDER, value)
                : null;
        }

        return credentials;
    }

    private static int sectionKind(
        String type)
    {
        int kind = 0;

        if (type != null)
        {
            kind = switch (type)
            {
            case "system-instruction" -> KIND_SYSTEM_INSTRUCTION;
            case "user-text" -> KIND_USER_TEXT;
            case "user-image" -> KIND_USER_IMAGE;
            case "user-document" -> KIND_USER_DOCUMENT;
            case "user-audio" -> KIND_USER_AUDIO;
            case "assistant-text" -> KIND_ASSISTANT_TEXT;
            case "assistant-refusal" -> KIND_ASSISTANT_REFUSAL;
            case "tool-definition" -> KIND_TOOL_DEFINITION;
            case "tool-call" -> KIND_TOOL_CALL;
            case "tool-result" -> KIND_TOOL_RESULT;
            case "unknown" -> KIND_UNKNOWN;
            default -> 0;
            };
        }

        return kind;
    }

    private static String role(
        int kind)
    {
        return switch (kind)
        {
        case KIND_SYSTEM_INSTRUCTION -> "system";
        case KIND_ASSISTANT_TEXT, KIND_ASSISTANT_REFUSAL, KIND_TOOL_CALL -> "assistant";
        case KIND_TOOL_RESULT -> "tool";
        default -> "user";
        };
    }

    private static boolean text(
        int kind)
    {
        return kind == KIND_SYSTEM_INSTRUCTION ||
            kind == KIND_USER_TEXT ||
            kind == KIND_ASSISTANT_TEXT ||
            kind == KIND_TOOL_RESULT;
    }

    private static boolean part(
        int kind)
    {
        return kind == KIND_USER_IMAGE ||
            kind == KIND_USER_DOCUMENT ||
            kind == KIND_USER_AUDIO ||
            kind == KIND_UNKNOWN;
    }

    private final class LlmOpenaiStream
    {
        private final MessageConsumer app;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long resolvedId;
        private final long authorization;
        private final long affinity;
        private final LlmBindingConfig binding;
        private final String model;
        private final LlmOpenaiHttpStream http;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;

        private LlmOpenaiStream(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long authorization,
            long affinity,
            LlmBindingConfig binding,
            String model)
        {
            this.app = app;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.authorization = authorization;
            this.affinity = affinity;
            this.binding = binding;
            this.model = model;
            this.http = new LlmOpenaiHttpStream(this);
        }

        private long replyAvailable()
        {
            return replyMax - (replySeq - replyAck) - replyPad;
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
                onAppBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onAppData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onAppEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onAppAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onAppWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onAppReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onAppChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = 0;
            state = LlmState.openingInitial(state);

            if (model == null)
            {
                doAppReset(traceId, STATUS_BAD_REQUEST, ERROR_TYPE_INVALID_REQUEST, MESSAGE_MODEL_MISSING);
            }
            else
            {
                http.doNetBegin(traceId);

                if (http.net != null)
                {
                    http.doEncodeRequestBegin();
                    flushAppWindow(traceId);
                    http.flushEncode(traceId);
                }
            }
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final int flags = data.flags();

            initialSeq = data.sequence() + data.reserved();

            if (initialSeq > initialAck + initialMax)
            {
                cleanup(traceId);
            }
            else
            {
                if (Flags.hasInit(flags))
                {
                    final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);
                    final OctetsFW extension = llmDataEx != null ? llmDataEx.extension() : null;
                    final String id = extension != null && extension.sizeof() > 0
                        ? extension.buffer().getStringWithoutLengthUtf8(extension.offset(), extension.sizeof())
                        : null;

                    http.doEncodeSectionBegin(
                        sectionKind(llmDataEx != null ? llmDataEx.type().asString() : null),
                        llmDataEx != null ? llmDataEx.message() : NO_MESSAGE,
                        id);
                }

                final OctetsFW payload = data.payload();

                if (payload != null && payload.sizeof() > 0)
                {
                    http.doEncodeSectionData(payload.buffer(), payload.offset(), payload.sizeof());
                }

                if (Flags.hasFin(flags))
                {
                    http.doEncodeSectionEnd();
                }

                if (http.encodeFailed)
                {
                    doAppReset(traceId, STATUS_BAD_REQUEST, ERROR_TYPE_INVALID_REQUEST, MESSAGE_SECTION_INVALID);
                    http.doNetAbort(traceId);
                }
                else
                {
                    http.flushEncode(traceId);
                    flushAppWindow(traceId);
                }
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            initialSeq = end.sequence();
            state = LlmState.closingInitial(state);

            http.doEncodeRequestEnd();
            http.flushEncode(traceId);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            initialSeq = abort.sequence();
            state = LlmState.closeInitial(state);

            http.doNetAbort(traceId);
            http.cleanupEncodeSlot();
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();

            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();
            state = LlmState.openReply(state);

            http.flushReply(traceId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = LlmState.closeReply(state);

            http.doNetReset(traceId);
            http.doNetAbort(traceId);
            http.cleanupEncodeSlot();
            http.cleanupDecodeSlot();
            http.cleanupReplySlot();
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            http.doNetChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void flushAppWindow(
            long traceId)
        {
            final int credit = Math.max(0, (encodeMax - http.encodeSlotOffset - ENCODE_SLACK) / ESCAPE_EXPANSION);
            final long target = initialSeq + credit;

            if (!LlmState.initialClosing(state) && !LlmState.initialClosed(state) && target > initialAck + initialMax)
            {
                initialAck = initialSeq;
                initialMax = (int) (target - initialSeq);
                doAppWindow(traceId);
            }
        }

        private void doAppBegin(
            long traceId,
            String contentType)
        {
            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(NAME);

            if (contentType != null)
            {
                builder.contentType(contentType);
            }

            final LlmBeginExFW llmBeginEx = builder.build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(llmBeginEx.buffer(), llmBeginEx.offset(), llmBeginEx.sizeof())
                .build();

            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            state = LlmState.openingReply(state);
        }

        private void doAppData(
            long traceId,
            int flags,
            String event,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final OctetsFW extension;

            if (Flags.hasInit(flags))
            {
                final LlmDataExFW.Builder builder = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId);

                if (event != null)
                {
                    builder.type(event);
                }

                final LlmDataExFW llmDataEx = builder.build();
                extension = extensionRO.wrap(llmDataEx.buffer(), llmDataEx.offset(), llmDataEx.offset() + llmDataEx.sizeof());
            }
            else
            {
                extension = EMPTY_OCTETS;
            }

            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(0L)
                .reserved(length + replyPad)
                .payload(buffer, offset, length)
                .extension(extension)
                .build();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
            replySeq += data.reserved();
        }

        private void doAppEnd(
            long traceId)
        {
            if (!LlmState.replyClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .build();

                app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeReply(state);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (!LlmState.replyClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .build();

                app.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeReply(state);
            }
        }

        private void doAppReset(
            long traceId,
            int status,
            String type,
            String message)
        {
            if (!LlmState.initialClosed(state))
            {
                final LlmResetExFW.Builder builder = llmResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId);

                builder.error(e ->
                {
                    e.status(status);
                    if (type != null)
                    {
                        e.type(type);
                    }
                    if (message != null)
                    {
                        e.message(message);
                    }
                });

                final LlmResetExFW llmResetEx = builder.build();

                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(llmResetEx.buffer(), llmResetEx.offset(), llmResetEx.sizeof())
                    .build();

                app.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doAppRejected(
            long traceId,
            int status,
            String type,
            String message)
        {
            if (LlmState.initialClosing(state) || LlmState.initialClosed(state))
            {
                if (!LlmState.replyClosed(state))
                {
                    if (!LlmState.replyOpening(state) && !LlmState.replyOpened(state))
                    {
                        doAppBegin(traceId, null);
                    }

                    final LlmAbortExFW.Builder builder = llmAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId)
                        .usage(u ->
                        {
                        });

                    builder.error(e ->
                    {
                        e.status(status);
                        if (type != null)
                        {
                            e.type(type);
                        }
                        if (message != null)
                        {
                            e.message(message);
                        }
                    });

                    final LlmAbortExFW llmAbortEx = builder.build();

                    final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(replyId)
                        .sequence(replySeq)
                        .acknowledge(replyAck)
                        .maximum(replyMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .extension(llmAbortEx.buffer(), llmAbortEx.offset(), llmAbortEx.sizeof())
                        .build();

                    app.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                    state = LlmState.closeReply(state);
                }
            }
            else
            {
                doAppReset(traceId, status, type, message);
            }
        }

        private void doAppFailure(
            long traceId,
            int status)
        {
            if (LlmState.replyOpening(state) || LlmState.replyOpened(state))
            {
                doAppAbort(traceId);
            }
            else
            {
                doAppReset(traceId, status, null, null);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .build();

            app.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

            app.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }

        private void cleanup(
            long traceId)
        {
            doAppReset(traceId, STATUS_BAD_GATEWAY, null, null);
            doAppAbort(traceId);
            http.doNetAbort(traceId);
            http.doNetReset(traceId);
            http.cleanupEncodeSlot();
            http.cleanupDecodeSlot();
            http.cleanupReplySlot();
        }
    }

    private final class LlmOpenaiHttpStream implements LlmContentDecoderOutput
    {
        private final LlmOpenaiStream stream;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String scheme;
        private final String authority;
        private final String requestPath;

        private MessageConsumer net;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeHold = NOT_HELD;
        private boolean encodeFailed;

        private int messageCount;
        private int messageIndex = NO_MESSAGE;
        private boolean messagesClosed;
        private boolean toolsOpened;
        private boolean toolCallsOpened;
        private boolean refusalWritten;
        private int contentState;
        private int sectionKind;
        private boolean sectionOpen;
        private boolean sectionArrayPart;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private boolean decodeSlotOverflow;
        private LlmContentDecoder decoder;
        private int errorStatus;
        private boolean valueStarted;
        private String valueEvent;
        private String pendingEvent;

        private int replySlot = NO_SLOT;
        private int replySlotOffset;
        private int replySlotLength;
        private boolean replySlotLast;
        private long replyTraceId;

        private LlmOpenaiHttpStream(
            LlmOpenaiStream stream)
        {
            final LlmServerConfig server = stream.binding.options.server;

            this.stream = stream;
            this.originId = stream.routedId;
            this.routedId = stream.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.scheme = server.scheme;
            this.authority = server.host + ":" + server.port;
            this.requestPath = server.path + PATH_CHAT_COMPLETIONS;
        }

        private void doNetBegin(
            long traceId)
        {
            state = LlmState.openingInitial(state);

            final String credentials = credentials(stream.binding, stream.authorization);

            final HttpBeginExFW.Builder httpBeginExBuilder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_METHOD).value(METHOD_POST))
                .headersItem(h -> h.name(HEADER_SCHEME).value(scheme))
                .headersItem(h -> h.name(HEADER_AUTHORITY).value(authority))
                .headersItem(h -> h.name(HEADER_PATH).value(requestPath))
                .headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON));

            if (credentials != null)
            {
                httpBeginExBuilder.headersItem(h -> h.name(HEADER_AUTHORIZATION).value(credentials));
            }

            final HttpBeginExFW httpBeginEx = httpBeginExBuilder.build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(0)
                .traceId(traceId)
                .authorization(stream.authorization)
                .affinity(stream.affinity)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            net = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onNetMessage);

            if (net == null)
            {
                state = LlmState.closeInitial(state);
                state = LlmState.closeReply(state);
                stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
            }
            else
            {
                net.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
                state = LlmState.openInitial(state);
            }
        }

        private void doEncodeRequestBegin()
        {
            put(REQUEST_START);
            putEscaped(stream.model);
            put(MESSAGES_START);
        }

        private void doEncodeSectionBegin(
            int kind,
            int message,
            String extension)
        {
            sectionKind = kind;
            sectionOpen = false;
            sectionArrayPart = false;

            if (kind == KIND_TOOL_DEFINITION)
            {
                closeMessage();
                if (!messagesClosed)
                {
                    putByte(']');
                    messagesClosed = true;
                }
                if (toolsOpened)
                {
                    putByte(',');
                }
                else
                {
                    put(TOOLS_START);
                }
                toolsOpened = true;
            }
            else if (kind == 0 || messagesClosed)
            {
                encodeFailed = true;
            }
            else
            {
                if (message != messageIndex)
                {
                    closeMessage();
                    openMessage(kind, message, extension);
                }

                if (text(kind))
                {
                    beginText();
                }
                else if (part(kind))
                {
                    beginPart();
                }
                else if (kind == KIND_ASSISTANT_REFUSAL)
                {
                    closeContent(false);
                    refusalWritten = true;
                    put(REFUSAL_START);
                    sectionOpen = true;
                }
                else
                {
                    closeContent(!refusalWritten);
                    if (toolCallsOpened)
                    {
                        putByte(',');
                    }
                    else
                    {
                        put(TOOL_CALLS_START);
                    }
                    toolCallsOpened = true;
                }
            }
        }

        private void doEncodeSectionData(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (sectionKind != 0)
            {
                if (text(sectionKind) || sectionKind == KIND_ASSISTANT_REFUSAL)
                {
                    putEscaped(buffer, offset, length);

                    if (encodeHold != NOT_HELD && sectionOpen && encodeMax - encodeSlotOffset < CONVERT_THRESHOLD)
                    {
                        convertHoldToArray();
                    }
                }
                else
                {
                    put(buffer, offset, length);
                }
            }
        }

        private void doEncodeSectionEnd()
        {
            if (sectionOpen)
            {
                if (sectionArrayPart)
                {
                    put(PART_TEXT_END);
                }
                else
                {
                    putByte('"');
                }
                sectionOpen = false;
            }
            sectionKind = 0;
        }

        private void doEncodeRequestEnd()
        {
            closeMessage();

            if (!messagesClosed)
            {
                putByte(']');
                messagesClosed = true;
            }

            if (toolsOpened)
            {
                putByte(']');
            }

            putByte('}');
            encodeHold = NOT_HELD;
            state = LlmState.closingInitial(state);
        }

        private void openMessage(
            int kind,
            int message,
            String extension)
        {
            if (messageCount++ > 0)
            {
                putByte(',');
            }

            put(MESSAGE_START);
            put(role(kind).getBytes(UTF_8));
            putByte('"');

            if (kind == KIND_TOOL_RESULT)
            {
                if (extension == null)
                {
                    encodeFailed = true;
                }
                else
                {
                    put(TOOL_CALL_ID);
                    putEscaped(extension);
                    putByte('"');
                }
            }

            messageIndex = message;
            contentState = CONTENT_NONE;
            toolCallsOpened = false;
            refusalWritten = false;
        }

        private void closeMessage()
        {
            if (messageIndex != NO_MESSAGE)
            {
                closeContent(false);

                if (toolCallsOpened)
                {
                    putByte(']');
                }

                putByte('}');
                messageIndex = NO_MESSAGE;
            }
        }

        private void closeContent(
            boolean nullWhenAbsent)
        {
            encodeHold = NOT_HELD;

            if (contentState == CONTENT_ARRAY)
            {
                putByte(']');
            }
            else if (contentState == CONTENT_NONE && nullWhenAbsent)
            {
                put(CONTENT_NULL);
            }

            contentState = CONTENT_CLOSED;
        }

        private void beginText()
        {
            switch (contentState)
            {
            case CONTENT_NONE:
                encodeHold = encodeSlotOffset;
                put(CONTENT_STRING_START);
                contentState = CONTENT_STRING;
                break;
            case CONTENT_STRING:
                convertHoldToArray();
                putByte(',');
                put(PART_TEXT_START);
                sectionArrayPart = true;
                break;
            case CONTENT_ARRAY:
                putByte(',');
                put(PART_TEXT_START);
                sectionArrayPart = true;
                break;
            default:
                encodeFailed = true;
                break;
            }

            sectionOpen = !encodeFailed;
        }

        private void beginPart()
        {
            switch (contentState)
            {
            case CONTENT_NONE:
                put(CONTENT_ARRAY_START);
                contentState = CONTENT_ARRAY;
                break;
            case CONTENT_STRING:
                convertHoldToArray();
                putByte(',');
                break;
            case CONTENT_ARRAY:
                putByte(',');
                break;
            default:
                encodeFailed = true;
                break;
            }
        }

        private void convertHoldToArray()
        {
            final MutableDirectBufferEx buffer = encodeBuffer(CONTENT_ARRAY_TEXT_START.length + 1);

            if (buffer != null)
            {
                final int at = encodeHold + CONTENT_MEMBER.length;
                final int tail = encodeSlotOffset - at;

                convertBuffer.putBytes(0, buffer, at, tail);
                buffer.putBytes(at, CONTENT_ARRAY_TEXT_START);
                buffer.putBytes(at + CONTENT_ARRAY_TEXT_START.length, convertBuffer, 0, tail);
                encodeSlotOffset += CONTENT_ARRAY_TEXT_START.length;

                if (sectionOpen)
                {
                    sectionArrayPart = true;
                }
                else
                {
                    putByte('}');
                }

                contentState = CONTENT_ARRAY;
                encodeHold = NOT_HELD;
            }
        }

        private MutableDirectBufferEx encodeBuffer(
            int length)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(initialId);
            }

            MutableDirectBufferEx buffer = null;

            if (encodeSlot != NO_SLOT && encodeSlotOffset + length <= encodeMax)
            {
                buffer = encodePool.buffer(encodeSlot);
            }
            else
            {
                encodeFailed = true;
            }

            return buffer;
        }

        private void putByte(
            int value)
        {
            final MutableDirectBufferEx buffer = encodeBuffer(1);

            if (buffer != null)
            {
                buffer.putByte(encodeSlotOffset++, (byte) value);
            }
        }

        private void put(
            byte[] bytes)
        {
            final MutableDirectBufferEx buffer = encodeBuffer(bytes.length);

            if (buffer != null)
            {
                buffer.putBytes(encodeSlotOffset, bytes);
                encodeSlotOffset += bytes.length;
            }
        }

        private void put(
            DirectBufferEx source,
            int offset,
            int length)
        {
            final MutableDirectBufferEx buffer = encodeBuffer(length);

            if (buffer != null)
            {
                buffer.putBytes(encodeSlotOffset, source, offset, length);
                encodeSlotOffset += length;
            }
        }

        private void putEscaped(
            String value)
        {
            final byte[] bytes = value.getBytes(UTF_8);
            putEscaped(new UnsafeBufferEx(bytes), 0, bytes.length);
        }

        private void putEscaped(
            DirectBufferEx source,
            int offset,
            int length)
        {
            final MutableDirectBufferEx buffer = encodeBuffer(length * ESCAPE_EXPANSION);

            if (buffer != null)
            {
                int position = encodeSlotOffset;

                for (int i = 0; i < length; i++)
                {
                    final byte value = source.getByte(offset + i);

                    switch (value)
                    {
                    case '"':
                        buffer.putByte(position++, (byte) '\\');
                        buffer.putByte(position++, (byte) '"');
                        break;
                    case '\\':
                        buffer.putByte(position++, (byte) '\\');
                        buffer.putByte(position++, (byte) '\\');
                        break;
                    case '\n':
                        buffer.putByte(position++, (byte) '\\');
                        buffer.putByte(position++, (byte) 'n');
                        break;
                    case '\r':
                        buffer.putByte(position++, (byte) '\\');
                        buffer.putByte(position++, (byte) 'r');
                        break;
                    case '\t':
                        buffer.putByte(position++, (byte) '\\');
                        buffer.putByte(position++, (byte) 't');
                        break;
                    case '\b':
                        buffer.putByte(position++, (byte) '\\');
                        buffer.putByte(position++, (byte) 'b');
                        break;
                    case '\f':
                        buffer.putByte(position++, (byte) '\\');
                        buffer.putByte(position++, (byte) 'f');
                        break;
                    default:
                        if (value >= 0 && value < 0x20)
                        {
                            buffer.putByte(position++, (byte) '\\');
                            buffer.putByte(position++, (byte) 'u');
                            buffer.putByte(position++, (byte) '0');
                            buffer.putByte(position++, (byte) '0');
                            buffer.putByte(position++, (byte) Character.forDigit(value >> 4, 16));
                            buffer.putByte(position++, (byte) Character.forDigit(value & 0x0f, 16));
                        }
                        else
                        {
                            buffer.putByte(position++, value);
                        }
                        break;
                    }
                }

                encodeSlotOffset = position;
            }
        }

        private void flushEncode(
            long traceId)
        {
            if (net != null && !LlmState.initialClosed(state))
            {
                if (encodeSlot != NO_SLOT)
                {
                    final int flushLimit = encodeHold != NOT_HELD ? encodeHold : encodeSlotOffset;
                    final long window = initialMax - (initialSeq - initialAck) - initialPad;
                    final int length = (int) Math.max(Math.min(flushLimit, window), 0L);

                    if (length > 0)
                    {
                        final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);

                        doNetData(traceId, buffer, 0, length);

                        final int remaining = encodeSlotOffset - length;
                        if (remaining > 0)
                        {
                            buffer.putBytes(0, buffer, length, remaining);
                        }
                        encodeSlotOffset = remaining;

                        if (encodeHold != NOT_HELD)
                        {
                            encodeHold -= length;
                        }
                    }

                    if (encodeSlotOffset == 0)
                    {
                        cleanupEncodeSlot();
                    }
                }

                if (LlmState.initialClosing(state) && encodeSlot == NO_SLOT)
                {
                    doNetEnd(traceId);
                }

                stream.flushAppWindow(traceId);
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
                onNetBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onNetData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onNetEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onNetAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onNetWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onNetReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onNetChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final HttpBeginExFW httpBeginEx = begin.extension().get(httpBeginExRO::tryWrap);

            replySeq = begin.sequence();
            replyAck = begin.acknowledge();
            replyMax = decodeMax;
            state = LlmState.openingReply(state);

            final JsonEnvelope headers = httpBeginEx != null ? LlmModelEnvelope.of(httpBeginEx) : null;
            final String status = headers != null ? header(headers, HEADER_STATUS) : null;
            final String contentType = headers != null ? header(headers, HEADER_CONTENT_TYPE) : null;
            final String mediaType = LlmContentCodecFactory.mediaType(contentType);

            if (status == null)
            {
                doNetReset(traceId);
                stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
            }
            else if (status.startsWith("2"))
            {
                decoder = CONTENT_TYPE_JSON.equals(mediaType) || CONTENT_TYPE_EVENT_STREAM.equals(mediaType)
                    ? codecs.createDecoder(mediaType)
                    : null;

                if (decoder != null)
                {
                    stream.doAppBegin(traceId, mediaType);
                    doNetWindow(traceId);
                }
                else
                {
                    doNetReset(traceId);
                    stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
                }
            }
            else
            {
                errorStatus = Integer.parseInt(status);
                doNetWindow(traceId);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + data.reserved();

            if (replySeq > replyAck + replyMax)
            {
                stream.cleanup(traceId);
            }
            else if (payload != null && payload.sizeof() > 0)
            {
                appendDecodeSlot(payload.buffer(), payload.offset(), payload.sizeof());

                if (errorStatus == 0 && decoder != null && decoder.streaming())
                {
                    decodeNet(traceId);
                }
                else
                {
                    flushNetWindow(traceId);
                }
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            replySeq = end.sequence();
            state = LlmState.closingReply(state);

            if (errorStatus != 0)
            {
                final int status = errorStatus;
                String type = null;
                String message = null;

                if (decodeSlot != NO_SLOT && !decodeSlotOverflow)
                {
                    final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                    final byte[] body = new byte[decodeSlotOffset];
                    buffer.getBytes(0, body);

                    try (JsonReader reader = Json.createReader(new ByteArrayInputStream(body)))
                    {
                        final JsonObject error = reader.readObject().getJsonObject("error");
                        type = error.getString("type", null);
                        message = error.getString("message", null);
                    }
                    catch (JsonException | ClassCastException | NullPointerException ex)
                    {
                        type = null;
                        message = null;
                    }
                }

                cleanupDecodeSlot();
                stream.doAppRejected(traceId, status, type, message);
                state = LlmState.closeReply(state);
            }
            else if (decoder != null)
            {
                if (!decoder.streaming())
                {
                    decodeBody(traceId);
                }
                else
                {
                    decodeNet(traceId);
                }

                finishReply(traceId);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            replySeq = abort.sequence();
            state = LlmState.closeReply(state);

            stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
            cleanupDecodeSlot();
            cleanupReplySlot();
        }

        private void onNetWindow(
            WindowFW window)
        {
            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialPad = window.padding();
            state = LlmState.openInitial(state);

            flushEncode(window.traceId());
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = LlmState.closeInitial(state);

            stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
            cleanupEncodeSlot();
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            stream.doAppChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void appendDecodeSlot(
            DirectBuffer source,
            int offset,
            int length)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(replyId);
            }

            if (decodeSlot != NO_SLOT && decodeSlotOffset + length <= decodeMax)
            {
                decodePool.buffer(decodeSlot).putBytes(decodeSlotOffset, source, offset, length);
                decodeSlotOffset += length;
            }
            else
            {
                decodeSlotOverflow = true;
            }
        }

        private void decodeBody(
            long traceId)
        {
            replyTraceId = traceId;

            if (decodeSlot != NO_SLOT && !decodeSlotOverflow)
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                decoder.decode(buffer, 0, decodeSlotOffset, this);
                cleanupDecodeSlot();
            }
            else
            {
                cleanupDecodeSlot();
                stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
            }
        }

        private void decodeNet(
            long traceId)
        {
            replyTraceId = traceId;

            if (decodeSlot != NO_SLOT && replySlot == NO_SLOT)
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                final int progress = decoder.decode(buffer, 0, decodeSlotOffset, this);

                final int remaining = decodeSlotOffset - progress;
                if (remaining > 0 && progress > 0)
                {
                    buffer.putBytes(0, buffer, progress, remaining);
                }
                decodeSlotOffset = remaining;

                if (decodeSlotOffset == 0)
                {
                    cleanupDecodeSlot();
                }
            }

            flushNetWindow(traceId);
        }

        private void flushNetWindow(
            long traceId)
        {
            final long ack = Math.max(decodeSlotOverflow ? replySeq : replySeq - decodeSlotOffset, replyAck);

            if (ack > replyAck || !LlmState.replyOpened(state))
            {
                replyAck = ack;
                doNetWindow(traceId);
            }
        }

        private void finishReply(
            long traceId)
        {
            if (replySlot == NO_SLOT && (decodeSlot == NO_SLOT || decodeSlotOffset == 0))
            {
                stream.doAppEnd(traceId);
                state = LlmState.closeReply(state);
            }
            else
            {
                state = LlmState.deferReplyEnd(state);
            }
        }

        private void flushReply(
            long traceId)
        {
            if (replySlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(replySlot);
                final int available = (int) Math.max(Math.min(stream.replyAvailable(), replySlotLength), 0L);

                if (available > 0)
                {
                    final boolean complete = available == replySlotLength;
                    final int flags = complete && replySlotLast ? Flags.FIN : Flags.NONE;
                    final int dataFlags = valueStarted ? flags : Flags.init(flags);

                    stream.doAppData(traceId, dataFlags, valueEvent, buffer, replySlotOffset, available);
                    valueStarted = !(complete && replySlotLast);
                    replySlotOffset += available;
                    replySlotLength -= available;
                }

                if (replySlotLength == 0)
                {
                    cleanupReplySlot();
                }
            }

            if (replySlot == NO_SLOT)
            {
                if (decoder != null && decoder.streaming() && decodeSlot != NO_SLOT && decodeSlotOffset > 0)
                {
                    decodeNet(traceId);
                }

                if (LlmState.replyEndDeferred(state) && replySlot == NO_SLOT &&
                    (decodeSlot == NO_SLOT || decodeSlotOffset == 0))
                {
                    state = LlmState.clearReplyEndDeferred(state);
                    stream.doAppEnd(traceId);
                    state = LlmState.closeReply(state);
                }
            }
        }

        @Override
        public boolean available()
        {
            return replySlot == NO_SLOT;
        }

        @Override
        public void event(
            String event)
        {
            pendingEvent = event;
        }

        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length,
            boolean last)
        {
            if (!valueStarted)
            {
                valueEvent = pendingEvent;
            }

            final int sent = (int) Math.max(Math.min(stream.replyAvailable(), length), 0L);

            if (sent > 0 || length == 0 && last)
            {
                final int flags = sent == length && last ? Flags.FIN : Flags.NONE;
                final int dataFlags = valueStarted ? flags : Flags.init(flags);

                replyRO.wrap(buffer, offset, sent);
                stream.doAppData(replyTraceId, dataFlags, valueEvent, replyRO, 0, sent);
                valueStarted = !(sent == length && last);
            }

            if (sent < length)
            {
                replySlot = encodePool.acquire(replyId);

                if (replySlot != NO_SLOT && length - sent <= encodeMax)
                {
                    encodePool.buffer(replySlot).putBytes(0, buffer, offset + sent, length - sent);
                    replySlotOffset = 0;
                    replySlotLength = length - sent;
                    replySlotLast = last;
                }
                else
                {
                    cleanupReplySlot();
                    stream.doAppFailure(replyTraceId, STATUS_BAD_GATEWAY);
                }
            }
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            pendingEvent = null;
        }

        private void doNetData(
            long traceId,
            MutableDirectBufferEx buffer,
            int offset,
            int length)
        {
            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(stream.authorization)
                .flags(Flags.COMPLETE)
                .budgetId(0L)
                .reserved(length + initialPad)
                .payload(buffer, offset, length)
                .extension(EMPTY_OCTETS)
                .build();

            net.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
            initialSeq += data.reserved();
        }

        private void doNetEnd(
            long traceId)
        {
            if (net != null && !LlmState.initialClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(stream.authorization)
                    .build();

                net.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doNetAbort(
            long traceId)
        {
            if (net != null && !LlmState.initialClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(stream.authorization)
                    .build();

                net.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doNetReset(
            long traceId)
        {
            if (net != null && !LlmState.replyClosed(state))
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .build();

                net.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
                state = LlmState.closeReply(state);
            }
        }

        private void doNetWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .build();

            net.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
            state = LlmState.openReply(state);
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (net != null)
            {
                final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(extension)
                    .build();

                net.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeHold = NOT_HELD;
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

        private void cleanupReplySlot()
        {
            if (replySlot != NO_SLOT)
            {
                encodePool.release(replySlot);
                replySlot = NO_SLOT;
                replySlotOffset = 0;
                replySlotLength = 0;
            }
        }
    }
}
