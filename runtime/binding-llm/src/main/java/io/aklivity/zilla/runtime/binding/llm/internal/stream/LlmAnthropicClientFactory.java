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

import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonException;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmMember;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
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
import io.aklivity.zilla.runtime.common.json.view.JsonStringBody;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;
import io.aklivity.zilla.runtime.common.lang.SegmentTransferTarget;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebit;
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
    private static final String VENDOR_PREFIX = NAME + ":";
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
    private static final int EVENT_FRAMES = 3;
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
    private static final int KIND_CONTROL = 14;
    private static final int KIND_SERVER_TOOL_CALL = 15;
    private static final int KIND_DROPPED = 16;
    private static final int KIND_SERVER_TOOL_DEFINITION = 17;

    private static final int ROLE_NONE = 0;
    private static final int ROLE_USER = 1;
    private static final int ROLE_ASSISTANT = 2;

    private static final int REGION_MESSAGES = 1 << 0;
    private static final int REGION_SYSTEM = 1 << 2;
    private static final int REGION_TOOLS = 1 << 4;
    private static final int REGION_BLOCK_OPENED = 1 << 6;
    private static final int REGION_RESULT_CONTENT_OPENED = 1 << 7;

    private static final int NO_MESSAGE = -2;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_STRUCTURE = 128;
    private static final int ENCODE_BREAKPOINT = 64;
    private static final int BREAKPOINTS_MAX = 4;
    private static final String[] BREAKPOINT_TTLS = {"5m", "1h"};
    private static final int ENCODE_MAX_TOKENS = 32;
    private static final int ENCODE_TOOL_CHOICE = 96;
    private static final int ENCODE_OUTPUT_CONFIG = 64;
    private static final int ENCODE_THINKING = 80;
    private static final int ENCODE_METADATA = 32;
    private static final int ENCODE_CLOSING = ENCODE_STRUCTURE + ENCODE_BREAKPOINT + ENCODE_MAX_TOKENS + ENCODE_TOOL_CHOICE +
        ENCODE_OUTPUT_CONFIG + ENCODE_THINKING + ENCODE_METADATA;
    private static final int NO_MAX_OUTPUT_TOKENS = -1;

    private static final int NONE = -1;
    private static final int ROOT_CONTROL = -1;
    private static final int CONTROL_CLOSING = 1;
    private static final int CONTROL_HEADER = 2 * Integer.BYTES;
    private static final int PIECE_CAPACITY = 1024;
    private static final DirectBufferEx CONTROL_TERMINATOR = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final int REASONING_ESCAPE_EXPANSION = 6;
    private static final int REASONING_FRAMING = 48;
    private static final byte[] ARGUMENTS_PREFIX = "{\"arguments\":".getBytes(UTF_8);
    private static final byte[] ARGUMENTS_EMPTY = "{}".getBytes(UTF_8);
    private static final byte[] ARGUMENTS_SUFFIX = "}".getBytes(UTF_8);
    private static final byte[] SECTION_SUFFIX = "\"}".getBytes(UTF_8);

    private static final String BLOCK_TOOL_USE = "tool_use";
    private static final String BLOCK_SERVER_TOOL_USE = "server_tool_use";
    private static final String BLOCK_OTHER = "other";
    private static final String BLOCK_TEXT = "text";
    private static final String BLOCK_THINKING = "thinking";
    private static final String BLOCK_REDACTED_THINKING = "redacted_thinking";
    private static final String KEY_STOP_REASON = "stop_reason";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_MODEL = "model";
    private static final String MEMBER_ROLE = "role";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_INDEX = "index";
    private static final String MEMBER_CACHE_CONTROL = "cache_control";
    private static final String MEMBER_TTL = "ttl";
    private static final String CACHE_CONTROL_EPHEMERAL = "ephemeral";
    private static final String MEMBER_STOP_SEQUENCE = "stop_sequence";
    private static final String MEMBER_MESSAGE = "message";
    private static final String MEMBER_CONTENT = "content";
    private static final String MEMBER_CONTENT_BLOCK = "content_block";
    private static final String MEMBER_DELTA = "delta";
    private static final String MEMBER_USAGE = "usage";
    private static final byte[] USAGE_PREFIX = ("{\"" + NAME + ':' + MEMBER_USAGE + "\":").getBytes(UTF_8);
    private static final String MEMBER_ERROR = "error";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_THINKING = "thinking";
    private static final String MEMBER_SIGNATURE = "signature";
    private static final String MEMBER_DATA = "data";
    private static final String REASONING_TEXT = "text";
    private static final String REASONING_SIGNATURE = "x:signature";
    private static final String REASONING_DATA = "x:redacted_data";
    private static final int OBJECT_OPENING = "{\"\":\"".length();
    private static final int SECTION_OPENING = Math.max(ARGUMENTS_PREFIX.length, OBJECT_OPENING +
        Math.max(Math.max(MEMBER_TEXT.length(), REASONING_TEXT.length()),
            Math.max(REASONING_SIGNATURE.length(), REASONING_DATA.length())));
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
    private static final String MEMBER_TOOL_CHOICE = "tool_choice";
    private static final String MEMBER_OUTPUT_CONFIG = "output_config";
    private static final String MEMBER_METADATA = "metadata";
    private static final String MEMBER_EFFORT = "effort";
    private static final String MEMBER_BUDGET_TOKENS = "budget_tokens";
    private static final String MEMBER_TYPE_CHOICE = "type";
    private static final String MEMBER_DISABLE_PARALLEL = "disable_parallel_tool_use";
    private static final String CHOICE_TYPE_AUTO = "auto";
    private static final String CHOICE_TYPE_NONE = "none";
    private static final String[] CONTAINERS = {MEMBER_TOOL_CHOICE, MEMBER_OUTPUT_CONFIG, MEMBER_THINKING, MEMBER_METADATA};
    private static final int CONTAINER_TOOL_CHOICE = 0;
    private static final int CONTAINER_OUTPUT_CONFIG = 1;
    private static final int CONTAINER_THINKING = 2;
    private static final int CONTAINER_METADATA = 3;
    private static final int NO_CONTAINER = -1;
    private static final int RECORD_LENGTH_OFFSET = Byte.BYTES;
    private static final int RECORD_HEADER = Byte.BYTES + Integer.BYTES;
    private static final String MEMBER_TOOLS = "tools";
    private static final String MEMBER_TOOL_USE_ID = "tool_use_id";
    private static final String MEMBER_IS_ERROR = "is_error";
    private static final String MEMBER_CITATIONS = "citations";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final String MEMBER_FUNCTION = "function";
    private static final String MEMBER_PARAMETERS = "parameters";
    private static final String MEMBER_DESCRIPTION = "description";
    private static final String MEMBER_INPUT_SCHEMA = "input_schema";
    private static final String TYPE_OBJECT = "object";
    private static final int PARALLEL_UNSET = -1;
    private static final int PARALLEL_DISABLED = 0;
    private static final int PARALLEL_ENABLED = 1;
    private static final int SEEN_INPUT = 1 << 0;
    private static final int SEEN_NAME = 1 << 1;
    private static final int SEEN_SCHEMA = 1 << 2;
    private static final int SEEN_TYPED = 1 << 3;
    private static final int SEEN_URL = 1 << 4;
    private static final int SEEN_OTHER = 1 << 5;
    private static final int SEEN_OPENED = 1 << 6;
    private static final int SEEN_ENABLED = 1 << 7;
    private static final int SEEN_ENABLED_KEY = 1 << 8;
    private static final int SEEN_CACHED = 1 << 9;
    private static final String MEMBER_SOURCE = "source";
    private static final String MEMBER_MEDIA_TYPE = "media_type";
    private static final String MEMBER_FILE_ID = "file_id";
    private static final String MEMBER_FILE_DATA = "file_data";
    private static final String MEMBER_FILE_URL = "file_url";
    private static final String MEMBER_IMAGE_URL = "image_url";
    private static final String MEMBER_FILE = "file";
    private static final String MEMBER_FILENAME = "filename";
    private static final String MEMBER_URL = "url";
    private static final String MEMBER_ENABLED = "enabled";
    private static final String MEMBER_TITLE = "title";
    private static final String MEMBER_REDACTED_DATA = "data";
    private static final String SOURCE_BASE64 = "base64";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_STOP_SEQUENCES = "stop_sequences";
    private static final String MEMBER_USER_ID = "user_id";
    private static final String MEMBER_SERVICE_TIER = "service_tier";
    private static final String MEMBER_FORMAT = "format";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String MEMBER_SCHEMA = "schema";
    private static final String TYPE_JSON_SCHEMA = "json_schema";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_TEXT = "text";
    private static final String CHOICE_TYPE_ANY = "any";
    private static final String CHOICE_TYPE_TOOL = "tool";
    private static final double TEMPERATURE_MINIMUM = 0.0;
    private static final double TEMPERATURE_MAXIMUM = 1.0;
    private static final int USER_ID_MAX = 256;
    private static final int CONTROL_HEADROOM = 16;
    private static final int CAPTURED_MAX = 4096;
    private static final int FORMAT_OFF = 0;
    private static final int FORMAT_UNSET = 1;
    private static final int FORMAT_SCHEMA = 2;
    private static final int FORMAT_OMITTED = 3;
    private static final int FORMAT_OBJECT = 4;
    private static final int FORMAT_TEXT = 5;
    private static final String SOURCE_FILE = "file";
    private static final String BLOCK_IMAGE = "image";
    private static final String BLOCK_DOCUMENT = "document";
    private static final String TYPE_URL_CITATION = "url_citation";
    private static final String TYPE_WEB_SEARCH_LOCATION = "web_search_result_location";
    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final String PART_TEXT = "text";
    private static final String PART_TOOL_RESULT = "tool_result";
    private static final String ROLE_NAME_USER = "user";
    private static final String ROLE_NAME_ASSISTANT = "assistant";

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final Map<String, StringEventPositions> PARSER_CONFIG =
        Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpDataExFW httpDataExRO = new HttpDataExFW();
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
    private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx containerBuffer;
    private final MutableDirectBufferEx recordHeader = new UnsafeBufferEx(new byte[RECORD_HEADER]);
    private final Matcher contentTypeMatcher;
    private final DirectBufferEx emptyBufferRO;
    private final LongUnaryOperator supplyInitialId;
    private final LlmAnthropicOutputSchema outputSchema;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BufferPool holdPool;
    private final SegmentTransferTarget pieceTarget = new SegmentTransferTarget();
    private final JsonStringBody body = new JsonStringBody();
    private final JsonStringView.Decoded emptyChars = new JsonStringView.Decoded().wrap("");
    private final JsonGeneratorEx controlGenerator = JsonEx.createGenerator();
    private final LlmAnthropicThinking thinking = new LlmAnthropicThinking();

    private byte[] pieceBytes;
    private UnsafeBufferEx pieceBuffer;
    private byte[] controlBytes;
    private UnsafeBufferEx controlBuffer;
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
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.containerBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.contentTypeMatcher = CONTENT_TYPE.matcher("");
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.holdPool = context.bufferPool().duplicate();
        this.pieceBytes = new byte[PIECE_CAPACITY];
        this.pieceBuffer = new UnsafeBufferEx(pieceBytes);
        this.controlBytes = new byte[CONTROL_HEADER];
        this.controlBuffer = new UnsafeBufferEx(controlBytes);
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.emptyBufferRO = new UnsafeBufferEx(new byte[0]);
        this.supplyInitialId = context::supplyInitialId;
        this.outputSchema = new LlmAnthropicOutputSchema();
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
    private interface LlmAnthropicRequestDecoder
    {
        int decode(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last);
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
        case SERVER_TOOL_DEFINITION -> KIND_SERVER_TOOL_DEFINITION;
        case SERVER_TOOL_CALL -> KIND_SERVER_TOOL_CALL;
        case SERVER_TOOL_RESULT -> KIND_SERVER_TOOL_RESULT;
        case CITATIONS -> KIND_CITATIONS;
        case CONTROL -> KIND_CONTROL;
        default -> 0;
        };
    }

    private static int role(
        int kind)
    {
        return switch (kind)
        {
        case KIND_ASSISTANT_TEXT, KIND_ASSISTANT_REFUSAL, KIND_ASSISTANT_REASONING, KIND_TOOL_CALL,
            KIND_SERVER_TOOL_CALL, KIND_SERVER_TOOL_RESULT -> ROLE_ASSISTANT;
        default -> ROLE_USER;
        };
    }

    private static boolean parsed(
        int kind)
    {
        return kind == KIND_TOOL_CALL ||
            kind == KIND_SERVER_TOOL_CALL ||
            kind == KIND_TOOL_DEFINITION ||
            kind == KIND_USER_IMAGE ||
            kind == KIND_USER_DOCUMENT ||
            kind == KIND_ASSISTANT_REASONING ||
            kind == KIND_CITATIONS ||
            kind == KIND_CONTROL;
    }

    private static boolean breakpointed(
        int kind)
    {
        return kind == KIND_TOOL_CALL ||
            kind == KIND_SERVER_TOOL_CALL ||
            kind == KIND_TOOL_DEFINITION ||
            kind == KIND_USER_IMAGE ||
            kind == KIND_USER_DOCUMENT;
    }

    private static boolean sharing(
        int kind)
    {
        return kind == KIND_ASSISTANT_REASONING || kind == KIND_USER_DOCUMENT;
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

    private static boolean objectShaped(
        LlmSectionType type)
    {
        return type == LlmSectionType.ASSISTANT_REASONING || type == LlmSectionType.SYSTEM_INSTRUCTION ||
            type == LlmSectionType.USER_TEXT || type == LlmSectionType.ASSISTANT_TEXT ||
            type == LlmSectionType.ASSISTANT_REFUSAL || type == LlmSectionType.TOOL_RESULT;
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
        private final LlmAnthropicResponseDecoder decodeControlValue = this::decodeControlValue;
        private final LlmAnthropicResponseDecoder decodeControlStream = this::decodeControlStream;
        private final LlmAnthropicResponseDecoder decodeSkip = this::decodeSkip;
        private final LlmAnthropicResponseDecoder decodeEnd = this::decodeEnd;
        private final LlmAnthropicResponseDecoder decodeIgnore = this::decodeIgnore;
        private final LlmAnthropicResponseDecoder decodeRejected = this::decodeRejected;
        private final LlmAnthropicResponseDecoder decodeLookahead = this::decodeLookahead;
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
        private final boolean crossDialect;
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
        private boolean decodedLast;
        private int decodeWindowLimit = NONE;
        private int skipDepth;

        private boolean scanError;
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
        private String reasoningMember;
        private String sectionMember;

        private boolean sectionOpen;
        private boolean sectionStarted;
        private boolean sectionWritten;
        private LlmSectionType sectionType;
        private String sectionToolCallId;
        private int argumentsLength;
        private String sectionToolName;

        private DirectBufferEx decodedInput;
        private final StringBuilder controlKey = new StringBuilder();
        private final LlmControlSeen controlSeen = new LlmControlSeen();
        private int controlSlot = NO_SLOT;
        private int controlSlotOffset;
        private int controlEntry;
        private int controlCount;
        private int controlFrom = NONE;
        private int controlMessage;
        private int controlDepth;
        private LlmAnthropicResponseDecoder controlThen;
        private int usageSlot = NO_SLOT;
        private String knownToolId;
        private String knownToolName;

        private int captureDepth;
        private int captureFrom;
        private int blockFrom;

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
            this.crossDialect = !NAME.equals(dialect);
            this.beta = crossDialect ? null : beta;
            this.http = new LlmAnthropicHttpStream(this);
            this.parser = JsonEx.createParser(PARSER_CONFIG);
            this.decoder = decodeLookahead;
        }

        private int replyCredit()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - replyPad);
        }

        private int replyWindow()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - controlSlotOffset - controlClosing() -
                eventReserve() - sectionOpening() - sectionClosing());
        }

        private int sectionOpening()
        {
            return sectionOpen ? 0 : SECTION_OPENING;
        }

        private int sectionClosing()
        {
            final int closing;

            if (!sectionOpen || !http.framing)
            {
                closing = 0;
            }
            else if (sectionType == LlmSectionType.TOOL_CALL)
            {
                closing = ARGUMENTS_EMPTY.length + ARGUMENTS_SUFFIX.length + replyPad;
            }
            else
            {
                closing = objectShaped(sectionType) ? SECTION_SUFFIX.length + replyPad : 0;
            }

            return closing;
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
            state = StreamState.openingInitial(state);

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
                else if (fin && http.requestDecoder != null)
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
                    cleanupSlots();
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
            state = StreamState.closingInitial(state);

            http.doEncodeRequestEnd(NO_MAX_OUTPUT_TOKENS);

            if (http.encodeFailed)
            {
                doAppReset(traceId, STATUS_BAD_REQUEST, ERROR_TYPE_INVALID_REQUEST, MESSAGE_SECTION_INVALID);
                http.doNetAbort(traceId);
                cleanupSlots();
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
            state = StreamState.closeInitial(state);

            http.doNetAbort(traceId);
            cleanupControlSlot();
            cleanupUsageSlot();
            http.cleanupEncodeSlot();
            http.cleanupHeldSlot();
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();

            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();
            state = StreamState.openReply(state);

            http.decodeNet(traceId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = StreamState.closeReply(state);

            http.doNetReset(traceId);
            http.doNetAbort(traceId);
            cleanupControlSlot();
            cleanupUsageSlot();
            http.cleanupEncodeSlot();
            http.cleanupHeldSlot();
            http.cleanupDecodeSlot();
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            http.doNetChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private String rejection()
        {
            final String member = http.rejectedMember();
            final String message;

            if (member != null)
            {
                message = MESSAGE_SECTION_CONVERT.formatted(member, dialect, NAME);
            }
            else if (requestSection == null || requestSection == LlmSectionType.NONE)
            {
                message = MESSAGE_SECTION_INVALID;
            }
            else
            {
                message = MESSAGE_SECTION_CONVERT.formatted(requestSection.name().toLowerCase().replace('_', '-'), dialect, NAME);
            }

            return message;
        }

        private void flushAppWindow(
            long traceId)
        {
            final int room = encodeMax - http.encodeSlotOffset - ENCODE_CLOSING - http.heldReserve();
            final int credit = Math.max(0, room / ESCAPE_EXPANSION);
            final long target = initialSeq + credit;

            if (http.initialMax > 0 &&
                !StreamState.initialClosing(state) &&
                !StreamState.initialClosed(state) &&
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
            if (!StreamState.replyOpening(state))
            {
                doAppBegin(traceId, servedModel(), id());
            }
        }

        private void doAppBegin(
            long traceId,
            String servedModel,
            String servedId)
        {
            state = StreamState.openingReply(state);

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

            state = StreamState.openReply(state);
            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(llmBeginEx.buffer(), llmBeginEx.offset(), llmBeginEx.sizeof())
                .build();

            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
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
                .state(state)
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
            return decoder != decodeLookahead && http.errorStatus == NO_ERROR;
        }

        private boolean rejected()
        {
            return decoder == decodeRejected;
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
                case "end_turn" -> "stop";
                case "max_tokens" -> "length";
                case "tool_use" -> "tool-call";
                case "refusal" -> "content-filter";
                default -> VENDOR_PREFIX + reported;
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
            draining = false;
            sectionOpen = false;
            sectionStarted = false;
            cleanupControlSlot();
            knownToolId = null;
            knownToolName = null;
            decoder = decodeLookahead;
        }

        private void beginDrain()
        {
            draining = true;
            cancelControl();
            decoder = decoder == decodeStart || decoder == decodeLookahead ? decodeDrainStart : decodeIgnore;
        }

        private int decodeNet(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            int progress = offset;

            decodeWindowLimit = NONE;

            if (controlFrom != NONE)
            {
                controlFrom = offset;
            }

            if (decoder == decodeCapture)
            {
                captureFrom = offset;
            }

            int previous;
            LlmAnthropicResponseDecoder previousDecoder;
            do
            {
                previous = progress;
                previousDecoder = decoder;
                progress = decodeInput(buffer, progress, limit, last);
            }
            while (progress != previous && progress < limit || decoder != previousDecoder);

            if (decoder == decodeCapture)
            {
                flushCapture(buffer, false);
            }

            flushControl(progress);

            return progress;
        }

        private int decodeInput(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            int progress = offset;

            decodedInput = buffer;
            decodedLast = last;

            final LlmAnthropicResponseDecoder previous = decoder;
            final boolean unconstrained = draining || http.errorStatus != NO_ERROR;
            final boolean credited = !unconstrained && decoder != decodeLookahead;

            try
            {
                if (decoder == decodeEnd || decoder == decodeIgnore || decoder == decodeRejected)
                {
                    decodeLimit = limit;
                    progress = decoder.decode(buffer, offset, limit);
                }
                else
                {
                    int windowLimit = limit;

                    if (credited)
                    {
                        if (decodeWindowLimit == NONE || decodeWindowLimit <= offset)
                        {
                            int window = Math.min(limit - offset, replyWindow());

                            if (window == 0 && limit > offset && decoder == decodeControlValue && controlSlot != NO_SLOT &&
                                replyCredit() > 0)
                            {
                                beginControlStream();
                                window = Math.min(limit - offset, replyWindow());
                            }

                            decodeWindowLimit = offset + window;
                        }

                        windowLimit = decodeWindowLimit;
                    }

                    if (windowLimit > offset || limit == offset)
                    {
                        decodeLimit = windowLimit;
                        parser.wrap(buffer, offset, windowLimit, last && windowLimit == limit);

                        progress = decoder.decode(buffer, offset, windowLimit);

                        if (decoder == decodeEnd)
                        {
                            while (progress < windowLimit && isWhitespace(buffer.getByte(progress)))
                            {
                                progress++;
                            }
                        }
                        else if (decoder == previous)
                        {
                            if (decoder == decodeCapture)
                            {
                                flushCapture(buffer, false);
                            }

                            flushControl(progress);
                        }

                        decodeWindowLimit = NONE;

                        if (decoder == previous && unconstrained)
                        {
                            progress = limit;
                        }
                    }
                }
            }
            catch (JsonException ex)
            {
                decoder = unconstrained ? decodeIgnore : decodeRejected;
                progress = limit;
            }

            return decoder == decodeRejected ? limit : progress;
        }

        private int decodeLookahead(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            parser.mark();

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
                    scanKey = parser.getString();
                    found = scanDepth == 1 && MEMBER_CONTENT.equals(scanKey);
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

            parser.rewind();

            if (found || decodedLast || limit - offset >= decodeMax)
            {
                decoder = decodeStart;

                if (!scanError)
                {
                    doAppBeginSections(http.decodeTraceId);
                }
            }

            return position();
        }

        private int position()
        {
            return decodeLimit - parser.remaining();
        }

        private boolean hasNextEvent()
        {
            return (!begun() || draining || eventSlack() >= eventReserve()) && parser.hasNextEvent();
        }

        private int eventSlack()
        {
            return replyMax - (int) (replySeq - replyAck) - controlSlotOffset - controlPending() -
                parser.remaining() - sectionClosing();
        }

        private int eventReserve()
        {
            return (EVENT_FRAMES + controlCount) * replyPad;
        }

        private int controlClosing()
        {
            return controlFrom != NONE ? CONTROL_CLOSING : 0;
        }

        private int controlPending()
        {
            return controlFrom != NONE ? position() - controlFrom + CONTROL_CLOSING : 0;
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
                    decoder = decodeRejected;
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
                        beginUsage();
                    }
                    else if (KEY_STOP_REASON.contentEquals(member))
                    {
                        valueThen = decodeRoot;
                        decoder = decodeStopReason;
                    }
                    else if (MEMBER_ERROR.contentEquals(member))
                    {
                        errorThen = decodeRoot;
                        decoder = decodeErrorObjectStart;
                    }
                    else if (MEMBER_ROLE.contentEquals(member) || MEMBER_INDEX.contentEquals(member) || !controlRoot())
                    {
                        skipMember(decodeRoot);
                    }
                    else
                    {
                        beginControl(member, ROOT_CONTROL, decodeRoot);
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

                    if (TYPE_CONTENT_BLOCK_STOP.equals(documentType))
                    {
                        blockType = null;
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
                        beginUsage();
                    }
                    else if (derivedMember(member) || !TYPE_MESSAGE_START.equals(documentType))
                    {
                        skipMember(decodeMessage);
                    }
                    else
                    {
                        beginControl(member, ROOT_CONTROL, decodeMessage);
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
                    closeSection();
                    decoder = decodeRoot;
                }
                else if (event == JsonEvent.START_OBJECT)
                {
                    if (sectionOpen && sectionType == LlmSectionType.ASSISTANT_REASONING)
                    {
                        closeSection();
                    }

                    blockType = null;
                    toolId = null;
                    toolName = null;
                    blockFrom = position() - 1;
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
                        beginReasoning(REASONING_TEXT, decodeContentItem);
                    }
                    else if (MEMBER_SIGNATURE.contentEquals(member))
                    {
                        beginReasoning(REASONING_SIGNATURE, decodeContentItem);
                    }
                    else if (MEMBER_DATA.contentEquals(member) &&
                        (blockType == null || BLOCK_REDACTED_THINKING.equals(blockType)))
                    {
                        beginReasoning(REASONING_DATA, decodeContentItem);
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

                        beginToolCall();
                    }

                    decoder = decodeRoot;
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
                    else if (MEMBER_DATA.contentEquals(member) && BLOCK_REDACTED_THINKING.equals(blockType))
                    {
                        beginReasoning(REASONING_DATA, decodeContentBlock);
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
                    final CharSequence type = parser.getStringView();

                    blockType = BLOCK_TOOL_USE.contentEquals(type) ? BLOCK_TOOL_USE
                        : BLOCK_REDACTED_THINKING.contentEquals(type) ? BLOCK_REDACTED_THINKING
                        : BLOCK_TEXT.contentEquals(type) ? BLOCK_TEXT
                        : BLOCK_THINKING.contentEquals(type) ? BLOCK_THINKING : BLOCK_OTHER;

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
                        beginReasoning(REASONING_TEXT, decodeDelta);
                    }
                    else if (MEMBER_SIGNATURE.contentEquals(member))
                    {
                        beginReasoning(REASONING_SIGNATURE, decodeDelta);
                    }
                    else if (MEMBER_PARTIAL_JSON.contentEquals(member))
                    {
                        decoder = decodeArguments;
                    }
                    else if (MEMBER_TYPE.contentEquals(member) || !TYPE_MESSAGE_DELTA.equals(documentType))
                    {
                        skipMember(decodeDelta);
                    }
                    else
                    {
                        beginControl(member, ROOT_CONTROL, decodeDelta);
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
                    if (endUsage())
                    {
                        decoder = usageThen;
                    }
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
                    parser.consumed(parser.getStringView().length());
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
                    parser.consumed(parser.getStringView().length());
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
                    decoder = decodeRejected;
                }
            }

            return limit;
        }

        private int decodeRejected(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            return limit;
        }

        private int decodeIgnore(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
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
            return event == JsonEvent.VALUE_STRING && parser.deferredBytes();
        }

        private void beginText(
            LlmSectionType type,
            LlmAnthropicResponseDecoder then)
        {
            textType = type;
            textThen = then;
            decoder = decodeBlockText;
        }

        private void beginReasoning(
            String member,
            LlmAnthropicResponseDecoder then)
        {
            reasoningMember = member;
            beginText(LlmSectionType.ASSISTANT_REASONING, then);
        }

        private void emitText(
            boolean deferred,
            int eventFrom)
        {
            final JsonStringView chars = parser.getStringView();
            final int length = chars.length();

            emitString(chars, length, deferred, eventFrom, textType, null, null);
        }

        private void emitArguments(
            boolean deferred,
            int eventFrom)
        {
            final JsonStringView chars = parser.getStringView();
            final int length = chars.length();

            if (!sectionOpen || sectionType != LlmSectionType.TOOL_CALL)
            {
                closeSection();
                openSection(LlmSectionType.TOOL_CALL, knownToolId, knownToolName);
                argumentsLength = 0;
            }

            if (length > 0 || !deferred)
            {
                final int prefix = sectionStarted ? 0 : ARGUMENTS_PREFIX.length;
                final int encoded = encodeText(chars, !deferred);

                argumentsLength += encoded - prefix;
                emitArgumentsPiece(encoded);
            }

            if (deferred)
            {
                parser.consumed(length);
            }
        }

        private void beginToolCall()
        {
            closeSection();
            openSection(LlmSectionType.TOOL_CALL, knownToolId, knownToolName);
            argumentsLength = 0;

            final int encoded = encodeText(emptyChars, true);

            emitArgumentsPiece(encoded);
        }

        private void emitArgumentsPiece(
            int length)
        {
            sectionStarted = true;

            writeSection(pieceBuffer, 0, length, false);
        }

        private int putArgumentsEnd()
        {
            final int capacity = ARGUMENTS_PREFIX.length + ARGUMENTS_EMPTY.length + ARGUMENTS_SUFFIX.length;

            reservePiece(capacity);

            int position = 0;

            if (!sectionStarted)
            {
                System.arraycopy(ARGUMENTS_PREFIX, 0, pieceBytes, position, ARGUMENTS_PREFIX.length);
                position += ARGUMENTS_PREFIX.length;
            }

            if (argumentsLength == 0)
            {
                System.arraycopy(ARGUMENTS_EMPTY, 0, pieceBytes, position, ARGUMENTS_EMPTY.length);
                position += ARGUMENTS_EMPTY.length;
            }

            System.arraycopy(ARGUMENTS_SUFFIX, 0, pieceBytes, position, ARGUMENTS_SUFFIX.length);

            return position + ARGUMENTS_SUFFIX.length;
        }

        private void emitString(
            JsonStringView chars,
            int length,
            boolean deferred,
            int eventFrom,
            LlmSectionType type,
            String toolCallId,
            String toolName)
        {
            if (!sectionOpen)
            {
                openSection(type, toolCallId, toolName);
            }

            emitStreamed(encodeText(chars, false));

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

                writeSection(pieceBuffer, 0, length, false);
            }
        }

        private boolean controlRoot()
        {
            return TYPE_MESSAGE.equals(documentType) || TYPE_MESSAGE_DELTA.equals(documentType);
        }

        private static boolean derivedMember(
            CharSequence member)
        {
            return MEMBER_ROLE.contentEquals(member) ||
                MEMBER_TYPE.contentEquals(member) ||
                MEMBER_CONTENT.contentEquals(member) ||
                KEY_STOP_REASON.contentEquals(member) ||
                MEMBER_STOP_SEQUENCE.contentEquals(member);
        }

        private void beginControl(
            CharSequence member,
            int message,
            LlmAnthropicResponseDecoder then)
        {
            if (http.framing && controlSeen.carried(member, message))
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
            LlmAnthropicResponseDecoder then)
        {
            controlKey.setLength(0);
            controlKey.append(NAME).append(':').append(member);

            final int capacity = controlKey.length() * REASONING_ESCAPE_EXPANSION + CONTROL_HEADER;

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
                controlCount++;
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
                    parser.consumed(parser.getStringView().length());
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
                    parser.consumed(parser.getStringView().length());
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
                        decodedInput, controlFrom, to - controlFrom);
                }
                else
                {
                    encodePool.buffer(controlSlot).putBytes(controlSlotOffset, decodedInput, controlFrom, to - controlFrom);
                    controlSlotOffset += to - controlFrom;
                }

                controlFrom = to;
            }
        }

        private int controlHoldMax()
        {
            return Math.min(encodeMax, replyMax) - eventReserve();
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
                controlCount--;
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

            controlCount = 0;
        }

        private void beginUsage()
        {
            openControl(MEMBER_USAGE, ROOT_CONTROL, usageThen);
            decoder = decodeUsageStart;
        }

        private boolean endUsage()
        {
            boolean merged = true;

            if (controlFrom != NONE)
            {
                flushControl(position());

                if (usageSlot == NO_SLOT)
                {
                    usageSlot = holdPool.acquire(replyId);

                    if (usageSlot != NO_SLOT)
                    {
                        LlmUsageMerge.reset(holdPool.buffer(usageSlot));
                    }
                }

                merged = usageSlot != NO_SLOT &&
                    LlmUsageMerge.merge(holdPool.buffer(usageSlot), holdPool.slotCapacity(),
                        encodePool.buffer(controlSlot), controlEntry + CONTROL_HEADER, controlSlotOffset);

                if (merged)
                {
                    cancelControl();
                }
                else
                {
                    cleanup(http.decodeTraceId);
                }
            }

            return merged;
        }

        private void emitUsage()
        {
            if (usageSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = holdPool.buffer(usageSlot);
                final int length = LlmUsageMerge.compose(slot, holdPool.slotCapacity(), USAGE_PREFIX);

                if (length != NONE)
                {
                    doAppFrame(http.decodeTraceId, Flags.COMPLETE, LlmSectionType.CONTROL, ROOT_CONTROL, null, null,
                        slot, LlmUsageMerge.composedAt(slot), length);
                }

                cleanupUsageSlot();
            }
        }

        private void cleanupUsageSlot()
        {
            if (usageSlot != NO_SLOT)
            {
                holdPool.release(usageSlot);
                usageSlot = NO_SLOT;
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
        }

        private void closeSection()
        {
            if (sectionOpen && sectionStarted)
            {
                if (sectionType == LlmSectionType.TOOL_CALL)
                {
                    final int encoded = putArgumentsEnd();

                    sectionOpen = false;
                    writeSection(pieceBuffer, 0, encoded, true);
                }
                else if (objectShaped(sectionType))
                {
                    final int encoded = encodeText(emptyChars, true);

                    sectionOpen = false;
                    writeSection(pieceBuffer, 0, encoded, true);
                }
                else
                {
                    sectionOpen = false;
                    writeSection(pieceBuffer, 0, 0, true);
                }
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

        private void beginSectionCapture()
        {
            final int from = position() - 1;

            captureThen = decodeContentItem;
            captureDepth = 1;
            captureFrom = from;

            closeSection();
            openSection(LlmSectionType.TOOL_CALL, toolId, toolName);
            argumentsLength = 1;

            emitArgumentsPiece(encodeText(emptyChars, true));

            decoder = decodeCapture;
        }

        private void endCapture(
            DirectBufferEx buffer)
        {
            flushCapture(buffer, false);
            closeSection();
            sectionOpen = false;
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

                doAppFrame(http.decodeTraceId, flags, sectionType, 0, sectionToolCallId, sectionToolName,
                    buffer, captureFrom, end - captureFrom);
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

        private void reservePiece(
            int capacity)
        {
            if (pieceBytes.length < capacity)
            {
                pieceBytes = new byte[capacity];
                pieceBuffer = new UnsafeBufferEx(pieceBytes);
            }
        }

        private int encodeText(
            JsonStringView chars,
            boolean last)
        {
            final int length = chars.length();
            final boolean object = objectShaped(sectionType);
            final boolean arguments = sectionType == LlmSectionType.TOOL_CALL;
            final int content = object ? body.length(chars, 0, length) : MutableDirectBufferEx.utf8Length(chars, 0, length);

            reservePiece(content + 8 + (object ? REASONING_FRAMING : arguments ? ARGUMENTS_PREFIX.length : 0));

            final String member = sectionType == LlmSectionType.ASSISTANT_REASONING ? reasoningMember : MEMBER_TEXT;

            int position = 0;

            if (object && !sectionStarted)
            {
                position = putMember(member, position);
                sectionMember = member;
            }
            else if (object && !member.equals(sectionMember))
            {
                position = putNextMember(member, position);
                sectionMember = member;
            }
            else if (arguments && !sectionStarted)
            {
                System.arraycopy(ARGUMENTS_PREFIX, 0, pieceBytes, position, ARGUMENTS_PREFIX.length);
                position += ARGUMENTS_PREFIX.length;
            }

            final int written = object
                ? body.transfer(chars, 0, length,
                    pieceTarget.wrap(pieceBuffer.segment(), pieceBuffer.wrapAdjustment() + position))
                : pieceBuffer.putStringWithoutLengthUtf8(position, chars, 0, length);

            position += written;

            if (object && last)
            {
                pieceBytes[position++] = '"';
                pieceBytes[position++] = '}';
            }

            return position;
        }

        private int putNextMember(
            String member,
            int position)
        {
            int next = position;

            pieceBytes[next++] = '"';
            pieceBytes[next++] = ',';

            return putMemberName(member, next);
        }

        private int putMember(
            String member,
            int position)
        {
            int next = position;

            pieceBytes[next++] = '{';

            return putMemberName(member, next);
        }

        private int putMemberName(
            String member,
            int position)
        {
            int next = position;

            pieceBytes[next++] = '"';

            for (int i = 0; i < member.length(); i++)
            {
                pieceBytes[next++] = (byte) member.charAt(i);
            }

            pieceBytes[next++] = '"';
            pieceBytes[next++] = ':';
            pieceBytes[next++] = '"';

            return next;
        }

        private void doAppEnd(
            long traceId)
        {
            state = StreamState.closingReply(state);

            if (!StreamState.replyClosed(state))
            {
                if (failed())
                {
                    doAppAbort(traceId);
                }
                else
                {
                    finish();
                    doAppBeginSections(traceId);

                    state = StreamState.closeReply(state);

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
                        .state(state)
                        .maximum(replyMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .extension(llmEndEx.buffer(), llmEndEx.offset(), llmEndEx.sizeof())
                        .build();

                    app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                    cleanupControlSlot();
                    cleanupUsageSlot();
                }
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (!StreamState.replyClosed(state))
            {
                if (servedModel() != null || id() != null)
                {
                    doAppBeginSections(traceId);
                }

                state = StreamState.closeReply(state);

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
                    .state(state)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(llmAbortEx.buffer(), llmAbortEx.offset(), llmAbortEx.sizeof())
                    .build();

                app.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                cleanupControlSlot();
                cleanupUsageSlot();
            }
        }

        private void doAppReset(
            long traceId,
            int status,
            String type,
            String message)
        {
            if (!StreamState.initialClosed(state))
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

                state = StreamState.closeInitial(state);
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(llmResetEx.buffer(), llmResetEx.offset(), llmResetEx.sizeof())
                    .build();

                app.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
            }
        }

        private void doAppFailure(
            long traceId,
            int status)
        {
            if (StreamState.replyOpening(state) || StreamState.replyOpened(state) || StreamState.initialClosed(state))
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
                .state(state)
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
                .state(state)
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
            decoder = decodeIgnore;
            doAppReset(traceId, STATUS_BAD_GATEWAY, null, null);
            doAppAbort(traceId);
            http.doNetAbort(traceId);
            http.doNetReset(traceId);
            cleanupSlots();
        }

        private void cleanupSlots()
        {
            cleanupControlSlot();
            cleanupUsageSlot();
            http.cleanupEncodeSlot();
            http.cleanupHeldSlot();
            http.cleanupDecodeSlot();
        }
    }

    private final class LlmAnthropicHttpStream
    {
        private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
        private final LlmAnthropicStream stream;
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
        private long initialBudgetId;
        private BudgetDebit initialDebit;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private boolean encodeFailed;

        private int regions;
        private final LlmRequestHold hold;
        private final LlmCacheBreakpoint breakpoints = new LlmCacheBreakpoint(BREAKPOINTS_MAX, BREAKPOINT_TTLS);
        private final LlmAnthropicRequestDecoder decodeRequestRoot = this::decodeRequestRoot;
        private final LlmAnthropicRequestDecoder decodeRequestObject = this::decodeRequestObject;
        private final LlmAnthropicRequestDecoder decodeRequestTextKey = this::decodeRequestTextKey;
        private final LlmAnthropicRequestDecoder decodeRequestTextValue = this::decodeRequestTextValue;
        private final LlmAnthropicRequestDecoder decodeRequestTextContinue = this::decodeRequestTextContinue;
        private final LlmAnthropicRequestDecoder decodeRequestBreakpoint = this::decodeRequestBreakpoint;
        private final LlmAnthropicRequestDecoder decodeRequestBreakpointKey = this::decodeRequestBreakpointKey;
        private final LlmAnthropicRequestDecoder decodeRequestBreakpointMode = this::decodeRequestBreakpointMode;
        private final LlmAnthropicRequestDecoder decodeRequestBreakpointTtl = this::decodeRequestBreakpointTtl;
        private final LlmAnthropicRequestDecoder decodeRequestMember = this::decodeRequestMember;
        private final LlmAnthropicRequestDecoder decodeRequestJudge = this::decodeRequestJudge;
        private final LlmAnthropicRequestDecoder decodeRequestPassString = this::decodeRequestPassString;
        private final LlmAnthropicRequestDecoder decodeRequestPassStringOrDrop = this::decodeRequestPassStringOrDrop;
        private final LlmAnthropicRequestDecoder decodeRequestPassObject = this::decodeRequestPassObject;
        private final LlmAnthropicRequestDecoder decodeRequestCallKey = this::decodeRequestCallKey;
        private final LlmAnthropicRequestDecoder decodeRequestDefinitionKey = this::decodeRequestDefinitionKey;
        private final LlmAnthropicRequestDecoder decodeRequestDefinitionFunction = this::decodeRequestDefinitionFunction;
        private final LlmAnthropicRequestDecoder decodeRequestFunctionKey = this::decodeRequestFunctionKey;
        private final LlmAnthropicRequestDecoder decodeRequestMediaKey = this::decodeRequestMediaKey;
        private final LlmAnthropicRequestDecoder decodeRequestMediaSource = this::decodeRequestMediaSource;
        private final LlmAnthropicRequestDecoder decodeRequestSourceKey = this::decodeRequestSourceKey;
        private final LlmAnthropicRequestDecoder decodeRequestMediaData = this::decodeRequestMediaData;
        private final LlmAnthropicRequestDecoder decodeRequestMediaDataContinue = this::decodeRequestMediaDataContinue;
        private final LlmAnthropicRequestDecoder decodeRequestCitedKey = this::decodeRequestCited;
        private final LlmAnthropicRequestDecoder decodeRequestReasoningKey = this::decodeRequestReasoningKey;
        private final LlmAnthropicRequestDecoder decodeRequestCitationsArray = this::decodeRequestCitationsArray;
        private final LlmAnthropicRequestDecoder decodeRequestCitationsElement = this::decodeRequestCitationsElement;
        private final LlmAnthropicRequestDecoder decodeRequestCitationsKey = this::decodeRequestCitationsKey;
        private final LlmAnthropicRequestDecoder decodeRequestCitationsType = this::decodeRequestCitationsType;
        private final LlmAnthropicRequestDecoder decodeRequestCitationsUrl = this::decodeRequestCitationsUrl;
        private final LlmAnthropicRequestDecoder decodeRequestCitationsUrlKey = this::decodeRequestCitationsUrlKey;
        private final LlmAnthropicRequestDecoder decodeRequestControlKey = this::decodeRequestControlKey;
        private final LlmAnthropicRequestDecoder decodeRequestControlScalar = this::decodeRequestControlScalar;
        private final LlmAnthropicRequestDecoder decodeRequestControlLimit = this::decodeRequestControlLimit;
        private final LlmAnthropicRequestDecoder decodeRequestControlClamp = this::decodeRequestControlClamp;
        private final LlmAnthropicRequestDecoder decodeRequestControlStop = this::decodeRequestControlStop;
        private final LlmAnthropicRequestDecoder decodeRequestControlElement = this::decodeRequestControlElement;
        private final LlmAnthropicRequestDecoder decodeRequestControlChoice = this::decodeRequestControlChoice;
        private final LlmAnthropicRequestDecoder decodeRequestControlFormat = this::decodeRequestControlFormat;
        private final LlmAnthropicRequestDecoder decodeRequestControlFormatKey = this::decodeRequestControlFormatKey;
        private final LlmAnthropicRequestDecoder decodeRequestControlFormatType = this::decodeRequestControlFormatType;
        private final LlmAnthropicRequestDecoder decodeRequestControlSchema = this::decodeRequestControlSchema;
        private final LlmAnthropicRequestDecoder decodeRequestControlSchemaKey = this::decodeRequestControlSchemaKey;
        private final LlmAnthropicRequestDecoder decodeRequestControlSchemaValue = this::decodeRequestControlSchemaValue;
        private final LlmAnthropicRequestDecoder decodeRequestSkip = this::decodeRequestSkip;
        private final LlmAnthropicRequestDecoder decodeRequestDone = this::decodeRequestDone;
        private LlmAnthropicRequestDecoder requestDecoder;
        private LlmAnthropicRequestDecoder requestThen;
        private JsonParserEx requestParser;
        private LlmAnthropicRequestDecoder breakpointThen;
        private final LlmEventCopier copier = new LlmEventCopier();
        private final LlmMemberKeys memberKeys;
        private String requestJudged;
        private int requestVerdict;
        private String rejectedRequestMember;
        private JsonGeneratorEx controlGenerator;
        private JsonGeneratorEx requestTarget;
        private LlmCapturedValue controlChoice;
        private LlmToolChoice toolChoice;
        private LlmAnthropicOutputSchema.Rejection schemaRejection;
        private JsonParserEx requestSource;
        private String controlMember;
        private int controlFormat;
        private String requestPassKey;
        private String callId;
        private String callName;
        private int seenMask;
        private int requestSkipDepth;
        private final StringBuilder breakpointText = new StringBuilder();
        private String breakpointMode;
        private String breakpointTtl;
        private boolean marked;
        private String markedTtl;
        private boolean cited;
        private boolean structured;
        private String conflicted;
        private int messageIndex = NO_MESSAGE;
        private int messageRole = ROLE_NONE;
        private boolean toolResultError;
        private String toolResultId;
        private int sectionKind;
        private boolean sectionOpen;
        private boolean rawOpen;
        private int controlMessage;
        private String choiceType;
        private int parallelCalls = PARALLEL_UNSET;
        private int effort = LlmEffort.ABSENT;
        private int budget = LlmEffort.ABSENT;
        private LlmModelCapability capability = LlmModelCapability.UNSPECIFIED;
        private final LlmMembersOutput controlOutput = new LlmMembersOutput(this::putControl);
        private final LlmAnthropicRequestDecoder decodeRequestControlContainer = this::decodeRequestControlContainer;
        private final LlmAnthropicRequestDecoder decodeRequestContainerMember = this::decodeRequestContainerMember;
        private JsonGeneratorEx containerGenerator;
        private LlmMembersOutput containerOutput;
        private int requestContainer = NO_CONTAINER;
        private int recordOffset;
        private String heldOverflow;

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
            this.sameDialect = !stream.crossDialect;
            this.memberKeys = new LlmMemberKeys(sameDialect ? NAME : null);
            this.generator = JsonEx.createGenerator();
            this.hold = new LlmRequestHold(holdPool, initialId);
        }

        private void doNetBegin(
            long traceId)
        {
            state = StreamState.openingInitial(state);

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

            state = StreamState.openInitial(state);
            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .state(state)
                .maximum(0)
                .traceId(traceId)
                .authorization(stream.authorization)
                .affinity(stream.affinity)
                .circuitId(LlmCircuitId.of(stream.model))
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            net = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onNetMessage);

            if (net == null)
            {
                state = StreamState.closeInitial(state);
                state = StreamState.closeReply(state);
                stream.doAppFailure(traceId, STATUS_BAD_GATEWAY);
            }
            else
            {
                net.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
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
            sectionKind = KIND_DROPPED;
        }

        private String rejectedMember()
        {
            return heldOverflow != null ? heldOverflow : conflicted != null ? conflicted :
                rejectedRequestMember != null ? LlmSharedMembers.wire(rejectedRequestMember) : null;
        }

        private int heldReserve()
        {
            return hold.length(LlmRequestHold.DEFER);
        }

        private void cleanupHeldSlot()
        {
            heldOverflow = null;
            hold.release();
        }

        private boolean holds(
            int container)
        {
            final DirectBufferEx held = hold.buffer();
            final int base = hold.offset(LlmRequestHold.DEFER);
            final int heldLength = hold.length(LlmRequestHold.DEFER);
            boolean holds = false;
            int offset = 0;

            while (!holds && offset < heldLength)
            {
                holds = held.getByte(base + offset) == container;
                offset += RECORD_HEADER + held.getInt(base + offset + RECORD_LENGTH_OFFSET);
            }

            return holds;
        }

        private void drain(
            int container)
        {
            final DirectBufferEx held = hold.buffer();
            final int base = hold.offset(LlmRequestHold.DEFER);
            final int heldLength = hold.length(LlmRequestHold.DEFER);
            int offset = 0;

            while (offset < heldLength)
            {
                final int length = held.getInt(base + offset + RECORD_LENGTH_OFFSET);

                if (held.getByte(base + offset) == container && length > 0)
                {
                    generator.writeMembers(held, base + offset + RECORD_HEADER, length, Completion.COMPLETE);
                }

                offset += RECORD_HEADER + length;
            }
        }

        private boolean openContainer(
            int container)
        {
            recordHeader.putByte(0, (byte) container);
            recordHeader.putInt(RECORD_LENGTH_OFFSET, 0);

            final int opened = hold.length(LlmRequestHold.DEFER);
            final boolean appended = hold.append(LlmRequestHold.DEFER, recordHeader, 0, RECORD_HEADER);

            if (appended)
            {
                recordOffset = opened;

                if (containerGenerator == null)
                {
                    containerGenerator = JsonEx.createGenerator();
                    containerOutput = new LlmMembersOutput(this::putContainer);
                }

                containerOutput.begin();
                containerGenerator.wrap(containerBuffer, 0, containerBuffer.capacity());
                containerGenerator.reset();
            }
            else
            {
                heldOverflow = CONTAINERS[container];
                encodeFailed = true;
            }

            return appended;
        }

        private void putContainer(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (hold.append(LlmRequestHold.DEFER, buffer, offset, length))
            {
                final MutableDirectBufferEx held = hold.buffer();
                final int lengthAt = hold.offset(LlmRequestHold.DEFER) + recordOffset + RECORD_LENGTH_OFFSET;

                held.putInt(lengthAt, held.getInt(lengthAt) + length);
            }
            else
            {
                heldOverflow = CONTAINERS[hold.buffer().getByte(hold.offset(LlmRequestHold.DEFER) + recordOffset)];
                encodeFailed = true;
            }
        }

        private void flushContainer(
            boolean last)
        {
            final int length = containerGenerator.length();

            if (length > 0 || last)
            {
                containerOutput.put(containerBuffer, 0, length, last);
                containerGenerator.wrap(containerBuffer, 0, containerBuffer.capacity());
            }
        }

        private static int containerOf(
            CharSequence member)
        {
            int found = NO_CONTAINER;

            for (int index = 0; found == NO_CONTAINER && index < CONTAINERS.length; index++)
            {
                found = CONTAINERS[index].contentEquals(member) ? index : NO_CONTAINER;
            }

            return found;
        }

        private void onControlEnd()
        {
            rejectCitedStructure();

            final int exclusiveEffort = LlmReasoning.exclusiveEffort(capability, effort, budget);
            budget = LlmReasoning.exclusiveBudget(capability, effort, budget);
            effort = exclusiveEffort;
        }

        private JsonParserEx requestParser()
        {
            if (requestParser == null)
            {
                requestParser = JsonEx.createParser();
            }

            return requestParser;
        }

        private void decodeRequest(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            DirectBufferEx input = buffer;
            int from = offset;
            int limit = offset + length;

            if (hold.length(LlmRequestHold.TAIL) > 0)
            {
                if (hold.append(LlmRequestHold.TAIL, buffer, offset, length))
                {
                    input = hold.buffer();
                    from = 0;
                    limit = hold.length(LlmRequestHold.TAIL);
                }
                else
                {
                    encodeFailed = true;
                }
            }

            if (!encodeFailed && beginEncode(0))
            {
                requestTarget = sectionKind == KIND_CONTROL ? controlGenerator() : generator;

                if (sectionKind == KIND_CONTROL)
                {
                    controlGenerator.wrap(extBuffer, 0, extBuffer.capacity());
                }

                int progress = from;
                int previous;
                LlmAnthropicRequestDecoder previousDecoder;

                do
                {
                    previous = progress;
                    previousDecoder = requestDecoder;
                    progress = requestDecoder.decode(input, progress, limit, last);

                    if (sectionKind == KIND_CONTROL)
                    {
                        flushRequestControl();
                    }
                }
                while ((progress != previous && progress < limit || requestDecoder != previousDecoder) && !encodeFailed);

                if (progress < limit && !last && requestDecoder != decodeRequestDone && !encodeFailed)
                {
                    encodeFailed |= !hold.replace(LlmRequestHold.TAIL, input, progress, limit - progress);
                }
                else
                {
                    hold.clear(LlmRequestHold.TAIL);
                }

                endEncode();
            }
        }

        private JsonParserEx wrapRequest(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            return requestSource.wrap(buffer, offset, limit, last);
        }

        private int positionRequest(
            int limit)
        {
            return limit - requestSource.remaining();
        }

        private int decodeRequestRoot(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestRoot && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.START_DOCUMENT)
                    {
                        requestDecoder = sectionKind == KIND_CITATIONS ? decodeRequestCitationsArray : decodeRequestObject;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestObject(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestObject && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.START_OBJECT)
                    {
                        openRequestObject();
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestTextKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestTextKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView key = parser.getStringView();

                        requestSkipDepth = 0;
                        requestThen = decodeRequestTextKey;

                        if (MEMBER_TEXT.contentEquals(key))
                        {
                            requestDecoder = decodeRequestTextValue;
                        }
                        else if (LlmCacheBreakpoint.MEMBER.contentEquals(key))
                        {
                            breakpointThen = decodeRequestTextKey;
                            requestDecoder = decodeRequestBreakpoint;
                        }
                        else
                        {
                            requestDecoder = decodeRequestSkip;
                        }
                    }
                    else
                    {
                        requestDecoder = decodeRequestDone;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestTextValue(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            return decodeRequestTextPiece(buffer, offset, limit, last, decodeRequestTextValue);
        }

        private int decodeRequestTextContinue(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            return decodeRequestTextPiece(buffer, offset, limit, last, decodeRequestTextContinue);
        }

        private int decodeRequestTextPiece(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last,
            LlmAnthropicRequestDecoder state)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == state && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.VALUE_STRING)
                    {
                        final boolean deferred = parser.deferredBytes();
                        final JsonStringView chars = parser.getStringView();
                        final int length = chars.length();

                        putEscaped(chars, 0, length);

                        if (deferred)
                        {
                            parser.consumed(length);
                            requestDecoder = decodeRequestTextContinue;
                        }
                        else
                        {
                            requestDecoder = decodeRequestTextKey;
                        }
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestBreakpoint(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestBreakpoint && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.START_OBJECT)
                    {
                        breakpointMode = null;
                        breakpointTtl = null;
                        requestDecoder = decodeRequestBreakpointKey;
                    }
                    else if (event == JsonEvent.START_ARRAY)
                    {
                        requestSkipDepth = 1;
                        requestDecoder = decodeRequestSkip;
                    }
                    else
                    {
                        requestDecoder = requestThen;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestBreakpointKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestBreakpointKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView key = parser.getStringView();

                        requestSkipDepth = 0;
                        requestThen = decodeRequestBreakpointKey;

                        if (LlmCacheBreakpoint.MODE.contentEquals(key) && breakpointMode == null)
                        {
                            requestDecoder = decodeRequestBreakpointMode;
                        }
                        else if (LlmCacheBreakpoint.TTL.contentEquals(key) && breakpointTtl == null)
                        {
                            requestDecoder = decodeRequestBreakpointTtl;
                        }
                        else
                        {
                            requestDecoder = decodeRequestSkip;
                        }
                    }
                    else
                    {
                        final boolean admitted = breakpoints.admit(breakpointMode, breakpointTtl);

                        if (text(sectionKind))
                        {
                            marked = admitted;
                            markedTtl = admitted ? breakpoints.ttl() : null;
                        }
                        else if (admitted)
                        {
                            writeCacheControl(breakpoints.ttl());
                            seenMask |= SEEN_CACHED;
                        }

                        requestThen = breakpointThen;
                        requestDecoder = breakpointThen;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestBreakpointMode(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestBreakpointMode && parser.hasNextEvent())
                {
                    final String value = requestBreakpointValue(parser);

                    breakpointMode = value != null ? value : breakpointMode;
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestBreakpointTtl(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestBreakpointTtl && parser.hasNextEvent())
                {
                    final String value = requestBreakpointValue(parser);

                    breakpointTtl = value != null ? value : breakpointTtl;
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private String requestBreakpointValue(
            JsonParserEx parser)
        {
            final JsonEvent event = parser.nextEvent();
            final boolean nested = event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY;
            String value = null;

            if (event == JsonEvent.VALUE_STRING)
            {
                final boolean deferred = parser.deferredBytes();
                final JsonStringView chars = parser.getStringView();

                breakpointText.append(chars);

                if (deferred)
                {
                    parser.consumed(chars.length());
                }
                else
                {
                    value = breakpointText.toString();
                    breakpointText.setLength(0);
                    requestDecoder = decodeRequestBreakpointKey;
                }
            }
            else
            {
                value = "";
                requestSkipDepth = nested ? 1 : 0;
                requestDecoder = nested ? decodeRequestSkip : decodeRequestBreakpointKey;
            }

            return value;
        }

        private int decodeRequestSkip(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestSkip && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
                    {
                        requestSkipDepth++;
                    }
                    else if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
                    {
                        requestSkipDepth--;
                    }

                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                    }
                    else if (requestSkipDepth == 0 && event != JsonEvent.KEY_NAME)
                    {
                        requestDecoder = requestThen;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void openRequestObject()
        {
            requestThen = null;

            switch (sectionKind)
            {
            case KIND_TOOL_CALL, KIND_SERVER_TOOL_CALL:
                openCall();
                requestDecoder = decodeRequestCallKey;
                break;
            case KIND_TOOL_DEFINITION:
                seenMask = 0;
                generator.writeStartObject();
                requestDecoder = decodeRequestDefinitionKey;
                break;
            case KIND_USER_IMAGE, KIND_USER_DOCUMENT:
                seenMask = 0;
                generator.writeStartObject().write(MEMBER_TYPE, sectionKind == KIND_USER_IMAGE ? BLOCK_IMAGE : BLOCK_DOCUMENT);
                requestDecoder = decodeRequestMediaKey;
                break;
            case KIND_ASSISTANT_REASONING:
                seenMask = 0;
                generator.writeStartObject();
                requestDecoder = decodeRequestReasoningKey;
                break;
            case KIND_CONTROL:
                controlGenerator.writeStartObject();
                requestDecoder = decodeRequestControlKey;
                break;
            default:
                requestDecoder = decodeRequestTextKey;
                break;
            }
        }

        private void selectMember(
            CharSequence key,
            LlmAnthropicRequestDecoder keys)
        {
            requestSkipDepth = 0;
            requestThen = keys;

            if (LlmCacheBreakpoint.MEMBER.contentEquals(key) && breakpointed(sectionKind) && (seenMask & SEEN_CACHED) == 0)
            {
                breakpointThen = keys;
                requestDecoder = decodeRequestBreakpoint;
            }
            else
            {
                final int action = sharing(sectionKind) ? memberKeys.selectShared(key) : memberKeys.select(key);

                if (action == LlmMemberKeys.COPY)
                {
                    writeMemberKey(memberKeys.target());
                    copier.reset();
                    requestDecoder = decodeRequestMember;
                }
                else if (action == LlmMemberKeys.SHARED)
                {
                    selectSharedMember(memberKeys.target());
                }
                else if (action == LlmMemberKeys.JUDGE)
                {
                    requestJudged = key.toString();
                    requestVerdict = LlmLegality.UNDECIDED;
                    requestDecoder = decodeRequestJudge;
                }
                else
                {
                    requestDecoder = decodeRequestSkip;
                }
            }
        }

        private void selectSharedMember(
            CharSequence member)
        {
            if (sectionKind == KIND_ASSISTANT_REASONING && LlmSharedMembers.SIGNATURE.contentEquals(member))
            {
                writeReasoningType(BLOCK_THINKING);
                generator.writeKey(MEMBER_SIGNATURE);
                copier.reset();
                requestDecoder = decodeRequestMember;
            }
            else if (sectionKind == KIND_ASSISTANT_REASONING && LlmSharedMembers.REDACTED_DATA.contentEquals(member))
            {
                writeReasoningType(BLOCK_REDACTED_THINKING);
                generator.writeKey(MEMBER_REDACTED_DATA);
                copier.reset();
                requestDecoder = decodeRequestMember;
            }
            else if (sectionKind == KIND_USER_DOCUMENT && LlmCitationsEnabled.MEMBER.contentEquals(member))
            {
                requestSkipDepth = 0;
                seenMask &= ~(SEEN_ENABLED | SEEN_ENABLED_KEY);
                requestDecoder = decodeRequestCitedKey;
            }
            else
            {
                requestDecoder = decodeRequestSkip;
            }
        }

        private void writeMemberKey(
            CharSequence key)
        {
            if (sectionKind == KIND_CITATIONS)
            {
                openCitationElement();
            }

            generator.writeKey(key);
        }

        private void passMember(
            String target,
            LlmAnthropicRequestDecoder keys,
            LlmAnthropicRequestDecoder pass)
        {
            requestPassKey = target;
            requestSkipDepth = 0;
            requestThen = keys;
            requestDecoder = pass;
        }

        private void copyRequestEvent(
            JsonParserEx parser,
            JsonEvent event)
        {
            copyEvent(requestTarget, parser, event);
        }

        private void copyEvent(
            JsonGeneratorEx target,
            JsonParserEx parser,
            JsonEvent event)
        {
            final int taken = copier.copy(target, event, parser);

            if (taken != LlmEventCopier.NO_TAKEN)
            {
                if (parser.deferredBytes())
                {
                    parser.consumed(taken);
                }
                else
                {
                    encodeFailed = true;
                }
            }
        }

        private int decodeRequestMember(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestMember && headroom() && parser.hasNextEvent())
                {
                    copyRequestEvent(parser, parser.nextEvent());

                    if (copier.depth() == 0 && !parser.deferredBytes())
                    {
                        requestDecoder = requestThen;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestJudge(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestJudge && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
                    {
                        requestSkipDepth++;
                        requestVerdict = LlmLegality.advance(requestJudged, requestVerdict, requestSkipDepth, event, null);
                    }
                    else if (event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY)
                    {
                        requestSkipDepth--;
                        requestVerdict = LlmLegality.advance(requestJudged, requestVerdict, requestSkipDepth, event, null);
                    }
                    else if (event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER || event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView chars = parser.getStringView();

                        requestVerdict = LlmLegality.advance(requestJudged, requestVerdict, requestSkipDepth, event, chars);

                        if (parser.deferredBytes())
                        {
                            parser.consumed(chars.length());
                        }
                    }
                    else
                    {
                        requestVerdict = LlmLegality.advance(requestJudged, requestVerdict, requestSkipDepth, event, null);
                    }

                    if (requestSkipDepth == 0 && event != JsonEvent.KEY_NAME && !parser.deferredBytes())
                    {
                        if (LlmLegality.harmless(requestVerdict))
                        {
                            requestDecoder = requestThen;
                        }
                        else
                        {
                            rejectedRequestMember = sectionKind == KIND_CONTROL ? requestJudged : null;
                            encodeFailed = true;
                        }
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestPassString(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            return decodeRequestPass(buffer, offset, limit, last, decodeRequestPassString);
        }

        private int decodeRequestPassStringOrDrop(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            return decodeRequestPass(buffer, offset, limit, last, decodeRequestPassStringOrDrop);
        }

        private int decodeRequestPassObject(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            return decodeRequestPass(buffer, offset, limit, last, decodeRequestPassObject);
        }

        private int decodeRequestPass(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last,
            LlmAnthropicRequestDecoder state)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == state && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();
                    final boolean passed = state == decodeRequestPassObject
                        ? event == JsonEvent.START_OBJECT
                        : event == JsonEvent.VALUE_STRING;

                    if (passed)
                    {
                        writeMemberKey(requestPassKey);
                        copier.reset();
                        copyRequestEvent(parser, event);
                        requestDecoder = copier.depth() == 0 && !parser.deferredBytes() ? requestThen : decodeRequestMember;
                    }
                    else if (state == decodeRequestPassStringOrDrop)
                    {
                        requestSkipDepth = nesting(event);
                        requestDecoder = requestSkipDepth == 0 ? requestThen : decodeRequestSkip;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void openCall()
        {
            callId = sectionAttributes.toolCallId();
            callName = sectionAttributes.toolName();
            seenMask = 0;

            generator.writeStartObject()
                .write(MEMBER_TYPE, sectionKind == KIND_SERVER_TOOL_CALL ? BLOCK_SERVER_TOOL_USE : BLOCK_TOOL_USE);

            if (callId != null)
            {
                generator.write(MEMBER_ID, callId);
            }

            if (callName != null)
            {
                generator.write(MEMBER_NAME, callName);
            }
        }

        private int decodeRequestCallKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCallKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView key = parser.getStringView();

                        if (MEMBER_ARGUMENTS.contentEquals(key))
                        {
                            seenMask |= SEEN_INPUT;
                            passMember(MEMBER_INPUT, decodeRequestCallKey, decodeRequestPassObject);
                        }
                        else
                        {
                            selectMember(key, decodeRequestCallKey);
                        }
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        closeCall();
                        requestDecoder = decodeRequestDone;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void closeCall()
        {
            final String lateId = callId == null ? sectionAttributes.toolCallId() : null;
            final String lateName = callName == null ? sectionAttributes.toolName() : null;

            if ((seenMask & SEEN_INPUT) == 0)
            {
                generator.writeKey(MEMBER_INPUT).writeStartObject().writeEnd();
            }

            if (lateId != null)
            {
                generator.write(MEMBER_ID, lateId);
            }

            if (lateName != null)
            {
                generator.write(MEMBER_NAME, lateName);
            }

            if ((callId != null || lateId != null) && (callName != null || lateName != null))
            {
                generator.writeEnd();
            }
            else
            {
                encodeFailed = true;
            }
        }

        private int decodeRequestDefinitionKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestDefinitionKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView key = parser.getStringView();

                        if (MEMBER_TYPE.contentEquals(key))
                        {
                            requestSkipDepth = 0;
                            requestThen = decodeRequestDefinitionKey;
                            requestDecoder = decodeRequestSkip;
                        }
                        else if (MEMBER_FUNCTION.contentEquals(key))
                        {
                            requestDecoder = decodeRequestDefinitionFunction;
                        }
                        else
                        {
                            selectMember(key, decodeRequestDefinitionKey);
                        }
                    }
                    else if (event == JsonEvent.END_OBJECT && (seenMask & SEEN_NAME) != 0)
                    {
                        generator.writeEnd();
                        requestDecoder = decodeRequestDone;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestDefinitionFunction(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestDefinitionFunction && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.START_OBJECT)
                    {
                        requestDecoder = decodeRequestFunctionKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestFunctionKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestFunctionKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        selectFunctionMember(parser.getStringView());
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        if ((seenMask & SEEN_SCHEMA) == 0)
                        {
                            generator.writeKey(MEMBER_INPUT_SCHEMA).writeStartObject().write(MEMBER_TYPE, TYPE_OBJECT).writeEnd();
                        }

                        requestDecoder = decodeRequestDefinitionKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void selectFunctionMember(
            CharSequence key)
        {
            if (MEMBER_NAME.contentEquals(key))
            {
                seenMask |= SEEN_NAME;
                passMember(MEMBER_NAME, decodeRequestFunctionKey, decodeRequestPassString);
            }
            else if (MEMBER_DESCRIPTION.contentEquals(key))
            {
                passMember(MEMBER_DESCRIPTION, decodeRequestFunctionKey, decodeRequestPassStringOrDrop);
            }
            else if (MEMBER_PARAMETERS.contentEquals(key))
            {
                seenMask |= SEEN_SCHEMA;
                passMember(MEMBER_INPUT_SCHEMA, decodeRequestFunctionKey, decodeRequestPassObject);
            }
            else
            {
                selectMember(key, decodeRequestFunctionKey);
            }
        }

        private String mediaDataMember()
        {
            return sectionKind == KIND_USER_IMAGE ? MEMBER_URL : MEMBER_FILE_DATA;
        }

        private String mediaUrlMember()
        {
            return sectionKind == KIND_USER_IMAGE ? MEMBER_URL : MEMBER_FILE_URL;
        }

        private boolean inlineSource()
        {
            return LlmSectionAttributes.SOURCE_INLINE.equals(sectionAttributes.source());
        }

        private int decodeRequestMediaKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestMediaKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView key = parser.getStringView();

                        if (MEMBER_TYPE.contentEquals(key))
                        {
                            requestSkipDepth = 0;
                            requestThen = decodeRequestMediaKey;
                            requestDecoder = decodeRequestSkip;
                        }
                        else if ((sectionKind == KIND_USER_IMAGE ? MEMBER_IMAGE_URL : MEMBER_FILE).contentEquals(key))
                        {
                            requestDecoder = decodeRequestMediaSource;
                        }
                        else
                        {
                            selectMember(key, decodeRequestMediaKey);
                        }
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        generator.writeEnd();
                        requestDecoder = decodeRequestDone;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestMediaSource(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestMediaSource && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.START_OBJECT)
                    {
                        openMediaSource();
                        requestDecoder = decodeRequestSourceKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void openMediaSource()
        {
            final String mediaType = inlineSource() ? sectionAttributes.mediaType() : null;

            generator.writeKey(MEMBER_SOURCE).writeStartObject();

            if (mediaType != null)
            {
                generator.write(MEMBER_TYPE, SOURCE_BASE64).write(MEMBER_MEDIA_TYPE, mediaType);
            }
            else if (LlmSectionAttributes.SOURCE_URL.equals(sectionAttributes.source()))
            {
                generator.write(MEMBER_TYPE, MEMBER_URL);
            }
            else if (LlmSectionAttributes.SOURCE_REFERENCE.equals(sectionAttributes.source()))
            {
                generator.write(MEMBER_TYPE, SOURCE_FILE);
            }
            else
            {
                encodeFailed = true;
            }
        }

        private int decodeRequestSourceKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestSourceKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        selectSourceMember(parser.getStringView());
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        generator.writeEnd();
                        requestDecoder = decodeRequestMediaKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void selectSourceMember(
            CharSequence key)
        {
            requestSkipDepth = 0;
            requestThen = decodeRequestSourceKey;

            if (mediaDataMember().contentEquals(key) && inlineSource())
            {
                requestDecoder = decodeRequestMediaData;
            }
            else if (mediaDataMember().contentEquals(key) || mediaUrlMember().contentEquals(key))
            {
                passMember(MEMBER_URL, decodeRequestSourceKey, decodeRequestPassString);
            }
            else if (MEMBER_FILE_ID.contentEquals(key))
            {
                passMember(MEMBER_FILE_ID, decodeRequestSourceKey, decodeRequestPassString);
            }
            else if (MEMBER_FILENAME.contentEquals(key))
            {
                requestDecoder = decodeRequestSkip;
            }
            else
            {
                selectMember(key, decodeRequestSourceKey);
            }
        }

        private int decodeRequestMediaData(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                boolean starved = false;

                while (requestDecoder == decodeRequestMediaData && !starved && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.VALUE_STRING)
                    {
                        final boolean deferred = parser.deferredBytes();
                        final JsonStringView chars = parser.getStringView();
                        final int length = chars.length();
                        final String mediaType = sectionAttributes.mediaType();
                        final int prefixLength = DATA_URI_PREFIX.length() + mediaType.length() + BASE64_MARKER.length();

                        if (length >= prefixLength && dataUriPrefix(chars, mediaType))
                        {
                            generator.writeKey(MEMBER_DATA);
                            generator.writeStartEscaped();
                            putEscaped(chars, prefixLength, length);

                            if (deferred)
                            {
                                parser.consumed(length);
                                requestDecoder = decodeRequestMediaDataContinue;
                            }
                            else
                            {
                                generator.writeEndEscaped();
                                requestDecoder = requestThen;
                            }
                        }
                        else if (length < prefixLength && deferred)
                        {
                            parser.consumed(0);
                            starved = true;
                        }
                        else
                        {
                            encodeFailed = true;
                        }
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestMediaDataContinue(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestMediaDataContinue && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.VALUE_STRING)
                    {
                        final boolean deferred = parser.deferredBytes();
                        final JsonStringView chars = parser.getStringView();
                        final int length = chars.length();

                        putEscaped(chars, 0, length);

                        if (deferred)
                        {
                            parser.consumed(length);
                        }
                        else
                        {
                            generator.writeEndEscaped();
                            requestDecoder = requestThen;
                        }
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private boolean dataUriPrefix(
            CharSequence chars,
            String mediaType)
        {
            final int mediaTypeEnd = DATA_URI_PREFIX.length() + mediaType.length();

            return startsWith(chars, 0, DATA_URI_PREFIX) &&
                startsWith(chars, DATA_URI_PREFIX.length(), mediaType) &&
                startsWith(chars, mediaTypeEnd, BASE64_MARKER);
        }

        private int decodeRequestCited(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCitedKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    requestSkipDepth += nesting(event);

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final boolean enabledKey = requestSkipDepth == 1 && MEMBER_ENABLED.contentEquals(parser.getStringView());

                        seenMask = enabledKey ? seenMask | SEEN_ENABLED_KEY : seenMask & ~SEEN_ENABLED_KEY;
                    }
                    else
                    {
                        if (event == JsonEvent.VALUE_TRUE && requestSkipDepth == 1 && (seenMask & SEEN_ENABLED_KEY) != 0)
                        {
                            seenMask |= SEEN_ENABLED;
                        }

                        seenMask &= ~SEEN_ENABLED_KEY;
                    }

                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                    }
                    else if (requestSkipDepth == 0 && event != JsonEvent.KEY_NAME)
                    {
                        writeCitationsEnabled();
                        requestDecoder = requestThen;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void writeCitationsEnabled()
        {
            if ((seenMask & SEEN_ENABLED) != 0)
            {
                generator.writeKey(MEMBER_CITATIONS)
                    .writeStartObject()
                    .write(MEMBER_ENABLED, true)
                    .writeEnd();
                cited = true;
            }
        }

        private void writeReasoningType(
            String type)
        {
            if ((seenMask & SEEN_TYPED) == 0)
            {
                generator.write(MEMBER_TYPE, type);
                seenMask |= SEEN_TYPED;
            }
        }

        private int decodeRequestReasoningKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestReasoningKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView key = parser.getStringView();

                        if (MEMBER_TEXT.contentEquals(key))
                        {
                            writeReasoningType(BLOCK_THINKING);
                            passMember(MEMBER_THINKING, decodeRequestReasoningKey, decodeRequestPassString);
                        }
                        else
                        {
                            selectMember(key, decodeRequestReasoningKey);
                        }
                    }
                    else if (event == JsonEvent.END_OBJECT && (seenMask & SEEN_TYPED) != 0)
                    {
                        generator.writeEnd();
                        requestDecoder = decodeRequestDone;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestCitationsArray(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCitationsArray && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.START_ARRAY)
                    {
                        generator.writeStartArray();
                        requestDecoder = decodeRequestCitationsElement;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestCitationsElement(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCitationsElement && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.START_OBJECT)
                    {
                        seenMask = 0;
                        requestDecoder = decodeRequestCitationsKey;
                    }
                    else if (event == JsonEvent.END_ARRAY)
                    {
                        generator.writeEnd();
                        requestDecoder = decodeRequestDone;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestCitationsKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCitationsKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        selectCitationMember(parser.getStringView());
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        if ((seenMask & SEEN_OPENED) != 0)
                        {
                            generator.writeEnd();
                        }

                        requestDecoder = decodeRequestCitationsElement;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void selectCitationMember(
            CharSequence key)
        {
            if (MEMBER_TYPE.contentEquals(key))
            {
                requestDecoder = decodeRequestCitationsType;
            }
            else if ((seenMask & SEEN_OTHER) != 0)
            {
                requestSkipDepth = 0;
                requestThen = decodeRequestCitationsKey;
                requestDecoder = decodeRequestSkip;
            }
            else if (TYPE_URL_CITATION.contentEquals(key))
            {
                writeCitationType();
                requestDecoder = decodeRequestCitationsUrl;
            }
            else
            {
                writeCitationType();
                selectMember(key, decodeRequestCitationsKey);
            }
        }

        private void writeCitationType()
        {
            if ((seenMask & SEEN_URL) != 0 && (seenMask & SEEN_TYPED) == 0)
            {
                openCitationElement();
                generator.write(MEMBER_TYPE, TYPE_WEB_SEARCH_LOCATION);
                seenMask |= SEEN_TYPED;
            }
        }

        private void openCitationElement()
        {
            if ((seenMask & SEEN_OPENED) == 0)
            {
                generator.writeStartObject();
                seenMask |= SEEN_OPENED;
            }
        }

        private int decodeRequestCitationsType(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCitationsType && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.VALUE_STRING)
                    {
                        final boolean deferred = parser.deferredBytes();
                        final JsonStringView chars = parser.getStringView();
                        final boolean url = !deferred && TYPE_URL_CITATION.contentEquals(chars);

                        seenMask = seenMask & ~(SEEN_URL | SEEN_OTHER) | (url ? SEEN_URL : SEEN_OTHER);

                        if (deferred)
                        {
                            parser.consumed(chars.length());
                        }
                        else
                        {
                            requestDecoder = decodeRequestCitationsKey;
                        }
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestCitationsUrl(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCitationsUrl && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.START_OBJECT)
                    {
                        requestDecoder = decodeRequestCitationsUrlKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestCitationsUrlKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestCitationsUrlKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        final JsonStringView key = parser.getStringView();

                        if (MEMBER_URL.contentEquals(key))
                        {
                            passMember(MEMBER_URL, decodeRequestCitationsUrlKey, decodeRequestPassString);
                        }
                        else if (MEMBER_TITLE.contentEquals(key))
                        {
                            passMember(MEMBER_TITLE, decodeRequestCitationsUrlKey, decodeRequestPassStringOrDrop);
                        }
                        else
                        {
                            requestSkipDepth = 0;
                            requestThen = decodeRequestCitationsUrlKey;
                            requestDecoder = decodeRequestSkip;
                        }
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        requestDecoder = decodeRequestCitationsKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private static boolean startsWith(
            CharSequence chars,
            int from,
            CharSequence prefix)
        {
            boolean matches = true;

            for (int index = 0; matches && index < prefix.length(); index++)
            {
                matches = chars.charAt(from + index) == prefix.charAt(index);
            }

            return matches;
        }

        private boolean headroom()
        {
            return requestTarget != controlGenerator || controlGenerator.remaining() >= CONTROL_HEADROOM;
        }

        private void rejectControl(
            String member)
        {
            rejectedRequestMember = member;
            encodeFailed = true;
        }

        private int decodeRequestControlKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlKey && controlGenerator.remaining() >= CONTROL_HEADROOM &&
                    parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        selectControlMember(parser.getStringView());
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        controlGenerator.writeEnd();
                        requestDecoder = decodeRequestDone;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void selectControlMember(
            CharSequence key)
        {
            requestThen = decodeRequestControlKey;
            requestSkipDepth = 0;

            if (MEMBER_STREAM_OPTIONS.contentEquals(key))
            {
                requestDecoder = decodeRequestSkip;
            }
            else
            {
                final int action = memberKeys.selectShared(key);

                if (action == LlmMemberKeys.COPY)
                {
                    requestContainer = containerOf(memberKeys.target());

                    if (requestContainer != NO_CONTAINER)
                    {
                        requestDecoder = decodeRequestControlContainer;
                    }
                    else
                    {
                        controlGenerator.writeKey(memberKeys.target());
                        copier.reset();
                        requestDecoder = decodeRequestMember;
                    }
                }
                else if (action == LlmMemberKeys.JUDGE)
                {
                    judgeControl(key);
                }
                else if (action == LlmMemberKeys.SHARED)
                {
                    selectSharedControl(memberKeys.target());
                }
                else
                {
                    requestDecoder = decodeRequestSkip;
                }
            }
        }

        private void judgeControl(
            CharSequence member)
        {
            requestJudged = member.toString();
            requestVerdict = LlmLegality.UNDECIDED;
            requestSkipDepth = 0;
            requestDecoder = decodeRequestJudge;
        }

        private void selectSharedControl(
            CharSequence member)
        {
            final int listed = capability.unsupported != 0 ? LlmMember.of(member) : LlmMember.INVALID;

            if (listed != LlmMember.INVALID && capability.unsupported(listed))
            {
                if (LlmMember.behavioural(listed))
                {
                    judgeControl(member);
                }
                else
                {
                    requestDecoder = decodeRequestSkip;
                }
            }
            else if (LlmSharedMembers.TEMPERATURE.contentEquals(member))
            {
                requestDecoder = capability.sampling ? decodeRequestControlClamp : decodeRequestSkip;
            }
            else if (LlmSharedMembers.TOP_P.contentEquals(member) || LlmSharedMembers.TOP_K.contentEquals(member))
            {
                if (capability.sampling)
                {
                    controlGenerator.writeKey(member);
                    copier.reset();
                    requestDecoder = decodeRequestMember;
                }
                else
                {
                    requestDecoder = decodeRequestSkip;
                }
            }
            else if (LlmSharedMembers.REASONING_EFFORT.contentEquals(member) ||
                LlmSharedMembers.THINKING_BUDGET_TOKENS.contentEquals(member) ||
                LlmSharedMembers.PARALLEL_TOOL_CALLS.contentEquals(member))
            {
                controlMember = member.toString();
                requestDecoder = decodeRequestControlScalar;
            }
            else if (LlmSharedMembers.STOP.contentEquals(member))
            {
                requestDecoder = decodeRequestControlStop;
            }
            else if (LlmSharedMembers.TOOL_CHOICE.contentEquals(member))
            {
                if (controlChoice == null)
                {
                    controlChoice = new LlmCapturedValue();
                    toolChoice = new LlmToolChoice();
                }

                controlChoice.clear();
                requestDecoder = decodeRequestControlChoice;
            }
            else if (LlmSharedMembers.RESPONSE_FORMAT.contentEquals(member))
            {
                requestDecoder = decodeRequestControlFormat;
            }
            else if (LlmSharedMembers.USER.contentEquals(member) || LlmSharedMembers.SERVICE_TIER.contentEquals(member))
            {
                controlMember = member.toString();
                requestDecoder = decodeRequestControlLimit;
            }
            else
            {
                requestDecoder = decodeRequestSkip;
            }
        }

        private int decodeRequestControlContainer(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestControlContainer && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    copier.reset();

                    if (event == JsonEvent.START_OBJECT)
                    {
                        if (openContainer(requestContainer))
                        {
                            copyEvent(containerGenerator, parser, event);
                            requestDecoder = decodeRequestContainerMember;
                        }
                    }
                    else
                    {
                        controlGenerator.writeKey(CONTAINERS[requestContainer]);
                        copyRequestEvent(parser, event);
                        requestDecoder = copier.depth() == 0 && !parser.deferredBytes() ? requestThen : decodeRequestMember;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestContainerMember(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestContainerMember &&
                    containerGenerator.remaining() >= CONTROL_HEADROOM && parser.hasNextEvent())
                {
                    copyEvent(containerGenerator, parser, parser.nextEvent());

                    if (copier.depth() == 0 && !parser.deferredBytes())
                    {
                        flushContainer(true);
                        requestDecoder = requestThen;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestControlClamp(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlClamp && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.VALUE_NUMBER)
                    {
                        final JsonStringView value = parser.getStringView();

                        writeClamped(value);

                        if (parser.deferredBytes())
                        {
                            parser.consumed(value.length());
                            requestSkipDepth = 0;
                            requestDecoder = decodeRequestSkip;
                        }
                        else
                        {
                            requestDecoder = decodeRequestControlKey;
                        }
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void writeClamped(
            CharSequence number)
        {
            final double value = Double.parseDouble(number.toString());
            final String bounded = value < TEMPERATURE_MINIMUM
                ? Double.toString(TEMPERATURE_MINIMUM)
                : value > TEMPERATURE_MAXIMUM
                    ? Double.toString(TEMPERATURE_MAXIMUM)
                    : number.toString();

            controlGenerator.writeKey(LlmSharedMembers.TEMPERATURE).writeNumber(bounded);
        }

        private int decodeRequestControlScalar(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestControlScalar && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();
                    final boolean deferred = parser.deferredBytes();
                    final boolean scalar = event != JsonEvent.START_OBJECT && event != JsonEvent.START_ARRAY;
                    final boolean whole = scalar && !deferred;
                    final CharSequence text = whole && (event == JsonEvent.VALUE_NUMBER || event == JsonEvent.VALUE_STRING)
                        ? parser.getStringView()
                        : null;

                    onControlScalar(whole ? event : null, text);

                    if (deferred)
                    {
                        parser.consumed(parser.getStringView().length());
                    }

                    requestSkipDepth = scalar ? 0 : 1;
                    requestDecoder = whole ? decodeRequestControlKey : decodeRequestSkip;
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void onControlScalar(
            JsonEvent scalar,
            CharSequence text)
        {
            if (LlmSharedMembers.PARALLEL_TOOL_CALLS.equals(controlMember))
            {
                if (scalar == JsonEvent.VALUE_TRUE)
                {
                    parallelCalls = PARALLEL_ENABLED;
                }
                else if (scalar == JsonEvent.VALUE_FALSE)
                {
                    parallelCalls = PARALLEL_DISABLED;
                }
                else if (scalar != JsonEvent.VALUE_NULL)
                {
                    rejectControl(LlmSharedMembers.PARALLEL_TOOL_CALLS);
                }
            }
            else if (LlmSharedMembers.REASONING_EFFORT.equals(controlMember))
            {
                final int value = scalar != null ? LlmReasoning.effortOf(scalar, text) : LlmEffort.INVALID;

                if (value == LlmEffort.INVALID)
                {
                    rejectControl(LlmSharedMembers.REASONING_EFFORT);
                }
                else if (value != LlmEffort.ABSENT)
                {
                    effort = value;
                }
            }
            else
            {
                final int value = scalar != null ? LlmReasoning.budgetOf(scalar, text) : LlmEffort.INVALID;

                if (value == LlmEffort.INVALID)
                {
                    rejectControl(LlmSharedMembers.THINKING_BUDGET_TOKENS);
                }
                else if (value != LlmEffort.ABSENT)
                {
                    budget = value;
                }
            }
        }

        private int decodeRequestControlLimit(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlLimit && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.VALUE_STRING)
                    {
                        final JsonStringView value = parser.getStringView();

                        if (parser.deferredBytes())
                        {
                            parser.consumed(value.length());
                            requestSkipDepth = 0;
                            requestDecoder = decodeRequestSkip;
                        }
                        else
                        {
                            writeControlLimit(value);
                            requestDecoder = decodeRequestControlKey;
                        }
                    }
                    else
                    {
                        requestSkipDepth = nesting(event);
                        requestDecoder = requestSkipDepth == 0 ? decodeRequestControlKey : decodeRequestSkip;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int controlLimit()
        {
            return LlmSharedMembers.USER.equals(controlMember) ? USER_ID_MAX : LlmServiceTiers.NAME_MAX;
        }

        private void writeControlLimit(
            CharSequence value)
        {
            if (Character.codePointCount(value, 0, value.length()) <= controlLimit())
            {
                if (LlmSharedMembers.USER.equals(controlMember))
                {
                    if (openContainer(CONTAINER_METADATA))
                    {
                        containerGenerator.writeStartObject().write(MEMBER_USER_ID, value.toString()).writeEnd();
                        flushContainer(true);
                    }
                }
                else
                {
                    final String adapted = LlmServiceTiers.anthropic(capability, value);

                    if (adapted != null)
                    {
                        controlGenerator.write(MEMBER_SERVICE_TIER, adapted);
                    }
                }
            }
        }

        private int decodeRequestControlStop(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestControlStop && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.START_ARRAY)
                    {
                        controlGenerator.writeKey(MEMBER_STOP_SEQUENCES).writeStartArray();
                        requestDecoder = decodeRequestControlElement;
                    }
                    else if (event == JsonEvent.VALUE_NULL)
                    {
                        requestDecoder = decodeRequestControlKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestControlElement(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlElement && controlGenerator.remaining() >= CONTROL_HEADROOM &&
                    parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.END_ARRAY)
                    {
                        controlGenerator.writeEnd();
                        requestDecoder = decodeRequestControlKey;
                    }
                    else
                    {
                        copier.reset();
                        copyRequestEvent(parser, event);

                        if (copier.depth() > 0 || parser.deferredBytes())
                        {
                            requestThen = decodeRequestControlElement;
                            requestDecoder = decodeRequestMember;
                        }
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestControlChoice(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlChoice && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();
                    final boolean deferred = parser.deferredBytes();
                    boolean accepted = true;

                    requestSkipDepth += nesting(event);

                    if (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER)
                    {
                        final JsonStringView chars = parser.getStringView();

                        if (deferred)
                        {
                            parser.consumed(chars.length());
                            accepted = false;
                        }
                        else
                        {
                            accepted = controlChoice.add(event, chars.toString(), CAPTURED_MAX);
                        }
                    }
                    else
                    {
                        accepted = controlChoice.add(event, null, CAPTURED_MAX);
                    }

                    if (!accepted)
                    {
                        rejectControl(LlmSharedMembers.TOOL_CHOICE);
                    }
                    else if (requestSkipDepth == 0 && event != JsonEvent.KEY_NAME && !deferred)
                    {
                        writeToolChoice(controlChoice);
                        requestDecoder = decodeRequestControlKey;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void writeToolChoice(
            LlmCapturedValue value)
        {
            if (toolChoice.parseCanonical(value) && toolChoice.kind() != LlmToolChoice.ALLOWED)
            {
                choiceType = toolChoice.kind() == LlmToolChoice.FUNCTION ? CHOICE_TYPE_TOOL : choiceTypeOf(toolChoice.mode());

                if (openContainer(CONTAINER_TOOL_CHOICE))
                {
                    containerGenerator.writeStartObject().write(MEMBER_TYPE_CHOICE, choiceType);

                    if (toolChoice.kind() == LlmToolChoice.FUNCTION)
                    {
                        containerGenerator.write(MEMBER_NAME, toolChoice.name());
                    }

                    containerGenerator.writeEnd();
                    flushContainer(true);
                }
            }
            else
            {
                rejectControl(LlmSharedMembers.TOOL_CHOICE);
            }
        }

        private static String choiceTypeOf(
            String mode)
        {
            return switch (mode)
            {
            case LlmToolChoice.MODE_REQUIRED -> CHOICE_TYPE_ANY;
            case LlmToolChoice.MODE_NONE -> CHOICE_TYPE_NONE;
            default -> CHOICE_TYPE_AUTO;
            };
        }

        private int decodeRequestControlFormat(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestControlFormat && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.START_OBJECT)
                    {
                        controlFormat = FORMAT_UNSET;
                        requestDecoder = decodeRequestControlFormatKey;
                    }
                    else if (event == JsonEvent.VALUE_NULL)
                    {
                        requestDecoder = decodeRequestControlKey;
                    }
                    else
                    {
                        rejectControl(LlmSharedMembers.RESPONSE_FORMAT);
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestControlFormatKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlFormatKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        selectFormatMember(parser.getStringView());
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        closeControlFormat();
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private void selectFormatMember(
            CharSequence key)
        {
            requestSkipDepth = 0;
            requestThen = decodeRequestControlFormatKey;

            if (MEMBER_TYPE.contentEquals(key))
            {
                requestDecoder = decodeRequestControlFormatType;
            }
            else if (MEMBER_JSON_SCHEMA.contentEquals(key))
            {
                if (openControlFormat(FORMAT_SCHEMA))
                {
                    requestDecoder = decodeRequestControlSchema;
                }
                else
                {
                    rejectControl(LlmSharedMembers.RESPONSE_FORMAT);
                }
            }
            else
            {
                requestDecoder = decodeRequestSkip;
            }
        }

        private void closeControlFormat()
        {
            if (controlFormat == FORMAT_UNSET)
            {
                rejectControl(LlmSharedMembers.RESPONSE_FORMAT);
            }
            else
            {
                if (controlFormat != FORMAT_OMITTED)
                {
                    containerGenerator.writeEnd().writeEnd();
                    flushContainer(true);
                }

                controlFormat = FORMAT_OFF;
                requestDecoder = decodeRequestControlKey;
            }
        }

        private boolean openControlFormat(
            int kind)
        {
            boolean opened = true;

            if (controlFormat == FORMAT_UNSET)
            {
                if (kind == FORMAT_OBJECT)
                {
                    opened = false;
                }
                else if (kind == FORMAT_TEXT)
                {
                    controlFormat = FORMAT_OMITTED;
                }
                else
                {
                    opened = openContainer(CONTAINER_OUTPUT_CONFIG);

                    if (opened)
                    {
                        containerGenerator.writeStartObject()
                            .writeKey(MEMBER_FORMAT)
                            .writeStartObject()
                            .write(MEMBER_TYPE, TYPE_JSON_SCHEMA);
                        structured = true;
                        controlFormat = FORMAT_SCHEMA;
                    }
                }
            }
            else
            {
                opened = controlFormat == kind || controlFormat == FORMAT_OMITTED && kind == FORMAT_TEXT;
            }

            return opened;
        }

        private int decodeRequestControlFormatType(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                if (requestDecoder == decodeRequestControlFormatType && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.VALUE_STRING)
                    {
                        final JsonStringView chars = parser.getStringView();
                        final boolean deferred = parser.deferredBytes();
                        final int kind = deferred ? FORMAT_OFF : formatKindOf(chars);

                        if (deferred)
                        {
                            parser.consumed(chars.length());
                        }

                        if (kind != FORMAT_OFF && openControlFormat(kind))
                        {
                            requestDecoder = decodeRequestControlFormatKey;
                        }
                        else
                        {
                            rejectControl(LlmSharedMembers.RESPONSE_FORMAT);
                        }
                    }
                    else
                    {
                        rejectControl(LlmSharedMembers.RESPONSE_FORMAT);
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private static int formatKindOf(
            CharSequence type)
        {
            int kind = FORMAT_OFF;

            if (TYPE_JSON_SCHEMA.contentEquals(type))
            {
                kind = FORMAT_SCHEMA;
            }
            else if (TYPE_JSON_OBJECT.contentEquals(type))
            {
                kind = FORMAT_OBJECT;
            }
            else if (TYPE_TEXT.contentEquals(type))
            {
                kind = FORMAT_TEXT;
            }

            return kind;
        }

        private int decodeRequestControlSchema(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlSchema && parser.hasNextEvent())
                {
                    if (parser.nextEvent() == JsonEvent.START_OBJECT)
                    {
                        requestDecoder = decodeRequestControlSchemaKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestControlSchemaKey(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlSchemaKey && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (event == JsonEvent.KEY_NAME)
                    {
                        if (MEMBER_SCHEMA.contentEquals(parser.getStringView()))
                        {
                            containerGenerator.writeKey(MEMBER_SCHEMA);
                            copier.reset();
                            schemaRejection = new LlmAnthropicOutputSchema.Rejection();
                            requestSource = outputSchema.newParser(requestParser(), schemaRejection);
                            requestDecoder = decodeRequestControlSchemaValue;
                        }
                        else
                        {
                            requestSkipDepth = 0;
                            requestThen = decodeRequestControlSchemaKey;
                            requestDecoder = decodeRequestSkip;
                        }
                    }
                    else if (event == JsonEvent.END_OBJECT)
                    {
                        requestDecoder = decodeRequestControlFormatKey;
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestControlSchemaValue(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            final JsonParserEx parser = wrapRequest(buffer, offset, limit, last);

            try
            {
                while (requestDecoder == decodeRequestControlSchemaValue &&
                    containerGenerator.remaining() >= CONTROL_HEADROOM && parser.hasNextEvent())
                {
                    final JsonEvent event = parser.nextEvent();

                    if (!encodeFailed && schemaRejection.rejected())
                    {
                        rejectControl(LlmSharedMembers.RESPONSE_FORMAT + " keyword " + schemaRejection.keyword());
                    }
                    else
                    {
                        copyEvent(containerGenerator, parser, event);

                        if (copier.depth() == 0 && !parser.deferredBytes())
                        {
                            requestSource = requestParser();
                            requestDecoder = decodeRequestControlSchemaKey;
                        }
                    }
                }
            }
            catch (JsonException ex)
            {
                encodeFailed = true;
            }

            return positionRequest(limit);
        }

        private int decodeRequestDone(
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean last)
        {
            return limit;
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
            if ((regions & REGION_RESULT_CONTENT_OPENED) == 0)
            {
                generator.writeStartArray(MEMBER_CONTENT);
                regions |= REGION_RESULT_CONTENT_OPENED;
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

            if ((kind == KIND_SERVER_TOOL_DEFINITION || kind == KIND_SERVER_TOOL_CALL || kind == KIND_SERVER_TOOL_RESULT) &&
                stream.crossDialect)
            {
                sectionKind = KIND_DROPPED;
            }
            else if (kind == 0 || kind == KIND_USER_AUDIO)
            {
                encodeFailed = true;
            }
            else if (beginEncode(ENCODE_STRUCTURE + (length(toolCallId) + length(sectionAttributes.toolName()) +
                length(sectionAttributes.mediaType()) + length(sectionAttributes.refId())) * ESCAPE_EXPANSION))
            {
                if (kind == KIND_CITATIONS)
                {
                    if ((regions & REGION_BLOCK_OPENED) != 0)
                    {
                        generator.writeKey(MEMBER_CITATIONS);
                    }
                    else
                    {
                        encodeFailed = true;
                    }
                }
                else if (kind == KIND_CONTROL)
                {
                    controlMessage = message;
                }
                else
                {
                    closeTextBlock();

                    if (kind == KIND_TOOL_DEFINITION || kind == KIND_SERVER_TOOL_DEFINITION)
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

                endEncode();
            }

            if (!encodeFailed && (text(kind) || parsed(kind)))
            {
                if (kind == KIND_CONTROL)
                {
                    beginControl();
                }

                requestDecoder = decodeRequestRoot;
                requestSource = requestParser();
                requestSource.reset();
            }
        }

        private void beginControl()
        {
            capability = stream.binding.capability(stream.model);
            controlOutput.begin();
            requestTarget = controlGenerator();
            requestTarget.reset();
            controlFormat = FORMAT_OFF;
        }

        private JsonGeneratorEx controlGenerator()
        {
            if (controlGenerator == null)
            {
                controlGenerator = JsonEx.createGenerator();
            }

            return controlGenerator;
        }

        private void flushRequestControl()
        {
            final int length = controlGenerator.length();

            if (length > 0 || requestDecoder == decodeRequestDone)
            {
                controlOutput.put(extBuffer, 0, length, requestDecoder == decodeRequestDone);
                controlGenerator.wrap(extBuffer, 0, extBuffer.capacity());
            }

            if (containerGenerator != null && containerGenerator.length() > 0)
            {
                flushContainer(false);
            }
        }

        private void beginMessageBlock(
            int kind,
            int message,
            String toolCallId)
        {
            openRegion(REGION_MESSAGES);

            final int role = role(kind);

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
                        regions &= ~REGION_RESULT_CONTENT_OPENED;
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

        private boolean regionOpen(
            int region)
        {
            return (regions & (region | region << 1)) == region;
        }

        private void openRegion(
            int next)
        {
            if (!regionOpen(next))
            {
                closeRegion();

                if ((regions & next) != 0)
                {
                    encodeFailed = true;
                }
                else
                {
                    generator.writeStartArray(next == REGION_MESSAGES ? MEMBER_MESSAGES :
                        next == REGION_SYSTEM ? MEMBER_SYSTEM : MEMBER_TOOLS);
                    regions |= next;
                }
            }
        }

        private void closeRegion()
        {
            closeTextBlock();

            if (regionOpen(REGION_MESSAGES))
            {
                closeMessage();
            }

            final int open = regionOpen(REGION_MESSAGES) ? REGION_MESSAGES :
                regionOpen(REGION_SYSTEM) ? REGION_SYSTEM :
                regionOpen(REGION_TOOLS) ? REGION_TOOLS : 0;

            if (open != 0)
            {
                generator.writeEnd();
                regions |= open << 1;
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
                if ((regions & REGION_RESULT_CONTENT_OPENED) != 0)
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
                regions &= ~REGION_RESULT_CONTENT_OPENED;
            }
        }

        private void writeCacheControl(
            String ttl)
        {
            generator.writeKey(MEMBER_CACHE_CONTROL).writeStartObject().write(MEMBER_TYPE, CACHE_CONTROL_EPHEMERAL);

            if (ttl != null)
            {
                generator.write(MEMBER_TTL, ttl);
            }

            generator.writeEnd();
        }

        private void closeTextBlock()
        {
            if ((regions & REGION_BLOCK_OPENED) != 0)
            {
                generator.writeEnd();
                regions &= ~REGION_BLOCK_OPENED;
            }
        }

        private void doEncodeSectionData(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            if (requestDecoder != null)
            {
                decodeRequest(buffer, offset, length, fin);
            }
            else if (sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(0))
            {
                putRaw(buffer, offset, length);
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

        private void putControl(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (openControl())
            {
                if (fits(length))
                {
                    generator.writeMembers(buffer, offset, length, Completion.INCOMPLETE);
                    rawOpen = true;
                }
                else
                {
                    encodeFailed = true;
                }
            }
        }

        private boolean openControl()
        {
            final boolean root = controlMessage == ROOT_CONTROL;

            if (root)
            {
                closeRegion();
            }

            return root || (regions & REGION_BLOCK_OPENED) != 0;
        }

        private void putEscaped(
            JsonStringView chars,
            int from,
            int to)
        {
            if (fits((to - from) * ESCAPE_EXPANSION))
            {
                generator.writeSegment(chars, from, to);
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void rejectCitedStructure()
        {
            if (cited && structured)
            {
                conflicted = LlmCitationsEnabled.SHARED_MEMBER;
                encodeFailed = true;
            }
        }

        private void doEncodeSectionEnd(
            boolean isError)
        {
            final boolean cacheMarked = marked;
            final String cacheTtl = markedTtl;

            marked = false;
            markedTtl = null;
            requestDecoder = null;
            hold.clear(LlmRequestHold.TAIL);

            if (sectionKind == KIND_USER_DOCUMENT)
            {
                rejectCitedStructure();
            }

            toolResultError |= sectionKind == KIND_TOOL_RESULT && isError;

            if (sectionKind == KIND_CONTROL)
            {
                onControlEnd();
            }

            if (sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(ENCODE_STRUCTURE + ENCODE_BREAKPOINT))
            {
                if (rawOpen)
                {
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                    rawOpen = false;
                }

                if (sectionOpen)
                {
                    generator.writeEndEscaped();

                    if (cacheMarked)
                    {
                        writeCacheControl(cacheTtl);
                    }

                    regions |= REGION_BLOCK_OPENED;
                    sectionOpen = false;
                }
                else if (sectionKind == KIND_CITATIONS)
                {
                    generator.writeEnd();
                    regions &= ~REGION_BLOCK_OPENED;
                }

                endEncode();
            }

            sectionKind = 0;
        }

        private void doEncodeRequestEnd(
            int maxOutputTokens)
        {
            if (!StreamState.initialClosing(state) && !StreamState.initialClosed(state))
            {
                final int maxTokens = maxOutputTokens != NO_MAX_OUTPUT_TOKENS ? maxOutputTokens : defaultMaxTokens;

                if (beginEncode(ENCODE_STRUCTURE + ENCODE_MAX_TOKENS + ENCODE_TOOL_CHOICE + ENCODE_OUTPUT_CONFIG +
                    ENCODE_THINKING + ENCODE_METADATA + hold.length(LlmRequestHold.DEFER)))
                {
                    closeRegion();
                    thinking.resolve(capability, effort, budget, maxTokens);
                    writeOutputConfig();
                    writeToolChoice();
                    writeThinking();
                    writeMetadata();

                    generator.write(MEMBER_MAX_TOKENS, maxTokens);
                    generator.writeEnd();
                    endEncode();
                }

                cleanupHeldSlot();

                state = StreamState.closingInitial(state);
            }
        }

        private void writeOutputConfig()
        {
            final String effort = thinking.effort();

            if (effort != null || holds(CONTAINER_OUTPUT_CONFIG))
            {
                generator.writeStartObject(MEMBER_OUTPUT_CONFIG);

                if (effort != null)
                {
                    generator.write(MEMBER_EFFORT, effort);
                }

                drain(CONTAINER_OUTPUT_CONFIG);
                generator.writeEnd();
            }
        }

        private void writeThinking()
        {
            final String type = thinking.type();

            if (type != null || holds(CONTAINER_THINKING))
            {
                generator.writeStartObject(MEMBER_THINKING);

                if (type != null)
                {
                    generator.write(MEMBER_TYPE_CHOICE, type);

                    if (thinking.budget() != LlmModelCapability.UNSET)
                    {
                        generator.write(MEMBER_BUDGET_TOKENS, thinking.budget());
                    }
                }

                drain(CONTAINER_THINKING);
                generator.writeEnd();
            }
        }

        private void writeMetadata()
        {
            if (holds(CONTAINER_METADATA))
            {
                generator.writeStartObject(MEMBER_METADATA);
                drain(CONTAINER_METADATA);
                generator.writeEnd();
            }
        }

        private void writeToolChoice()
        {
            final boolean held = holds(CONTAINER_TOOL_CHOICE);
            final boolean parallel = parallelCalls != PARALLEL_UNSET &&
                !CHOICE_TYPE_NONE.equals(choiceType);

            if (held || parallel)
            {
                generator.writeStartObject(MEMBER_TOOL_CHOICE);

                if (!held)
                {
                    generator.write(MEMBER_TYPE_CHOICE, CHOICE_TYPE_AUTO);
                }

                drain(CONTAINER_TOOL_CHOICE);

                if (parallel)
                {
                    generator.write(MEMBER_DISABLE_PARALLEL, parallelCalls == PARALLEL_DISABLED);
                }

                generator.writeEnd();
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

        private int claimInitial(
            long traceId,
            int minimum,
            int maximum)
        {
            return initialDebit != null ? initialDebit.claim(traceId, minimum, maximum) : maximum;
        }

        private void flushEncode(
            long traceId)
        {
            if (net != null && !StreamState.initialClosed(state))
            {
                if (encodeSlot != NO_SLOT)
                {
                    final int flushLimit = encodeSlotOffset;
                    final long window = initialMax - (initialSeq - initialAck) - initialPad;
                    final int lengthMax = (int) Math.max(Math.min(flushLimit, window), 0L);
                    final int length = lengthMax > 0 ? claimInitial(traceId, 1, lengthMax) : 0;

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

                if (StreamState.initialClosing(state) && encodeSlot == NO_SLOT)
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
            state = StreamState.openingReply(state);
            state = StreamState.openReply(state);

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

            state = StreamState.closingReply(state);

            pendingEndTraceId = traceId;

            if (errorStatus != NO_ERROR)
            {
                state = StreamState.closeReply(state);
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
            initialBudgetId = window.budgetId();
            state = StreamState.openInitial(state);

            if (initialBudgetId != 0L)
            {
                if (initialDebit == null)
                {
                    initialDebit = LlmAnthropicClientFactory.this.context.supplyDebit(initialId, initialBudgetId,
                        this::flushEncode);
                }
                initialDebit.declare(window.traceId(), initialPad, 0);
            }

            flushEncode(window.traceId());
            stream.flushAppWindow(window.traceId());
        }

        private void onNetReset(
            ResetFW reset)
        {
            state = StreamState.closeInitial(state);

            cleanupNet(reset.traceId());
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

                if (!StreamState.replyClosing(state))
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
            else if (decodeSlot == NO_SLOT && StreamState.replyClosing(state))
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

                if (StreamState.replyClosing(state))
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
            final boolean last = StreamState.replyClosing(state);

            int progress = stream.decodeNet(buffer, offset, limit, last);

            responseStarted = !stream.completed();

            if (stream.rejected())
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
                stream.decodeNet(buffer, offset, limit, last);

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
            final int progress = stream.decodeNet(decodePool.buffer(decodeSlot), from, to, last);

            int tailFrom = to;

            if (stream.rejected())
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
            final int progress = stream.decodeNet(source, from, to, last);

            if (stream.rejected())
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
                stream.decodeNet(decodePool.buffer(decodeSlot), 0, decodeSlotOffset, true);
                cleanupDecodeSlot();
            }

            stream.resetErrorDocument();
        }

        private void doAppEnd(
            long traceId)
        {
            if (!StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);
                stream.doAppEnd(traceId);
            }
        }

        private void cleanupNet(
            long traceId)
        {
            cleanupDecodeSlot();
            resetDecodeState();
            cleanupEncodeSlot();
            cleanupHeldSlot();
            doNetReset(traceId);
            doNetAbort(traceId);
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
                .state(state)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(stream.authorization)
                .flags(Flags.COMPLETE)
                .budgetId(initialBudgetId)
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
            if (net != null && !StreamState.initialClosed(state))
            {
                state = StreamState.closeInitial(state);
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(stream.authorization)
                    .build();

                net.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
            }
        }

        private void doNetAbort(
            long traceId)
        {
            if (net != null && !StreamState.initialClosed(state))
            {
                state = StreamState.abortingInitial(state);
                state = StreamState.closeInitial(state);
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .state(state)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(stream.authorization)
                    .build();

                net.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
            }

            cleanupHeldSlot();
        }

        private void doNetReset(
            long traceId)
        {
            if (net != null && !StreamState.replyClosed(state))
            {
                state = StreamState.closeReply(state);
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .state(state)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .build();

                net.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
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

            state = StreamState.openReply(state);
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(decodeMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(framing ? QUEUE_ENTRY_PADDING : 0)
                .capabilities(framing ? FRAMING_CAPABILITIES_MASK : 0)
                .build();

            net.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
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
                    .state(state)
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
