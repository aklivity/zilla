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
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDialectBeforeBlockDataExFW;
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
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmAnthropicClientFactory implements LlmDialectHandler
{
    private static final String MODEL = "model";
    private static final String HTTP_TYPE_NAME = "http";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_SCHEME = ":scheme";
    private static final String HEADER_AUTHORITY = ":authority";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-api-key";
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String METHOD_POST = "POST";
    private static final String PATH_MESSAGES = "/messages";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ERROR_TYPE_INVALID_REQUEST = "invalid_request_error";
    private static final String MESSAGE_MODEL_MISSING = "model: Field required";
    private static final String MESSAGE_SECTION_INVALID = "Invalid request section.";
    private static final Set<String> RESPONSE_CONTENT_TYPES = Set.of(CONTENT_TYPE_JSON, CONTENT_TYPE_EVENT_STREAM);
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
    private static final int KIND_ASSISTANT_REASONING = 8;
    private static final int KIND_TOOL_DEFINITION = 9;
    private static final int KIND_TOOL_CALL = 10;
    private static final int KIND_TOOL_RESULT = 11;
    private static final int KIND_SERVER_TOOL_RESULT = 12;
    private static final int KIND_CITATIONS = 13;
    private static final int KIND_UNKNOWN = 14;
    private static final int KIND_DIALECT = 15;
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

    private static final int NONE = -1;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private static final String BLOCK_TOOL_USE = "tool_use";
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
    private static final String TYPE_CONTENT_BLOCK_STOP = "content_block_stop";
    private static final String USAGE_INPUT_TOKENS = "input_tokens";
    private static final String USAGE_CACHE_CREATION_INPUT_TOKENS = "cache_creation_input_tokens";
    private static final String USAGE_CACHE_READ_INPUT_TOKENS = "cache_read_input_tokens";
    private static final String USAGE_OUTPUT_TOKENS = "output_tokens";
    private static final int USAGE_INPUT = 0;
    private static final int USAGE_CACHE_WRITE = 1;
    private static final int USAGE_CACHE_READ = 2;
    private static final int USAGE_OUTPUT = 3;

    private static final byte[] MESSAGES_REGION = "\"messages\":[".getBytes(UTF_8);
    private static final byte[] SYSTEM_REGION = "\"system\":[".getBytes(UTF_8);
    private static final byte[] TOOLS_REGION = "\"tools\":[".getBytes(UTF_8);
    private static final byte[] MODEL_MEMBER = "\"model\":\"".getBytes(UTF_8);
    private static final byte[] MODEL_END = "\"".getBytes(UTF_8);
    private static final byte[] MEMBER_NAME_END = "\":".getBytes(UTF_8);
    private static final byte[] MESSAGE_USER_START = "{\"role\":\"user\",\"content\":[".getBytes(UTF_8);
    private static final byte[] MESSAGE_ASSISTANT_START = "{\"role\":\"assistant\",\"content\":[".getBytes(UTF_8);
    private static final byte[] TEXT_BLOCK_START = "{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] PART_TEXT_START = "{\"type\":\"text\"".getBytes(UTF_8);
    private static final byte[] TEXT_MEMBER_START = ",\"text\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_RESULT_START = "{\"type\":\"tool_result\",\"tool_use_id\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_RESULT_ID_END = "\"".getBytes(UTF_8);
    private static final byte[] TOOL_RESULT_CONTENT_START = ",\"content\":[".getBytes(UTF_8);
    private static final byte[] TOOL_RESULT_ERROR_END = ",\"is_error\":true}".getBytes(UTF_8);
    private static final byte[] IMAGE_BASE64_START =
        "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"".getBytes(UTF_8);
    private static final byte[] DOCUMENT_BASE64_START =
        "{\"type\":\"document\",\"source\":{\"type\":\"base64\",\"media_type\":\"".getBytes(UTF_8);
    private static final byte[] DATA_MEMBER = "\",\"data\":".getBytes(UTF_8);
    private static final byte[] SOURCE_END = "}}".getBytes(UTF_8);
    private static final byte[] IMAGE_URL_START =
        "{\"type\":\"image\",\"source\":{\"type\":\"url\",\"url\":".getBytes(UTF_8);
    private static final byte[] DOCUMENT_FILE_START =
        "{\"type\":\"document\",\"source\":{\"type\":\"file\",\"file_id\":\"".getBytes(UTF_8);
    private static final byte[] STRING_SOURCE_END = "\"}}".getBytes(UTF_8);
    private static final byte[] CITATIONS_MEMBER = ",\"citations\":".getBytes(UTF_8);
    private static final byte[] ARRAY_OBJECT_END = "]}".getBytes(UTF_8);

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
    private final MutableDirectBufferEx copyBuffer;
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

    public LlmAnthropicClientFactory(
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.contentTypeMatcher = CONTENT_TYPE.matcher("");
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.copyBuffer = new UnsafeBufferEx(new byte[decodeMax]);
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
                llmBeginEx.dialect().asString())::onAppMessage;
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

    private static boolean canonicalMember(
        CharSequence member)
    {
        return MEMBER_ID.contentEquals(member) ||
            MEMBER_TYPE.contentEquals(member) ||
            MEMBER_ROLE.contentEquals(member) ||
            MEMBER_MODEL.contentEquals(member) ||
            MEMBER_CONTENT.contentEquals(member) ||
            MEMBER_USAGE.contentEquals(member);
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
        private final LlmAnthropicHttpStream http;
        private final JsonParserEx parser;
        private final StringBuilder captureValue;
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

        private LlmAnthropicResponseDecoder decoder;
        private LlmAnthropicResponseDecoder skipThen;
        private LlmAnthropicResponseDecoder captureThen;
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
        private boolean textAtomic;

        private boolean sectionOpen;
        private boolean sectionStarted;
        private boolean sectionWritten;
        private boolean sectionAtomic;
        private LlmSectionType sectionType;
        private String sectionToolCallId;
        private String sectionToolName;

        private char highSurrogate;
        private byte[] textBytes;
        private UnsafeBufferEx textBuffer;
        private byte[] heldBytes;
        private UnsafeBufferEx heldBuffer;
        private int heldLength;

        private boolean captureSection;
        private boolean captureStarted;
        private boolean captureTrim;
        private int captureDepth;
        private int captureFrom;
        private String captureName;

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
            this.http = new LlmAnthropicHttpStream(this);
            this.parser = JsonEx.createParser();
            this.captureValue = new StringBuilder();
            this.textBytes = new byte[1024];
            this.textBuffer = new UnsafeBufferEx(textBytes);
            this.heldBytes = new byte[1024];
            this.heldBuffer = new UnsafeBufferEx(heldBytes);
            this.heldLength = NONE;
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
                final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);
                final LlmSectionDataExFW section = llmDataEx != null && llmDataEx.kind() == LlmDataExFW.KIND_SECTION
                    ? llmDataEx.section()
                    : null;

                if (Flags.hasInit(flags))
                {
                    if (llmDataEx != null && llmDataEx.kind() == LlmDataExFW.KIND_BLOCK)
                    {
                        http.doEncodeDialectBegin(llmDataEx.block().block().asString());
                    }
                    else if (llmDataEx != null && llmDataEx.kind() == LlmDataExFW.KIND_BEFORE)
                    {
                        final LlmDialectBeforeBlockDataExFW pre = llmDataEx.before();

                        sectionAttributes.readFrom(pre);

                        http.doEncodeBeforeBlockBegin(sectionKind(pre.type().get()), pre.message(), pre.owner().get(),
                            sectionAttributes.toolCallId(), pre.block().asString());
                    }
                    else if (llmDataEx != null && llmDataEx.kind() == LlmDataExFW.KIND_AFTER)
                    {
                        http.doEncodeAfterBlockBegin(llmDataEx.after().owner().get(), llmDataEx.after().block().asString());
                    }
                    else
                    {
                        sectionAttributes.readFrom(section);

                        final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                        http.doEncodeSectionBegin(
                            sectionKind(type),
                            section != null ? section.message() : NO_MESSAGE,
                            sectionAttributes.toolCallId());
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
                else if (fin && http.sectionStreaming)
                {
                    http.doEncodeSectionData(EMPTY_OCTETS.buffer(), 0, 0, true);
                }

                if (fin)
                {
                    http.doEncodeSectionEnd(LlmSectionAttributes.TRUE.equals(sectionAttributes.isError()));
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

                if (block != null)
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
                writeSection(heldBuffer, 0, heldLength, false);
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

                    if (TYPE_MESSAGE.equals(documentType) && !canonicalMember(member) && !MEMBER_ERROR.contentEquals(member))
                    {
                        beginCapture(member.toString(), decodeRoot);
                    }
                    else if (MEMBER_TYPE.contentEquals(member))
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
                    else if (MEMBER_ERROR.contentEquals(member))
                    {
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

        private int decodeType(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeType && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeRoot);

                if (value != null)
                {
                    documentType = value;

                    if (TYPE_CONTENT_BLOCK_STOP.equals(value) || TYPE_MESSAGE_DELTA.equals(value) ||
                        TYPE_MESSAGE_STOP.equals(value))
                    {
                        closeSection();
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
                final String value = stringValue(parser.nextEvent(), valueThen);

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
                final String value = stringValue(parser.nextEvent(), valueThen);

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
            if (parser.hasNextEvent())
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
            while (decoder == decodeMessage && parser.hasNextEvent())
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

                    if (TYPE_MESSAGE_START.equals(documentType) && !canonicalMember(member))
                    {
                        beginCapture(member.toString(), decodeMessage);
                    }
                    else if (MEMBER_ID.contentEquals(member))
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
            if (parser.hasNextEvent())
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
            while (decoder == decodeContent && parser.hasNextEvent())
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
            while (decoder == decodeContentItem && parser.hasNextEvent())
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
                        beginText(LlmSectionType.ASSISTANT_TEXT, true, decodeContentItem);
                    }
                    else if (MEMBER_THINKING.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_REASONING, true, decodeContentItem);
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
            if (parser.hasNextEvent())
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
            if (parser.hasNextEvent())
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
            while (decoder == decodeContentBlock && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    if (BLOCK_TOOL_USE.equals(blockType))
                    {
                        closeSection();
                        openSection(LlmSectionType.TOOL_CALL, toolId, toolName, false);
                        emitSection(0, false);
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
            while (decoder == decodeBlockType && parser.hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), blockThen);

                if (value != null)
                {
                    blockType = value;
                }
            }

            return position();
        }

        private int decodeBlockId(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeBlockId && parser.hasNextEvent())
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
            while (decoder == decodeBlockName && parser.hasNextEvent())
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
            while (decoder == decodeBlockText && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitText(deferred);

                    if (!deferred)
                    {
                        decoder = textThen;
                    }
                }
                else
                {
                    skipAfter(event, textThen);
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
            while (decoder == decodeDelta && parser.hasNextEvent())
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

                    if (TYPE_MESSAGE_DELTA.equals(documentType))
                    {
                        beginCapture(member.toString(), decodeDelta);
                    }
                    else if (MEMBER_TEXT.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_TEXT, false, decodeDelta);
                    }
                    else if (MEMBER_THINKING.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_REASONING, false, decodeDelta);
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
            while (decoder == decodeArguments && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();

                    emitArguments(deferred);

                    if (!deferred)
                    {
                        decoder = decodeDelta;
                    }
                }
                else
                {
                    skipAfter(event, decodeDelta);
                }
            }

            return position();
        }

        private int decodeUsageStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (parser.hasNextEvent())
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
            while (decoder == decodeUsage && parser.hasNextEvent())
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
            while (decoder == decodeUsageValue && parser.hasNextEvent())
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
            boolean atomic,
            LlmAnthropicResponseDecoder then)
        {
            textType = type;
            textAtomic = atomic;
            textThen = then;
            decoder = decodeBlockText;
        }

        private void emitText(
            boolean deferred)
        {
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();

            if (length > 0 || highSurrogate != 0 || !deferred && textAtomic && sectionOpen)
            {
                if (!sectionOpen || textType != sectionType)
                {
                    closeSection();
                    openSection(textType, null, null, textAtomic);
                }

                emitSection(encodeText(chars, !deferred), !deferred && textAtomic);
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

            if (sectionOpen && sectionType == LlmSectionType.TOOL_CALL && (length > 0 || highSurrogate != 0))
            {
                emitSection(encodeText(chars, !deferred), false);
            }

            if (deferred)
            {
                parser.consumed(length);
            }
        }

        private void openSection(
            LlmSectionType type,
            String toolCallId,
            String toolName,
            boolean atomic)
        {
            sectionOpen = true;
            sectionStarted = false;
            sectionWritten = false;
            sectionAtomic = atomic;
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

                if (sectionAtomic)
                {
                    emitAtomic(length, fin);
                }
                else
                {
                    writeSection(textBuffer, 0, length, fin);
                }
            }
        }

        private void emitAtomic(
            int length,
            boolean fin)
        {
            if (heldLength != NONE && fin && length == 0)
            {
                writeSection(heldBuffer, 0, heldLength, true);
                heldLength = NONE;
            }
            else
            {
                if (heldLength != NONE)
                {
                    writeSection(heldBuffer, 0, heldLength, false);
                    heldLength = NONE;
                }

                if (fin)
                {
                    writeSection(textBuffer, 0, length, true);
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
            int offset,
            int length,
            boolean fin)
        {
            final int flags = sectionWritten ? Flags.NONE : Flags.INIT;

            sectionWritten = true;

            doAppFrame(http.decodeTraceId, fin ? Flags.fin(flags) : flags, null, sectionType, 0, sectionToolCallId,
                sectionToolName, buffer, offset, length);
        }

        private void beginCapture(
            String name,
            LlmAnthropicResponseDecoder then)
        {
            captureSection = false;
            captureThen = then;
            captureName = name;
            captureValue.setLength(0);
            captureStarted = false;
            captureTrim = true;
            captureDepth = 0;
            captureFrom = position();

            closeSection();

            decoder = decodeCapture;
        }

        private void beginSectionCapture()
        {
            captureSection = true;
            captureThen = decodeContentItem;
            captureStarted = false;
            captureTrim = true;
            captureDepth = 1;
            captureFrom = position() - 1;

            closeSection();
            openSection(LlmSectionType.TOOL_CALL, toolId, toolName, false);
            sectionStarted = true;

            decoder = decodeCapture;
        }

        private void endCapture(
            DirectBufferEx buffer)
        {
            flushCapture(buffer, true);

            if (captureSection)
            {
                sectionOpen = false;
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

                if (captureSection)
                {
                    captureStarted = !fin;
                    sectionWritten = captureStarted || fin;

                    doAppFrame(http.decodeTraceId, flags, null, sectionType, 0, sectionToolCallId, sectionToolName, buffer,
                        from, end - from);
                }
                else
                {
                    captureStarted = !fin;

                    if (KEY_STOP_REASON.equals(captureName))
                    {
                        captureStopReason(buffer, from, end, fin);
                    }

                    doAppFrame(http.decodeTraceId, flags, captureName, null, NO_MESSAGE, null, null, buffer, from, end - from);
                }
            }

            captureFrom = end;
        }

        private void captureStopReason(
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

    private final class LlmAnthropicHttpStream
    {
        private final LlmAnthropicStream stream;
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
        private boolean encodeFailed;

        private int region = REGION_NONE;
        private boolean membersStarted;
        private boolean modelWritten;
        private boolean messagesOpened;
        private boolean systemOpened;
        private boolean toolsOpened;
        private int regionCount;
        private int messageCount;
        private int messageIndex = NO_MESSAGE;
        private int messageRole = ROLE_NONE;
        private boolean toolResultError;
        private int blockCount;
        private boolean textBlockOpen;
        private int preKind;
        private int preMessage;
        private LlmBlockOwner preOwner;
        private int toolResultContent;
        private String toolResultId;
        private int sectionKind;
        private boolean sectionOpen;
        private boolean translated;
        private boolean sectionStreaming;
        private LlmSectionPipeline sectionPipeline;
        private LlmSectionPipeline mediaDataPipeline;
        private LlmSectionPipeline mediaUrlPipeline;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline toolDefinitionPipeline;
        private final LlmSectionPipeline.Output sectionOutput = this::put;

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
            putByte('{');

            if (!NAME.equals(stream.dialect))
            {
                putModelMember();
            }
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
            translated = false;

            if (NAME.equals(stream.dialect))
            {
                sectionKind = KIND_DIALECT;
                closeTextBlock();
                closeRegion();
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

        private void doEncodeBeforeBlockBegin(
            int kind,
            int message,
            LlmBlockOwner owner,
            String toolCallId,
            String block)
        {
            sectionOpen = false;
            translated = false;

            final boolean container = owner == LlmBlockOwner.CONTAINER;

            if (!NAME.equals(stream.dialect))
            {
                sectionKind = KIND_DROPPED;
            }
            else if (preKind == 0
                ? !part(kind) || container && kind != KIND_TOOL_RESULT
                : preKind != kind || preMessage != message || container && preOwner != LlmBlockOwner.CONTAINER)
            {
                sectionKind = KIND_DROPPED;
                encodeFailed = true;
            }
            else
            {
                sectionKind = KIND_DIALECT;

                if (preKind == 0)
                {
                    closeTextBlock();

                    if (kind == KIND_SYSTEM_INSTRUCTION)
                    {
                        openRegion(REGION_SYSTEM);
                        beginRegionBlock();
                        put(PART_TEXT_START);
                    }
                    else
                    {
                        beginMessageBlock(kind, message, toolCallId, true, container);
                    }

                    preKind = kind;
                    preMessage = message;
                    preOwner = owner;
                }
                else if (!container && preOwner == LlmBlockOwner.CONTAINER)
                {
                    openToolResultContent();
                    beginMessageBlockSeparator();
                    put(PART_TEXT_START);
                    preOwner = LlmBlockOwner.SECTION;
                }

                putByte(',');
                putByte('"');
                putEscaped(block);
                put(MEMBER_NAME_END);
            }
        }

        private void doEncodeAfterBlockBegin(
            LlmBlockOwner owner,
            String block)
        {
            sectionOpen = false;
            translated = false;

            if (!NAME.equals(stream.dialect))
            {
                sectionKind = KIND_DROPPED;
            }
            else if (owner == LlmBlockOwner.CONTAINER ? toolResultId == null || toolResultContent == 0 : !textBlockOpen)
            {
                sectionKind = KIND_DROPPED;
                encodeFailed = true;
            }
            else
            {
                sectionKind = KIND_DIALECT;

                if (owner == LlmBlockOwner.CONTAINER)
                {
                    closeTextBlock();

                    if (toolResultContent == TOOL_RESULT_CONTENT_OPEN)
                    {
                        putByte(']');
                        toolResultContent = TOOL_RESULT_CONTENT_CLOSED;
                    }
                }

                putByte(',');
                putByte('"');
                putEscaped(block);
                put(MEMBER_NAME_END);
            }
        }

        private void doEncodePartContinue(
            int kind,
            int message)
        {
            if (kind == preKind && message == preMessage)
            {
                if (preOwner == LlmBlockOwner.CONTAINER)
                {
                    openToolResultContent();
                    beginMessageBlockSeparator();
                    put(TEXT_BLOCK_START);
                }
                else
                {
                    put(TEXT_MEMBER_START);
                }

                sectionOpen = !encodeFailed;
            }
            else
            {
                encodeFailed = true;
            }

            preKind = 0;
        }

        private void openToolResultContent()
        {
            if (toolResultContent == 0)
            {
                put(TOOL_RESULT_CONTENT_START);
                toolResultContent = TOOL_RESULT_CONTENT_OPEN;
                blockCount = 0;
            }
            else if (toolResultContent == TOOL_RESULT_CONTENT_CLOSED)
            {
                encodeFailed = true;
            }
        }

        private void doEncodeSectionBegin(
            int kind,
            int message,
            String toolCallId)
        {
            sectionKind = kind;
            sectionOpen = false;
            translated = !NAME.equals(stream.dialect) && translatable(kind);

            if (preKind != 0)
            {
                doEncodePartContinue(kind, message);
            }
            else if (kind == 0 || kind == KIND_USER_AUDIO || kind == KIND_UNKNOWN && !NAME.equals(stream.dialect))
            {
                encodeFailed = true;
            }
            else if (kind == KIND_CITATIONS)
            {
                if (textBlockOpen)
                {
                    put(CITATIONS_MEMBER);
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
                    beginRegionBlock();
                }
                else if (kind == KIND_SYSTEM_INSTRUCTION)
                {
                    openRegion(REGION_SYSTEM);
                    beginRegionBlock();
                    put(TEXT_BLOCK_START);
                    sectionOpen = !encodeFailed;
                }
                else
                {
                    beginMessageBlock(kind, message, toolCallId, false, false);
                }
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
                    toolCallPipeline = new LlmSectionPipeline(new LlmOpenaiToolCallTransform(), transformBuffer);
                }

                beginSectionStreaming(toolCallPipeline);
            }
            else
            {
                if (toolDefinitionPipeline == null)
                {
                    toolDefinitionPipeline = new LlmSectionPipeline(new LlmOpenaiToolDefinitionTransform(), transformBuffer);
                }

                beginSectionStreaming(toolDefinitionPipeline);
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
                    mediaDataPipeline = new LlmSectionPipeline(new LlmOpenaiMediaDataTransform(), transformBuffer);
                }

                put(kind == KIND_USER_IMAGE ? IMAGE_BASE64_START : DOCUMENT_BASE64_START);
                putEscaped(mediaType);
                put(DATA_MEMBER);

                beginSectionStreaming(mediaDataPipeline);
            }
            else if (kind == KIND_USER_IMAGE && LlmSectionAttributes.SOURCE_URL.equals(source))
            {
                if (mediaUrlPipeline == null)
                {
                    mediaUrlPipeline = new LlmSectionPipeline(new LlmOpenaiMediaUrlTransform(), transformBuffer);
                }

                put(IMAGE_URL_START);

                beginSectionStreaming(mediaUrlPipeline);
            }
            else if (kind == KIND_USER_DOCUMENT && LlmSectionAttributes.SOURCE_REFERENCE.equals(source) && refId != null)
            {
                put(DOCUMENT_FILE_START);
                putEscaped(refId);
                put(STRING_SOURCE_END);
            }
            else
            {
                encodeFailed = true;
            }
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
            String toolCallId,
            boolean pre,
            boolean container)
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
                        beginMessageBlockSeparator();
                        put(TOOL_RESULT_START);
                        putEscaped(toolCallId);
                        put(TOOL_RESULT_ID_END);
                        toolResultId = toolCallId;
                        toolResultError = false;
                        toolResultContent = 0;
                    }

                    if (container)
                    {
                        encodeFailed |= toolResultContent != 0;
                    }
                    else
                    {
                        openToolResultContent();
                        beginMessageBlockSeparator();
                        put(pre ? PART_TEXT_START : TEXT_BLOCK_START);
                        sectionOpen = !pre && !encodeFailed;
                    }
                }
            }
            else
            {
                closeToolResult();
                beginMessageBlockSeparator();

                if (text(kind))
                {
                    put(pre ? PART_TEXT_START : TEXT_BLOCK_START);
                    sectionOpen = !pre && !encodeFailed;
                }
            }
        }

        private void beginMessageBlockSeparator()
        {
            if (blockCount++ > 0)
            {
                putByte(',');
            }
        }

        private void beginRegionBlock()
        {
            if (regionCount++ > 0)
            {
                putByte(',');
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
                        startMember();
                        put(MESSAGES_REGION);
                        messageCount = 0;
                        break;
                    case REGION_SYSTEM:
                        systemOpened = true;
                        startMember();
                        put(SYSTEM_REGION);
                        break;
                    default:
                        toolsOpened = true;
                        startMember();
                        put(TOOLS_REGION);
                        break;
                    }

                    region = next;
                    regionCount = 0;
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
                putByte(']');
                region = REGION_NONE;
            }
        }

        private void openMessage(
            int role,
            int message)
        {
            if (messageCount++ > 0)
            {
                putByte(',');
            }

            put(role == ROLE_ASSISTANT ? MESSAGE_ASSISTANT_START : MESSAGE_USER_START);

            messageIndex = message;
            messageRole = role;
            blockCount = 0;
        }

        private void closeMessage()
        {
            if (messageIndex != NO_MESSAGE)
            {
                closeTextBlock();
                closeToolResult();
                put(ARRAY_OBJECT_END);
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
                    putByte(']');
                }

                if (toolResultError)
                {
                    put(TOOL_RESULT_ERROR_END);
                }
                else
                {
                    putByte('}');
                }

                toolResultId = null;
                toolResultError = false;
                toolResultContent = 0;
                blockCount = 1;
            }
        }

        private void closeTextBlock()
        {
            if (textBlockOpen)
            {
                putByte('}');
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
                if (!sectionPipeline.feed(buffer, offset, length, fin, sectionOutput))
                {
                    encodeFailed = true;
                }
            }
            else if (!translated && sectionKind != 0 && sectionKind != KIND_DROPPED)
            {
                if (text(sectionKind))
                {
                    putEscaped(buffer, offset, length);
                }
                else
                {
                    put(buffer, offset, length);
                }
            }
        }

        private void doEncodeSectionEnd(
            boolean isError)
        {
            toolResultError |= sectionKind == KIND_TOOL_RESULT && isError;

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
                putByte('"');

                textBlockOpen = true;

                sectionOpen = false;
            }
            else if (sectionKind == KIND_CITATIONS)
            {
                putByte('}');
                textBlockOpen = false;
            }

            sectionKind = 0;
        }

        private void doEncodeRequestEnd()
        {
            if (preKind != 0)
            {
                encodeFailed = true;
            }
            else if (!LlmState.initialClosing(state) && !LlmState.initialClosed(state))
            {
                closeRegion();

                if (!modelWritten)
                {
                    putModelMember();
                }

                putByte('}');
                state = LlmState.closingInitial(state);
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

        private void forwardResponseSections(
            DirectBuffer buffer,
            int offset,
            int length,
            boolean last)
        {
            if (length > 0 || last)
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
