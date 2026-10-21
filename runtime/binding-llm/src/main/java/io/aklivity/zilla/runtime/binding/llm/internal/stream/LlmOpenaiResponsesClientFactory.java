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

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonException;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
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
    private static final String SHARED_PREFIX = "x:";
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
    private static final String MEMBER_REASONING = "reasoning";
    private static final String MEMBER_EFFORT = "effort";
    private static final String[] CONTAINERS = {MEMBER_TEXT, MEMBER_REASONING};
    private static final int CONTAINER_TEXT = 0;
    private static final int CONTAINER_REASONING = 1;
    private static final String MEMBER_INCOMPLETE_DETAILS = "incomplete_details";
    private static final String MEMBER_REASON = "reason";
    private static final String TYPE_ERROR = "error";
    private static final String TYPE_OTHER = "";
    private static final String TYPE_TEXT_DELTA = "response.output_text.delta";
    private static final String TYPE_TEXT_DONE = "response.output_text.done";
    private static final String TYPE_PART_ADDED = "response.content_part.added";
    private static final String TYPE_PART_DONE = "response.content_part.done";
    private static final String TYPE_ITEM_ADDED = "response.output_item.added";
    private static final String TYPE_FUNCTION_DELTA = "response.function_call_arguments.delta";
    private static final String TYPE_CUSTOM_DELTA = "response.custom_tool_call_input.delta";
    private static final String TYPE_CODE_DELTA = "response.code_interpreter_call_code.delta";
    private static final String TYPE_MCP_DELTA = "response.mcp_call_arguments.delta";
    private static final String TYPE_ITEM_DONE = "response.output_item.done";
    private static final String TYPE_REFUSAL_DELTA = "response.refusal.delta";
    private static final String TYPE_REFUSAL_DONE = "response.refusal.done";
    private static final String TYPE_SUMMARY_PART_ADDED = "response.reasoning_summary_part.added";
    private static final String TYPE_SUMMARY_DELTA = "response.reasoning_summary_text.delta";
    private static final String TYPE_REASONING_DELTA = "response.reasoning_text.delta";
    private static final String TYPE_ANNOTATION_ADDED = "response.output_text.annotation.added";
    private static final String TYPE_COMPLETED = "response.completed";
    private static final String TYPE_INCOMPLETE = "response.incomplete";
    private static final String TYPE_FAILED = "response.failed";
    private static final String[] EVENT_TYPES =
    {
        TYPE_ERROR, TYPE_TEXT_DELTA, TYPE_TEXT_DONE, TYPE_PART_ADDED, TYPE_PART_DONE, TYPE_ITEM_ADDED, TYPE_ITEM_DONE,
        TYPE_COMPLETED, TYPE_INCOMPLETE, TYPE_FAILED, TYPE_FUNCTION_DELTA, TYPE_CUSTOM_DELTA, TYPE_CODE_DELTA,
        TYPE_MCP_DELTA, TYPE_REFUSAL_DELTA, TYPE_REFUSAL_DONE, TYPE_SUMMARY_PART_ADDED, TYPE_SUMMARY_DELTA,
        TYPE_REASONING_DELTA, TYPE_ANNOTATION_ADDED
    };
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
    private static final int KIND_TOOL_DEFINITION = 6;
    private static final int KIND_SERVER_TOOL_DEFINITION = 7;
    private static final int KIND_TOOL_CALL = 8;
    private static final int KIND_TOOL_RESULT = 9;
    private static final int KIND_SERVER_TOOL_CALL = 10;
    private static final int KIND_SERVER_TOOL_RESULT = 11;
    private static final int KIND_USER_IMAGE = 12;
    private static final int KIND_USER_DOCUMENT = 13;
    private static final int KIND_USER_AUDIO = 14;
    private static final int KIND_ASSISTANT_REFUSAL = 15;
    private static final int KIND_ASSISTANT_REASONING = 16;
    private static final int KIND_CITATIONS = 17;
    private static final int CONTROL_SEEN_MAX = 64;
    private static final int CONTROL_CLOSING = 1;
    private static final int CONTROL_HEADER = 2 * Integer.BYTES;

    private static final int CONTENT_NONE = 0;
    private static final int CONTENT_HELD = 1;
    private static final int CONTENT_ARRAY = 2;
    private static final int CONTENT_CLOSED = 3;
    private static final int CONTENT_PART = 4;
    private static final int REASONING_CLOSED = 0;
    private static final int REASONING_SUMMARY = 1;
    private static final int REASONING_MEMBERS = 2;

    private static final String MEMBER_ITEM_ID = "item_id";
    private static final String MEMBER_NAME = "name";
    private static final String MESSAGE_ITEMS_INTERLEAVED = "Output items interleaved beyond the supported bound.";
    private static final int ITEMS_MAX = 16;
    private static final int ITEM_STAGE_MAX = 1024;
    private static final int MODE_FULL = 0;
    private static final int MODE_ADDED = 1;
    private static final int MODE_DONE = 2;
    private static final int MODE_IGNORE = 3;
    private static final int FLAG_HEAD = 1;
    private static final int FLAG_MEMBERS = 2;
    private static final int FLAG_PASSED = 4;
    private static final int FLAG_RESULT = 8;
    private static final int FLAG_OPEN = 16;
    private static final int FLAG_COMPLETE = 32;
    private static final int FLAG_LATE = 64;
    private static final int FLAG_SEEN = 128;
    private static final int FLAG_VALUE = 256;
    private static final int RECORD_DATA = 0;
    private static final int RECORD_FIN = 1;
    private static final int PART_CALL = 0;
    private static final int PART_RESULT = 1;
    private static final int SERIAL_STAGED = -1;
    private static final int HELD_HEADER = 5 * Integer.BYTES;
    private static final int TARGET_ITEM_ID = 0;
    private static final int TARGET_CALL_ID = 1;
    private static final int TARGET_NAME = 2;
    private static final int TARGET_EVENT_ITEM = 3;
    private static final byte[] CALL_HEAD = "{".getBytes(UTF_8);
    private static final byte[] PROVIDER_HEAD = "{\"arguments\":{".getBytes(UTF_8);
    private static final byte[] CALL_TAIL = "}".getBytes(UTF_8);
    private static final byte[] PROVIDER_TAIL = "}}".getBytes(UTF_8);
    private static final String KEY_ARGUMENTS = "arguments";

    private static final int NO_MESSAGE = -2;
    private static final int ROOT_CONTROL = -1;
    private static final int ASSISTANT_MESSAGE = 0;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int ENCODE_SLACK = 256;
    private static final int ENCODE_STRUCTURE = 128;
    private static final int ENCODE_MAX_OUTPUT_TOKENS = 48;
    private static final int ENCODE_CONTAINER = 64;
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
    private static final String PART_REFUSAL = "refusal";
    private static final String PART_SUMMARY_TEXT = "summary_text";
    private static final String PART_REASONING_TEXT = "reasoning_text";
    private static final String MEMBER_ANNOTATION = "annotation";
    private static final String MEMBER_ENCRYPTED = "encrypted_content";
    private static final String MEMBER_OPENAI_ID = VENDOR_PREFIX + "id";
    private static final String MEMBER_OPENAI_ENCRYPTED = VENDOR_PREFIX + MEMBER_ENCRYPTED;
    private static final String MESSAGE_ENCRYPTED_BOUND =
        "Encrypted content preceding the reasoning summary exceeds the supported bound.";
    private static final String MESSAGE_ANNOTATIONS_BOUND = "Annotations exceed the supported bound.";
    private static final String MESSAGE_ANNOTATIONS_INVALID = "Unsupported output annotation.";
    private static final int REASONING_IGNORE = 0;
    private static final int REASONING_STREAMED = 1;
    private static final int REASONING_PENDING = 2;
    private static final int REASONING_READY = 3;
    private static final int MEMBER_ROOM = 96;
    private static final int CITATIONS_GROWTH = 4;
    private static final String MEMBER_REFUSAL = "refusal";
    private static final String MEMBER_SUMMARY = "summary";
    private static final String MEMBER_ANNOTATIONS = "annotations";
    private static final String TYPE_REASONING = "reasoning";
    private static final String ROLE_TOOL = "tool";
    private static final String MEMBER_TOOLS = "tools";
    private static final String MEMBER_CALL_ID = "call_id";
    private static final String TYPE_FUNCTION_OUTPUT = "function_call_output";
    private static final DirectBufferEx OBJECT_CLOSE = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final DirectBufferEx ITEM_SEPARATOR = new UnsafeBufferEx(",".getBytes(UTF_8));
    private static final DirectBufferEx ARRAY_OPEN = new UnsafeBufferEx("[".getBytes(UTF_8));
    private static final DirectBufferEx ARRAY_CLOSE = new UnsafeBufferEx("]".getBytes(UTF_8));

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
    private final BufferPool containerPool;
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
        this.containerPool = context.bufferPool().duplicate();
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
        return kind == KIND_CONTROL ||
            kind == KIND_TOOL_DEFINITION ||
            kind == KIND_TOOL_CALL ||
            kind == KIND_SERVER_TOOL_CALL ||
            kind == KIND_SERVER_TOOL_RESULT ||
            kind == KIND_USER_IMAGE ||
            kind == KIND_USER_DOCUMENT ||
            kind == KIND_USER_AUDIO ||
            kind == KIND_ASSISTANT_REASONING ||
            kind == KIND_CITATIONS;
    }

    private static boolean part(
        int kind)
    {
        return kind == KIND_USER_IMAGE || kind == KIND_USER_DOCUMENT || kind == KIND_USER_AUDIO;
    }

    private static int sectionKind(
        LlmSectionType type,
        boolean sameDialect)
    {
        return switch (type)
        {
        case SYSTEM_INSTRUCTION -> KIND_SYSTEM_INSTRUCTION;
        case USER_TEXT -> KIND_USER_TEXT;
        case ASSISTANT_TEXT -> KIND_ASSISTANT_TEXT;
        case CONTROL -> KIND_CONTROL;
        case TOOL_DEFINITION -> KIND_TOOL_DEFINITION;
        case TOOL_CALL -> KIND_TOOL_CALL;
        case TOOL_RESULT -> KIND_TOOL_RESULT;
        case SERVER_TOOL_DEFINITION -> sameDialect ? KIND_SERVER_TOOL_DEFINITION : KIND_DROPPED;
        case SERVER_TOOL_CALL -> sameDialect ? KIND_SERVER_TOOL_CALL : KIND_DROPPED;
        case SERVER_TOOL_RESULT -> sameDialect ? KIND_SERVER_TOOL_RESULT : KIND_DROPPED;
        case USER_IMAGE -> KIND_USER_IMAGE;
        case USER_DOCUMENT -> KIND_USER_DOCUMENT;
        case USER_AUDIO -> KIND_USER_AUDIO;
        case ASSISTANT_REFUSAL -> KIND_ASSISTANT_REFUSAL;
        case ASSISTANT_REASONING -> sameDialect ? KIND_ASSISTANT_REASONING : KIND_DROPPED;
        case CITATIONS -> KIND_CITATIONS;
        default -> 0;
        };
    }

    private static String role(
        int kind)
    {
        return switch (kind)
        {
        case KIND_SYSTEM_INSTRUCTION -> ROLE_DEVELOPER;
        case KIND_ASSISTANT_TEXT, KIND_ASSISTANT_REFUSAL -> ROLE_ASSISTANT;
        case KIND_TOOL_RESULT -> ROLE_TOOL;
        default -> ROLE_USER;
        };
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

    private static boolean item(
        int kind)
    {
        return kind == KIND_TOOL_CALL || kind == KIND_SERVER_TOOL_CALL || kind == KIND_SERVER_TOOL_RESULT;
    }

    private static boolean objectShaped(
        LlmSectionType type)
    {
        return type == LlmSectionType.SYSTEM_INSTRUCTION ||
            type == LlmSectionType.USER_TEXT ||
            type == LlmSectionType.ASSISTANT_TEXT ||
            type == LlmSectionType.ASSISTANT_REFUSAL ||
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
        private final LlmOpenaiResponsesDecoder decodeItemMember = this::decodeItemMember;
        private final LlmOpenaiResponsesDecoder decodeItemString = this::decodeItemString;
        private final LlmOpenaiResponsesDecoder decodeItemValue = this::decodeItemValue;
        private final LlmOpenaiResponsesDecoder decodeItemText = this::decodeItemText;
        private final LlmOpenaiResponsesDecoder decodeContentStart = this::decodeContentStart;
        private final LlmOpenaiResponsesDecoder decodeContent = this::decodeContent;
        private final LlmOpenaiResponsesDecoder decodeContentPart = this::decodeContentPart;
        private final LlmOpenaiResponsesDecoder decodePartStart = this::decodePartStart;
        private final LlmOpenaiResponsesDecoder decodePart = this::decodePart;
        private final LlmOpenaiResponsesDecoder decodePartType = this::decodePartType;
        private final LlmOpenaiResponsesDecoder decodeText = this::decodeText;
        private final LlmOpenaiResponsesDecoder decodeReasoning = this::decodeReasoning;
        private final LlmOpenaiResponsesDecoder decodeReasoningId = this::decodeReasoningId;
        private final LlmOpenaiResponsesDecoder decodeReasoningArrayStart = this::decodeReasoningArrayStart;
        private final LlmOpenaiResponsesDecoder decodeReasoningArray = this::decodeReasoningArray;
        private final LlmOpenaiResponsesDecoder decodeReasoningPart = this::decodeReasoningPart;
        private final LlmOpenaiResponsesDecoder decodeEncryptedHold = this::decodeEncryptedHold;
        private final LlmOpenaiResponsesDecoder decodeReasoningTail = this::decodeReasoningTail;
        private final LlmOpenaiResponsesDecoder decodeAnnotation = this::decodeAnnotation;
        private final LlmOpenaiResponsesDecoder decodeCitations = this::decodeCitations;
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
        private String sectionExtension;

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

        private final LlmHeldTail encrypted;
        private final LlmHeldTail annotations;
        private final LlmSectionAttributes annotationAttributes = new LlmSectionAttributes();
        private final LlmSectionPipeline.Output citationsOutput =
            (buffer, offset, length, last) -> putCitations(buffer, offset, length);
        private LlmSectionPipeline annotationPipeline;
        private LlmOpenaiResponsesDecoder annotationThen;
        private int annotationFrom = NONE;
        private int annotationDepth;
        private int annotationObjects;
        private int annotationSeen;
        private int reasoningMode;
        private int reasoningParts;
        private String reasoningId;
        private String reasoningKey;
        private String textMember;
        private String sectionMember;

        private final ResponseItem[] items;
        private int itemCount;
        private int itemSerial;
        private int toolCalls;
        private ResponseItem item;
        private ResponseItem deltaItem;
        private String itemId;
        private int itemTarget;
        private int itemFrom = NONE;
        private int itemDepth;
        private ResponseItem textItem;
        private boolean textEscaped;
        private int stagedBytes;
        private int heldSlot = NO_SLOT;
        private int heldSlotOffset;
        private byte[] itemBytes;
        private UnsafeBufferEx itemBuffer;
        private int itemLength;

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
            this.encrypted = new LlmHeldTail(encodePool);
            this.annotations = new LlmHeldTail(encodePool);
            this.items = new ResponseItem[ITEMS_MAX];
            this.itemBytes = new byte[ENCODE_SLACK];
            this.itemBuffer = new UnsafeBufferEx(itemBytes);

            for (int index = 0; index < ITEMS_MAX; index++)
            {
                this.items[index] = new ResponseItem();
            }

            this.decoder = decodeStart;
        }

        private int replyCredit()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - replyPad);
        }

        private int replyWindow()
        {
            return Math.max(0, replyMax - (int) (replySeq - replyAck) - encodeSlotOffset - controlSlotOffset -
                headHeld() - EVENT_FRAMES * replyPad - sectionClosing());
        }

        private int headHeld()
        {
            return itemCount > 0 ? items[0].held : 0;
        }

        private int sectionClosing()
        {
            return sectionOpen && objectShaped(sectionType) && (http.framing || sectionType == LlmSectionType.ASSISTANT_REASONING)
                ? TEXT_SUFFIX.length + replyPad
                : 0;
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

                        sectionExtension = section != null && section.extension().sizeof() > 0
                            ? section.extension().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o))
                            : null;

                        http.doEncodeSectionBegin(
                            sectionKind(type, http.sameDialect),
                            section != null ? section.message() : NO_MESSAGE,
                            sectionAttributes.toolCallId(),
                            sectionExtension);
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
                else if (fin && http.streaming())
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
            cleanupHeldSlot();
            cleanupHolds();
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

            drainItems();
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
            cleanupHeldSlot();
            cleanupHolds();
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
            final String member = http.rejectedMember();

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
                final String section = requestSection.name().toLowerCase().replace('_', '-');

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
            final int credit = Math.max(0, room - http.holdLength - http.containerReserve());
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
                neutral = toolCalls > 0 ? "tool-call" : "stop";
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
            closeText();
            closeItems();
        }

        private void closeText()
        {
            if (sectionOpen &&
                (sectionType == LlmSectionType.ASSISTANT_TEXT || sectionType == LlmSectionType.ASSISTANT_REFUSAL))
            {
                closeSection();
            }

            drainItems();
        }

        private void openText(
            LlmSectionType type)
        {
            drainItems();

            if (itemCount > 0)
            {
                failItems();
            }
            else
            {
                openSection(type, ASSISTANT_MESSAGE, null, null);
            }
        }

        private boolean itemDelta(
            String type)
        {
            return TYPE_FUNCTION_DELTA.equals(type) ||
                TYPE_CUSTOM_DELTA.equals(type) ||
                TYPE_CODE_DELTA.equals(type) ||
                TYPE_MCP_DELTA.equals(type);
        }

        private void onItemType(
            String type)
        {
            final int kind = LlmOpenaiResponsesItems.kind(type);

            if (kind == LlmOpenaiResponsesItems.KIND_MESSAGE)
            {
                if (TYPE_ITEM_DONE.equals(documentType))
                {
                    closeText();
                }
            }
            else if (kind == LlmOpenaiResponsesItems.KIND_REASONING)
            {
                beginReasoning();
            }
            else if (kind == LlmOpenaiResponsesItems.KIND_FUNCTION_CALL || kind == LlmOpenaiResponsesItems.KIND_CALL)
            {
                beginItem(type, kind);
            }
            else
            {
                rejectOutput(MESSAGE_ITEM_UNSUPPORTED.formatted(type));
            }
        }

        private void beginItem(
            String type,
            int kind)
        {
            final boolean deltas = kind == LlmOpenaiResponsesItems.KIND_FUNCTION_CALL ||
                LlmOpenaiResponsesItems.deltaMember(type) != null;
            final ResponseItem found = http.framing && itemId != null ? findItem(itemId) : null;

            final int mode;

            if (!http.framing)
            {
                mode = MODE_FULL;
            }
            else if (TYPE_ITEM_ADDED.equals(documentType))
            {
                mode = deltas ? MODE_ADDED : MODE_IGNORE;
            }
            else
            {
                mode = found != null ? MODE_DONE : MODE_FULL;
            }

            if (mode == MODE_DONE)
            {
                item = found;
                item.mode = MODE_DONE;
                item.clear(FLAG_SEEN);
            }
            else if (mode == MODE_IGNORE)
            {
                item = null;
            }
            else if (itemCount < ITEMS_MAX)
            {
                item = items[itemCount++];
                item.begin(itemSerial++, itemId, type, kind, mode);
                toolCalls += kind == LlmOpenaiResponsesItems.KIND_FUNCTION_CALL ? 1 : 0;
            }
            else
            {
                item = null;
                failItems();
            }

            decoder = decodeItemMember;
        }

        private ResponseItem findItem(
            String id)
        {
            ResponseItem found = null;

            for (int index = 0; index < itemCount && found == null; index++)
            {
                found = id.equals(items[index].id) ? items[index] : null;
            }

            return found;
        }

        private int openItems()
        {
            int open = 0;

            for (int index = 0; index < itemCount; index++)
            {
                open += items[index].has(FLAG_COMPLETE) ? 0 : 1;
            }

            return open;
        }

        private ResponseItem soleOpenItem()
        {
            ResponseItem sole = null;

            if (openItems() == 1)
            {
                for (int index = 0; index < itemCount && sole == null; index++)
                {
                    sole = items[index].has(FLAG_COMPLETE) ? null : items[index];
                }
            }

            return sole;
        }

        private boolean headItem(
            ResponseItem target)
        {
            return itemCount > 0 && items[0] == target;
        }

        private void failItems()
        {
            errorType = ERROR_TYPE_SERVER;
            errorMessage = MESSAGE_ITEMS_INTERLEAVED;
            parseRejected = true;
        }

        private int decodeItemMember(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeItemMember && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    endItem();
                    decoder = itemThen;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    onItemMember(parser.getStringView());
                }
            }

            return position();
        }

        private void onItemMember(
            CharSequence key)
        {
            final ResponseItem target = item;

            if (target == null ||
                MEMBER_ID.contentEquals(key) ||
                MEMBER_TYPE.contentEquals(key) ||
                MEMBER_STATUS.contentEquals(key))
            {
                skipMember(decodeItemMember);
            }
            else if (LlmOpenaiResponsesItems.hasCallId(target.type) && MEMBER_CALL_ID.contentEquals(key))
            {
                promoteMember(target, TARGET_CALL_ID);
            }
            else if (LlmOpenaiResponsesItems.hasName(target.type) && MEMBER_NAME.contentEquals(key))
            {
                promoteMember(target, TARGET_NAME);
            }
            else if (deltaKey(target, key))
            {
                onDeltaMember(target);
            }
            else if (target.mode == MODE_DONE && !target.has(FLAG_SEEN) ||
                target.mode == MODE_ADDED && target.has(FLAG_SEEN))
            {
                skipMember(decodeItemMember);
            }
            else
            {
                beginItemMember(target, key);
            }
        }

        private void promoteMember(
            ResponseItem target,
            int itemTarget)
        {
            if (target.mode == MODE_DONE)
            {
                skipMember(decodeItemMember);
            }
            else
            {
                beginItemString(itemTarget, decodeItemMember);
            }
        }

        private boolean deltaKey(
            ResponseItem target,
            CharSequence key)
        {
            return target.kind == LlmOpenaiResponsesItems.KIND_FUNCTION_CALL
                ? KEY_ARGUMENTS.contentEquals(key)
                : LlmOpenaiResponsesItems.deltaMember(target.type, key);
        }

        private String deltaName(
            ResponseItem target)
        {
            return target.kind == LlmOpenaiResponsesItems.KIND_FUNCTION_CALL
                ? KEY_ARGUMENTS
                : LlmOpenaiResponsesItems.deltaMember(target.type);
        }

        private void onDeltaMember(
            ResponseItem target)
        {
            final boolean replay = target.mode == MODE_DONE && target.has(FLAG_PASSED) && target.has(FLAG_VALUE);

            target.set(FLAG_SEEN);

            if (target.mode == MODE_ADDED)
            {
                openDelta(target);
                skipMember(decodeItemMember);
            }
            else if (replay)
            {
                closeDelta(target);
                skipMember(decodeItemMember);
            }
            else
            {
                if (!target.has(FLAG_PASSED))
                {
                    openDelta(target);
                }

                textItem = target;
                textEscaped = target.escaped();
                textThen = decodeItemMember;
                decoder = decodeItemText;
            }
        }

        private void openDelta(
            ResponseItem target)
        {
            target.set(FLAG_PASSED);
            writeMemberHeader(target, deltaName(target), target.escaped());
        }

        private void closeDelta(
            ResponseItem target)
        {
            if (target.escaped())
            {
                itemLength = 0;
                putByte('"');
                itemData(target, RECORD_DATA, target.part(), itemBuffer, 0, itemLength);
            }
            else if (!target.has(FLAG_VALUE))
            {
                itemLength = 0;
                putByte('{');
                putByte('}');
                itemData(target, RECORD_DATA, target.part(), itemBuffer, 0, itemLength);
            }

            target.clear(FLAG_PASSED);
        }

        private void beginItemMember(
            ResponseItem target,
            CharSequence key)
        {
            if (LlmOpenaiResponsesItems.resultMember(target.type, key) && !target.has(FLAG_RESULT))
            {
                closeItemPart(target);
                target.set(FLAG_RESULT);
            }

            writeMemberHeader(target, key, false);

            textItem = target;
            itemFrom = position();
            itemDepth = 0;
            decoder = decodeItemValue;
        }

        private int decodeItemValue(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeItemValue && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                final boolean deferred = deferredScalar(event);

                itemDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (itemDepth == 0)
                {
                    flushItem(position());
                    itemFrom = NONE;
                    decoder = decodeItemMember;
                }
            }

            return position();
        }

        private void flushItem(
            int to)
        {
            if (itemFrom != NONE && to > itemFrom && !parseRejected)
            {
                itemData(textItem, RECORD_DATA, textItem.part(), decodeInput, itemFrom, to - itemFrom);
                itemFrom = to;
            }
        }

        private void beginItemString(
            int target,
            LlmOpenaiResponsesDecoder then)
        {
            itemTarget = target;
            valueThen = then;
            decoder = decodeItemString;
        }

        private int decodeItemString(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeItemString && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), valueThen);

                if (value != null)
                {
                    onItemString(value);
                }
            }

            return position();
        }

        private void onItemString(
            String value)
        {
            switch (itemTarget)
            {
            case TARGET_ITEM_ID:
                itemId = value;
                break;
            case TARGET_CALL_ID:
                item.callId = value;
                drainItems();
                break;
            case TARGET_NAME:
                item.name = value;
                drainItems();
                break;
            default:
                deltaItem = findItem(value);
                resolveStaged();
                break;
            }
        }

        private void beginDelta()
        {
            textItem = deltaItem != null ? deltaItem : soleOpenItem();
            textEscaped = !TYPE_FUNCTION_DELTA.equals(documentType);
            textThen = decodeRoot;

            if (textItem == null && openItems() < 2)
            {
                skipMember(decodeRoot);
            }
            else
            {
                decoder = decodeItemText;
            }
        }

        private int decodeItemText(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeItemText && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();
                    final boolean stalled = emitItemText(deferred);

                    if (!deferred && !stalled)
                    {
                        endItemText();
                    }
                    else if (stalled)
                    {
                        break;
                    }
                }
                else
                {
                    skipAfter(event, textThen);
                    endItemText();
                }
            }

            return position();
        }

        private void endItemText()
        {
            if (textThen == decodeItemMember && textItem != null)
            {
                closeDelta(textItem);
            }

            decoder = textThen;
        }

        private boolean emitItemText(
            boolean deferred)
        {
            final ResponseItem target = textItem;

            boolean stalled = false;
            boolean again = true;

            while (again)
            {
                again = false;

                final CharSequence chars = parser.getStringView();
                final int length = chars.length();
                final int part = target != null ? target.part() : PART_CALL;
                final boolean emit = target != null && emittable(target, part);
                final int room = emit ? replyCredit() : holdRoom(target);
                final int take = itemAllowance(chars, textEscaped, deferred, room);
                final boolean finished = !deferred && take == length;

                if (take > 0 || finished && highSurrogate != 0)
                {
                    final int decoded = encodeText(chars, take, finished);

                    if (textEscaped)
                    {
                        final int escaped = escapeText(decoded);

                        routeText(target, part, pieceBuffer, escaped);
                    }
                    else
                    {
                        routeText(target, part, textBuffer, decoded);
                    }
                }

                if (!finished && take > 0)
                {
                    parser.consumed(take);
                }

                if (take < length)
                {
                    if (emit)
                    {
                        stalled = true;
                    }
                    else
                    {
                        drainItems();

                        if (target != null && emittable(target, part))
                        {
                            again = true;
                        }
                        else if (headItem(target))
                        {
                            stalled = true;
                        }
                        else
                        {
                            failItems();
                            stalled = true;
                        }
                    }
                }
            }

            return stalled;
        }

        private void routeText(
            ResponseItem target,
            int part,
            DirectBufferEx buffer,
            int length)
        {
            if (length > 0)
            {
                if (target != null)
                {
                    target.set(FLAG_VALUE);
                    itemData(target, RECORD_DATA, part, buffer, 0, length);
                }
                else
                {
                    holdItem(null, RECORD_DATA, PART_CALL, buffer, 0, length);
                }
            }
        }

        private int holdRoom(
            ResponseItem target)
        {
            final int slotRoom = Math.max(0, encodeMax - heldSlotOffset - HELD_HEADER);

            return headItem(target) ? Math.min(slotRoom, Math.max(0, ITEM_STAGE_MAX - target.held)) : slotRoom;
        }

        private int itemAllowance(
            CharSequence chars,
            boolean escaped,
            boolean deferred,
            int room)
        {
            final int length = chars.length();

            int cost = 0;
            int take = 0;
            int index = 0;
            boolean fits = true;

            while (fits && index < length)
            {
                final char c = chars.charAt(index);
                final boolean pair = Character.isHighSurrogate(c) && index + 1 < length &&
                    Character.isLowSurrogate(chars.charAt(index + 1));
                final int width = pair ? 2 : 1;
                final int next = cost + (pair ? 4 : escaped ? escapedWidth(c) : utf8Width(c));

                fits = next <= room;

                if (fits)
                {
                    cost = next;
                    index += width;
                    take = index;
                }
            }

            return take;
        }

        private static int utf8Width(
            char c)
        {
            return c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
        }

        private int escapeText(
            int length)
        {
            final int capacity = length * LlmJsonEscape.EXPANSION;

            if (pieceBytes.length < capacity)
            {
                pieceBytes = new byte[capacity];
                pieceBuffer = new UnsafeBufferEx(pieceBytes);
            }

            return LlmJsonEscape.escape(textBuffer, 0, length, pieceBytes);
        }

        private void endItem()
        {
            if (item != null && item.mode == MODE_ADDED)
            {
                drainItems();
            }
            else if (item != null)
            {
                finishItem(item);
            }

            item = null;
        }

        private void finishItem(
            ResponseItem target)
        {
            closeItemPart(target);
            target.set(FLAG_COMPLETE);
            drainItems();
        }

        private void closeItemPart(
            ResponseItem target)
        {
            final int part = target.part();

            itemLength = 0;
            putHead(target, part);

            final byte[] tail = target.kind == LlmOpenaiResponsesItems.KIND_CALL && part == PART_CALL
                ? PROVIDER_TAIL
                : CALL_TAIL;

            putBytes(tail);
            target.clear(FLAG_HEAD | FLAG_MEMBERS);

            itemData(target, RECORD_FIN, part, itemBuffer, 0, itemLength);
        }

        private void closeItems()
        {
            for (int index = 0; index < itemCount; index++)
            {
                if (!items[index].has(FLAG_COMPLETE))
                {
                    items[index].mode = MODE_FULL;
                    finishItem(items[index]);
                }
            }

            drainItems();
        }

        private boolean itemsPending()
        {
            closeItems();

            return itemCount > 0;
        }

        private void writeMemberHeader(
            ResponseItem target,
            CharSequence key,
            boolean quoted)
        {
            final int part = target.part();

            itemLength = 0;
            putHead(target, part);

            if (target.has(FLAG_MEMBERS))
            {
                putByte(',');
            }

            target.set(FLAG_MEMBERS);

            putByte('"');

            if (target.kind == LlmOpenaiResponsesItems.KIND_FUNCTION_CALL && !KEY_ARGUMENTS.contentEquals(key))
            {
                putChars(VENDOR_PREFIX);
            }

            putChars(key);
            putByte('"');
            putByte(':');

            if (quoted)
            {
                putByte('"');
            }

            itemData(target, RECORD_DATA, part, itemBuffer, 0, itemLength);
        }

        private void putHead(
            ResponseItem target,
            int part)
        {
            if (!target.has(FLAG_HEAD))
            {
                target.set(FLAG_HEAD);
                putBytes(target.kind == LlmOpenaiResponsesItems.KIND_CALL && part == PART_CALL ? PROVIDER_HEAD : CALL_HEAD);
            }
        }

        private void putByte(
            char c)
        {
            ensureItemCapacity(1);
            itemBytes[itemLength++] = (byte) c;
        }

        private void putBytes(
            byte[] bytes)
        {
            ensureItemCapacity(bytes.length);
            System.arraycopy(bytes, 0, itemBytes, itemLength, bytes.length);
            itemLength += bytes.length;
        }

        private void putChars(
            CharSequence chars)
        {
            final int length = chars.length();

            ensureItemCapacity(length * 3);

            for (int index = 0; index < length; index++)
            {
                final char c = chars.charAt(index);

                if (c < 0x80)
                {
                    itemBytes[itemLength++] = (byte) c;
                }
                else if (c < 0x800)
                {
                    itemBytes[itemLength++] = (byte) (0xC0 | c >> 6);
                    itemBytes[itemLength++] = (byte) (0x80 | c & 0x3F);
                }
                else
                {
                    itemBytes[itemLength++] = (byte) (0xE0 | c >> 12);
                    itemBytes[itemLength++] = (byte) (0x80 | c >> 6 & 0x3F);
                    itemBytes[itemLength++] = (byte) (0x80 | c & 0x3F);
                }
            }
        }

        private void ensureItemCapacity(
            int extra)
        {
            if (itemBytes.length < itemLength + extra)
            {
                itemBytes = Arrays.copyOf(itemBytes, Math.max(itemBytes.length << 1, itemLength + extra));
                itemBuffer = new UnsafeBufferEx(itemBytes);
            }
        }

        private boolean emittable(
            ResponseItem target,
            int part)
        {
            return headItem(target) &&
                target.held == 0 &&
                (!sectionOpen || target.has(FLAG_OPEN)) &&
                (target.has(FLAG_OPEN) || target.ready(part));
        }

        private void itemData(
            ResponseItem target,
            int kind,
            int part,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (emittable(target, part) && length <= replyCredit())
            {
                writeItemFrame(target, part, buffer, offset, length, kind == RECORD_FIN);
            }
            else
            {
                holdItem(target, kind, part, buffer, offset, length);
                drainItems();
            }
        }

        private void holdItem(
            ResponseItem target,
            int kind,
            int part,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (heldSlot == NO_SLOT)
            {
                heldSlot = encodePool.acquire(replyId);
            }

            if (heldSlot == NO_SLOT || heldSlotOffset + HELD_HEADER + length > encodeMax)
            {
                failItems();
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(heldSlot);

                slot.putInt(heldSlotOffset, target != null ? target.serial : SERIAL_STAGED);
                slot.putInt(heldSlotOffset + Integer.BYTES, kind);
                slot.putInt(heldSlotOffset + 2 * Integer.BYTES, part);
                slot.putInt(heldSlotOffset + 3 * Integer.BYTES, length);
                slot.putInt(heldSlotOffset + 4 * Integer.BYTES, 0);
                slot.putBytes(heldSlotOffset + HELD_HEADER, buffer, offset, length);
                heldSlotOffset += HELD_HEADER + length;

                if (target != null)
                {
                    target.held += length;
                }
                else
                {
                    stagedBytes += length;
                }
            }
        }

        private void resolveStaged()
        {
            if (stagedBytes > 0)
            {
                if (deltaItem == null)
                {
                    failItems();
                }
                else
                {
                    final MutableDirectBufferEx slot = encodePool.buffer(heldSlot);

                    for (int from = 0; from < heldSlotOffset; )
                    {
                        final int length = slot.getInt(from + 3 * Integer.BYTES);

                        if (slot.getInt(from) == SERIAL_STAGED)
                        {
                            slot.putInt(from, deltaItem.serial);
                        }

                        from += HELD_HEADER + length;
                    }

                    deltaItem.held += stagedBytes;
                    deltaItem.set(FLAG_VALUE);
                    stagedBytes = 0;
                    drainItems();
                }
            }
        }

        private void drainItems()
        {
            boolean progress = true;

            while (progress && itemCount > 0)
            {
                final ResponseItem head = items[0];

                progress = false;

                if (!sectionOpen || head.has(FLAG_OPEN))
                {
                    if (head.held > 0 &&
                        (head.has(FLAG_OPEN) ||
                        head.has(FLAG_COMPLETE) ||
                        head.held >= ITEM_STAGE_MAX ||
                        head.ready(PART_CALL)))
                    {
                        releaseHeld(head);
                    }

                    if (head.has(FLAG_COMPLETE) && head.held == 0 && !head.has(FLAG_OPEN))
                    {
                        popHead();
                        progress = true;
                    }
                }
            }
        }

        private void releaseHeld(
            ResponseItem head)
        {
            final MutableDirectBufferEx slot = encodePool.buffer(heldSlot);

            int from = 0;
            int to = 0;
            boolean blocked = false;

            while (from < heldSlotOffset)
            {
                final int serial = slot.getInt(from);
                final int kind = slot.getInt(from + Integer.BYTES);
                final int part = slot.getInt(from + 2 * Integer.BYTES);
                final int length = slot.getInt(from + 3 * Integer.BYTES);
                final int consumed = slot.getInt(from + 4 * Integer.BYTES);
                final int record = HELD_HEADER + length;

                boolean keep = true;

                if (serial == head.serial && !blocked)
                {
                    final int remaining = length - consumed;
                    final int take = Math.min(remaining, replyCredit());

                    if (take == remaining && (take > 0 || kind == RECORD_FIN))
                    {
                        writeItemFrame(head, part, slot, from + HELD_HEADER + consumed, take, kind == RECORD_FIN);
                        head.held -= take;
                        keep = false;
                    }
                    else if (take > 0)
                    {
                        writeItemFrame(head, part, slot, from + HELD_HEADER + consumed, take, false);
                        head.held -= take;
                        slot.putInt(from + 4 * Integer.BYTES, consumed + take);
                        blocked = true;
                    }
                    else
                    {
                        blocked = true;
                    }
                }

                if (keep)
                {
                    if (to != from)
                    {
                        slot.putBytes(to, slot, from, record);
                    }

                    to += record;
                }

                from += record;
            }

            heldSlotOffset = to;

            if (heldSlotOffset == 0)
            {
                cleanupHeldSlot();
            }
        }

        private void popHead()
        {
            final ResponseItem done = items[0];

            System.arraycopy(items, 1, items, 0, itemCount - 1);
            items[--itemCount] = done;
        }

        private void cleanupHeldSlot()
        {
            if (heldSlot != NO_SLOT)
            {
                encodePool.release(heldSlot);
                heldSlot = NO_SLOT;
                heldSlotOffset = 0;
            }
        }

        private void writeItemFrame(
            ResponseItem target,
            int part,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            int flags = Flags.NONE;

            if (!target.has(FLAG_OPEN))
            {
                flags = Flags.INIT;
                target.set(FLAG_OPEN);
                target.openedToolId = target.toolCallId();
                target.openedToolName = part == PART_CALL ? target.toolName() : null;
                sectionOpen = true;
                sectionType = part == PART_RESULT
                    ? LlmSectionType.SERVER_TOOL_RESULT
                    : target.kind == LlmOpenaiResponsesItems.KIND_FUNCTION_CALL
                        ? LlmSectionType.TOOL_CALL
                        : LlmSectionType.SERVER_TOOL_CALL;

                if (target.openedToolId == null || part == PART_CALL && target.openedToolName == null)
                {
                    target.set(FLAG_LATE);
                }
            }

            String lateId = null;
            String lateName = null;

            if (fin)
            {
                flags = Flags.fin(flags);

                if (target.has(FLAG_LATE))
                {
                    lateId = target.openedToolId == null ? target.toolCallId() : null;
                    lateName = part == PART_CALL && target.openedToolName == null ? target.toolName() : null;
                    target.clear(FLAG_LATE);
                }
            }

            doAppItemFrame(http.decodeTraceId, flags, target, part, lateId, lateName, buffer, offset, length);

            if (fin)
            {
                target.clear(FLAG_OPEN);
                sectionOpen = false;
                emitControls();
            }
        }

        private void doAppItemFrame(
            long traceId,
            int flags,
            ResponseItem target,
            int part,
            String lateId,
            String lateName,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final OctetsFW extension;

            if (Flags.hasInit(flags))
            {
                final String toolId = target.openedToolId;
                final String toolName = target.openedToolName;
                final byte[] itemType = target.type.getBytes(UTF_8);
                final LlmSectionType type = sectionType;
                final LlmDataExFW llmDataEx = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .section(x ->
                    {
                        x.type(t -> t.set(type)).message(ASSISTANT_MESSAGE);

                        if (toolName != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_NAME).value(toolName));
                        }

                        if (toolId != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_CALL_ID).value(toolId));
                        }

                        if (target.kind == LlmOpenaiResponsesItems.KIND_CALL)
                        {
                            x.extension(e -> e.set(itemType));
                        }
                    })
                    .build();

                extension = extensionRO.wrap(llmDataEx.buffer(), llmDataEx.offset(), llmDataEx.offset() + llmDataEx.sizeof());
            }
            else if (lateId != null || lateName != null)
            {
                final LlmDataExFW llmDataEx = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .section(x ->
                    {
                        x.type(t -> t.set(LlmSectionType.NONE)).message(NONE);

                        if (lateName != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_NAME).value(lateName));
                        }

                        if (lateId != null)
                        {
                            x.attributesItem(a -> a.name(LlmSectionAttributes.TOOL_CALL_ID).value(lateId));
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
            itemCount = 0;
            item = null;
            deltaItem = null;
            itemFrom = NONE;
            stagedBytes = 0;
            toolCalls = 0;
            annotationFrom = NONE;
            annotationSeen = 0;
            reasoningMode = REASONING_IGNORE;
            reasoningParts = 0;
            reasoningId = null;
            cleanupEncodeSlot();
            cleanupControlSlot();
            cleanupHeldSlot();
            cleanupHolds();
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

            if (itemFrom != NONE)
            {
                itemFrom = offset;
            }

            if (annotationFrom != NONE)
            {
                annotationFrom = offset;
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
                flushItem(progress);
                flushAnnotation(progress);
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
                headHeld() - parser.remaining() - sectionClosing();
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
                    if (stagedBytes > 0)
                    {
                        failItems();
                    }

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
                        beginText(LlmSectionType.ASSISTANT_TEXT, decodeRoot);
                    }
                    else if (MEMBER_DELTA.contentEquals(member) && TYPE_REFUSAL_DELTA.equals(documentType))
                    {
                        beginText(LlmSectionType.ASSISTANT_REFUSAL, decodeRoot);
                    }
                    else if (MEMBER_DELTA.contentEquals(member) &&
                        (TYPE_SUMMARY_DELTA.equals(documentType) || TYPE_REASONING_DELTA.equals(documentType)))
                    {
                        beginText(LlmSectionType.ASSISTANT_REASONING, decodeRoot);
                    }
                    else if (MEMBER_ANNOTATION.contentEquals(member) && TYPE_ANNOTATION_ADDED.equals(documentType))
                    {
                        beginAnnotation(decodeRoot);
                    }
                    else if (MEMBER_DELTA.contentEquals(member) && itemDelta(documentType))
                    {
                        beginDelta();
                    }
                    else if (MEMBER_ITEM_ID.contentEquals(member) && itemDelta(documentType))
                    {
                        beginItemString(TARGET_EVENT_ITEM, decodeRoot);
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

                    if (TYPE_TEXT_DONE.equals(documentType) || TYPE_PART_DONE.equals(documentType) ||
                        TYPE_REFUSAL_DONE.equals(documentType))
                    {
                        closeText();
                    }
                    else if (TYPE_COMPLETED.equals(documentType) || TYPE_INCOMPLETE.equals(documentType))
                    {
                        closeText();
                        closeItems();
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
                    itemId = null;
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
                    itemId = null;
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
                    else if (MEMBER_ID.contentEquals(member))
                    {
                        beginItemString(TARGET_ITEM_ID, decodeItem);
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

                if (value != null)
                {
                    onItemType(value);
                }
            }

            return position();
        }

        private void beginReasoning()
        {
            final boolean added = TYPE_ITEM_ADDED.equals(documentType);

            closeText();

            reasoningId = itemId;
            reasoningMode = added
                ? REASONING_IGNORE
                : http.framing && reasoningParts > 0 ? REASONING_STREAMED : REASONING_PENDING;
            reasoningParts = added ? 0 : reasoningParts;
            decoder = decodeReasoning;
        }

        private int decodeReasoning(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeReasoning && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = reasoningMode == REASONING_IGNORE ? itemThen : decodeReasoningTail;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    onReasoningMember(parser.getStringView());
                }
            }

            return position();
        }

        private void onReasoningMember(
            CharSequence key)
        {
            if (reasoningMode == REASONING_IGNORE)
            {
                skipMember(decodeReasoning);
            }
            else if (MEMBER_ID.contentEquals(key))
            {
                decoder = decodeReasoningId;
            }
            else if (reasoningMode == REASONING_PENDING &&
                (MEMBER_SUMMARY.contentEquals(key) || MEMBER_CONTENT.contentEquals(key)))
            {
                reasoningKey = MEMBER_SUMMARY.contentEquals(key) ? MEMBER_SUMMARY : MEMBER_CONTENT;
                decoder = decodeReasoningArrayStart;
            }
            else if (MEMBER_ENCRYPTED.contentEquals(key))
            {
                beginEncrypted();
            }
            else
            {
                skipMember(decodeReasoning);
            }
        }

        private void beginEncrypted()
        {
            if (reasoningId != null && reasoningMode != REASONING_PENDING && !encrypted.held())
            {
                textType = LlmSectionType.ASSISTANT_REASONING;
                textMember = MEMBER_OPENAI_ENCRYPTED;
                textThen = decodeReasoning;
                decoder = decodeText;
            }
            else
            {
                decoder = decodeEncryptedHold;
            }
        }

        private int decodeReasoningId(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeReasoningId && hasNextEvent())
            {
                final String value = stringValue(parser.nextEvent(), decodeReasoning);

                if (value != null)
                {
                    reasoningId = value;
                }
            }

            return position();
        }

        private int decodeReasoningArrayStart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_ARRAY)
                {
                    decoder = decodeReasoningArray;
                }
                else
                {
                    skipAfter(event, decodeReasoning);
                }
            }

            return position();
        }

        private int decodeReasoningArray(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeReasoningArray && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.START_OBJECT)
                {
                    openReasoningPart();
                    decoder = decodeReasoningPart;
                }
                else if (event == JsonEvent.END_ARRAY)
                {
                    reasoningMode = MEMBER_SUMMARY.equals(reasoningKey) ? REASONING_READY : reasoningMode;
                    decoder = decodeReasoning;
                }
                else
                {
                    skipAfter(event, decodeReasoningArray);
                }
            }

            return position();
        }

        private int decodeReasoningPart(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeReasoningPart && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.END_OBJECT)
                {
                    decoder = decodeReasoningArray;
                }
                else if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else if (MEMBER_TEXT.contentEquals(parser.getStringView()))
                {
                    beginText(LlmSectionType.ASSISTANT_REASONING, decodeReasoningPart);
                }
                else
                {
                    skipMember(decodeReasoningPart);
                }
            }

            return position();
        }

        private void openReasoningPart()
        {
            if (sectionOpen && sectionType == LlmSectionType.ASSISTANT_REASONING)
            {
                closeSection();
            }

            openText(LlmSectionType.ASSISTANT_REASONING);
            reasoningParts++;
        }

        private int decodeEncryptedHold(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeEncryptedHold && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                if (event == JsonEvent.VALUE_STRING)
                {
                    final boolean deferred = parser.deferredBytes();
                    final CharSequence chars = parser.getStringView();
                    final int length = chars.length();
                    final int decoded = encodeText(chars, length, !deferred);

                    if (!encrypted.append(replyId, textBuffer, 0, decoded))
                    {
                        rejectOutput(MESSAGE_ENCRYPTED_BOUND);
                    }
                    else if (deferred)
                    {
                        parser.consumed(length);
                    }
                    else
                    {
                        decoder = decodeReasoning;
                    }
                }
                else
                {
                    skipAfter(event, decodeReasoning);
                }
            }

            return position();
        }

        private int decodeReasoningTail(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            boolean progress = true;

            while (decoder == decodeReasoningTail && progress && !parseRejected)
            {
                if (encrypted.remaining() > 0)
                {
                    final int take = tailAllowance();

                    progress = take > 0;

                    if (progress)
                    {
                        textType = LlmSectionType.ASSISTANT_REASONING;
                        textMember = MEMBER_OPENAI_ENCRYPTED;
                        text(encrypted.buffer(), encrypted.offset(), take);
                        encrypted.consumed(take);
                    }
                }
                else
                {
                    progress = tailCredit() >= finishCost();

                    if (progress)
                    {
                        finishReasoning();
                        decoder = itemThen;
                    }
                }
            }

            return position();
        }

        private int tailCredit()
        {
            return replyMax - (int) (replySeq - replyAck) - encodeSlotOffset - controlSlotOffset - headHeld();
        }

        private int tailAllowance()
        {
            final MutableDirectBufferEx held = encrypted.buffer();
            final int from = encrypted.offset();
            final int length = encrypted.remaining();
            final int credit = tailCredit();

            int cost = (headerPending() ? memberHeaderLength() + replyPad : 0) + replyPad + TEXT_SUFFIX.length + replyPad;
            int take = 0;
            boolean fits = true;

            while (fits && take < length)
            {
                final int next = cost + escapedWidth((char) (held.getByte(from + take) & 0xFF));

                fits = next <= credit;

                if (fits)
                {
                    cost = next;
                    take++;
                }
            }

            return take;
        }

        private int finishCost()
        {
            return TEXT_SUFFIX.length + replyPad +
                (sectionMember == null && reasoningId != null ? memberHeaderLength() + replyPad : 0);
        }

        private void finishReasoning()
        {
            if (sectionOpen && sectionMember != null)
            {
                closeSection();
            }
            else if (reasoningId != null)
            {
                emitIdMember();
            }
            else if (sectionOpen)
            {
                closeSection();
            }

            encrypted.release();
            reasoningParts = 0;
            reasoningId = null;
            reasoningMode = REASONING_IGNORE;
        }

        private void emitIdMember()
        {
            if (!sectionOpen)
            {
                openText(LlmSectionType.ASSISTANT_REASONING);
            }

            if (sectionOpen)
            {
                reservePiece(memberHeaderLength());
                generator.wrap(pieceBuffer, 0, pieceBytes.length);

                if (sectionStarted)
                {
                    generator.writeEndEscaped();
                }
                else
                {
                    generator.reset();
                    generator.writeStartObject();
                }

                generator.write(MEMBER_OPENAI_ID, reasoningId).writeEnd();
                emitFrame(pieceBuffer, 0, generator.length(), true);
                emitControls();
            }
        }

        private boolean headerPending()
        {
            return textMember != null && (!sectionOpen || !textMember.equals(sectionMember));
        }

        private int memberHeaderLength()
        {
            return MEMBER_ROOM + (reasoningId != null ? reasoningId.length() * ESCAPE_EXPANSION : 0);
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

        private void emitMemberHeader()
        {
            reservePiece(memberHeaderLength());
            generator.wrap(pieceBuffer, 0, pieceBytes.length);

            if (sectionStarted)
            {
                generator.writeEndEscaped();
            }
            else
            {
                generator.reset();
                generator.writeStartObject();
            }

            if (reasoningId != null)
            {
                generator.write(MEMBER_OPENAI_ID, reasoningId);
            }

            generator.writeKey(textMember).writeStartEscaped();
            sectionMember = textMember;
            emitFrame(pieceBuffer, 0, generator.length(), false);
        }

        private void beginAnnotation(
            LlmOpenaiResponsesDecoder then)
        {
            annotationThen = then;
            annotationSeen++;

            appendAnnotation(annotationObjects == 0 ? ARRAY_OPEN : ITEM_SEPARATOR, 0, 1);
            annotationObjects++;
            startAnnotationCapture();
        }

        private void beginAnnotations(
            LlmOpenaiResponsesDecoder then)
        {
            annotationThen = then;

            startAnnotationCapture();
        }

        private void startAnnotationCapture()
        {
            annotationDepth = 0;
            annotationFrom = position();
            decoder = decodeAnnotation;
        }

        private int decodeAnnotation(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            while (decoder == decodeAnnotation && hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                final boolean deferred = deferredScalar(event);

                annotationDepth += nesting(event);

                if (deferred)
                {
                    parser.consumed(deferredLength(event));
                }
                else if (annotationDepth == 0)
                {
                    flushAnnotation(position());
                    annotationFrom = NONE;
                    decoder = annotationThen;
                }
            }

            return position();
        }

        private void flushAnnotation(
            int to)
        {
            if (annotationFrom != NONE && to > annotationFrom)
            {
                appendAnnotation(decodeInput, annotationFrom, to - annotationFrom);
                annotationFrom = to;
            }
        }

        private void appendAnnotation(
            DirectBufferEx source,
            int offset,
            int length)
        {
            if (!parseRejected && !annotations.append(replyId, source, offset, length))
            {
                rejectOutput(MESSAGE_ANNOTATIONS_BOUND);
            }
        }

        private void emitCitations(
            LlmOpenaiResponsesDecoder then)
        {
            annotationThen = then;
            decoder = then;

            if (annotationObjects > 0)
            {
                appendAnnotation(ARRAY_CLOSE, 0, 1);
                annotationObjects = 0;
            }

            if (annotations.held() && !parseRejected)
            {
                if (annotated())
                {
                    annotationAttributes.clear();
                    annotationPipeline().begin(annotationAttributes);
                    openText(LlmSectionType.CITATIONS);
                }

                if (sectionOpen && sectionType == LlmSectionType.CITATIONS)
                {
                    decoder = decodeCitations;
                }
                else
                {
                    annotations.release();
                }
            }
        }

        private boolean annotated()
        {
            final MutableDirectBufferEx held = annotations.buffer();

            boolean found = false;

            for (int index = 0; index < annotations.length() && !found; index++)
            {
                found = held.getByte(index) == '{';
            }

            return found;
        }

        private LlmSectionPipeline annotationPipeline()
        {
            if (annotationPipeline == null)
            {
                annotationPipeline = new LlmSectionPipeline(LlmOpenaiResponsesCitationsToCanonical.array(), transformBuffer);
            }

            return annotationPipeline;
        }

        private int decodeCitations(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            boolean progress = true;

            while (decoder == decodeCitations && progress && !parseRejected)
            {
                final int remaining = annotations.remaining();
                final int take = Math.min(remaining, citationsAllowance());

                progress = take > 0;

                if (progress)
                {
                    final boolean last = take == remaining;

                    if (!annotationPipeline.feed(annotations.buffer(), annotations.offset(), take, last, citationsOutput))
                    {
                        rejectOutput(MESSAGE_ANNOTATIONS_INVALID);
                    }

                    annotations.consumed(take);

                    if (last)
                    {
                        closeSection();
                        annotations.release();
                        decoder = annotationThen;
                    }
                }
            }

            return position();
        }

        private int citationsAllowance()
        {
            return Math.max(0, (Math.min(tailCredit(), encodeMax) - 3 * replyPad - TEXT_SUFFIX.length) / CITATIONS_GROWTH);
        }

        private void putCitations(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                emitSection(buffer, offset, length, false);
            }
        }

        private void cleanupHolds()
        {
            encrypted.release();
            annotations.release();
            annotationObjects = 0;
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
                    annotationSeen = 0;
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
                    emitCitations(decodeContent);
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
                        beginText(LlmSectionType.ASSISTANT_TEXT, decodeContentPart);
                    }
                    else if (MEMBER_REFUSAL.contentEquals(member))
                    {
                        beginText(LlmSectionType.ASSISTANT_REFUSAL, decodeContentPart);
                    }
                    else if (MEMBER_ANNOTATIONS.contentEquals(member))
                    {
                        beginAnnotations(decodeContentPart);
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
                    if (TYPE_PART_DONE.equals(documentType))
                    {
                        emitCitations(decodeRoot);
                    }
                    else
                    {
                        decoder = decodeRoot;
                    }
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
                else if (MEMBER_ANNOTATIONS.contentEquals(parser.getStringView()) &&
                    TYPE_PART_DONE.equals(documentType) && annotationSeen == 0)
                {
                    beginAnnotations(decodePart);
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

                if (value != null)
                {
                    onPartType(value);
                }
            }

            return position();
        }

        private void onPartType(
            String value)
        {
            final boolean added = TYPE_PART_ADDED.equals(documentType);

            if (PART_OUTPUT_TEXT.equals(value))
            {
                if (added)
                {
                    annotationSeen = 0;
                    openText(LlmSectionType.ASSISTANT_TEXT);
                }
            }
            else if (PART_REFUSAL.equals(value))
            {
                if (added)
                {
                    openText(LlmSectionType.ASSISTANT_REFUSAL);
                }
            }
            else if (PART_SUMMARY_TEXT.equals(value) || PART_REASONING_TEXT.equals(value))
            {
                if (added || TYPE_SUMMARY_PART_ADDED.equals(documentType))
                {
                    openReasoningPart();
                }
            }
            else
            {
                rejectOutput(MESSAGE_PART_UNSUPPORTED.formatted(value));
            }
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
                    final boolean deferred = parser.deferredBytes();

                    usagePath.append('.').append(usageKey);
                    decodeUsageNumber(usagePath.toString(), deferred);
                    usagePath.setLength(length);
                    usageKey = deferred ? usageKey : null;
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
            LlmSectionType type,
            LlmOpenaiResponsesDecoder then)
        {
            textType = type;
            textMember = null;
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

            if (finished && !http.framing && textType != LlmSectionType.ASSISTANT_REASONING)
            {
                if (!sectionOpen)
                {
                    openText(textType);
                }

                closeText();
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

            int cost = headerPending() ? memberHeaderLength() + replyPad : sectionOpen ? 0 : TEXT_PREFIX.length + replyPad;
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
                final boolean closes = last && !deferred && !http.framing && textType != LlmSectionType.ASSISTANT_REASONING;

                fits = next + (closes ? TEXT_SUFFIX.length + replyPad : 0) <= credit;

                if (fits)
                {
                    cost = next;
                    index += width;
                    take = index;
                }
            }

            if (length == 0 && !deferred && !http.framing && textType != LlmSectionType.ASSISTANT_REASONING &&
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
                openText(textType);
            }

            if (sectionOpen)
            {
                if (headerPending())
                {
                    emitMemberHeader();
                }

                final int encoded = encodePiece(buffer, offset, length);

                emitFrame(pieceBuffer, 0, encoded, false);
            }
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
            sectionMember = null;
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
            deltaItem = null;
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
                    cleanupHeldSlot();
                    cleanupHolds();
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
                cleanupHeldSlot();
                cleanupHolds();
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
            cleanupHeldSlot();
            cleanupHolds();
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
        private String messageToolId;
        private boolean inputOpened;
        private boolean inputClosed;
        private boolean toolsOpened;
        private boolean toolsClosed;
        private String pendingItemType;
        private String pendingItemId;
        private LlmSectionPipeline toolDefinitionPipeline;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline serverToolPipeline;
        private LlmSectionPipeline imagePipeline;
        private LlmSectionPipeline documentPipeline;
        private LlmSectionPipeline audioPipeline;
        private LlmSectionPipeline citationsPipeline;
        private LlmSectionPipeline reasoningPipeline;
        private LlmOpenaiResponsesServerToolFromCanonical serverToolTransform;
        private final CallOutput callOutput = new CallOutput();
        private final ContinuationOutput continuationOutput = new ContinuationOutput();
        private boolean translated;
        private boolean sectionStreaming;
        private LlmSectionPipeline sectionPipeline;
        private LlmSectionPipeline.Output sectionOutput;
        private LlmSectionPipeline controlPipeline;
        private LlmOpenaiResponsesControlFromCanonical controlTransform;
        private LlmModelCapability capability = LlmModelCapability.UNSPECIFIED;
        private int effort = LlmEffort.ABSENT;
        private int budget = LlmEffort.ABSENT;
        private int controlMessage;
        private LlmTextPipeline encodeText;
        private final LlmTextPipeline sectionText = new LlmTextPipeline(this::onSectionText);
        private final LlmTextPipeline reasoningText = new LlmTextPipeline(this::onReasoningText, true);
        private final LlmEdgeFilter reasoningMembers =
            new LlmEdgeFilter((buffer, offset, length, last) -> putReasoningMembers(buffer, offset, length));
        private final LlmSectionPipeline.Output rawOutput = (buffer, offset, length, last) -> putRaw(buffer, offset, length);
        private final LlmMemberCollector collector = new LlmMemberCollector(CONTAINERS, this::putControl, this::containerBuffer);
        private final LlmMembersOutput controlOutput = new LlmMembersOutput(collector);
        private final LlmSectionPipeline.Output discardOutput = (buffer, offset, length, last) ->
        {
        };
        private final LlmMembersOutput.Members containerOutput = this::putContainer;
        private int containerSlot = NO_SLOT;
        private String messageRole;
        private int contentState;
        private int reasoningState;
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
            return switch (kind)
            {
            case KIND_CONTROL -> controlPipeline();
            case KIND_TOOL_DEFINITION -> toolDefinitionPipeline();
            case KIND_TOOL_CALL -> toolCallPipeline();
            case KIND_SERVER_TOOL_CALL, KIND_SERVER_TOOL_RESULT -> serverToolPipeline();
            case KIND_USER_IMAGE -> imagePipeline();
            case KIND_USER_DOCUMENT -> documentPipeline();
            case KIND_USER_AUDIO -> audioPipeline();
            case KIND_CITATIONS -> citationsPipeline();
            default -> null;
            };
        }

        private LlmSectionPipeline imagePipeline()
        {
            if (imagePipeline == null)
            {
                imagePipeline = new LlmSectionPipeline(LlmOpenaiResponsesMediaFromCanonical.image(sameDialect), transformBuffer);
            }

            return imagePipeline;
        }

        private LlmSectionPipeline documentPipeline()
        {
            if (documentPipeline == null)
            {
                documentPipeline = new LlmSectionPipeline(LlmOpenaiResponsesMediaFromCanonical.document(sameDialect),
                    transformBuffer);
            }

            return documentPipeline;
        }

        private LlmSectionPipeline audioPipeline()
        {
            if (audioPipeline == null)
            {
                audioPipeline = new LlmSectionPipeline(LlmOpenaiMediaFromCanonical.audio(sameDialect), transformBuffer);
            }

            return audioPipeline;
        }

        private LlmSectionPipeline citationsPipeline()
        {
            if (citationsPipeline == null)
            {
                citationsPipeline = new LlmSectionPipeline(LlmOpenaiResponsesCitationsFromCanonical.array(sameDialect),
                    transformBuffer);
            }

            return citationsPipeline;
        }

        private LlmSectionPipeline reasoningPipeline()
        {
            if (reasoningPipeline == null)
            {
                reasoningPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesReasoningFromCanonical(sameDialect),
                    transformBuffer);
            }

            return reasoningPipeline;
        }

        private LlmSectionPipeline toolDefinitionPipeline()
        {
            if (toolDefinitionPipeline == null)
            {
                toolDefinitionPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesToolDefinitionFromCanonical(sameDialect),
                    transformBuffer);
            }

            return toolDefinitionPipeline;
        }

        private LlmSectionPipeline toolCallPipeline()
        {
            if (toolCallPipeline == null)
            {
                toolCallPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesToolCallFromCanonical(sameDialect),
                    transformBuffer);
            }

            return toolCallPipeline;
        }

        private LlmSectionPipeline serverToolPipeline()
        {
            if (serverToolPipeline == null)
            {
                serverToolTransform = new LlmOpenaiResponsesServerToolFromCanonical(sameDialect);
                serverToolPipeline = new LlmSectionPipeline(serverToolTransform, transformBuffer);
            }

            return serverToolPipeline;
        }

        private String rejectedMember()
        {
            final String shared = controlTransform != null ? controlTransform.rejectedMember() : null;

            return collector.overflowed() ? collector.overflow() : shared != null ? SHARED_PREFIX + shared : null;
        }

        private int containerReserve()
        {
            return collector.held();
        }

        private MutableDirectBufferEx containerBuffer()
        {
            if (containerSlot == NO_SLOT)
            {
                containerSlot = containerPool.acquire(initialId);
            }

            return containerSlot != NO_SLOT ? containerPool.buffer(containerSlot) : null;
        }

        private void cleanupContainerSlot()
        {
            collector.reset();

            if (containerSlot != NO_SLOT)
            {
                containerPool.release(containerSlot);
                containerSlot = NO_SLOT;
            }
        }

        private void putContainer(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            generator.writeMembers(buffer, offset, length, Completion.COMPLETE);
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                capability = stream.binding.capability(stream.model);
                controlTransform = LlmOpenaiResponsesControlFromCanonical.members(sameDialect, capability);
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

        private void onReasoningText(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (reasoningState == REASONING_SUMMARY)
            {
                if (!sectionArrayPart)
                {
                    generator.writeStartObject().write(MEMBER_TYPE, PART_SUMMARY_TEXT).writeKey(MEMBER_TEXT);
                    generator.writeStartEscaped();
                    sectionArrayPart = true;
                }

                putEscaped(buffer, offset, length);
            }
            else
            {
                encodeFailed = true;
            }
        }

        private void putReasoningMembers(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (reasoningState == REASONING_SUMMARY)
                {
                    closeSummaryPart();
                    generator.writeEnd();
                    putRaw(ITEM_SEPARATOR, 0, ITEM_SEPARATOR.capacity());
                    reasoningState = REASONING_MEMBERS;
                }

                putRaw(buffer, offset, length);
            }
        }

        private void closeSummaryPart()
        {
            if (sectionArrayPart)
            {
                generator.writeEndEscaped().writeEnd();
                sectionArrayPart = false;
            }
        }

        private int modelRoom()
        {
            return stream.model != null ? stream.model.length() * ESCAPE_EXPANSION : 0;
        }

        private boolean annotatable(
            int message)
        {
            return message == messageIndex &&
                ROLE_ASSISTANT.equals(messageRole) &&
                (contentState == CONTENT_HELD || contentState == CONTENT_PART);
        }

        private void doEncodeSectionBegin(
            int requested,
            int message,
            String toolCallId,
            String itemType)
        {
            final int kind = requested == KIND_CITATIONS && !annotatable(message) ? KIND_DROPPED : requested;
            final boolean continuation = kind == KIND_SERVER_TOOL_RESULT &&
                pendingItemType != null &&
                pendingItemType.equals(itemType) &&
                pendingItemId != null &&
                pendingItemId.equals(toolCallId);
            final boolean summarizing = kind == KIND_ASSISTANT_REASONING && reasoningState == REASONING_SUMMARY;
            final boolean instructions = kind == KIND_SYSTEM_INSTRUCTION && instructionsMessage == NO_MESSAGE;
            final boolean regional = kind == KIND_CONTROL ||
                kind == KIND_TOOL_DEFINITION ||
                kind == KIND_SERVER_TOOL_DEFINITION;

            if (kind != KIND_DROPPED && !continuation && !summarizing)
            {
                closePendingItem();
            }

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
            else if (kind == 0 || !regional && !instructions && inputClosed)
            {
                encodeFailed = true;
            }
            else if (beginEncode(ENCODE_STRUCTURE + sectionRoom(toolCallId, itemType)))
            {
                if (kind == KIND_CONTROL)
                {
                    controlMessage = message;
                }
                else if (kind == KIND_TOOL_DEFINITION || kind == KIND_SERVER_TOOL_DEFINITION)
                {
                    beginToolDefinition();
                }
                else if (instructions)
                {
                    beginInstructions(message);
                }
                else if (kind == KIND_ASSISTANT_REASONING)
                {
                    beginReasoning(summarizing);
                }
                else if (item(kind))
                {
                    beginItem(continuation);
                }
                else if (kind == KIND_CITATIONS)
                {
                    beginCitations();
                }
                else
                {
                    ensureMessage(kind, message, toolCallId);

                    if (part(kind))
                    {
                        beginPart();
                    }
                    else
                    {
                        beginText(kind);
                    }
                }

                if (translated && !encodeFailed)
                {
                    beginTranslated(kind, itemType, continuation);
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
            String toolCallId,
            String itemType)
        {
            return (length(toolCallId) + length(itemType) + length(sectionAttributes.mediaType()) +
                length(sectionAttributes.refId())) * ESCAPE_EXPANSION;
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
            int kind,
            String itemType,
            boolean continuation)
        {
            switch (kind)
            {
            case KIND_CONTROL:
                collector.begin();
                controlOutput.begin();
                beginSectionStreaming(pipelineFor(kind), controlMessage == NONE ? controlOutput : discardOutput);
                break;
            case KIND_ASSISTANT_REASONING:
                reasoningText.begin();
                reasoningPipeline().begin(sectionAttributes);
                reasoningMembers.reset();
                break;
            case KIND_SERVER_TOOL_CALL:
                callOutput.begin();
                serverToolPipeline();
                serverToolTransform.configure(LlmOpenaiResponsesServerToolFromCanonical.PART_CALL, itemType, null);
                beginSectionStreaming(serverToolPipeline, callOutput);
                break;
            case KIND_SERVER_TOOL_RESULT:
                continuationOutput.begin();
                serverToolPipeline();
                serverToolTransform.configure(continuation
                    ? LlmOpenaiResponsesServerToolFromCanonical.PART_RESULT
                    : LlmOpenaiResponsesServerToolFromCanonical.PART_OUTPUT, itemType, null);
                beginSectionStreaming(serverToolPipeline, continuation ? continuationOutput : rawOutput);
                break;
            default:
                beginSectionStreaming(pipelineFor(kind), rawOutput);
                break;
            }
        }

        private void beginToolDefinition()
        {
            closeMessage();

            if (inputOpened && !inputClosed)
            {
                generator.writeEnd();
                inputClosed = true;
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
                    toolsOpened = true;
                }

                sectionOpen = false;
            }
        }

        private void beginItem(
            boolean continuation)
        {
            if (continuation)
            {
                pendingItemType = null;
                pendingItemId = null;
            }
            else
            {
                openInput();
                closeMessage();
            }
        }

        private void beginReasoning(
            boolean summarizing)
        {
            if (!summarizing)
            {
                openInput();
                closeMessage();

                generator.writeStartObject().write(MEMBER_TYPE, TYPE_REASONING).writeStartArray(MEMBER_SUMMARY);
                pendingItemType = TYPE_REASONING;
                reasoningState = REASONING_SUMMARY;
            }
        }

        private void closePendingItem()
        {
            if (pendingItemType != null && beginEncode(ENCODE_STRUCTURE))
            {
                if (reasoningState == REASONING_SUMMARY)
                {
                    generator.writeEnd().writeEnd();
                    reasoningState = REASONING_CLOSED;
                }
                else
                {
                    generator.writeSegment(OBJECT_CLOSE, 0, OBJECT_CLOSE.capacity(), Completion.INCOMPLETE);
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                    rawOpen = false;
                }

                pendingItemType = null;
                pendingItemId = null;
                endEncode();
            }
        }

        private void endReasoning()
        {
            closeSummaryPart();

            if (reasoningState == REASONING_MEMBERS)
            {
                generator.writeEnd();
                reasoningState = REASONING_CLOSED;
                pendingItemType = null;
            }
        }

        private void beginCitations()
        {
            if (contentState == CONTENT_HELD)
            {
                commitPart();
            }

            generator.writeKey(MEMBER_ANNOTATIONS);
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

        private final class CallOutput implements LlmSectionPipeline.Output
        {
            private final UnsafeBufferEx tailBuffer = new UnsafeBufferEx(new byte[1]);

            private int tail = NONE;

            @Override
            public void put(
                DirectBufferEx buffer,
                int offset,
                int length,
                boolean last)
            {
                if (length > 0)
                {
                    finish();

                    if (length > 1)
                    {
                        putRaw(buffer, offset, length - 1);
                    }

                    tail = buffer.getByte(offset + length - 1);
                }
            }

            private void begin()
            {
                tail = NONE;
            }

            private void finish()
            {
                if (tail != NONE)
                {
                    tailBuffer.putByte(0, (byte) tail);
                    tail = NONE;
                    putRaw(tailBuffer, 0, 1);
                }
            }

            private void discard()
            {
                tail = NONE;
            }
        }

        private final class ContinuationOutput implements LlmSectionPipeline.Output
        {
            private boolean started;

            @Override
            public void put(
                DirectBufferEx buffer,
                int offset,
                int length,
                boolean last)
            {
                if (length > 0)
                {
                    if (started)
                    {
                        putRaw(buffer, offset, length);
                    }
                    else
                    {
                        started = true;
                        putRaw(ITEM_SEPARATOR, 0, ITEM_SEPARATOR.capacity());

                        if (length > 1)
                        {
                            putRaw(buffer, offset + 1, length - 1);
                        }
                    }
                }
            }

            private void begin()
            {
                started = false;
            }
        }

        private void putControl(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (controlMessage == NONE)
            {
                if (!rawOpen)
                {
                    openControl();
                }

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

        private void openControl()
        {
            closeMessage();
            closeRegions();
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
            if (toolsOpened && !toolsClosed)
            {
                generator.writeEnd();
                toolsClosed = true;
            }

            if (!inputOpened)
            {
                generator.writeStartArray(MEMBER_INPUT);
                inputOpened = true;
            }
        }

        private void ensureMessage(
            int kind,
            int message,
            String toolCallId)
        {
            openInput();

            final String role = role(kind);

            if (message != messageIndex ||
                !role.equals(messageRole) ||
                kind == KIND_TOOL_RESULT && (toolCallId == null || !toolCallId.equals(messageToolId)))
            {
                closeMessage();
                openMessage(kind, role, message, toolCallId);
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
                    if (!sectionPipeline.feed(buffer, offset, length, fin, sectionOutput) || collector.overflowed())
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
            else if (translated && sectionKind == KIND_ASSISTANT_REASONING)
            {
                feedReasoning(buffer, offset, length, fin);
            }
            else if (!translated && sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(0))
            {
                putRaw(buffer, offset, length);
                endEncode();
            }
        }

        private void feedReasoning(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean fin)
        {
            if (beginEncode(ENCODE_STRUCTURE))
            {
                if (!reasoningText.feed(buffer, offset, length, fin) ||
                    !reasoningPipeline.feed(buffer, offset, length, fin, reasoningMembers))
                {
                    encodeFailed = true;
                }

                endEncode();
            }
        }

        private boolean streaming()
        {
            return sectionStreaming || encodeText != null || translated && sectionKind == KIND_ASSISTANT_REASONING;
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

        private void onControlEnd()
        {
            if (controlTransform.effort() != LlmEffort.ABSENT)
            {
                effort = controlTransform.effort();
            }

            if (controlTransform.budget() != LlmEffort.ABSENT)
            {
                budget = controlTransform.budget();
            }

            final int exclusiveEffort = LlmReasoning.exclusiveEffort(capability, effort, budget);
            budget = LlmReasoning.exclusiveBudget(capability, effort, budget);
            effort = exclusiveEffort;
        }

        private void doEncodeSectionEnd()
        {
            if (sectionKind == KIND_CONTROL && sectionStreaming)
            {
                onControlEnd();
            }

            encodeText = null;

            if (sectionKind != 0 && sectionKind != KIND_DROPPED && beginEncode(ENCODE_STRUCTURE))
            {
                sectionStreaming = false;

                translated = false;

                if (rawOpen)
                {
                    endRaw();
                }

                if (sectionKind == KIND_ASSISTANT_REASONING)
                {
                    endReasoning();
                }
                else if (sectionKind == KIND_CITATIONS)
                {
                    generator.writeEnd();
                    contentState = CONTENT_ARRAY;
                }

                if (sectionOpen)
                {
                    if (sectionArrayPart)
                    {
                        generator.writeEndEscaped();

                        if (sectionKind == KIND_ASSISTANT_TEXT)
                        {
                            contentState = CONTENT_PART;
                        }
                        else
                        {
                            generator.writeEnd();
                        }
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

        private void endRaw()
        {
            if (sectionKind == KIND_SERVER_TOOL_CALL && LlmOpenaiResponsesItems.split(stream.sectionExtension))
            {
                callOutput.discard();
                pendingItemType = stream.sectionExtension;
                pendingItemId = sectionAttributes.toolCallId();
            }
            else
            {
                callOutput.finish();
                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                rawOpen = false;
            }
        }

        private void doEncodeRequestEnd(
            int maxOutputTokens)
        {
            if (!LlmState.initialClosing(state) && !LlmState.initialClosed(state))
            {
                closePendingItem();

                if (beginEncode(ENCODE_STRUCTURE + ENCODE_MAX_OUTPUT_TOKENS + ENCODE_CONTAINER + collector.held()))
                {
                    closeMessage();
                    closeRegions();

                    final String reasoning = LlmOpenaiReasoning.effort(capability, effort, budget);

                    if (reasoning != null || collector.holds(CONTAINER_REASONING))
                    {
                        generator.writeStartObject(MEMBER_REASONING);

                        if (reasoning != null)
                        {
                            generator.write(MEMBER_EFFORT, reasoning);
                        }

                        collector.drain(CONTAINER_REASONING, containerOutput);
                        generator.writeEnd();
                    }

                    if (collector.holds(CONTAINER_TEXT))
                    {
                        generator.writeStartObject(MEMBER_TEXT);
                        collector.drain(CONTAINER_TEXT, containerOutput);
                        generator.writeEnd();
                    }

                    if (maxOutputTokens != NO_MAX_OUTPUT_TOKENS)
                    {
                        generator.write(MEMBER_MAX_OUTPUT_TOKENS, maxOutputTokens);
                    }

                    generator.writeEnd();
                    endEncode();
                }

                cleanupContainerSlot();

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

            if (inputOpened && !inputClosed)
            {
                generator.writeEnd();
                inputClosed = true;
            }
        }

        private void openMessage(
            int kind,
            String role,
            int message,
            String toolCallId)
        {
            generator.writeStartObject();

            messageIndex = message;
            contentState = CONTENT_NONE;

            if (kind == KIND_TOOL_RESULT)
            {
                if (toolCallId == null)
                {
                    encodeFailed = true;
                }
                else
                {
                    generator.write(MEMBER_TYPE, TYPE_FUNCTION_OUTPUT);
                    generator.write(MEMBER_CALL_ID, toolCallId);
                }
            }
            else
            {
                generator.write(MEMBER_ROLE, role);
            }

            messageRole = role;
            messageToolId = kind == KIND_TOOL_RESULT ? toolCallId : null;
        }

        private void closeMessage()
        {
            if (messageIndex != NO_MESSAGE)
            {
                closeContent();

                generator.writeEnd();
                messageIndex = NO_MESSAGE;
                messageRole = null;
                messageToolId = null;
            }
        }

        private String contentKey()
        {
            return messageToolId != null ? MEMBER_OUTPUT : MEMBER_CONTENT;
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
            else if (contentState == CONTENT_PART)
            {
                generator.writeEnd().writeEnd();
            }

            contentState = CONTENT_CLOSED;
        }

        private void beginText(
            int kind)
        {
            switch (contentState)
            {
            case CONTENT_NONE:
                if (kind == KIND_ASSISTANT_REFUSAL)
                {
                    generator.writeStartArray(contentKey());
                    contentState = CONTENT_ARRAY;
                    beginTextPart(kind);
                }
                else
                {
                    holdSlot = stagePool.acquire(initialId);
                    holdLength = 0;
                    encodeFailed |= holdSlot == NO_SLOT;
                    contentState = CONTENT_HELD;
                }
                break;
            case CONTENT_HELD:
                commitArray(false);
                beginTextPart(kind);
                break;
            case CONTENT_ARRAY:
                beginTextPart(kind);
                break;
            case CONTENT_PART:
                generator.writeEnd();
                contentState = CONTENT_ARRAY;
                beginTextPart(kind);
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
                generator.writeStartArray(contentKey());
                contentState = CONTENT_ARRAY;
                break;
            case CONTENT_HELD:
                commitArray(false);
                break;
            case CONTENT_ARRAY:
                break;
            case CONTENT_PART:
                generator.writeEnd();
                contentState = CONTENT_ARRAY;
                break;
            default:
                encodeFailed = true;
                break;
            }
        }

        private void beginTextPart(
            int kind)
        {
            final boolean refusal = kind == KIND_ASSISTANT_REFUSAL;

            generator.writeStartObject()
                .write(MEMBER_TYPE, refusal ? PART_REFUSAL : partType())
                .writeKey(refusal ? MEMBER_REFUSAL : MEMBER_TEXT);
            generator.writeStartEscaped();
            sectionArrayPart = true;
        }

        private String partType()
        {
            return ROLE_ASSISTANT.equals(messageRole) ? PART_OUTPUT_TEXT : PART_INPUT_TEXT;
        }

        private void commitPart()
        {
            generator.writeStartArray(contentKey());
            generator.writeStartObject().write(MEMBER_TYPE, PART_OUTPUT_TEXT).writeKey(MEMBER_TEXT);
            generator.writeStartEscaped();

            if (holdLength > 0)
            {
                generator.writeSegment(stagePool.buffer(holdSlot), 0, holdLength);
            }

            generator.writeEndEscaped();
            releaseHold();
            contentState = CONTENT_PART;
        }

        private void commitString()
        {
            generator.writeKey(contentKey());
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
            generator.writeStartArray(contentKey());
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
            if (!LlmState.replyClosed(state) && (stream.failed() || !stream.itemsPending()))
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
            cleanupContainerSlot();
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

    private static final class ResponseItem
    {
        private int serial;
        private String id;
        private String type;
        private int kind;
        private String callId;
        private String name;
        private int mode;
        private int flags;
        private int held;
        private String openedToolId;
        private String openedToolName;

        private void begin(
            int serial,
            String id,
            String type,
            int kind,
            int mode)
        {
            this.serial = serial;
            this.id = id;
            this.type = type;
            this.kind = kind;
            this.mode = mode;
            this.callId = null;
            this.name = null;
            this.flags = 0;
            this.held = 0;
            this.openedToolId = null;
            this.openedToolName = null;
        }

        private boolean has(
            int flag)
        {
            return (flags & flag) != 0;
        }

        private void set(
            int flag)
        {
            flags |= flag;
        }

        private void clear(
            int flag)
        {
            flags &= ~flag;
        }

        private String toolCallId()
        {
            return LlmOpenaiResponsesItems.hasCallId(type) ? callId : id;
        }

        private String toolName()
        {
            return name != null || LlmOpenaiResponsesItems.hasName(type) ? name : LlmOpenaiResponsesItems.toolName(type);
        }

        private int part()
        {
            return has(FLAG_RESULT) ? PART_RESULT : PART_CALL;
        }

        private boolean ready(
            int part)
        {
            return toolCallId() != null && (part == PART_RESULT || toolName() != null);
        }

        private boolean escaped()
        {
            return kind != LlmOpenaiResponsesItems.KIND_FUNCTION_CALL;
        }
    }

    private String mediaType(
        String contentType)
    {
        contentTypeMatcher.reset(contentType != null ? contentType : "");

        return contentTypeMatcher.matches() ? contentTypeMatcher.group(1) : null;
    }
}
