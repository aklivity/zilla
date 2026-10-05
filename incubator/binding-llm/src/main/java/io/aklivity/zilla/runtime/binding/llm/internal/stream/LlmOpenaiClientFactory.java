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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.LongUnaryOperator;


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
import io.aklivity.zilla.runtime.binding.llm.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFollow;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmResetExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
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
    private static final String RESPONSE_SCHEMA_RESOURCE = "openai.response.schema.json";
    private static final Set<String> RESPONSE_CONTENT_TYPES = Set.of(CONTENT_TYPE_JSON, CONTENT_TYPE_EVENT_STREAM);
    private static final DirectBufferEx RESPONSE_TERMINATOR = new UnsafeBufferEx("[DONE]".getBytes(UTF_8));
    private static final int STATUS_OK = 200;
    private static final int STATUS_MULTIPLE_CHOICES = 300;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int NO_ERROR = -1;
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
    private static final int KIND_DROPPED = 12;
    private static final int KIND_DIALECT = 13;

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

    private static final byte[] REQUEST_START = "{\"model\":\"".getBytes(UTF_8);
    private static final byte[] MODEL_END = "\"".getBytes(UTF_8);
    private static final byte[] MEMBER_NAME_END = "\":".getBytes(UTF_8);
    private static final byte[] MESSAGES_REGION = ",\"messages\":[".getBytes(UTF_8);
    private static final byte[] MESSAGES_EMPTY = ",\"messages\":[]".getBytes(UTF_8);
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
    private static final byte[] TOOLS_START = ",\"tools\":[".getBytes(UTF_8);

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
    private final MutableDirectBufferEx convertBuffer;
    private final MutableDirectBufferEx copyBuffer;
    private final MutableDirectBufferEx transformBuffer;
    private final UnsafeBufferEx comparisonRO;
    private final DirectBufferEx emptyBufferRO;
    private final JsonSchema responseSchema;
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
        this.copyBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.transformBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.comparisonRO = new UnsafeBufferEx(new byte[0]);
        this.emptyBufferRO = new UnsafeBufferEx(new byte[0]);
        this.responseSchema = JsonSchema.of(readResponseSchema());
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

    private static String readResponseSchema()
    {
        String text;
        try (InputStream input = LlmOpenaiClientFactory.class.getResourceAsStream(RESPONSE_SCHEMA_RESOURCE))
        {
            text = new String(input.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return text;
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
        private final LlmOpenaiResponseExtractor extractor;
        private final LlmOpenaiSectionSink sink;
        private final Deque<ReplyFrame> replyFrames;
        private final JsonPipeline responsePipeline;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;
        private boolean rejected;
        private boolean sectionsFinished;
        private int appKind;
        private boolean logprobsBlock;

        private int replyEncodeSlot = NO_SLOT;
        private int replyEncodeSlotOffset;

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
            this.extractor = new LlmOpenaiResponseExtractor();
            this.replyFrames = new ArrayDeque<>();

            this.sink = new LlmOpenaiSectionSink(new ReplyOutput());
            this.responsePipeline = JsonEx.stream(JsonEx.createParser())
                .transform(responseSchema.validator())
                .transform(extractor)
                .into(sink);
            this.http = new LlmOpenaiHttpStream(this);
        }

        private int replyWindow()
        {
            return replyMax - (int) (replySeq - replyAck) - replyPad;
        }

        private boolean replyAvailable()
        {
            return replyEncodeSlot == NO_SLOT && replyFrames.isEmpty();
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
                    default:
                        break;
                    }
                }

                if (appKind == LlmDataExFW.KIND_BLOCK || appKind == LlmDataExFW.KIND_SECTION)
                {
                    final OctetsFW payload = data.payload();
                    final boolean fin = Flags.hasFin(flags);

                    if (payload != null && payload.sizeof() > 0)
                    {
                        if (logprobsBlock)
                        {
                            sink.logprobs(payload.buffer().getByte(payload.offset()) == 't');
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

            drainReplyFrames(traceId);

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
            cleanupReplyEncodeSlot();
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
                doAppBegin(traceId, extractor.model(), extractor.id());
                queueResponseBlocks();
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

        private void queueResponseBlocks()
        {
            final LlmResponseOptions options = extractor.options();

            int slot = options.next();
            while (slot != LlmResponseOptions.NONE)
            {
                final byte[] literal = options.latest(slot).getBytes(UTF_8);
                final String member = options.member(slot);

                options.deliver(slot);

                queueReplyFrame(member, false, null, NO_MESSAGE, null, null, false, Flags.COMPLETE,
                    new UnsafeBufferEx(literal), 0, literal.length);

                slot = options.next();
            }
        }

        private void queueReplyFrame(
            String block,
            boolean after,
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            boolean follow,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final long traceId = http.decodeTraceId;

            if (length > 0 && replyEncodeSlot == NO_SLOT)
            {
                replyEncodeSlot = encodePool.acquire(replyId);
            }

            if (length > 0 && (replyEncodeSlot == NO_SLOT || replyEncodeSlotOffset + length > encodeMax))
            {
                cleanup(traceId);
            }
            else
            {
                final int slotOffset = replyEncodeSlotOffset;

                if (length > 0)
                {
                    encodePool.buffer(replyEncodeSlot).putBytes(slotOffset, buffer, offset, length);
                    replyEncodeSlotOffset += length;
                }

                replyFrames.add(new ReplyFrame(block, after, type, message, toolCallId, toolName, follow, flags,
                    slotOffset, length));

                drainReplyFrames(traceId);
            }
        }

        private void drainReplyFrames(
            long traceId)
        {
            while (!replyFrames.isEmpty())
            {
                final ReplyFrame frame = replyFrames.peek();
                final int remaining = frame.length - frame.sent;
                final int window = replyWindow();

                if (window < 0 || remaining > 0 && window == 0)
                {
                    break;
                }

                final int length = Math.min(remaining, window);
                final boolean first = frame.sent == 0;
                final boolean last = frame.sent + length == frame.length;
                final int initFlags = first && Flags.hasInit(frame.flags) ? Flags.INIT : Flags.NONE;
                final int flags = last && Flags.hasFin(frame.flags) ? Flags.fin(initFlags) : initFlags;

                doAppFrame(traceId, flags, first && Flags.hasInit(frame.flags) ? frame : null, frame.offset + frame.sent, length);

                frame.sent += length;

                if (last)
                {
                    replyFrames.poll();
                }
            }

            if (replyFrames.isEmpty())
            {
                cleanupReplyEncodeSlot();

                if (LlmState.replyClosing(state))
                {
                    doAppEnd(traceId);
                }
            }
        }

        private void doAppFrame(
            long traceId,
            int flags,
            ReplyFrame header,
            int slotOffset,
            int length)
        {
            final OctetsFW extension;

            if (header != null)
            {
                final LlmDataExFW.Builder dataExBuilder = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId);

                if (header.after)
                {
                    dataExBuilder.after(a -> a.block(header.block));
                }
                else if (header.block != null)
                {
                    dataExBuilder.block(d -> d.block(header.block));
                }
                else
                {
                    dataExBuilder.section(x ->
                    {
                        x.type(t -> t.set(header.type)).message(header.message);

                        if (header.toolName != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_NAME).value(header.toolName));
                        }

                        if (header.toolCallId != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_CALL_ID).value(header.toolCallId));
                        }

                        if (header.follow)
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
                .payload(length > 0 ? encodePool.buffer(replyEncodeSlot) : EMPTY_OCTETS.buffer(),
                    length > 0 ? slotOffset : 0, length)
                .extension(extension)
                .build();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
            replySeq += data.reserved();
        }

        private final class ReplyOutput implements LlmOpenaiSectionSink.Output
        {
            @Override
            public void begin()
            {
                doAppBeginSections(http.decodeTraceId);
            }

            @Override
            public void frame(
                LlmSectionType type,
                int message,
                String toolCallId,
                String toolName,
                boolean follow,
                int flags,
                DirectBuffer buffer,
                int offset,
                int length)
            {
                queueReplyFrame(null, false, type, message, toolCallId, toolName, follow, flags, buffer, offset, length);
            }

            @Override
            public void after(
                String block,
                int flags,
                DirectBuffer buffer,
                int offset,
                int length)
            {
                queueReplyFrame(block, true, null, NO_MESSAGE, null, null, false, flags, buffer, offset, length);
            }
        }

        private void doAppEnd(
            long traceId)
        {
            state = LlmState.closingReply(state);

            if (!sectionsFinished && !extractor.failed() && replyAvailable() && !LlmState.replyClosed(state))
            {
                sectionsFinished = true;
                sink.finish();
            }

            if (replyAvailable() && !LlmState.replyClosed(state))
            {
                if (extractor.failed())
                {
                    doAppAbort(traceId);
                }
                else
                {
                    doAppBeginSections(traceId);
                    queueResponseBlocks();
                }

                if (replyAvailable() && !extractor.failed())
                {
                    state = LlmState.closeReply(state);

                    final LlmEndExFW llmEndEx = llmEndExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId)
                        .usage(u -> extractor.populateUsage(u))
                        .finishReason(extractor.finishReason())
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
                cleanupReplyEncodeSlot();

                final LlmAbortExFW llmAbortEx = llmAbortExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .usage(u -> extractor.populateUsage(u))
                    .error(e -> extractor.populateError(e))
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

        private void cleanupReplyEncodeSlot()
        {
            if (replyEncodeSlot != NO_SLOT)
            {
                encodePool.release(replyEncodeSlot);
                replyEncodeSlot = NO_SLOT;
                replyEncodeSlotOffset = 0;
            }

            replyFrames.clear();
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
            cleanupReplyEncodeSlot();
        }
    }

    private static final class ReplyFrame
    {
        private final String block;
        private final boolean after;
        private final LlmSectionType type;
        private final int message;
        private final String toolCallId;
        private final String toolName;
        private final boolean follow;
        private final int flags;
        private final int offset;
        private final int length;
        private int sent;

        private ReplyFrame(
            String block,
            boolean after,
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            boolean follow,
            int flags,
            int offset,
            int length)
        {
            this.block = block;
            this.after = after;
            this.type = type;
            this.message = message;
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.follow = follow;
            this.flags = flags;
            this.offset = offset;
            this.length = length;
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
        private boolean sectionsStarted;

        private int messageCount;
        private int messageIndex = NO_MESSAGE;
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
        private LlmContentDecoder decoder;
        private boolean streaming;
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
            put(REQUEST_START);
            putEscaped(stream.model);
            put(MODEL_END);
            encodeHold = 0;
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

                putByte(',');
                putByte('"');
                putEscaped(block);
                put(MEMBER_NAME_END);
            }
            else
            {
                sectionKind = KIND_DROPPED;
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
            if (toolsOpened && !toolsClosed)
            {
                putByte(']');
                toolsClosed = true;
            }

            if (!messagesOpened)
            {
                put(MESSAGES_REGION);
                messagesOpened = true;
            }

            final String role = kind == KIND_UNKNOWN && messageRole != null ? messageRole : role(kind);

            if (message != messageIndex || !role.equals(messageRole) || kind == KIND_TOOL_RESULT && !extensionMatches(extension))
            {
                closeMessage();
                openMessage(kind, role, message, extension);
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
                    put(MESSAGES_EMPTY);
                }
                else if (!messagesClosed)
                {
                    putByte(']');
                    messagesClosed = true;
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
            final String mediaType = LlmContentCodecFactory.mediaType(responseContentType);
            final int status = responseStatus != null ? Integer.parseInt(responseStatus) : STATUS_OK;
            final boolean declared = mediaType != null && RESPONSE_CONTENT_TYPES.contains(mediaType);

            if (stream.rejected)
            {
                doNetReset(traceId);
            }
            else if (status < STATUS_OK || status >= STATUS_MULTIPLE_CHOICES)
            {
                errorStatus = status;
                decoder = declared ? codecs.createDecoder(responseContentType) : null;
                doNetWindow(traceId);
            }
            else if (!declared)
            {
                cleanupNet(traceId);
            }
            else
            {
                decoder = codecs.createDecoder(responseContentType);
                streaming = decoder.streaming();

                doNetWindow(traceId);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + data.reserved();

            DirectBufferEx buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();

            if (errorStatus != NO_ERROR)
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
                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    decodeNet(traceId, slotBuffer, 0, decodeSlotOffset);
                }
                else if (responseStarted)
                {
                    decodeNet(traceId, emptyBufferRO, 0, 0);
                }

                if (decodeSlot == NO_SLOT)
                {
                    doAppEnd(pendingEndTraceId);
                }
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            cleanupDecodeSlot();
            resetDecodeState();

            if (errorStatus != NO_ERROR)
            {
                doNetAbort(traceId);
                stream.doAppReset(traceId, errorStatus, null, null);
            }
            else
            {
                stream.doAppAbort(traceId);
            }
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
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                decodeNet(traceId, slotBuffer, 0, decodeSlotOffset);

                doNetWindow(traceId);
            }
        }

        private void decodeNet(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            if (stream.replyAvailable())
            {
                decodeTraceId = traceId;

                progress = streaming
                    ? decoder.decode(buffer, offset, limit, this)
                    : decodeJsonSections(buffer, offset, limit);
            }

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

                if (LlmState.replyClosing(state))
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
            int progress = offset;
            final boolean last = LlmState.replyClosing(state);
            final JsonPipeline pipeline = stream.responsePipeline;

            while ((progress < limit || last) && stream.replyAvailable())
            {
                final Status status = pipeline.transform(buffer, progress, limit, last);

                responseStarted = status != Status.COMPLETED;

                if (status == Status.REJECTED)
                {
                    pipeline.reset();
                    cleanupNet(decodeTraceId);
                    progress = limit;
                    break;
                }

                final int consumed = (limit - progress) - pipeline.remaining();
                if (consumed == 0)
                {
                    break;
                }
                progress += consumed;
            }

            return progress;
        }

        @Override
        public boolean available()
        {
            return stream.replyAvailable();
        }

        @Override
        public void event(
            String event)
        {
        }

        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length,
            boolean last)
        {
            forwardResponseSections(buffer, offset, length, last);
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
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
                final JsonPipeline pipeline = stream.responsePipeline;
                final Status status = pipeline.transform((DirectBufferEx) buffer, offset, offset + length, last);

                if (status == Status.REJECTED)
                {
                    pipeline.reset();
                    cleanupNet(decodeTraceId);
                }
                else if (status == Status.COMPLETED && last)
                {
                    pipeline.nextDocument();
                }
            }
        }

        private void appendErrorBody(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (decoder != null && !errorBodyDropped)
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
            final LlmErrorBodyDecoder errorDecoder = new LlmErrorBodyDecoder();

            if (decodeSlot != NO_SLOT)
            {
                decoder.decode(decodePool.buffer(decodeSlot), 0, decodeSlotOffset, errorDecoder);
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
            stream.responsePipeline.reset();
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
            final int buffered = errorStatus == NO_ERROR ? decodeSlotOffset : 0;
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
    }

    private final class LlmErrorBodyDecoder implements LlmContentDecoderOutput
    {
        private final LlmOpenaiResponseExtractor extractor;
        private final JsonPipeline pipeline;

        private LlmErrorBodyDecoder()
        {
            this.extractor = new LlmOpenaiResponseExtractor();
            this.pipeline = JsonEx.stream(JsonEx.createParser())
                .transform(extractor)
                .into(JsonEx.createGenerator());
        }

        private String type()
        {
            return extractor.errorType();
        }

        private String message()
        {
            return extractor.errorMessage();
        }

        @Override
        public boolean available()
        {
            return true;
        }

        @Override
        public void event(
            String event)
        {
        }

        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length,
            boolean last)
        {
            Status status;
            do
            {
                status = pipeline.transform((DirectBufferEx) buffer, offset, offset + length, last,
                    transformBuffer, 0, transformBuffer.capacity()).status();
            }
            while (status == Status.SUSPENDED);
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            pipeline.reset();
        }
    }
}
