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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmOpenaiResponsesDialectFactorySpi.NAME;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonException;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.dialect.LlmDialectNames;
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

public final class LlmOpenaiResponsesClientFactory implements LlmDialectHandler
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
    private static final String PATH_RESPONSES = "/responses";
    private static final String VENDOR = LlmDialectNames.vendor(NAME);
    private static final String VENDOR_PREFIX = VENDOR + ":";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ERROR_TYPE_INVALID_REQUEST = "invalid_request_error";
    private static final String ERROR_TYPE_SERVER = "server_error";
    private static final String MODEL = "model";
    private static final String MESSAGE_MODEL_MISSING = "Missing required parameter: 'model'.";
    private static final String MESSAGE_SECTION_INVALID = "Invalid request section.";
    private static final String MESSAGE_SECTION_CONVERT = "%s cannot be converted from %s to %s";
    private static final String MESSAGE_SECTION_CONVERT_AFTER_INPUT =
        "%s cannot be converted from %s to %s after the input is closed";
    private static final String MESSAGE_ITEM_UNSUPPORTED = "Unsupported output item type: '%s'.";
    private static final String MESSAGE_PART_UNSUPPORTED = "Unsupported output content part type: '%s'.";
    private static final Set<String> RESPONSE_CONTENT_TYPES = Set.of(CONTENT_TYPE_JSON, CONTENT_TYPE_EVENT_STREAM);
    private static final DirectBufferEx CONTROL_TERMINATOR = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final int STATUS_OK = 200;
    private static final int STATUS_MULTIPLE_CHOICES = 300;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int NO_ERROR = -1;
    private static final int STATUS_BAD_GATEWAY = 502;
    private static final int NONE = -1;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final String USAGE = "usage";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_OBJECT = "object";
    private static final String MEMBER_STATUS = "status";
    private static final String MEMBER_ERROR = "error";
    private static final String MEMBER_CODE = "code";
    private static final String MEMBER_MESSAGE = "message";
    private static final String MEMBER_PARAM = "param";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_RESPONSE = "response";
    private static final String MEMBER_ITEM = "item";
    private static final String MEMBER_PART = "part";
    private static final String MEMBER_DELTA = "delta";
    private static final String MEMBER_OUTPUT = "output";
    private static final String MEMBER_CONTENT = "content";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_INCOMPLETE_DETAILS = "incomplete_details";
    private static final String MEMBER_REASON = "reason";
    private static final String TYPE_ERROR = "error";
    private static final String TYPE_OTHER = "";
    private static final String TYPE_TEXT_DELTA = "response.output_text.delta";
    private static final String TYPE_TEXT_DONE = "response.output_text.done";
    private static final String TYPE_PART_ADDED = "response.content_part.added";
    private static final String TYPE_PART_DONE = "response.content_part.done";
    private static final String TYPE_ITEM_ADDED = "response.output_item.added";
    private static final String TYPE_ITEM_DONE = "response.output_item.done";
    private static final String TYPE_COMPLETED = "response.completed";
    private static final String TYPE_INCOMPLETE = "response.incomplete";
    private static final String TYPE_FAILED = "response.failed";
    private static final String[] EVENT_TYPES =
    {
        TYPE_ERROR, TYPE_TEXT_DELTA, TYPE_TEXT_DONE, TYPE_PART_ADDED, TYPE_PART_DONE, TYPE_ITEM_ADDED, TYPE_ITEM_DONE,
        TYPE_COMPLETED, TYPE_INCOMPLETE, TYPE_FAILED
    };
    private static final String ITEM_MESSAGE = "message";
    private static final String PART_OUTPUT_TEXT = "output_text";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_INCOMPLETE = "incomplete";
    private static final String STATUS_FAILED = "failed";
    private static final String REASON_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String REASON_CONTENT_FILTER = "content_filter";
    private static final int EVENT_FRAMES = 4;
    private static final byte[] TEXT_PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte[] TEXT_SUFFIX = "\"}".getBytes(UTF_8);
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final int EVENT_ATTRIBUTES_CAPACITY = 256;
    private static final int QUEUE_ENTRY_OVERHEAD = 5 * Integer.BYTES + 3 * Long.BYTES + Byte.BYTES;
    private static final int QUEUE_ENTRY_PADDING = QUEUE_ENTRY_OVERHEAD + EVENT_ATTRIBUTES_CAPACITY;

    private static final int KIND_SYSTEM_INSTRUCTION = 1;
    private static final int KIND_USER_TEXT = 2;
    private static final int KIND_ASSISTANT_TEXT = 3;
    private static final int KIND_CONTROL = 4;
    private static final int KIND_DROPPED = 5;
    private static final int CONTROL_SEEN_MAX = 64;
    private static final int CONTROL_CLOSING = 1;
    private static final int CONTROL_HEADER = 2 * Integer.BYTES;

    private static final int CONTENT_NONE = 0;
    private static final int CONTENT_HELD = 1;
    private static final int CONTENT_ARRAY = 2;
    private static final int CONTENT_CLOSED = 3;

    private static final int NO_MESSAGE = -2;
    private static final int ROOT_CONTROL = -1;
    private static final int ASSISTANT_MESSAGE = 0;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_SLACK = 256;
    private static final int ENCODE_STRUCTURE = 128;
    private static final int ENCODE_MAX_OUTPUT_TOKENS = 48;
    private static final int NO_MAX_OUTPUT_TOKENS = -1;

    private static final String MEMBER_MODEL = "model";
    private static final String MEMBER_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String MEMBER_INPUT = "input";
    private static final String MEMBER_INSTRUCTIONS = "instructions";
    private static final String MEMBER_ROLE = "role";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_DEVELOPER = "developer";
    private static final String PART_INPUT_TEXT = "input_text";

    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);
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
    private final int holdLimit;
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

    public LlmOpenaiResponsesClientFactory(
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
        this.holdLimit = (encodeMax - ENCODE_SLACK) / (2 * ESCAPE_EXPANSION);
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
            newStream = new LlmOpenaiResponsesStream(
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
    private interface LlmOpenaiResponsesDecoder
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
        return kind == KIND_CONTROL;
    }

    private static int sectionKind(
        LlmSectionType type)
    {
        return switch (type)
        {
        case SYSTEM_INSTRUCTION -> KIND_SYSTEM_INSTRUCTION;
        case USER_TEXT -> KIND_USER_TEXT;
        case ASSISTANT_TEXT -> KIND_ASSISTANT_TEXT;
        case CONTROL -> KIND_CONTROL;
        case ASSISTANT_REFUSAL, ASSISTANT_REASONING, CITATIONS, SERVER_TOOL_DEFINITION, SERVER_TOOL_CALL,
            SERVER_TOOL_RESULT -> KIND_DROPPED;
        default -> 0;
        };
    }

    private static String role(
        int kind)
    {
        return switch (kind)
        {
        case KIND_SYSTEM_INSTRUCTION -> ROLE_DEVELOPER;
        case KIND_ASSISTANT_TEXT -> ROLE_ASSISTANT;
        default -> ROLE_USER;
        };
    }

    private static boolean text(
        int kind)
    {
        return kind == KIND_SYSTEM_INSTRUCTION ||
            kind == KIND_USER_TEXT ||
            kind == KIND_ASSISTANT_TEXT;
    }

    private static boolean objectShaped(
        LlmSectionType type)
    {
        return type == LlmSectionType.SYSTEM_INSTRUCTION ||
            type == LlmSectionType.USER_TEXT ||
            type == LlmSectionType.ASSISTANT_TEXT ||
            type == LlmSectionType.ASSISTANT_REASONING;
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

    private final class LlmOpenaiResponsesStream
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
        private final LlmOpenaiResponsesHttpStream http;
        private final JsonParserEx parser;
        private final StringBuilder usagePath;
        private final int[] usageMark;
        private final LlmOpenaiResponsesDecoder decodeStart = this::decodeStart;
        private final LlmOpenaiResponsesDecoder decodeRootStart = this::decodeRootStart;
        private final LlmOpenaiResponsesDecoder decodeRoot = this::decodeRoot;
        private final LlmOpenaiResponsesDecoder decodeType = this::decodeType;
        private final LlmOpenaiResponsesDecoder decodeId = this::decodeId;
        private final LlmOpenaiResponsesDecoder decodeModel = this::decodeModel;
        private final LlmOpenaiResponsesDecoder decodeStatus = this::decodeStatus;
        private final LlmOpenaiResponsesDecoder decodeResponseStart = this::decodeResponseStart;
        private final LlmOpenaiResponsesDecoder decodeResponse = this::decodeResponse;
        private final LlmOpenaiResponsesDecoder decodeIncompleteStart = this::decodeIncompleteStart;
        private final LlmOpenaiResponsesDecoder decodeIncomplete = this::decodeIncomplete;
        private final LlmOpenaiResponsesDecoder decodeReason = this::decodeReason;
        private final LlmOpenaiResponsesDecoder decodeOutputStart = this::decodeOutputStart;
        private final LlmOpenaiResponsesDecoder decodeOutput = this::decodeOutput;
        private final LlmOpenaiResponsesDecoder decodeItemStart = this::decodeItemStart;
        private final LlmOpenaiResponsesDecoder decodeItem = this::decodeItem;
        private final LlmOpenaiResponsesDecoder decodeItemType = this::decodeItemType;
        private final LlmOpenaiResponsesDecoder decodeContentStart = this::decodeContentStart;
        private final LlmOpenaiResponsesDecoder decodeContent = this::decodeContent;
        private final LlmOpenaiResponsesDecoder decodeContentPart = this::decodeContentPart;
        private final LlmOpenaiResponsesDecoder decodePartStart = this::decodePartStart;
        private final LlmOpenaiResponsesDecoder decodePart = this::decodePart;
        private final LlmOpenaiResponsesDecoder decodePartType = this::decodePartType;
        private final LlmOpenaiResponsesDecoder decodeText = this::decodeText;
        private final LlmOpenaiResponsesDecoder decodeUsage = this::decodeUsage;
        private final LlmOpenaiResponsesDecoder decodeSkip = this::decodeSkip;
        private final LlmOpenaiResponsesDecoder decodeControlValue = this::decodeControlValue;
        private final LlmOpenaiResponsesDecoder decodeControlStream = this::decodeControlStream;
        private final LlmOpenaiResponsesDecoder decodeEnd = this::decodeEnd;
        private final LlmOpenaiResponsesDecoder decodeIgnore = this::decodeIgnore;
        private final LlmOpenaiResponsesDecoder decodeErrorStart = this::decodeErrorStart;
        private final LlmOpenaiResponsesDecoder decodeErrorRootStart = this::decodeErrorRootStart;
        private final LlmOpenaiResponsesDecoder decodeErrorRoot = this::decodeErrorRoot;
        private final LlmOpenaiResponsesDecoder decodeErrorObjectStart = this::decodeErrorObjectStart;
        private final LlmOpenaiResponsesDecoder decodeErrorObject = this::decodeErrorObject;
        private final LlmOpenaiResponsesDecoder decodeErrorType = this::decodeErrorType;
        private final LlmOpenaiResponsesDecoder decodeErrorCode = this::decodeErrorCode;
        private final LlmOpenaiResponsesDecoder decodeErrorParam = this::decodeErrorParam;
        private final LlmOpenaiResponsesDecoder decodeErrorMessage = this::decodeErrorMessage;

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
        private LlmSectionType requestSection;

        private LlmOpenaiResponsesDecoder decoder;
        private LlmOpenaiResponsesDecoder skipThen;
        private LlmOpenaiResponsesDecoder errorThen;
        private LlmOpenaiResponsesDecoder usageThen;
        private LlmOpenaiResponsesDecoder valueThen;
        private LlmOpenaiResponsesDecoder incompleteThen;
        private LlmOpenaiResponsesDecoder outputThen;
        private LlmOpenaiResponsesDecoder itemThen;
        private LlmOpenaiResponsesDecoder textThen;
        private int decodeLimit;
        private int skipDepth;

        private boolean scanned;
        private boolean scanError;
        private boolean parseRejected;
        private boolean draining;

        private String documentType;
        private int documentInputTokens = NONE;
        private int documentOutputTokens = NONE;
        private int documentTotalTokens = NONE;
        private int documentCacheReadTokens = NONE;
        private int documentCacheWriteTokens = NONE;
        private int documentReasoningTokens = NONE;
        private String documentErrorType;
        private String documentErrorCode;
        private String documentErrorParam;
        private String documentErrorMessage;
        private String documentId;
        private String documentServedModel;
        private String documentStatus;
        private String documentReason;

        private int inputTokens = NONE;
        private int outputTokens = NONE;
        private int totalTokens = NONE;
        private int cacheReadTokens = NONE;
        private int cacheWriteTokens = NONE;
        private int reasoningTokens = NONE;
        private String errorType;
        private String errorCode;
        private String errorParam;
        private String errorMessage;
        private String id;
        private String servedModel;
        private String status;
        private String reason;

        private LlmSectionType textType;

        private boolean sectionOpen;
        private boolean sectionStarted;
        private boolean sectionWritten;
        private LlmSectionType sectionType;
        private int sectionMessage;
        private String sectionToolId;
        private String sectionToolName;

        private String sectionTag;

        private char highSurrogate;
        private byte[] textBytes;
        private UnsafeBufferEx textBuffer;
        private byte[] pieceBytes;
        private UnsafeBufferEx pieceBuffer;
        private final JsonGeneratorEx generator;
        private final JsonGeneratorEx controlGenerator;
        private byte[] controlBytes;
        private UnsafeBufferEx controlBuffer;
        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private DirectBufferEx decodeInput;
        private final StringBuilder controlKey = new StringBuilder();
        private final String[] controlSeen = new String[CONTROL_SEEN_MAX];
        private final int[] controlSeenMessage = new int[CONTROL_SEEN_MAX];
        private int controlSeenCount;
        private int controlSlot = NO_SLOT;
        private int controlSlotOffset;
        private int controlEntry;
        private int controlFrom = NONE;
        private int controlMessage;
        private int controlDepth;
        private LlmOpenaiResponsesDecoder controlThen;

        private int captureDepth;
        private String usageKey;
        private int usageDepth;

        private LlmOpenaiResponsesStream(
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
            this.http = new LlmOpenaiResponsesHttpStream(this);
            this.parser = JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
            this.usagePath = new StringBuilder();
            this.usageMark = new int[8];
            this.textBytes = new byte[1024];
            this.textBuffer = new UnsafeBufferEx(textBytes);
            this.pieceBytes = new byte[1024];
            this.pieceBuffer = new UnsafeBufferEx(pieceBytes);
            this.generator = JsonEx.createGenerator();
            this.controlGenerator = JsonEx.createGenerator();
            this.controlBytes = new byte[CONTROL_HEADER];
            this.controlBuffer = new UnsafeBufferEx(controlBytes);
            this.decoder = decodeStart;
        }

        private int replyCredit()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - replyPad);
        }

        private int replyWindow()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - encodeSlotOffset - controlSlotOffset -
                EVENT_FRAMES * replyPad - sectionClosing());
        }

        private int sectionClosing()
        {
            return sectionOpen && http.framing && objectShaped(sectionType) ? TEXT_SUFFIX.length + replyPad : 0;
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
                    http.doEncodeSectionEnd();
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
            http.flushEncode(traceId);
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
            cleanupEncodeSlot();
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
            final String message;

            if (requestSection == null || requestSection == LlmSectionType.NONE)
            {
                message = MESSAGE_SECTION_INVALID;
            }
            else
            {
                final String section = requestSection.name().toLowerCase().replace('_', '-');
                final int kind = sectionKind(requestSection);

                message = http.inputClosed
                    ? MESSAGE_SECTION_CONVERT_AFTER_INPUT.formatted(section, dialect, NAME)
                    : MESSAGE_SECTION_CONVERT.formatted(section, dialect, NAME);
            }

            return message;
        }

        private void flushAppWindow(
            long traceId)
        {
            final int room = (encodeMax - http.encodeSlotOffset - ENCODE_SLACK) / ESCAPE_EXPANSION;
            final int credit = Math.max(0, room - http.holdLength);
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
            String reasoningTag,
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

                        if (reasoningTag != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.REASONING_TAG).value(reasoningTag));
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
            return errorType != null || errorMessage != null || STATUS_FAILED.equals(status);
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
            final String reportedStatus = documentStatus != null ? documentStatus : status;
            final String reportedReason = documentReason != null ? documentReason : reason;

            String neutral = null;

            if (STATUS_COMPLETED.equals(reportedStatus))
            {
                neutral = "stop";
            }
            else if (STATUS_INCOMPLETE.equals(reportedStatus) && reportedReason != null)
            {
                neutral = switch (reportedReason)
                {
                case REASON_MAX_OUTPUT_TOKENS -> "length";
                case REASON_CONTENT_FILTER -> "content-filter";
                default -> VENDOR_PREFIX + reportedReason;
                };
            }

            return neutral;
        }

        private void populateUsage(
            LlmUsageFW.Builder usage)
        {
            usage.inputTokens(inputTokens)
                .cacheWriteTokens(cacheWriteTokens)
                .cacheReadTokens(cacheReadTokens)
                .outputTokens(outputTokens)
                .reasoningTokens(reasoningTokens)
                .totalTokens(totalTokens);
        }

        private void populateError(
            LlmErrorFW.Builder error)
        {
            error.status(NONE)
                .type(errorType)
                .param(errorParam)
                .message(errorMessage)
                .code(errorCode);
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
            highSurrogate = 0;
            cleanupEncodeSlot();
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
            cancelControl();
            decoder = decodeIgnore;
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
                int window = Math.min(limit - progress, replyWindow());

                if (window == 0 && limit > progress && encodeSlotOffset > 0 && replyCredit() > 0)
                {
                    flushHeld();
                    window = Math.min(limit - progress, replyWindow());
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

            if (controlFrom != NONE)
            {
                controlFrom = offset;
            }

            parser.wrap(buffer, offset, windowLimit, windowLast);

            LlmOpenaiResponsesDecoder previous = null;
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
            return parser.hasNextEvent() && (!begun() || eventSlack() >= EVENT_FRAMES * replyPad);
        }

        private int eventSlack()
        {
            final int pending = controlFrom != NONE ? position() - controlFrom + CONTROL_CLOSING : 0;

            return replyMax - (int) (replySeq - replyAck) - encodeSlotOffset - controlSlotOffset - pending -
                parser.remaining() - sectionClosing();
        }

        private void flushHeld()
        {
            if (encodeSlot != NO_SLOT)
            {
                writeSection(encodePool.buffer(encodeSlot), 0, encodeSlotOffset, false);
                cleanupEncodeSlot();
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
            String scanParent = null;
            boolean found = false;

            scanError = false;

            while (!found && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    scanDepth++;
                    if (scanDepth == 2)
                    {
                        scanParent = scanKey;
                        scanError = event == JsonEvent.START_OBJECT && MEMBER_ERROR.equals(scanKey);
                        found = scanError;
                    }
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
                    else
                    {
                        scanKey = parser.getString();
                    }
                    break;
                case VALUE_STRING:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(deferredLength(event));
                    }
                    else if (scanKey != null)
                    {
                        found = scanValue(scanDepth, scanKey, scanParent);
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
            int scanDepth,
            String scanKey,
            String scanParent)
        {
            if (scanDepth == 1 && MEMBER_TYPE.equals(scanKey))
            {
                scanError = TYPE_ERROR.equals(parser.getString());
            }
            else if (scanDepth == 1 && MEMBER_ID.equals(scanKey) ||
                scanDepth == 2 && MEMBER_RESPONSE.equals(scanParent) && MEMBER_ID.equals(scanKey))
            {
                documentId = parser.getString();
            }
            else if (scanDepth == 1 && MODEL.equals(scanKey) ||
                scanDepth == 2 && MEMBER_RESPONSE.equals(scanParent) && MODEL.equals(scanKey))
            {
                documentServedModel = parser.getString();
            }

            return scanError || documentId != null && documentServedModel != null;
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
                    decoder = decodeEnd;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_TYPE.contentEquals(member) && documentType == null)
                    {
                        decoder = decodeType;
                    }
                    else if (documentType == null)
                    {
                        decodeResponseMember(member, decodeRoot);
                    }
                    else if (MEMBER_RESPONSE.contentEquals(member))
                    {
                        decoder = decodeResponseStart;
                    }
                    else if (MEMBER_ITEM.contentEquals(member))
                    {
                        decoder = decodeItemStart;
                    }
                    else if (MEMBER_PART.contentEquals(member))
                    {
                        decoder = decodePartStart;
                    }
                    else if (MEMBER_DELTA.contentEquals(member) && TYPE_TEXT_DELTA.equals(documentType))
                    {
                        beginText(decodeRoot);
                    }
                    else if (TYPE_ERROR.equals(documentType) && MEMBER_CODE.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeErrorCode;
                    }
                    else if (TYPE_ERROR.equals(documentType) && MEMBER_MESSAGE.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeErrorMessage;
                    }
                    else if (TYPE_ERROR.equals(documentType) && MEMBER_PARAM.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeErrorParam;
                    }
                    else
                    {
                        skipMember(decodeRoot);
                    }
                }
            }

            return position();
        }

        private void decodeResponseMember(
            CharSequence member,
            LlmOpenaiResponsesDecoder then)
        {
            if (MEMBER_ID.contentEquals(member))
            {
                valueThen = then;
                decoder = decodeId;
            }
            else if (MODEL.contentEquals(member))
            {
                valueThen = then;
                decoder = decodeModel;
            }
            else if (MEMBER_STATUS.contentEquals(member))
            {
                valueThen = then;
                decoder = decodeStatus;
            }
            else if (MEMBER_INCOMPLETE_DETAILS.contentEquals(member))
            {
                incompleteThen = then;
                decoder = decodeIncompleteStart;
            }
            else if (MEMBER_ERROR.contentEquals(member))
            {
                errorThen = then;
                decoder = decodeErrorObjectStart;
            }
            else if (MEMBER_OUTPUT.contentEquals(member) && !http.framing)
            {
                outputThen = then;
                decoder = decodeOutputStart;
            }
            else if (MEMBER_OUTPUT.contentEquals(member) || MEMBER_OBJECT.contentEquals(member))
            {
                skipMember(then);
            }
            else if (USAGE.contentEquals(member))
            {
                openControl(member, ROOT_CONTROL, then);
                beginUsage();
                usageThen = then;
                decoder = decodeUsage;
            }
            else
            {
                beginControl(member, ROOT_CONTROL, then);
            }
        }

        private int decodeType(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeType && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes())
                {
                    documentType = eventType(parser.getStringView());
                    decoder = decodeRoot;

                    if (TYPE_TEXT_DONE.equals(documentType) ||
                        TYPE_PART_DONE.equals(documentType) ||
                        TYPE_ITEM_DONE.equals(documentType) ||
                        TYPE_COMPLETED.equals(documentType) ||
                        TYPE_INCOMPLETE.equals(documentType))
                    {
                        closeSection();
                    }
                }
                else
                {
                    stringValue(event, decodeRoot);
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

        private int decodeStatus(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeStatus && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentStatus = value;
                }
            }

            return position();
        }

        private int decodeResponseStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeResponse;
                }
                else
                {
                    skipAfter(event, decodeRoot);
                }
            }

            return position();
        }

        private int decodeResponse(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeResponse && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeRoot;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    decodeResponseMember(parser.getStringView(), decodeResponse);
                }
            }

            return position();
        }

        private int decodeIncompleteStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeIncomplete;
                }
                else
                {
                    skipAfter(event, incompleteThen);
                }
            }

            return position();
        }

        private int decodeIncomplete(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeIncomplete && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = incompleteThen;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (MEMBER_REASON.contentEquals(parser.getStringView()))
                {
                    valueThen = decodeIncomplete;
                    decoder = decodeReason;
                }
                else
                {
                    skipMember(decodeIncomplete);
                }
            }

            return position();
        }

        private int decodeReason(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeReason && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentReason = value;
                }
            }

            return position();
        }

        private int decodeOutputStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeOutput;
                }
                else
                {
                    skipAfter(event, outputThen);
                }
            }

            return position();
        }

        private int decodeOutput(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeOutput && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    itemThen = decodeOutput;
                    decoder = decodeItem;
                }
                else if (event == JsonEvent.END_ARRAY)
                {
                    decoder = outputThen;
                }
                else
                {
                    skipAfter(event, decodeOutput);
                }
            }

            return position();
        }

        private int decodeItemStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    itemThen = decodeRoot;
                    decoder = decodeItem;
                }
                else
                {
                    skipAfter(event, decodeRoot);
                }
            }

            return position();
        }

        private int decodeItem(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeItem && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = itemThen;
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
                        decoder = decodeItemType;
                    }
                    else if (MEMBER_CONTENT.contentEquals(member) && !http.framing)
                    {
                        decoder = decodeContentStart;
                    }
                    else
                    {
                        skipMember(decodeItem);
                    }
                }
            }

            return position();
        }

        private int decodeItemType(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeItemType && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeItem);

                if (value != null && !ITEM_MESSAGE.equals(value))
                {
                    rejectOutput(MESSAGE_ITEM_UNSUPPORTED.formatted(value));
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

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeContent;
                }
                else
                {
                    skipAfter(event, decodeItem);
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

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeContentPart;
                }
                else if (event == JsonEvent.END_ARRAY)
                {
                    decoder = decodeItem;
                }
                else
                {
                    skipAfter(event, decodeContent);
                }
            }

            return position();
        }

        private int decodeContentPart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeContentPart && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeContent;
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
                        valueThen = decodeContentPart;
                        decoder = decodePartType;
                    }
                    else if (MEMBER_TEXT.contentEquals(member))
                    {
                        beginText(decodeContentPart);
                    }
                    else
                    {
                        skipMember(decodeContentPart);
                    }
                }
            }

            return position();
        }

        private int decodePartStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodePart;
                }
                else
                {
                    skipAfter(event, decodeRoot);
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
                    decoder = decodeRoot;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (MEMBER_TYPE.contentEquals(parser.getStringView()))
                {
                    valueThen = decodePart;
                    decoder = decodePartType;
                }
                else
                {
                    skipMember(decodePart);
                }
            }

            return position();
        }

        private int decodePartType(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodePartType && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null && !PART_OUTPUT_TEXT.equals(value))
                {
                    rejectOutput(MESSAGE_PART_UNSUPPORTED.formatted(value));
                }
                else if (value != null && TYPE_PART_ADDED.equals(documentType))
                {
                    openSection(LlmSectionType.ASSISTANT_TEXT, ASSISTANT_MESSAGE, null, null);
                }
            }

            return position();
        }

        private int decodeText(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int eventFrom = position();

            while (decoder == decodeText && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();
                    final boolean stalled = emitText(deferred, eventFrom);

                    if (!deferred && !stalled)
                    {
                        decoder = textThen;
                    }
                    else if (stalled)
                    {
                        break;
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

        private int decodeUsage(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeUsage && hasNextEvent())
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
                    if (event == JsonEvent.VALUE_NULL)
                    {
                        cancelControl();
                    }
                    else if (controlFrom != NONE)
                    {
                        endControl();
                    }

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
                        valueThen = decodeErrorObject;
                        decoder = decodeErrorType;
                    }
                    else if (MEMBER_CODE.contentEquals(member))
                    {
                        valueThen = decodeErrorObject;
                        decoder = decodeErrorCode;
                    }
                    else if (MEMBER_PARAM.contentEquals(member))
                    {
                        valueThen = decodeErrorObject;
                        decoder = decodeErrorParam;
                    }
                    else if (MEMBER_MESSAGE.contentEquals(member))
                    {
                        valueThen = decodeErrorObject;
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
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentErrorType = value;
                }
            }

            return position();
        }

        private int decodeErrorCode(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeErrorCode && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentErrorCode = value;
                }
            }

            return position();
        }

        private int decodeErrorParam(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeErrorParam && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentErrorParam = value;
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
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentErrorMessage = value;
                }
            }

            return position();
        }

        private String eventType(
            CharSequence value)
        {
            String type = TYPE_OTHER;

            for (int index = 0; index < EVENT_TYPES.length && TYPE_OTHER.equals(type); index++)
            {
                if (EVENT_TYPES[index].contentEquals(value))
                {
                    type = EVENT_TYPES[index];
                }
            }

            return type;
        }

        private void rejectOutput(
            String message)
        {
            errorType = ERROR_TYPE_SERVER;
            errorMessage = message;
            parseRejected = true;
        }

        private String stringValue(
            JsonEvent event,
            LlmOpenaiResponsesDecoder then)
        {
            return stringValue(event, then, null);
        }

        private String stringValue(
            JsonEvent event,
            LlmOpenaiResponsesDecoder then,
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

        private void skipMember(
            LlmOpenaiResponsesDecoder then)
        {
            skipDepth = 0;
            skipThen = then;
            decoder = decodeSkip;
        }

        private void skipAfter(
            JsonEvent event,
            LlmOpenaiResponsesDecoder then)
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
                case "usage.input_tokens":
                    documentInputTokens = parser.getInt();
                    break;
                case "usage.output_tokens":
                    documentOutputTokens = parser.getInt();
                    break;
                case "usage.total_tokens":
                    documentTotalTokens = parser.getInt();
                    break;
                case "usage.input_tokens_details.cached_tokens":
                    documentCacheReadTokens = parser.getInt();
                    break;
                case "usage.input_tokens_details.cache_write_tokens":
                    documentCacheWriteTokens = parser.getInt();
                    break;
                case "usage.output_tokens_details.reasoning_tokens":
                    documentReasoningTokens = parser.getInt();
                    break;
                default:
                    break;
                }
            }
        }

        private void beginText(
            LlmOpenaiResponsesDecoder then)
        {
            textType = LlmSectionType.ASSISTANT_TEXT;
            textThen = then;
            decoder = decodeText;
        }

        private boolean emitText(
            boolean deferred,
            int eventFrom)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            final int take = textAllowance(chars, deferred);
            final boolean finished = !deferred && take == length;

            if (take > 0 || finished && highSurrogate != 0)
            {
                final int decoded = encodeText(chars, take, finished);

                text(textBuffer, 0, decoded);
            }

            if (finished && !http.framing)
            {
                if (!sectionOpen)
                {
                    openSection(textType, ASSISTANT_MESSAGE, null, null);
                }

                closeSection();
            }
            else if (!finished && take > 0)
            {
                parser.consumed(take);
            }

            return take < length;
        }

        private int textAllowance(
            CharSequence chars,
            boolean deferred)
        {
            final int length = chars.length();
            final int credit = replyMax - (int) (replySeq - replyAck) - encodeSlotOffset;

            int cost = sectionOpen ? 0 : TEXT_PREFIX.length + replyPad;
            int take = 0;
            int index = 0;
            boolean fits = true;

            while (fits && index < length)
            {
                final char c = chars.charAt(index);
                final boolean pair = Character.isHighSurrogate(c) && index + 1 < length &&
                    Character.isLowSurrogate(chars.charAt(index + 1));
                final int width = pair ? 2 : 1;
                final int escaped = pair ? 4 : escapedWidth(c);
                final int next = cost + escaped;
                final boolean last = index + width == length;
                final boolean closes = last && !deferred && !http.framing;

                fits = next + (closes ? TEXT_SUFFIX.length + replyPad : 0) <= credit;

                if (fits)
                {
                    cost = next;
                    index += width;
                    take = index;
                }
            }

            if (length == 0 && !deferred && !http.framing &&
                cost + TEXT_SUFFIX.length + replyPad > credit)
            {
                take = -1;
            }

            return take;
        }

        private static int escapedWidth(
            char c)
        {
            final int width;

            if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f')
            {
                width = 2;
            }
            else if (c < 0x20)
            {
                width = 6;
            }
            else if (c < 0x80)
            {
                width = 1;
            }
            else if (c < 0x800)
            {
                width = 2;
            }
            else
            {
                width = 3;
            }

            return width;
        }

        private void text(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (sectionOpen && (sectionType != textType || sectionMessage != ASSISTANT_MESSAGE))
            {
                closeSection();
            }

            if (!sectionOpen)
            {
                openSection(textType, ASSISTANT_MESSAGE, null, null);
            }

            final int encoded = encodePiece(buffer, offset, length);

            emitFrame(pieceBuffer, 0, encoded, false);
        }

        private void beginControl(
            CharSequence member,
            int message,
            LlmOpenaiResponsesDecoder then)
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
            LlmOpenaiResponsesDecoder then)
        {
            controlKey.setLength(0);
            controlKey.append(VENDOR).append(':').append(member);

            final int capacity = controlKey.length() * ESCAPE_EXPANSION + CONTROL_HEADER;

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
                    doAppFrame(http.decodeTraceId, Flags.NONE, LlmSectionType.CONTROL, controlMessage, null, null, null,
                        decodeInput, controlFrom, to - controlFrom);
                }
                else
                {
                    encodePool.buffer(controlSlot).putBytes(controlSlotOffset, decodeInput, controlFrom, to - controlFrom);
                    controlSlotOffset += to - controlFrom;
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

                    doAppFrame(http.decodeTraceId, Flags.COMPLETE, LlmSectionType.CONTROL, message, null, null, null,
                        slot, index + CONTROL_HEADER, length);

                    index += CONTROL_HEADER + length;
                }

                doAppFrame(http.decodeTraceId, Flags.INIT, LlmSectionType.CONTROL, controlMessage, null, null, null,
                    slot, controlEntry + CONTROL_HEADER, controlSlotOffset - controlEntry - CONTROL_HEADER);

                cleanupControlSlot();
                decoder = decodeControlStream;
            }
        }

        private void endControlStream()
        {
            flushControl(position());

            doAppFrame(http.decodeTraceId, Flags.FIN, LlmSectionType.CONTROL, controlMessage, null, null, null,
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

                    doAppFrame(http.decodeTraceId, Flags.COMPLETE, LlmSectionType.CONTROL, message, null, null, null,
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

        private void openSection(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName)
        {
            emitControls();

            sectionOpen = true;
            sectionStarted = false;
            sectionWritten = false;
            sectionType = type;
            sectionMessage = message;
            sectionToolId = toolCallId;
            sectionToolName = toolName;
            sectionTag = null;
        }

        private void closeSection()
        {
            if (sectionOpen)
            {
                if (objectShaped(sectionType))
                {
                    final int encoded = encodePieceEnd();

                    emitFrame(pieceBuffer, 0, encoded, true);
                }
                else
                {
                    emitSection(pieceBuffer, 0, 0, true);
                }
            }

            sectionOpen = false;

            emitControls();
        }

        private void emitFrame(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            sectionStarted = true;
            sectionOpen = !fin;

            writeSection(buffer, offset, length, fin);
        }

        private void emitSection(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            if (sectionOpen)
            {
                sectionStarted = true;
                sectionOpen = !fin;

                emitAtomicSection(buffer, offset, length, fin);
            }
        }

        private void emitAtomicSection(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            if (encodeSlot != NO_SLOT && fin && length == 0)
            {
                writeSection(encodePool.buffer(encodeSlot), 0, encodeSlotOffset, true);
                cleanupEncodeSlot();
            }
            else
            {
                flushHeld();

                if (fin)
                {
                    writeSection(buffer, offset, length, true);
                }
                else
                {
                    holdSection(buffer, offset, length);
                }
            }
        }

        private void holdSection(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length <= encodeMax)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            if (encodeSlot == NO_SLOT)
            {
                cleanup(http.decodeTraceId);
            }
            else
            {
                encodePool.buffer(encodeSlot).putBytes(0, buffer, offset, length);
                encodeSlotOffset = length;
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

        private void writeSection(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            final int flags = sectionWritten ? Flags.NONE : Flags.INIT;

            sectionWritten = true;

            doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, sectionType, sectionMessage,
                sectionToolId, sectionToolName, sectionTag, buffer, offset, length);
        }

        private void commitDocument()
        {
            inputTokens = documentInputTokens != NONE ? documentInputTokens : inputTokens;
            outputTokens = documentOutputTokens != NONE ? documentOutputTokens : outputTokens;
            totalTokens = documentTotalTokens != NONE ? documentTotalTokens : totalTokens;
            cacheReadTokens = documentCacheReadTokens != NONE ? documentCacheReadTokens : cacheReadTokens;
            cacheWriteTokens = documentCacheWriteTokens != NONE ? documentCacheWriteTokens : cacheWriteTokens;
            reasoningTokens = documentReasoningTokens != NONE ? documentReasoningTokens : reasoningTokens;
            errorType = documentErrorType != null ? documentErrorType : errorType;
            errorCode = documentErrorCode != null ? documentErrorCode : errorCode;
            errorParam = documentErrorParam != null ? documentErrorParam : errorParam;
            errorMessage = documentErrorMessage != null ? documentErrorMessage : errorMessage;
            id = documentId != null ? documentId : id;
            servedModel = documentServedModel != null ? documentServedModel : servedModel;
            status = documentStatus != null ? documentStatus : status;
            reason = documentReason != null ? documentReason : reason;

            documentType = null;
            documentInputTokens = NONE;
            documentOutputTokens = NONE;
            documentTotalTokens = NONE;
            documentCacheReadTokens = NONE;
            documentCacheWriteTokens = NONE;
            documentReasoningTokens = NONE;
            documentErrorType = null;
            documentErrorCode = null;
            documentErrorParam = null;
            documentErrorMessage = null;
            documentId = null;
            documentServedModel = null;
            documentStatus = null;
            documentReason = null;
        }

        private int encodePiece(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final int capacity = TEXT_PREFIX.length + length * ESCAPE_EXPANSION;

            if (pieceBytes.length < capacity)
            {
                pieceBytes = new byte[capacity];
                pieceBuffer = new UnsafeBufferEx(pieceBytes);
            }

            generator.wrap(pieceBuffer, 0, pieceBytes.length);

            if (!sectionStarted)
            {
                beginPieceObject();
            }

            generator.writeSegment(buffer, offset, length);

            return generator.length();
        }

        private int encodePieceEnd()
        {
            generator.wrap(pieceBuffer, 0, pieceBytes.length);

            if (!sectionStarted)
            {
                beginPieceObject();
            }

            generator.writeEndEscaped().writeEnd();

            return generator.length();
        }

        private void beginPieceObject()
        {
            generator.reset();
            generator.writeStartObject().writeKey(MEMBER_TEXT).writeStartEscaped();
        }

        private int encodeText(
            CharSequence chars,
            int length,
            boolean last)
        {
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
                    cleanupEncodeSlot();
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
                cleanupEncodeSlot();
                cleanupControlSlot();
            }
        }

        private void doAppReset(
            long traceId,
            int status,
            String type,
            String message)
        {
            doAppReset(traceId, status, type, null, null, message);
        }

        private void doAppReset(
            long traceId,
            int status,
            String type,
            String param,
            String code,
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
                    if (param != null)
                    {
                        e.param(param);
                    }
                    if (message != null)
                    {
                        e.message(message);
                    }
                    if (code != null)
                    {
                        e.code(code);
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
            cleanupEncodeSlot();
            cleanupControlSlot();
            http.cleanupEncodeSlot();
            http.cleanupDecodeSlot();
        }
    }

    private final class LlmOpenaiResponsesHttpStream
    {
        private final HttpDataExFW httpDataExRO = new HttpDataExFW();
        private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
        private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();
        private final LlmOpenaiResponsesStream stream;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String scheme;
        private final String authority;
        private final String requestPath;
        private final boolean sameDialect;

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
        private boolean encodeFailed;
        private boolean sectionsStarted;

        private int messageIndex = NO_MESSAGE;
        private int instructionsMessage = NO_MESSAGE;
        private boolean inputOpened;
        private boolean inputClosed;
        private boolean translated;
        private boolean sectionStreaming;
        private LlmSectionPipeline sectionPipeline;
        private LlmSectionPipeline.Output sectionOutput;
        private LlmSectionPipeline controlPipeline;
        private LlmOpenaiResponsesControlFromCanonical controlTransform;
        private int controlMessage;
        private LlmTextPipeline encodeText;
        private final LlmTextPipeline sectionText = new LlmTextPipeline(this::onSectionText);
        private final LlmSectionPipeline.Output rawOutput = (buffer, offset, length, last) -> putRaw(buffer, offset, length);
        private final LlmSectionPipeline.Output controlOutput =
            (buffer, offset, length, last) -> putControl(buffer, offset, length);
        private String messageRole;
        private int contentState;
        private int sectionKind;
        private boolean sectionOpen;
        private boolean sectionArrayPart;

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

        private LlmOpenaiResponsesHttpStream(
            LlmOpenaiResponsesStream stream)
        {
            final LlmServerConfig server = stream.binding.options.server;

            this.stream = stream;
            this.originId = stream.routedId;
            this.routedId = stream.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.scheme = server.scheme;
            this.authority = server.host + ":" + server.port;
            this.requestPath = server.path + PATH_RESPONSES;
            this.sameDialect = NAME.equals(stream.dialect);
            this.generator = JsonEx.createGenerator();
            this.bodyHeld = true;
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
            if (beginEncode(ENCODE_STRUCTURE + modelRoom()))
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

        private LlmSectionPipeline pipelineFor(
            int kind)
        {
            return kind == KIND_CONTROL ? controlPipeline() : null;
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmOpenaiResponsesControlFromCanonical(sameDialect);
                controlPipeline = new LlmSectionPipeline(controlTransform, transformBuffer);
            }

            return controlPipeline;
        }

        private void onSectionText(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (text(sectionKind) && contentState == CONTENT_HELD && !sectionArrayPart)
            {
                stageText(buffer, offset, length);
            }
            else if (beginEncode(0))
            {
                putEscaped(buffer, offset, length);
                endEncode();
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
            final boolean instructions = kind == KIND_SYSTEM_INSTRUCTION && instructionsMessage == NO_MESSAGE;

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
            else if (kind == 0 || kind != KIND_CONTROL && !instructions && inputClosed)
            {
                encodeFailed = true;
            }
            else if (beginEncode(ENCODE_STRUCTURE + sectionRoom(extension)))
            {
                if (kind == KIND_CONTROL)
                {
                    controlMessage = message;
                }
                else if (instructions)
                {
                    beginInstructions(message);
                }
                else
                {
                    ensureMessage(kind, message);
                    beginText();
                }

                if (translated && !encodeFailed)
                {
                    beginTranslated(kind);
                }

                endEncode();
            }

            if (!encodeFailed && text(kind))
            {
                encodeText = sectionText;
                encodeText.begin();
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
            beginSectionStreaming(pipelineFor(kind), kind == KIND_CONTROL ? controlOutput : rawOutput);
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

            if (member != null && controlMessage == NONE)
            {
                if (!rawOpen)
                {
                    openControl(member);
                }

                putRaw(buffer, offset, length);
            }
        }

        private void openControl(
            String member)
        {
            closeMessage();
            closeRegions();

            generator.writeKey(member);
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

        private void beginInstructions(
            int message)
        {
            closeMessage();
            closeRegions();

            instructionsMessage = message;
            contentState = CONTENT_CLOSED;
            generator.writeKey(MEMBER_INSTRUCTIONS);
            generator.writeStartEscaped();
            sectionOpen = true;
        }

        private void openInput()
        {
            if (!inputOpened)
            {
                generator.writeStartArray(MEMBER_INPUT);
                inputOpened = true;
            }
        }

        private void ensureMessage(
            int kind,
            int message)
        {
            openInput();

            final String role = role(kind);

            if (message != messageIndex || !role.equals(messageRole))
            {
                closeMessage();
                openMessage(role, message);
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
                    if (!sectionPipeline.feed(buffer, offset, length, fin, sectionOutput))
                    {
                        encodeFailed = true;
                    }

                    endEncode();
                }
            }
            else if (encodeText != null)
            {
                if (!encodeText.feed(buffer, offset, length, fin))
                {
                    encodeFailed = true;
                }
            }
            else if (!translated && sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(0))
            {
                putRaw(buffer, offset, length);
                endEncode();
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
            encodeText = null;

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

        private void doEncodeRequestEnd(
            int maxOutputTokens)
        {
            if (!LlmState.initialClosing(state) && !LlmState.initialClosed(state))
            {
                if (beginEncode(ENCODE_STRUCTURE + ENCODE_MAX_OUTPUT_TOKENS))
                {
                    closeMessage();
                    closeRegions();

                    if (maxOutputTokens != NO_MAX_OUTPUT_TOKENS)
                    {
                        generator.write(MEMBER_MAX_OUTPUT_TOKENS, maxOutputTokens);
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
            if (inputOpened && !inputClosed)
            {
                generator.writeEnd();
                inputClosed = true;
            }
        }

        private void openMessage(
            String role,
            int message)
        {
            generator.writeStartObject();

            messageIndex = message;
            contentState = CONTENT_NONE;

            generator.write(MEMBER_ROLE, role);

            messageRole = role;
        }

        private void closeMessage()
        {
            if (messageIndex != NO_MESSAGE)
            {
                closeContent();

                generator.writeEnd();
                messageIndex = NO_MESSAGE;
                messageRole = null;
            }
        }

        private void closeContent()
        {
            if (contentState == CONTENT_HELD)
            {
                commitString();
            }
            else if (contentState == CONTENT_ARRAY)
            {
                generator.writeEnd();
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
            generator.writeStartObject().write(MEMBER_TYPE, partType()).writeKey(MEMBER_TEXT);
            generator.writeStartEscaped();
            sectionArrayPart = true;
        }

        private String partType()
        {
            return ROLE_ASSISTANT.equals(messageRole) ? PART_OUTPUT_TEXT : PART_INPUT_TEXT;
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
            generator.writeStartObject().write(MEMBER_TYPE, partType()).writeKey(MEMBER_TEXT);
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
                stream.doAppReset(traceId, errorStatus, stream.errorType, stream.errorParam, stream.errorCode,
                    stream.errorMessage);
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
                final OctetsFW attributes = entry.extension();
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
            DirectBufferEx buffer,
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
                    decodeFragment(buffer, offset, offset + length, last);
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
