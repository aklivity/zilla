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
    private static final int NONE = -1;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final String FINISH_REASON = "finish_reason";
    private static final String COMPLETION = "chat.completion";
    private static final String USAGE = "usage";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_OBJECT = "object";
    private static final String MEMBER_CHOICES = "choices";
    private static final String MEMBER_ERROR = "error";
    private static final String MEMBER_INDEX = "index";
    private static final String MEMBER_DELTA = "delta";
    private static final String MEMBER_MESSAGE = "message";
    private static final String MEMBER_CONTENT = "content";
    private static final String MEMBER_REFUSAL = "refusal";
    private static final String MEMBER_TOOL_CALLS = "tool_calls";
    private static final String MEMBER_ROLE = "role";
    private static final String MEMBER_FUNCTION = "function";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final String MEMBER_TYPE = "type";
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

    private static final DirectBufferEx NULL_VALUE = new UnsafeBufferEx("null".getBytes(UTF_8));
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

    @FunctionalInterface
    private interface LlmOpenaiResponseDecoder
    {
        int decode(
            DirectBufferEx buffer,
            int offset,
            int limit);
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

    private static boolean canonicalMember(
        CharSequence member)
    {
        return MEMBER_ID.contentEquals(member) ||
            MEMBER_OBJECT.contentEquals(member) ||
            MODEL.contentEquals(member) ||
            MEMBER_CHOICES.contentEquals(member) ||
            USAGE.contentEquals(member) ||
            MEMBER_ERROR.contentEquals(member);
    }

    private static boolean carriedMember(
        CharSequence member)
    {
        return MODEL.contentEquals(member) || USAGE.contentEquals(member);
    }

    private static boolean canonicalMessageMember(
        CharSequence member)
    {
        return MEMBER_ROLE.contentEquals(member) ||
            MEMBER_CONTENT.contentEquals(member) ||
            MEMBER_REFUSAL.contentEquals(member) ||
            MEMBER_TOOL_CALLS.contentEquals(member);
    }

    private static int nesting(
        JsonEvent event)
    {
        return switch (event)
        {
        case START_OBJECT, START_ARRAY -> 1;
        case END_OBJECT, END_ARRAY -> -1;
        default -> 0;
        };
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
        private final JsonParserEx parser;
        private final StringBuilder captureValue;
        private final StringBuilder usagePath;
        private final int[] usageMark;
        private final LlmOpenaiResponseDecoder decodeStart = this::decodeStart;
        private final LlmOpenaiResponseDecoder decodeRootStart = this::decodeRootStart;
        private final LlmOpenaiResponseDecoder decodeRoot = this::decodeRoot;
        private final LlmOpenaiResponseDecoder decodeId = this::decodeId;
        private final LlmOpenaiResponseDecoder decodeObject = this::decodeObject;
        private final LlmOpenaiResponseDecoder decodeModel = this::decodeModel;
        private final LlmOpenaiResponseDecoder decodeChoicesStart = this::decodeChoicesStart;
        private final LlmOpenaiResponseDecoder decodeChoices = this::decodeChoices;
        private final LlmOpenaiResponseDecoder decodeChoice = this::decodeChoice;
        private final LlmOpenaiResponseDecoder decodeChoiceIndex = this::decodeChoiceIndex;
        private final LlmOpenaiResponseDecoder decodeFinishReason = this::decodeFinishReason;
        private final LlmOpenaiResponseDecoder decodeDeltaStart = this::decodeDeltaStart;
        private final LlmOpenaiResponseDecoder decodeMessageStart = this::decodeMessageStart;
        private final LlmOpenaiResponseDecoder decodeDelta = this::decodeDelta;
        private final LlmOpenaiResponseDecoder decodeMessage = this::decodeMessage;
        private final LlmOpenaiResponseDecoder decodeText = this::decodeText;
        private final LlmOpenaiResponseDecoder decodeToolCallsStart = this::decodeToolCallsStart;
        private final LlmOpenaiResponseDecoder decodeToolCalls = this::decodeToolCalls;
        private final LlmOpenaiResponseDecoder decodeToolCall = this::decodeToolCall;
        private final LlmOpenaiResponseDecoder decodeToolIndex = this::decodeToolIndex;
        private final LlmOpenaiResponseDecoder decodeToolId = this::decodeToolId;
        private final LlmOpenaiResponseDecoder decodeFunctionStart = this::decodeFunctionStart;
        private final LlmOpenaiResponseDecoder decodeFunction = this::decodeFunction;
        private final LlmOpenaiResponseDecoder decodeToolName = this::decodeToolName;
        private final LlmOpenaiResponseDecoder decodeArguments = this::decodeArguments;
        private final LlmOpenaiResponseDecoder decodeUsage = this::decodeUsage;
        private final LlmOpenaiResponseDecoder decodeCapture = this::decodeCapture;
        private final LlmOpenaiResponseDecoder decodeSkip = this::decodeSkip;
        private final LlmOpenaiResponseDecoder decodeEnd = this::decodeEnd;
        private final LlmOpenaiResponseDecoder decodeIgnore = this::decodeIgnore;
        private final LlmOpenaiResponseDecoder decodeDrainStart = this::decodeDrainStart;
        private final LlmOpenaiResponseDecoder decodeDrainRootStart = this::decodeDrainRootStart;
        private final LlmOpenaiResponseDecoder decodeDrainRoot = this::decodeDrainRoot;
        private final LlmOpenaiResponseDecoder decodeErrorStart = this::decodeErrorStart;
        private final LlmOpenaiResponseDecoder decodeErrorRootStart = this::decodeErrorRootStart;
        private final LlmOpenaiResponseDecoder decodeErrorRoot = this::decodeErrorRoot;
        private final LlmOpenaiResponseDecoder decodeErrorObjectStart = this::decodeErrorObjectStart;
        private final LlmOpenaiResponseDecoder decodeErrorObject = this::decodeErrorObject;
        private final LlmOpenaiResponseDecoder decodeErrorType = this::decodeErrorType;
        private final LlmOpenaiResponseDecoder decodeErrorMessage = this::decodeErrorMessage;

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

        private LlmOpenaiResponseDecoder decoder;
        private LlmOpenaiResponseDecoder skipThen;
        private LlmOpenaiResponseDecoder captureThen;
        private LlmOpenaiResponseDecoder errorThen;
        private LlmOpenaiResponseDecoder usageThen;
        private int decodeLimit;
        private int skipDepth;

        private boolean scanned;
        private boolean scanError;
        private boolean parseRejected;
        private boolean draining;
        private boolean logprobsRequested;
        private boolean messageScope;
        private boolean completion;
        private boolean choiceMessage;

        private int documentInputTokens = NONE;
        private int documentOutputTokens = NONE;
        private int documentTotalTokens = NONE;
        private int documentCacheReadTokens = NONE;
        private int documentReasoningTokens = NONE;
        private String documentErrorType;
        private String documentErrorMessage;
        private String documentId;
        private String documentServedModel;
        private String documentFinishReason;

        private int inputTokens = NONE;
        private int outputTokens = NONE;
        private int totalTokens = NONE;
        private int cacheReadTokens = NONE;
        private int reasoningTokens = NONE;
        private String errorType;
        private String errorMessage;
        private String id;
        private String servedModel;
        private String finishReason;

        private int choiceIndex;
        private int toolIndex;
        private String toolId;
        private String toolName;
        private boolean toolArguments;
        private LlmSectionType textType;
        private String textMember;

        private boolean sectionOpen;
        private boolean sectionStarted;
        private boolean sectionWritten;
        private boolean sectionAtomic;
        private boolean sectionFollow;
        private boolean followPending;
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

        private boolean captureAfter;
        private boolean captureStarted;
        private boolean captureTrim;
        private boolean captureContainer;
        private boolean captureUsage;
        private int captureDepth;
        private int captureFrom;
        private String captureName;
        private String usageKey;
        private int usageDepth;

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
            this.http = new LlmOpenaiHttpStream(this);
            this.parser = JsonEx.createParser();
            this.captureValue = new StringBuilder();
            this.usagePath = new StringBuilder();
            this.usageMark = new int[8];
            this.textBytes = new byte[1024];
            this.textBuffer = new UnsafeBufferEx(textBytes);
            this.heldBytes = new byte[1024];
            this.heldBuffer = new UnsafeBufferEx(heldBytes);
            this.heldLength = NONE;
            this.sectionTool = NONE;
            this.decoder = decodeStart;
        }

        private int replyCredit()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck)) / (1 + replyPad);
        }

        private boolean replyAvailable()
        {
            return !begun() || replyCredit() > 0;
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
                        logprobsRequested = payload.buffer().getByte(payload.offset()) == 't';
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
                doAppBegin(traceId, servedModel(), id());
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

        private boolean failed()
        {
            return errorType != null || errorMessage != null;
        }

        private boolean begun()
        {
            return scanned;
        }

        private boolean parseRejected()
        {
            return parseRejected;
        }

        private boolean completed()
        {
            return decoder == decodeEnd;
        }

        private String servedModel()
        {
            return documentServedModel != null ? documentServedModel : servedModel;
        }

        private String id()
        {
            return documentId != null ? documentId : id;
        }

        private String finishReason()
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

        private void populateUsage(
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

        private void populateError(
            LlmErrorFW.Builder error)
        {
            error.status(NONE)
                .type(errorType)
                .message(errorMessage);
        }

        private void finish()
        {
            closeSection();
        }

        private void resetDocument()
        {
            commitDocument();
            parser.reset();
            decoder = decodeStart;
        }

        private void resetErrorDocument()
        {
            commitDocument();
            parser.reset();
            decoder = decodeErrorStart;
        }

        private void resetResponse()
        {
            resetDocument();
            scanned = false;
            parseRejected = false;
            draining = false;
            sectionOpen = false;
            sectionStarted = false;
            sectionFollow = false;
            followPending = false;
            sectionTool = NONE;
            highSurrogate = 0;
            heldLength = NONE;
        }

        private int decodeResponse(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            int progress = offset;

            try
            {
                if (decoder == decodeEnd || decoder == decodeIgnore)
                {
                    progress = decoder.decode(buffer, offset, limit);
                }
                else if (draining)
                {
                    progress = decodeDrain(buffer, offset, limit, last);
                }
                else
                {
                    boolean ready = scanned;

                    if (!scanned)
                    {
                        final boolean found = scanResponse(buffer, offset, limit, last);

                        if (found || last || limit - offset >= decodeMax)
                        {
                            scanned = true;
                            ready = true;

                            if (!scanError && !parseRejected)
                            {
                                doAppBeginSections(http.decodeTraceId);
                            }
                        }
                    }

                    if (parseRejected)
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
                parseRejected = true;
                progress = limit;
            }

            return progress;
        }

        private void beginDrain()
        {
            draining = true;
            decoder = decoder == decodeStart ? decodeDrainStart : decodeIgnore;
        }

        private int decodeDrain(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            try
            {
                decodeWindow(buffer, offset, limit, last);
            }
            catch (JsonException ex)
            {
                decoder = decodeIgnore;
            }

            return limit;
        }

        private void decodeErrorResponse(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            try
            {
                decodeWindow(buffer, offset, limit, last);
            }
            catch (JsonException ex)
            {
                decoder = decodeIgnore;
            }
        }

        private int decodeAvailable(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            int progress = offset;
            boolean advanced = true;

            while (advanced && decoder != decodeEnd && !parseRejected)
            {
                int window = Math.min(limit - progress, Math.max(0, replyCredit() - Math.max(heldLength, 0)));

                if (window == 0 && limit > progress && heldLength > 0 && replyCredit() > 0)
                {
                    flushHeld();
                    window = Math.min(limit - progress, Math.max(0, replyCredit() - Math.max(heldLength, 0)));
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

        private int decodeWindow(
            DirectBufferEx buffer,
            int offset,
            int windowLimit,
            boolean windowLast)
        {
            decodeLimit = windowLimit;
            captureFrom = offset;

            parser.wrap(buffer, offset, windowLimit, windowLast);

            LlmOpenaiResponseDecoder previous = null;
            while (previous != decoder && decoder != decodeEnd && !parseRejected)
            {
                previous = decoder;
                decoder.decode(buffer, offset, windowLimit);
            }

            if (decoder == decodeCapture)
            {
                flushCapture(buffer, false);
            }

            int progress = position();

            if (decoder == decodeEnd)
            {
                while (progress < windowLimit && isWhitespace(buffer.getByte(progress)))
                {
                    progress++;
                }
            }

            return parseRejected ? windowLimit : progress;
        }

        private int position()
        {
            return decodeLimit - parser.remaining();
        }

        private void flushHeld()
        {
            if (heldLength != NONE)
            {
                writeSection(heldBuffer, heldLength, false);
                heldLength = NONE;
            }
        }

        private boolean scanResponse(
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
                    parseRejected = event != JsonEvent.START_ARRAY;
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
                            scanError = MEMBER_ERROR.equals(scanKey);
                            scanChoices = MEMBER_CHOICES.equals(scanKey);
                            found = scanError;
                        }
                        break;
                    case VALUE_STRING:
                        if (parser.deferredBytes())
                        {
                            parser.consumed(deferredLength(event));
                        }
                        else if (scanDepth == 1 && scanKey != null)
                        {
                            switch (scanKey)
                            {
                            case MEMBER_ID:
                                documentId = parser.getString();
                                break;
                            case MODEL:
                                documentServedModel = parser.getString();
                                break;
                            default:
                                break;
                            }
                            found = documentId != null && documentServedModel != null;
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

        private int decodeStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                parser.nextEvent();
                decoder = decodeRootStart;
            }

            return position();
        }

        private int decodeRootStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeRoot;
                }
                else
                {
                    parseRejected = true;
                }
            }

            return position();
        }

        private int decodeRoot(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeRoot && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    commitDocument();
                    decoder = decodeEnd;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (!canonicalMember(member) || completion && carriedMember(member))
                    {
                        beginCapture(member.toString(), false, false, decodeRoot);
                    }
                    else if (MEMBER_ID.contentEquals(member))
                    {
                        decoder = decodeId;
                    }
                    else if (MEMBER_OBJECT.contentEquals(member))
                    {
                        decoder = decodeObject;
                    }
                    else if (MODEL.contentEquals(member))
                    {
                        decoder = decodeModel;
                    }
                    else if (MEMBER_CHOICES.contentEquals(member))
                    {
                        decoder = decodeChoicesStart;
                    }
                    else if (USAGE.contentEquals(member))
                    {
                        beginUsage();
                        usageThen = decodeRoot;
                        decoder = decodeUsage;
                    }
                    else
                    {
                        errorThen = decodeRoot;
                        decoder = decodeErrorObjectStart;
                    }
                }
            }

            return position();
        }

        private int decodeId(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeId && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeRoot);

                if (value != null)
                {
                    documentId = value;
                }
            }

            return position();
        }

        private int decodeObject(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeObject && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeRoot);

                if (value != null)
                {
                    completion = COMPLETION.equals(value);
                }
            }

            return position();
        }

        private int decodeModel(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeModel && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeRoot);

                if (value != null)
                {
                    documentServedModel = value;
                }
            }

            return position();
        }

        private int decodeChoicesStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeChoices;
                }
                else
                {
                    parseRejected = true;
                }
            }

            return position();
        }

        private int decodeChoices(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    choiceIndex = 0;
                    decoder = decodeChoice;
                }
                else
                {
                    decoder = decodeRoot;
                }
            }

            return position();
        }

        private int decodeChoice(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeChoice && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    if (followPending)
                    {
                        emitAfterEmpty();
                    }

                    decoder = decodeChoices;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (BLOCK_LOGPROBS.contentEquals(member))
                    {
                        beginCapture(BLOCK_LOGPROBS, true, false, decodeChoice);
                    }
                    else if (MEMBER_MESSAGE.contentEquals(member))
                    {
                        choiceMessage = true;
                        decoder = decodeMessageStart;
                    }
                    else if (choiceMessage &&
                        (FINISH_REASON.contentEquals(member) || completion && !MEMBER_INDEX.contentEquals(member)))
                    {
                        beginCapture(member.toString(), true, false, decodeChoice);
                    }
                    else if (MEMBER_INDEX.contentEquals(member))
                    {
                        decoder = decodeChoiceIndex;
                    }
                    else if (MEMBER_DELTA.contentEquals(member))
                    {
                        decoder = decodeDeltaStart;
                    }
                    else if (FINISH_REASON.contentEquals(member))
                    {
                        decoder = decodeFinishReason;
                    }
                    else
                    {
                        skipMember(decodeChoice);
                    }
                }
            }

            return position();
        }

        private int decodeChoiceIndex(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeChoiceIndex && parser.hasNextEvent())
            {
                final int value = intValue(parser.nextEvent(), decodeChoice);

                if (value != NONE)
                {
                    choiceIndex = value;
                }
            }

            return position();
        }

        private int decodeFinishReason(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeFinishReason && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeChoice);

                if (value != null)
                {
                    documentFinishReason = value;
                }
            }

            return position();
        }

        private int decodeDeltaStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                messageScope = false;

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeDelta;
                }
                else
                {
                    skipAfter(event, decodeChoice);
                }
            }

            return position();
        }

        private int decodeMessageStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                messageScope = true;

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeMessage;
                }
                else
                {
                    skipAfter(event, decodeChoice);
                }
            }

            return position();
        }

        private int decodeDelta(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeDelta && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeChoice;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    decodeScopeMember(parser.getStringView(), decodeDelta);
                }
            }

            return position();
        }

        private int decodeMessage(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeMessage && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeChoice;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (completion && !canonicalMessageMember(member))
                    {
                        beginCapture(member.toString(), true, true, decodeMessage);
                    }
                    else
                    {
                        decodeScopeMember(member, decodeMessage);
                    }
                }
            }

            return position();
        }

        private void decodeScopeMember(
            CharSequence member,
            LlmOpenaiResponseDecoder scope)
        {
            if (MEMBER_CONTENT.contentEquals(member))
            {
                textType = LlmSectionType.ASSISTANT_TEXT;
                textMember = MEMBER_CONTENT;
                decoder = decodeText;
            }
            else if (MEMBER_REFUSAL.contentEquals(member))
            {
                textType = LlmSectionType.ASSISTANT_REFUSAL;
                textMember = MEMBER_REFUSAL;
                decoder = decodeText;
            }
            else if (MEMBER_TOOL_CALLS.contentEquals(member))
            {
                decoder = decodeToolCallsStart;
            }
            else
            {
                skipMember(scope);
            }
        }

        private int decodeText(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeText && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitText(deferred);

                    if (!deferred)
                    {
                        decoder = memberScope();
                    }
                }
                else
                {
                    if (event == JsonEvent.VALUE_NULL && completion && messageScope)
                    {
                        emitNullMember(textMember);
                    }

                    skipAfter(event, memberScope());
                }
            }

            return position();
        }

        private int decodeToolCallsStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeToolCalls;
                }
                else
                {
                    if (event == JsonEvent.VALUE_NULL && completion && messageScope)
                    {
                        emitNullMember(MEMBER_TOOL_CALLS);
                    }

                    skipAfter(event, memberScope());
                }
            }

            return position();
        }

        private int decodeToolCalls(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    toolIndex = NONE;
                    toolId = null;
                    toolName = null;
                    toolArguments = false;
                    decoder = decodeToolCall;
                }
                else
                {
                    decoder = memberScope();
                }
            }

            return position();
        }

        private int decodeToolCall(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeToolCall && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    if (!toolArguments && (toolId != null || toolName != null))
                    {
                        openTool();
                        emitSection(0, messageScope);
                    }

                    decoder = decodeToolCalls;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_INDEX.contentEquals(member))
                    {
                        decoder = decodeToolIndex;
                    }
                    else if (MEMBER_ID.contentEquals(member))
                    {
                        decoder = decodeToolId;
                    }
                    else if (MEMBER_FUNCTION.contentEquals(member))
                    {
                        decoder = decodeFunctionStart;
                    }
                    else
                    {
                        skipMember(decodeToolCall);
                    }
                }
            }

            return position();
        }

        private int decodeToolIndex(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeToolIndex && parser.hasNextEvent())
            {
                final int value = intValue(parser.nextEvent(), decodeToolCall);

                if (value != NONE)
                {
                    toolIndex = value;
                }
            }

            return position();
        }

        private int decodeToolId(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeToolId && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeToolCall);

                if (value != null)
                {
                    toolId = value;
                }
            }

            return position();
        }

        private int decodeFunctionStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeFunction;
                }
                else
                {
                    skipAfter(event, decodeToolCall);
                }
            }

            return position();
        }

        private int decodeFunction(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeFunction && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeToolCall;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_NAME.contentEquals(member))
                    {
                        decoder = decodeToolName;
                    }
                    else if (MEMBER_ARGUMENTS.contentEquals(member))
                    {
                        decoder = decodeArguments;
                    }
                    else
                    {
                        skipMember(decodeFunction);
                    }
                }
            }

            return position();
        }

        private int decodeToolName(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeToolName && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeFunction);

                if (value != null)
                {
                    toolName = value;
                }
            }

            return position();
        }

        private int decodeArguments(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeArguments && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitArguments(deferred);

                    if (!deferred)
                    {
                        decoder = decodeFunction;
                    }
                }
                else
                {
                    skipAfter(event, decodeFunction);
                }
            }

            return position();
        }

        private int decodeUsage(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeUsage && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                final boolean deferred = deferredScalar(event);

                trackUsage(event);

                captureDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (captureDepth == 0)
                {
                    decoder = usageThen;
                }
            }

            return position();
        }

        private int decodeCapture(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeCapture && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                final boolean deferred = deferredScalar(event);

                if (captureUsage)
                {
                    trackUsage(event);
                }

                captureDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (captureDepth == 0)
                {
                    endCapture(buffer, event);
                }
            }

            return position();
        }

        private int decodeSkip(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeSkip && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                skipDepth += nesting(event);

                if (deferredScalar(event))
                {
                    parser.consumed(deferredLength(event));
                }
                else if (event == JsonEvent.KEY_NAME && parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (event != JsonEvent.KEY_NAME && skipDepth == 0)
                {
                    decoder = skipThen;
                }
            }

            return position();
        }

        private int decodeEnd(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            for (int index = offset; index < limit; index++)
            {
                if (!isWhitespace(buffer.getByte(index)))
                {
                    parseRejected = true;
                }
            }

            return limit;
        }

        private int decodeIgnore(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            http.cleanupDecodeSlot();

            return limit;
        }

        private int decodeDrainStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                parser.nextEvent();
                decoder = decodeDrainRootStart;
            }

            return position();
        }

        private int decodeDrainRootStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                decoder = event == JsonEvent.START_OBJECT ? decodeDrainRoot : decodeIgnore;
            }

            return position();
        }

        private int decodeDrainRoot(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeDrainRoot && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeIgnore;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (USAGE.contentEquals(parser.getStringView()))
                {
                    beginUsage();
                    usageThen = decodeDrainRoot;
                    decoder = decodeUsage;
                }
                else
                {
                    skipMember(decodeDrainRoot);
                }
            }

            return position();
        }

        private int decodeErrorStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                parser.nextEvent();
                decoder = decodeErrorRootStart;
            }

            return position();
        }

        private int decodeErrorRootStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                decoder = event == JsonEvent.START_OBJECT ? decodeErrorRoot : decodeIgnore;
            }

            return position();
        }

        private int decodeErrorRoot(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeErrorRoot && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeEnd;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (MEMBER_ERROR.contentEquals(parser.getStringView()))
                {
                    errorThen = decodeErrorRoot;
                    decoder = decodeErrorObjectStart;
                }
                else
                {
                    skipMember(decodeErrorRoot);
                }
            }

            return position();
        }

        private int decodeErrorObjectStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeErrorObject;
                }
                else
                {
                    skipAfter(event, errorThen);
                }
            }

            return position();
        }

        private int decodeErrorObject(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeErrorObject && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = errorThen;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_TYPE.contentEquals(member))
                    {
                        decoder = decodeErrorType;
                    }
                    else if (MEMBER_MESSAGE.contentEquals(member))
                    {
                        decoder = decodeErrorMessage;
                    }
                    else
                    {
                        skipMember(decodeErrorObject);
                    }
                }
            }

            return position();
        }

        private int decodeErrorType(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeErrorType && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeErrorObject);

                if (value != null)
                {
                    documentErrorType = value;
                }
            }

            return position();
        }

        private int decodeErrorMessage(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeErrorMessage && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeErrorObject);

                if (value != null)
                {
                    documentErrorMessage = value;
                }
            }

            return position();
        }

        private LlmOpenaiResponseDecoder memberScope()
        {
            return messageScope ? decodeMessage : decodeDelta;
        }

        private String stringValue(
            JsonEvent event,
            LlmOpenaiResponseDecoder then)
        {
            String value = null;

            if (event != JsonEvent.VALUE_STRING)
            {
                skipAfter(event, then);
            }
            else if (parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else
            {
                value = parser.getString();
                decoder = then;
            }

            return value;
        }

        private int intValue(
            JsonEvent event,
            LlmOpenaiResponseDecoder then)
        {
            int value = NONE;

            if (event != JsonEvent.VALUE_NUMBER)
            {
                skipAfter(event, then);
            }
            else if (parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else
            {
                value = parser.getInt();
                decoder = then;
            }

            return value;
        }

        private void skipMember(
            LlmOpenaiResponseDecoder then)
        {
            skipDepth = 0;
            skipThen = then;
            decoder = decodeSkip;
        }

        private void skipAfter(
            JsonEvent event,
            LlmOpenaiResponseDecoder then)
        {
            skipDepth = nesting(event);
            skipThen = then;
            decoder = skipDepth == 0 ? then : decodeSkip;
        }

        private boolean deferredScalar(
            JsonEvent event)
        {
            return (event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER) && parser.deferredBytes();
        }

        private int deferredLength(
            JsonEvent event)
        {
            return event == JsonEvent.VALUE_STRING ? parser.getStringView().length() : 0;
        }

        private void beginUsage()
        {
            usagePath.setLength(0);
            usagePath.append(USAGE);
            usageKey = null;
            usageDepth = 0;
            captureDepth = 0;
        }

        private void trackUsage(
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
                    decodeUsageNumber(usagePath.toString(), parser.deferredBytes());
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

        private void decodeUsageNumber(
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

        private void emitText(
            boolean deferred)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();
            final boolean atomic = messageScope;

            if (length > 0 || highSurrogate != 0 || !deferred && atomic && sectionOpen)
            {
                if (!sectionOpen || textType != sectionType || sectionMessage != choiceIndex)
                {
                    closeSection();
                    openSection(textType, choiceIndex, null, null, NONE);
                    sectionFollow = logprobsRequested && !atomic;
                }

                emitSection(encodeText(chars, !deferred), !deferred && (atomic || sectionFollow));
            }

            if (deferred)
            {
                parser.consumed(length);
            }
        }

        private void emitArguments(
            boolean deferred)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();
            final boolean atomic = messageScope;

            toolArguments = true;

            if (!sectionOpen || !continuesTool())
            {
                openTool();

                if (length == 0 && (deferred || !atomic))
                {
                    emitSection(0, false);
                }
            }

            if (length > 0 || highSurrogate != 0 || !deferred && atomic && sectionOpen)
            {
                emitSection(encodeText(chars, !deferred), !deferred && atomic);
            }

            if (deferred)
            {
                parser.consumed(length);
            }
        }

        private void emitNullMember(
            String member)
        {
            closeSection();
            doAppFrame(http.decodeTraceId, Flags.COMPLETE, member, LlmBlockOwner.CONTAINER, null, NO_MESSAGE,
                null, null, false, NULL_VALUE, 0, NULL_VALUE.capacity());
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
            sectionAtomic = messageScope;
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
                emitSection(0, true);
            }

            sectionOpen = false;
        }

        private void emitSection(
            int length,
            boolean fin)
        {
            if (sectionOpen)
            {
                sectionStarted = true;
                sectionOpen = !fin;

                if (sectionAtomic)
                {
                    emitAtomicSection(length, fin);
                }
                else
                {
                    writeSection(textBuffer, length, fin);
                }
            }
        }

        private void emitAtomicSection(
            int length,
            boolean fin)
        {
            if (heldLength != NONE && fin && length == 0)
            {
                writeSection(heldBuffer, heldLength, true);
                heldLength = NONE;
            }
            else
            {
                if (heldLength != NONE)
                {
                    writeSection(heldBuffer, heldLength, false);
                    heldLength = NONE;
                }

                if (fin)
                {
                    writeSection(textBuffer, length, true);
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

        private void writeSection(
            DirectBufferEx buffer,
            int length,
            boolean fin)
        {
            final int flags = sectionWritten ? Flags.NONE : Flags.INIT;

            sectionWritten = true;

            doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, null, null, sectionType, sectionMessage,
                sectionToolId, sectionToolName, sectionFollow, buffer, 0, length);

            followPending = fin && sectionFollow;
        }

        private void emitAfterEmpty()
        {
            followPending = false;
            doAppFrame(http.decodeTraceId, Flags.COMPLETE, BLOCK_LOGPROBS, LlmBlockOwner.SECTION, null, NO_MESSAGE, null, null,
                false, textBuffer, 0, 0);
        }

        private void beginCapture(
            String name,
            boolean after,
            boolean container,
            LlmOpenaiResponseDecoder then)
        {
            captureAfter = after;
            captureContainer = container;
            captureThen = then;
            captureUsage = !after && USAGE.equals(name);
            captureName = name;
            captureValue.setLength(0);
            captureStarted = false;
            captureTrim = true;
            captureFrom = position();

            beginUsage();
            closeSection();

            decoder = decodeCapture;
        }

        private void endCapture(
            DirectBufferEx buffer,
            JsonEvent event)
        {
            if (captureAfter && !completion && !captureStarted && event == JsonEvent.VALUE_NULL)
            {
                if (followPending)
                {
                    emitAfterEmpty();
                }
            }
            else
            {
                flushCapture(buffer, true);
                followPending = followPending && !captureAfter;
            }

            decoder = captureThen;
        }

        private void flushCapture(
            DirectBufferEx buffer,
            boolean fin)
        {
            final int end = position();
            int from = captureFrom;

            if (captureTrim)
            {
                while (from < end && isSeparator(buffer.getByte(from)))
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
                final LlmBlockOwner owner = captureAfter
                    ? captureContainer ? LlmBlockOwner.CONTAINER : LlmBlockOwner.SECTION
                    : null;

                captureStarted = !fin;

                if (captureAfter && FINISH_REASON.equals(captureName))
                {
                    captureFinishReason(buffer, from, end, fin);
                }

                doAppFrame(http.decodeTraceId, flags, captureName, owner, null, NO_MESSAGE, null, null,
                    false, buffer, from, end - from);
            }

            captureFrom = end;
        }

        private void captureFinishReason(
            DirectBufferEx buffer,
            int from,
            int end,
            boolean fin)
        {
            for (int index = from; index < end; index++)
            {
                captureValue.append((char) buffer.getByte(index));
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

        private void commitDocument()
        {
            inputTokens = documentInputTokens != NONE ? documentInputTokens : inputTokens;
            outputTokens = documentOutputTokens != NONE ? documentOutputTokens : outputTokens;
            totalTokens = documentTotalTokens != NONE ? documentTotalTokens : totalTokens;
            cacheReadTokens = documentCacheReadTokens != NONE ? documentCacheReadTokens : cacheReadTokens;
            reasoningTokens = documentReasoningTokens != NONE ? documentReasoningTokens : reasoningTokens;
            errorType = documentErrorType != null ? documentErrorType : errorType;
            errorMessage = documentErrorMessage != null ? documentErrorMessage : errorMessage;
            id = documentId != null ? documentId : id;
            servedModel = documentServedModel != null ? documentServedModel : servedModel;
            finishReason = documentFinishReason != null ? documentFinishReason : finishReason;

            documentInputTokens = NONE;
            documentOutputTokens = NONE;
            documentTotalTokens = NONE;
            documentCacheReadTokens = NONE;
            documentReasoningTokens = NONE;
            documentErrorType = null;
            documentErrorMessage = null;
            documentId = null;
            documentServedModel = null;
            documentFinishReason = null;
            choiceMessage = false;
            completion = false;
        }

        private int encodeText(
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
                    position = putCodePoint(Character.toCodePoint(highSurrogate, c), position);
                    index = 1;
                }
                else
                {
                    position = putCodePoint(REPLACEMENT_CHARACTER, position);
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
                            position = putCodePoint(Character.toCodePoint(c, d), position);
                            index++;
                        }
                        else
                        {
                            position = putCodePoint(REPLACEMENT_CHARACTER, position);
                        }
                    }
                    else if (last)
                    {
                        position = putCodePoint(REPLACEMENT_CHARACTER, position);
                    }
                    else
                    {
                        highSurrogate = c;
                    }
                }
                else if (Character.isLowSurrogate(c))
                {
                    position = putCodePoint(REPLACEMENT_CHARACTER, position);
                }
                else
                {
                    position = putCodePoint(c, position);
                }
            }

            if (last && highSurrogate != 0)
            {
                position = putCodePoint(REPLACEMENT_CHARACTER, position);
                highSurrogate = 0;
            }

            return position;
        }

        private int putCodePoint(
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

        private void doAppEnd(
            long traceId)
        {
            state = LlmState.closingReply(state);

            if (!LlmState.replyClosed(state))
            {
                if (failed())
                {
                    doAppAbort(traceId);
                }
                else
                {
                    finish();
                    doAppBeginSections(traceId);

                    state = LlmState.closeReply(state);

                    final LlmEndExFW llmEndEx = llmEndExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId)
                        .usage(this::populateUsage)
                        .finishReason(finishReason())
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
                    .usage(this::populateUsage)
                    .error(this::populateError)
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
                errorJson = declared && !framing;
                stream.decoder = stream.decodeErrorStart;
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
                decodeErrorBody();
                doNetAbort(traceId);
                stream.doAppReset(traceId, errorStatus, stream.errorType, stream.errorMessage);
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
                stream.beginDrain();
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

                stream.commitDocument();
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
            final boolean last = LlmState.replyClosing(state);

            int progress = stream.decodeResponse(buffer, offset, limit, last);

            responseStarted = !stream.completed();

            if (stream.parseRejected())
            {
                stream.resetResponse();
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
                stream.decodeErrorResponse(buffer, offset, limit, last);

                if (last)
                {
                    stream.resetErrorDocument();
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
            final int progress = stream.decodeResponse(source, from, to, last);

            if (stream.parseRejected())
            {
                stream.resetResponse();
                cleanupStageSlot();
                cleanupNet(decodeTraceId);
            }
            else
            {
                restage(source, progress, to, last);

                if (last && stageSlot == NO_SLOT && stream.completed())
                {
                    stream.resetDocument();
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

        private void decodeErrorBody()
        {
            if (decodeSlot != NO_SLOT)
            {
                stream.decodeErrorResponse(decodePool.buffer(decodeSlot), 0, decodeSlotOffset, true);
                cleanupDecodeSlot();
            }

            stream.resetErrorDocument();
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
            stream.resetResponse();
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
}
