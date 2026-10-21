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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObjectBuilder;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.llm.config.LlmAuthorizationResult;
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
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmOpenaiResponsesServerFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final int QUEUE_ENTRY_OVERHEAD = 5 * Integer.BYTES + 3 * Long.BYTES + Byte.BYTES;
    private static final int EVENT_ATTRIBUTES_CAPACITY = 2048;
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String METHOD_POST = "POST";
    private static final String PATH_RESPONSES = "/v1/responses";
    private static final String VENDOR = LlmDialectNames.vendor(NAME);
    private static final String VENDOR_PREFIX = VENDOR + ":";
    private static final String STATUS_OK = "200";
    private static final String STATUS_UNAUTHORIZED = "401";
    private static final int NO_MAX_OUTPUT_TOKENS = -1;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String UNAUTHORIZED_BODY = "{\"error\":{\"message\":\"Incorrect API key provided.\"," +
        "\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_api_key\"}}";

    private static final String MESSAGE_PARSE_ERROR = "We could not parse the JSON body of your request.";
    private static final String MESSAGE_MODEL_MISSING = "Missing required parameter: 'model'.";
    private static final String MESSAGE_MODEL_ORDER =
        "The 'model' parameter must precede 'input' in a request that does not fit the decode buffer.";
    private static final String MESSAGE_ROLE_MISSING = "Missing required parameter: 'role'.";
    private static final String MESSAGE_ROLE_ORDER = "The 'role' parameter must precede 'content' in each input item.";
    private static final String MESSAGE_PART_TYPE_ORDER = "The 'type' parameter must be the first member of each content part.";
    private static final String MESSAGE_FORMAT_INVALID =
        "The 'text.format' parameter is invalid, or its 'type' follows members that are too large.";
    private static final String MESSAGE_ITEM_UNSUPPORTED = "Unsupported input item type: '%s'.";
    private static final String MESSAGE_PART_UNSUPPORTED = "Unsupported content part type: '%s'.";
    private static final String MESSAGE_CALL_ID_ORDER =
        "The 'call_id' parameter must precede 'output' in a function call output item.";
    private static final String MESSAGE_ATTRIBUTE_TOO_LARGE = "A tool attribute value is too large.";
    private static final String MESSAGE_TYPE_ORDER =
        "The 'type' parameter must precede 'summary' and 'encrypted_content' in each reasoning item.";
    private static final String MESSAGE_ENCRYPTED_BOUND =
        "The 'encrypted_content' of a reasoning item that precedes its 'summary' is too large.";
    private static final String MESSAGE_ANNOTATIONS_BOUND = "The 'annotations' of an output text part are too large.";
    private static final String MESSAGE_TOOL_TYPE_MISSING = "Missing required parameter: 'type'.";
    private static final int BLOCK_BUFFER_MAX = 4096;

    private static final int RESPONSE_ENCODE_PADDING = 128;
    private static final int RESPONSE_ESCAPE_EXPANSION = 6;
    private static final int ESCAPE_EXPANSION = 6;
    private static final int MIN_TEXT_INPUT = "\"content\":\"\"".length();
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_REASONING = "reasoning";
    private static final String MEMBER_REFUSAL = "refusal";
    private static final String MEMBER_SUMMARY = "summary";
    private static final String MEMBER_ENCRYPTED = "encrypted_content";
    private static final String TYPE_REFUSAL = "refusal";
    private static final String TYPE_INPUT_IMAGE = "input_image";
    private static final String TYPE_INPUT_FILE = "input_file";
    private static final String TYPE_INPUT_AUDIO = "input_audio";
    private static final byte[] ENCRYPTED_KEY = "\"openai:encrypted_content\":\"".getBytes(UTF_8);
    private static final String ID_MEMBER = VENDOR_PREFIX + "id";
    private static final int EMPTY_ARRAY_LENGTH = 2;
    private static final byte[] TEXT_PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte[] TEXT_SUFFIX = "\"}".getBytes(UTF_8);
    private static final int RESPONSE_ENCODE_SLACK = 2048;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx STREAM_OPTIONS_SECTION =
        new UnsafeBufferEx("{\"stream_options\":{\"include_usage\":true}}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CLOSE = new UnsafeBufferEx("}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_BEGIN = new UnsafeBufferEx("{".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CONTINUE = new UnsafeBufferEx(",".getBytes(UTF_8));
    private static final String SHARED_PREFIX = "x:";
    private static final DirectBufferEx EMPTY_VALUE = new UnsafeBufferEx(new byte[0]);
    private static final DirectBufferEx OBJECT_OPEN = new UnsafeBufferEx("{".getBytes(UTF_8));
    private static final DirectBufferEx STRING_CLOSE = new UnsafeBufferEx("\"".getBytes(UTF_8));

    private static final int MIN_WINDOW = 64;
    private static final int CONTROL_GROWTH = 3;
    private static final int CONTROL_OPEN_CAPACITY = 8;
    private static final int HOLD_MAX = 1024;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final int NONE = -1;

    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_TOOL_CHOICE = "tool_choice";
    private static final String MEMBER_SERVICE_TIER = "service_tier";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ROLE = "role";
    private static final String MEMBER_CONTENT = "content";
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_INPUT_TEXT = "input_text";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String MEMBER_OUTPUT = "output";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_CALL_ID = "call_id";
    private static final String MEMBER_OUTPUT_ITEM = "output";
    private static final String MEMBER_OBJECT = "object";
    private static final String MEMBER_MODEL = "model";
    private static final String MEMBER_STATUS = "status";
    private static final String MEMBER_ITEM = "item";
    private static final String MEMBER_ITEM_ID = "item_id";
    private static final String MEMBER_PART = "part";
    private static final String MEMBER_DELTA = "delta";
    private static final String MEMBER_CONTENT_INDEX = "content_index";
    private static final String MEMBER_OUTPUT_INDEX = "output_index";
    private static final String MEMBER_SEQUENCE_NUMBER = "sequence_number";
    private static final String MEMBER_ANNOTATIONS = "annotations";
    private static final String MEMBER_LOGPROBS = "logprobs";
    private static final String MEMBER_INCOMPLETE_DETAILS = "incomplete_details";
    private static final String MEMBER_REASON = "reason";
    private static final String MEMBER_CODE = "code";
    private static final String MEMBER_MESSAGE = "message";
    private static final String MEMBER_PARAM = "param";
    private static final String MEMBER_ERROR = "error";
    private static final String ROLE_ASSISTANT = "assistant";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpDataExFW.Builder httpDataExRW = new HttpDataExFW.Builder();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmChallengeExFW llmChallengeExRO = new LlmChallengeExFW();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();
    private final LlmSectionAttributes replyAttributes = new LlmSectionAttributes();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmFlushExFW.Builder llmFlushExRW = new LlmFlushExFW.Builder();
    private final LlmResetExFW llmResetExRO = new LlmResetExFW();
    private final LlmEndExFW llmEndExRO = new LlmEndExFW();
    private final LlmAbortExFW llmAbortExRO = new LlmAbortExFW();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final Matcher contentTypeMatcher;
    private final MutableDirectBufferEx copyBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BufferPool holdPool;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;
    private final int decodeMax;
    private final int llmTypeId;
    private final int httpTypeId;

    private final LlmOpenaiResponsesServerDecoder decodeStart = this::decodeStart;
    private final LlmOpenaiResponsesServerDecoder decodeRootStart = this::decodeRootStart;
    private final LlmOpenaiResponsesServerDecoder decodeModelMember = this::decodeModelMember;
    private final LlmOpenaiResponsesServerDecoder decodeModelValue = this::decodeModelValue;
    private final LlmOpenaiResponsesServerDecoder decodeRoot = this::decodeRoot;
    private final LlmOpenaiResponsesServerDecoder decodeRootControl = this::decodeRootControl;
    private final LlmOpenaiResponsesServerDecoder decodeInstructions = this::decodeInstructions;
    private final LlmOpenaiResponsesServerDecoder decodeInput = this::decodeInput;
    private final LlmOpenaiResponsesServerDecoder decodeItems = this::decodeItems;
    private final LlmOpenaiResponsesServerDecoder decodeItem = this::decodeItem;
    private final LlmOpenaiResponsesServerDecoder decodeItemType = this::decodeItemType;
    private final LlmOpenaiResponsesServerDecoder decodeItemRole = this::decodeItemRole;
    private final LlmOpenaiResponsesServerDecoder decodeItemContent = this::decodeItemContent;
    private final LlmOpenaiResponsesServerDecoder decodeParts = this::decodeParts;
    private final LlmOpenaiResponsesServerDecoder decodePartType = this::decodePartType;
    private final LlmOpenaiResponsesServerDecoder decodePartTypeValue = this::decodePartTypeValue;
    private final LlmOpenaiResponsesServerDecoder decodePart = this::decodePart;
    private final LlmOpenaiResponsesServerDecoder decodeRefusalPart = this::decodeRefusalPart;
    private final LlmOpenaiResponsesServerDecoder decodeAnnotationsStart = this::decodeAnnotationsStart;
    private final LlmOpenaiResponsesServerDecoder decodeReasoning = this::decodeReasoning;
    private final LlmOpenaiResponsesServerDecoder decodeReasoningId = this::decodeReasoningId;
    private final LlmOpenaiResponsesServerDecoder decodeSummaryStart = this::decodeSummaryStart;
    private final LlmOpenaiResponsesServerDecoder decodeSummary = this::decodeSummary;
    private final LlmOpenaiResponsesServerDecoder decodeReasoningContentStart = this::decodeReasoningContentStart;
    private final LlmOpenaiResponsesServerDecoder decodeReasoningContent = this::decodeReasoningContent;
    private final LlmOpenaiResponsesServerDecoder decodeSummaryPart = this::decodeSummaryPart;
    private final LlmOpenaiResponsesServerDecoder decodeEncryptedStream = this::decodeEncryptedStream;
    private final LlmOpenaiResponsesServerDecoder decodeEncryptedHold = this::decodeEncryptedHold;
    private final LlmOpenaiResponsesServerDecoder decodeTail = this::decodeTail;
    private final LlmOpenaiResponsesServerDecoder decodeToolsStart = this::decodeToolsStart;
    private final LlmOpenaiResponsesServerDecoder decodeTools = this::decodeTools;
    private final LlmOpenaiResponsesServerDecoder decodeTool = this::decodeTool;
    private final LlmOpenaiResponsesServerDecoder decodeToolType = this::decodeToolType;
    private final LlmOpenaiResponsesServerDecoder decodeToolName = this::decodeToolName;
    private final LlmOpenaiResponsesServerDecoder decodeToolCapture = this::decodeToolCapture;
    private final LlmOpenaiResponsesServerDecoder decodeItemId = this::decodeItemId;
    private final LlmOpenaiResponsesServerDecoder decodeItemCapture = this::decodeItemCapture;
    private final LlmOpenaiResponsesServerDecoder decodeAttributes = this::decodeAttributes;
    private final LlmOpenaiResponsesServerDecoder decodeOutputItem = this::decodeOutputItem;
    private final LlmOpenaiResponsesServerDecoder decodeOutputCallId = this::decodeOutputCallId;
    private final LlmOpenaiResponsesServerDecoder decodeOutputValue = this::decodeOutputValue;
    private final LlmOpenaiResponsesServerDecoder decodeText = this::decodeText;
    private final LlmOpenaiResponsesServerDecoder decodeSkip = this::decodeSkip;
    private final LlmOpenaiResponsesServerDecoder decodeControlValue = this::decodeControlValue;
    private final LlmOpenaiResponsesServerDecoder decodeControlSeparator = this::decodeControlSeparator;
    private final LlmOpenaiResponsesServerDecoder decodeMaxOutputTokensValue = this::decodeMaxOutputTokensValue;
    private final LlmOpenaiResponsesServerDecoder decodeEnd = this::decodeEnd;
    private final LlmOpenaiResponsesServerDecoder decodeIgnore = this::decodeIgnore;


    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;

    public LlmOpenaiResponsesServerFactory(
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.contentTypeMatcher = CONTENT_TYPE.matcher("");
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.holdPool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.copyBuffer = new UnsafeBufferEx(new byte[encodePool.slotCapacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bindings = new Long2ObjectHashMap<>();
        this.textBytes = new byte[1024];
        this.textBuffer = new UnsafeBufferEx(textBytes);
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
        final String method = header(headers, HEADER_METHOD);
        final String path = header(headers, HEADER_PATH);
        final String contentType = header(headers, HEADER_CONTENT_TYPE);

        return METHOD_POST.equalsIgnoreCase(method) &&
            PATH_RESPONSES.equals(path) &&
            CONTENT_TYPE_JSON.equals(mediaType(contentType));
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final HttpBeginExFW httpBeginEx = begin.extension().get(httpBeginExRO::tryWrap);

        final LlmBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null && httpBeginEx != null)
        {
            final LlmRequestHeaders headers = LlmRequestHeaders.of(httpBeginEx);
            final String contentType = header(headers, HEADER_CONTENT_TYPE);

            if (!CONTENT_TYPE_JSON.equals(mediaType(contentType)))
            {
                newStream = new LlmOpenaiRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_UNSUPPORTED_MEDIA_TYPE),
                    errorBody(STATUS_UNSUPPORTED_MEDIA_TYPE, null, null, null, null))::onNetMessage;
            }
            else
            {
                final LlmAuthorizationResult authResult = binding.authorize(
                    begin.traceId(), routedId, initialId, authorization, headers, HEADER_AUTHORIZATION);

                if (!authResult.authorized())
                {
                    newStream = new LlmOpenaiRejectHandler(network, originId, routedId, initialId,
                        STATUS_UNAUTHORIZED, UNAUTHORIZED_BODY)::onNetMessage;
                }
                else
                {
                    final LlmRouteConfig route = binding.resolve(authResult.authorization());

                    if (route != null)
                    {
                        newStream = new LlmOpenaiResponsesServer(
                                network,
                                originId,
                                routedId,
                                initialId,
                                route.id,
                                authResult.authorization(),
                                contentType,
                                authResult.deauthorize())::onNetMessage;
                    }
                    else
                    {
                        authResult.deauthorize().run();
                    }
                }
            }
        }

        return newStream;
    }

    private static String reason(
        int status)
    {
        return switch (status)
        {
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 408 -> "Request Timeout";
        case 413 -> "Payload Too Large";
        case 415 -> "Unsupported Media Type";
        case 422 -> "Unprocessable Entity";
        case 429 -> "Too Many Requests";
        case 500 -> "Internal Server Error";
        case 502 -> "Bad Gateway";
        case 503 -> "Service Unavailable";
        case 504 -> "Gateway Timeout";
        default -> "Error";
        };
    }

    private static String header(
        JsonEnvelope headers,
        String name)
    {
        final DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    private static DirectBufferEx asBuffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static String errorBody(
        int status,
        String type,
        String param,
        String code,
        String message)
    {
        final JsonObjectBuilder error = Json.createObjectBuilder()
            .add("message", message != null ? message : reason(status))
            .add("type", type != null ? type : errorType(status));

        if (param != null)
        {
            error.add("param", param);
        }
        else
        {
            error.addNull("param");
        }

        if (code != null)
        {
            error.add("code", code);
        }
        else if (status == 429)
        {
            error.add("code", "rate_limit_exceeded");
        }
        else
        {
            error.addNull("code");
        }

        return Json.createObjectBuilder()
            .add("error", error)
            .build()
            .toString();
    }

    private static String errorType(
        int status)
    {
        return status == 429 ? "requests" : status >= 500 ? "server_error" : "invalid_request_error";
    }

    private static DirectBufferEx eventType(
        String type)
    {
        final HttpDataExFW dataEx = new HttpDataExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[EVENT_ATTRIBUTES_CAPACITY]), 0, EVENT_ATTRIBUTES_CAPACITY)
            .typeId(0)
            .headersItem(h -> h.name("type").value(type))
            .build();
        final Array32FW<HttpHeaderFW> headers = dataEx.headers();
        final byte[] bytes = new byte[headers.sizeof()];

        headers.buffer().getBytes(headers.offset(), bytes);

        return new UnsafeBufferEx(bytes);
    }

    @FunctionalInterface
    private interface LlmOpenaiResponsesServerDecoder
    {
        int decode(
            LlmOpenaiResponsesServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeModelMember(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, "model"))
                {
                    server.decoder = decodeModelValue;
                }
                else
                {
                    server.skip(decodeModelMember);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.onDecodeInvalidRequest(traceId, MESSAGE_MODEL_MISSING);
                break decode;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeModelValue(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                server.onDecodeModel(traceId, authorization, parser.getString());
            }
        }

        return server.position();
    }

    private int decodeStart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_DOCUMENT)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.decoder = decodeRootStart;
        }

        return server.position();
    }

    private int decodeRootStart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_OBJECT)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.decoder = server.lookahead ? decodeModelMember : decodeRoot;
        }

        return server.position();
    }

    private int decodeRoot(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                server.onDecodeRootKey(parser.getStringView());
                break;
            case END_OBJECT:
                server.onDecodeRootEnd(traceId);
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRootControl(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (isRootControl(key))
                {
                    server.controlMore(key, decodeRootControl);
                }
                else
                {
                    server.closeControl(traceId, authorization);
                    server.decoder = decodeRoot;
                    server.onDecodeRootKey(key);
                }
                break;
            case END_OBJECT:
                server.closeControl(traceId, authorization);
                server.onDecodeRootEnd(traceId);
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private boolean isRootControl(
        CharSequence key)
    {
        return !matches(key, "instructions") &&
            !matches(key, "input") &&
            !matches(key, "tools") &&
            !matches(key, "max_output_tokens") &&
            !matches(key, "model") &&
            !matches(key, MEMBER_STREAM) &&
            !matches(key, MEMBER_STREAM_OPTIONS);
    }

    private int decodeInstructions(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.beginInstructions();
                server.text(LlmSectionType.SYSTEM_INSTRUCTION, decodeRoot);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case VALUE_NULL:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeInput(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.beginMessage(null);
                server.text(LlmSectionType.USER_TEXT, decodeRoot);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.decoder = decodeItems;
                break;
            case VALUE_NULL:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeItems(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.role = null;
                server.itemType = null;
                server.itemId = null;
                server.hold();
                server.decoder = decodeItem;
                break;
            case END_ARRAY:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeItem(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, MEMBER_TYPE))
                {
                    server.decoder = decodeItemType;
                }
                else if (matches(key, MEMBER_ROLE))
                {
                    server.decoder = decodeItemRole;
                }
                else if (matches(key, MEMBER_ID))
                {
                    server.decoder = decodeItemId;
                }
                else if (matches(key, MEMBER_CONTENT))
                {
                    if (server.role == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_ORDER);
                        break decode;
                    }
                    server.decoder = decodeItemContent;
                }
                else if ((matches(key, MEMBER_SUMMARY) || matches(key, MEMBER_ENCRYPTED)) &&
                    server.itemType == null && server.role == null)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_TYPE_ORDER);
                    break decode;
                }
                else
                {
                    server.skip(decodeItem);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                if (server.role == null)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_MISSING);
                    break decode;
                }
                server.release();
                server.decoder = decodeItems;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeItemType(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                final String type = parser.getString();
                final int kind = LlmOpenaiResponsesItems.kind(type);

                server.itemType = type;

                if (kind == LlmOpenaiResponsesItems.KIND_MESSAGE)
                {
                    server.release();
                    server.decoder = decodeItem;
                }
                else if (kind == LlmOpenaiResponsesItems.KIND_NONE)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_ITEM_UNSUPPORTED.formatted(type));
                }
                else
                {
                    server.onDecodeItemType(type, kind);
                }
            }
        }

        return server.position();
    }

    private int decodeItemRole(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                final String role = parser.getString();
                final LlmSectionType roleType = roleSectionType(role);

                if (roleType != null)
                {
                    server.role = role;
                    server.roleType = roleType;
                    server.release();
                    server.beginMessage(roleType);
                    server.decoder = decodeItem;
                }
                else
                {
                    server.skipDepth = 1;
                    server.skipThen = decodeItems;
                    server.decoder = decodeSkip;
                }
            }
        }

        return server.position();
    }

    private int decodeItemContent(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.text(server.roleType, decodeItem);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.partsThen = decodeItem;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeItem;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeParts(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.hold();
                server.decoder = decodePartType;
                break;
            case END_ARRAY:
                server.decoder = server.partsThen;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodePartType(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.KEY_NAME)
            {
                server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TYPE_ORDER);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                if (matches(parser.getStringView(), MEMBER_TYPE))
                {
                    server.decoder = decodePartTypeValue;
                }
                else
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TYPE_ORDER);
                }
            }
        }

        return server.position();
    }

    private int decodePartTypeValue(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                final String type = parser.getString();

                final LlmSectionType mediaType = mediaSectionType(type);

                if (TYPE_INPUT_TEXT.equals(type) || TYPE_OUTPUT_TEXT.equals(type))
                {
                    server.release();
                    server.decoder = decodePart;
                }
                else if (TYPE_REFUSAL.equals(type) && server.roleType == LlmSectionType.ASSISTANT_TEXT)
                {
                    server.release();
                    server.decoder = decodeRefusalPart;
                }
                else if (mediaType != null && server.roleType == LlmSectionType.USER_TEXT)
                {
                    server.probe(mediaType, server.itemMessage, decodeToolCapture, decodeParts);
                    server.decoder = decodeAttributes;
                }
                else
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_PART_UNSUPPORTED.formatted(type));
                }
            }
        }

        return server.position();
    }

    private int decodePart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, MEMBER_TEXT))
                {
                    server.text(server.roleType, decodePart);
                    server.decoder = decodeText;
                }
                else if (matches(key, MEMBER_ANNOTATIONS) && server.roleType == LlmSectionType.ASSISTANT_TEXT)
                {
                    server.decoder = decodeAnnotationsStart;
                }
                else
                {
                    server.skip(decodePart);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.onDecodePartEnd();
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRefusalPart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                if (matches(parser.getStringView(), MEMBER_REFUSAL))
                {
                    server.text(LlmSectionType.ASSISTANT_REFUSAL, decodeRefusalPart);
                    server.decoder = decodeText;
                }
                else
                {
                    server.skip(decodeRefusalPart);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = decodeParts;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeAnnotationsStart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_ARRAY:
                server.onDecodeAnnotations(decodePart);
                server.decoder = decodeControlValue;
                break;
            case VALUE_NULL:
                server.decoder = decodePart;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeReasoning(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, MEMBER_ID))
                {
                    server.decoder = decodeReasoningId;
                }
                else if (matches(key, MEMBER_SUMMARY))
                {
                    server.decoder = decodeSummaryStart;
                }
                else if (matches(key, MEMBER_CONTENT))
                {
                    server.decoder = decodeReasoningContentStart;
                }
                else if (matches(key, MEMBER_ENCRYPTED))
                {
                    server.onDecodeEncrypted(traceId);
                    server.decoder = server.reasoningSummaries > 0 ? decodeEncryptedStream : decodeEncryptedHold;
                }
                else
                {
                    server.skip(decodeReasoning);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.onDecodeReasoningEnd(traceId);
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeReasoningId(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final String value = server.decodeString(traceId, decodeReasoning);

        if (value != null)
        {
            server.reasoningId = value;
        }

        return server.position();
    }

    private int decodeSummaryStart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.START_ARRAY)
            {
                server.partsThen = decodeSummary;
                server.decoder = decodeSummary;
            }
            else
            {
                server.onDecodeNonArray(event, decodeReasoning);
                server.reasoningSummaries++;
            }
        }

        return server.position();
    }

    private int decodeSummary(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.decoder = decodeSummaryPart;
                break;
            case END_ARRAY:
                server.reasoningSummaries++;
                server.decoder = decodeReasoning;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeReasoningContentStart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.START_ARRAY)
            {
                server.partsThen = decodeReasoningContent;
                server.decoder = decodeReasoningContent;
            }
            else
            {
                server.onDecodeNonArray(event, decodeReasoning);
            }
        }

        return server.position();
    }

    private int decodeReasoningContent(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.decoder = decodeSummaryPart;
                break;
            case END_ARRAY:
                server.decoder = decodeReasoning;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeSummaryPart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                if (matches(parser.getStringView(), MEMBER_TEXT))
                {
                    server.onDecodeReasoningPart(traceId, authorization);
                    server.decoder = decodeText;
                }
                else
                {
                    server.skip(decodeSummaryPart);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = server.partsThen;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeEncryptedStream(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeEncryptedStream && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING)
            {
                final boolean deferred = parser.deferredBytes();
                final CharSequence chars = parser.getStringView();
                final int length = chars.length();
                final int take = server.onDecodeEncryptedStream(traceId, authorization, chars, !deferred);

                if (take < length)
                {
                    server.onDecodeParseError(traceId);
                }
                else if (deferred)
                {
                    parser.consumed(take);
                }
                else
                {
                    server.onDecodeEncryptedEnd(traceId, authorization);
                    server.decoder = decodeReasoning;
                }
            }
            else if (event == JsonEvent.VALUE_NULL)
            {
                server.onDecodeEncryptedEnd(traceId, authorization);
                server.decoder = decodeReasoning;
            }
            else
            {
                server.onDecodeParseError(traceId);
            }
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeEncryptedHold(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeEncryptedHold && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING)
            {
                final boolean deferred = parser.deferredBytes();
                final CharSequence chars = parser.getStringView();
                final int length = chars.length();

                server.onDecodeEncryptedHold(traceId, chars, !deferred);

                if (deferred)
                {
                    parser.consumed(length);
                }
                else
                {
                    server.onDecodeEncryptedHoldEnd(traceId);
                    server.decoder = decodeReasoning;
                }
            }
            else if (event == JsonEvent.VALUE_NULL)
            {
                server.onDecodeEncryptedHoldEnd(traceId);
                server.decoder = decodeReasoning;
            }
            else
            {
                server.onDecodeParseError(traceId);
            }
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeTail(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        server.onDecodeTail(traceId, authorization);

        return server.position();
    }

    private int decodeToolsStart(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_ARRAY:
                server.decoder = decodeTools;
                break;
            case VALUE_NULL:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeTools(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                server.itemType = null;
                server.itemName = null;
                server.hold();
                server.decoder = decodeTool;
                break;
            case END_ARRAY:
                server.decoder = decodeRoot;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeTool(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, MEMBER_TYPE))
                {
                    server.decoder = decodeToolType;
                }
                else if (matches(key, MEMBER_NAME))
                {
                    server.decoder = decodeToolName;
                }
                else
                {
                    server.skip(decodeTool);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_TYPE_MISSING);
                break decode;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolName(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final String value = server.decodeString(traceId, decodeTool);

        if (value != null)
        {
            server.itemName = value;
        }

        return server.position();
    }

    private int decodeToolType(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.VALUE_STRING)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            if (!parser.deferredBytes())
            {
                server.onDecodeToolType(parser.getString());
            }
        }

        return server.position();
    }

    private int decodeItemId(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final String value = server.decodeString(traceId, decodeItem);

        if (value != null)
        {
            server.itemId = value;
        }

        return server.position();
    }

    private int decodeAttributes(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeAttributes && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            switch (server.probe.onEvent(event, parser))
            {
            case RESOLVED:
                server.captureProbed();
                break;
            case COMPLETE:
                server.captureProbed();
                server.endCapture(traceId, authorization);
                break;
            case REJECTED:
                server.onDecodeInvalidRequest(traceId, MESSAGE_ATTRIBUTE_TOO_LARGE);
                break;
            default:
                if (server.position() - server.holdFrom > HOLD_MAX)
                {
                    server.captureProbed();
                }
                break;
            }
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeItemCapture(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        int eventFrom = server.position();

        while (server.decoder == decodeItemCapture && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (event == JsonEvent.KEY_NAME &&
                server.probe.depth() == 1 &&
                !parser.deferredBytes() &&
                server.blockSection == LlmSectionType.SERVER_TOOL_CALL &&
                LlmOpenaiResponsesItems.resultMember(server.itemType, parser.getStringView()))
            {
                server.onDecodeItemResult(traceId, authorization, eventFrom);
            }

            server.probe.onEvent(event, parser);
            server.captureDepth = server.probe.depth();

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }

            eventFrom = server.position();
        }

        return server.position();
    }

    private int decodeToolCapture(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeToolCapture && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            server.probe.onEvent(event, parser);
            server.captureDepth = server.probe.depth();

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeOutputItem(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (parser.deferredBytes())
                {
                    break;
                }
                final CharSequence key = parser.getStringView();
                if (matches(key, MEMBER_CALL_ID))
                {
                    server.decoder = decodeOutputCallId;
                }
                else if (matches(key, MEMBER_OUTPUT_ITEM))
                {
                    if (server.outputCallId == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_CALL_ID_ORDER);
                        break decode;
                    }
                    server.decoder = decodeOutputValue;
                }
                else
                {
                    server.skip(decodeOutputItem);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                server.decoder = decodeItems;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeOutputCallId(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final String value = server.decodeString(traceId, decodeOutputItem);

        if (value != null)
        {
            server.outputCallId = value;
        }

        return server.position();
    }

    private int decodeOutputValue(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.text(LlmSectionType.TOOL_RESULT, decodeOutputItem);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.roleType = LlmSectionType.TOOL_RESULT;
                server.partsThen = decodeOutputItem;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeOutputItem;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeText(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        int eventFrom = server.position();

        while (server.decoder == decodeText && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);

            if (progress < 0)
            {
                break;
            }

            eventFrom = server.position();
        }

        return server.position();
    }

    private int decodeTextValue(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int progress,
        int limit,
        JsonEvent event,
        int eventFrom)
    {
        final JsonParserEx parser = server.parser;

        boolean stalled = false;

        server.decoder = decodeText;

        if (event == JsonEvent.VALUE_STRING)
        {
            final boolean deferred = parser.deferredBytes();
            final CharSequence chars = parser.getStringView();
            final int length = chars.length();
            final int take = server.onDecodeText(traceId, authorization, chars, !deferred, eventFrom);

            if (!deferred && take == length)
            {
                server.textOpen = false;
                server.decoder = server.textThen;
            }
            else if (take == length)
            {
                parser.consumed(take);
            }
            else
            {
                server.onDecodeParseError(traceId);
            }

            stalled = take < length;
        }
        else if (event == JsonEvent.VALUE_NULL && !server.textOpen)
        {
            server.decoder = server.textThen;
        }
        else
        {
            server.onDecodeParseError(traceId);
        }

        return stalled ? -1 : server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeMaxOutputTokensValue(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_NUMBER:
                if (parser.deferredBytes())
                {
                    parser.consumed(0);
                }
                else
                {
                    server.maxOutputTokens = parser.getInt();
                    server.decoder = decodeRoot;
                }
                break;
            case START_OBJECT:
            case START_ARRAY:
                server.skipThen = decodeRoot;
                server.skipDepth = 1;
                server.decoder = decodeSkip;
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                }
                else
                {
                    server.decoder = decodeRoot;
                }
                break;
            default:
                server.decoder = decodeRoot;
                break;
            }
        }

        return server.position();
    }

    private int decodeControlSeparator(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        server.onDecodeControlSeparator();

        return server.position();
    }

    private int decodeControlValue(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeControlValue && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = (event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER) &&
                parser.deferredBytes();

            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                server.captureDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                server.captureDepth--;
                break;
            case VALUE_STRING, VALUE_NUMBER:
                if (deferred)
                {
                    parser.consumed(parser.getStringView().length());
                }
                break;
            default:
                break;
            }

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }
        }

        return server.position();
    }

    private int decodeSkip(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = server.parser;

        while (server.decoder == decodeSkip && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                server.skipDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                server.skipDepth--;
                break;
            default:
                break;
            }

            final boolean fragment = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (fragment)
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event != JsonEvent.KEY_NAME && server.skipDepth == 0)
            {
                server.decoder = server.skipThen;
            }

        }

        return server.position();
    }

    private static LlmSectionType mediaSectionType(
        String type)
    {
        return switch (type)
        {
        case TYPE_INPUT_IMAGE -> LlmSectionType.USER_IMAGE;
        case TYPE_INPUT_FILE -> LlmSectionType.USER_DOCUMENT;
        case TYPE_INPUT_AUDIO -> LlmSectionType.USER_AUDIO;
        default -> null;
        };
    }

    private static LlmSectionType roleSectionType(
        String role)
    {
        return switch (role)
        {
        case "system", "developer" -> LlmSectionType.SYSTEM_INSTRUCTION;
        case "user" -> LlmSectionType.USER_TEXT;
        case "assistant" -> LlmSectionType.ASSISTANT_TEXT;
        default -> null;
        };
    }


    private int encode(
        LlmOpenaiResponsesServer server,
        CharSequence chars,
        int length,
        boolean fin)
    {
        if (textBytes.length < length * 3 + 8)
        {
            textBytes = new byte[length * 3 + 8];
            textBuffer = new UnsafeBufferEx(textBytes);
        }

        int position = 0;
        int index = 0;

        if (server.textHighSurrogate != 0 && length > 0)
        {
            final char c = chars.charAt(0);
            if (Character.isLowSurrogate(c))
            {
                position = put(Character.toCodePoint(server.textHighSurrogate, c), position);
                index = 1;
            }
            else
            {
                position = put(REPLACEMENT_CHARACTER, position);
            }
            server.textHighSurrogate = 0;
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
                else if (fin)
                {
                    position = put(REPLACEMENT_CHARACTER, position);
                }
                else
                {
                    server.textHighSurrogate = c;
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

        if (fin && server.textHighSurrogate != 0)
        {
            position = put(REPLACEMENT_CHARACTER, position);
            server.textHighSurrogate = 0;
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

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static boolean matches(
        CharSequence chars,
        String value)
    {
        boolean matches = chars.length() == value.length();

        for (int i = 0; matches && i < value.length(); i++)
        {
            matches = chars.charAt(i) == value.charAt(i);
        }

        return matches;
    }

    private int decodeEnd(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        int index = progress;

        while (index < limit && isWhitespace(buffer.getByte(index)))
        {
            index++;
        }

        if (index < limit)
        {
            server.onDecodeParseError(traceId);
        }

        return limit;
    }

    private int decodeIgnore(
        LlmOpenaiResponsesServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private final class LlmOpenaiResponsesServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long exitId;
        private final long initialAuthorization;
        private final String contentType;
        private final Runnable deauthorize;
        private final JsonParserEx parser;
        private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
        private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();
        private final Array32FW<HttpHeaderFW> eventHeadersRO = new Array32FW<>(new HttpHeaderFW());

        private LlmOpenaiResponsesStream stream;
        private LlmOpenaiResponsesServerDecoder decoder;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private boolean replyBegun;
        private int state;
        private boolean appEndPending;
        private boolean deauthorized;
        private boolean decoding;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private DirectBufferEx decodedInput;
        private int decodedWindowLimit;
        private boolean decodedLast;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotRawOffset;
        private int encodeSent;
        private boolean flushingReply;

        private long pendingReplyEndTraceId;
        private long pendingReplyEndAuthorization;

        private int messages;
        private int itemMessage = NONE;
        private int assistantMessage = NONE;
        private String role;
        private LlmSectionType roleType;
        private String itemType;
        private String itemId;
        private String itemName;
        private String outputCallId;
        private LlmOpenaiResponsesServerDecoder partsThen;

        private final LlmHeldTail tail;
        private LlmSectionType tailSection;
        private int tailMessage;
        private LlmOpenaiResponsesServerDecoder tailThen;
        private String reasoningId;
        private int reasoningSections;
        private int reasoningSummaries;
        private LlmSectionPipeline imagePipeline;
        private LlmSectionPipeline documentPipeline;
        private LlmSectionPipeline citationsPipeline;

        private LlmOpenaiResponsesServerDecoder probeCapture;
        private LlmOpenaiResponsesServerDecoder probeThen;
        private int probeMessage;
        private boolean probing;
        private final LlmOpenaiResponsesAttributeProbe probe;
        private final LlmSectionAttributes sentAttributes;
        private final LlmSectionAttributes finAttributes;
        private String blockExtension;
        private LlmSectionPipeline toolCallPipeline;
        private LlmSectionPipeline toolDefinitionPipeline;
        private LlmSectionPipeline serverToolCallPipeline;
        private LlmSectionPipeline serverToolResultPipeline;

        private LlmOpenaiResponsesServerDecoder skipThen;
        private int skipDepth;

        private boolean streaming;
        private boolean streamOptions;
        private boolean usageRequested;
        private boolean obfuscation = true;
        private int maxOutputTokens = NO_MAX_OUTPUT_TOKENS;

        private LlmOpenaiResponsesServerDecoder textThen;
        private boolean textOpen;
        private char textHighSurrogate;

        private LlmOpenaiResponsesServerDecoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;

        private boolean holding;
        private boolean lookahead;
        private boolean replaying;
        private int holdFrom;
        private int retained;

        private final LlmSectionAttributes blockAttributes;

        private long decodeTraceId;
        private long decodeAuthorization;

        private LlmSectionType blockSection;
        private int blockMessage;
        private int blockFlags;
        private final StringBuilder controlKey = new StringBuilder();
        private LlmSectionPipeline controlPipeline;
        private LlmOpenaiControlToCanonical controlTransform;
        private LlmSectionPipeline choicePipeline;
        private String choiceMember;
        private byte[] pieceBytes;
        private UnsafeBufferEx pieceBuffer;
        private final JsonGeneratorEx generator;
        private LlmSectionPipeline blockPipeline;
        private LlmSectionPipeline.Output sectionSink;
        private final LlmSectionPipeline.Output emitSink = (buffer, offset, length, last) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, last);
        private final LlmSectionPipeline.Output holdSink = (buffer, offset, length, last) ->
            onDecodeHold(buffer, offset, length);
        private final LlmSectionPipeline.Output blockOutput = (buffer, offset, length, last) ->
            sectionSink.put(buffer, offset, length, last);
        private final LlmMembersOutput memberOutput = new LlmMembersOutput((buffer, offset, length) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, false));

        private LlmOpenaiResponsesServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long exitId,
            long authorization,
            String contentType,
            Runnable deauthorize)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.exitId = exitId;
            this.initialAuthorization = authorization;
            this.contentType = contentType;
            this.deauthorize = deauthorize;
            this.parser = JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
            this.pieceBytes = new byte[1024];
            this.pieceBuffer = new UnsafeBufferEx(pieceBytes);
            this.generator = JsonEx.createGenerator();
            this.lookahead = true;
            this.holding = true;
            this.blockAttributes = new LlmSectionAttributes();
            this.sentAttributes = new LlmSectionAttributes();
            this.finAttributes = new LlmSectionAttributes();
            this.probe = new LlmOpenaiResponsesAttributeProbe();
            this.tail = new LlmHeldTail(encodePool);
            this.sectionSink = emitSink;
            this.decoder = decodeStart;
        }


        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = decodeMax;
            state = LlmState.openingInitial(state);

            doNetWindow(traceId);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + reserved;

            if (decodeSlot == NO_SLOT)
            {
                decodeNet(traceId, initialAuthorization, budgetId, reserved, payload.buffer(), payload.offset(), payload.limit());
            }
            else
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();

                decodeNet(traceId, initialAuthorization, budgetId);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            initialSeq = end.sequence();
            state = LlmState.closeInitial(state);
            state = LlmState.deferInitialEnd(state);

            if (decodeSlot != NO_SLOT)
            {
                decodeNet(traceId, initialAuthorization, 0L);
            }
            else
            {
                flushNetEnd(traceId);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (!decoding && decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                final int limit = decodeSlotOffset;

                decodeNet(traceId, authorization, budgetId, limit, buffer, 0, limit);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            if (offset < limit)
            {
                decoding = true;

                try
                {
                    int previous;
                    do
                    {
                        previous = progress;
                        progress = decodeInput(traceId, authorization, budgetId, reserved, buffer, progress, limit);
                    }
                    while (progress != previous && progress < limit && decoder != decodeIgnore);
                }
                catch (JsonException ex)
                {
                    onDecodeParseError(traceId);
                }
                finally
                {
                    decoding = false;
                }
            }

            if (decoder != decodeIgnore)
            {
                if (progress < limit)
                {
                    final int length = limit - progress;

                    if (decodeSlot == NO_SLOT)
                    {
                        decodeSlot = decodePool.acquire(initialId);
                    }

                    if (decodeSlot == NO_SLOT)
                    {
                        doNetReset(traceId);
                        if (stream != null)
                        {
                            stream.doAppAbort(traceId);
                        }
                        cleanup(traceId);
                    }
                    else
                    {
                        final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                        slot.putBytes(0, buffer, progress, length);
                        decodeSlotOffset = length;
                    }
                }
                else
                {
                    cleanupDecodeSlot();
                }

                if (decoder != decodeIgnore)
                {
                    flushNetWindow(traceId);
                    flushNetEnd(traceId);
                }
            }
        }

        private int decodeInput(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            decodedInput = buffer;
            decodedLast = LlmState.initialClosed(state);
            decodedWindowLimit = limit;

            if (decoder == decodeEnd)
            {
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }
            else if (decoder != decodeIgnore)
            {
                final int held = holding ? retained : 0;
                final int window = stream != null ? Math.min(limit - offset, decodeRoom()) : limit - offset;

                if (window >= held + Math.min(MIN_WINDOW, limit - offset - held))
                {
                    progress = decodeWindow(traceId, authorization, budgetId, reserved, buffer, offset, offset + window, limit);
                }
            }

            return progress;
        }

        private int decodeWindow(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int windowLimit,
            int limit)
        {
            final boolean windowLast = decodedLast && windowLimit == limit;

            final boolean gated = stream != null;

            decodedWindowLimit = windowLimit;
            decodeTraceId = traceId;
            decodeAuthorization = authorization;
            captureFrom = offset;
            holdFrom = offset;
            parser.wrap(buffer, offset + retained, windowLimit, windowLast);

            boolean replayed = false;
            LlmOpenaiResponsesServerDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder && (stream != null) == gated)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }

            if (captureOpen)
            {
                flushCapture(traceId, authorization, false);
                progress = position();
            }
            else if (holding)
            {
                retained = position() - holdFrom;
                progress = holdFrom;

                if (lookahead && retained >= decodeMax)
                {
                    onDecodeInvalidRequest(traceId, MESSAGE_MODEL_ORDER);
                    progress = limit;
                }
                else if (!lookahead && retained > HOLD_MAX)
                {
                    onDecodeParseError(traceId);
                    progress = limit;
                }
            }
            else if (replaying)
            {
                replaying = false;
                replayed = true;
                progress = holdFrom;
            }

            if (windowLast && !replayed && decoder != decodeEnd && decoder != decodeIgnore && decoder != decodeTail)
            {
                onDecodeParseError(traceId);
                progress = limit;
            }

            return progress;
        }

        private void flushNetWindow(
            long traceId)
        {
            final long initialAckMax = initialSeq - decodeSlotOffset;

            if (initialAckMax > initialAck)
            {
                initialAck = initialAckMax;
                doNetWindow(traceId);
            }
        }

        private void flushNetEnd(
            long traceId)
        {
            if (LlmState.initialEndDeferred(state) && decodeSlot == NO_SLOT && decoder != decodeIgnore)
            {
                state = LlmState.clearInitialEndDeferred(state);
                onDecodeEnd(traceId);
            }
        }

        private int decodeRoom()
        {
            final int framing = TEXT_PREFIX.length + TEXT_SUFFIX.length;
            final boolean inside = decoder == decodeText && !Flags.hasInit(blockFlags);
            final int reserve = inside ? TEXT_SUFFIX.length : framing;
            final int sections = 1 + (framing + MIN_TEXT_INPUT - 1) / MIN_TEXT_INPUT;
            final int growth = decoder == decodeText ? sections : Math.max(sections, CONTROL_GROWTH);

            return Math.max(available() - reserve, 0) / growth;
        }

        private int available()
        {
            final int available = stream != null ? (int) Math.max(stream.initialAvailable(), 0L) : 0;
            final int appendRoom = streaming && !streamOptions ? Math.min(STREAM_OPTIONS_SECTION.capacity(), available >> 1) : 0;

            return available - appendRoom;
        }

        private int position()
        {
            return decodedWindowLimit - parser.remaining();
        }

        private void skip(
            LlmOpenaiResponsesServerDecoder then)
        {
            skipThen = then;
            skipDepth = 0;
        }

        private void beginMessage(
            LlmSectionType type)
        {
            beginMessage(type == LlmSectionType.ASSISTANT_TEXT);
        }

        private void beginInstructions()
        {
            itemMessage = NONE;
            assistantMessage = NONE;
        }

        private void beginMessage(
            boolean assistant)
        {
            itemMessage = assistant && assistantMessage != NONE ? assistantMessage : messages++;
            assistantMessage = assistant ? itemMessage : NONE;
        }

        private String decodeString(
            long traceId,
            LlmOpenaiResponsesServerDecoder then)
        {
            String value = null;

            if (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();

                switch (event)
                {
                case VALUE_STRING:
                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                        skip(then);
                        decoder = decodeSkip;
                    }
                    else
                    {
                        value = parser.getString();
                        decoder = then;
                    }
                    break;
                case START_OBJECT:
                case START_ARRAY:
                    skip(then);
                    skipDepth = 1;
                    decoder = decodeSkip;
                    break;
                default:
                    decoder = then;
                    break;
                }
            }

            return value;
        }

        private void onDecodeItemType(
            String type,
            int kind)
        {
            itemType = type;

            switch (kind)
            {
            case LlmOpenaiResponsesItems.KIND_REASONING:
                onDecodeReasoningItem();
                break;
            case LlmOpenaiResponsesItems.KIND_FUNCTION_OUTPUT:
                beginMessage(false);
                outputCallId = null;
                release();
                decoder = decodeOutputItem;
                break;
            case LlmOpenaiResponsesItems.KIND_FUNCTION_CALL:
                beginMessage(true);
                probeItem(LlmSectionType.TOOL_CALL);
                break;
            case LlmOpenaiResponsesItems.KIND_CALL:
                beginMessage(true);
                probeItem(LlmSectionType.SERVER_TOOL_CALL);
                break;
            default:
                beginMessage(false);
                probeItem(LlmSectionType.SERVER_TOOL_RESULT);
                break;
            }
        }

        private void probeItem(
            LlmSectionType section)
        {
            probe(section, itemMessage, decodeItemCapture, decodeItems);
            probe.configure(itemType, itemId, null);
            decoder = decodeAttributes;
        }

        private void onDecodeToolType(
            String type)
        {
            itemType = type;

            probe(TYPE_FUNCTION.equals(type) ? LlmSectionType.TOOL_DEFINITION : LlmSectionType.SERVER_TOOL_DEFINITION,
                NONE, decodeToolCapture, decodeTools);
            probe.configure(type, null, itemName);
            decoder = decodeAttributes;
        }

        private void onDecodeReasoningItem()
        {
            beginMessage(true);
            reasoningId = itemId;
            reasoningSections = 0;
            reasoningSummaries = 0;
            tail.release();
            release();
            decoder = decodeReasoning;
        }

        private void onDecodeNonArray(
            JsonEvent event,
            LlmOpenaiResponsesServerDecoder then)
        {
            switch (event)
            {
            case START_OBJECT:
                skip(then);
                skipDepth = 1;
                decoder = decodeSkip;
                break;
            case VALUE_STRING:
                if (parser.deferredBytes())
                {
                    parser.consumed(parser.getStringView().length());
                    skip(then);
                    decoder = decodeSkip;
                }
                else
                {
                    decoder = then;
                }
                break;
            default:
                decoder = then;
                break;
            }
        }

        private void onDecodeReasoningPart(
            long traceId,
            long authorization)
        {
            if (reasoningSections > 0)
            {
                onDecodeData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true);
            }

            text(LlmSectionType.ASSISTANT_REASONING, decodeSummaryPart);
        }

        private void onDecodeEncrypted(
            long traceId)
        {
            final boolean stream = reasoningSummaries > 0;
            final boolean fresh = reasoningSections == 0;

            if (stream && fresh)
            {
                onDecodeBlock(LlmSectionType.ASSISTANT_REASONING, itemMessage);
            }

            final int length = encodeMemberHeader(stream && fresh ? '{' : ',');

            if (stream)
            {
                onDecodeData(traceId, decodeAuthorization, pieceBuffer, 0, length, false);
            }
            else if (!tail.append(initialId, pieceBuffer, 0, length))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private int encodeMemberHeader(
            int separator)
        {
            reservePiece(ENCRYPTED_KEY.length + (reasoningId != null ? reasoningId.length() * ESCAPE_EXPANSION : 0) +
                CONTROL_OPEN_CAPACITY * 4);

            int length = 1;

            if (reasoningId != null)
            {
                generator.wrap(pieceBuffer, 0, pieceBytes.length);
                generator.reset();
                generator.writeStartObject().write(ID_MEMBER, reasoningId);
                length = generator.length();
                reasoningId = null;
                pieceBytes[length++] = ',';
            }

            pieceBytes[0] = (byte) separator;
            System.arraycopy(ENCRYPTED_KEY, 0, pieceBytes, length, ENCRYPTED_KEY.length);

            return length + ENCRYPTED_KEY.length;
        }

        private int onDecodeEncryptedStream(
            long traceId,
            long authorization,
            CharSequence chars,
            boolean fin)
        {
            final int length = chars.length();
            final int take = memberAllowance(chars, fin);

            if (take == length)
            {
                final int decoded = encode(this, chars, take, fin);
                final int escaped = escapePiece(decoded);

                if (escaped > 0)
                {
                    onDecodeData(traceId, authorization, pieceBuffer, 0, escaped, false);
                }
            }

            return take;
        }

        private void onDecodeEncryptedEnd(
            long traceId,
            long authorization)
        {
            onDecodeData(traceId, authorization, STRING_CLOSE, 0, STRING_CLOSE.capacity(), false);
        }

        private void onDecodeEncryptedHold(
            long traceId,
            CharSequence chars,
            boolean fin)
        {
            final int decoded = encode(this, chars, chars.length(), fin);
            final int escaped = escapePiece(decoded);

            if (!tail.append(initialId, pieceBuffer, 0, escaped))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private void onDecodeEncryptedHoldEnd(
            long traceId)
        {
            if (!tail.append(initialId, STRING_CLOSE, 0, STRING_CLOSE.capacity()))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private int escapePiece(
            int length)
        {
            reservePiece(length * ESCAPE_EXPANSION);

            return LlmJsonEscape.escape(textBuffer, 0, length, pieceBytes);
        }

        private int memberAllowance(
            CharSequence chars,
            boolean fin)
        {
            final int length = chars.length();
            final int credit = available();

            int cost = fin ? STRING_CLOSE.capacity() : 0;
            int take = 0;
            int index = 0;
            boolean fits = true;

            while (fits && index < length)
            {
                final char c = chars.charAt(index);
                final boolean pair = Character.isHighSurrogate(c) && index + 1 < length &&
                    Character.isLowSurrogate(chars.charAt(index + 1));
                final int width = pair ? 2 : 1;

                cost += pair ? 4 : escapedWidth(c);
                fits = cost <= credit;

                if (fits)
                {
                    index += width;
                    take = index;
                }
            }

            return take;
        }

        private void onDecodeReasoningEnd(
            long traceId)
        {
            final boolean open = reasoningSections > 0;

            if (reasoningId != null)
            {
                appendPendingId(traceId);
            }

            if (open && !tail.held())
            {
                onDecodeData(traceId, decodeAuthorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true);
                decoder = decodeItems;
            }
            else if (tail.held())
            {
                if (!tail.append(initialId, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity()))
                {
                    onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
                }
                else
                {
                    if (!open)
                    {
                        tail.buffer().putByte(0, (byte) '{');
                    }

                    beginTail(open ? null : LlmSectionType.ASSISTANT_REASONING, decodeItems);
                }
            }
            else
            {
                decoder = decodeItems;
            }

            reasoningId = null;
            reasoningSections = 0;
            reasoningSummaries = 0;
        }

        private void appendPendingId(
            long traceId)
        {
            reservePiece(reasoningId.length() * ESCAPE_EXPANSION + CONTROL_OPEN_CAPACITY * 4);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);
            generator.reset();
            generator.writeStartObject().write(ID_MEMBER, reasoningId);
            pieceBytes[0] = (byte) ',';

            if (!tail.append(initialId, pieceBuffer, 0, generator.length()))
            {
                onDecodeInvalidRequest(traceId, MESSAGE_ENCRYPTED_BOUND);
            }
        }

        private void beginTail(
            LlmSectionType type,
            LlmOpenaiResponsesServerDecoder then)
        {
            tailSection = type;
            tailMessage = itemMessage;
            tailThen = then;
            decoder = decodeTail;
        }

        private void onDecodeTail(
            long traceId,
            long authorization)
        {
            boolean progress = true;

            while (progress && tail.remaining() > 0)
            {
                final int take = (int) Math.min(tail.remaining(), Math.max(available(), 0));

                progress = take > 0;

                if (progress)
                {
                    final boolean first = tail.offset() == 0;
                    final int init = first && tailSection != null ? Flags.INIT : Flags.NONE;
                    final int flags = take == tail.remaining() ? Flags.fin(init) : init;

                    stream.doAppData(traceId, authorization, flags, first ? tailSection : null, tailMessage, null, null,
                        tail.buffer(), tail.offset(), take);
                    tail.consumed(take);
                }
            }

            if (tail.remaining() == 0)
            {
                tail.release();
                decoder = tailThen;
            }
        }

        private void onDecodeAnnotations(
            LlmOpenaiResponsesServerDecoder then)
        {
            onDecodeBlock(LlmSectionType.CITATIONS, itemMessage);
            tail.release();
            sectionSink = holdSink;
            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = position() - 1;
        }

        private void onDecodeHold(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (!tail.append(initialId, buffer, offset, length))
            {
                onDecodeInvalidRequest(decodeTraceId, MESSAGE_ANNOTATIONS_BOUND);
            }
        }

        private void onDecodePartEnd()
        {
            if (tail.length() > EMPTY_ARRAY_LENGTH)
            {
                beginTail(LlmSectionType.CITATIONS, decodeParts);
            }
            else
            {
                tail.release();
                decoder = decodeParts;
            }
        }

        private void probe(
            LlmSectionType type,
            int message,
            LlmOpenaiResponsesServerDecoder capturing,
            LlmOpenaiResponsesServerDecoder then)
        {
            probeMessage = message;
            probeCapture = capturing;
            probeThen = then;
            probe.begin(type);
        }

        private void captureProbed()
        {
            captureHeld(probe.type(), probeMessage, probeThen, probe.depth());
            probing = true;
            decoder = probeCapture;
        }

        private void captureHeld(
            LlmSectionType type,
            int message,
            LlmOpenaiResponsesServerDecoder then,
            int depth)
        {
            onDecodeBlock(type, message);

            if (type == LlmSectionType.SERVER_TOOL_CALL || type == LlmSectionType.SERVER_TOOL_RESULT)
            {
                blockExtension = itemType;
            }

            captureThen = then;
            captureOpen = true;
            captureDepth = depth;
            captureFrom = holdFrom;
            release();
        }

        private void onDecodeItemResult(
            long traceId,
            long authorization,
            int eventFrom)
        {
            int keyFrom = eventFrom;

            while (decodedInput.getByte(keyFrom) != '"')
            {
                keyFrom++;
            }

            if (eventFrom > captureFrom)
            {
                onDecodeData(traceId, authorization, decodedInput, captureFrom, eventFrom - captureFrom, false);
            }

            onDecodeData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true);

            probing = false;
            onDecodeBlock(LlmSectionType.SERVER_TOOL_RESULT, blockMessage);
            blockExtension = itemType;
            blockAttributes.toolCallId(probe.attributes().toolCallId());

            onDecodeData(traceId, authorization, OBJECT_OPEN, 0, OBJECT_OPEN.capacity(), false);

            captureFrom = keyFrom;
        }

        private void text(
            LlmSectionType type,
            LlmOpenaiResponsesServerDecoder then)
        {
            textThen = then;
            textOpen = false;
            onDecodeBlock(type, itemMessage);
        }

        private void hold()
        {
            holding = true;
            holdFrom = position() - 1;
            retained = 0;
        }

        private void release()
        {
            holding = false;
            retained = 0;
        }

        private void onDecodeRootKey(
            CharSequence key)
        {
            if (matches(key, "instructions"))
            {
                decoder = decodeInstructions;
            }
            else if (matches(key, "input"))
            {
                decoder = decodeInput;
            }
            else if (matches(key, "tools"))
            {
                decoder = decodeToolsStart;
            }
            else if (matches(key, "max_output_tokens"))
            {
                decoder = decodeMaxOutputTokensValue;
            }
            else if (matches(key, "model"))
            {
                skip(decodeRoot);
                decoder = decodeSkip;
            }
            else
            {
                control(key, -1, isRootControl(key) ? decodeRootControl : decodeRoot);
            }
        }

        private void onDecodeControlSeparator()
        {
            final int start = LlmValueStart.of(decodedInput, captureFrom, decodedWindowLimit);

            if (start < decodedWindowLimit)
            {
                captureFrom = start;
                decoder = decodeControlValue;
            }
        }

        private void controlMore(
            CharSequence key,
            LlmOpenaiResponsesServerDecoder then)
        {
            captureThen = then;

            if (matches(key, MEMBER_TOOL_CHOICE) || matches(key, MEMBER_TEXT) || matches(key, MEMBER_REASONING) ||
                matches(key, MEMBER_SERVICE_TIER))
            {
                controlConverted(key, CONTROL_CONTINUE);
            }
            else
            {
                onDecodeControlKey(key, true, ',');
            }

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            decoder = decodeControlSeparator;
        }

        private void controlConverted(
            CharSequence key,
            DirectBufferEx separator)
        {
            onDecodeSectionData(decodeTraceId, decodeAuthorization, separator, 0, separator.capacity(), false);

            choiceMember = matches(key, MEMBER_TEXT)
                ? MEMBER_TEXT
                : matches(key, MEMBER_REASONING) ? MEMBER_REASONING : MEMBER_TOOL_CHOICE;
            blockPipeline = choicePipeline();
            blockPipeline.begin(blockAttributes);
            memberOutput.begin();

            onDecodeControlKey(key, false, '{');
        }

        private void closeControl(
            long traceId,
            long authorization)
        {
            onDecodeData(traceId, authorization, EMPTY_VALUE, 0, 0, true);
        }

        private void control(
            CharSequence key,
            int message,
            LlmOpenaiResponsesServerDecoder then)
        {
            onDecodeBlock(LlmSectionType.CONTROL, message);

            captureThen = then;

            if (matches(key, MEMBER_STREAM) || matches(key, MEMBER_STREAM_OPTIONS))
            {
                blockPipeline = controlPipeline();
                blockPipeline.begin(blockAttributes);
                onDecodeControlKey(key, false, '{');
            }
            else if (matches(key, MEMBER_TOOL_CHOICE) || matches(key, MEMBER_TEXT) || matches(key, MEMBER_REASONING) ||
                matches(key, MEMBER_SERVICE_TIER))
            {
                controlConverted(key, CONTROL_BEGIN);
            }
            else
            {
                onDecodeControlKey(key, true, '{');
            }

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            decoder = decodeControlSeparator;
        }

        private void endCapture(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, captureThen != decodeRootControl);

            if (blockSection == LlmSectionType.CONTROL && blockPipeline != null)
            {
                if (captureThen == decodeRootControl &&
                    !onDecodeBlockData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true))
                {
                    onDecodeBlockRejected(traceId);
                }

                if (blockPipeline == controlPipeline)
                {
                    onDecodeControl();
                }
            }

            blockPipeline = null;
            sectionSink = emitSink;
            captureOpen = false;
            probing = false;
            decoder = decoder == decodeIgnore ? decoder : captureThen;
        }

        private void flushCapture(
            long traceId,
            long authorization,
            boolean fin)
        {
            final int end = position();

            if (end > captureFrom || fin)
            {
                onDecodeData(traceId, authorization, decodedInput, captureFrom, end - captureFrom, fin);
            }

            captureFrom = end;
        }

        private void onDecodeModel(
            long traceId,
            long authorization,
            String model)
        {
            decoder = decodeRoot;
            stream = new LlmOpenaiResponsesStream(this);
            stream.doAppBegin(traceId, authorization, model);

            parser.reset();
            decoder = decodeStart;
            replaying = true;

            lookahead = false;
            holding = false;
            retained = 0;
        }

        private int onDecodeText(
            long traceId,
            long authorization,
            CharSequence chars,
            boolean fin,
            int eventFrom)
        {
            textOpen = true;

            final int length = chars.length();
            final int take = textAllowance(chars, fin);
            final boolean finished = fin && take == length;

            if (take > 0 || finished && textHighSurrogate != 0)
            {
                final int decoded = encode(this, chars, take, finished);
                final int encoded = encodePiece(textBuffer, 0, decoded);

                onDecodeData(traceId, authorization, pieceBuffer, 0, encoded, false);
            }

            if (finished)
            {
                final boolean reasoning = blockSection == LlmSectionType.ASSISTANT_REASONING;
                final int encoded = encodePieceEnd(reasoning);

                onDecodeData(traceId, authorization, pieceBuffer, 0, encoded, !reasoning);
            }

            return take;
        }

        private int textAllowance(
            CharSequence chars,
            boolean fin)
        {
            final int length = chars.length();
            final int credit = available();

            int cost = Flags.hasInit(blockFlags) ? TEXT_PREFIX.length : 0;
            int take = 0;
            int index = 0;
            boolean fits = true;

            while (fits && index < length)
            {
                final char c = chars.charAt(index);
                final boolean pair = Character.isHighSurrogate(c) && index + 1 < length &&
                    Character.isLowSurrogate(chars.charAt(index + 1));
                final int width = pair ? 2 : 1;
                final int next = cost + (pair ? 4 : escapedWidth(c));
                final boolean last = index + width == length;

                fits = next + (last && fin ? TEXT_SUFFIX.length : 0) <= credit;

                if (fits)
                {
                    cost = next;
                    index += width;
                    take = index;
                }
            }

            if (length == 0 && fin && cost + TEXT_SUFFIX.length > credit)
            {
                take = -1;
            }

            return take;
        }

        private void reservePiece(
            int capacity)
        {
            if (pieceBytes.length < capacity)
            {
                pieceBytes = Arrays.copyOf(pieceBytes, capacity);
                pieceBuffer = new UnsafeBufferEx(pieceBytes);
            }
        }

        private int encodePiece(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            reservePiece(TEXT_PREFIX.length + length * ESCAPE_EXPANSION);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);

            if (Flags.hasInit(blockFlags))
            {
                beginPieceObject();
            }

            generator.writeSegment(buffer, offset, length);

            return generator.length();
        }

        private int encodePieceEnd(
            boolean open)
        {
            reservePiece(TEXT_PREFIX.length + TEXT_SUFFIX.length);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);

            if (Flags.hasInit(blockFlags))
            {
                beginPieceObject();
            }

            generator.writeEndEscaped();

            if (!open)
            {
                generator.writeEnd();
            }

            return generator.length();
        }

        private void beginPieceObject()
        {
            generator.reset();
            generator.writeStartObject().writeKey(MEMBER_TEXT).writeStartEscaped();
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

        private void onDecodeBlock(
            LlmSectionType type,
            int message)
        {
            blockSection = type;
            blockMessage = message;
            blockFlags = Flags.INIT;

            blockAttributes.clear();
            blockExtension = null;

            if (type == LlmSectionType.TOOL_RESULT)
            {
                blockAttributes.toolCallId(outputCallId);
            }

            blockPipeline = blockPipelineFor(type);

            if (blockPipeline != null)
            {
                blockPipeline.begin(blockAttributes);
            }
        }

        private LlmSectionPipeline blockPipelineFor(
            LlmSectionType type)
        {
            LlmSectionPipeline pipeline = null;

            switch (type)
            {
            case TOOL_CALL:
                if (toolCallPipeline == null)
                {
                    toolCallPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesToolCallToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = toolCallPipeline;
                break;
            case TOOL_DEFINITION:
                if (toolDefinitionPipeline == null)
                {
                    toolDefinitionPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesToolDefinitionToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = toolDefinitionPipeline;
                break;
            case SERVER_TOOL_CALL:
                if (serverToolCallPipeline == null)
                {
                    serverToolCallPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesServerToolToCanonical(true),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = serverToolCallPipeline;
                break;
            case SERVER_TOOL_RESULT:
                if (serverToolResultPipeline == null)
                {
                    serverToolResultPipeline = new LlmSectionPipeline(new LlmOpenaiResponsesServerToolToCanonical(false),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = serverToolResultPipeline;
                break;
            case USER_IMAGE:
                if (imagePipeline == null)
                {
                    imagePipeline = new LlmSectionPipeline(LlmOpenaiResponsesMediaToCanonical.image(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = imagePipeline;
                break;
            case USER_DOCUMENT:
                if (documentPipeline == null)
                {
                    documentPipeline = new LlmSectionPipeline(LlmOpenaiResponsesMediaToCanonical.document(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = documentPipeline;
                break;
            case CITATIONS:
                if (citationsPipeline == null)
                {
                    citationsPipeline = new LlmSectionPipeline(LlmOpenaiResponsesCitationsToCanonical.array(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = citationsPipeline;
                break;
            default:
                break;
            }

            return pipeline;
        }


        private void onDecodeData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            if (decoder != decodeIgnore)
            {
                final boolean closing = blockSection == LlmSectionType.CONTROL && last;

                if (!onDecodeBlockData(traceId, authorization, buffer, offset, length, last && !closing) ||
                    closing && !onDecodeBlockData(traceId, authorization, CONTROL_CLOSE, 0, CONTROL_CLOSE.capacity(), true))
                {
                    onDecodeBlockRejected(traceId);
                }
            }
        }

        private boolean onDecodeBlockData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            boolean accepted = true;

            if (blockPipeline == null)
            {
                onDecodeSectionData(traceId, authorization, buffer, offset, length, last);
            }
            else
            {
                accepted = blockPipeline.feed(buffer, offset, length, last,
                    captureThen == decodeRootControl ? memberOutput : blockOutput);
            }

            return accepted;
        }

        private LlmSectionPipeline choicePipeline()
        {
            if (choicePipeline == null)
            {
                choicePipeline = new LlmSectionPipeline(new LlmOpenaiResponsesControlToCanonical(),
                    new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return choicePipeline;
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmOpenaiControlToCanonical();
                controlPipeline = new LlmSectionPipeline(controlTransform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return controlPipeline;
        }

        private void onDecodeControlKey(
            CharSequence key,
            boolean prefixed,
            char open)
        {
            controlKey.setLength(0);

            final String shared = prefixed ? LlmSharedMembers.openaiResponses(key) : null;

            if (shared != null)
            {
                controlKey.append(SHARED_PREFIX).append(shared);
            }
            else
            {
                if (prefixed)
                {
                    controlKey.append(VENDOR_PREFIX);
                }

                controlKey.append(key);
            }

            reservePiece(controlKey.length() * ESCAPE_EXPANSION + CONTROL_OPEN_CAPACITY);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);
            generator.reset();
            generator.writeStartObject().writeKey(controlKey);

            pieceBuffer.putByte(0, (byte) open);

            onDecodeBlockData(decodeTraceId, decodeAuthorization, pieceBuffer, 0, generator.length(), false);
        }

        private void onDecodeControl()
        {
            if (controlTransform.streamSeen())
            {
                streaming = controlTransform.streaming();
            }

            if (controlTransform.optionsSeen())
            {
                streamOptions = true;
                usageRequested = controlTransform.usageRequested();
                obfuscation = !controlTransform.obfuscationDisabled();
            }
        }

        private void onDecodeSectionData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            final int flags = last ? Flags.fin(blockFlags) : blockFlags;
            final boolean init = Flags.hasInit(flags);
            final LlmSectionAttributes attributes;

            if (init)
            {
                reasoningSections += blockSection == LlmSectionType.ASSISTANT_REASONING ? 1 : 0;

                if (probing)
                {
                    blockAttributes.copyFrom(probe.attributes());
                    sentAttributes.copyFrom(probe.attributes());
                }

                attributes = blockAttributes;
            }
            else if (probing && last)
            {
                finAttributes.difference(probe.attributes(), sentAttributes);
                attributes = finAttributes;
            }
            else
            {
                attributes = null;
            }

            stream.doAppData(traceId, authorization, flags, init ? blockSection : null, blockMessage, attributes,
                init ? blockExtension : null, buffer, offset, length);

            blockFlags = Flags.NONE;
        }

        private void onDecodeRootEnd(
            long traceId)
        {
            if (streaming && !streamOptions)
            {
                streamOptions = true;
                usageRequested = false;
                stream.doAppData(decodeTraceId, decodeAuthorization, Flags.COMPLETE, LlmSectionType.CONTROL, -1, null, null,
                    STREAM_OPTIONS_SECTION, 0, STREAM_OPTIONS_SECTION.capacity());
            }

            decoder = decodeEnd;
        }

        private void onDecodeEnd(
            long traceId)
        {
            if (decoder != decodeEnd)
            {
                onDecodeParseError(traceId);
            }
            else if (stream == null)
            {
                cleanup(traceId);
            }
            else
            {
                stream.doAppFlush(traceId, initialAuthorization, maxOutputTokens);

                if (LlmState.replyClosed(stream.state))
                {
                    appEndPending = false;
                    stream.doAppEnd(traceId);
                }
                else
                {
                    appEndPending = true;
                }
            }
        }

        private void onDecodeParseError(
            long traceId)
        {
            onDecodeInvalidRequest(traceId, MESSAGE_PARSE_ERROR);
        }

        private void onDecodeBlockRejected(
            long traceId)
        {
            final boolean format = blockPipeline == choicePipeline && MEMBER_TEXT.equals(choiceMember);

            onDecodeInvalidRequest(traceId, format ? MESSAGE_FORMAT_INVALID : MESSAGE_PARSE_ERROR);
        }

        private void onDecodeInvalidRequest(
            long traceId,
            String message)
        {
            decoder = decodeIgnore;
            release();

            initialAck = initialSeq;
            doNetWindow(traceId);
            doNetError(traceId, initialAuthorization, STATUS_BAD_REQUEST, message);
            if (stream != null)
            {
                stream.doAppAbort(traceId);
                stream.doAppReset(traceId);
            }
        }

        private void doNetError(
            long traceId,
            long authorization,
            LlmErrorFW error)
        {
            final int status = error.status() != -1 ? error.status() : STATUS_INTERNAL_SERVER_ERROR;

            doNetError(traceId, authorization, status, error.type().asString(), error.param().asString(),
                error.code().asString(), error.message().asString());
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String message)
        {
            doNetError(traceId, authorization, status, null, null, null, message);
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String type,
            String param,
            String code,
            String message)
        {
            final DirectBufferEx body = asBuffer(errorBody(status, type, param, code, message));

            cleanupDecodeSlot();

            doNetBegin(traceId, authorization, 0L, Integer.toString(status), CONTENT_TYPE_JSON);
            doNetData(body, 0, body.capacity(), 0, Flags.COMPLETE, EMPTY_OCTETS, 0L, traceId, authorization);

            if (encodeSlot != NO_SLOT)
            {
                state = LlmState.deferReplyEnd(state);
                pendingReplyEndTraceId = traceId;
                pendingReplyEndAuthorization = authorization;
            }
            else
            {
                doNetEnd(traceId, authorization, EMPTY_OCTETS);
            }

            doNetReset(traceId);
        }

        private void cleanup(
            long traceId)
        {
            decoder = decodeIgnore;
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            cleanupTail();
            if (stream != null)
            {
                stream.cleanupAccumulator();
                stream.cleanupHeld();
            }
            if (!deauthorized)
            {
                deauthorized = true;
                deauthorize.run();
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
            }
            decodeSlotOffset = 0;
        }

        private void cleanupTail()
        {
            tail.release();
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSent = 0;
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

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            initialSeq = abort.sequence();
            state = LlmState.closeInitial(state);

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
            cleanup(traceId);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = window.padding();
            state = LlmState.openReply(state);

            flushEncodeSlot(traceId);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
            cleanup(traceId);
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            if (stream != null)
            {
                stream.doAppChallenge(traceId, authorization, extension);
            }
        }

        private void doNetData(
            OctetsFW payload,
            int flags,
            long budgetId,
            long traceId,
            long authorization)
        {
            doNetData(payload.buffer(), payload.offset(), payload.sizeof(), payload.sizeof(), flags, EMPTY_OCTETS, budgetId,
                traceId, authorization);
        }

        private void doNetData(
            DirectBufferEx buffer,
            int offset,
            int length,
            int rawLength,
            int flags,
            OctetsFW attributes,
            long budgetId,
            long traceId,
            long authorization)
        {
            final long available = replyMax - (replySeq - replyAck) - replyPad;

            if (encodeSlot == NO_SLOT && available > 0 && length <= available)
            {
                doNetData(traceId, authorization, flags, budgetId, length + replyPad, attributes, buffer, offset, length);
                stream.ackReply(traceId);
            }
            else
            {
                doNetEncode(buffer, offset, length, rawLength, flags, attributes, budgetId, traceId, authorization);
            }
        }

        private void doNetEncode(
            DirectBufferEx buffer,
            int offset,
            int length,
            int rawLength,
            int flags,
            OctetsFW attributes,
            long budgetId,
            long traceId,
            long authorization)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            if (encodeSlot == NO_SLOT ||
                encodeSlotOffset + QUEUE_ENTRY_OVERHEAD + attributes.sizeof() + length > encodePool.slotCapacity())
            {
                doNetReset(traceId);
                stream.doAppAbort(traceId);
                cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                encodeSlotOffset = queueEntryRW.wrap(slotBuffer, encodeSlotOffset, slotBuffer.capacity())
                    .extension(attributes.buffer(), attributes.offset(), attributes.sizeof())
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(budgetId)
                    .reserved(rawLength)
                    .flags(flags)
                    .payload(buffer, offset, length)
                    .build()
                    .limit();
                encodeSlotRawOffset += rawLength;

                flushEncodeSlot(traceId);
            }
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW attributes,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final DataFW.Builder dataBuilder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(buffer, offset, length);

            if (attributes.sizeof() > 0)
            {
                final HttpDataExFW dataEx = httpDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(httpTypeId)
                    .headers(eventHeadersRO.wrap(attributes.buffer(), attributes.offset(), attributes.limit()))
                    .build();

                dataBuilder.extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof());
            }
            else
            {
                dataBuilder.extension(EMPTY_OCTETS);
            }

            final DataFW data = dataBuilder.build();

            network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
            replySeq += reserved;
        }

        private void flushEncodeSlot(
            long traceId)
        {
            if (flushingReply || encodeSlot == NO_SLOT)
            {
                return;
            }

            flushingReply = true;
            try
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                while (encodeSlotOffset > 0)
                {
                    final long available = replyMax - (replySeq - replyAck) - replyPad;
                    if (available <= 0)
                    {
                        break;
                    }

                    final LlmQueueEntryFW entry = queueEntryRO.wrap(buffer, 0, encodeSlotOffset);
                    final OctetsFW payload = entry.payload();
                    final int remaining = payload.sizeof() - encodeSent;
                    final int sliceLength = (int) Math.min(remaining, available);
                    if (sliceLength <= 0 && remaining > 0)
                    {
                        break;
                    }

                    final int sliceOffset = payload.offset() + encodeSent;
                    final boolean init = encodeSent == 0 && Flags.hasInit(entry.flags());
                    encodeSent += sliceLength;

                    final int initFlags = init ? Flags.INIT : Flags.NONE;
                    final boolean last = encodeSent == payload.sizeof() && Flags.hasFin(entry.flags());
                    final int flags = last ? Flags.fin(initFlags) : initFlags;
                    doNetData(entry.traceId(), entry.authorization(), flags, entry.budgetId(),
                        sliceLength + replyPad, init ? entry.extension() : EMPTY_OCTETS, buffer, sliceOffset, sliceLength);

                    if (encodeSent >= payload.sizeof())
                    {
                        final int size = entry.sizeof();

                        encodeSlotRawOffset -= entry.reserved();
                        encodeSlotOffset -= size;
                        if (encodeSlotOffset > 0)
                        {
                            buffer.putBytes(0, buffer, size, encodeSlotOffset);
                        }
                        encodeSent = 0;
                    }
                }

                if (encodeSlotOffset == 0)
                {
                    encodePool.release(encodeSlot);
                    encodeSlot = NO_SLOT;
                    encodeSlotRawOffset = 0;
                }
            }
            finally
            {
                flushingReply = false;
            }

            if (stream != null)
            {
                stream.ackReply(traceId);
            }

            if (LlmState.replyEndDeferred(state) && encodeSlot == NO_SLOT)
            {
                state = LlmState.clearReplyEndDeferred(state);
                doNetEnd(pendingReplyEndTraceId, pendingReplyEndAuthorization, EMPTY_OCTETS);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            String status,
            String responseContentType)
        {
            final HttpBeginExFW.Builder httpBeginExBuilder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_STATUS).value(status));

            if (responseContentType != null)
            {
                httpBeginExBuilder.headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(responseContentType));
            }

            final HttpBeginExFW httpBeginEx = httpBeginExBuilder.build();

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
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            this.replyBegun = true;
            state = LlmState.openingReply(state);
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

                network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeReply(state);

                if (!deauthorized)
                {
                    deauthorized = true;
                    deauthorize.run();
                }
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

                network.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeReply(state);

                if (!deauthorized)
                {
                    deauthorized = true;
                    deauthorize.run();
                }
            }
        }

        private void doNetWindow(
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
                .capabilities(FRAMING_CAPABILITIES_MASK)
                .build();

            network.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doNetReset(
            long traceId)
        {
            if (!LlmState.initialClosed(state))
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                network.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
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

            network.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }
    }

    private final class LlmOpenaiResponsesStream
    {
        private final LlmOpenaiResponsesServer server;
        private final LlmTextPipeline textPipeline;

        private MessageConsumer app;
        private long initialId;
        private long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private boolean flushed;

        private LlmOpenaiResponsesStream(
            LlmOpenaiResponsesServer server)
        {
            this.server = server;
            this.generator = JsonEx.createGenerator();
            this.eventOffsets = new int[EVENT_CAPACITY];
            this.eventLengths = new int[EVENT_CAPACITY];
            this.eventFlags = new int[EVENT_CAPACITY];
            this.eventAttributesBuffer = new UnsafeBufferEx(new byte[EVENT_ATTRIBUTES_CAPACITY]);
            this.eventAttributeOffsets = new int[EVENT_CAPACITY];
            this.eventAttributeLengths = new int[EVENT_CAPACITY];
            this.eventStart = NO_EVENT;
            this.textPipeline = new LlmTextPipeline(this::onEncodeText);
            this.partKinds = new byte[PART_CAPACITY * PART_VALUES];
            this.reasoningText = new LlmTextPipeline(this::onReasoningText, true);
            this.reasoningIdFilter = new LlmEdgeFilter((buffer, offset, length, last) ->
                onReasoningId(buffer, offset, length, last));
            this.reasoningMembers = new LlmEdgeFilter((buffer, offset, length, last) ->
                onReasoningMembers(buffer, offset, length));
            this.citationsOutput = (buffer, offset, length, last) -> onCitations(buffer, offset, length);
            this.reasoningIdBytes = new byte[ID_CAPACITY];
            this.elementBytes = new byte[ELEMENT_MAX];
            this.elementBuffer = new UnsafeBufferEx(elementBytes);
            this.itemOriginals = new String[PART_CAPACITY];
            this.itemTypes = new String[PART_CAPACITY];
            this.itemIds = new String[PART_CAPACITY];
            this.itemCallIds = new String[PART_CAPACITY];
            this.itemNames = new String[PART_CAPACITY];
            this.itemFirst = new int[PART_CAPACITY];
            this.itemCounts = new int[PART_CAPACITY];
            this.tailBytes = new byte[TAIL_MAX];
            this.tailBuffer = new UnsafeBufferEx(tailBytes);
            this.escapeBytes = new byte[TEXT_CHUNK];
            this.escapeBuffer = new UnsafeBufferEx(escapeBytes);
            this.deltaBytes = new byte[TEXT_CHUNK];
            this.deltaBuffer = new UnsafeBufferEx(deltaBytes);
            this.argumentsOutput = (buffer, offset, length, last) -> onArguments(buffer, offset, length);
            this.valueFilter = new LlmStringFilter((buffer, offset, length, last) -> onDelta(buffer, offset, length));
            this.tailFilter = new LlmEdgeFilter((buffer, offset, length, last) -> onTail(buffer, offset, length));
            this.membersFilter = new LlmEdgeFilter((buffer, offset, length, last) -> onMembers(buffer, offset, length));
        }

        private long initialAvailable()
        {
            return initialMax - (initialSeq - initialAck);
        }


        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();
            final LlmResetExFW resetEx = extension.get(llmResetExRO::tryWrap);

            state = LlmState.closeInitial(state);
            server.appEndPending = false;

            if (resetEx != null && !server.replyBegun)
            {
                server.doNetError(traceId, authorization, resetEx.error());
            }
            else
            {
                server.doNetReset(traceId);
                server.cleanup(traceId);
            }
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();

            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialBud = window.budgetId();
            state = LlmState.openInitial(state);

            server.decodeNet(traceId, server.initialAuthorization, 0L);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            String model)
        {
            this.initialId = supplyInitialId.applyAsLong(server.exitId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(NAME);

            if (server.contentType != null)
            {
                builder.contentType(server.contentType);
            }

            builder.model(model);

            final LlmBeginExFW llmBeginEx = builder.build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(llmBeginEx.buffer(), llmBeginEx.offset(), llmBeginEx.sizeof())
                .build();

            app = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onAppMessage);
            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            state = LlmState.openingInitial(state);
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            final OctetsFW extension = begin.extension();
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::tryWrap);

            replySeq = sequence;
            replyAck = acknowledge;
            state = LlmState.openingReply(state);

            replyMax = (encodePool.slotCapacity() - RESPONSE_ENCODE_SLACK) / RESPONSE_ESCAPE_EXPANSION;

            if (server.streaming && accumulator == null)
            {
                accumulator = new LlmContentAccumulator(encodePool, replyId);
            }

            responseId = llmBeginEx != null ? llmBeginEx.id().asString() : null;
            responseModel = llmBeginEx != null ? llmBeginEx.model().asString() : null;
            responseDialect = llmBeginEx != null ? llmBeginEx.dialect().asString() : null;

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                server.streaming ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            beginEncode();
            doEncodeBegin();
            doEncodeFlush(0, 0L, traceId, authorization);

            doAppWindow(traceId);
        }

        private void onAppData(
            DataFW data)
        {
            replySeq = data.sequence() + data.reserved();

            onAppSection(data);
        }

        private void onAppSection(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload() != null ? data.payload() : EMPTY_OCTETS;

            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            beginStep();

            final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);
            final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;

            if (Flags.hasInit(flags))
            {
                final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;
                final String extension = section != null && section.extension().sizeof() > 0
                    ? section.extension().get((b, o, l) -> b.getStringWithoutLengthUtf8(o, l - o))
                    : null;

                replyAttributes.readFrom(section);

                doEncodeSection(type, section != null ? section.message() : NONE, extension);
            }
            else
            {
                replyAttributes.mergeFrom(section);
            }

            doEncodeData(traceId, payload.buffer(), payload.offset(), payload.sizeof(), Flags.hasFin(flags));

            if (Flags.hasFin(flags))
            {
                doEncodeEnd();
            }

            if (encodeLength() > 0)
            {
                doEncodeFlush(reserved, budgetId, traceId, authorization);
            }
            else
            {
                ackReply(traceId);
            }
        }


        private void ackReply(
            long traceId)
        {
            final long replyAckMax = replySeq - server.encodeSlotRawOffset - hold.held();

            if (replyAckMax > replyAck)
            {
                replyAck = replyAckMax;
                doAppWindow(traceId);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final OctetsFW extension = end.extension();

            replySeq = end.sequence();
            state = LlmState.closeReply(state);

            final LlmEndExFW llmEndEx = extension.get(llmEndExRO::tryWrap);
            final LlmUsageFW usage = llmEndEx != null ? llmEndEx.usage() : null;

            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            beginStep();

            doEncodeEnd();
            doEncodeFinish(
                llmEndEx != null ? llmEndEx.finishReason().asString() : null,
                usage != null ? usage.inputTokens() : NONE,
                usage != null ? usage.cacheReadTokens() : NONE,
                usage != null ? usage.cacheWriteTokens() : NONE,
                usage != null ? usage.outputTokens() : NONE,
                usage != null ? usage.reasoningTokens() : NONE,
                usage != null ? usage.totalTokens() : NONE);

            cleanupHeld();

            doEncodeFlush(0, 0L, traceId, authorization);

            if (server.encodeSlot != NO_SLOT)
            {
                server.state = LlmState.deferReplyEnd(server.state);
                server.pendingReplyEndTraceId = traceId;
                server.pendingReplyEndAuthorization = authorization;
            }
            else
            {
                server.doNetEnd(traceId, authorization, EMPTY_OCTETS);
            }

            cleanupAccumulator();

            if (server.appEndPending)
            {
                server.onDecodeEnd(traceId);
            }
        }


        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            replySeq = abort.sequence();
            state = LlmState.closeReply(state);

            cleanupHeld();

            final LlmAbortExFW llmAbortEx = server.streaming ? extension.get(llmAbortExRO::tryWrap) : null;
            final LlmErrorFW error = llmAbortEx != null ? llmAbortEx.error() : null;

            encodeTraceId = traceId;
            encodeAuthorization = authorization;

            if (error != null && (error.type().asString() != null || error.message().asString() != null))
            {
                beginStep();
                doEncodeError(error.type().asString(), error.param().asString(), error.code().asString(),
                    error.message().asString());
                doEncodeFlush(0, 0L, traceId, authorization);

                if (server.encodeSlot != NO_SLOT)
                {
                    server.state = LlmState.deferReplyEnd(server.state);
                    server.pendingReplyEndTraceId = traceId;
                    server.pendingReplyEndAuthorization = authorization;
                }
                else
                {
                    server.doNetEnd(traceId, authorization, EMPTY_OCTETS);
                }
            }
            else
            {
                server.doNetAbort(traceId, authorization, extension);
            }

            cleanupAccumulator();

            if (server.appEndPending)
            {
                server.onDecodeEnd(traceId);
            }
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();
            final LlmChallengeExFW llmChallengeEx = extension.get(llmChallengeExRO::tryWrap);

            if (llmChallengeEx == null || llmChallengeEx.typeId() != llmTypeId)
            {
                server.doNetChallenge(traceId, authorization, extension);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            int maxOutputTokens)
        {
            if (!flushed && !LlmState.initialClosed(state))
            {
                flushed = true;

                final FlushFW.Builder builder = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(0L)
                    .reserved(0);

                if (maxOutputTokens != NO_MAX_OUTPUT_TOKENS)
                {
                    final LlmFlushExFW llmFlushEx = llmFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId)
                        .dialect(d -> d.maxOutputTokens(maxOutputTokens))
                        .build();

                    builder.extension(llmFlushEx.buffer(), llmFlushEx.offset(), llmFlushEx.sizeof());
                }

                final FlushFW flush = builder.build();

                app.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
            }
        }

        private void doAppEnd(
            long traceId)
        {
            if (!LlmState.initialClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (!LlmState.initialClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doAppReset(
            long traceId)
        {
            if (!LlmState.replyClosed(state))
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .build();

                app.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
                state = LlmState.closeReply(state);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(RESPONSE_ENCODE_PADDING)
                .build();

            app.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
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

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            LlmSectionType type,
            int message,
            LlmSectionAttributes attributes,
            String extension,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(initialBud)
                .reserved(length)
                .payload(buffer, offset, length);

            if (type != null || attributes != null && attributes.present())
            {
                final LlmDataExFW dataEx = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .section(x ->
                    {
                        if (type != null)
                        {
                            x.type(t -> t.set(type)).message(message);
                        }

                        if (attributes != null)
                        {
                            attributes.writeTo(x);
                        }

                        if (extension != null)
                        {
                            final byte[] bytes = extension.getBytes(UTF_8);
                            x.extension(e -> e.set(bytes));
                        }
                    })
                    .build();

                builder.extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof());
            }

            final DataFW data = builder.build();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            initialSeq += length;
        }

        private static final int EVENT_CAPACITY = 8;
        private static final int NO_EVENT = -1;
        private static final int PART_CAPACITY = 8;

        private static final int KIND_NONE = 0;
        private static final int KIND_TEXT = 1;
        private static final int KIND_SKIPPED = 2;
        private static final int KIND_CONTROL = 3;

        private static final int CONTROL_DROP = 0;
        private static final int CONTROL_HELD = 1;
        private static final int CONTROL_INLINE = 2;
        private static final int OBFUSCATION_MIN = 4;
        private static final int OBFUSCATION_RANGE = 28;
        private static final int TEXT_CHUNK = 1024;
        private static final int CHUNK_RESERVE = 512;
        private static final int TAIL_MAX = 4096;
        private static final int REPEAT_RESERVE = 256;
        private static final int REPEAT_STEP_DIVISOR = 4;
        private static final int KIND_CALL = 4;
        private static final int KIND_PROVIDER = 5;
        private static final int KIND_RESULT = 6;
        private static final int KIND_REFUSAL = 7;
        private static final int KIND_CITATIONS = 8;
        private static final int KIND_REASONING = 9;
        private static final int ITEM_REASONING = 4;
        private static final int PART_NONE = 0;
        private static final int PART_ACTIVE = 1;
        private static final int PART_PENDING = 2;
        private static final int PART_DIRECT = 3;
        private static final int PART_KIND_TEXT = 0;
        private static final int PART_KIND_REFUSAL = 1;
        private static final int PART_VALUES = 2;
        private static final int ELEMENT_MAX = 4096;
        private static final int ID_CAPACITY = 64;
        private static final int LEX_NONE = 0;
        private static final int LEX_STRING = 1;
        private static final int LEX_ESCAPE = 2;
        private static final String TYPE_REASONING = LlmOpenaiResponsesItems.TYPE_REASONING;
        private static final String TYPE_SUMMARY_TEXT = "summary_text";
        private static final String MEMBER_SUMMARY_INDEX = "summary_index";
        private static final String MEMBER_ANNOTATION = "annotation";
        private static final String MEMBER_ANNOTATION_INDEX = "annotation_index";
        private static final String EVENT_SUMMARY_PART_ADDED = "response.reasoning_summary_part.added";
        private static final String EVENT_SUMMARY_PART_DONE = "response.reasoning_summary_part.done";
        private static final String EVENT_SUMMARY_TEXT_DELTA = "response.reasoning_summary_text.delta";
        private static final String EVENT_SUMMARY_TEXT_DONE = "response.reasoning_summary_text.done";
        private static final String EVENT_REFUSAL_DELTA = "response.refusal.delta";
        private static final String EVENT_REFUSAL_DONE = "response.refusal.done";
        private static final String EVENT_ANNOTATION_ADDED = "response.output_text.annotation.added";
        private static final DirectBufferEx ARRAY_OPEN = new UnsafeBufferEx("[".getBytes(UTF_8));
        private static final DirectBufferEx ARRAY_CLOSE = new UnsafeBufferEx("]".getBytes(UTF_8));
        private static final int ITEM_NONE = 0;
        private static final int ITEM_MESSAGE = 1;
        private static final int ITEM_CALL = 2;
        private static final int ITEM_PROVIDER = 3;
        private static final int VALUE_DELTA = 0;
        private static final int VALUE_TAIL = 1;
        private static final int VALUE_RESULT = 2;
        private static final String TYPE_FUNCTION_CALL = "function_call";
        private static final String MEMBER_ARGUMENTS = "arguments";
        private static final DirectBufferEx QUOTE = new UnsafeBufferEx("\"".getBytes(UTF_8));
        private static final DirectBufferEx COMMA = new UnsafeBufferEx(",".getBytes(UTF_8));
        private static final String MEMBER_USAGE = "usage";
        private static final Predicate<String> ALL_MEMBERS = name -> true;
        private static final String MEMBER_OBFUSCATION = "obfuscation";
        private static final String OBFUSCATION_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        private static final String OBJECT_RESPONSE = "response";
        private static final String STATUS_COMPLETED = "completed";
        private static final String STATUS_INCOMPLETE = "incomplete";
        private static final String STATUS_IN_PROGRESS = "in_progress";
        private static final String STATUS_FAILED = "failed";
        private static final String REASON_MAX_OUTPUT_TOKENS = "max_output_tokens";
        private static final String REASON_CONTENT_FILTER = "content_filter";
        private static final String ERROR_TYPE_SERVER = "server_error";

        private static final String EVENT_CREATED = "response.created";
        private static final String EVENT_IN_PROGRESS = "response.in_progress";
        private static final String EVENT_ITEM_ADDED = "response.output_item.added";
        private static final String EVENT_PART_ADDED = "response.content_part.added";
        private static final String EVENT_TEXT_DELTA = "response.output_text.delta";
        private static final String EVENT_TEXT_DONE = "response.output_text.done";
        private static final String EVENT_PART_DONE = "response.content_part.done";
        private static final String EVENT_ITEM_DONE = "response.output_item.done";
        private static final String EVENT_COMPLETED = "response.completed";
        private static final String EVENT_INCOMPLETE = "response.incomplete";
        private static final String EVENT_FAILED = "response.failed";
        private static final String EVENT_ERROR = "error";
        private static final Map<String, DirectBufferEx> EVENT_ATTRIBUTES = eventAttributes();

        private static Map<String, DirectBufferEx> eventAttributes()
        {
            final Map<String, DirectBufferEx> attributes = new HashMap<>();

            for (String name : List.of(EVENT_CREATED, EVENT_IN_PROGRESS, EVENT_ITEM_ADDED, EVENT_PART_ADDED, EVENT_TEXT_DELTA,
                EVENT_TEXT_DONE, EVENT_PART_DONE, EVENT_ITEM_DONE, EVENT_COMPLETED, EVENT_INCOMPLETE, EVENT_FAILED, EVENT_ERROR))
            {
                attributes.put(name, eventType(name));
            }

            for (String name : LlmOpenaiResponsesItems.events())
            {
                attributes.put(name, eventType(name));
            }

            return attributes;
        }

        private final JsonGeneratorEx generator;

        private String responseId;
        private String responseModel;
        private String responseDialect;

        private int[] eventOffsets;
        private int[] eventLengths;
        private int[] eventFlags;
        private final MutableDirectBufferEx eventAttributesBuffer;
        private final OctetsFW eventAttributesRO = new OctetsFW();
        private int[] eventAttributeOffsets;
        private int[] eventAttributeLengths;
        private int eventAttributesLimit;
        private int eventAttributeOffset;
        private int eventAttributeLength;
        private int eventCount;
        private boolean eventOpen;
        private boolean eventStarted;
        private int eventStart;

        private LlmSectionPipeline controlPipeline;
        private LlmOpenaiControlFromCanonical controlTransform;
        private final LlmSectionPipeline.Output encodeControl = (buffer, offset, length, last) ->
            onEncodeControl(buffer, offset, length);
        private int controlMessage;
        private int controlMode;
        private boolean controlOpen;
        private final byte[] obfuscationBytes = new byte[OBFUSCATION_MIN + OBFUSCATION_RANGE];
        private final UnsafeBufferEx obfuscationBuffer = new UnsafeBufferEx(obfuscationBytes);
        private final LlmControlHold hold = new LlmControlHold(this::heldBuffer);
        private final LlmMemberSplitter splitter = new LlmMemberSplitter(this::onEncodeMember);
        private int heldSlot = NO_SLOT;
        private String controlName;
        private boolean usageVerbatim;

        private int kind;
        private int sequence;
        private int itemCount;
        private int itemMessage = NONE;
        private int itemParts;
        private long encodeTraceId;
        private long encodeAuthorization;
        private String responseStatus = STATUS_COMPLETED;
        private String incompleteReason;

        private LlmContentAccumulator accumulator;
        private int openItem;
        private int partValue;
        private int partMode;
        private int partKind;
        private int partAnnotations = NONE;
        private byte[] partKinds;
        private final LlmTextPipeline reasoningText;
        private final LlmEdgeFilter reasoningIdFilter;
        private final LlmEdgeFilter reasoningMembers;
        private final LlmSectionPipeline.Output citationsOutput;
        private LlmSectionPipeline reasoningIdPipeline;
        private LlmSectionPipeline reasoningMembersPipeline;
        private LlmSectionPipeline citationsPipeline;
        private String reasoningId;
        private byte[] reasoningIdBytes;
        private int reasoningIdLength;
        private byte[] elementBytes;
        private UnsafeBufferEx elementBuffer;
        private int elementLength;
        private int elementDepth;
        private int elementLex;
        private int annotationCount;
        private String[] itemOriginals;
        private String[] itemTypes;
        private String[] itemIds;
        private String[] itemCallIds;
        private String[] itemNames;
        private int[] itemFirst;
        private int[] itemCounts;
        private String sectionItemType;
        private String valueMember;
        private LlmSectionPipeline valuePipeline;
        private LlmSectionPipeline restPipeline;
        private LlmSectionPipeline argumentsPipeline;
        private LlmSectionPipeline extrasPipeline;
        private LlmSectionPipeline membersPipeline;
        private LlmSectionPipeline resultPipeline;
        private LlmOpenaiResponsesServerToolFromCanonical membersTransform;
        private final Map<String, LlmSectionPipeline> valuePipelines = new HashMap<>();
        private final LlmSectionPipeline.Output argumentsOutput;
        private final LlmStringFilter valueFilter;
        private final LlmEdgeFilter tailFilter;
        private final LlmEdgeFilter membersFilter;
        private final byte[] tailBytes;
        private final UnsafeBufferEx tailBuffer;
        private int tailLength;
        private byte[] escapeBytes;
        private UnsafeBufferEx escapeBuffer;
        private boolean membersStarted;
        private int repeatedBytes;
        private byte[] deltaBytes;
        private UnsafeBufferEx deltaBuffer;
        private int deltaLength;

        private void beginStep()
        {
            repeatedBytes = 0;
            beginEncode();
        }

        private void beginEncode()
        {
            generator.wrap(copyBuffer, 0, copyBuffer.capacity());
            eventAttributesLimit = 0;

            if (eventOpen)
            {
                eventStart = 0;
            }
        }

        private void doEncodeFlush(
            int reserved,
            long budgetId,
            long traceId,
            long authorization)
        {
            if (server.streaming)
            {
                if (eventOpen && eventStart != NO_EVENT)
                {
                    emitEvent(encodeLength(), false);
                    eventStart = NO_EVENT;
                }

                final int count = eventCount;

                for (int index = 0; index < count; index++)
                {
                    final int attributesLength = eventAttributeLengths[index];
                    final OctetsFW attributes = attributesLength > 0
                        ? eventAttributesRO.wrap(eventAttributesBuffer, eventAttributeOffsets[index],
                            eventAttributeOffsets[index] + attributesLength)
                        : EMPTY_OCTETS;

                    server.doNetData(copyBuffer, eventOffsets[index], eventLengths[index], index == count - 1 ? reserved : 0,
                        eventFlags[index], attributes, budgetId, traceId, authorization);
                }

                eventCount = 0;

                if (count == 0)
                {
                    ackReply(traceId);
                }
            }
            else if (encodeLength() > 0)
            {
                server.doNetData(copyBuffer, 0, encodeLength(), reserved, Flags.COMPLETE, EMPTY_OCTETS, budgetId,
                    traceId, authorization);
            }
            else
            {
                ackReply(traceId);
            }
        }

        private void doEncodeBegin()
        {
            if (server.streaming)
            {
                eventResponse(EVENT_CREATED);
                eventResponse(EVENT_IN_PROGRESS);
            }
            else
            {
                generator.writeStartObject();
                writeEnvelope();
            }
        }

        private void doEncodeSection(
            LlmSectionType type,
            int message,
            String extension)
        {
            sectionItemType = extension;

            kind = switch (type)
            {
            case CONTROL -> KIND_CONTROL;
            case ASSISTANT_TEXT -> KIND_TEXT;
            case ASSISTANT_REFUSAL -> KIND_REFUSAL;
            case ASSISTANT_REASONING -> NAME.equals(responseDialect) ? KIND_REASONING : KIND_SKIPPED;
            case CITATIONS -> citable(message) ? KIND_CITATIONS : KIND_SKIPPED;
            case TOOL_CALL -> KIND_CALL;
            case SERVER_TOOL_CALL -> providerCall(extension) ? KIND_PROVIDER : KIND_SKIPPED;
            case SERVER_TOOL_RESULT -> awaitingResult(extension) ? KIND_RESULT : KIND_SKIPPED;
            default -> KIND_SKIPPED;
            };

            switch (kind)
            {
            case KIND_TEXT:
                sectionPart(message, PART_KIND_TEXT);
                textPipeline.begin();
                break;
            case KIND_REFUSAL:
                sectionPart(message, PART_KIND_REFUSAL);
                textPipeline.begin();
                break;
            case KIND_REASONING:
                sectionReasoning();
                break;
            case KIND_CITATIONS:
                sectionCitations();
                break;
            case KIND_CONTROL:
                controlMessage = message;
                controlMode = CONTROL_DROP;
                splitter.begin();
                break;
            case KIND_CALL:
                sectionCall();
                break;
            case KIND_PROVIDER:
                sectionProvider();
                break;
            case KIND_RESULT:
                sectionResult();
                break;
            default:
                break;
            }
        }

        private boolean citable(
            int message)
        {
            return partMode == PART_PENDING &&
                partKind == PART_KIND_TEXT &&
                openItem == ITEM_MESSAGE &&
                itemMessage == message;
        }

        private boolean providerCall(
            String extension)
        {
            return NAME.equals(responseDialect) &&
                extension != null &&
                LlmOpenaiResponsesItems.kind(extension) == LlmOpenaiResponsesItems.KIND_CALL;
        }

        private boolean awaitingResult(
            String extension)
        {
            return NAME.equals(responseDialect) &&
                openItem == ITEM_PROVIDER &&
                extension != null &&
                extension.equals(itemTypes[itemCount - 1]) &&
                LlmOpenaiResponsesItems.split(extension);
        }

        private void doEncodeData(
            long traceId,
            DirectBufferEx payload,
            int payloadOffset,
            int payloadLength,
            boolean fin)
        {
            boolean accepted = true;

            switch (kind)
            {
            case KIND_TEXT, KIND_REFUSAL:
                textPipeline.feed(payload, payloadOffset, payloadLength, fin);
                break;
            case KIND_REASONING:
                accepted = reasoningText.feed(payload, payloadOffset, payloadLength, fin) &&
                    reasoningIdPipeline.feed(payload, payloadOffset, payloadLength, fin, reasoningIdFilter) &&
                    reasoningMembersPipeline.feed(payload, payloadOffset, payloadLength, fin, reasoningMembers);
                break;
            case KIND_CITATIONS:
                accepted = citationsPipeline.feed(payload, payloadOffset, payloadLength, fin, citationsOutput);
                break;
            case KIND_CONTROL:
                accepted = splitter.put(payload, payloadOffset, payloadLength, fin) && !hold.failed();
                break;
            case KIND_CALL:
                accepted = valuePipeline.feed(payload, payloadOffset, payloadLength, fin, argumentsOutput) &&
                    restPipeline.feed(payload, payloadOffset, payloadLength, fin, tailFilter);
                break;
            case KIND_PROVIDER:
                accepted = (valuePipeline == null ||
                    valuePipeline.feed(payload, payloadOffset, payloadLength, fin, valueFilter)) &&
                    restPipeline.feed(payload, payloadOffset, payloadLength, fin, valueMember != null
                        ? tailFilter
                        : membersFilter);
                break;
            case KIND_RESULT:
                accepted = restPipeline.feed(payload, payloadOffset, payloadLength, fin, membersFilter);
                break;
            default:
                break;
            }

            if (!accepted)
            {
                server.cleanup(traceId);
            }
            else if (deltaLength > 0)
            {
                eventValueDelta(LlmOpenaiResponsesItems.deltaEvent(itemTypes[itemCount - 1]), itemCount - 1, deltaBuffer, 0,
                    deltaLength);
                deltaLength = 0;
            }
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmOpenaiControlFromCanonical(NAME.equals(responseDialect));
                controlPipeline = new LlmSectionPipeline(controlTransform, blockBuffer());
            }

            return controlPipeline;
        }

        private void sectionPart(
            int message,
            int kind)
        {
            if (openItem != ITEM_MESSAGE || itemMessage != message)
            {
                closeOpenItem(STATUS_COMPLETED);
            }

            if (openItem == ITEM_NONE)
            {
                openMessageItem(message);
            }

            closePart();
            openPart(kind);
        }

        private void sectionReasoning()
        {
            if (openItem != ITEM_REASONING)
            {
                closeOpenItem(STATUS_COMPLETED);
                openReasoningItem();
            }

            membersStarted = false;
            reasoningText.begin();
            reasoningIdPipeline().begin(replyAttributes);
            reasoningMembersPipeline().begin(replyAttributes);
            reasoningIdFilter.reset();
            reasoningMembers.reset();
            reasoningIdLength = 0;
        }

        private void sectionCitations()
        {
            annotationCount = 0;
            elementDepth = 0;
            elementLength = 0;
            elementLex = LEX_NONE;
            citationsPipeline().begin(replyAttributes);

            if (server.streaming)
            {
                partAnnotations = accumulator.open();
            }
        }

        private int registerItem(
            String type)
        {
            if (itemCount == 0 && !server.streaming)
            {
                generator.writeStartArray(MEMBER_OUTPUT);
            }

            if (itemCount == itemTypes.length)
            {
                final int capacity = itemCount << 1;

                itemTypes = Arrays.copyOf(itemTypes, capacity);
                itemIds = Arrays.copyOf(itemIds, capacity);
                itemCallIds = Arrays.copyOf(itemCallIds, capacity);
                itemNames = Arrays.copyOf(itemNames, capacity);
                itemOriginals = Arrays.copyOf(itemOriginals, capacity);
                itemFirst = Arrays.copyOf(itemFirst, capacity);
                itemCounts = Arrays.copyOf(itemCounts, capacity);
            }

            final int index = itemCount++;

            itemTypes[index] = type;
            itemIds[index] = LlmOpenaiResponsesItems.prefix(type) + "_" + (index + 1);
            itemCallIds[index] = null;
            itemNames[index] = null;
            itemOriginals[index] = null;
            itemFirst[index] = accumulator != null ? accumulator.count() : 0;
            itemCounts[index] = 0;

            return index;
        }

        private void bindAttributes(
            int index)
        {
            itemCallIds[index] = replyAttributes.toolCallId();
            itemNames[index] = replyAttributes.toolName();
        }

        private void closeOpenItem(
            String status)
        {
            switch (openItem)
            {
            case ITEM_MESSAGE:
                closePart();
                closeItem(status);
                break;
            case ITEM_REASONING:
                finishReasoningItem();
                break;
            case ITEM_PROVIDER:
                finishItem(itemCount - 1);
                break;
            default:
                break;
            }
        }

        private void openMessageItem(
            int message)
        {
            final int index = registerItem(TYPE_MESSAGE);

            openItem = ITEM_MESSAGE;
            itemMessage = message;
            itemParts = 0;

            if (server.streaming)
            {
                eventItemAdded();
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_ID, itemId(index))
                    .write(MEMBER_TYPE, TYPE_MESSAGE)
                    .writeStartArray(MEMBER_CONTENT);
            }
        }

        private void openPart(
            int kind)
        {
            itemParts++;
            partKind = kind;
            partMode = PART_ACTIVE;
            partAnnotations = NONE;
            annotationCount = 0;

            if (server.streaming)
            {
                partValue = accumulator.open();
                markPart(partValue, kind);
                eventPartAdded();
            }
            else if (kind == PART_KIND_REFUSAL)
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_REFUSAL)
                    .writeKey(MEMBER_REFUSAL)
                    .writeStartEscaped();
                partMode = PART_DIRECT;
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_OUTPUT_TEXT)
                    .writeKey(MEMBER_TEXT)
                    .writeStartEscaped();
            }
        }

        private void markPart(
            int value,
            int kind)
        {
            if (partKinds.length <= value)
            {
                partKinds = Arrays.copyOf(partKinds, Math.max(partKinds.length << 1, value + 1));
            }

            partKinds[value] = (byte) kind;
        }

        private void endPart()
        {
            if (server.streaming)
            {
                accumulator.close();
                partMode = PART_PENDING;
            }
            else if (partMode == PART_DIRECT)
            {
                closePart();
            }
            else
            {
                generator.writeEndEscaped();
                partMode = PART_PENDING;
            }
        }

        private void closePart()
        {
            if (partMode != PART_NONE)
            {
                if (server.streaming)
                {
                    accumulator.close();

                    if (partAnnotations == NONE)
                    {
                        partAnnotations = accumulator.open();
                        accumulator.close();
                    }

                    if (partKind == PART_KIND_REFUSAL)
                    {
                        eventRefusalDone();
                    }
                    else
                    {
                        eventTextDone();
                    }

                    eventPartDone();
                    doEncodeFlush(0, 0L, encodeTraceId, encodeAuthorization);
                    beginStep();
                }
                else if (partMode == PART_DIRECT)
                {
                    generator.writeEndEscaped().writeEnd();
                }
                else
                {
                    closeTextPart();
                }

                partMode = PART_NONE;
            }
        }

        private void closeTextPart()
        {
            if (partMode == PART_ACTIVE)
            {
                generator.writeEndEscaped();
            }

            if (annotationCount > 0)
            {
                generator.writeSegment(ARRAY_CLOSE, 0, 1, Completion.COMPLETE);
            }
            else
            {
                generator.writeStartArray(MEMBER_ANNOTATIONS).writeEnd();
            }

            generator.writeStartArray(MEMBER_LOGPROBS).writeEnd().writeEnd();
        }

        private void ensureEncodeRoom(
            int length)
        {
            if (copyBuffer.capacity() - encodeLength() - CHUNK_RESERVE < length)
            {
                doEncodeFlush(0, 0L, encodeTraceId, encodeAuthorization);
                beginEncode();
            }
        }

        private void closeItem(
            String status)
        {
            if (server.streaming)
            {
                itemCounts[itemCount - 1] = accumulator.count() - itemFirst[itemCount - 1];
                eventItemDone(status);
            }
            else
            {
                generator.writeEnd()
                    .write(MEMBER_ROLE, ROLE_ASSISTANT)
                    .write(MEMBER_STATUS, status)
                    .writeEnd();
            }

            itemMessage = NONE;
            openItem = ITEM_NONE;
        }

        private void onEncodeText(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (server.streaming)
                {
                    accumulate(payload, offset, length);

                    if (partKind == PART_KIND_REFUSAL)
                    {
                        eventRefusalDelta(payload, offset, length);
                    }
                    else
                    {
                        eventTextDelta(payload, offset, length);
                    }
                }
                else
                {
                    ensureEncodeRoom(length * RESPONSE_ESCAPE_EXPANSION);
                    generator.writeSegment(payload, offset, length);
                }
            }
        }

        private void accumulate(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            ensureEscape(length);

            final int escaped = LlmJsonEscape.escape(payload, offset, length, escapeBytes);

            accumulator.append(escapeBuffer, 0, escaped);
        }

        private void ensureEscape(
            int length)
        {
            final int capacity = length * LlmJsonEscape.EXPANSION;

            if (escapeBytes.length < capacity)
            {
                escapeBytes = new byte[capacity];
                escapeBuffer = new UnsafeBufferEx(escapeBytes);
            }
        }

        private void doEncodeEnd()
        {
            switch (kind)
            {
            case KIND_TEXT:
                endPart();
                break;
            case KIND_REFUSAL, KIND_CITATIONS:
                closePart();
                break;
            case KIND_REASONING:
                endReasoning();
                break;
            case KIND_CALL:
                endCall();
                break;
            case KIND_PROVIDER:
                endProvider();
                break;
            case KIND_RESULT:
                finishItem(itemCount - 1);
                break;
            default:
                break;
            }

            kind = KIND_NONE;
        }

        private void sectionCall()
        {
            closeOpenItem(STATUS_COMPLETED);

            final int index = registerItem(TYPE_FUNCTION_CALL);

            openItem = ITEM_CALL;
            bindAttributes(index);
            valueMember = MEMBER_ARGUMENTS;
            valuePipeline = argumentsPipeline();
            valuePipeline.begin(replyAttributes);
            restPipeline = extrasPipeline();
            restPipeline.begin(replyAttributes);
            tailFilter.reset();
            tailLength = 0;

            beginItem(index);
        }

        private void sectionProvider()
        {
            closeOpenItem(STATUS_COMPLETED);

            final int index = registerItem(sectionItemType);

            openItem = ITEM_PROVIDER;
            bindAttributes(index);
            valueMember = LlmOpenaiResponsesItems.deltaMember(sectionItemType);
            valuePipeline = valueMember != null ? memberPipeline(valueMember) : null;
            restPipeline = membersPipeline(valueMember);

            if (valuePipeline != null)
            {
                valuePipeline.begin(replyAttributes);
                valueFilter.reset();
            }

            restPipeline.begin(replyAttributes);
            tailFilter.reset();
            membersFilter.reset();
            membersStarted = false;
            tailLength = 0;

            beginItem(index);
        }

        private void sectionResult()
        {
            restPipeline = resultPipeline();
            restPipeline.begin(replyAttributes);
            membersFilter.reset();
            membersStarted = false;

            if (server.streaming)
            {
                accumulator.open();
            }
        }

        private void beginItem(
            int index)
        {
            final String type = itemTypes[index];

            if (server.streaming)
            {
                accumulator.open();
                eventItemAdded(index, valueMember);

                for (String name : LlmOpenaiResponsesItems.initEvents(type))
                {
                    eventStatus(name, index);
                }

                if (valueMember == null)
                {
                    accumulator.close();
                    accumulator.open();
                    beginItemDone(index);
                }
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_ID, itemId(index))
                    .write(MEMBER_TYPE, type);

                if (valueMember != null)
                {
                    generator.writeKey(valueMember);
                    generator.writeSegment(QUOTE, 0, 1, Completion.INCOMPLETE);
                }
            }
        }

        private void endCall()
        {
            final int index = itemCount - 1;

            bindAttributes(index);
            endDelta(index);
            finishItem(index);
        }

        private void endProvider()
        {
            final int index = itemCount - 1;
            final String type = itemTypes[index];

            bindAttributes(index);

            if (valueMember != null)
            {
                endDelta(index);
            }
            else
            {
                writeFooter(index);
            }

            if (!LlmOpenaiResponsesItems.split(type))
            {
                finishItem(index);
            }
        }

        private void endDelta(
            int index)
        {
            final String type = itemTypes[index];

            if (server.streaming)
            {
                final int value = itemFirst[index] + VALUE_DELTA;

                accumulator.close();
                accumulator.open();
                accumulator.append(tailBuffer, 0, tailLength);
                accumulator.close();

                eventValueDone(LlmOpenaiResponsesItems.doneEvent(type), valueMember, index, value);

                for (String name : LlmOpenaiResponsesItems.doneEvents(type))
                {
                    eventStatus(name, index);
                }

                beginItemDone(index);
                generator.writeKey(valueMember);
                repeatString(value);
            }
            else
            {
                generator.writeSegment(QUOTE, 0, 1, Completion.COMPLETE);
            }

            writeTail();
            writeFooter(index);
        }

        private void finishItem(
            int index)
        {
            if (server.streaming)
            {
                accumulator.close();
                itemCounts[index] = accumulator.count() - itemFirst[index];
                endItemDone(index);
            }
            else
            {
                generator.write(MEMBER_STATUS, STATUS_COMPLETED).writeEnd();
            }

            openItem = ITEM_NONE;
        }

        private void writeTail()
        {
            if (tailLength > 0)
            {
                generator.writeSegment(COMMA, 0, 1);
                generator.writeSegment(tailBuffer, 0, tailLength);
            }
        }

        private void writeFooter(
            int index)
        {
            final String type = itemTypes[index];

            if (LlmOpenaiResponsesItems.hasCallId(type))
            {
                generator.write(MEMBER_CALL_ID, itemCallIds[index] != null ? itemCallIds[index] : "");
            }

            if (LlmOpenaiResponsesItems.hasName(type))
            {
                generator.write(MEMBER_NAME, itemNames[index] != null ? itemNames[index] : "");
            }
        }

        private void onArguments(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                ensureEscape(length);

                final int escaped = LlmJsonEscape.escape(buffer, offset, length, escapeBytes);

                onDelta(escapeBuffer, 0, escaped);
            }
        }

        private void onDelta(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (server.streaming)
                {
                    accumulator.append(buffer, offset, length);

                    if (deltaBytes.length < deltaLength + length)
                    {
                        deltaBytes = Arrays.copyOf(deltaBytes, Math.max(deltaBytes.length << 1, deltaLength + length));
                        deltaBuffer = new UnsafeBufferEx(deltaBytes);
                    }

                    buffer.getBytes(offset, deltaBytes, deltaLength, length);
                    deltaLength += length;
                }
                else
                {
                    generator.writeSegment(buffer, offset, length, Completion.INCOMPLETE);
                }
            }
        }

        private void onTail(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0 && tailLength + length <= TAIL_MAX)
            {
                tailBuffer.putBytes(tailLength, buffer, offset, length);
                tailLength += length;
            }
        }

        private void onMembers(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (!membersStarted)
                {
                    membersStarted = true;
                    generator.writeSegment(COMMA, 0, 1);
                }

                generator.writeSegment(buffer, offset, length);

                if (server.streaming)
                {
                    accumulator.append(buffer, offset, length);
                }
            }
        }

        private MutableDirectBufferEx blockBuffer()
        {
            return new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]);
        }

        private LlmSectionPipeline argumentsPipeline()
        {
            if (argumentsPipeline == null)
            {
                argumentsPipeline = new LlmSectionPipeline(new LlmToolCallToValue(), blockBuffer());
            }

            return argumentsPipeline;
        }

        private LlmSectionPipeline extrasPipeline()
        {
            if (extrasPipeline == null)
            {
                final LlmOpenaiResponsesServerToolFromCanonical transform =
                    new LlmOpenaiResponsesServerToolFromCanonical(NAME.equals(responseDialect));

                transform.configure(LlmOpenaiResponsesServerToolFromCanonical.PART_EXTRAS, TYPE_FUNCTION_CALL, null);
                extrasPipeline = new LlmSectionPipeline(transform, blockBuffer());
            }

            return extrasPipeline;
        }

        private LlmSectionPipeline memberPipeline(
            String member)
        {
            LlmSectionPipeline pipeline = valuePipelines.get(member);

            if (pipeline == null)
            {
                pipeline = new LlmSectionPipeline(new LlmOpenaiResponsesMemberToValue(member),
                    blockBuffer());
                valuePipelines.put(member, pipeline);
            }

            return pipeline;
        }

        private LlmSectionPipeline membersPipeline(
            String exclude)
        {
            if (membersPipeline == null)
            {
                membersTransform = new LlmOpenaiResponsesServerToolFromCanonical(NAME.equals(responseDialect));
                membersPipeline = new LlmSectionPipeline(membersTransform, blockBuffer());
            }

            membersTransform.configure(LlmOpenaiResponsesServerToolFromCanonical.PART_MEMBERS, sectionItemType, exclude);

            return membersPipeline;
        }

        private LlmSectionPipeline resultPipeline()
        {
            if (resultPipeline == null)
            {
                final LlmOpenaiResponsesServerToolFromCanonical transform =
                    new LlmOpenaiResponsesServerToolFromCanonical(NAME.equals(responseDialect));

                transform.configure(LlmOpenaiResponsesServerToolFromCanonical.PART_RESULT, sectionItemType, null);
                resultPipeline = new LlmSectionPipeline(transform, blockBuffer());
            }

            return resultPipeline;
        }

        private void repeatString(
            int value)
        {
            final int length = accumulator.length(value);

            generator.writeSegment(QUOTE, 0, 1, Completion.INCOMPLETE);

            if (length > 0 && length + REPEAT_RESERVE <= repeatRoom())
            {
                repeatedBytes += length + REPEAT_RESERVE;
                repeatSegments(value, length, true);
            }

            generator.writeSegment(QUOTE, 0, 1, Completion.COMPLETE);
        }

        private void repeatArray(
            int value)
        {
            final int length = accumulator.length(value);

            generator.writeSegment(ARRAY_OPEN, 0, 1, Completion.INCOMPLETE);

            if (length > 0 && length + REPEAT_RESERVE <= repeatRoom())
            {
                repeatedBytes += length + REPEAT_RESERVE;
                repeatSegments(value, length, true);
            }

            generator.writeSegment(ARRAY_CLOSE, 0, 1, Completion.COMPLETE);
        }

        private void repeatMembers(
            int value)
        {
            final int length = accumulator.length(value);

            if (length > 0 && length + REPEAT_RESERVE <= repeatRoom())
            {
                repeatedBytes += length + REPEAT_RESERVE;
                generator.writeSegment(COMMA, 0, 1);
                repeatSegments(value, length, false);
            }
        }

        private void repeatSegments(
            int value,
            int length,
            boolean string)
        {
            final MutableDirectBufferEx slot = accumulator.buffer();
            final int offset = accumulator.offset(value);

            for (int index = 0; index < length; )
            {
                int room = copyBuffer.capacity() - encodeLength() - CHUNK_RESERVE;

                if (room < TEXT_CHUNK)
                {
                    doEncodeFlush(0, 0L, encodeTraceId, encodeAuthorization);
                    beginEncode();
                    room = copyBuffer.capacity() - encodeLength() - CHUNK_RESERVE;
                }

                final int take = Math.min(length - index, room);

                if (string)
                {
                    generator.writeSegment(slot, offset + index, take, Completion.INCOMPLETE);
                }
                else
                {
                    generator.writeSegment(slot, offset + index, take);
                }

                index += take;
            }
        }

        private int repeatRoom()
        {
            return Math.max(0, encodePool.slotCapacity() / REPEAT_STEP_DIVISOR - repeatedBytes);
        }

        private void onEncodeControl(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            final String name = controlTransform.member();

            if (name != null)
            {
                if (!controlOpen)
                {
                    controlOpen = true;
                    onEncodeControlBegin(name);
                }

                if (controlMode == CONTROL_HELD)
                {
                    onEncodeControlHeld(payload, offset, length);
                }
                else if (controlMode == CONTROL_INLINE && length > 0)
                {
                    ensureEncodeRoom(length);
                    generator.writeSegment(payload, offset, length, Completion.INCOMPLETE);
                }
            }
        }

        private void onEncodeControlBegin(
            String name)
        {
            if (controlMessage != NONE)
            {
                controlMode = CONTROL_DROP;
            }
            else
            {
                controlMode = server.streaming || itemCount > 0 ? CONTROL_HELD : CONTROL_INLINE;
            }

            if (controlMode == CONTROL_INLINE)
            {
                controlName = name;
                generator.writeKey(name);
            }
            else if (controlMode == CONTROL_HELD && !hold.begin(name))
            {
                controlMode = CONTROL_DROP;
            }
        }


        private boolean onEncodeMember(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean init,
            boolean last)
        {
            if (init)
            {
                controlOpen = false;
                controlPipeline().begin(replyAttributes);
            }

            final boolean accepted = controlPipeline.feed(buffer, offset, length, last, encodeControl);

            if (last)
            {
                onEncodeControlEnd();
            }

            return accepted;
        }

        private void onEncodeControlHeld(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (!hold.put(payload, offset, length))
            {
                controlMode = CONTROL_DROP;
            }
        }


        private void onEncodeControlEnd()
        {
            if (controlMode == CONTROL_INLINE)
            {
                generator.writeSegment(EMPTY_VALUE, 0, 0, Completion.COMPLETE);
                usageVerbatim |= MEMBER_USAGE.equals(controlName);
            }
            else if (controlMode == CONTROL_HELD)
            {
                usageVerbatim |= MEMBER_USAGE.equals(hold.end());
            }

            controlMode = CONTROL_DROP;
        }


        private void writeControls()
        {
            hold.write(generator, ALL_MEMBERS);
        }

        private MutableDirectBufferEx heldBuffer()
        {
            if (heldSlot == NO_SLOT)
            {
                heldSlot = holdPool.acquire(replyId);
            }

            return heldSlot != NO_SLOT ? holdPool.buffer(heldSlot) : null;
        }

        private void cleanupHeld()
        {
            hold.reset();

            if (heldSlot != NO_SLOT)
            {
                holdPool.release(heldSlot);
                heldSlot = NO_SLOT;
            }
        }


        private void writeObfuscation()
        {
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            final int length = OBFUSCATION_MIN + random.nextInt(OBFUSCATION_RANGE);

            for (int index = 0; index < length; index++)
            {
                obfuscationBytes[index] = (byte) OBFUSCATION_ALPHABET.charAt(random.nextInt(OBFUSCATION_ALPHABET.length()));
            }

            generator.writeKey(MEMBER_OBFUSCATION).writeStartEscaped();
            generator.writeSegment(obfuscationBuffer, 0, length);
            generator.writeEndEscaped();
        }

        private void doEncodeFinish(
            String finishReason,
            int inputTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            finishStatus(finishReason);

            closeOpenItem(responseStatus);

            if (server.streaming)
            {
                final String name = STATUS_INCOMPLETE.equals(responseStatus) ? EVENT_INCOMPLETE : EVENT_COMPLETED;

                eventBegin(generator.length(), name);
                generator.writeStartObject().write(MEMBER_TYPE, name).writeKey(OBJECT_RESPONSE);
                generator.writeStartObject();
                writeEnvelope();
                generator.writeStartArray(MEMBER_OUTPUT);

                for (int index = 0; index < itemCount; index++)
                {
                    writeItem(index, index == itemCount - 1 ? responseStatus : STATUS_COMPLETED);
                }

                generator.writeEnd();
                writeResponseTail(inputTokens, cacheReadTokens, cacheWriteTokens, outputTokens, reasoningTokens, totalTokens);
                generator.writeEnd();
                generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
                eventEnd(generator.length());
            }
            else
            {
                if (itemCount == 0)
                {
                    generator.writeStartArray(MEMBER_OUTPUT);
                }

                generator.writeEnd();
                writeResponseTail(inputTokens, cacheReadTokens, cacheWriteTokens, outputTokens, reasoningTokens, totalTokens);
                generator.writeEnd();
            }
        }

        private void finishStatus(
            String finishReason)
        {
            responseStatus = STATUS_COMPLETED;
            incompleteReason = null;

            if (finishReason != null)
            {
                switch (finishReason)
                {
                case "length":
                    responseStatus = STATUS_INCOMPLETE;
                    incompleteReason = REASON_MAX_OUTPUT_TOKENS;
                    break;
                case "content-filter":
                    responseStatus = STATUS_INCOMPLETE;
                    incompleteReason = REASON_CONTENT_FILTER;
                    break;
                default:
                    if (NAME.equals(responseDialect) && finishReason.startsWith(VENDOR_PREFIX))
                    {
                        responseStatus = STATUS_INCOMPLETE;
                        incompleteReason = finishReason.substring(VENDOR_PREFIX.length());
                    }
                    break;
                }
            }
        }

        private void writeEnvelope()
        {
            if (responseId != null)
            {
                generator.write(MEMBER_ID, responseId);
            }

            generator.write(MEMBER_OBJECT, OBJECT_RESPONSE);

            if (responseModel != null)
            {
                generator.write(MEMBER_MODEL, responseModel);
            }
        }

        private void writeResponseTail(
            int inputTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            generator.write(MEMBER_STATUS, responseStatus);

            if (incompleteReason != null)
            {
                generator.writeStartObject(MEMBER_INCOMPLETE_DETAILS).write(MEMBER_REASON, incompleteReason).writeEnd();
            }

            writeControls();

            if (!usageVerbatim)
            {
                putUsage(inputTokens, cacheReadTokens, cacheWriteTokens, outputTokens, reasoningTokens, totalTokens);
            }
        }

        private void writeItem(
            int index,
            String status)
        {
            if (TYPE_MESSAGE.equals(itemTypes[index]))
            {
                writeMessageItem(index, status);
            }
            else if (TYPE_REASONING.equals(itemTypes[index]))
            {
                writeReasoningItem(index);
            }
            else
            {
                writeToolItem(index);
            }
        }

        private void writeToolItem(
            int index)
        {
            final String type = itemTypes[index];
            final int first = itemFirst[index];
            final int count = itemCounts[index];
            final String member = LlmOpenaiResponsesItems.valueMember(type);

            generator.writeStartObject()
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, type)
                .write(MEMBER_STATUS, STATUS_COMPLETED);

            if (member != null)
            {
                generator.writeKey(member);
                repeatString(first + VALUE_DELTA);
            }

            if (count > VALUE_TAIL)
            {
                repeatMembers(first + VALUE_TAIL);
            }

            writeFooter(index);

            if (count > VALUE_RESULT)
            {
                repeatMembers(first + VALUE_RESULT);
            }

            generator.writeEnd();
        }

        private void writeMessageItem(
            int index,
            String status)
        {
            generator.writeStartObject()
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, TYPE_MESSAGE)
                .write(MEMBER_STATUS, status)
                .writeStartArray(MEMBER_CONTENT);

            final int first = itemFirst[index];
            final int last = first + itemCounts[index];

            for (int part = first; part < last; part += PART_VALUES)
            {
                writePart(part);
            }

            generator.writeEnd()
                .write(MEMBER_ROLE, ROLE_ASSISTANT)
                .writeEnd();
        }

        private void writePart(
            int part)
        {
            if (partKinds[part] == PART_KIND_REFUSAL)
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_REFUSAL)
                    .writeKey(MEMBER_REFUSAL);
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_OUTPUT_TEXT)
                    .writeKey(MEMBER_ANNOTATIONS);
                repeatArray(part + 1);
                generator.writeStartArray(MEMBER_LOGPROBS).writeEnd()
                    .writeKey(MEMBER_TEXT);
            }

            writeCompleted(part);
            generator.writeEnd();
        }

        private void writeCompleted(
            int part)
        {
            repeatString(part);
        }

        private String itemId(
            int index)
        {
            return itemIds[index];
        }

        private void eventResponse(
            String name)
        {
            eventBegin(generator.length(), name);
            generator.writeStartObject().write(MEMBER_TYPE, name).writeStartObject(OBJECT_RESPONSE);
            writeEnvelope();
            generator.write(MEMBER_STATUS, STATUS_IN_PROGRESS)
                .writeStartArray(MEMBER_OUTPUT).writeEnd()
                .writeEnd();
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            eventEnd(generator.length());
        }

        private void eventItemAdded(
            int index,
            String member)
        {
            eventBegin(generator.length(), EVENT_ITEM_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_ADDED)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, itemTypes[index])
                .write(MEMBER_STATUS, STATUS_IN_PROGRESS);

            if (member != null)
            {
                generator.write(member, "");
            }

            writeFooter(index);

            generator.writeEnd()
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventStatus(
            String name,
            int index)
        {
            eventBegin(generator.length(), name);
            generator.writeStartObject().write(MEMBER_TYPE, name)
                .write(MEMBER_ITEM_ID, itemId(index))
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventValueDelta(
            String name,
            int index,
            DirectBufferEx payload,
            int offset,
            int length)
        {
            eventBegin(generator.length(), name);
            generator.writeStartObject().write(MEMBER_TYPE, name).writeKey(MEMBER_DELTA);
            generator.writeSegment(QUOTE, 0, 1, Completion.INCOMPLETE);
            generator.writeSegment(payload, offset, length, Completion.INCOMPLETE);
            generator.writeSegment(QUOTE, 0, 1, Completion.COMPLETE);
            generator.write(MEMBER_ITEM_ID, itemId(index));

            if (server.obfuscation)
            {
                writeObfuscation();
            }

            generator.write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventValueDone(
            String name,
            String member,
            int index,
            int value)
        {
            eventBegin(generator.length(), name);
            generator.writeStartObject().write(MEMBER_TYPE, name).writeKey(member);
            repeatString(value);
            generator.write(MEMBER_ITEM_ID, itemId(index))
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void beginItemDone(
            int index)
        {
            eventBegin(generator.length(), EVENT_ITEM_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_DONE)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, itemTypes[index])
                .write(MEMBER_STATUS, STATUS_COMPLETED);
        }

        private void endItemDone(
            int index)
        {
            generator.writeEnd()
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventItemAdded()
        {
            eventBegin(generator.length(), EVENT_ITEM_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_ADDED)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(itemCount - 1))
                .write(MEMBER_TYPE, TYPE_MESSAGE)
                .write(MEMBER_STATUS, STATUS_IN_PROGRESS)
                .writeStartArray(MEMBER_CONTENT).writeEnd()
                .write(MEMBER_ROLE, ROLE_ASSISTANT)
                .writeEnd()
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventPartAdded()
        {
            eventBegin(generator.length(), EVENT_PART_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_PART_ADDED)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeStartObject(MEMBER_PART);

            if (partKind == PART_KIND_REFUSAL)
            {
                generator.write(MEMBER_TYPE, TYPE_REFUSAL).write(MEMBER_REFUSAL, "");
            }
            else
            {
                generator.write(MEMBER_TYPE, TYPE_OUTPUT_TEXT)
                    .writeStartArray(MEMBER_ANNOTATIONS).writeEnd()
                    .writeStartArray(MEMBER_LOGPROBS).writeEnd()
                    .write(MEMBER_TEXT, "");
            }

            generator.writeEnd()
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventTextDelta(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            eventBegin(generator.length(), EVENT_TEXT_DELTA);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_TEXT_DELTA)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .writeKey(MEMBER_DELTA)
                .writeStartEscaped();
            generator.writeSegment(payload, offset, length);
            generator.writeEndEscaped();
            generator.write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .writeStartArray(MEMBER_LOGPROBS).writeEnd();

            if (server.obfuscation)
            {
                writeObfuscation();
            }

            generator.write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventTextDone()
        {
            eventBegin(generator.length(), EVENT_TEXT_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_TEXT_DONE)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .writeStartArray(MEMBER_LOGPROBS).writeEnd()
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeKey(MEMBER_TEXT);
            writeCompleted(partValue);
            generator.writeEnd();
            eventEnd(generator.length());
        }

        private void eventPartDone()
        {
            eventBegin(generator.length(), EVENT_PART_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_PART_DONE)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeKey(MEMBER_PART);
            writePart(partValue);
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void openReasoningItem()
        {
            final int index = registerItem(TYPE_REASONING);

            openItem = ITEM_REASONING;
            itemParts = 0;
            partMode = PART_NONE;

            if (server.streaming)
            {
                eventReasoningAdded(index);
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_REASONING)
                    .writeStartArray(MEMBER_SUMMARY);
            }
        }

        private void openSummaryPart()
        {
            itemParts++;
            partMode = PART_ACTIVE;

            if (server.streaming)
            {
                partValue = accumulator.open();
                eventSummaryPartAdded();
            }
            else
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                    .writeKey(MEMBER_TEXT)
                    .writeStartEscaped();
            }
        }

        private void closeSummaryPart()
        {
            if (partMode == PART_ACTIVE)
            {
                if (server.streaming)
                {
                    accumulator.close();
                    eventSummaryTextDone();
                    eventSummaryPartDone();
                }
                else
                {
                    generator.writeEndEscaped().writeEnd();
                }

                partMode = PART_NONE;
            }
        }

        private void onReasoningText(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (length > 0 && !membersStarted)
            {
                if (partMode != PART_ACTIVE)
                {
                    openSummaryPart();
                }

                if (server.streaming)
                {
                    accumulate(payload, offset, length);
                    eventSummaryTextDelta(payload, offset, length);
                }
                else
                {
                    generator.writeSegment(payload, offset, length);
                }
            }
        }

        private void onReasoningId(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            if (length > 0)
            {
                if (reasoningIdBytes.length < reasoningIdLength + length)
                {
                    reasoningIdBytes = Arrays.copyOf(reasoningIdBytes, Math.max(reasoningIdBytes.length << 1,
                        reasoningIdLength + length));
                }

                buffer.getBytes(offset, reasoningIdBytes, reasoningIdLength, length);
                reasoningIdLength += length;
            }

            if (last && reasoningIdLength > 0)
            {
                reasoningId = new String(reasoningIdBytes, 0, reasoningIdLength, UTF_8);
                itemOriginals[itemCount - 1] = reasoningId;
            }
        }

        private void onReasoningMembers(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (!membersStarted)
                {
                    beginReasoningMembers();
                }

                generator.writeSegment(buffer, offset, length);
            }
        }

        private void beginReasoningMembers()
        {
            final int index = itemCount - 1;

            closeSummaryPart();
            membersStarted = true;

            if (server.streaming)
            {
                itemCounts[index] = accumulator.count() - itemFirst[index];

                eventBegin(generator.length(), EVENT_ITEM_DONE);
                generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_DONE)
                    .writeStartObject(MEMBER_ITEM)
                    .write(MEMBER_TYPE, TYPE_REASONING);
                writeSummary(index);
            }
            else
            {
                generator.writeEnd();
            }

            generator.writeSegment(COMMA, 0, 1);
        }

        private void endReasoning()
        {
            if (membersStarted)
            {
                final int index = itemCount - 1;

                if (itemOriginals[index] == null)
                {
                    generator.write(MEMBER_ID, itemId(index));
                }

                if (server.streaming)
                {
                    endItemDone(index);
                }
                else
                {
                    generator.writeEnd();
                }

                membersStarted = false;
                openItem = ITEM_NONE;
            }
            else
            {
                closeSummaryPart();
            }
        }

        private void finishReasoningItem()
        {
            final int index = itemCount - 1;

            closeSummaryPart();

            if (server.streaming)
            {
                itemCounts[index] = accumulator.count() - itemFirst[index];
                eventItemDone(STATUS_COMPLETED);
            }
            else
            {
                generator.writeEnd()
                    .write(MEMBER_ID, reasoningItemId(index))
                    .writeEnd();
            }

            openItem = ITEM_NONE;
        }

        private String reasoningItemId(
            int index)
        {
            return itemOriginals[index] != null ? itemOriginals[index] : itemId(index);
        }

        private void writeReasoningItem(
            int index)
        {
            generator.writeStartObject().write(MEMBER_TYPE, TYPE_REASONING);
            writeSummary(index);
            generator.write(MEMBER_ID, reasoningItemId(index)).writeEnd();
        }

        private void writeSummary(
            int index)
        {
            final int first = itemFirst[index];
            final int last = first + itemCounts[index];

            generator.writeStartArray(MEMBER_SUMMARY);

            for (int part = first; part < last; part++)
            {
                generator.writeStartObject()
                    .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                    .writeKey(MEMBER_TEXT);
                repeatString(part);
                generator.writeEnd();
            }

            generator.writeEnd();
        }

        private void onCitations(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            for (int index = 0; index < length; index++)
            {
                scanElement(buffer.getByte(offset + index));
            }
        }

        private void scanElement(
            byte value)
        {
            if (elementDepth > 0)
            {
                if (elementLength >= 0 && elementLength < ELEMENT_MAX)
                {
                    elementBytes[elementLength++] = value;
                }
                else
                {
                    elementLength = NONE;
                }
            }

            switch (elementLex)
            {
            case LEX_STRING:
                elementLex = value == '\\' ? LEX_ESCAPE : value == '"' ? LEX_NONE : LEX_STRING;
                break;
            case LEX_ESCAPE:
                elementLex = LEX_STRING;
                break;
            default:
                scanStructure(value);
                break;
            }
        }

        private void scanStructure(
            byte value)
        {
            if (value == '"')
            {
                elementLex = LEX_STRING;
            }
            else if (value == '{')
            {
                if (elementDepth++ == 0)
                {
                    elementLength = 0;
                    elementBytes[elementLength++] = value;
                }
            }
            else if (value == '}' && --elementDepth == 0)
            {
                if (elementLength > 0)
                {
                    onAnnotation();
                }

                elementLength = 0;
            }
        }

        private void onAnnotation()
        {
            if (server.streaming)
            {
                if (annotationCount > 0)
                {
                    accumulator.append(COMMA, 0, 1);
                }

                accumulator.append(elementBuffer, 0, elementLength);
                eventAnnotationAdded(annotationCount);
            }
            else
            {
                ensureEncodeRoom(elementLength + 1);

                if (annotationCount == 0)
                {
                    generator.writeKey(MEMBER_ANNOTATIONS);
                    generator.writeSegment(ARRAY_OPEN, 0, 1, Completion.INCOMPLETE);
                }
                else
                {
                    generator.writeSegment(COMMA, 0, 1, Completion.INCOMPLETE);
                }

                generator.writeSegment(elementBuffer, 0, elementLength, Completion.INCOMPLETE);
            }

            annotationCount++;
        }

        private LlmSectionPipeline reasoningIdPipeline()
        {
            if (reasoningIdPipeline == null)
            {
                reasoningIdPipeline = new LlmSectionPipeline(
                    new LlmOpenaiResponsesReasoningToValue(NAME.equals(responseDialect), MEMBER_ID), blockBuffer());
            }

            return reasoningIdPipeline;
        }

        private LlmSectionPipeline reasoningMembersPipeline()
        {
            if (reasoningMembersPipeline == null)
            {
                reasoningMembersPipeline = new LlmSectionPipeline(
                    new LlmOpenaiResponsesReasoningFromCanonical(NAME.equals(responseDialect)), blockBuffer());
            }

            return reasoningMembersPipeline;
        }

        private LlmSectionPipeline citationsPipeline()
        {
            if (citationsPipeline == null)
            {
                citationsPipeline = new LlmSectionPipeline(
                    LlmOpenaiResponsesCitationsFromCanonical.array(NAME.equals(responseDialect)), blockBuffer());
            }

            return citationsPipeline;
        }

        private void eventReasoningAdded(
            int index)
        {
            eventBegin(generator.length(), EVENT_ITEM_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_ADDED)
                .writeStartObject(MEMBER_ITEM)
                .write(MEMBER_ID, itemId(index))
                .write(MEMBER_TYPE, TYPE_REASONING)
                .writeStartArray(MEMBER_SUMMARY).writeEnd()
                .writeEnd()
                .write(MEMBER_OUTPUT_INDEX, index)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventSummaryPartAdded()
        {
            eventBegin(generator.length(), EVENT_SUMMARY_PART_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_SUMMARY_PART_ADDED)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeStartObject(MEMBER_PART)
                .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                .write(MEMBER_TEXT, "")
                .writeEnd()
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .write(MEMBER_SUMMARY_INDEX, itemParts - 1)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventSummaryTextDelta(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            eventBegin(generator.length(), EVENT_SUMMARY_TEXT_DELTA);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_SUMMARY_TEXT_DELTA)
                .writeKey(MEMBER_DELTA)
                .writeStartEscaped();
            generator.writeSegment(payload, offset, length);
            generator.writeEndEscaped();
            generator.write(MEMBER_ITEM_ID, itemId(itemCount - 1));

            if (server.obfuscation)
            {
                writeObfuscation();
            }

            generator.write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .write(MEMBER_SUMMARY_INDEX, itemParts - 1)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventSummaryTextDone()
        {
            eventBegin(generator.length(), EVENT_SUMMARY_TEXT_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_SUMMARY_TEXT_DONE)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .write(MEMBER_SUMMARY_INDEX, itemParts - 1)
                .writeKey(MEMBER_TEXT);
            writeCompleted(partValue);
            generator.writeEnd();
            eventEnd(generator.length());
        }

        private void eventSummaryPartDone()
        {
            eventBegin(generator.length(), EVENT_SUMMARY_PART_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_SUMMARY_PART_DONE)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeStartObject(MEMBER_PART)
                .write(MEMBER_TYPE, TYPE_SUMMARY_TEXT)
                .writeKey(MEMBER_TEXT);
            writeCompleted(partValue);
            generator.writeEnd()
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .write(MEMBER_SUMMARY_INDEX, itemParts - 1)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventRefusalDelta(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            eventBegin(generator.length(), EVENT_REFUSAL_DELTA);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_REFUSAL_DELTA)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .writeKey(MEMBER_DELTA)
                .writeStartEscaped();
            generator.writeSegment(payload, offset, length);
            generator.writeEndEscaped();
            generator.write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventRefusalDone()
        {
            eventBegin(generator.length(), EVENT_REFUSAL_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_REFUSAL_DONE)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .writeKey(MEMBER_REFUSAL);
            writeCompleted(partValue);
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventAnnotationAdded(
            int annotationIndex)
        {
            eventBegin(generator.length(), EVENT_ANNOTATION_ADDED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ANNOTATION_ADDED)
                .writeKey(MEMBER_ANNOTATION);
            generator.writeSegment(elementBuffer, 0, elementLength, Completion.COMPLETE);
            generator.write(MEMBER_ANNOTATION_INDEX, annotationIndex)
                .write(MEMBER_CONTENT_INDEX, itemParts - 1)
                .write(MEMBER_ITEM_ID, itemId(itemCount - 1))
                .write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void eventItemDone(
            String status)
        {
            eventBegin(generator.length(), EVENT_ITEM_DONE);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_ITEM_DONE)
                .writeKey(MEMBER_ITEM);
            writeItem(itemCount - 1, status);
            generator.write(MEMBER_OUTPUT_INDEX, itemCount - 1)
                .write(MEMBER_SEQUENCE_NUMBER, sequence++)
                .writeEnd();
            eventEnd(generator.length());
        }

        private void doEncodeError(
            String type,
            String param,
            String code,
            String message)
        {
            if (eventOpen)
            {
                eventEnd(encodeLength());
            }

            final String reported = code != null ? code : type != null ? type : ERROR_TYPE_SERVER;
            final String text = message != null ? message : "";

            eventBegin(generator.length(), EVENT_ERROR);
            generator.writeStartObject()
                .write(MEMBER_TYPE, EVENT_ERROR)
                .write(MEMBER_CODE, reported)
                .write(MEMBER_MESSAGE, text);

            if (param != null)
            {
                generator.write(MEMBER_PARAM, param);
            }
            else
            {
                generator.writeNull(MEMBER_PARAM);
            }

            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            eventEnd(generator.length());

            eventBegin(generator.length(), EVENT_FAILED);
            generator.writeStartObject().write(MEMBER_TYPE, EVENT_FAILED).writeStartObject(OBJECT_RESPONSE);
            writeEnvelope();
            generator.writeStartArray(MEMBER_OUTPUT).writeEnd()
                .write(MEMBER_STATUS, STATUS_FAILED)
                .writeStartObject(MEMBER_ERROR).write(MEMBER_CODE, reported).write(MEMBER_MESSAGE, text).writeEnd()
                .writeEnd();
            generator.write(MEMBER_SEQUENCE_NUMBER, sequence++).writeEnd();
            eventEnd(generator.length());
        }

        private int encodeLength()
        {
            return generator.length();
        }

        private void eventBegin(
            int position,
            String type)
        {
            final DirectBufferEx attributes = EVENT_ATTRIBUTES.get(type);

            this.eventOpen = true;
            this.eventStarted = false;
            this.eventStart = position;
            this.eventAttributeOffset = eventAttributesLimit;
            this.eventAttributeLength = eventAttributesLimit + attributes.capacity() <= eventAttributesBuffer.capacity()
                ? attributes.capacity()
                : 0;

            if (eventAttributeLength > 0)
            {
                eventAttributesBuffer.putBytes(eventAttributesLimit, attributes, 0, attributes.capacity());
                eventAttributesLimit += attributes.capacity();
            }
        }

        private void eventEnd(
            int position)
        {
            if (eventOpen)
            {
                emitEvent(position, true);
                eventOpen = false;
                eventStart = NO_EVENT;
                generator.reset();
            }
        }

        private void emitEvent(
            int position,
            boolean fin)
        {
            final int length = position - eventStart;

            if (length > 0 || fin && eventStarted)
            {
                if (eventCount == eventOffsets.length)
                {
                    final int capacity = eventCount << 1;
                    eventOffsets = Arrays.copyOf(eventOffsets, capacity);
                    eventLengths = Arrays.copyOf(eventLengths, capacity);
                    eventFlags = Arrays.copyOf(eventFlags, capacity);
                    eventAttributeOffsets = Arrays.copyOf(eventAttributeOffsets, capacity);
                    eventAttributeLengths = Arrays.copyOf(eventAttributeLengths, capacity);
                }

                final int init = eventStarted ? Flags.NONE : Flags.INIT;

                eventOffsets[eventCount] = eventStart;
                eventLengths[eventCount] = length;
                eventFlags[eventCount] = fin ? Flags.fin(init) : init;
                eventAttributeOffsets[eventCount] = eventAttributeOffset;
                eventAttributeLengths[eventCount] = eventStarted ? 0 : eventAttributeLength;
                eventCount++;

                eventStarted = true;
            }
        }

        private void putUsage(
            int inputTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            final int total = totalTokens != NONE || inputTokens == NONE || outputTokens == NONE
                ? totalTokens
                : inputTokens + outputTokens;

            if (inputTokens != NONE || outputTokens != NONE || total != NONE)
            {
                generator.writeStartObject(MEMBER_USAGE);

                if (inputTokens != NONE)
                {
                    generator.writeKey("input_tokens").write(inputTokens);
                }

                if (cacheWriteTokens != NONE || cacheReadTokens != NONE)
                {
                    generator.writeStartObject("input_tokens_details");

                    if (cacheWriteTokens != NONE)
                    {
                        generator.writeKey("cache_write_tokens").write(cacheWriteTokens);
                    }

                    if (cacheReadTokens != NONE)
                    {
                        generator.writeKey("cached_tokens").write(cacheReadTokens);
                    }

                    generator.writeEnd();
                }

                if (outputTokens != NONE)
                {
                    generator.writeKey("output_tokens").write(outputTokens);
                }

                if (reasoningTokens != NONE)
                {
                    generator.writeStartObject("output_tokens_details")
                        .writeKey("reasoning_tokens").write(reasoningTokens)
                        .writeEnd();
                }

                if (total != NONE)
                {
                    generator.writeKey("total_tokens").write(total);
                }

                generator.writeEnd();
            }
        }

        private void cleanupAccumulator()
        {
            if (accumulator != null)
            {
                accumulator.release();
            }
        }
    }


    private String mediaType(
        String contentType)
    {
        contentTypeMatcher.reset(contentType != null ? contentType : "");

        return contentTypeMatcher.matches() ? contentTypeMatcher.group(1) : null;
    }

    private final class LlmOpenaiRejectHandler
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String status;
        private final DirectBufferEx body;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private boolean began;
        private int bodySent;

        private LlmOpenaiRejectHandler(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            String status,
            String body)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.status = status;
            this.body = asBuffer(body);
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
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = decodeMax;

            doNetWindow(traceId);
            doNetBegin(traceId, authorization);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();

            initialSeq = data.sequence() + data.reserved();
            initialAck = initialSeq;

            doNetWindow(traceId);
        }

        private void onNetEnd(
            EndFW end)
        {
            initialSeq = end.sequence();
            initialAck = initialSeq;
        }

        private void onNetAbort(
            AbortFW abort)
        {
            initialSeq = abort.sequence();
            initialAck = initialSeq;
        }

        private void onNetWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();

            flushBody(window.traceId(), window.authorization());
        }

        private void doNetWindow(
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

            network.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_STATUS).value(status))
                .headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            began = true;
        }

        private void flushBody(
            long traceId,
            long authorization)
        {
            if (!began || bodySent >= body.capacity())
            {
                return;
            }

            while (bodySent < body.capacity())
            {
                final long available = replyMax - (replySeq - replyAck);
                if (available <= 0)
                {
                    break;
                }

                final int remaining = body.capacity() - bodySent;
                final int length = (int) Math.min(remaining, available);
                final int initFlags = bodySent == 0 ? Flags.INIT : Flags.NONE;
                final int flags = bodySent + length == body.capacity() ? Flags.fin(initFlags) : initFlags;

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
                    .reserved(length)
                    .payload(body, bodySent, length)
                    .extension(EMPTY_OCTETS)
                    .build();

                network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                replySeq += length;
                bodySent += length;
            }

            if (bodySent >= body.capacity())
            {
                doNetEnd(traceId, authorization);
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(EMPTY_OCTETS)
                .build();

            network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }
    }
}
