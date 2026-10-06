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

import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockOwner;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDialectAfterBlockDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDialectBeforeBlockDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmErrorFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFollow;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmResetExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmUsageFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
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
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ERROR_TYPE_INVALID_REQUEST = "invalid_request_error";
    private static final String MODEL = "model";
    private static final String MESSAGE_MODEL_MISSING = "Missing required parameter: 'model'.";
    private static final String MESSAGE_SECTION_INVALID = "Invalid request section.";
    private static final Set<String> RESPONSE_CONTENT_TYPES = Set.of(CONTENT_TYPE_JSON, CONTENT_TYPE_EVENT_STREAM);
    private static final DirectBufferEx RESPONSE_TERMINATOR = new UnsafeBufferEx("[DONE]".getBytes(UTF_8));
    private static final int STATUS_OK = 200;
    private static final int STATUS_MULTIPLE_CHOICES = 300;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int NO_ERROR = -1;
    private static final int STATUS_BAD_GATEWAY = 502;
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final int EVENT_HEADER_SIZE = Integer.BYTES + Byte.BYTES;

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
    private static final int KIND_DROPPED = 12;
    private static final int KIND_DIALECT = 13;
    private static final int KIND_MEMBER = 14;

    private static final int CONTENT_NONE = 0;
    private static final int CONTENT_STRING = 1;
    private static final int CONTENT_ARRAY = 2;
    private static final int CONTENT_CLOSED = 3;

    private static final int NO_MESSAGE = -2;
    private static final String BLOCK_LOGPROBS = "logprobs";
    private static final int TOP_LEVEL = -1;
    private static final int NOT_HELD = -1;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_SLACK = 256;
    private static final int CONVERT_THRESHOLD = ENCODE_SLACK + ESCAPE_EXPANSION * 1024;

    private static final byte[] MODEL_END = "\"".getBytes(UTF_8);
    private static final byte[] MEMBER_NAME_END = "\":".getBytes(UTF_8);
    private static final byte[] MESSAGES_REGION = "\"messages\":[".getBytes(UTF_8);
    private static final byte[] MESSAGES_EMPTY = "\"messages\":[]".getBytes(UTF_8);
    private static final byte[] MODEL_MEMBER = "\"model\":\"".getBytes(UTF_8);
    private static final byte[] MESSAGE_START = "{\"role\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALL_ID = ",\"tool_call_id\":\"".getBytes(UTF_8);
    private static final byte[] IMAGE_URL_START =
        "{\"type\":\"image_url\",\"image_url\":{\"url\":\"".getBytes(UTF_8);
    private static final byte[] FILE_DATA_START =
        "{\"type\":\"file\",\"file\":{\"file_data\":\"".getBytes(UTF_8);
    private static final byte[] FILE_ID_START =
        "{\"type\":\"file\",\"file\":{\"file_id\":\"".getBytes(UTF_8);
    private static final byte[] DATA_URL_START = "data:".getBytes(UTF_8);
    private static final byte[] BASE64_MARKER = ";base64,".getBytes(UTF_8);
    private static final byte[] SOURCE_END = "}}".getBytes(UTF_8);
    private static final byte[] STRING_SOURCE_END = "\"}}".getBytes(UTF_8);
    private static final byte[] CONTENT_MEMBER = ",\"content\":".getBytes(UTF_8);
    private static final byte[] CONTENT_STRING_START = ",\"content\":\"".getBytes(UTF_8);
    private static final byte[] CONTENT_ARRAY_START = ",\"content\":[".getBytes(UTF_8);
    private static final byte[] CONTENT_NULL = ",\"content\":null".getBytes(UTF_8);
    private static final byte[] CONTENT_ARRAY_TEXT_START = "[{\"type\":\"text\",\"text\":".getBytes(UTF_8);
    private static final byte[] PART_TEXT_START = "{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] PART_TEXT_END = "\"}".getBytes(UTF_8);
    private static final byte[] REFUSAL_START = ",\"refusal\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALLS_START = ",\"tool_calls\":[".getBytes(UTF_8);
    private static final byte[] TOOLS_START = "\"tools\":[".getBytes(UTF_8);

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();
    private final OctetsFW extensionRO = new OctetsFW();
    private final LlmSectionAttributes sectionAttributes = new LlmSectionAttributes();

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
    private final LlmEndExFW.Builder llmEndExRW = new LlmEndExFW.Builder();
    private final LlmAbortExFW.Builder llmAbortExRW = new LlmAbortExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final Matcher contentTypeMatcher;
    private final MutableDirectBufferEx convertBuffer;
    private final MutableDirectBufferEx copyBuffer;
    private final MutableDirectBufferEx transformBuffer;
    private final UnsafeBufferEx comparisonRO;
    private final DirectBufferEx emptyBufferRO;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
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
        this.contentTypeMatcher = CONTENT_TYPE.matcher("");
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.convertBuffer = new UnsafeBufferEx(new byte[encodeMax]);
        this.copyBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.transformBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.comparisonRO = new UnsafeBufferEx(new byte[0]);
        this.emptyBufferRO = new UnsafeBufferEx(new byte[0]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
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
    public boolean detect(
        JsonEnvelope headers)
    {
        return false;
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

        if (route != null && binding.options.server != null && llmBeginEx != null)
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
                llmBeginEx.model().asString(),
                llmBeginEx.dialect().asString())::onAppMessage;
        }

        return newStream;
    }

    private static String header(
        HttpBeginExFW httpBeginEx,
        String name)
    {
        final HttpHeaderFW header = httpBeginEx != null
            ? httpBeginEx.headers().matchFirst(h -> name.equals(h.name().asString()))
            : null;
        return header != null ? header.value().asString() : null;
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

    private static boolean translatable(
        int kind)
    {
        return kind == KIND_USER_IMAGE ||
            kind == KIND_USER_DOCUMENT ||
            kind == KIND_TOOL_CALL ||
            kind == KIND_TOOL_DEFINITION;
    }

    private static boolean media(
        int kind)
    {
        return kind == KIND_USER_IMAGE || kind == KIND_USER_DOCUMENT;
    }

    private static int sectionKind(
        LlmSectionType type)
    {
        return switch (type)
        {
        case SYSTEM_INSTRUCTION -> KIND_SYSTEM_INSTRUCTION;
        case USER_TEXT -> KIND_USER_TEXT;
        case USER_IMAGE -> KIND_USER_IMAGE;
        case USER_DOCUMENT -> KIND_USER_DOCUMENT;
        case USER_AUDIO -> KIND_USER_AUDIO;
        case ASSISTANT_TEXT -> KIND_ASSISTANT_TEXT;
        case ASSISTANT_REFUSAL -> KIND_ASSISTANT_REFUSAL;
        case TOOL_DEFINITION -> KIND_TOOL_DEFINITION;
        case TOOL_CALL -> KIND_TOOL_CALL;
        case TOOL_RESULT -> KIND_TOOL_RESULT;
        case ASSISTANT_REASONING, SERVER_TOOL_RESULT, CITATIONS -> KIND_DROPPED;
        case UNKNOWN -> KIND_UNKNOWN;
        default -> 0;
        };
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
        private final String dialect;
        private final LlmOpenaiHttpStream http;
        private final LlmOpenaiResponseDecoder responseDecoder;
        private final ReplyOutput replyOutput;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;
        private boolean rejected;
        private int appKind;
        private boolean logprobsBlock;


        private LlmOpenaiStream(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long authorization,
            long affinity,
            LlmBindingConfig binding,
            String model,
            String dialect)
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
            this.dialect = dialect;
            this.replyOutput = new ReplyOutput();
            this.responseDecoder = new LlmOpenaiResponseDecoder(replyOutput, decodeMax);
            this.http = new LlmOpenaiHttpStream(this);
        }

        private int replyCredit()
        {
            return replyOutput.muted
                ? Integer.MAX_VALUE
                : Math.max(0, replyMax - (int) (replySeq - replyAck)) / (1 + replyPad);
        }

        private boolean replyAvailable()
        {
            return !responseDecoder.begun() || replyCredit() > 0;
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
            case FlushFW.TYPE_ID:
                onAppFlush(flushRO.wrap(buffer, index, index + length));
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

                    appKind = llmDataEx != null ? llmDataEx.kind() : LlmDataExFW.KIND_SECTION;

                    switch (appKind)
                    {
                    case LlmDataExFW.KIND_BLOCK:
                        final String block = llmDataEx.block().block().asString();

                        logprobsBlock = NAME.equals(dialect) && BLOCK_LOGPROBS.equals(block);
                        http.doEncodeDialectBegin(block);
                        break;
                    case LlmDataExFW.KIND_SECTION:
                        final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;
                        sectionAttributes.readFrom(section);

                        final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                        http.doEncodeSectionBegin(
                            sectionKind(type),
                            section != null ? section.message() : NO_MESSAGE,
                            sectionAttributes.toolCallId());
                        break;
                    case LlmDataExFW.KIND_BEFORE:
                        final LlmDialectBeforeBlockDataExFW before = llmDataEx.before();
                        sectionAttributes.readFrom(before);

                        http.doEncodeMemberBegin(true,
                            sectionKind(before.type().get()),
                            before.message(),
                            sectionAttributes.toolCallId(),
                            before.block().asString(),
                            before.owner().get() == LlmBlockOwner.CONTAINER);
                        break;
                    case LlmDataExFW.KIND_AFTER:
                        final LlmDialectAfterBlockDataExFW after = llmDataEx.after();

                        http.doEncodeMemberBegin(false, 0, NO_MESSAGE, null,
                            after.block().asString(),
                            after.owner().get() == LlmBlockOwner.CONTAINER);
                        break;
                    default:
                        break;
                    }
                }

                final OctetsFW payload = data.payload();
                final boolean fin = Flags.hasFin(flags);

                if (payload != null && payload.sizeof() > 0)
                {
                    if (logprobsBlock)
                    {
                        responseDecoder.logprobs(payload.buffer().getByte(payload.offset()) == 't');
                        logprobsBlock = false;
                    }

                    http.doEncodeSectionData(payload.buffer(), payload.offset(), payload.sizeof(), fin);
                }
                else if (fin && http.sectionStreaming)
                {
                    http.doEncodeSectionData(EMPTY_OCTETS.buffer(), 0, 0, true);
                }

                if (fin)
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

        private void onAppFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();

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

            http.decodeNet(traceId);
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
            http.cleanupStageSlot();
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

        private void doAppBeginSections(
            long traceId)
        {
            if (!LlmState.replyOpening(state))
            {
                doAppBegin(traceId, responseDecoder.model(), responseDecoder.id());
            }
        }

        private void doAppBegin(
            long traceId,
            String servedModel,
            String servedId)
        {
            state = LlmState.openingReply(state);

            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(NAME);

            if (servedModel != null)
            {
                builder.model(servedModel);
            }

            if (servedId != null)
            {
                builder.id(servedId);
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
            state = LlmState.openReply(state);
        }

        private void doAppFrame(
            long traceId,
            int flags,
            String block,
            LlmBlockOwner after,
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            boolean follow,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final OctetsFW extension;

            if (Flags.hasInit(flags))
            {
                final LlmDataExFW.Builder dataExBuilder = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId);

                if (after != null)
                {
                    dataExBuilder.after(a -> a.owner(o -> o.set(after)).block(block));
                }
                else if (block != null)
                {
                    dataExBuilder.block(d -> d.block(block));
                }
                else
                {
                    dataExBuilder.section(x ->
                    {
                        x.type(t -> t.set(type)).message(message);

                        if (toolName != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_NAME).value(toolName));
                        }

                        if (toolCallId != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_CALL_ID).value(toolCallId));
                        }

                        if (follow)
                        {
                            x.follow(f -> f.set(LlmFollow.AFTER_BLOCK));
                        }
                    });
                }

                final LlmDataExFW llmDataEx = dataExBuilder.build();
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

        private final class ReplyOutput implements LlmOpenaiResponseDecoder.Output
        {
            private boolean muted;

            @Override
            public int credit()
            {
                return replyCredit();
            }

            @Override
            public void begin()
            {
                if (!muted)
                {
                    doAppBeginSections(http.decodeTraceId);
                }
            }

            @Override
            public void frame(
                LlmSectionType type,
                int message,
                String toolCallId,
                String toolName,
                boolean follow,
                int flags,
                DirectBufferEx buffer,
                int offset,
                int length)
            {
                if (!muted)
                {
                    doAppFrame(http.decodeTraceId, flags, null, null, type, message, toolCallId, toolName, follow,
                        buffer, offset, length);
                }
            }

            @Override
            public void block(
                String block,
                int flags,
                DirectBufferEx buffer,
                int offset,
                int length)
            {
                if (!muted)
                {
                    doAppFrame(http.decodeTraceId, flags, block, null, null, NO_MESSAGE, null, null, false,
                        buffer, offset, length);
                }
            }

            @Override
            public void after(
                String block,
                boolean container,
                int flags,
                DirectBufferEx buffer,
                int offset,
                int length)
            {
                if (!muted)
                {
                    doAppFrame(http.decodeTraceId, flags, block, container ? LlmBlockOwner.CONTAINER : LlmBlockOwner.SECTION,
                        null, NO_MESSAGE, null, null, false, buffer, offset, length);
                }
            }
        }

        private void muteReply()
        {
            replyOutput.muted = true;
        }

        private void doAppEnd(
            long traceId)
        {
            state = LlmState.closingReply(state);

            if (!LlmState.replyClosed(state))
            {
                if (responseDecoder.failed())
                {
                    doAppAbort(traceId);
                }
                else
                {
                    responseDecoder.finish();
                    doAppBeginSections(traceId);

                    state = LlmState.closeReply(state);

                    final LlmEndExFW llmEndEx = llmEndExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId)
                        .usage(u -> responseDecoder.populateUsage(u))
                        .finishReason(responseDecoder.finishReason())
                        .build();

                    final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(replyId)
                        .sequence(replySeq)
                        .acknowledge(replyAck)
                        .maximum(replyMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .extension(llmEndEx.buffer(), llmEndEx.offset(), llmEndEx.sizeof())
                        .build();

                    app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                }
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (!LlmState.replyClosed(state))
            {
                doAppBeginSections(traceId);

                state = LlmState.closeReply(state);

                final LlmAbortExFW llmAbortEx = llmAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .usage(u -> responseDecoder.populateUsage(u))
                    .error(e -> responseDecoder.populateError(e))
                    .build();

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
                rejected = true;

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
            http.cleanupStageSlot();
        }
    }

    private final class LlmOpenaiHttpStream
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
        private boolean sectionsStarted;

        private int messageCount;
        private int messageIndex = NO_MESSAGE;
        private boolean membersStarted;
        private boolean modelWritten;
        private boolean messagesOpened;
        private boolean messagesClosed;
        private boolean toolsOpened;
        private boolean toolsClosed;
        private boolean translated;
        private boolean sectionStreaming;
        private boolean mediaDropQuote;
        private LlmSectionPipeline sectionPipeline;
        private LlmSectionPipeline.Output sectionOutput;
        private LlmSectionPipeline mediaDataPipeline;
        private LlmSectionPipeline mediaUrlPipeline;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline toolDefinitionPipeline;
        private final LlmSectionPipeline.Output mediaOutput = this::putMedia;
        private final LlmSectionPipeline.Output toolOutput = this::put;
        private String messageRole;
        private String messageToolId;
        private boolean toolCallsOpened;
        private boolean refusalWritten;
        private int contentState;
        private int sectionKind;
        private boolean sectionOpen;
        private boolean sectionArrayPart;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int stageSlot = NO_SLOT;
        private int stageSlotOffset;
        private boolean stageLast;
        private boolean framing;
        private boolean errorJson;
        private LlmErrorBodyDecoder errorBody;
        private int errorStatus = NO_ERROR;
        private boolean errorBodyDropped;
        private long decodeTraceId;
        private long pendingEndTraceId;
        private boolean responseStarted;

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
            putByte('{');

            if (!NAME.equals(stream.dialect))
            {
                putModelMember();
            }

            encodeHold = 0;
        }

        private void startMember()
        {
            if (membersStarted)
            {
                putByte(',');
            }

            membersStarted = true;
        }

        private void putModelMember()
        {
            startMember();
            put(MODEL_MEMBER);
            putEscaped(stream.model);
            put(MODEL_END);
            modelWritten = true;
        }

        private void doEncodeDialectBegin(
            String block)
        {
            sectionOpen = false;
            sectionArrayPart = false;
            translated = false;

            if (NAME.equals(stream.dialect))
            {
                sectionKind = KIND_DIALECT;
                closeMessage();

                if (messagesOpened && !messagesClosed)
                {
                    putByte(']');
                    messagesClosed = true;
                }

                if (toolsOpened && !toolsClosed)
                {
                    putByte(']');
                    toolsClosed = true;
                }

                startMember();
                putByte('"');
                putEscaped(block);
                put(MEMBER_NAME_END);
                modelWritten |= MODEL.equals(block);
            }
            else
            {
                sectionKind = KIND_DROPPED;
            }
        }

        private void doEncodeMemberBegin(
            boolean before,
            int kind,
            int message,
            String extension,
            String block,
            boolean container)
        {
            sectionOpen = false;
            sectionArrayPart = false;
            translated = false;

            if (!NAME.equals(stream.dialect) || !container ||
                before && (kind == 0 || kind == KIND_TOOL_DEFINITION || kind == KIND_DROPPED || kind == KIND_UNKNOWN))
            {
                sectionKind = KIND_DROPPED;
            }
            else
            {
                sectionKind = KIND_MEMBER;

                if (!sectionsStarted)
                {
                    sectionsStarted = true;
                    encodeHold = NOT_HELD;
                }

                if (before && messagesClosed || !before && messageIndex == NO_MESSAGE)
                {
                    encodeFailed = true;
                }
                else if (before)
                {
                    ensureMessage(kind, message, extension);
                }
                else
                {
                    closeContent(false);

                    if (toolCallsOpened)
                    {
                        putByte(']');
                        toolCallsOpened = false;
                    }
                }

                if (!encodeFailed)
                {
                    putByte(',');
                    putByte('"');
                    putEscaped(block);
                    put(MEMBER_NAME_END);
                }
            }
        }

        private void doEncodeSectionBegin(
            int kind,
            int message,
            String extension)
        {
            sectionKind = kind;
            sectionOpen = false;
            sectionArrayPart = false;
            translated = !NAME.equals(stream.dialect) && translatable(kind);

            if (!sectionsStarted && kind != KIND_DROPPED)
            {
                sectionsStarted = true;
                encodeHold = NOT_HELD;
            }

            if (kind == KIND_DROPPED)
            {
                sectionKind = KIND_DROPPED;
            }
            else if (kind == KIND_UNKNOWN && !NAME.equals(stream.dialect))
            {
                encodeFailed = true;
            }
            else if (kind == KIND_TOOL_DEFINITION)
            {
                beginToolDefinition();
            }
            else if (kind == 0 || messagesClosed)
            {
                encodeFailed = true;
            }
            else if (kind == KIND_SYSTEM_INSTRUCTION && message == TOP_LEVEL && messageCount > 0)
            {
                encodeFailed = true;
            }
            else
            {
                beginMessageSection(kind, message, extension);
            }

            if (translated && !encodeFailed)
            {
                beginTranslated(kind);
            }
        }

        private void beginTranslated(
            int kind)
        {
            if (media(kind))
            {
                beginMedia(kind);
            }
            else if (kind == KIND_TOOL_CALL)
            {
                if (toolCallPipeline == null)
                {
                    toolCallPipeline = new LlmSectionPipeline(new LlmAnthropicToolCallTransform(), transformBuffer);
                }

                beginSectionStreaming(toolCallPipeline, toolOutput);
            }
            else
            {
                if (toolDefinitionPipeline == null)
                {
                    toolDefinitionPipeline = new LlmSectionPipeline(new LlmAnthropicToolDefinitionTransform(),
                        transformBuffer);
                }

                beginSectionStreaming(toolDefinitionPipeline, toolOutput);
            }
        }

        private void beginMedia(
            int kind)
        {
            final String source = sectionAttributes.source();
            final String mediaType = sectionAttributes.mediaType();
            final String refId = sectionAttributes.refId();

            if (LlmSectionAttributes.SOURCE_INLINE.equals(source) && mediaType != null)
            {
                if (mediaDataPipeline == null)
                {
                    mediaDataPipeline = new LlmSectionPipeline(new LlmAnthropicMediaDataTransform(), transformBuffer);
                }

                put(kind == KIND_USER_IMAGE ? IMAGE_URL_START : FILE_DATA_START);
                put(DATA_URL_START);
                putEscaped(mediaType);
                put(BASE64_MARKER);

                beginMediaStreaming(mediaDataPipeline);
            }
            else if (kind == KIND_USER_IMAGE && LlmSectionAttributes.SOURCE_URL.equals(source))
            {
                if (mediaUrlPipeline == null)
                {
                    mediaUrlPipeline = new LlmSectionPipeline(new LlmAnthropicMediaUrlTransform(), transformBuffer);
                }

                put(IMAGE_URL_START);

                beginMediaStreaming(mediaUrlPipeline);
            }
            else if (kind == KIND_USER_DOCUMENT && LlmSectionAttributes.SOURCE_REFERENCE.equals(source) && refId != null)
            {
                put(FILE_ID_START);
                putEscaped(refId);
                put(STRING_SOURCE_END);
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void beginMediaStreaming(
            LlmSectionPipeline pipeline)
        {
            beginSectionStreaming(pipeline, mediaOutput);
            mediaDropQuote = true;
        }

        private void beginSectionStreaming(
            LlmSectionPipeline pipeline,
            LlmSectionPipeline.Output output)
        {
            sectionPipeline = pipeline;
            sectionOutput = output;
            sectionPipeline.begin(sectionAttributes);
            sectionStreaming = true;
        }

        private void putMedia(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (mediaDropQuote)
            {
                put(buffer, offset + 1, length - 1);
                mediaDropQuote = false;
            }
            else
            {
                put(buffer, offset, length);
            }
        }

        private void beginToolDefinition()
        {
            closeMessage();

            if (messagesOpened && !messagesClosed)
            {
                putByte(']');
                messagesClosed = true;
            }

            if (toolsClosed)
            {
                encodeFailed = true;
            }
            else
            {
                if (toolsOpened)
                {
                    putByte(',');
                }
                else
                {
                    startMember();
                    put(TOOLS_START);
                }
                toolsOpened = true;
            }
        }

        private void beginMessageSection(
            int kind,
            int message,
            String extension)
        {
            ensureMessage(kind, message, extension);

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

        private void ensureMessage(
            int kind,
            int message,
            String extension)
        {
            if (toolsOpened && !toolsClosed)
            {
                putByte(']');
                toolsClosed = true;
            }

            if (!messagesOpened)
            {
                startMember();
                put(MESSAGES_REGION);
                messagesOpened = true;
            }

            final String role = kind == KIND_UNKNOWN && messageRole != null ? messageRole : role(kind);

            if (message != messageIndex || !role.equals(messageRole) || kind == KIND_TOOL_RESULT && !extensionMatches(extension))
            {
                closeMessage();
                openMessage(kind, role, message, extension);
            }
        }

        private boolean extensionMatches(
            String extension)
        {
            return extension != null && extension.equals(messageToolId);
        }

        private void doEncodeSectionData(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            if (sectionStreaming)
            {
                if (!sectionPipeline.feed(buffer, offset, length, fin, sectionOutput))
                {
                    encodeFailed = true;
                }
            }
            else if (!translated && sectionKind != 0 && sectionKind != KIND_DROPPED)
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
            if (sectionStreaming)
            {
                if (media(sectionKind))
                {
                    put(SOURCE_END);
                }

                sectionStreaming = false;
            }

            translated = false;

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
            if (!LlmState.initialClosing(state) && !LlmState.initialClosed(state))
            {
                closeMessage();

                if (toolsOpened && !toolsClosed)
                {
                    putByte(']');
                    toolsClosed = true;
                }

                if (!messagesOpened)
                {
                    startMember();
                    put(MESSAGES_EMPTY);
                }
                else if (!messagesClosed)
                {
                    putByte(']');
                    messagesClosed = true;
                }

                if (!modelWritten)
                {
                    putModelMember();
                }

                putByte('}');
                encodeHold = NOT_HELD;
                state = LlmState.closingInitial(state);
            }
        }

        private void openMessage(
            int kind,
            String role,
            int message,
            String extension)
        {
            if (messageCount++ > 0)
            {
                putByte(',');
            }

            put(MESSAGE_START);
            put(role.getBytes(UTF_8));
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
            messageRole = role;
            messageToolId = kind == KIND_TOOL_RESULT ? extension : null;
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
                messageRole = null;
                messageToolId = null;
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
            state = LlmState.openReply(state);

            final String responseStatus = header(httpBeginEx, HEADER_STATUS);
            final String responseContentType = header(httpBeginEx, HEADER_CONTENT_TYPE);
            final String mediaType = mediaType(responseContentType);
            final int status = responseStatus != null ? Integer.parseInt(responseStatus) : STATUS_OK;
            final boolean declared = mediaType != null && RESPONSE_CONTENT_TYPES.contains(mediaType);
            final boolean eventStream = CONTENT_TYPE_EVENT_STREAM.equals(mediaType);

            if (stream.rejected)
            {
                doNetReset(traceId);
            }
            else if (status < STATUS_OK || status >= STATUS_MULTIPLE_CHOICES)
            {
                errorStatus = status;
                framing = eventStream;
                errorBody = framing ? new LlmErrorBodyDecoder() : null;
                errorJson = declared && !framing;
                doNetWindow(traceId);
            }
            else if (!declared)
            {
                cleanupNet(traceId);
            }
            else
            {
                framing = eventStream;

                doNetWindow(traceId);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + data.reserved();

            DirectBufferEx buffer = payload != null ? payload.buffer() : emptyBufferRO;
            int offset = payload != null ? payload.offset() : 0;
            int limit = payload != null ? payload.limit() : 0;

            if (framing)
            {
                onNetEvent(traceId, buffer, offset, limit, Flags.hasFin(data.flags()));
                doNetWindow(traceId);
            }
            else if (errorStatus != NO_ERROR)
            {
                appendErrorBody(buffer, offset, limit);
                doNetWindow(traceId);
            }
            else if (decodeSlot != NO_SLOT && decodeSlotOffset + (limit - offset) > decodeMax)
            {
                cleanupNet(traceId);
            }
            else
            {
                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                }

                decodeNet(traceId, buffer, offset, limit);

                doNetWindow(traceId);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = LlmState.closingReply(state);

            pendingEndTraceId = traceId;

            if (errorStatus != NO_ERROR)
            {
                state = LlmState.closeReply(state);
                final LlmErrorBodyDecoder errorDecoder = decodeErrorBody();
                doNetAbort(traceId);
                stream.doAppReset(traceId, errorStatus, errorDecoder.type(), errorDecoder.message());
            }
            else
            {
                if (framing)
                {
                    drainEvents(traceId);
                }
                else if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    decodeNet(traceId, slotBuffer, 0, decodeSlotOffset);
                }
                else if (responseStarted)
                {
                    decodeNet(traceId, emptyBufferRO, 0, 0);
                }

                if (decodeSlot == NO_SLOT && stageSlot == NO_SLOT)
                {
                    doAppEnd(pendingEndTraceId);
                }
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            if (errorStatus == NO_ERROR)
            {
                stream.muteReply();
                decodeTraceId = traceId;

                if (stageSlot != NO_SLOT)
                {
                    decodeStaged();
                }

                if (framing)
                {
                    drainEvents(traceId);
                }
                else if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    decodeNet(traceId, slotBuffer, 0, decodeSlotOffset);
                }
                else if (responseStarted)
                {
                    decodeNet(traceId, emptyBufferRO, 0, 0);
                }
            }

            cleanupDecodeSlot();

            if (errorStatus != NO_ERROR)
            {
                doNetAbort(traceId);
                stream.doAppReset(traceId, errorStatus, null, null);
            }
            else
            {
                stream.doAppAbort(traceId);
            }

            resetDecodeState();
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
            cleanupEncodeSlot();
            cleanupDecodeSlot();
            resetDecodeState();

            stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            stream.doAppChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void decodeNet(
            long traceId)
        {
            decodeTraceId = traceId;

            if (stageSlot != NO_SLOT && stream.replyCredit() > 0)
            {
                decodeStaged();
            }

            if (framing)
            {
                drainEvents(traceId);

                if (!LlmState.replyClosing(state))
                {
                    doNetWindow(traceId);
                }
                else if (decodeSlot == NO_SLOT && stageSlot == NO_SLOT)
                {
                    doAppEnd(pendingEndTraceId);
                }
            }
            else if (decodeSlot != NO_SLOT && stageSlot == NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                decodeNet(traceId, slotBuffer, 0, decodeSlotOffset);

                doNetWindow(traceId);
            }
            else if (decodeSlot == NO_SLOT && stageSlot == NO_SLOT && LlmState.replyClosing(state))
            {
                doAppEnd(pendingEndTraceId);
            }
        }

        private void decodeNet(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            decodeTraceId = traceId;

            final int progress = decodeJsonSections(buffer, offset, limit);

            if (progress < limit)
            {
                final int remaining = limit - progress;

                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId);
                }
                else
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(0, buffer, progress, remaining);
                    decodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupDecodeSlot();

                if (stageSlot == NO_SLOT && LlmState.replyClosing(state))
                {
                    doAppEnd(pendingEndTraceId);
                }
            }
        }

        private int decodeJsonSections(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final LlmOpenaiResponseDecoder responseDecoder = stream.responseDecoder;
            final boolean last = LlmState.replyClosing(state);

            int progress = responseDecoder.decode(buffer, offset, limit, last);

            responseStarted = !responseDecoder.completed();

            if (responseDecoder.rejected())
            {
                responseDecoder.reset();
                cleanupNet(decodeTraceId);
                progress = limit;
            }

            return progress;
        }

        private void onNetEvent(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final int length = limit - offset;

            decodeTraceId = traceId;

            if (errorStatus != NO_ERROR)
            {
                errorBody.data(buffer, offset, length, last);

                if (last)
                {
                    errorBody.flush();
                }
            }
            else if (decodeSlot == NO_SLOT && eventAvailable())
            {
                forwardResponseSections(buffer, offset, length, last);
            }
            else
            {
                enqueueEvent(traceId, buffer, offset, length, last);
            }
        }

        private boolean eventAvailable()
        {
            return stageSlot == NO_SLOT ? stream.replyAvailable() : !stageLast;
        }

        private void enqueueEvent(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            if (decodeSlot == NO_SLOT || decodeSlotOffset + EVENT_HEADER_SIZE + length > decodeMax)
            {
                cleanupNet(traceId);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                slotBuffer.putInt(decodeSlotOffset, length);
                slotBuffer.putByte(decodeSlotOffset + Integer.BYTES, (byte) (last ? 1 : 0));
                slotBuffer.putBytes(decodeSlotOffset + EVENT_HEADER_SIZE, buffer, offset, length);
                decodeSlotOffset += EVENT_HEADER_SIZE + length;
            }
        }

        private void drainEvents(
            long traceId)
        {
            decodeTraceId = traceId;

            int progress = 0;

            while (decodeSlot != NO_SLOT && progress < decodeSlotOffset && eventAvailable())
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                final int length = slotBuffer.getInt(progress);
                final boolean last = slotBuffer.getByte(progress + Integer.BYTES) != 0;

                progress += EVENT_HEADER_SIZE;
                forwardResponseSections(slotBuffer, progress, length, last);
                progress += length;
            }

            if (decodeSlot != NO_SLOT)
            {
                final int remaining = decodeSlotOffset - progress;

                if (remaining > 0)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    copyBuffer.putBytes(0, slotBuffer, progress, remaining);
                    slotBuffer.putBytes(0, copyBuffer, 0, remaining);
                    decodeSlotOffset = remaining;
                }
                else
                {
                    cleanupDecodeSlot();
                }
            }
        }

        private boolean matchesTerminator(
            DirectBuffer buffer,
            int offset,
            int length,
            DirectBufferEx terminator)
        {
            boolean matches = terminator != null;
            if (matches)
            {
                comparisonRO.wrap((DirectBufferEx) buffer, offset, length);
                matches = comparisonRO.equals(terminator);
            }
            return matches;
        }

        private void forwardResponseSections(
            DirectBuffer buffer,
            int offset,
            int length,
            boolean last)
        {
            if ((length > 0 || last) && !matchesTerminator(buffer, offset, length, RESPONSE_TERMINATOR))
            {
                if (stageSlot != NO_SLOT)
                {
                    if (stageSlotOffset + length > decodeMax)
                    {
                        cleanupStageSlot();
                        cleanupNet(decodeTraceId);
                    }
                    else
                    {
                        decodePool.buffer(stageSlot).putBytes(stageSlotOffset, buffer, offset, length);
                        stageSlotOffset += length;
                        stageLast = last;
                        decodeStaged();
                    }
                }
                else
                {
                    decodeFragment((DirectBufferEx) buffer, offset, offset + length, last);
                }
            }
        }

        private void decodeStaged()
        {
            decodeFragment(decodePool.buffer(stageSlot), 0, stageSlotOffset, stageLast);
        }

        private void decodeFragment(
            DirectBufferEx source,
            int from,
            int to,
            boolean last)
        {
            final LlmOpenaiResponseDecoder responseDecoder = stream.responseDecoder;
            final int progress = responseDecoder.decode(source, from, to, last);

            if (responseDecoder.rejected())
            {
                responseDecoder.reset();
                cleanupStageSlot();
                cleanupNet(decodeTraceId);
            }
            else
            {
                restage(source, progress, to, last);

                if (last && stageSlot == NO_SLOT && responseDecoder.completed())
                {
                    responseDecoder.nextDocument();
                }
            }
        }

        private void restage(
            DirectBufferEx source,
            int progress,
            int limit,
            boolean last)
        {
            final int remaining = limit - progress;

            if (remaining > 0)
            {
                if (stageSlot == NO_SLOT)
                {
                    stageSlot = decodePool.acquire(replyId);
                }

                if (stageSlot == NO_SLOT || remaining > decodeMax)
                {
                    cleanupStageSlot();
                    cleanupNet(decodeTraceId);
                }
                else
                {
                    copyBuffer.putBytes(0, source, progress, remaining);
                    decodePool.buffer(stageSlot).putBytes(0, copyBuffer, 0, remaining);
                    stageSlotOffset = remaining;
                    stageLast = last;
                }
            }
            else
            {
                cleanupStageSlot();
            }
        }

        private void appendErrorBody(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (errorJson && !errorBodyDropped)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT || decodeSlotOffset + (limit - offset) > decodeMax)
                {
                    errorBodyDropped = true;
                    cleanupDecodeSlot();
                }
                else
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                }
            }
        }

        private LlmErrorBodyDecoder decodeErrorBody()
        {
            final LlmErrorBodyDecoder errorDecoder = errorBody != null ? errorBody : new LlmErrorBodyDecoder();

            if (decodeSlot != NO_SLOT)
            {
                errorDecoder.data(decodePool.buffer(decodeSlot), 0, decodeSlotOffset, true);
                errorDecoder.flush();
                cleanupDecodeSlot();
            }

            return errorDecoder;
        }

        private void doAppEnd(
            long traceId)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closeReply(state);
                stream.doAppEnd(traceId);
            }
        }

        private void cleanupNet(
            long traceId)
        {
            cleanupDecodeSlot();
            resetDecodeState();
            cleanupEncodeSlot();
            doNetReset(traceId);
            stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
        }

        private void resetDecodeState()
        {
            stream.responseDecoder.reset();
            cleanupStageSlot();
            responseStarted = false;
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
            final int buffered = errorStatus == NO_ERROR ? decodeSlotOffset + stageSlotOffset : 0;
            final long replyAckMax = replySeq - buffered;

            if (replyAckMax > replyAck)
            {
                replyAck = replyAckMax;
            }

            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(decodeMax - buffered)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .capabilities(framing ? FRAMING_CAPABILITIES_MASK : 0)
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

        private void cleanupStageSlot()
        {
            if (stageSlot != NO_SLOT)
            {
                decodePool.release(stageSlot);
                stageSlot = NO_SLOT;
                stageSlotOffset = 0;
            }
        }
    }

    private String mediaType(
        String contentType)
    {
        contentTypeMatcher.reset(contentType != null ? contentType : "");

        return contentTypeMatcher.matches() ? contentTypeMatcher.group(1) : null;
    }

    private final class LlmErrorBodyDecoder
    {
        private final LlmOpenaiResponseDecoder responseDecoder;

        private LlmErrorBodyDecoder()
        {
            this.responseDecoder = new LlmOpenaiResponseDecoder(new LlmOpenaiResponseDecoder.Output()
            {
                @Override
                public int credit()
                {
                    return Integer.MAX_VALUE;
                }

                @Override
                public void begin()
                {
                }

                @Override
                public void frame(
                    LlmSectionType type,
                    int message,
                    String toolCallId,
                    String toolName,
                    boolean follow,
                    int flags,
                    DirectBufferEx buffer,
                    int offset,
                    int length)
                {
                }

                @Override
                public void block(
                    String block,
                    int flags,
                    DirectBufferEx buffer,
                    int offset,
                    int length)
                {
                }

                @Override
                public void after(
                    String block,
                    boolean container,
                    int flags,
                    DirectBufferEx buffer,
                    int offset,
                    int length)
                {
                }
            }, decodeMax);
        }

        private String type()
        {
            responseDecoder.nextDocument();
            return responseDecoder.errorType();
        }

        private String message()
        {
            return responseDecoder.errorMessage();
        }

        private void data(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            responseDecoder.decode(buffer, offset, offset + length, last);
        }

        private void flush()
        {
            responseDecoder.reset();
        }
    }

    private static final class LlmOpenaiResponseDecoder
    {
        private static final int NONE = -1;
        private static final int REPLACEMENT_CHARACTER = 0xFFFD;
        private static final String LOGPROBS = "logprobs";
        private static final String FINISH_REASON = "finish_reason";
        private static final String COMPLETION = "chat.completion";
        private static final String MODEL = "model";
        private static final String USAGE = "usage";

        private final Output output;
        private final JsonParserEx parser;
        private final StringBuilder path;
        private final int[] pathLengthAt;
        private final int holdMax;

        private DirectBufferEx input;
        private int inputLimit;

        private boolean scanned;
        private boolean scanError;
        private boolean completed;
        private boolean rejected;

        private int depth;
        private String pendingKey;

        private int documentInputTokens = NONE;
        private int documentOutputTokens = NONE;
        private int documentTotalTokens = NONE;
        private int documentCacheReadTokens = NONE;
        private int documentReasoningTokens = NONE;
        private String documentErrorType;
        private String documentErrorMessage;
        private String documentId;
        private String documentModel;
        private String documentFinishReason;

        private int inputTokens = NONE;
        private int outputTokens = NONE;
        private int totalTokens = NONE;
        private int cacheReadTokens = NONE;
        private int reasoningTokens = NONE;
        private String errorType;
        private String errorMessage;
        private String id;
        private String model;
        private String finishReason;

        private int choiceIndex;
        private int toolIndex;
        private String toolId;
        private String toolName;
        private boolean toolArguments;

        private boolean sectionOpen;
        private boolean sectionStarted;
        private boolean sectionWritten;
        private boolean sectionAtomic;
        private boolean sectionFollow;
        private boolean followPending;
        private boolean logprobs;
        private LlmSectionType sectionType;
        private int sectionMessage;
        private int sectionTool;
        private String sectionToolId;
        private String sectionToolName;

        private char highSurrogate;
        private byte[] textBytes;
        private UnsafeBufferEx textBuffer;
        private byte[] heldBytes;
        private UnsafeBufferEx heldBuffer;
        private int heldLength;

        private boolean capturing;
        private boolean captureAfter;
        private boolean captureStarted;
        private boolean captureTrim;
        private int captureDepth;
        private int captureFrom;
        private String captureName;
        private boolean choiceMessage;
        private boolean completion;
        private boolean captureContainer;
        private boolean captureUsage;
        private String usageKey;
        private int usageDepth;
        private final StringBuilder captureValue;
        private final StringBuilder usagePath;
        private final int[] usageMark;
        private final UnsafeBufferEx nullBuffer;

        LlmOpenaiResponseDecoder(
            Output output,
            int holdMax)
        {
            this.output = output;
            this.captureValue = new StringBuilder();
            this.usagePath = new StringBuilder();
            this.usageMark = new int[8];
            this.nullBuffer = new UnsafeBufferEx("null".getBytes(UTF_8));
            this.holdMax = holdMax;
            this.parser = JsonEx.createParser();
            this.path = new StringBuilder();
            this.pathLengthAt = new int[16];
            this.textBytes = new byte[1024];
            this.textBuffer = new UnsafeBufferEx(textBytes);
            this.heldBytes = new byte[1024];
            this.heldBuffer = new UnsafeBufferEx(heldBytes);
            this.heldLength = NONE;
            this.sectionTool = NONE;
        }

        void logprobs(
            boolean requested)
        {
            this.logprobs = requested;
        }

        boolean failed()
        {
            return errorType != null || errorMessage != null;
        }

        boolean begun()
        {
            return scanned;
        }

        boolean rejected()
        {
            return rejected;
        }

        boolean completed()
        {
            return completed;
        }

        String errorType()
        {
            return errorType;
        }

        String errorMessage()
        {
            return errorMessage;
        }

        String id()
        {
            return documentId != null ? documentId : id;
        }

        String model()
        {
            return documentModel != null ? documentModel : model;
        }

        String finishReason()
        {
            final String reported = documentFinishReason != null ? documentFinishReason : finishReason;

            String neutral = reported;

            if (reported != null)
            {
                neutral = switch (reported)
                {
                case "tool_calls", "function_call" -> "tool-call";
                case "content_filter" -> "content-filter";
                default -> reported;
                };
            }

            return neutral;
        }

        void populateUsage(
            LlmUsageFW.Builder usage)
        {
            usage.inputTokens(inputTokens)
                .cacheWriteTokens(NONE)
                .cacheReadTokens(cacheReadTokens)
                .outputTokens(outputTokens)
                .reasoningTokens(reasoningTokens)
                .totalTokens(totalTokens);

            final JsonObjectBuilder builder = Json.createObjectBuilder();
            addIfPresent(builder, "input_tokens", inputTokens);
            addIfPresent(builder, "cache_read_tokens", cacheReadTokens);
            addIfPresent(builder, "output_tokens", outputTokens);
            addIfPresent(builder, "reasoning_tokens", reasoningTokens);
            addIfPresent(builder, "total_tokens", totalTokens);

            final JsonObject nativeUsage = builder.build();
            if (!nativeUsage.isEmpty())
            {
                usage.nativeUsage(nativeUsage.toString());
            }
        }

        void populateError(
            LlmErrorFW.Builder error)
        {
            error.status(NONE)
                .type(errorType)
                .message(errorMessage);
        }

        void finish()
        {
            closeSection();
        }

        void nextDocument()
        {
            onDocumentEnd();
            parser.reset();
            completed = false;
            depth = 0;
            path.setLength(0);
            pendingKey = null;
            capturing = false;
        }

        void reset()
        {
            nextDocument();
            scanned = false;
            rejected = false;
            sectionOpen = false;
            sectionStarted = false;
            sectionFollow = false;
            followPending = false;
            sectionTool = NONE;
            highSurrogate = 0;
            heldLength = NONE;
        }

        int decode(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            int progress = offset;

            try
            {
                if (completed)
                {
                    progress = limit;

                    for (int index = offset; index < limit; index++)
                    {
                        if (!isWhitespace(buffer.getByte(index)))
                        {
                            rejected = true;
                        }
                    }
                }
                else
                {
                    boolean ready = scanned;

                    if (!scanned)
                    {
                        final boolean found = scan(buffer, offset, limit, last);

                        if (found || last || limit - offset >= holdMax)
                        {
                            scanned = true;
                            ready = true;

                            if (!scanError && !rejected)
                            {
                                output.begin();
                            }
                        }
                    }

                    if (rejected)
                    {
                        progress = limit;
                    }
                    else if (ready)
                    {
                        progress = decodeAvailable(buffer, offset, limit, last);
                    }
                }
            }
            catch (JsonException ex)
            {
                rejected = true;
                progress = limit;
            }

            return progress;
        }

        private int decodeAvailable(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            int progress = offset;
            boolean advanced = true;

            while (advanced && !completed && !rejected)
            {
                int window = Math.min(limit - progress, Math.max(0, output.credit() - Math.max(heldLength, 0)));

                if (window == 0 && limit > progress && heldLength > 0 && output.credit() > 0)
                {
                    flushHeld();
                    window = Math.min(limit - progress, Math.max(0, output.credit() - Math.max(heldLength, 0)));
                }

                if (window > 0)
                {
                    final int next = decodeWindow(buffer, progress, progress + window, last && progress + window == limit);

                    advanced = next > progress && next < limit;
                    progress = next;
                }
                else
                {
                    if (limit == progress)
                    {
                        progress = decodeWindow(buffer, progress, progress, last);
                    }

                    advanced = false;
                }
            }

            return progress;
        }

        private void flushHeld()
        {
            if (heldLength != NONE)
            {
                write(heldBuffer, heldLength, false);
                heldLength = NONE;
            }
        }

        private boolean scan(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            parser.reset();
            parser.wrap(buffer, offset, limit, last);

            int scanDepth = 0;
            String scanKey = null;
            boolean scanChoices = false;
            boolean found = false;

            scanError = false;

            while (!found && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (scanChoices)
                {
                    rejected = event != JsonEvent.START_ARRAY;
                    found = true;
                }
                else
                {
                    switch (event)
                    {
                    case START_OBJECT:
                    case START_ARRAY:
                        scanDepth++;
                        scanKey = null;
                        break;
                    case END_OBJECT:
                    case END_ARRAY:
                        scanDepth--;
                        found = scanDepth == 0;
                        break;
                    case KEY_NAME:
                        if (parser.deferredBytes())
                        {
                            parser.consumed(0);
                        }
                        else if (scanDepth == 1)
                        {
                            scanKey = parser.getString();
                            scanError = "error".equals(scanKey);
                            scanChoices = "choices".equals(scanKey);
                            found = scanError;
                        }
                        break;
                    case VALUE_STRING:
                        if (parser.deferredBytes())
                        {
                            parser.consumed(parser.getStringView().length());
                        }
                        else if (scanDepth == 1 && scanKey != null)
                        {
                            switch (scanKey)
                            {
                            case "id":
                                documentId = parser.getString();
                                break;
                            case "model":
                                documentModel = parser.getString();
                                break;
                            default:
                                break;
                            }
                            found = documentId != null && documentModel != null;
                        }
                        break;
                    default:
                        break;
                    }
                }
            }

            parser.reset();

            return found;
        }

        private int decodeWindow(
            DirectBufferEx buffer,
            int offset,
            int windowLimit,
            boolean windowLast)
        {
            input = buffer;
            inputLimit = windowLimit;
            captureFrom = offset;

            parser.wrap(buffer, offset, windowLimit, windowLast);

            while (!completed && !rejected && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (capturing)
                {
                    onCapture(event);
                }
                else
                {
                    onEvent(event);
                }
            }

            if (capturing)
            {
                flushCapture(false);
            }

            int progress = position();

            if (completed)
            {
                while (progress < windowLimit && isWhitespace(buffer.getByte(progress)))
                {
                    progress++;
                }
            }

            return rejected ? windowLimit : progress;
        }

        private int position()
        {
            return inputLimit - parser.remaining();
        }

        private void onEvent(
            JsonEvent event)
        {
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                onContainerStart(event);
                break;
            case END_OBJECT:
            case END_ARRAY:
                onContainerEnd(event);
                break;
            case KEY_NAME:
                onKeyName();
                break;
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                onLeafValue(event);
                break;
            default:
                break;
            }
        }

        private void onContainerStart(
            JsonEvent event)
        {
            if (depth == 0 && event != JsonEvent.START_OBJECT)
            {
                rejected = true;
            }

            pathLengthAt[depth++] = path.length();

            if (pendingKey != null)
            {
                if (path.length() > 0)
                {
                    path.append('.');
                }
                path.append(pendingKey);
                pendingKey = null;
            }
            else if (event == JsonEvent.START_OBJECT)
            {
                if (pathIs("choices"))
                {
                    choiceIndex = 0;
                }
                else if (toolCalls())
                {
                    toolIndex = NONE;
                    toolId = null;
                    toolName = null;
                    toolArguments = false;
                }
            }
        }

        private void onContainerEnd(
            JsonEvent event)
        {
            if (event == JsonEvent.END_OBJECT && toolCalls() && !toolArguments && (toolId != null || toolName != null))
            {
                openTool();
                emit(0, !delta());
            }
            else if (event == JsonEvent.END_OBJECT && followPending && pathIs("choices"))
            {
                emitAfterEmpty();
            }

            path.setLength(pathLengthAt[--depth]);

            if (depth == 0)
            {
                completed = true;
                onDocumentEnd();
            }
        }

        private void onKeyName()
        {
            if (parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else
            {
                pendingKey = parser.getString();

                if (depth == 1 && (!canonical(pendingKey) || completion && carried(pendingKey)))
                {
                    beginCapture(pendingKey, false, false);
                }
                else if (depth == 3 && pathIs("choices"))
                {
                    onChoiceKey();
                }
                else if (depth == 4 && pathIs("choices.message") && completion && !messageCanonical(pendingKey))
                {
                    beginCapture(pendingKey, true, true);
                }
            }
        }

        private void onChoiceKey()
        {
            if (LOGPROBS.equals(pendingKey))
            {
                beginCapture(LOGPROBS, true, false);
            }
            else if ("message".equals(pendingKey))
            {
                choiceMessage = true;
            }
            else if (choiceMessage && (FINISH_REASON.equals(pendingKey) || completion && !"index".equals(pendingKey)))
            {
                beginCapture(pendingKey, true, false);
            }
        }

        private static boolean carried(
            String member)
        {
            return MODEL.equals(member) || USAGE.equals(member);
        }

        private static boolean messageCanonical(
            String member)
        {
            return switch (member)
            {
            case "role", "content", "refusal", "tool_calls" -> true;
            default -> false;
            };
        }

        private static boolean canonical(
            String member)
        {
            return switch (member)
            {
            case "id", "object", "model", "choices", "usage", "error" -> true;
            default -> false;
            };
        }

        private void onLeafValue(
            JsonEvent event)
        {
            if (pendingKey != null)
            {
                if (event == JsonEvent.VALUE_NULL && completion && pathIs("choices.message") &&
                    messageCanonical(pendingKey) && !"role".equals(pendingKey))
                {
                    closeSection();
                    output.after(pendingKey, true, Flags.COMPLETE, nullBuffer, 0, nullBuffer.capacity());
                }

                final boolean deferred = parser.deferredBytes();
                final int fieldAt = path.length();

                if (fieldAt > 0)
                {
                    path.append('.');
                }
                path.append(pendingKey);

                final String field = path.toString();

                path.setLength(fieldAt);

                if (event == JsonEvent.VALUE_STRING)
                {
                    onString(field, deferred);
                }
                else if (event == JsonEvent.VALUE_NUMBER)
                {
                    onNumber(field, deferred);
                }

                if ("choices".equals(field))
                {
                    rejected = true;
                }

                if (!deferred)
                {
                    pendingKey = null;
                }
            }
        }

        private void onString(
            String field,
            boolean deferred)
        {
            switch (field)
            {
            case "choices.delta.content":
            case "choices.message.content":
                onText(LlmSectionType.ASSISTANT_TEXT, deferred, field.startsWith("choices.message"));
                break;
            case "choices.delta.refusal":
            case "choices.message.refusal":
                onText(LlmSectionType.ASSISTANT_REFUSAL, deferred, field.startsWith("choices.message"));
                break;
            case "choices.delta.tool_calls.id":
            case "choices.message.tool_calls.id":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    toolId = parser.getString();
                }
                break;
            case "choices.delta.tool_calls.function.name":
            case "choices.message.tool_calls.function.name":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    toolName = parser.getString();
                }
                break;
            case "choices.delta.tool_calls.function.arguments":
            case "choices.message.tool_calls.function.arguments":
                onArguments(deferred, field.startsWith("choices.message"));
                break;
            case "error.type":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    documentErrorType = parser.getString();
                }
                break;
            case "error.message":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    documentErrorMessage = parser.getString();
                }
                break;
            case "object":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    completion = COMPLETION.equals(parser.getString());
                }
                break;
            case "id":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    documentId = parser.getString();
                }
                break;
            case "model":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    documentModel = parser.getString();
                }
                break;
            case "choices.finish_reason":
                if (deferred)
                {
                    parser.consumed(0);
                }
                else
                {
                    documentFinishReason = parser.getString();
                }
                break;
            default:
                parser.consumed(parser.getStringView().length());
                break;
            }
        }

        private void onNumber(
            String field,
            boolean deferred)
        {
            if (deferred)
            {
                parser.consumed(0);
            }
            else
            {
                switch (field)
                {
                case "choices.index":
                    choiceIndex = parser.getInt();
                    break;
                case "choices.delta.tool_calls.index":
                case "choices.message.tool_calls.index":
                    toolIndex = parser.getInt();
                    break;
                case "usage.prompt_tokens":
                    documentInputTokens = parser.getInt();
                    break;
                case "usage.completion_tokens":
                    documentOutputTokens = parser.getInt();
                    break;
                case "usage.total_tokens":
                    documentTotalTokens = parser.getInt();
                    break;
                case "usage.prompt_tokens_details.cached_tokens":
                    documentCacheReadTokens = parser.getInt();
                    break;
                case "usage.completion_tokens_details.reasoning_tokens":
                    documentReasoningTokens = parser.getInt();
                    break;
                default:
                    break;
                }
            }
        }

        private void onText(
            LlmSectionType type,
            boolean deferred,
            boolean atomic)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            if (length > 0 || highSurrogate != 0 || !deferred && atomic && sectionOpen)
            {
                if (!sectionOpen || type != sectionType || sectionMessage != choiceIndex)
                {
                    closeSection();
                    openSection(type, choiceIndex, null, null, NONE);
                    sectionFollow = logprobs && !atomic;
                }

                emit(encode(chars, !deferred), !deferred && (atomic || sectionFollow));
            }

            if (deferred)
            {
                parser.consumed(length);
            }
        }

        private void onArguments(
            boolean deferred,
            boolean atomic)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            toolArguments = true;

            if (!sectionOpen || !continuesTool())
            {
                openTool();

                if (length == 0 && (deferred || !atomic))
                {
                    emit(0, false);
                }
            }

            if (length > 0 || highSurrogate != 0 || !deferred && atomic && sectionOpen)
            {
                emit(encode(chars, !deferred), !deferred && atomic);
            }

            if (deferred)
            {
                parser.consumed(length);
            }
        }

        private boolean continuesTool()
        {
            return sectionType == LlmSectionType.TOOL_CALL &&
                sectionMessage == choiceIndex &&
                toolIndex != NONE &&
                sectionTool == toolIndex &&
                (toolId == null || toolId.equals(sectionToolId));
        }

        private void openTool()
        {
            if (!sectionOpen || !continuesTool())
            {
                closeSection();
                openSection(LlmSectionType.TOOL_CALL, choiceIndex, toolId, toolName, toolIndex);
            }
        }

        private void openSection(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            int tool)
        {
            sectionOpen = true;
            sectionStarted = false;
            sectionWritten = false;
            sectionAtomic = !delta();
            sectionFollow = false;
            sectionType = type;
            sectionMessage = message;
            sectionTool = tool;
            sectionToolId = toolCallId;
            sectionToolName = toolName;
        }

        private void closeSection()
        {
            if (sectionOpen && sectionStarted)
            {
                emit(0, true);
            }

            sectionOpen = false;
        }

        private void emit(
            int length,
            boolean fin)
        {
            if (sectionOpen)
            {
                sectionStarted = true;
                sectionOpen = !fin;

                if (sectionAtomic)
                {
                    emitAtomic(length, fin);
                }
                else
                {
                    write(textBuffer, length, fin);
                }
            }
        }

        private void emitAtomic(
            int length,
            boolean fin)
        {
            if (heldLength != NONE && fin && length == 0)
            {
                write(heldBuffer, heldLength, true);
                heldLength = NONE;
            }
            else
            {
                if (heldLength != NONE)
                {
                    write(heldBuffer, heldLength, false);
                    heldLength = NONE;
                }

                if (fin)
                {
                    write(textBuffer, length, true);
                }
                else
                {
                    if (heldBytes.length < length)
                    {
                        heldBytes = new byte[length];
                        heldBuffer = new UnsafeBufferEx(heldBytes);
                    }

                    textBuffer.getBytes(0, heldBytes, 0, length);
                    heldLength = length;
                }
            }
        }

        private void write(
            DirectBufferEx buffer,
            int length,
            boolean fin)
        {
            final int flags = sectionWritten ? Flags.NONE : Flags.INIT;

            sectionWritten = true;

            output.frame(
                sectionType,
                sectionMessage,
                sectionToolId,
                sectionToolName,
                sectionFollow,
                fin ? Flags.fin(flags) : flags,
                buffer,
                0,
                length);

            followPending = fin && sectionFollow;
        }

        private void emitAfterEmpty()
        {
            followPending = false;
            output.after(LOGPROBS, false, Flags.COMPLETE, textBuffer, 0, 0);
        }

        private void beginCapture(
            String name,
            boolean after,
            boolean container)
        {
            capturing = true;
            captureAfter = after;
            captureContainer = container;
            captureUsage = !after && USAGE.equals(name);
            usagePath.setLength(0);
            usagePath.append(USAGE);
            usageKey = null;
            usageDepth = 0;
            captureName = name;
            captureValue.setLength(0);
            captureStarted = false;
            captureTrim = true;
            captureDepth = 0;
            captureFrom = position();
            pendingKey = null;

            closeSection();
        }

        private void onCapture(
            JsonEvent event)
        {
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (captureUsage)
            {
                onUsage(event);
            }

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                captureDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                captureDepth--;
                break;
            case VALUE_STRING:
                if (deferred)
                {
                    parser.consumed(parser.getStringView().length());
                }
                break;
            default:
                break;
            }

            if (captureDepth == 0 && !deferred)
            {
                if (captureAfter && !completion && !captureStarted && event == JsonEvent.VALUE_NULL)
                {
                    capturing = false;

                    if (followPending)
                    {
                        emitAfterEmpty();
                    }
                }
                else
                {
                    flushCapture(true);
                    capturing = false;
                    followPending = followPending && !captureAfter;
                }
            }
        }

        private void flushCapture(
            boolean fin)
        {
            final int end = position();
            int from = captureFrom;

            if (captureTrim)
            {
                while (from < end && isSeparator(input.getByte(from)))
                {
                    from++;
                }

                captureTrim = from == end;
            }

            if (end > from || fin)
            {
                final int flags = captureStarted
                    ? fin ? Flags.FIN : Flags.NONE
                    : fin ? Flags.COMPLETE : Flags.INIT;

                captureStarted = !fin;

                if (captureAfter)
                {
                    if (FINISH_REASON.equals(captureName))
                    {
                        captureFinishReason(from, end, fin);
                    }

                    output.after(captureName, captureContainer, flags, input, from, end - from);
                }
                else
                {
                    output.block(captureName, flags, input, from, end - from);
                }
            }

            captureFrom = end;
        }

        private void onUsage(
            JsonEvent event)
        {
            switch (event)
            {
            case START_OBJECT:
                usageMark[usageDepth++] = usagePath.length();

                if (usageKey != null)
                {
                    usagePath.append('.').append(usageKey);
                    usageKey = null;
                }
                break;
            case END_OBJECT:
                usagePath.setLength(usageMark[--usageDepth]);
                break;
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    usageKey = parser.getString();
                }
                break;
            case VALUE_NUMBER:
                if (usageKey != null)
                {
                    final int length = usagePath.length();

                    usagePath.append('.').append(usageKey);
                    onNumber(usagePath.toString(), parser.deferredBytes());
                    usagePath.setLength(length);
                    usageKey = null;
                }
                break;
            case VALUE_STRING:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                usageKey = null;
                break;
            default:
                break;
            }
        }

        private void captureFinishReason(
            int from,
            int end,
            boolean fin)
        {
            for (int index = from; index < end; index++)
            {
                captureValue.append((char) input.getByte(index));
            }

            if (fin)
            {
                final String value = captureValue.toString().strip();

                if (value.length() > 1 && value.charAt(0) == '"')
                {
                    documentFinishReason = value.substring(1, value.length() - 1);
                }
            }
        }

        private void onDocumentEnd()
        {
            inputTokens = documentInputTokens != NONE ? documentInputTokens : inputTokens;
            outputTokens = documentOutputTokens != NONE ? documentOutputTokens : outputTokens;
            totalTokens = documentTotalTokens != NONE ? documentTotalTokens : totalTokens;
            cacheReadTokens = documentCacheReadTokens != NONE ? documentCacheReadTokens : cacheReadTokens;
            reasoningTokens = documentReasoningTokens != NONE ? documentReasoningTokens : reasoningTokens;
            errorType = documentErrorType != null ? documentErrorType : errorType;
            errorMessage = documentErrorMessage != null ? documentErrorMessage : errorMessage;
            id = documentId != null ? documentId : id;
            model = documentModel != null ? documentModel : model;
            finishReason = documentFinishReason != null ? documentFinishReason : finishReason;

            documentInputTokens = NONE;
            documentOutputTokens = NONE;
            documentTotalTokens = NONE;
            documentCacheReadTokens = NONE;
            documentReasoningTokens = NONE;
            documentErrorType = null;
            documentErrorMessage = null;
            documentId = null;
            documentModel = null;
            documentFinishReason = null;
            choiceMessage = false;
            completion = false;
        }

        private boolean pathIs(
            String value)
        {
            return path.length() == value.length() && path.indexOf(value) == 0;
        }

        private boolean toolCalls()
        {
            final int length = path.length();

            return length > 0 &&
                (path.indexOf("choices.delta.tool_calls") == 0 && length == "choices.delta.tool_calls".length() ||
                path.indexOf("choices.message.tool_calls") == 0 && length == "choices.message.tool_calls".length());
        }

        private boolean delta()
        {
            return path.indexOf("choices.delta") == 0;
        }

        private static boolean isSeparator(
            byte value)
        {
            return value == ':' || isWhitespace(value);
        }

        private static boolean isWhitespace(
            byte value)
        {
            return value == ' ' || value == '\t' || value == '\r' || value == '\n';
        }

        private static void addIfPresent(
            JsonObjectBuilder builder,
            String name,
            int value)
        {
            if (value != NONE)
            {
                builder.add(name, value);
            }
        }

        private int encode(
            CharSequence chars,
            boolean last)
        {
            final int length = chars.length();

            if (textBytes.length < length * 3 + 8)
            {
                textBytes = new byte[length * 3 + 8];
                textBuffer = new UnsafeBufferEx(textBytes);
            }

            int position = 0;
            int index = 0;

            if (highSurrogate != 0 && length > 0)
            {
                final char c = chars.charAt(0);
                if (Character.isLowSurrogate(c))
                {
                    position = put(Character.toCodePoint(highSurrogate, c), position);
                    index = 1;
                }
                else
                {
                    position = put(REPLACEMENT_CHARACTER, position);
                }
                highSurrogate = 0;
            }

            while (index < length)
            {
                final char c = chars.charAt(index++);

                if (Character.isHighSurrogate(c))
                {
                    if (index < length)
                    {
                        final char d = chars.charAt(index);
                        if (Character.isLowSurrogate(d))
                        {
                            position = put(Character.toCodePoint(c, d), position);
                            index++;
                        }
                        else
                        {
                            position = put(REPLACEMENT_CHARACTER, position);
                        }
                    }
                    else if (last)
                    {
                        position = put(REPLACEMENT_CHARACTER, position);
                    }
                    else
                    {
                        highSurrogate = c;
                    }
                }
                else if (Character.isLowSurrogate(c))
                {
                    position = put(REPLACEMENT_CHARACTER, position);
                }
                else
                {
                    position = put(c, position);
                }
            }

            if (last && highSurrogate != 0)
            {
                position = put(REPLACEMENT_CHARACTER, position);
                highSurrogate = 0;
            }

            return position;
        }

        private int put(
            int codePoint,
            int position)
        {
            int next = position;

            if (codePoint < 0x80)
            {
                textBytes[next++] = (byte) codePoint;
            }
            else if (codePoint < 0x800)
            {
                textBytes[next++] = (byte) (0xC0 | codePoint >> 6);
                textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
            }
            else if (codePoint < 0x10000)
            {
                textBytes[next++] = (byte) (0xE0 | codePoint >> 12);
                textBytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
                textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
            }
            else
            {
                textBytes[next++] = (byte) (0xF0 | codePoint >> 18);
                textBytes[next++] = (byte) (0x80 | codePoint >> 12 & 0x3F);
                textBytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
                textBytes[next++] = (byte) (0x80 | codePoint & 0x3F);
            }

            return next;
        }

        interface Output
        {
            int credit();

            void begin();

            void frame(
                LlmSectionType type,
                int message,
                String toolCallId,
                String toolName,
                boolean follow,
                int flags,
                DirectBufferEx buffer,
                int offset,
                int length);

            void block(
                String block,
                int flags,
                DirectBufferEx buffer,
                int offset,
                int length);

            void after(
                String block,
                boolean container,
                int flags,
                DirectBufferEx buffer,
                int offset,
                int length);
        }
    }
}
