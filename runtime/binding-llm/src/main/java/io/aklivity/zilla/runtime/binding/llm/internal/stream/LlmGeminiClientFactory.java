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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmGeminiDialectFactorySpi.NAME;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringReader;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.queue.LlmQueueEntryFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmErrorFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFlushExFW;
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
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmGeminiClientFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_SCHEME = ":scheme";
    private static final String HEADER_AUTHORITY = ":authority";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-goog-api-key";
    private static final String METHOD_POST = "POST";
    private static final String PATH_MODELS = "/models/";
    private static final String OPERATION_GENERATE = "generateContent";
    private static final String OPERATION_STREAM = "streamGenerateContent";
    private static final String QUERY_ALT_SSE = "?alt=sse";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ERROR_TYPE_INVALID_REQUEST = "INVALID_ARGUMENT";
    private static final String MESSAGE_MODEL_MISSING = "model: Field required";
    private static final String MESSAGE_SECTION_INVALID = "Invalid request section.";
    private static final String MESSAGE_SECTION_CONVERT = "%s cannot be converted from %s to %s";
    private static final Set<String> RESPONSE_CONTENT_TYPES = Set.of(CONTENT_TYPE_JSON, CONTENT_TYPE_EVENT_STREAM);
    private static final int STATUS_OK = 200;
    private static final int STATUS_MULTIPLE_CHOICES = 300;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int NO_ERROR = -1;
    private static final int STATUS_BAD_GATEWAY = 502;
    private static final int EVENT_FRAMES = 4;
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final int EVENT_ATTRIBUTES_CAPACITY = 256;
    private static final int QUEUE_ENTRY_OVERHEAD = 5 * Integer.BYTES + 3 * Long.BYTES + Byte.BYTES;
    private static final int QUEUE_ENTRY_PADDING = QUEUE_ENTRY_OVERHEAD + EVENT_ATTRIBUTES_CAPACITY;

    private static final int KIND_SYSTEM_INSTRUCTION = 1;
    private static final int KIND_USER_TEXT = 2;
    private static final int KIND_USER_IMAGE = 3;
    private static final int KIND_USER_DOCUMENT = 4;
    private static final int KIND_USER_AUDIO = 5;
    private static final int KIND_USER_VIDEO = 12;
    private static final int KIND_ASSISTANT_TEXT = 6;
    private static final int KIND_ASSISTANT_REFUSAL = 7;
    private static final int KIND_TOOL_DEFINITION = 9;
    private static final int KIND_TOOL_CALL = 10;
    private static final int KIND_TOOL_RESULT = 11;
    private static final int KIND_CONTROL = 14;
    private static final int KIND_DROPPED = 16;
    private static final int KIND_UNSUPPORTED = 17;
    private static final int CALLS_MAX = 16;
    private static final int MEDIA_KINDS = 4;
    private static final int CONTROL_PENDING = 0;
    private static final int CONTROL_WRITE = 1;
    private static final int CONTROL_DROP = 2;

    private static final int ROLE_NONE = 0;
    private static final int ROLE_USER = 1;
    private static final int ROLE_MODEL = 2;

    private static final int REGION_NONE = 0;
    private static final int REGION_CONTENTS = 1;
    private static final int REGION_SYSTEM = 2;
    private static final int REGION_TOOLS = 3;

    private static final int NO_MESSAGE = -2;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_SLACK = 256;
    private static final int ENCODE_STRUCTURE = 128;
    private static final int ENCODE_MAX_TOKENS = 64;
    private static final int NO_MAX_OUTPUT_TOKENS = -1;

    private static final int NONE = -1;
    private static final int ROOT_CONTROL = -1;
    private static final int CONTROL_SEEN_MAX = 16;
    private static final int CONTROL_CLOSING = 1;
    private static final int CONTROL_HEADER = 2 * Integer.BYTES;
    private static final DirectBufferEx CONTROL_TERMINATOR = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final int TEXT_ESCAPE_EXPANSION = 6;
    private static final int TEXT_FRAMING = 48;
    private static final byte[] SECTION_SUFFIX = "\"}".getBytes(UTF_8);
    private static final DirectBufferEx ARGUMENTS_PREFIX = new UnsafeBufferEx("{\"arguments\":".getBytes(UTF_8));
    private static final DirectBufferEx ARGUMENTS_EMPTY = new UnsafeBufferEx("{\"arguments\":{}}".getBytes(UTF_8));
    private static final DirectBufferEx ARGUMENTS_SUFFIX = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final String TOOL_CALL_ID_PREFIX = "call_0_";

    private static final String KEY_FINISH_REASON = "finishReason";
    private static final String MEMBER_ROLE = "role";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_CANDIDATES = "candidates";
    private static final String MEMBER_CONTENT = "content";
    private static final String MEMBER_PARTS = "parts";
    private static final String MEMBER_INDEX = "index";
    private static final String MEMBER_MODEL_VERSION = "modelVersion";
    private static final String MEMBER_RESPONSE_ID = "responseId";
    private static final String MEMBER_USAGE = "usageMetadata";
    private static final String MEMBER_ERROR = "error";
    private static final String MEMBER_MESSAGE = "message";
    private static final String MEMBER_STATUS = "status";
    private static final String MEMBER_FUNCTION_CALL = "functionCall";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_ARGS = "args";
    private static final String MEMBER_FUNCTION_RESPONSE = "functionResponse";
    private static final String MEMBER_RESPONSE = "response";
    private static final String MEMBER_OUTPUT = "output";
    private static final String MEMBER_ERROR_RESPONSE = "error";
    private static final String MEMBER_SIGNATURE = "thoughtSignature";
    private static final String MEMBER_WIRE_SIGNATURE = ",\"gemini:thoughtSignature\":\"";
    private static final String MEMBER_WIRE_ID = ",\"gemini:id\":\"";
    private static final String USAGE_PROMPT_TOKENS = "promptTokenCount";
    private static final String USAGE_CANDIDATES_TOKENS = "candidatesTokenCount";
    private static final String USAGE_CACHED_TOKENS = "cachedContentTokenCount";
    private static final String USAGE_THOUGHTS_TOKENS = "thoughtsTokenCount";
    private static final String USAGE_TOTAL_TOKENS = "totalTokenCount";
    private static final int USAGE_PROMPT = 0;
    private static final int USAGE_CANDIDATES = 1;
    private static final int USAGE_CACHED = 2;
    private static final int USAGE_THOUGHTS = 3;
    private static final int USAGE_TOTAL = 4;

    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);

    private static final String MEMBER_CONTENTS = "contents";
    private static final String MEMBER_TOOLS = "tools";
    private static final String MEMBER_FUNCTION_DECLARATIONS = "functionDeclarations";
    private static final String MEMBER_SYSTEM_INSTRUCTION = "systemInstruction";
    private static final String MEMBER_GENERATION_CONFIG = "generationConfig";
    private static final String MEMBER_MAX_OUTPUT_TOKENS = "maxOutputTokens";
    private static final String ROLE_NAME_USER = "user";
    private static final String ROLE_NAME_MODEL = "model";

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
    private final LlmFlushExFW llmFlushExRO = new LlmFlushExFW();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmResetExFW.Builder llmResetExRW = new LlmResetExFW.Builder();
    private final LlmEndExFW.Builder llmEndExRW = new LlmEndExFW.Builder();
    private final LlmAbortExFW.Builder llmAbortExRW = new LlmAbortExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final Matcher contentTypeMatcher;
    private final MutableDirectBufferEx transformBuffer;
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

    public LlmGeminiClientFactory(
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.contentTypeMatcher = CONTENT_TYPE.matcher("");
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.transformBuffer = new UnsafeBufferEx(new byte[decodeMax]);
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
            newStream = new LlmGeminiStream(
                app,
                originId,
                routedId,
                initialId,
                route.id,
                authorization,
                affinity,
                binding,
                llmBeginEx.model().asString(),
                llmBeginEx.dialect().asString(),
                llmBeginEx.extension().sizeof() > 0
                    ? llmBeginEx.extension().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o))
                    : null)::onAppMessage;
        }

        return newStream;
    }

    @FunctionalInterface
    private interface LlmGeminiResponseDecoder
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
        case USER_VIDEO -> KIND_USER_VIDEO;
        case ASSISTANT_TEXT -> KIND_ASSISTANT_TEXT;
        case ASSISTANT_REFUSAL -> KIND_ASSISTANT_REFUSAL;
        case TOOL_DEFINITION -> KIND_TOOL_DEFINITION;
        case TOOL_CALL -> KIND_TOOL_CALL;
        case TOOL_RESULT -> KIND_TOOL_RESULT;
        case CONTROL -> KIND_CONTROL;
        case NONE -> 0;
        default -> KIND_UNSUPPORTED;
        };
    }

    private static int role(
        int kind)
    {
        return kind == KIND_ASSISTANT_TEXT || kind == KIND_ASSISTANT_REFUSAL || kind == KIND_TOOL_CALL ? ROLE_MODEL : ROLE_USER;
    }

    private static boolean media(
        int kind)
    {
        return kind == KIND_USER_IMAGE ||
            kind == KIND_USER_DOCUMENT ||
            kind == KIND_USER_AUDIO ||
            kind == KIND_USER_VIDEO;
    }

    private static boolean text(
        int kind)
    {
        return kind == KIND_SYSTEM_INSTRUCTION ||
            kind == KIND_USER_TEXT ||
            kind == KIND_ASSISTANT_TEXT ||
            kind == KIND_ASSISTANT_REFUSAL ||
            kind == KIND_TOOL_RESULT;
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

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private final class LlmGeminiStream
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
        private final String operation;
        private final LlmGeminiHttpStream http;
        private final JsonParserEx parser;
        private final LlmGeminiResponseDecoder decodeStart = this::decodeStart;
        private final LlmGeminiResponseDecoder decodeRootStart = this::decodeRootStart;
        private final LlmGeminiResponseDecoder decodeChunks = this::decodeChunks;
        private final LlmGeminiResponseDecoder decodeRoot = this::decodeRoot;
        private final LlmGeminiResponseDecoder decodeCandidatesStart = this::decodeCandidatesStart;
        private final LlmGeminiResponseDecoder decodeCandidates = this::decodeCandidates;
        private final LlmGeminiResponseDecoder decodeCandidate = this::decodeCandidate;
        private final LlmGeminiResponseDecoder decodeContentStart = this::decodeContentStart;
        private final LlmGeminiResponseDecoder decodeContent = this::decodeContent;
        private final LlmGeminiResponseDecoder decodePartsStart = this::decodePartsStart;
        private final LlmGeminiResponseDecoder decodeParts = this::decodeParts;
        private final LlmGeminiResponseDecoder decodePart = this::decodePart;
        private final LlmGeminiResponseDecoder decodeCallStart = this::decodeCallStart;
        private final LlmGeminiResponseDecoder decodeCall = this::decodeCall;
        private final LlmGeminiResponseDecoder decodeCallName = this::decodeCallName;
        private final LlmGeminiResponseDecoder decodeCallId = this::decodeCallId;
        private final LlmGeminiResponseDecoder decodeCallArguments = this::decodeCallArguments;
        private final LlmGeminiResponseDecoder decodeCapture = this::decodeCapture;
        private final LlmGeminiResponseDecoder decodeSignature = this::decodeSignature;
        private final LlmGeminiResponseDecoder decodeBlockText = this::decodeBlockText;
        private final LlmGeminiResponseDecoder decodeFinishReason = this::decodeFinishReason;
        private final LlmGeminiResponseDecoder decodeId = this::decodeId;
        private final LlmGeminiResponseDecoder decodeModel = this::decodeModel;
        private final LlmGeminiResponseDecoder decodeUsageStart = this::decodeUsageStart;
        private final LlmGeminiResponseDecoder decodeUsage = this::decodeUsage;
        private final LlmGeminiResponseDecoder decodeUsageValue = this::decodeUsageValue;
        private final LlmGeminiResponseDecoder decodeControlValue = this::decodeControlValue;
        private final LlmGeminiResponseDecoder decodeControlStream = this::decodeControlStream;
        private final LlmGeminiResponseDecoder decodeSkip = this::decodeSkip;
        private final LlmGeminiResponseDecoder decodeEnd = this::decodeEnd;
        private final LlmGeminiResponseDecoder decodeIgnore = this::decodeIgnore;
        private final LlmGeminiResponseDecoder decodeDrainStart = this::decodeDrainStart;
        private final LlmGeminiResponseDecoder decodeDrainRootStart = this::decodeDrainRootStart;
        private final LlmGeminiResponseDecoder decodeDrainRoot = this::decodeDrainRoot;
        private final LlmGeminiResponseDecoder decodeErrorStart = this::decodeErrorStart;
        private final LlmGeminiResponseDecoder decodeErrorRootStart = this::decodeErrorRootStart;
        private final LlmGeminiResponseDecoder decodeErrorRoot = this::decodeErrorRoot;
        private final LlmGeminiResponseDecoder decodeErrorObjectStart = this::decodeErrorObjectStart;
        private final LlmGeminiResponseDecoder decodeErrorObject = this::decodeErrorObject;
        private final LlmGeminiResponseDecoder decodeErrorType = this::decodeErrorType;
        private final LlmGeminiResponseDecoder decodeErrorMessage = this::decodeErrorMessage;

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
        private final boolean crossDialect;
        private LlmSectionType requestSection;

        private LlmGeminiResponseDecoder decoder;
        private LlmGeminiResponseDecoder rootThen;
        private LlmGeminiResponseDecoder skipThen;
        private LlmGeminiResponseDecoder errorThen;
        private LlmGeminiResponseDecoder usageThen;
        private LlmGeminiResponseDecoder valueThen;
        private LlmGeminiResponseDecoder textThen;
        private int decodeLimit;
        private int skipDepth;
        private int candidateOrdinal;

        private boolean scanned;
        private boolean scanError;
        private boolean parseRejected;
        private boolean draining;

        private int documentPromptTokens = NONE;
        private int documentCandidatesTokens = NONE;
        private int documentCachedTokens = NONE;
        private int documentThoughtsTokens = NONE;
        private int documentTotalTokens = NONE;
        private String documentErrorType;
        private String documentErrorMessage;
        private String documentId;
        private String documentServedModel;
        private String documentFinishReason;

        private int promptTokens = NONE;
        private int candidatesTokens = NONE;
        private int cachedTokens = NONE;
        private int thoughtsTokens = NONE;
        private int totalTokens = NONE;
        private String errorType;
        private String errorMessage;
        private String id;
        private String servedModel;
        private String finishReason;

        private int usageField;
        private String callName;
        private String callId;
        private int callOrdinal;
        private int toolCalls;
        private int callArguments = NONE;
        private String sectionToolCallId;
        private String sectionToolName;
        private String sectionSignature;
        private final StringBuilder signatureBuilder = new StringBuilder();
        private LlmGeminiResponseDecoder captureThen;
        private int captureDepth;
        private int captureFrom;

        private boolean sectionOpen;
        private boolean sectionStarted;
        private boolean sectionWritten;
        private LlmSectionType sectionType;

        private char highSurrogate;
        private byte[] textBytes;
        private UnsafeBufferEx textBuffer;

        private DirectBufferEx decodeInput;
        private final JsonGeneratorEx controlGenerator;
        private byte[] controlBytes;
        private UnsafeBufferEx controlBuffer;
        private final StringBuilder controlKey = new StringBuilder();
        private final String[] controlSeen = new String[CONTROL_SEEN_MAX];
        private final int[] controlSeenMessage = new int[CONTROL_SEEN_MAX];
        private int controlSeenCount;
        private int controlSlot = NO_SLOT;
        private int controlSlotOffset;
        private int controlEntry;
        private int controlValue;
        private int controlFrom = NONE;
        private int controlMessage;
        private int controlDepth;
        private LlmGeminiResponseDecoder controlThen;
        private JsonObject usageMerged;

        private LlmGeminiStream(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long authorization,
            long affinity,
            LlmBindingConfig binding,
            String model,
            String dialect,
            String operation)
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
            this.operation = operation;
            this.crossDialect = !NAME.equals(dialect);
            this.http = new LlmGeminiHttpStream(this);
            this.parser = JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
            this.textBytes = new byte[1024];
            this.textBuffer = new UnsafeBufferEx(textBytes);
            this.decoder = decodeStart;
            this.controlGenerator = JsonEx.createGenerator();
            this.controlBytes = new byte[CONTROL_HEADER];
            this.controlBuffer = new UnsafeBufferEx(controlBytes);
        }

        private int replyCredit()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - replyPad);
        }

        private int replyWindow()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - controlSlotOffset -
                EVENT_FRAMES * replyPad - sectionClosing());
        }

        private int sectionClosing()
        {
            return sectionOpen ? SECTION_SUFFIX.length + replyPad + signatureClosing() : 0;
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
                final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);
                final LlmSectionDataExFW section = llmDataEx != null && llmDataEx.kind() == LlmDataExFW.KIND_SECTION
                    ? llmDataEx.section()
                    : null;

                if (Flags.hasInit(flags))
                {
                    appKind = llmDataEx != null ? llmDataEx.kind() : LlmDataExFW.KIND_SECTION;

                    if (appKind == LlmDataExFW.KIND_SECTION)
                    {
                        sectionAttributes.readFrom(section);

                        final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                        requestSection = type;

                        http.doEncodeSectionBegin(
                            sectionKind(type),
                            section != null ? section.message() : NO_MESSAGE,
                            sectionAttributes.toolCallId());
                    }
                    else
                    {
                        http.doEncodeDialectDropped();
                    }
                }
                else
                {
                    sectionAttributes.mergeFrom(section);
                }

                final OctetsFW payload = data.payload();
                final boolean fin = Flags.hasFin(flags);

                if (payload != null && payload.sizeof() > 0)
                {
                    http.doEncodeSectionData(payload.buffer(), payload.offset(), payload.sizeof(), fin);
                }
                else if (fin && (http.sectionStreaming || http.encodeText != null))
                {
                    http.doEncodeSectionData(EMPTY_OCTETS.buffer(), 0, 0, true);
                }

                if (fin)
                {
                    http.doEncodeSectionEnd(LlmSectionAttributes.TRUE.equals(sectionAttributes.isError()));
                }

                if (http.encodeFailed)
                {
                    doAppReset(traceId, STATUS_BAD_REQUEST, ERROR_TYPE_INVALID_REQUEST, rejection());
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

            http.doEncodeRequestEnd(NO_MAX_OUTPUT_TOKENS);

            if (http.encodeFailed)
            {
                doAppReset(traceId, STATUS_BAD_REQUEST, ERROR_TYPE_INVALID_REQUEST, MESSAGE_SECTION_INVALID);
                http.doNetAbort(traceId);
            }
            else
            {
                http.flushEncode(traceId);
            }
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();

            final LlmFlushExFW llmFlushEx = flush.extension().get(llmFlushExRO::tryWrap);
            final int maxOutputTokens = llmFlushEx != null && llmFlushEx.typeId() == llmTypeId &&
                llmFlushEx.kind() == LlmFlushExFW.KIND_DIALECT
                ? llmFlushEx.dialect().maxOutputTokens()
                : NO_MAX_OUTPUT_TOKENS;

            http.doEncodeRequestEnd(maxOutputTokens);
            http.flushEncode(traceId);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            initialSeq = abort.sequence();
            state = LlmState.closeInitial(state);

            http.doNetAbort(traceId);
            cleanupControlSlot();
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
            cleanupControlSlot();
            http.cleanupEncodeSlot();
            http.cleanupDecodeSlot();
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            http.doNetChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private String rejection()
        {
            return requestSection == null || requestSection == LlmSectionType.NONE
                ? MESSAGE_SECTION_INVALID
                : MESSAGE_SECTION_CONVERT.formatted(requestSection.name().toLowerCase().replace('_', '-'), dialect, NAME);
        }

        private void flushAppWindow(
            long traceId)
        {
            final int credit = Math.max(0, (encodeMax - http.encodeSlotOffset - ENCODE_SLACK) / ESCAPE_EXPANSION);
            final long target = initialSeq + credit;

            if (http.initialMax > 0 &&
                !LlmState.initialClosing(state) &&
                !LlmState.initialClosed(state) &&
                target > initialAck + initialMax)
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
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final OctetsFW extension;

            if (Flags.hasInit(flags))
            {
                final LlmDataExFW llmDataEx = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .section(x ->
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
                    })
                    .build();

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

        private String id()
        {
            return documentId != null ? documentId : id;
        }

        private String servedModel()
        {
            return documentServedModel != null ? documentServedModel : servedModel;
        }

        private String finishReason()
        {
            final String reported = documentFinishReason != null ? documentFinishReason : finishReason;

            String neutral = reported;

            if (reported != null)
            {
                neutral = switch (reported)
                {
                case "STOP" -> toolCalls > 0 ? "tool-call" : "stop";
                case "MAX_TOKENS" -> "length";
                case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY", "LANGUAGE" ->
                    "content-filter";
                default -> reported;
                };
            }

            return neutral;
        }

        private void populateUsage(
            LlmUsageFW.Builder usage)
        {
            final boolean generated = candidatesTokens != NONE || thoughtsTokens != NONE;
            final int output = generated ? Math.max(candidatesTokens, 0) + Math.max(thoughtsTokens, 0) : NONE;

            usage.inputTokens(promptTokens)
                .cacheWriteTokens(NONE)
                .cacheReadTokens(cachedTokens)
                .outputTokens(output)
                .reasoningTokens(thoughtsTokens)
                .totalTokens(totalTokens);
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
            emitUsage();
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
            highSurrogate = 0;
            cleanupControlSlot();
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
            cancelControl();
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
                final int window = Math.min(limit - progress, replyWindow());

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
            decodeInput = buffer;

            if (controlFrom != NONE)
            {
                controlFrom = offset;
            }

            if (decoder == decodeCapture)
            {
                captureFrom = offset;
            }

            parser.wrap(buffer, offset, windowLimit, windowLast);

            LlmGeminiResponseDecoder previous = null;
            while (previous != decoder && decoder != decodeEnd && !parseRejected)
            {
                previous = decoder;
                decoder.decode(buffer, offset, windowLimit);
            }

            if (decoder == decodeCapture)
            {
                flushCapture(buffer);
            }

            int progress = position();

            if (decoder == decodeEnd)
            {
                while (progress < windowLimit && isWhitespace(buffer.getByte(progress)))
                {
                    progress++;
                }
            }
            else
            {
                flushControl(progress);
            }

            return parseRejected ? windowLimit : progress;
        }

        private int position()
        {
            return decodeLimit - parser.remaining();
        }

        private boolean hasNextEvent()
        {
            return parser.hasNextEvent() && (!begun() || draining || eventSlack() >= EVENT_FRAMES * replyPad);
        }

        private int eventSlack()
        {
            final int pending = controlFrom != NONE ? position() - controlFrom + CONTROL_CLOSING : 0;

            return replyMax - (int) (replySeq - replyAck) - controlSlotOffset - pending - parser.remaining() - sectionClosing();
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
            int memberDepth = 1;
            String scanKey = null;
            boolean found = false;

            scanError = false;

            while (!found && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    memberDepth = scanDepth == 0 && event == JsonEvent.START_ARRAY ? 2 : memberDepth;
                    scanDepth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    scanDepth--;
                    found = scanDepth < memberDepth;
                    break;
                case KEY_NAME:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(0);
                    }
                    else
                    {
                        scanKey = parser.getString();
                        scanError = scanDepth == memberDepth && MEMBER_ERROR.equals(scanKey);
                        found = scanError;
                    }
                    break;
                case VALUE_STRING:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                    }
                    else if (scanKey != null && scanDepth == memberDepth)
                    {
                        found = scanValue(scanKey);
                    }
                    break;
                default:
                    break;
                }
            }

            parser.reset();

            return found;
        }

        private boolean scanValue(
            String scanKey)
        {
            if (MEMBER_RESPONSE_ID.equals(scanKey))
            {
                documentId = parser.getString();
            }
            else if (MEMBER_MODEL_VERSION.equals(scanKey))
            {
                documentServedModel = parser.getString();
            }

            return documentId != null && documentServedModel != null;
        }

        private int decodeStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
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
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    rootThen = decodeEnd;
                    decoder = decodeRoot;
                }
                else if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeChunks;
                }
                else
                {
                    parseRejected = true;
                }
            }

            return position();
        }

        private int decodeChunks(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeChunks && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_ARRAY)
                {
                    decoder = decodeEnd;
                }
                else if (event == JsonEvent.START_OBJECT)
                {
                    rootThen = decodeChunks;
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
            while (decoder == decodeRoot && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    commitDocument();
                    decoder = rootThen;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_CANDIDATES.contentEquals(member))
                    {
                        decoder = decodeCandidatesStart;
                    }
                    else if (MEMBER_USAGE.contentEquals(member))
                    {
                        usageThen = decodeRoot;
                        beginUsage();
                    }
                    else if (MEMBER_MODEL_VERSION.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeModel;
                    }
                    else if (MEMBER_RESPONSE_ID.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeId;
                    }
                    else if (MEMBER_ERROR.contentEquals(member))
                    {
                        errorThen = decodeRoot;
                        decoder = decodeErrorObjectStart;
                    }
                    else
                    {
                        beginControl(member, ROOT_CONTROL, decodeRoot);
                    }
                }
            }

            return position();
        }

        private int decodeCandidatesStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeCandidates;
                }
                else
                {
                    skipAfter(event, decodeRoot);
                }
            }

            return position();
        }

        private int decodeCandidates(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeCandidates && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_ARRAY)
                {
                    decoder = decodeRoot;
                }
                else if (event == JsonEvent.START_OBJECT && candidateOrdinal++ == 0)
                {
                    decoder = decodeCandidate;
                }
                else
                {
                    skipAfter(event, decodeCandidates);
                }
            }

            return position();
        }

        private int decodeCandidate(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeCandidate && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeCandidates;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_CONTENT.contentEquals(member))
                    {
                        decoder = decodeContentStart;
                    }
                    else if (KEY_FINISH_REASON.contentEquals(member))
                    {
                        valueThen = decodeCandidate;
                        decoder = decodeFinishReason;
                    }
                    else if (MEMBER_INDEX.contentEquals(member))
                    {
                        skipMember(decodeCandidate);
                    }
                    else
                    {
                        beginControl(member, ROOT_CONTROL, decodeCandidate);
                    }
                }
            }

            return position();
        }

        private int decodeContentStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeContent;
                }
                else
                {
                    skipAfter(event, decodeCandidate);
                }
            }

            return position();
        }

        private int decodeContent(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeContent && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeCandidate;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (MEMBER_PARTS.contentEquals(parser.getStringView()))
                {
                    decoder = decodePartsStart;
                }
                else
                {
                    skipMember(decodeContent);
                }
            }

            return position();
        }

        private int decodePartsStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeParts;
                }
                else
                {
                    skipAfter(event, decodeContent);
                }
            }

            return position();
        }

        private int decodeParts(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeParts && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_ARRAY)
                {
                    decoder = decodeContent;
                }
                else if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodePart;
                }
                else
                {
                    skipAfter(event, decodeParts);
                }
            }

            return position();
        }

        private int decodePart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodePart && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    if (sectionType == LlmSectionType.TOOL_CALL)
                    {
                        closeSection();
                    }

                    decoder = decodeParts;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (MEMBER_TEXT.contentEquals(parser.getStringView()))
                {
                    beginText(LlmSectionType.ASSISTANT_TEXT, decodePart);
                }
                else if (MEMBER_FUNCTION_CALL.contentEquals(parser.getStringView()))
                {
                    decoder = decodeCallStart;
                }
                else if (MEMBER_SIGNATURE.contentEquals(parser.getStringView()))
                {
                    signatureBuilder.setLength(0);
                    decoder = decodeSignature;
                }
                else
                {
                    skipMember(decodePart);
                }
            }

            return position();
        }

        private int decodeSignature(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeSignature && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();
                    final CharSequence chars = parser.getStringView();
                    final int length = chars.length();

                    signatureBuilder.append(chars);

                    if (deferred)
                    {
                        parser.consumed(length);
                    }
                    else
                    {
                        sectionSignature = sectionOpen ? signatureBuilder.toString() : null;
                        signatureBuilder.setLength(0);
                        decoder = decodePart;
                    }
                }
                else
                {
                    skipAfter(event, decodePart);
                }
            }

            return position();
        }

        private int decodeCallStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    callName = null;
                    callId = null;
                    callArguments = NONE;
                    decoder = decodeCall;
                }
                else
                {
                    skipAfter(event, decodePart);
                }
            }

            return position();
        }

        private int decodeCall(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeCall && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    endCall();
                    decoder = decodePart;
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
                        valueThen = decodeCall;
                        decoder = decodeCallName;
                    }
                    else if (MEMBER_ID.contentEquals(member))
                    {
                        valueThen = decodeCall;
                        decoder = decodeCallId;
                    }
                    else if (MEMBER_ARGS.contentEquals(member))
                    {
                        decoder = decodeCallArguments;
                    }
                    else
                    {
                        skipMember(decodeCall);
                    }
                }
            }

            return position();
        }

        private int decodeCallName(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeCallName && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    callName = value;
                }
            }

            return position();
        }

        private int decodeCallId(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeCallId && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    callId = value;
                }
            }

            return position();
        }

        private int decodeCallArguments(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT && callName != null)
                {
                    openCall();
                    callArguments = 0;
                    sectionStarted = true;
                    writeSection(ARGUMENTS_PREFIX, 0, ARGUMENTS_PREFIX.capacity(), false);

                    captureThen = decodeCall;
                    captureDepth = 1;
                    captureFrom = position() - 1;
                    decoder = decodeCapture;
                }
                else if (callName == null)
                {
                    parseRejected = true;
                }
                else
                {
                    skipAfter(event, decodeCall);
                }
            }

            return position();
        }

        private void endCall()
        {
            if (callArguments != NONE && callId != null && !callId.equals(sectionToolCallId))
            {
                writeSection(textBuffer, 0, encodeWireId(callId), false);
            }

            if (callArguments == NONE)
            {
                if (callName == null)
                {
                    parseRejected = true;
                }
                else
                {
                    openCall();
                }
            }
        }

        private void openCall()
        {
            final String toolCallId = callId != null ? callId : TOOL_CALL_ID_PREFIX + callOrdinal++;

            closeSection();
            openSection(LlmSectionType.TOOL_CALL, toolCallId, callName);
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

                captureDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (captureDepth == 0)
                {
                    flushCapture(buffer);
                    decoder = captureThen;
                }
            }

            return position();
        }

        private void flushCapture(
            DirectBufferEx buffer)
        {
            final int end = position();

            if (end > captureFrom)
            {
                writeSection(buffer, captureFrom, end - captureFrom, false);
                callArguments = end - captureFrom;
            }

            captureFrom = end;
        }

        private int decodeBlockText(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int eventFrom = position();

            while (decoder == decodeBlockText && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitText(deferred, eventFrom);

                    if (!deferred)
                    {
                        decoder = textThen;
                    }
                }
                else
                {
                    skipAfter(event, textThen);
                }

                eventFrom = position();
            }

            return position();
        }

        private int decodeFinishReason(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeFinishReason && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentFinishReason = value;
                }
            }

            return position();
        }

        private int decodeId(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeId && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen, id);

                if (value != null)
                {
                    documentId = value;
                }
            }

            return position();
        }

        private int decodeModel(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeModel && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen, servedModel);

                if (value != null)
                {
                    documentServedModel = value;
                }
            }

            return position();
        }

        private int decodeUsageStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeUsage;
                }
                else
                {
                    cancelControl();
                    skipAfter(event, usageThen);
                }
            }

            return position();
        }

        private int decodeUsage(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeUsage && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    endUsage();
                    decoder = usageThen;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (USAGE_PROMPT_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_PROMPT;
                        decoder = decodeUsageValue;
                    }
                    else if (USAGE_CANDIDATES_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_CANDIDATES;
                        decoder = decodeUsageValue;
                    }
                    else if (USAGE_CACHED_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_CACHED;
                        decoder = decodeUsageValue;
                    }
                    else if (USAGE_THOUGHTS_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_THOUGHTS;
                        decoder = decodeUsageValue;
                    }
                    else if (USAGE_TOTAL_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_TOTAL;
                        decoder = decodeUsageValue;
                    }
                    else
                    {
                        skipMember(decodeUsage);
                    }
                }
            }

            return position();
        }

        private int decodeUsageValue(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeUsageValue && hasNextEvent())
            {
                final int value = intValue(parser.nextEvent(), decodeUsage);

                if (value != NONE)
                {
                    switch (usageField)
                    {
                    case USAGE_PROMPT:
                        documentPromptTokens = value;
                        break;
                    case USAGE_CANDIDATES:
                        documentCandidatesTokens = value;
                        break;
                    case USAGE_CACHED:
                        documentCachedTokens = value;
                        break;
                    case USAGE_THOUGHTS:
                        documentThoughtsTokens = value;
                        break;
                    default:
                        documentTotalTokens = value;
                        break;
                    }
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
                else if (MEMBER_USAGE.contentEquals(parser.getStringView()))
                {
                    usageThen = decodeDrainRoot;
                    decoder = decodeUsageStart;
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
            while (decoder == decodeErrorRootStart && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeErrorRoot;
                }
                else if (event != JsonEvent.START_ARRAY)
                {
                    decoder = decodeIgnore;
                }
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
                    decoder = decodeIgnore;
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

                    if (MEMBER_STATUS.contentEquals(member))
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

        private String stringValue(
            JsonEvent event,
            LlmGeminiResponseDecoder then)
        {
            return stringValue(event, then, null);
        }


        private String stringValue(
            JsonEvent event,
            LlmGeminiResponseDecoder then,
            String known)
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
                value = known != null && known.contentEquals(parser.getStringView()) ? known : parser.getString();
                decoder = then;
            }

            return value;
        }

        private int intValue(
            JsonEvent event,
            LlmGeminiResponseDecoder then)
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
            LlmGeminiResponseDecoder then)
        {
            skipDepth = 0;
            skipThen = then;
            decoder = decodeSkip;
        }

        private void skipAfter(
            JsonEvent event,
            LlmGeminiResponseDecoder then)
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

        private void beginText(
            LlmSectionType type,
            LlmGeminiResponseDecoder then)
        {
            if (sectionOpen && sectionType != type)
            {
                closeSection();
            }

            textThen = then;
            decoder = decodeBlockText;
        }

        private void emitText(
            boolean deferred,
            int eventFrom)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            if (sectionOpen || length > 0 || deferred)
            {
                if (!sectionOpen)
                {
                    openSection(LlmSectionType.ASSISTANT_TEXT, null, null);
                }

                emitStreamed(encodeText(chars, false));
            }

            if (deferred)
            {
                parser.consumed(length);
            }
        }

        private void emitStreamed(
            int length)
        {
            if (length > 0)
            {
                sectionStarted = true;

                writeSection(textBuffer, 0, length, false);
            }
        }

        private void beginControl(
            CharSequence member,
            int message,
            LlmGeminiResponseDecoder then)
        {
            if (http.framing && controlCarried(member, message))
            {
                skipMember(then);
            }
            else
            {
                openControl(member, message, then);
            }
        }

        private void openControl(
            CharSequence member,
            int message,
            LlmGeminiResponseDecoder then)
        {
            controlKey.setLength(0);
            controlKey.append(NAME).append(':').append(member);

            final int capacity = controlKey.length() * TEXT_ESCAPE_EXPANSION + CONTROL_HEADER;

            if (controlBytes.length < capacity)
            {
                controlBytes = new byte[capacity];
                controlBuffer = new UnsafeBufferEx(controlBytes);
            }

            controlGenerator.wrap(controlBuffer, 0, controlBytes.length);
            controlGenerator.reset();
            controlGenerator.writeStartObject().writeKey(controlKey);

            if (controlSlot == NO_SLOT)
            {
                controlSlot = encodePool.acquire(replyId);
            }

            if (controlSlot == NO_SLOT || controlSlotOffset + CONTROL_HEADER + controlGenerator.length() > encodeMax)
            {
                cleanup(http.decodeTraceId);
            }
            else
            {
                controlEntry = controlSlotOffset;
                controlSlotOffset += CONTROL_HEADER;
                encodePool.buffer(controlSlot).putBytes(controlSlotOffset, controlBuffer, 0, controlGenerator.length());
                controlSlotOffset += controlGenerator.length();
                controlValue = controlSlotOffset;
                controlMessage = message;
                controlThen = then;
                controlDepth = 0;
                controlFrom = position();
                decoder = decodeControlValue;
            }
        }

        private boolean controlCarried(
            CharSequence member,
            int message)
        {
            boolean carried = false;

            for (int index = 0; index < controlSeenCount && !carried; index++)
            {
                carried = controlSeenMessage[index] == message && member.toString().equals(controlSeen[index]);
            }

            if (!carried && controlSeenCount < CONTROL_SEEN_MAX)
            {
                controlSeen[controlSeenCount] = member.toString();
                controlSeenMessage[controlSeenCount] = message;
                controlSeenCount++;
            }

            return carried;
        }

        private int decodeControlValue(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeControlValue && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                final boolean deferred = deferredScalar(event);

                controlDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (controlDepth == 0)
                {
                    endControl();
                    decoder = controlThen;
                }
            }

            return position();
        }

        private int decodeControlStream(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeControlStream && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                final boolean deferred = deferredScalar(event);

                controlDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (controlDepth == 0)
                {
                    endControlStream();
                    decoder = controlThen;
                }
            }

            return position();
        }

        private void flushControl(
            int to)
        {
            if (controlFrom != NONE && to > controlFrom)
            {
                if (decoder == decodeControlValue && controlSlotOffset + to - controlFrom + CONTROL_CLOSING > controlHoldMax())
                {
                    beginControlStream();
                }

                if (decoder == decodeControlStream)
                {
                    doAppFrame(http.decodeTraceId, Flags.NONE, LlmSectionType.CONTROL, controlMessage, null, null,
                        decodeInput, controlFrom, to - controlFrom);
                }
                else
                {
                    int from = controlFrom;

                    while (controlSlotOffset == controlValue && from < to &&
                        (isWhitespace(decodeInput.getByte(from)) || decodeInput.getByte(from) == ':'))
                    {
                        from++;
                    }

                    encodePool.buffer(controlSlot).putBytes(controlSlotOffset, decodeInput, from, to - from);
                    controlSlotOffset += to - from;
                }

                controlFrom = to;
            }
        }

        private int controlHoldMax()
        {
            return Math.min(encodeMax, replyMax) - EVENT_FRAMES * replyPad;
        }

        private void beginControlStream()
        {
            if (sectionOpen)
            {
                cleanup(http.decodeTraceId);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(controlSlot);

                for (int index = 0; index < controlEntry; )
                {
                    final int message = slot.getInt(index);
                    final int length = slot.getInt(index + Integer.BYTES);

                    doAppFrame(http.decodeTraceId, Flags.COMPLETE, LlmSectionType.CONTROL, message, null, null,
                        slot, index + CONTROL_HEADER, length);

                    index += CONTROL_HEADER + length;
                }

                doAppFrame(http.decodeTraceId, Flags.INIT, LlmSectionType.CONTROL, controlMessage, null, null,
                    slot, controlEntry + CONTROL_HEADER, controlSlotOffset - controlEntry - CONTROL_HEADER);

                cleanupControlSlot();
                decoder = decodeControlStream;
            }
        }

        private void endControlStream()
        {
            flushControl(position());

            doAppFrame(http.decodeTraceId, Flags.FIN, LlmSectionType.CONTROL, controlMessage, null, null,
                CONTROL_TERMINATOR, 0, CONTROL_CLOSING);

            controlFrom = NONE;
        }

        private void endControl()
        {
            flushControl(position());

            final MutableDirectBufferEx slot = encodePool.buffer(controlSlot);

            slot.putByte(controlSlotOffset, (byte) '}');
            controlSlotOffset += CONTROL_CLOSING;
            slot.putInt(controlEntry, controlMessage);
            slot.putInt(controlEntry + Integer.BYTES, controlSlotOffset - controlEntry - CONTROL_HEADER);
            controlFrom = NONE;

            if (!sectionOpen)
            {
                emitControls();
            }
        }

        private void cancelControl()
        {
            if (controlFrom != NONE)
            {
                controlSlotOffset = controlEntry;
                controlFrom = NONE;

                if (controlSlotOffset == 0)
                {
                    cleanupControlSlot();
                }
            }
        }

        private void emitControls()
        {
            if (controlSlot != NO_SLOT && controlFrom == NONE)
            {
                final MutableDirectBufferEx slot = encodePool.buffer(controlSlot);

                for (int index = 0; index < controlSlotOffset; )
                {
                    final int message = slot.getInt(index);
                    final int length = slot.getInt(index + Integer.BYTES);

                    doAppFrame(http.decodeTraceId, Flags.COMPLETE, LlmSectionType.CONTROL, message, null, null,
                        slot, index + CONTROL_HEADER, length);

                    index += CONTROL_HEADER + length;
                }

                cleanupControlSlot();
            }
        }

        private void cleanupControlSlot()
        {
            if (controlSlot != NO_SLOT)
            {
                encodePool.release(controlSlot);
                controlSlot = NO_SLOT;
                controlSlotOffset = 0;
            }
        }

        private void beginUsage()
        {
            openControl(MEMBER_USAGE, ROOT_CONTROL, usageThen);
            decoder = decodeUsageStart;
        }

        private void endUsage()
        {
            if (controlFrom != NONE)
            {
                flushControl(position());

                final MutableDirectBufferEx slot = encodePool.buffer(controlSlot);
                final int from = controlEntry + CONTROL_HEADER;
                final String member = slot.getStringWithoutLengthUtf8(from, controlSlotOffset - from) + "}";

                try (JsonReader reader = Json.createReader(new StringReader(member)))
                {
                    final JsonObjectBuilder merged = usageMerged != null
                        ? Json.createObjectBuilder(usageMerged)
                        : Json.createObjectBuilder();

                    reader.readObject().getJsonObject(NAME + ':' + MEMBER_USAGE).forEach(merged::add);
                    usageMerged = merged.build();
                }

                cancelControl();
            }
        }

        private void emitUsage()
        {
            if (usageMerged != null)
            {
                final byte[] member = ("{\"" + NAME + ':' + MEMBER_USAGE + "\":" + usageMerged + "}").getBytes(UTF_8);
                final UnsafeBufferEx buffer = new UnsafeBufferEx(member);

                doAppFrame(http.decodeTraceId, Flags.COMPLETE, LlmSectionType.CONTROL, ROOT_CONTROL, null, null,
                    buffer, 0, member.length);

                usageMerged = null;
            }
        }

        private void openSection(
            LlmSectionType type,
            String toolCallId,
            String toolName)
        {
            emitControls();

            sectionOpen = true;
            sectionStarted = false;
            sectionWritten = false;
            sectionType = type;
            sectionToolCallId = toolCallId;
            sectionToolName = toolName;
            sectionSignature = null;

            if (type == LlmSectionType.TOOL_CALL)
            {
                toolCalls++;
            }
        }

        private void closeSection()
        {
            if (sectionOpen && sectionType == LlmSectionType.TOOL_CALL && sectionSignature != null)
            {
                final int encoded = encodeToolCallEnd();

                sectionOpen = false;
                writeSection(textBuffer, 0, encoded, true);
            }
            else if (sectionOpen && sectionType == LlmSectionType.TOOL_CALL)
            {
                final DirectBufferEx end = sectionStarted ? ARGUMENTS_SUFFIX : ARGUMENTS_EMPTY;

                sectionOpen = false;
                writeSection(end, 0, end.capacity(), true);
            }
            else if (sectionOpen && sectionStarted)
            {
                final int encoded = encodeText("", true);

                sectionOpen = false;
                writeSection(textBuffer, 0, encoded, true);
            }

            sectionOpen = false;

            emitControls();
        }

        private void writeSection(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            final int flags = sectionWritten ? Flags.NONE : Flags.INIT;

            sectionWritten = true;

            doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, sectionType, 0, sectionToolCallId,
                sectionToolName, buffer, offset, length);
        }

        private void commitDocument()
        {
            promptTokens = documentPromptTokens != NONE ? documentPromptTokens : promptTokens;
            candidatesTokens = documentCandidatesTokens != NONE ? documentCandidatesTokens : candidatesTokens;
            cachedTokens = documentCachedTokens != NONE ? documentCachedTokens : cachedTokens;
            thoughtsTokens = documentThoughtsTokens != NONE ? documentThoughtsTokens : thoughtsTokens;
            totalTokens = documentTotalTokens != NONE ? documentTotalTokens : totalTokens;
            errorType = documentErrorType != null ? documentErrorType : errorType;
            errorMessage = documentErrorMessage != null ? documentErrorMessage : errorMessage;
            id = documentId != null ? documentId : id;
            servedModel = documentServedModel != null ? documentServedModel : servedModel;
            finishReason = documentFinishReason != null ? documentFinishReason : finishReason;

            documentPromptTokens = NONE;
            documentCandidatesTokens = NONE;
            documentCachedTokens = NONE;
            documentThoughtsTokens = NONE;
            documentTotalTokens = NONE;
            documentErrorType = null;
            documentErrorMessage = null;
            documentId = null;
            documentServedModel = null;
            documentFinishReason = null;
            candidateOrdinal = 0;
        }

        private int encodeText(
            CharSequence chars,
            boolean last)
        {
            final int length = chars.length();
            final int capacity = length * TEXT_ESCAPE_EXPANSION + TEXT_FRAMING + (last ? signatureRoom() : 0);

            if (textBytes.length < capacity)
            {
                textBytes = new byte[capacity];
                textBuffer = new UnsafeBufferEx(textBytes);
            }

            int position = 0;
            int index = 0;

            if (!sectionStarted)
            {
                position = putMember(position);
            }

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
                    position = putEscaped(c, position);
                }
            }

            if (last && highSurrogate != 0)
            {
                position = putCodePoint(REPLACEMENT_CHARACTER, position);
                highSurrogate = 0;
            }

            if (last)
            {
                textBytes[position++] = '"';
                position = putSignature(position);
                textBytes[position++] = '}';
            }

            return position;
        }

        private int signatureRoom()
        {
            return sectionSignature != null ? sectionSignature.length() + MEMBER_WIRE_SIGNATURE.length() + 2 : 0;
        }

        private int signatureClosing()
        {
            return signatureRoom();
        }

        private int putSignature(
            int position)
        {
            int next = position;

            if (sectionSignature != null)
            {
                for (int i = 0; i < MEMBER_WIRE_SIGNATURE.length(); i++)
                {
                    textBytes[next++] = (byte) MEMBER_WIRE_SIGNATURE.charAt(i);
                }

                for (int i = 0; i < sectionSignature.length(); i++)
                {
                    next = putEscaped(sectionSignature.charAt(i), next);
                }

                textBytes[next++] = '"';
                sectionSignature = null;
            }

            return next;
        }

        private int encodeToolCallEnd()
        {
            final int capacity = signatureRoom() + ARGUMENTS_EMPTY.capacity() + TEXT_FRAMING;

            if (textBytes.length < capacity)
            {
                textBytes = new byte[capacity];
                textBuffer = new UnsafeBufferEx(textBytes);
            }

            int position = 0;

            if (!sectionStarted)
            {
                for (int i = 0; i < ARGUMENTS_PREFIX.capacity(); i++)
                {
                    textBytes[position++] = ARGUMENTS_PREFIX.getByte(i);
                }

                textBytes[position++] = '{';
                textBytes[position++] = '}';
            }

            position = putSignature(position);
            textBytes[position++] = '}';

            return position;
        }

        private int encodeWireId(
            String wireId)
        {
            final int capacity = wireId.length() * TEXT_ESCAPE_EXPANSION + TEXT_FRAMING;

            if (textBytes.length < capacity)
            {
                textBytes = new byte[capacity];
                textBuffer = new UnsafeBufferEx(textBytes);
            }

            int position = 0;

            for (int i = 0; i < MEMBER_WIRE_ID.length(); i++)
            {
                textBytes[position++] = (byte) MEMBER_WIRE_ID.charAt(i);
            }

            for (int index = 0; index < wireId.length(); index++)
            {
                position = putEscaped(wireId.charAt(index), position);
            }

            textBytes[position++] = '"';

            return position;
        }

        private int putMember(
            int position)
        {
            int next = position;

            textBytes[next++] = '{';
            textBytes[next++] = '"';

            for (int i = 0; i < MEMBER_TEXT.length(); i++)
            {
                textBytes[next++] = (byte) MEMBER_TEXT.charAt(i);
            }

            textBytes[next++] = '"';
            textBytes[next++] = ':';
            textBytes[next++] = '"';

            return next;
        }

        private int putEscaped(
            char c,
            int position)
        {
            int next = position;

            switch (c)
            {
            case '"':
            case '\\':
                textBytes[next++] = '\\';
                textBytes[next++] = (byte) c;
                break;
            case '\n':
                textBytes[next++] = '\\';
                textBytes[next++] = 'n';
                break;
            case '\r':
                textBytes[next++] = '\\';
                textBytes[next++] = 'r';
                break;
            case '\t':
                textBytes[next++] = '\\';
                textBytes[next++] = 't';
                break;
            case '\b':
                textBytes[next++] = '\\';
                textBytes[next++] = 'b';
                break;
            case '\f':
                textBytes[next++] = '\\';
                textBytes[next++] = 'f';
                break;
            default:
                if (c < 0x20)
                {
                    textBytes[next++] = '\\';
                    textBytes[next++] = 'u';
                    textBytes[next++] = '0';
                    textBytes[next++] = '0';
                    textBytes[next++] = (byte) Character.forDigit(c >> 4, 16);
                    textBytes[next++] = (byte) Character.forDigit(c & 0xF, 16);
                }
                else
                {
                    next = putCodePoint(c, next);
                }
                break;
            }

            return next;
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
                    cleanupControlSlot();
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
                cleanupControlSlot();
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
            doAppWindow(traceId, initialMax);
        }

        private void doAppWindow(
            long traceId,
            int maximum)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(maximum)
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
            cleanupControlSlot();
            http.cleanupEncodeSlot();
            http.cleanupDecodeSlot();
        }
    }

    private final class LlmGeminiHttpStream
    {
        private final HttpDataExFW httpDataExRO = new HttpDataExFW();
        private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
        private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();
        private final LlmGeminiStream stream;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String scheme;
        private final String authority;
        private final String requestPath;

        private final JsonGeneratorEx generator;

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
        private boolean encodeFailed;

        private int region = REGION_NONE;
        private LlmTextPipeline encodeText;
        private final LlmTextPipeline sectionText = new LlmTextPipeline(this::onSectionText);
        private boolean contentsOpened;
        private boolean systemOpened;
        private boolean toolsOpened;
        private int messageIndex = NO_MESSAGE;
        private int messageRole = ROLE_NONE;
        private boolean textBlockOpen;
        private int sectionKind;
        private boolean sectionOpen;
        private boolean translated;
        private boolean sectionStreaming;
        private boolean rawOpen;
        private LlmSectionPipeline sectionPipeline;
        private LlmSectionPipeline toolDefinitionPipeline;
        private LlmSectionPipeline toolCallPipeline;
        private final LlmSectionPipeline[] mediaPipelines = new LlmSectionPipeline[MEDIA_KINDS];
        private LlmGeminiToolCallFromCanonical toolCallTransform;
        private int messageToolCalls;
        private int partDepth = 1;
        private final String[] callIds = new String[CALLS_MAX];
        private final String[] callNames = new String[CALLS_MAX];
        private int callCount;
        private LlmSectionPipeline controlPipeline;
        private LlmGeminiControlFromCanonical controlTransform;
        private int controlMessage;
        private int controlMode;
        private LlmSectionPipeline.Output sectionTarget;
        private final LlmSectionPipeline.Output sectionOutput = (buffer, offset, length, last) -> putRaw(buffer, offset, length);
        private final LlmSectionPipeline.Output controlOutput =
            (buffer, offset, length, last) -> putControl(buffer, offset, length);

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeRemainder;
        private boolean decodeRemainderLast;
        private boolean framing;
        private boolean errorJson;
        private int errorStatus = NO_ERROR;
        private boolean errorBodyDropped;
        private long decodeTraceId;
        private long pendingEndTraceId;
        private boolean responseStarted;

        private LlmGeminiHttpStream(
            LlmGeminiStream stream)
        {
            final LlmServerConfig server = stream.binding.options.server;
            final boolean generate = OPERATION_GENERATE.equals(stream.operation);

            this.stream = stream;
            this.originId = stream.routedId;
            this.routedId = stream.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.scheme = server.scheme;
            this.authority = server.host + ":" + server.port;
            this.requestPath = server.path + PATH_MODELS + stream.model + ':' +
                (generate ? OPERATION_GENERATE : OPERATION_STREAM + QUERY_ALT_SSE);
            this.generator = JsonEx.createGenerator();
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
                httpBeginExBuilder.headersItem(h -> h.name(HEADER_API_KEY).value(credentials));
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
            if (beginEncode(ENCODE_STRUCTURE))
            {
                generator.writeStartObject();
                endEncode();
            }
        }

        private void doEncodeDialectDropped()
        {
            sectionOpen = false;
            translated = false;
            sectionKind = KIND_DROPPED;
        }

        private LlmSectionPipeline toolCallPipeline()
        {
            if (toolCallPipeline == null)
            {
                toolCallTransform = new LlmGeminiToolCallFromCanonical();
                toolCallPipeline = new LlmSectionPipeline(toolCallTransform, transformBuffer);
            }

            return toolCallPipeline;
        }

        private LlmSectionPipeline mediaPipeline(
            int kind)
        {
            final int index = switch (kind)
            {
            case KIND_USER_IMAGE -> 0;
            case KIND_USER_AUDIO -> 1;
            case KIND_USER_VIDEO -> 2;
            default -> 3;
            };

            if (mediaPipelines[index] == null)
            {
                final LlmGeminiMediaFromCanonical transform = switch (kind)
                {
                case KIND_USER_IMAGE -> LlmGeminiMediaFromCanonical.image();
                case KIND_USER_AUDIO -> LlmGeminiMediaFromCanonical.audio();
                case KIND_USER_VIDEO -> LlmGeminiMediaFromCanonical.video();
                default -> LlmGeminiMediaFromCanonical.document();
                };

                mediaPipelines[index] = new LlmSectionPipeline(transform, transformBuffer);
            }

            return mediaPipelines[index];
        }

        private LlmSectionPipeline toolDefinitionPipeline()
        {
            if (toolDefinitionPipeline == null)
            {
                toolDefinitionPipeline = new LlmSectionPipeline(new LlmGeminiToolDefinitionFromCanonical(), transformBuffer);
            }

            return toolDefinitionPipeline;
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmGeminiControlFromCanonical();
                controlPipeline = new LlmSectionPipeline(controlTransform, transformBuffer);
            }

            return controlPipeline;
        }

        private void onSectionText(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (beginEncode(0))
            {
                putEscaped(buffer, offset, length);
                endEncode();
            }
        }

        private void beginTextBlock()
        {
            generator.writeStartObject().writeKey(MEMBER_TEXT);
            generator.writeStartEscaped();
        }

        private void doEncodeSectionBegin(
            int kind,
            int message,
            String toolCallId)
        {
            sectionKind = kind;
            sectionOpen = false;
            translated = kind == KIND_CONTROL || kind == KIND_TOOL_DEFINITION || kind == KIND_TOOL_CALL || media(kind);

            if (kind == 0 || kind == KIND_UNSUPPORTED)
            {
                encodeFailed = true;
            }
            else if (beginEncode(ENCODE_STRUCTURE))
            {
                if (kind == KIND_CONTROL)
                {
                    controlMessage = message;
                    controlMode = CONTROL_PENDING;
                    beginSectionStreaming(controlPipeline(), controlOutput);
                }
                else if (kind == KIND_TOOL_DEFINITION)
                {
                    closeTextBlock();
                    openRegion(REGION_TOOLS);
                    beginSectionStreaming(toolDefinitionPipeline(), sectionOutput);
                }
                else
                {
                    closeTextBlock();

                    if (kind == KIND_SYSTEM_INSTRUCTION)
                    {
                        openRegion(REGION_SYSTEM);
                        beginTextBlock();
                        sectionOpen = !encodeFailed;
                    }
                    else
                    {
                        beginContentBlock(kind, message);
                    }
                }

                endEncode();
            }

            if (!encodeFailed && text(kind))
            {
                encodeText = sectionText;
                encodeText.begin();
            }
        }

        private void beginSectionStreaming(
            LlmSectionPipeline pipeline,
            LlmSectionPipeline.Output output)
        {
            sectionPipeline = pipeline;
            sectionTarget = output;
            sectionPipeline.begin(sectionAttributes);
            sectionStreaming = true;
        }

        private void beginContentBlock(
            int kind,
            int message)
        {
            openRegion(REGION_CONTENTS);

            final int role = role(kind);

            if (message != messageIndex || role != messageRole)
            {
                closeMessage();
                openMessage(role, message);
            }

            if (kind == KIND_TOOL_CALL)
            {
                toolCallPipeline();
                toolCallTransform.placeholder(messageToolCalls++ == 0);
                beginSectionStreaming(toolCallPipeline, sectionOutput);
            }
            else if (kind == KIND_TOOL_RESULT)
            {
                beginToolResult();
            }
            else if (media(kind))
            {
                beginSectionStreaming(mediaPipeline(kind), sectionOutput);
            }
            else
            {
                beginTextBlock();
                sectionOpen = !encodeFailed;
            }
        }

        private void beginToolResult()
        {
            final String toolCallId = sectionAttributes.toolCallId();
            final String toolName = toolNameOf(toolCallId);
            final boolean failed = LlmSectionAttributes.TRUE.equals(sectionAttributes.isError());

            if (toolName == null)
            {
                encodeFailed = true;
            }
            else
            {
                generator.writeStartObject()
                    .writeStartObject(MEMBER_FUNCTION_RESPONSE)
                    .write(MEMBER_NAME, toolName)
                    .write(MEMBER_ID, toolCallId)
                    .writeStartObject(MEMBER_RESPONSE)
                    .writeKey(failed ? MEMBER_ERROR_RESPONSE : MEMBER_OUTPUT);
                generator.writeStartEscaped();
                partDepth = 3;
                sectionOpen = true;
            }
        }

        private String toolNameOf(
            String toolCallId)
        {
            String toolName = null;

            for (int index = 0; toolName == null && toolCallId != null && index < callCount; index++)
            {
                if (toolCallId.equals(callIds[index]))
                {
                    toolName = callNames[index];
                }
            }

            return toolName;
        }

        private void recordCall(
            String toolCallId,
            String toolName)
        {
            if (toolCallId != null && toolName != null && callCount < CALLS_MAX)
            {
                callIds[callCount] = toolCallId;
                callNames[callCount] = toolName;
                callCount++;
            }
        }

        private void openRegion(
            int next)
        {
            if (region != next)
            {
                closeRegion();

                final boolean reopened = switch (next)
                {
                case REGION_CONTENTS -> contentsOpened;
                case REGION_SYSTEM -> systemOpened;
                default -> toolsOpened;
                };

                if (reopened)
                {
                    encodeFailed = true;
                }
                else
                {
                    switch (next)
                    {
                    case REGION_CONTENTS:
                        contentsOpened = true;
                        generator.writeStartArray(MEMBER_CONTENTS);
                        break;
                    case REGION_SYSTEM:
                        systemOpened = true;
                        generator.writeStartObject(MEMBER_SYSTEM_INSTRUCTION).writeStartArray(MEMBER_PARTS);
                        break;
                    default:
                        toolsOpened = true;
                        generator.writeStartArray(MEMBER_TOOLS).writeStartObject().writeStartArray(MEMBER_FUNCTION_DECLARATIONS);
                        break;
                    }

                    region = next;
                }
            }
        }

        private void closeRegion()
        {
            closeTextBlock();

            if (region == REGION_CONTENTS)
            {
                closeMessage();
            }

            if (region != REGION_NONE)
            {
                generator.writeEnd();

                if (region == REGION_SYSTEM)
                {
                    generator.writeEnd();
                }
                else if (region == REGION_TOOLS)
                {
                    generator.writeEnd().writeEnd();
                }

                region = REGION_NONE;
            }
        }

        private void openMessage(
            int role,
            int message)
        {
            generator.writeStartObject()
                .write(MEMBER_ROLE, role == ROLE_MODEL ? ROLE_NAME_MODEL : ROLE_NAME_USER)
                .writeStartArray(MEMBER_PARTS);

            messageIndex = message;
            messageRole = role;
            messageToolCalls = 0;
        }

        private void closeMessage()
        {
            if (messageIndex != NO_MESSAGE)
            {
                closeTextBlock();
                generator.writeEnd().writeEnd();
                messageIndex = NO_MESSAGE;
                messageRole = ROLE_NONE;
            }
        }

        private void closeTextBlock()
        {
            if (textBlockOpen)
            {
                for (int depth = 0; depth < partDepth; depth++)
                {
                    generator.writeEnd();
                }

                partDepth = 1;
                textBlockOpen = false;
            }
        }

        private void doEncodeSectionData(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            if (sectionStreaming)
            {
                if (beginEncode(0))
                {
                    if (!sectionPipeline.feed(buffer, offset, length, fin, sectionTarget))
                    {
                        encodeFailed = true;
                    }

                    endEncode();
                }
            }
            else if (encodeText != null && !encodeText.feed(buffer, offset, length, fin))
            {
                encodeFailed = true;
            }
        }

        private void putRaw(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (fits(length))
            {
                generator.writeSegment(buffer, offset, length, Completion.INCOMPLETE);
                rawOpen = true;
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void putControl(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final String member = controlTransform.member();

            if (member != null)
            {
                if (controlMode == CONTROL_PENDING)
                {
                    controlMode = openControl(member) ? CONTROL_WRITE : CONTROL_DROP;
                }

                if (controlMode == CONTROL_WRITE)
                {
                    putRaw(buffer, offset, length);
                }
            }
        }

        private boolean openControl(
            String member)
        {
            final boolean root = controlMessage == ROOT_CONTROL;

            if (root)
            {
                closeRegion();
            }

            final boolean writable = root || textBlockOpen;

            if (writable)
            {
                generator.writeKey(member);
            }

            return writable;
        }

        private void putEscaped(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (fits(length * ESCAPE_EXPANSION))
            {
                generator.writeSegment(buffer, offset, length);
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void doEncodeSectionEnd(
            boolean isError)
        {
            encodeText = null;

            if (sectionKind == KIND_TOOL_CALL)
            {
                recordCall(sectionAttributes.toolCallId(), sectionAttributes.toolName());
            }

            if (sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(ENCODE_STRUCTURE))
            {
                sectionStreaming = false;
                translated = false;

                if (rawOpen)
                {
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                    rawOpen = false;
                }

                if (sectionOpen)
                {
                    generator.writeEndEscaped();
                    textBlockOpen = true;
                    sectionOpen = false;
                }

                endEncode();
            }

            sectionKind = 0;
        }

        private void doEncodeRequestEnd(
            int maxOutputTokens)
        {
            if (!LlmState.initialClosing(state) && !LlmState.initialClosed(state))
            {
                if (beginEncode(ENCODE_STRUCTURE + ENCODE_MAX_TOKENS))
                {
                    closeRegion();

                    if (stream.crossDialect && maxOutputTokens != NO_MAX_OUTPUT_TOKENS)
                    {
                        generator.writeStartObject(MEMBER_GENERATION_CONFIG)
                            .write(MEMBER_MAX_OUTPUT_TOKENS, maxOutputTokens)
                            .writeEnd();
                    }

                    generator.writeEnd();
                    endEncode();
                }

                state = LlmState.closingInitial(state);
            }
        }

        private boolean beginEncode(
            int required)
        {
            boolean ready = !encodeFailed;

            if (ready)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                ready = encodeSlot != NO_SLOT && encodeSlotOffset + required <= encodeMax;

                if (ready)
                {
                    generator.wrap(encodePool.buffer(encodeSlot), encodeSlotOffset, encodeMax);
                }
                else
                {
                    encodeFailed = true;
                }
            }

            return ready;
        }

        private void endEncode()
        {
            encodeSlotOffset += generator.length();
        }

        private boolean fits(
            int length)
        {
            return encodeSlotOffset + generator.length() + length <= encodeMax;
        }

        private void flushEncode(
            long traceId)
        {
            if (net != null && !LlmState.initialClosed(state))
            {
                if (encodeSlot != NO_SLOT)
                {
                    final int flushLimit = encodeSlotOffset;
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
                onNetEvent(traceId, buffer, offset, limit, data.flags(), data.extension());
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
                    drainEvents(traceId, false);
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

            if (errorStatus == NO_ERROR)
            {
                stream.beginDrain();
                decodeTraceId = traceId;

                if (framing)
                {
                    drainEvents(traceId, true);
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
            stream.flushAppWindow(window.traceId());
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

            if (framing)
            {
                drainEvents(traceId, stream.replyCredit() > 0);

                if (!LlmState.replyClosing(state))
                {
                    doNetWindow(traceId);
                }
                else if (decodeSlot == NO_SLOT)
                {
                    doAppEnd(pendingEndTraceId);
                }
            }
            else if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                decodeNet(traceId, slotBuffer, 0, decodeSlotOffset);

                doNetWindow(traceId);
            }
            else if (decodeSlot == NO_SLOT && LlmState.replyClosing(state))
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
            int flags,
            OctetsFW extension)
        {
            final boolean last = Flags.hasFin(flags);
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
            else if (decodeSlotOffset == decodeRemainder && eventAvailable())
            {
                forwardResponseSections(buffer, offset, length, last);
            }
            else
            {
                enqueueEvent(traceId, buffer, offset, length, flags, extension);
            }
        }

        private Array32FW<HttpHeaderFW> eventHeadersOf(
            int flags,
            OctetsFW extension)
        {
            final HttpDataExFW httpDataEx = Flags.hasInit(flags) ? extension.get(httpDataExRO::tryWrap) : null;
            final Array32FW<HttpHeaderFW> headers = httpDataEx != null ? httpDataEx.headers() : null;

            return headers != null && headers.fieldCount() > 0 && headers.sizeof() <= EVENT_ATTRIBUTES_CAPACITY ? headers : null;
        }

        private boolean eventAvailable()
        {
            return decodeRemainder == 0 ? stream.replyAvailable() : !decodeRemainderLast;
        }

        private void enqueueEvent(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length,
            int flags,
            OctetsFW extension)
        {
            final Array32FW<HttpHeaderFW> headers = eventHeadersOf(flags, extension);
            final int attributesLength = headers != null ? headers.sizeof() : 0;

            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            if (decodeSlot == NO_SLOT || decodeSlotOffset + QUEUE_ENTRY_OVERHEAD + attributesLength + length > decodeMax)
            {
                cleanupNet(traceId);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                final LlmQueueEntryFW.Builder entry = queueEntryRW.wrap(slotBuffer, decodeSlotOffset, slotBuffer.capacity());

                if (headers != null)
                {
                    entry.extension(headers.buffer(), headers.offset(), attributesLength);
                }
                else
                {
                    entry.extension(emptyBufferRO, 0, 0);
                }

                decodeSlotOffset = entry
                    .traceId(traceId)
                    .authorization(0L)
                    .budgetId(0L)
                    .reserved(0)
                    .flags(flags)
                    .payload(buffer, offset, length)
                    .build()
                    .limit();
            }
        }

        private void drainEvents(
            long traceId,
            boolean settle)
        {
            decodeTraceId = traceId;

            int tailFrom = 0;
            int progress = decodeRemainder;

            if (settle && decodeRemainder > 0)
            {
                tailFrom = decodeSegment(0, progress, decodeRemainderLast);
            }

            while (decodeSlot != NO_SLOT && progress < decodeSlotOffset && eventAvailable())
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                final LlmQueueEntryFW entry = queueEntryRO.wrap(slotBuffer, progress, decodeSlotOffset);
                final OctetsFW payload = entry.payload();
                final int flags = entry.flags();
                final int payloadFrom = payload.offset();
                final int payloadLength = payload.sizeof();
                final int next = entry.limit();
                final boolean last = Flags.hasFin(flags);

                final int segmentFrom = payloadFrom - decodeRemainder;

                if (decodeRemainder > 0)
                {
                    slotBuffer.putBytes(segmentFrom, slotBuffer, tailFrom, decodeRemainder);
                }

                tailFrom = segmentFrom;
                progress = next;

                if (payloadLength > 0 || last)
                {
                    tailFrom = decodeSegment(segmentFrom, next, last);
                }
            }

            if (decodeSlot != NO_SLOT)
            {
                final int queued = decodeSlotOffset - progress;
                final int retained = decodeRemainder + queued;

                if (retained > 0)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);

                    if (decodeRemainder > 0 && tailFrom > 0)
                    {
                        slotBuffer.putBytes(0, slotBuffer, tailFrom, decodeRemainder);
                    }

                    if (queued > 0)
                    {
                        slotBuffer.putBytes(decodeRemainder, slotBuffer, progress, queued);
                    }

                    decodeSlotOffset = retained;
                }
                else
                {
                    cleanupDecodeSlot();
                }
            }
        }

        private int decodeSegment(
            int from,
            int to,
            boolean last)
        {
            final int progress = stream.decodeResponse(decodePool.buffer(decodeSlot), from, to, last);

            int tailFrom = to;

            if (stream.parseRejected())
            {
                stream.resetResponse();
                cleanupNet(decodeTraceId);
            }
            else if (decodeSlot != NO_SLOT)
            {
                tailFrom = progress;
                decodeRemainder = to - progress;
                decodeRemainderLast = last;

                if (last && decodeRemainder == 0 && stream.completed())
                {
                    stream.resetDocument();
                }
            }

            return tailFrom;
        }

        private void forwardResponseSections(
            DirectBuffer buffer,
            int offset,
            int length,
            boolean last)
        {
            if (length > 0 || last)
            {
                if (decodeRemainder > 0)
                {
                    if (decodeRemainder + length > decodeMax)
                    {
                        cleanupNet(decodeTraceId);
                    }
                    else
                    {
                        final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                        slotBuffer.putBytes(decodeSlotOffset, buffer, offset, length);
                        decodeSlotOffset += length;
                        decodeFragment(slotBuffer, 0, decodeSlotOffset, last);
                    }
                }
                else
                {
                    decodeFragment((DirectBufferEx) buffer, offset, offset + length, last);
                }
            }
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
                cleanupNet(decodeTraceId);
            }
            else
            {
                retainRemainder(source, progress, to, last);

                if (last && decodeRemainder == 0 && stream.completed())
                {
                    stream.resetDocument();
                }
            }
        }

        private void retainRemainder(
            DirectBufferEx source,
            int progress,
            int limit,
            boolean last)
        {
            final int remaining = limit - progress;

            if (remaining > 0)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT || remaining > decodeMax)
                {
                    cleanupNet(decodeTraceId);
                }
                else
                {
                    decodePool.buffer(decodeSlot).putBytes(0, source, progress, remaining);
                    decodeSlotOffset = remaining;
                    decodeRemainder = remaining;
                    decodeRemainderLast = last;
                }
            }
            else
            {
                cleanupDecodeSlot();
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
                .padding(framing ? QUEUE_ENTRY_PADDING : 0)
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
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeRemainder = 0;
                decodeRemainderLast = false;
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
