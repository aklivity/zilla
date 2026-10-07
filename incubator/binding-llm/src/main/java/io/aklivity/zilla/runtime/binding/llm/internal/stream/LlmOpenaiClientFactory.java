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

import java.util.Map;
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
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmErrorFW;
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
    private static final String USAGE = "usage";
    private static final String MEMBER_ID = "id";
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

    private static final int CONTENT_NONE = 0;
    private static final int CONTENT_HELD = 1;
    private static final int CONTENT_ARRAY = 2;
    private static final int CONTENT_CLOSED = 3;

    private static final int NO_MESSAGE = -2;
    private static final int TOP_LEVEL = -1;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_SLACK = 256;
    private static final int ENCODE_STRUCTURE = 128;

    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);
    private static final DirectBufferEx DATA_URL_PREFIX = new UnsafeBufferEx("data:".getBytes(UTF_8));
    private static final DirectBufferEx BASE64_MARKER = new UnsafeBufferEx(";base64,".getBytes(UTF_8));

    private static final String MEMBER_MODEL = "model";
    private static final String MEMBER_MESSAGES = "messages";
    private static final String MEMBER_TOOLS = "tools";
    private static final String MEMBER_TOOL_CALL_ID = "tool_call_id";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_URL = "url";
    private static final String MEMBER_FILE_DATA = "file_data";
    private static final String MEMBER_FILE_ID = "file_id";
    private static final String PART_TEXT = "text";
    private static final String PART_IMAGE_URL = "image_url";
    private static final String PART_FILE = "file";

    private static final int MEDIA_NONE = 0;
    private static final int MEDIA_DATA = 1;
    private static final int MEDIA_URL = 2;

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
    private final MutableDirectBufferEx transformBuffer;
    private final JsonGeneratorEx escaper;
    private final int holdLimit;
    private final UnsafeBufferEx comparisonRO;
    private final DirectBufferEx emptyBufferRO;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool stagePool;
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
        this.stagePool = decodePool.duplicate();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.transformBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.escaper = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        this.holdLimit = (encodeMax - ENCODE_SLACK) / (2 * ESCAPE_EXPANSION);
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
        private final StringBuilder usagePath;
        private final int[] usageMark;
        private final LlmOpenaiResponseDecoder decodeStart = this::decodeStart;
        private final LlmOpenaiResponseDecoder decodeRootStart = this::decodeRootStart;
        private final LlmOpenaiResponseDecoder decodeRoot = this::decodeRoot;
        private final LlmOpenaiResponseDecoder decodeId = this::decodeId;
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
        private final boolean replay;

        private LlmOpenaiResponseDecoder decoder;
        private LlmOpenaiResponseDecoder skipThen;
        private LlmOpenaiResponseDecoder errorThen;
        private LlmOpenaiResponseDecoder usageThen;
        private int decodeLimit;
        private int skipDepth;

        private boolean messageScope;
        private boolean scanned;
        private boolean scanError;
        private boolean parseRejected;
        private boolean draining;

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
        private LlmSectionType textType;
        private String textMember;

        private boolean sectionOpen;
        private boolean sectionStarted;
        private boolean sectionWritten;
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

        private DirectBufferEx decodeInput;
        private boolean dialectActive;
        private boolean dialectOpen;
        private int dialectFrom;
        private int knownToolIndex = NONE;
        private String knownToolId;
        private String knownToolName;

        private int captureDepth;
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
            this.replay = NAME.equals(dialect);
            this.http = new LlmOpenaiHttpStream(this);
            this.parser = JsonEx.createParser();
            this.usagePath = new StringBuilder();
            this.usageMark = new int[8];
            this.textBytes = new byte[1024];
            this.textBuffer = new UnsafeBufferEx(textBytes);
            this.heldBytes = new byte[1024];
            this.heldBuffer = new UnsafeBufferEx(heldBytes);
            this.heldLength = NONE;
            this.sectionTool = NONE;
            this.decoder = decodeStart;
            this.dialectActive = true;
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

                    if (appKind == LlmDataExFW.KIND_SECTION)
                    {
                        final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;
                        sectionAttributes.readFrom(section);

                        final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                        if (replay)
                        {
                            http.doReplaySection(type);
                        }
                        else
                        {
                            http.doEncodeSectionBegin(
                                sectionKind(type),
                                section != null ? section.message() : NO_MESSAGE,
                                sectionAttributes.toolCallId());
                        }
                    }
                    else if (replay)
                    {
                        http.doReplayDialect();
                    }
                    else
                    {
                        http.doEncodeDialectDropped();
                    }
                }

                final OctetsFW payload = data.payload();
                final boolean fin = Flags.hasFin(flags);

                if (replay)
                {
                    if (payload != null && payload.sizeof() > 0)
                    {
                        http.doReplayData(payload.buffer(), payload.offset(), payload.sizeof());
                    }
                }
                else
                {
                    if (payload != null && payload.sizeof() > 0)
                    {
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
            final int room = (encodeMax - http.encodeSlotOffset - ENCODE_SLACK) / ESCAPE_EXPANSION;
            final int credit = Math.max(0, room - http.holdLength);
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
            boolean dialectFrame,
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
                final LlmDataExFW.Builder dataExBuilder = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId);

                if (dialectFrame)
                {
                    dataExBuilder.dialect(d ->
                    {
                    });
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

        private void doDialectEvent(
            long traceId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (begun() && !draining)
            {
                doAppFrame(traceId, Flags.COMPLETE, true, null, NO_MESSAGE, null, null, buffer, offset, length);
            }
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
            dialectActive = !draining;
            dialectOpen = false;
        }

        private void resetErrorDocument()
        {
            commitDocument();
            parser.reset();
            decoder = decodeErrorStart;
            dialectActive = false;
        }

        private void resetResponse()
        {
            resetDocument();
            scanned = false;
            parseRejected = false;
            draining = false;
            sectionOpen = false;
            sectionStarted = false;
            sectionTool = NONE;
            highSurrogate = 0;
            heldLength = NONE;
            knownToolIndex = NONE;
            knownToolId = null;
            knownToolName = null;
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
            dialectActive = false;
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
            decodeInput = buffer;
            dialectFrom = offset;

            parser.wrap(buffer, offset, windowLimit, windowLast);

            LlmOpenaiResponseDecoder previous = null;
            while (previous != decoder && decoder != decodeEnd && !parseRejected)
            {
                previous = decoder;
                decoder.decode(buffer, offset, windowLimit);
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
                flushDialect(progress, false);
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
                    flushDialect(position(), true);
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

                    if (MEMBER_ID.contentEquals(member))
                    {
                        decoder = decodeId;
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
                    else if (MEMBER_ERROR.contentEquals(member))
                    {
                        dialectActive = false;
                        errorThen = decodeRoot;
                        decoder = decodeErrorObjectStart;
                    }
                    else
                    {
                        skipMember(decodeRoot);
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
                final String value = stringValue(parser.nextEvent(), decodeRoot, id);

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
            while (decoder == decodeModel && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeRoot, servedModel);

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
                    decoder = decodeChoices;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_MESSAGE.contentEquals(member))
                    {
                        decoder = decodeMessageStart;
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
                    decodeScopeMember(parser.getStringView(), decodeMessage);
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
            int eventFrom = position();

            while (decoder == decodeText && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitText(deferred, eventFrom);

                    if (!deferred)
                    {
                        decoder = memberScope();
                    }
                }
                else
                {
                    skipAfter(event, memberScope());
                }

                eventFrom = position();
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
            int eventFrom = position();

            while (decoder == decodeArguments && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitArguments(deferred, eventFrom);

                    if (!deferred)
                    {
                        decoder = decodeFunction;
                    }
                }
                else
                {
                    skipAfter(event, decodeFunction);
                }

                eventFrom = position();
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
            return stringValue(event, then, null);
        }

        private String stringValue(
            JsonEvent event,
            LlmOpenaiResponseDecoder then,
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
            boolean deferred,
            int eventFrom)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            if (length > 0 || highSurrogate != 0 || !deferred && sectionOpen)
            {
                if (!sectionOpen)
                {
                    openSection(textType, choiceIndex, null, null, NONE, contentFrom(eventFrom));
                }

                emitSection(encodeText(chars, !deferred), !deferred);
            }

            if (!deferred)
            {
                dialectResume(position() - 1);
            }
            else
            {
                parser.consumed(length);
            }
        }

        private void emitArguments(
            boolean deferred,
            int eventFrom)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            rememberTool();

            if (length > 0 || highSurrogate != 0 || !deferred && sectionOpen)
            {
                if (!sectionOpen)
                {
                    openSection(LlmSectionType.TOOL_CALL, choiceIndex, toolCallId(), toolCallName(), toolIndex,
                        contentFrom(eventFrom));
                }

                emitSection(encodeText(chars, !deferred), !deferred);
            }

            if (!deferred)
            {
                dialectResume(position() - 1);
            }
            else
            {
                parser.consumed(length);
            }
        }

        private void rememberTool()
        {
            if (toolIndex != NONE && (toolId != null || toolName != null))
            {
                if (toolIndex != knownToolIndex)
                {
                    knownToolId = null;
                    knownToolName = null;
                }

                knownToolIndex = toolIndex;
                knownToolId = toolId != null ? toolId : knownToolId;
                knownToolName = toolName != null ? toolName : knownToolName;
            }
        }

        private String toolCallId()
        {
            return toolId != null || toolIndex == NONE || toolIndex != knownToolIndex ? toolId : knownToolId;
        }

        private String toolCallName()
        {
            return toolName != null || toolIndex == NONE || toolIndex != knownToolIndex ? toolName : knownToolName;
        }

        private int contentFrom(
            int eventFrom)
        {
            int from = eventFrom;

            while (decodeInput.getByte(from) != '"')
            {
                from++;
            }

            return from + 1;
        }

        private void dialectResume(
            int from)
        {
            if (!dialectActive && !draining)
            {
                dialectFrom = from;
                dialectActive = true;
            }
        }

        private void flushDialect(
            int to,
            boolean fin)
        {
            if (dialectActive)
            {
                final int length = to - dialectFrom;

                if (length > 0 || fin && dialectOpen)
                {
                    final int flags = dialectOpen ? Flags.NONE : Flags.INIT;

                    doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, true, null, NO_MESSAGE, null, null,
                        decodeInput, dialectFrom, length);

                    dialectOpen = !fin;
                }

                dialectFrom = to;
            }
        }

        private void openSection(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            int tool,
            int sectionFrom)
        {
            flushDialect(sectionFrom, true);
            dialectActive = false;

            sectionOpen = true;
            sectionStarted = false;
            sectionWritten = false;
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

                emitAtomicSection(length, fin);
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

            doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, false, sectionType, sectionMessage,
                sectionToolId, sectionToolName, buffer, 0, length);
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
        private int holdSlot = NO_SLOT;
        private int holdLength;
        private boolean bodyHeld;
        private boolean rawOpen;
        private int mediaScope;
        private boolean encodeFailed;
        private boolean sectionsStarted;

        private int messageCount;
        private int messageIndex = NO_MESSAGE;
        private boolean replayEscaped;
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
        private final LlmSectionPipeline.Output rawOutput = (buffer, offset, length, last) -> putRaw(buffer, offset, length);
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
            this.generator = JsonEx.createGenerator();
            this.bodyHeld = !stream.replay;
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
            if (!stream.replay && beginEncode(ENCODE_STRUCTURE + modelRoom()))
            {
                generator.writeStartObject();
                generator.write(MEMBER_MODEL, stream.model);
                endEncode();
            }
        }

        private void doEncodeDialectDropped()
        {
            sectionOpen = false;
            sectionArrayPart = false;
            translated = false;
            sectionKind = KIND_DROPPED;
        }

        private void doReplaySection(
            LlmSectionType type)
        {
            replayEscaped = type == LlmSectionType.SYSTEM_INSTRUCTION ||
                type == LlmSectionType.USER_TEXT ||
                type == LlmSectionType.ASSISTANT_TEXT ||
                type == LlmSectionType.ASSISTANT_REFUSAL ||
                type == LlmSectionType.TOOL_RESULT;
        }

        private void doReplayDialect()
        {
            replayEscaped = false;
        }

        private void doReplayData(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (beginEncode(replayEscaped ? length * ESCAPE_EXPANSION : length))
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);

                if (replayEscaped)
                {
                    escaper.wrap(slot, encodeSlotOffset, encodeMax);
                    escaper.writeSegment(buffer, offset, length);
                    encodeSlotOffset += escaper.length();
                }
                else
                {
                    slot.putBytes(encodeSlotOffset, buffer, offset, length);
                    encodeSlotOffset += length;
                }
            }
        }

        private int modelRoom()
        {
            return stream.model != null ? stream.model.length() * ESCAPE_EXPANSION : 0;
        }

        private void doEncodeSectionBegin(
            int kind,
            int message,
            String extension)
        {
            sectionKind = kind;
            sectionOpen = false;
            sectionArrayPart = false;
            translated = translatable(kind);

            if (kind != KIND_DROPPED)
            {
                startSections();
            }

            if (kind == KIND_DROPPED)
            {
                sectionKind = KIND_DROPPED;
            }
            else if (kind == KIND_UNKNOWN)
            {
                encodeFailed = true;
            }
            else if (kind == 0 || kind != KIND_TOOL_DEFINITION && messagesClosed)
            {
                encodeFailed = true;
            }
            else if (kind == KIND_SYSTEM_INSTRUCTION && message == TOP_LEVEL && messageCount > 0)
            {
                encodeFailed = true;
            }
            else if (beginEncode(ENCODE_STRUCTURE + sectionRoom(extension)))
            {
                if (kind == KIND_TOOL_DEFINITION)
                {
                    beginToolDefinition();
                }
                else
                {
                    beginMessageSection(kind, message, extension);
                }

                if (translated && !encodeFailed)
                {
                    beginTranslated(kind);
                }

                endEncode();
            }
        }

        private int sectionRoom(
            String extension)
        {
            return (length(extension) + length(sectionAttributes.mediaType()) + length(sectionAttributes.refId())) *
                ESCAPE_EXPANSION;
        }

        private static int length(
            String value)
        {
            return value != null ? value.length() : 0;
        }

        private void startSections()
        {
            if (!sectionsStarted)
            {
                sectionsStarted = true;
                bodyHeld = false;
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

                beginSectionStreaming(toolCallPipeline, rawOutput);
            }
            else
            {
                if (toolDefinitionPipeline == null)
                {
                    toolDefinitionPipeline = new LlmSectionPipeline(new LlmAnthropicToolDefinitionTransform(),
                        transformBuffer);
                }

                beginSectionStreaming(toolDefinitionPipeline, rawOutput);
            }
        }

        private void beginMedia(
            int kind)
        {
            final String source = sectionAttributes.source();
            final String mediaType = sectionAttributes.mediaType();
            final String refId = sectionAttributes.refId();
            final boolean image = kind == KIND_USER_IMAGE;

            if (LlmSectionAttributes.SOURCE_INLINE.equals(source) && mediaType != null)
            {
                if (mediaDataPipeline == null)
                {
                    mediaDataPipeline = new LlmSectionPipeline(new LlmAnthropicMediaDataTransform(), transformBuffer);
                }

                beginMediaPart(image, image ? MEMBER_URL : MEMBER_FILE_DATA);
                generator.writeStartEscaped();
                generator.writeSegment(DATA_URL_PREFIX, 0, DATA_URL_PREFIX.capacity());
                generator.writeSegment(new UnsafeBufferEx(mediaType.getBytes(UTF_8)), 0, mediaType.length());
                generator.writeSegment(BASE64_MARKER, 0, BASE64_MARKER.capacity());

                mediaScope = MEDIA_DATA;
                beginSectionStreaming(mediaDataPipeline, mediaOutput);
                mediaDropQuote = true;
            }
            else if (image && LlmSectionAttributes.SOURCE_URL.equals(source))
            {
                if (mediaUrlPipeline == null)
                {
                    mediaUrlPipeline = new LlmSectionPipeline(new LlmAnthropicMediaUrlTransform(), transformBuffer);
                }

                beginMediaPart(true, MEMBER_URL);

                mediaScope = MEDIA_URL;
                beginSectionStreaming(mediaUrlPipeline, rawOutput);
            }
            else if (kind == KIND_USER_DOCUMENT && LlmSectionAttributes.SOURCE_REFERENCE.equals(source) && refId != null)
            {
                beginMediaPart(false, MEMBER_FILE_ID);
                generator.write(refId);
                generator.writeEnd().writeEnd();
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void beginMediaPart(
            boolean image,
            String member)
        {
            generator.writeStartObject()
                .write(MEMBER_TYPE, image ? PART_IMAGE_URL : PART_FILE)
                .writeStartObject(image ? PART_IMAGE_URL : PART_FILE)
                .writeKey(member);
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
            int length,
            boolean last)
        {
            final int from = mediaDropQuote ? offset + 1 : offset;
            final int to = last ? offset + length - 1 : offset + length;

            mediaDropQuote = false;

            if (to > from)
            {
                putEscaped(buffer, from, to - from);
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

        private void beginToolDefinition()
        {
            closeMessage();

            if (messagesOpened && !messagesClosed)
            {
                generator.writeEnd();
                messagesClosed = true;
            }

            if (toolsClosed)
            {
                encodeFailed = true;
            }
            else
            {
                if (!toolsOpened)
                {
                    generator.writeStartArray(MEMBER_TOOLS);
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
                generator.writeKey(MEMBER_REFUSAL);
                generator.writeStartEscaped();
                sectionOpen = true;
            }
            else
            {
                closeContent(!refusalWritten);

                if (!toolCallsOpened)
                {
                    generator.writeStartArray(MEMBER_TOOL_CALLS);
                    toolCallsOpened = true;
                }
            }
        }

        private void ensureMessage(
            int kind,
            int message,
            String extension)
        {
            if (toolsOpened && !toolsClosed)
            {
                generator.writeEnd();
                toolsClosed = true;
            }

            if (!messagesOpened)
            {
                generator.writeStartArray(MEMBER_MESSAGES);
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
                if (beginEncode(0))
                {
                    if (!sectionPipeline.feed(buffer, offset, length, fin, sectionOutput))
                    {
                        encodeFailed = true;
                    }

                    endEncode();
                }
            }
            else if (!translated && sectionKind != 0 && sectionKind != KIND_DROPPED)
            {
                if (text(sectionKind) && contentState == CONTENT_HELD && !sectionArrayPart)
                {
                    stageText(buffer, offset, length);
                }
                else if (beginEncode(0))
                {
                    if (text(sectionKind) || sectionKind == KIND_ASSISTANT_REFUSAL)
                    {
                        putEscaped(buffer, offset, length);
                    }
                    else
                    {
                        putRaw(buffer, offset, length);
                    }

                    endEncode();
                }
            }
        }

        private void stageText(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (holdLength + length > holdLimit)
            {
                if (beginEncode(ENCODE_STRUCTURE + length * ESCAPE_EXPANSION))
                {
                    commitArray(true);
                    putEscaped(buffer, offset, length);
                    endEncode();
                }
            }
            else if (holdSlot != NO_SLOT)
            {
                stagePool.buffer(holdSlot).putBytes(holdLength, buffer, offset, length);
                holdLength += length;
            }
        }

        private void doEncodeSectionEnd()
        {
            if (sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(ENCODE_STRUCTURE))
            {
                if (sectionStreaming)
                {
                    endMedia();
                    sectionStreaming = false;
                }

                translated = false;

                if (rawOpen)
                {
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                    rawOpen = false;
                }

                if (sectionOpen)
                {
                    if (sectionArrayPart)
                    {
                        generator.writeEndEscaped().writeEnd();
                    }
                    else if (contentState != CONTENT_HELD)
                    {
                        generator.writeEndEscaped();
                    }

                    sectionOpen = false;
                }

                endEncode();
            }

            sectionKind = 0;
        }

        private void endMedia()
        {
            if (mediaScope == MEDIA_DATA)
            {
                generator.writeEndEscaped().writeEnd().writeEnd();
            }
            else if (mediaScope == MEDIA_URL)
            {
                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                rawOpen = false;
                generator.writeEnd().writeEnd();
            }

            mediaScope = MEDIA_NONE;
        }

        private void doEncodeRequestEnd()
        {
            if (!LlmState.initialClosing(state) && !LlmState.initialClosed(state))
            {
                if (!stream.replay && beginEncode(ENCODE_STRUCTURE))
                {
                    closeMessage();
                    closeRegions();

                    if (!messagesOpened)
                    {
                        generator.writeStartArray(MEMBER_MESSAGES).writeEnd();
                    }

                    generator.writeEnd();
                    endEncode();
                }

                bodyHeld = false;
                state = LlmState.closingInitial(state);
            }
        }

        private void closeRegions()
        {
            if (toolsOpened && !toolsClosed)
            {
                generator.writeEnd();
                toolsClosed = true;
            }

            if (messagesOpened && !messagesClosed)
            {
                generator.writeEnd();
                messagesClosed = true;
            }
        }

        private void openMessage(
            int kind,
            String role,
            int message,
            String extension)
        {
            messageCount++;

            generator.writeStartObject().write(MEMBER_ROLE, role);

            if (kind == KIND_TOOL_RESULT)
            {
                if (extension == null)
                {
                    encodeFailed = true;
                }
                else
                {
                    generator.write(MEMBER_TOOL_CALL_ID, extension);
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
                closeToolCalls();

                generator.writeEnd();
                messageIndex = NO_MESSAGE;
                messageRole = null;
                messageToolId = null;
            }
        }

        private void closeToolCalls()
        {
            if (toolCallsOpened)
            {
                generator.writeEnd();
                toolCallsOpened = false;
            }
        }

        private void closeContent(
            boolean nullWhenAbsent)
        {
            if (contentState == CONTENT_HELD)
            {
                commitString();
            }
            else if (contentState == CONTENT_ARRAY)
            {
                generator.writeEnd();
            }
            else if (contentState == CONTENT_NONE && nullWhenAbsent)
            {
                generator.writeNull(MEMBER_CONTENT);
            }

            contentState = CONTENT_CLOSED;
        }

        private void beginText()
        {
            switch (contentState)
            {
            case CONTENT_NONE:
                holdSlot = stagePool.acquire(initialId);
                holdLength = 0;
                encodeFailed |= holdSlot == NO_SLOT;
                contentState = CONTENT_HELD;
                break;
            case CONTENT_HELD:
                commitArray(false);
                beginTextPart();
                break;
            case CONTENT_ARRAY:
                beginTextPart();
                break;
            default:
                encodeFailed = true;
                break;
            }

            sectionOpen = !encodeFailed;
        }

        private void beginTextPart()
        {
            generator.writeStartObject().write(MEMBER_TYPE, PART_TEXT).writeKey(MEMBER_TEXT);
            generator.writeStartEscaped();
            sectionArrayPart = true;
        }

        private void beginPart()
        {
            switch (contentState)
            {
            case CONTENT_NONE:
                generator.writeStartArray(MEMBER_CONTENT);
                contentState = CONTENT_ARRAY;
                break;
            case CONTENT_HELD:
                commitArray(false);
                break;
            case CONTENT_ARRAY:
                break;
            default:
                encodeFailed = true;
                break;
            }
        }

        private void commitString()
        {
            generator.writeKey(MEMBER_CONTENT);
            generator.writeStartEscaped();

            if (holdLength > 0)
            {
                generator.writeSegment(stagePool.buffer(holdSlot), 0, holdLength);
            }

            generator.writeEndEscaped();
            releaseHold();
        }

        private void commitArray(
            boolean open)
        {
            generator.writeStartArray(MEMBER_CONTENT);
            generator.writeStartObject().write(MEMBER_TYPE, PART_TEXT).writeKey(MEMBER_TEXT);
            generator.writeStartEscaped();

            if (holdLength > 0)
            {
                generator.writeSegment(stagePool.buffer(holdSlot), 0, holdLength);
            }

            if (open)
            {
                sectionArrayPart = true;
            }
            else
            {
                generator.writeEndEscaped().writeEnd();
            }

            releaseHold();
            contentState = CONTENT_ARRAY;
        }

        private void releaseHold()
        {
            if (holdSlot != NO_SLOT)
            {
                stagePool.release(holdSlot);
                holdSlot = NO_SLOT;
            }

            holdLength = 0;
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

                ready = encodeSlot != NO_SLOT && encodeSlotOffset + holdLength * ESCAPE_EXPANSION + required <= encodeMax;

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
                    final int flushLimit = bodyHeld ? 0 : encodeSlotOffset;
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
                        releaseEncodeSlot();
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
                    slotBuffer.putBytes(0, slotBuffer, progress, remaining);
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
            if (matchesTerminator(buffer, offset, length, RESPONSE_TERMINATOR))
            {
                stream.doDialectEvent(decodeTraceId, (DirectBufferEx) buffer, offset, length);
            }
            else if (length > 0 || last)
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
                        stagePool.buffer(stageSlot).putBytes(stageSlotOffset, buffer, offset, length);
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
            decodeFragment(stagePool.buffer(stageSlot), 0, stageSlotOffset, stageLast);
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
                    stagePool.buffer(stageSlot).putBytes(0, source, progress, remaining);
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
            releaseEncodeSlot();
            releaseHold();
        }

        private void releaseEncodeSlot()
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
