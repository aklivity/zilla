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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmAnthropicDialectFactorySpi.NAME;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

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
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
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
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAttributeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmChallengeExFW;
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
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmAnthropicClientFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_SCHEME = ":scheme";
    private static final String HEADER_AUTHORITY = ":authority";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-api-key";
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";
    private static final String HEADER_ANTHROPIC_BETA = "anthropic-beta";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String METHOD_POST = "POST";
    private static final String PATH_MESSAGES = "/messages";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ERROR_TYPE_INVALID_REQUEST = "invalid_request_error";
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
    private static final int KIND_ASSISTANT_TEXT = 6;
    private static final int KIND_ASSISTANT_REFUSAL = 7;
    private static final int KIND_ASSISTANT_REASONING = 8;
    private static final int KIND_TOOL_DEFINITION = 9;
    private static final int KIND_TOOL_CALL = 10;
    private static final int KIND_TOOL_RESULT = 11;
    private static final int KIND_SERVER_TOOL_RESULT = 12;
    private static final int KIND_CITATIONS = 13;
    private static final int KIND_UNKNOWN = 14;
    private static final int KIND_DROPPED = 16;

    private static final int ROLE_NONE = 0;
    private static final int ROLE_USER = 1;
    private static final int ROLE_ASSISTANT = 2;

    private static final int TOOL_RESULT_CONTENT_OPEN = 1;
    private static final int TOOL_RESULT_CONTENT_CLOSED = 2;
    private static final int REGION_NONE = 0;
    private static final int REGION_MESSAGES = 1;
    private static final int REGION_SYSTEM = 2;
    private static final int REGION_TOOLS = 3;

    private static final int NO_MESSAGE = -2;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_SLACK = 256;
    private static final int ENCODE_STRUCTURE = 128;
    private static final int ENCODE_MAX_TOKENS = 32;
    private static final int NO_MAX_OUTPUT_TOKENS = -1;

    private static final int NONE = -1;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private static final String BLOCK_TOOL_USE = "tool_use";
    private static final String BLOCK_OTHER = "other";
    private static final String KEY_STOP_REASON = "stop_reason";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_MODEL = "model";
    private static final String MEMBER_ROLE = "role";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_MESSAGE = "message";
    private static final String MEMBER_CONTENT = "content";
    private static final String MEMBER_CONTENT_BLOCK = "content_block";
    private static final String MEMBER_DELTA = "delta";
    private static final String MEMBER_USAGE = "usage";
    private static final String MEMBER_ERROR = "error";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_THINKING = "thinking";
    private static final String MEMBER_INPUT = "input";
    private static final String MEMBER_PARTIAL_JSON = "partial_json";
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_MESSAGE_START = "message_start";
    private static final String TYPE_MESSAGE_DELTA = "message_delta";
    private static final String TYPE_MESSAGE_STOP = "message_stop";
    private static final String TYPE_CONTENT_BLOCK_DELTA = "content_block_delta";
    private static final String TYPE_CONTENT_BLOCK_STOP = "content_block_stop";
    private static final String USAGE_INPUT_TOKENS = "input_tokens";
    private static final String USAGE_CACHE_CREATION_INPUT_TOKENS = "cache_creation_input_tokens";
    private static final String USAGE_CACHE_READ_INPUT_TOKENS = "cache_read_input_tokens";
    private static final String USAGE_OUTPUT_TOKENS = "output_tokens";
    private static final int USAGE_INPUT = 0;
    private static final int USAGE_CACHE_WRITE = 1;
    private static final int USAGE_CACHE_READ = 2;
    private static final int USAGE_OUTPUT = 3;

    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);

    private static final String MEMBER_MESSAGES = "messages";
    private static final String MEMBER_SYSTEM = "system";
    private static final String MEMBER_MAX_TOKENS = "max_tokens";
    private static final String MEMBER_TOOLS = "tools";
    private static final String MEMBER_SOURCE = "source";
    private static final String MEMBER_MEDIA_TYPE = "media_type";
    private static final String MEMBER_DATA = "data";
    private static final String MEMBER_URL = "url";
    private static final String MEMBER_FILE_ID = "file_id";
    private static final String MEMBER_TOOL_USE_ID = "tool_use_id";
    private static final String MEMBER_IS_ERROR = "is_error";
    private static final String MEMBER_CITATIONS = "citations";
    private static final String PART_TEXT = "text";
    private static final String PART_IMAGE = "image";
    private static final String PART_DOCUMENT = "document";
    private static final String PART_TOOL_RESULT = "tool_result";
    private static final String SOURCE_BASE64 = "base64";
    private static final String SOURCE_URL = "url";
    private static final String SOURCE_FILE = "file";
    private static final String ROLE_NAME_USER = "user";
    private static final String ROLE_NAME_ASSISTANT = "assistant";

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
    private final LlmChallengeExFW.Builder llmChallengeExRW = new LlmChallengeExFW.Builder();
    private final LlmFlushExFW llmFlushExRO = new LlmFlushExFW();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmResetExFW.Builder llmResetExRW = new LlmResetExFW.Builder();
    private final LlmEndExFW.Builder llmEndExRW = new LlmEndExFW.Builder();
    private final LlmAbortExFW.Builder llmAbortExRW = new LlmAbortExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final Matcher contentTypeMatcher;
    private final MutableDirectBufferEx transformBuffer;
    private final JsonGeneratorEx escaper;
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
    private final int defaultMaxTokens;

    public LlmAnthropicClientFactory(
        LlmConfiguration config,
        EngineContext context)
    {
        this.defaultMaxTokens = config.anthropicMaxTokens();
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
            newStream = new LlmAnthropicStream(
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
    private interface LlmAnthropicResponseDecoder
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
        case ASSISTANT_REASONING -> KIND_ASSISTANT_REASONING;
        case TOOL_DEFINITION -> KIND_TOOL_DEFINITION;
        case TOOL_CALL -> KIND_TOOL_CALL;
        case TOOL_RESULT -> KIND_TOOL_RESULT;
        case SERVER_TOOL_RESULT -> KIND_SERVER_TOOL_RESULT;
        case CITATIONS -> KIND_CITATIONS;
        case UNKNOWN -> KIND_UNKNOWN;
        default -> 0;
        };
    }

    private static int role(
        int kind,
        int current)
    {
        return switch (kind)
        {
        case KIND_ASSISTANT_TEXT, KIND_ASSISTANT_REFUSAL, KIND_ASSISTANT_REASONING, KIND_TOOL_CALL,
            KIND_SERVER_TOOL_RESULT -> ROLE_ASSISTANT;
        case KIND_UNKNOWN -> current != ROLE_NONE ? current : ROLE_USER;
        default -> ROLE_USER;
        };
    }

    private static boolean part(
        int kind)
    {
        return kind == KIND_SYSTEM_INSTRUCTION ||
            kind == KIND_USER_TEXT ||
            kind == KIND_ASSISTANT_TEXT ||
            kind == KIND_TOOL_RESULT;
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

    private final class LlmAnthropicStream
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
        private final String beta;
        private final LlmAnthropicHttpStream http;
        private final JsonParserEx parser;
        private final LlmAnthropicResponseDecoder decodeStart = this::decodeStart;
        private final LlmAnthropicResponseDecoder decodeRootStart = this::decodeRootStart;
        private final LlmAnthropicResponseDecoder decodeRoot = this::decodeRoot;
        private final LlmAnthropicResponseDecoder decodeType = this::decodeType;
        private final LlmAnthropicResponseDecoder decodeId = this::decodeId;
        private final LlmAnthropicResponseDecoder decodeModel = this::decodeModel;
        private final LlmAnthropicResponseDecoder decodeMessageStart = this::decodeMessageStart;
        private final LlmAnthropicResponseDecoder decodeMessage = this::decodeMessage;
        private final LlmAnthropicResponseDecoder decodeContentStart = this::decodeContentStart;
        private final LlmAnthropicResponseDecoder decodeContent = this::decodeContent;
        private final LlmAnthropicResponseDecoder decodeContentItem = this::decodeContentItem;
        private final LlmAnthropicResponseDecoder decodeInputStart = this::decodeInputStart;
        private final LlmAnthropicResponseDecoder decodeContentBlockStart = this::decodeContentBlockStart;
        private final LlmAnthropicResponseDecoder decodeContentBlock = this::decodeContentBlock;
        private final LlmAnthropicResponseDecoder decodeBlockType = this::decodeBlockType;
        private final LlmAnthropicResponseDecoder decodeBlockId = this::decodeBlockId;
        private final LlmAnthropicResponseDecoder decodeBlockName = this::decodeBlockName;
        private final LlmAnthropicResponseDecoder decodeBlockText = this::decodeBlockText;
        private final LlmAnthropicResponseDecoder decodeDeltaStart = this::decodeDeltaStart;
        private final LlmAnthropicResponseDecoder decodeDelta = this::decodeDelta;
        private final LlmAnthropicResponseDecoder decodeArguments = this::decodeArguments;
        private final LlmAnthropicResponseDecoder decodeUsageStart = this::decodeUsageStart;
        private final LlmAnthropicResponseDecoder decodeUsage = this::decodeUsage;
        private final LlmAnthropicResponseDecoder decodeUsageValue = this::decodeUsageValue;
        private final LlmAnthropicResponseDecoder decodeCapture = this::decodeCapture;
        private final LlmAnthropicResponseDecoder decodeSkip = this::decodeSkip;
        private final LlmAnthropicResponseDecoder decodeEnd = this::decodeEnd;
        private final LlmAnthropicResponseDecoder decodeIgnore = this::decodeIgnore;
        private final LlmAnthropicResponseDecoder decodeDrainStart = this::decodeDrainStart;
        private final LlmAnthropicResponseDecoder decodeDrainRootStart = this::decodeDrainRootStart;
        private final LlmAnthropicResponseDecoder decodeDrainRoot = this::decodeDrainRoot;
        private final LlmAnthropicResponseDecoder decodeDrainMessageStart = this::decodeDrainMessageStart;
        private final LlmAnthropicResponseDecoder decodeDrainMessage = this::decodeDrainMessage;
        private final LlmAnthropicResponseDecoder decodeErrorStart = this::decodeErrorStart;
        private final LlmAnthropicResponseDecoder decodeErrorRootStart = this::decodeErrorRootStart;
        private final LlmAnthropicResponseDecoder decodeErrorRoot = this::decodeErrorRoot;
        private final LlmAnthropicResponseDecoder decodeErrorObjectStart = this::decodeErrorObjectStart;
        private final LlmAnthropicResponseDecoder decodeErrorObject = this::decodeErrorObject;
        private final LlmAnthropicResponseDecoder decodeErrorType = this::decodeErrorType;
        private final LlmAnthropicResponseDecoder decodeErrorMessage = this::decodeErrorMessage;

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
        private boolean challenged;
        private LlmSectionType requestSection;

        private LlmAnthropicResponseDecoder decoder;
        private LlmAnthropicResponseDecoder skipThen;
        private LlmAnthropicResponseDecoder captureThen;
        private final LlmAnthropicResponseDecoder decodeStopReason = this::decodeStopReason;
        private LlmAnthropicResponseDecoder errorThen;
        private LlmAnthropicResponseDecoder usageThen;
        private LlmAnthropicResponseDecoder valueThen;
        private LlmAnthropicResponseDecoder blockThen;
        private LlmAnthropicResponseDecoder textThen;
        private int decodeLimit;
        private int skipDepth;

        private boolean scanned;
        private boolean scanError;
        private boolean parseRejected;
        private boolean draining;
        private boolean usageOutput;

        private String documentType;

        private int documentInputTokens = NONE;
        private int documentOutputTokens = NONE;
        private int documentCacheWriteTokens = NONE;
        private int documentCacheReadTokens = NONE;
        private String documentErrorType;
        private String documentErrorMessage;
        private String documentId;
        private String documentServedModel;
        private String documentFinishReason;

        private int inputTokens = NONE;
        private int outputTokens = NONE;
        private int cacheWriteTokens = NONE;
        private int cacheReadTokens = NONE;
        private String errorType;
        private String errorMessage;
        private String id;
        private String servedModel;
        private String finishReason;

        private int usageField;
        private String blockType;
        private String toolId;
        private String toolName;
        private LlmSectionType textType;

        private boolean sectionOpen;
        private boolean contentContinued;
        private boolean sectionStarted;
        private boolean sectionWritten;
        private LlmSectionType sectionType;
        private String sectionToolCallId;
        private String sectionToolName;

        private char highSurrogate;
        private byte[] textBytes;
        private UnsafeBufferEx textBuffer;
        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private DirectBufferEx decodeInput;
        private boolean dialectActive;
        private boolean dialectOpen;
        private int dialectFrom;
        private String knownToolId;
        private String knownToolName;

        private int captureDepth;
        private int captureFrom;

        private LlmAnthropicStream(
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
            String beta)
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
            this.beta = beta;
            this.replay = NAME.equals(dialect);
            this.http = new LlmAnthropicHttpStream(this);
            this.parser = JsonEx.createParser();
            this.textBytes = new byte[1024];
            this.textBuffer = new UnsafeBufferEx(textBytes);
            this.decoder = decodeStart;
            this.dialectActive = true;
        }

        private int replyCredit()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - replyPad);
        }

        private int replyWindow()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - encodeSlotOffset - EVENT_FRAMES * replyPad);
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
                else
                {
                    sectionAttributes.mergeFrom(section);
                }

                final OctetsFW payload = data.payload();
                final boolean fin = Flags.hasFin(flags);

                if (replay)
                {
                    if (payload != null && payload.sizeof() > 0)
                    {
                        http.doReplayData(payload.buffer(), payload.offset(), payload.sizeof(), fin);
                    }
                    else if (fin && http.replayPipeline != null)
                    {
                        http.doReplayData(EMPTY_OCTETS.buffer(), 0, 0, true);
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
                        http.doEncodeSectionEnd(LlmSectionAttributes.TRUE.equals(sectionAttributes.isError()));
                    }
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
            final int maxOutputTokens = llmFlushEx != null && llmFlushEx.typeId() == llmTypeId
                ? llmFlushEx.maxOutputTokens()
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
            return replay || requestSection == null || requestSection == LlmSectionType.NONE
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
            boolean dialectFrame,
            boolean complete,
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (dialectFrame && !replay)
            {
                return;
            }

            final OctetsFW extension;

            if (Flags.hasInit(flags) || dialectFrame && complete)
            {
                final LlmDataExFW.Builder dataExBuilder = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId);

                if (dialectFrame)
                {
                    final Array32FW<LlmAttributeFW> eventAttributes = Flags.hasInit(flags) ? http.eventAttributes() : null;

                    dataExBuilder.dialect(d ->
                    {
                        d.complete(complete ? 1 : 0);

                        if (eventAttributes != null)
                        {
                            d.attributes(eventAttributes);
                        }
                    });

                    if (Flags.hasInit(flags))
                    {
                        http.eventAttributesLength = 0;
                    }
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
                case "end_turn", "stop_sequence", "pause_turn" -> "stop";
                case "max_tokens" -> "length";
                case "tool_use" -> "tool-call";
                case "refusal" -> "content-filter";
                default -> reported;
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
                .reasoningTokens(NONE)
                .totalTokens(NONE);

            final JsonObjectBuilder builder = Json.createObjectBuilder();
            addIfPresent(builder, "input_tokens", inputTokens);
            addIfPresent(builder, "cache_creation_input_tokens", cacheWriteTokens);
            addIfPresent(builder, "cache_read_input_tokens", cacheReadTokens);
            addIfPresent(builder, "output_tokens", outputTokens);

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
            highSurrogate = 0;
            cleanupEncodeSlot();
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
            dialectFrom = offset;

            parser.wrap(buffer, offset, windowLimit, windowLast);

            LlmAnthropicResponseDecoder previous = null;
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
            else
            {
                flushDialect(progress, false, false);
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
            final int pending = dialectActive ? position() - dialectFrom : 0;

            return replyMax - (int) (replySeq - replyAck) - encodeSlotOffset - pending - parser.remaining();
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
                    }
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
                        found = scanDepth == 1 && MEMBER_CONTENT.equals(scanKey);
                    }
                    break;
                case VALUE_STRING:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
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
            boolean found = false;

            if (scanDepth == 1 && MEMBER_TYPE.equals(scanKey))
            {
                scanError = MEMBER_ERROR.equals(parser.getString());
                found = scanError;
            }
            else if (scanDepth == 1 && MEMBER_ID.equals(scanKey) ||
                scanDepth == 2 && MEMBER_MESSAGE.equals(scanParent) && MEMBER_ID.equals(scanKey))
            {
                documentId = parser.getString();
            }
            else if (scanDepth == 1 && MEMBER_MODEL.equals(scanKey) ||
                scanDepth == 2 && MEMBER_MESSAGE.equals(scanParent) && MEMBER_MODEL.equals(scanKey))
            {
                documentServedModel = parser.getString();
            }

            return found || documentId != null && documentServedModel != null;
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
                    flushDialect(position(), true, true);
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

                    if (MEMBER_TYPE.contentEquals(member))
                    {
                        decoder = decodeType;
                    }
                    else if (MEMBER_ID.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeId;
                    }
                    else if (MEMBER_MODEL.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeModel;
                    }
                    else if (MEMBER_MESSAGE.contentEquals(member))
                    {
                        decoder = decodeMessageStart;
                    }
                    else if (MEMBER_CONTENT.contentEquals(member))
                    {
                        decoder = decodeContentStart;
                    }
                    else if (MEMBER_CONTENT_BLOCK.contentEquals(member))
                    {
                        decoder = decodeContentBlockStart;
                    }
                    else if (MEMBER_DELTA.contentEquals(member))
                    {
                        decoder = decodeDeltaStart;
                    }
                    else if (MEMBER_USAGE.contentEquals(member))
                    {
                        usageThen = decodeRoot;
                        usageOutput = true;
                        decoder = decodeUsageStart;
                    }
                    else if (KEY_STOP_REASON.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeStopReason;
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

        private int decodeStopReason(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeStopReason && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    documentFinishReason = value;
                }
            }

            return position();
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

                    if (TYPE_CONTENT_BLOCK_STOP.equals(documentType) || TYPE_MESSAGE_DELTA.equals(documentType) ||
                        TYPE_MESSAGE_STOP.equals(documentType))
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

        private int decodeMessageStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeMessage;
                }
                else
                {
                    skipAfter(event, decodeRoot);
                }
            }

            return position();
        }

        private int decodeMessage(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeMessage && hasNextEvent())
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
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_ID.contentEquals(member))
                    {
                        valueThen = decodeMessage;
                        decoder = decodeId;
                    }
                    else if (MEMBER_MODEL.contentEquals(member))
                    {
                        valueThen = decodeMessage;
                        decoder = decodeModel;
                    }
                    else if (MEMBER_USAGE.contentEquals(member))
                    {
                        usageThen = decodeMessage;
                        usageOutput = false;
                        decoder = decodeUsageStart;
                    }
                    else
                    {
                        skipMember(decodeMessage);
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

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeContent;
                }
                else
                {
                    skipAfter(event, decodeRoot);
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

                if (event == JsonEvent.END_ARRAY)
                {
                    decoder = decodeRoot;
                }
                else if (event == JsonEvent.START_OBJECT)
                {
                    blockType = null;
                    toolId = null;
                    toolName = null;
                    decoder = decodeContentItem;
                }
                else
                {
                    skipAfter(event, decodeContent);
                }
            }

            return position();
        }

        private int decodeContentItem(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeContentItem && hasNextEvent())
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
                        blockThen = decodeContentItem;
                        decoder = decodeBlockType;
                    }
                    else if (MEMBER_ID.contentEquals(member))
                    {
                        blockThen = decodeContentItem;
                        decoder = decodeBlockId;
                    }
                    else if (MEMBER_NAME.contentEquals(member))
                    {
                        blockThen = decodeContentItem;
                        decoder = decodeBlockName;
                    }
                    else if (MEMBER_TEXT.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_TEXT, decodeContentItem);
                    }
                    else if (MEMBER_THINKING.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_REASONING, decodeContentItem);
                    }
                    else if (MEMBER_INPUT.contentEquals(member))
                    {
                        decoder = decodeInputStart;
                    }
                    else
                    {
                        skipMember(decodeContentItem);
                    }
                }
            }

            return position();
        }

        private int decodeInputStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT && BLOCK_TOOL_USE.equals(blockType))
                {
                    beginSectionCapture();
                }
                else
                {
                    skipAfter(event, decodeContentItem);
                }
            }

            return position();
        }

        private int decodeContentBlockStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeContentBlock;
                }
                else
                {
                    skipAfter(event, decodeRoot);
                }
            }

            return position();
        }

        private int decodeContentBlock(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeContentBlock && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    if (BLOCK_TOOL_USE.equals(blockType))
                    {
                        knownToolId = toolId;
                        knownToolName = toolName;
                    }

                    decoder = decodeRoot;
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
                        blockThen = decodeContentBlock;
                        decoder = decodeBlockType;
                    }
                    else if (MEMBER_ID.contentEquals(member))
                    {
                        blockThen = decodeContentBlock;
                        decoder = decodeBlockId;
                    }
                    else if (MEMBER_NAME.contentEquals(member))
                    {
                        blockThen = decodeContentBlock;
                        decoder = decodeBlockName;
                    }
                    else
                    {
                        skipMember(decodeContentBlock);
                    }
                }
            }

            return position();
        }

        private int decodeBlockType(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeBlockType && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes())
                {
                    blockType = BLOCK_TOOL_USE.contentEquals(parser.getStringView()) ? BLOCK_TOOL_USE : BLOCK_OTHER;
                    decoder = blockThen;
                }
                else
                {
                    stringValue(event, blockThen);
                }
            }

            return position();
        }

        private int decodeBlockId(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeBlockId && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), blockThen);

                if (value != null)
                {
                    toolId = value;
                }
            }

            return position();
        }

        private int decodeBlockName(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeBlockName && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), blockThen);

                if (value != null)
                {
                    toolName = value;
                }
            }

            return position();
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

        private int decodeDeltaStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeDelta;
                }
                else
                {
                    skipAfter(event, decodeRoot);
                }
            }

            return position();
        }

        private int decodeDelta(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeDelta && hasNextEvent())
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
                    final CharSequence member = parser.getStringView();

                    if (KEY_STOP_REASON.contentEquals(member))
                    {
                        valueThen = decodeDelta;
                        decoder = decodeStopReason;
                    }
                    else if (MEMBER_TEXT.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_TEXT, decodeDelta);
                    }
                    else if (MEMBER_THINKING.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_REASONING, decodeDelta);
                    }
                    else if (MEMBER_PARTIAL_JSON.contentEquals(member))
                    {
                        decoder = decodeArguments;
                    }
                    else
                    {
                        skipMember(decodeDelta);
                    }
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

            while (decoder == decodeArguments && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitArguments(deferred, eventFrom);

                    if (!deferred)
                    {
                        decoder = decodeDelta;
                    }
                }
                else
                {
                    skipAfter(event, decodeDelta);
                }

                eventFrom = position();
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
                    decoder = usageThen;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (USAGE_INPUT_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_INPUT;
                        decoder = decodeUsageValue;
                    }
                    else if (USAGE_CACHE_CREATION_INPUT_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_CACHE_WRITE;
                        decoder = decodeUsageValue;
                    }
                    else if (USAGE_CACHE_READ_INPUT_TOKENS.contentEquals(member))
                    {
                        usageField = USAGE_CACHE_READ;
                        decoder = decodeUsageValue;
                    }
                    else if (USAGE_OUTPUT_TOKENS.contentEquals(member) && usageOutput)
                    {
                        usageField = USAGE_OUTPUT;
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
                    case USAGE_INPUT:
                        documentInputTokens = value;
                        break;
                    case USAGE_CACHE_WRITE:
                        documentCacheWriteTokens = value;
                        break;
                    case USAGE_CACHE_READ:
                        documentCacheReadTokens = value;
                        break;
                    default:
                        documentOutputTokens = value;
                        break;
                    }
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

                captureDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (captureDepth == 0)
                {
                    endCapture(buffer);
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
                else
                {
                    final CharSequence member = parser.getStringView();

                    if (MEMBER_USAGE.contentEquals(member))
                    {
                        usageThen = decodeDrainRoot;
                        usageOutput = true;
                        decoder = decodeUsageStart;
                    }
                    else if (MEMBER_MESSAGE.contentEquals(member))
                    {
                        decoder = decodeDrainMessageStart;
                    }
                    else
                    {
                        skipMember(decodeDrainRoot);
                    }
                }
            }

            return position();
        }

        private int decodeDrainMessageStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    decoder = decodeDrainMessage;
                }
                else
                {
                    skipAfter(event, decodeDrainRoot);
                }
            }

            return position();
        }

        private int decodeDrainMessage(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeDrainMessage && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeDrainRoot;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (MEMBER_USAGE.contentEquals(parser.getStringView()))
                {
                    usageThen = decodeDrainMessage;
                    usageOutput = false;
                    decoder = decodeUsageStart;
                }
                else
                {
                    skipMember(decodeDrainMessage);
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

        private String stringValue(
            JsonEvent event,
            LlmAnthropicResponseDecoder then)
        {
            return stringValue(event, then, null);
        }

        private String stringValue(
            JsonEvent event,
            LlmAnthropicResponseDecoder then,
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

        private String eventType(
            CharSequence type)
        {
            return TYPE_MESSAGE_START.contentEquals(type) ? TYPE_MESSAGE_START :
                TYPE_CONTENT_BLOCK_DELTA.contentEquals(type) ? TYPE_CONTENT_BLOCK_DELTA :
                TYPE_CONTENT_BLOCK_STOP.contentEquals(type) ? TYPE_CONTENT_BLOCK_STOP :
                TYPE_MESSAGE_DELTA.contentEquals(type) ? TYPE_MESSAGE_DELTA :
                TYPE_MESSAGE.contentEquals(type) ? TYPE_MESSAGE :
                TYPE_MESSAGE_STOP.contentEquals(type) ? TYPE_MESSAGE_STOP :
                type.toString();
        }

        private int intValue(
            JsonEvent event,
            LlmAnthropicResponseDecoder then)
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
            LlmAnthropicResponseDecoder then)
        {
            skipDepth = 0;
            skipThen = then;
            decoder = decodeSkip;
        }

        private void skipAfter(
            JsonEvent event,
            LlmAnthropicResponseDecoder then)
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
            LlmAnthropicResponseDecoder then)
        {
            textType = type;
            textThen = then;
            decoder = decodeBlockText;
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
                    openSection(textType, null, null, contentFrom(eventFrom));
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

            contentContinued = deferred;
        }

        private void emitArguments(
            boolean deferred,
            int eventFrom)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            if (length > 0 || highSurrogate != 0 || !deferred && sectionOpen)
            {
                if (!sectionOpen)
                {
                    openSection(LlmSectionType.TOOL_CALL, knownToolId, knownToolName, contentFrom(eventFrom));
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

            contentContinued = deferred;
        }

        private int contentFrom(
            int eventFrom)
        {
            int from = eventFrom;

            if (!contentContinued)
            {
                while (decodeInput.getByte(from) != '"')
                {
                    from++;
                }

                from++;
            }

            return from;
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
            boolean fin,
            boolean complete)
        {
            if (dialectActive)
            {
                final int length = to - dialectFrom;

                if (length > 0 || fin && dialectOpen)
                {
                    final int flags = dialectOpen ? Flags.NONE : Flags.INIT;

                    doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, true, complete, null, NO_MESSAGE, null, null,
                        decodeInput, dialectFrom, length);

                    dialectOpen = !fin;
                }

                dialectFrom = to;
            }
        }

        private void openSection(
            LlmSectionType type,
            String toolCallId,
            String toolName,
            int sectionFrom)
        {
            flushDialect(sectionFrom, true, false);
            dialectActive = false;

            sectionOpen = true;
            sectionStarted = false;
            sectionWritten = false;
            sectionType = type;
            sectionToolCallId = toolCallId;
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

                emitAtomic(length, fin);
            }
        }

        private void emitAtomic(
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
                    writeSection(textBuffer, 0, length, true);
                }
                else
                {
                    holdSection(length);
                }
            }
        }

        private void holdSection(
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
                encodePool.buffer(encodeSlot).putBytes(0, textBuffer, 0, length);
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

            doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, false, false, sectionType, 0, sectionToolCallId,
                sectionToolName, buffer, offset, length);
        }

        private void beginSectionCapture()
        {
            final int from = position() - 1;

            captureThen = decodeContentItem;
            captureDepth = 1;
            captureFrom = from;

            closeSection();
            openSection(LlmSectionType.TOOL_CALL, toolId, toolName, from);
            sectionStarted = true;

            decoder = decodeCapture;
        }

        private void endCapture(
            DirectBufferEx buffer)
        {
            flushCapture(buffer, true);

            sectionOpen = false;
            dialectResume(position());
            decoder = captureThen;
        }

        private void flushCapture(
            DirectBufferEx buffer,
            boolean fin)
        {
            final int end = position();

            if (end > captureFrom || fin)
            {
                final int flags = sectionWritten
                    ? fin ? Flags.FIN : Flags.NONE
                    : fin ? Flags.COMPLETE : Flags.INIT;

                sectionWritten = true;

                doAppFrame(http.decodeTraceId, flags, false, false, sectionType, 0, sectionToolCallId, sectionToolName, buffer,
                    captureFrom, end - captureFrom);
            }

            captureFrom = end;
        }

        private void commitDocument()
        {
            inputTokens = documentInputTokens != NONE ? documentInputTokens : inputTokens;
            outputTokens = documentOutputTokens != NONE ? documentOutputTokens : outputTokens;
            cacheWriteTokens = documentCacheWriteTokens != NONE ? documentCacheWriteTokens : cacheWriteTokens;
            cacheReadTokens = documentCacheReadTokens != NONE ? documentCacheReadTokens : cacheReadTokens;
            errorType = documentErrorType != null ? documentErrorType : errorType;
            errorMessage = documentErrorMessage != null ? documentErrorMessage : errorMessage;
            id = documentId != null ? documentId : id;
            servedModel = documentServedModel != null ? documentServedModel : servedModel;
            finishReason = documentFinishReason != null ? documentFinishReason : finishReason;

            documentInputTokens = NONE;
            documentOutputTokens = NONE;
            documentCacheWriteTokens = NONE;
            documentCacheReadTokens = NONE;
            documentErrorType = null;
            documentErrorMessage = null;
            documentId = null;
            documentServedModel = null;
            documentFinishReason = null;
            documentType = null;
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
                    cleanupEncodeSlot();
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

        private void doAppDialectChallenge(
            long traceId)
        {
            final LlmChallengeExFW llmChallengeEx = llmChallengeExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(NAME)
                .build();

            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(llmChallengeEx.buffer(), llmChallengeEx.offset(), llmChallengeEx.sizeof())
                .build();

            app.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
            challenged = true;
        }

        private void doAppWindow(
            long traceId)
        {
            if (!replay && !challenged)
            {
                doAppWindow(traceId, 0);
                doAppDialectChallenge(traceId);
            }

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
            http.cleanupEncodeSlot();
            http.cleanupDecodeSlot();
        }
    }

    private final class LlmAnthropicHttpStream
    {
        private final HttpDataExFW httpDataExRO = new HttpDataExFW();
        private final Array32FW<LlmAttributeFW> eventAttributesRO = new Array32FW<>(new LlmAttributeFW());
        private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
        private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();
        private final MutableDirectBufferEx eventAttributesBuffer = new UnsafeBufferEx(new byte[EVENT_ATTRIBUTES_CAPACITY]);
        private int eventAttributesLength;
        private final LlmAnthropicStream stream;
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
        private boolean replayEscaped;
        private boolean messagesOpened;
        private boolean systemOpened;
        private boolean toolsOpened;
        private int messageIndex = NO_MESSAGE;
        private int messageRole = ROLE_NONE;
        private boolean toolResultError;
        private boolean textBlockOpen;
        private int toolResultContent;
        private String toolResultId;
        private int sectionKind;
        private boolean sectionOpen;
        private boolean translated;
        private boolean sectionStreaming;
        private boolean rawOpen;
        private LlmSectionPipeline sectionPipeline;
        private LlmSectionPipeline mediaDataPipeline;
        private LlmSectionPipeline mediaUrlPipeline;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline toolDefinitionPipeline;
        private LlmSectionPipeline replayPipeline;
        private final LlmSectionPipeline.Output replayOutput =
            (buffer, offset, length, last) -> putReplay(buffer, offset, length);
        private final LlmSectionPipeline.Output sectionOutput = (buffer, offset, length, last) -> putRaw(buffer, offset, length);

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

        private LlmAnthropicHttpStream(
            LlmAnthropicStream stream)
        {
            final LlmServerConfig server = stream.binding.options.server;

            this.stream = stream;
            this.originId = stream.routedId;
            this.routedId = stream.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.scheme = server.scheme;
            this.authority = server.host + ":" + server.port;
            this.requestPath = server.path + PATH_MESSAGES;
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
                .headersItem(h -> h.name(HEADER_ANTHROPIC_VERSION).value(ANTHROPIC_VERSION))
                .headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON));

            if (stream.beta != null)
            {
                httpBeginExBuilder.headersItem(h -> h.name(HEADER_ANTHROPIC_BETA).value(stream.beta));
            }

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
            translated = false;
            sectionKind = KIND_DROPPED;
        }

        private LlmSectionPipeline toolDefinitionPipeline()
        {
            if (toolDefinitionPipeline == null)
            {
                toolDefinitionPipeline = new LlmSectionPipeline(new LlmAnthropicToolDefinitionFromCanonical(),
                    transformBuffer);
            }

            return toolDefinitionPipeline;
        }

        private void putReplay(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (encodeSlotOffset + length <= encodeMax)
            {
                encodePool.buffer(encodeSlot).putBytes(encodeSlotOffset, buffer, offset, length);
                encodeSlotOffset += length;
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void doReplaySection(
            LlmSectionType type)
        {
            replayEscaped = text(sectionKind(type));
            replayPipeline = type == LlmSectionType.TOOL_DEFINITION ? toolDefinitionPipeline() : null;

            if (replayPipeline != null)
            {
                replayPipeline.begin(sectionAttributes);
            }
        }

        private void doReplayDialect()
        {
            replayEscaped = false;
            replayPipeline = null;
        }

        private void doReplayData(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            if (replayPipeline != null)
            {
                if (beginEncode(0) && !replayPipeline.feed(buffer, offset, length, fin, replayOutput))
                {
                    encodeFailed = true;
                }
            }
            else if (beginEncode(replayEscaped ? length * ESCAPE_EXPANSION : length))
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

        private static int length(
            String value)
        {
            return value != null ? value.length() : 0;
        }

        private void openToolResultContent()
        {
            if (toolResultContent == 0)
            {
                generator.writeStartArray(MEMBER_CONTENT);
                toolResultContent = TOOL_RESULT_CONTENT_OPEN;
            }
            else if (toolResultContent == TOOL_RESULT_CONTENT_CLOSED)
            {
                encodeFailed = true;
            }
        }

        private void beginTextBlock()
        {
            generator.writeStartObject().write(MEMBER_TYPE, PART_TEXT).writeKey(MEMBER_TEXT);
            generator.writeStartEscaped();
        }

        private void doEncodeSectionBegin(
            int kind,
            int message,
            String toolCallId)
        {
            sectionKind = kind;
            sectionOpen = false;
            translated = translatable(kind);

            if (kind == 0 || kind == KIND_USER_AUDIO || kind == KIND_UNKNOWN)
            {
                encodeFailed = true;
            }
            else if (beginEncode(ENCODE_STRUCTURE + (length(toolCallId) + length(sectionAttributes.mediaType()) +
                length(sectionAttributes.refId())) * ESCAPE_EXPANSION))
            {
                if (kind == KIND_CITATIONS)
                {
                    if (textBlockOpen)
                    {
                        generator.writeKey(MEMBER_CITATIONS);
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
                else
                {
                    closeTextBlock();

                    if (kind == KIND_TOOL_DEFINITION)
                    {
                        openRegion(REGION_TOOLS);
                    }
                    else if (kind == KIND_SYSTEM_INSTRUCTION)
                    {
                        openRegion(REGION_SYSTEM);
                        beginTextBlock();
                        sectionOpen = !encodeFailed;
                    }
                    else
                    {
                        beginMessageBlock(kind, message, toolCallId);
                    }
                }

                if (translated && !encodeFailed)
                {
                    beginTranslated(kind);
                }

                endEncode();
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
                    toolCallPipeline = new LlmSectionPipeline(new LlmAnthropicToolCallFromCanonical(), transformBuffer);
                }

                beginSectionStreaming(toolCallPipeline);
            }
            else
            {
                beginSectionStreaming(toolDefinitionPipeline());
            }
        }

        private void beginMedia(
            int kind)
        {
            final String source = sectionAttributes.source();
            final String mediaType = sectionAttributes.mediaType();
            final String refId = sectionAttributes.refId();
            final String part = kind == KIND_USER_IMAGE ? PART_IMAGE : PART_DOCUMENT;

            if (LlmSectionAttributes.SOURCE_INLINE.equals(source) && mediaType != null)
            {
                if (mediaDataPipeline == null)
                {
                    mediaDataPipeline = new LlmSectionPipeline(new LlmOpenaiMediaDataTransform(), transformBuffer);
                }

                beginSource(part, SOURCE_BASE64);
                generator.write(MEMBER_MEDIA_TYPE, mediaType).writeKey(MEMBER_DATA);

                beginSectionStreaming(mediaDataPipeline);
            }
            else if (kind == KIND_USER_IMAGE && LlmSectionAttributes.SOURCE_URL.equals(source))
            {
                if (mediaUrlPipeline == null)
                {
                    mediaUrlPipeline = new LlmSectionPipeline(new LlmOpenaiMediaUrlTransform(), transformBuffer);
                }

                beginSource(part, SOURCE_URL);
                generator.writeKey(MEMBER_URL);

                beginSectionStreaming(mediaUrlPipeline);
            }
            else if (kind == KIND_USER_DOCUMENT && LlmSectionAttributes.SOURCE_REFERENCE.equals(source) && refId != null)
            {
                beginSource(part, SOURCE_FILE);
                generator.write(MEMBER_FILE_ID, refId);
                generator.writeEnd().writeEnd();
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void beginSource(
            String part,
            String type)
        {
            generator.writeStartObject()
                .write(MEMBER_TYPE, part)
                .writeStartObject(MEMBER_SOURCE)
                .write(MEMBER_TYPE, type);
        }

        private void beginSectionStreaming(
            LlmSectionPipeline pipeline)
        {
            sectionPipeline = pipeline;
            sectionPipeline.begin(sectionAttributes);
            sectionStreaming = true;
        }

        private void beginMessageBlock(
            int kind,
            int message,
            String toolCallId)
        {
            openRegion(REGION_MESSAGES);

            final int role = role(kind, messageRole);

            if (message != messageIndex || role != messageRole)
            {
                closeMessage();
                openMessage(role, message);
            }

            if (kind == KIND_TOOL_RESULT)
            {
                if (toolCallId == null)
                {
                    encodeFailed = true;
                }
                else
                {
                    if (!toolCallId.equals(toolResultId))
                    {
                        closeToolResult();
                        generator.writeStartObject()
                            .write(MEMBER_TYPE, PART_TOOL_RESULT)
                            .write(MEMBER_TOOL_USE_ID, toolCallId);
                        toolResultId = toolCallId;
                        toolResultError = false;
                        toolResultContent = 0;
                    }

                    openToolResultContent();
                    beginTextBlock();
                    sectionOpen = !encodeFailed;
                }
            }
            else
            {
                closeToolResult();

                if (text(kind))
                {
                    beginTextBlock();
                    sectionOpen = !encodeFailed;
                }
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
                case REGION_MESSAGES -> messagesOpened;
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
                    case REGION_MESSAGES:
                        messagesOpened = true;
                        generator.writeStartArray(MEMBER_MESSAGES);
                        break;
                    case REGION_SYSTEM:
                        systemOpened = true;
                        generator.writeStartArray(MEMBER_SYSTEM);
                        break;
                    default:
                        toolsOpened = true;
                        generator.writeStartArray(MEMBER_TOOLS);
                        break;
                    }

                    region = next;
                }
            }
        }

        private void closeRegion()
        {
            closeTextBlock();

            if (region == REGION_MESSAGES)
            {
                closeMessage();
            }

            if (region != REGION_NONE)
            {
                generator.writeEnd();
                region = REGION_NONE;
            }
        }

        private void openMessage(
            int role,
            int message)
        {
            generator.writeStartObject()
                .write(MEMBER_ROLE, role == ROLE_ASSISTANT ? ROLE_NAME_ASSISTANT : ROLE_NAME_USER)
                .writeStartArray(MEMBER_CONTENT);

            messageIndex = message;
            messageRole = role;
        }

        private void closeMessage()
        {
            if (messageIndex != NO_MESSAGE)
            {
                closeTextBlock();
                closeToolResult();
                generator.writeEnd().writeEnd();
                messageIndex = NO_MESSAGE;
                messageRole = ROLE_NONE;
            }
        }

        private void closeToolResult()
        {
            if (toolResultId != null)
            {
                if (toolResultContent == TOOL_RESULT_CONTENT_OPEN)
                {
                    generator.writeEnd();
                }

                if (toolResultError)
                {
                    generator.write(MEMBER_IS_ERROR, true);
                }

                generator.writeEnd();

                toolResultId = null;
                toolResultError = false;
                toolResultContent = 0;
            }
        }

        private void closeTextBlock()
        {
            if (textBlockOpen)
            {
                generator.writeEnd();
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
                    if (!sectionPipeline.feed(buffer, offset, length, fin, sectionOutput))
                    {
                        encodeFailed = true;
                    }

                    endEncode();
                }
            }
            else if (!translated && sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(0))
            {
                if (text(sectionKind))
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

        private void doEncodeSectionEnd(
            boolean isError)
        {
            toolResultError |= sectionKind == KIND_TOOL_RESULT && isError;

            if (sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(ENCODE_STRUCTURE))
            {
                if (sectionStreaming)
                {
                    if (media(sectionKind))
                    {
                        generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                        rawOpen = false;
                        generator.writeEnd().writeEnd();
                    }

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
                    generator.writeEndEscaped();
                    textBlockOpen = true;
                    sectionOpen = false;
                }
                else if (sectionKind == KIND_CITATIONS)
                {
                    generator.writeEnd();
                    textBlockOpen = false;
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
                if (!stream.replay && beginEncode(ENCODE_STRUCTURE + ENCODE_MAX_TOKENS))
                {
                    closeRegion();
                    final int maxTokens = maxOutputTokens != NO_MAX_OUTPUT_TOKENS ? maxOutputTokens : defaultMaxTokens;

                    generator.write(MEMBER_MAX_TOKENS, maxTokens);
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
                final Array32FW<HttpHeaderFW> headers = eventHeadersOf(flags, extension);

                startEvent(Flags.hasInit(flags), headers);
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

        private void startEvent(
            boolean init,
            Array32FW<HttpHeaderFW> headers)
        {
            if (headers != null)
            {
                startEvent(init, headers.buffer(), headers.offset(), headers.sizeof());
            }
            else
            {
                startEvent(init, null, 0, 0);
            }
        }

        private void startEvent(
            boolean init,
            DirectBufferEx attributes,
            int offset,
            int length)
        {
            if (init)
            {
                eventAttributesLength = length <= eventAttributesBuffer.capacity() ? length : 0;

                if (eventAttributesLength > 0)
                {
                    eventAttributesBuffer.putBytes(0, attributes, offset, length);
                }
            }
        }

        private Array32FW<LlmAttributeFW> eventAttributes()
        {
            return eventAttributesLength > 0 ? eventAttributesRO.wrap(eventAttributesBuffer, 0, eventAttributesLength) : null;
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

                startEvent(Flags.hasInit(flags), attributes.buffer(), attributes.offset(), attributes.sizeof());

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
