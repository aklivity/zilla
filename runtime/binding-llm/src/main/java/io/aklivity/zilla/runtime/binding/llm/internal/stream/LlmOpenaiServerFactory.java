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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
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

public final class LlmOpenaiServerFactory implements LlmDialectHandler
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
    private static final String PATH_CHAT_COMPLETIONS = "/v1/chat/completions";
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
        "The 'model' parameter must precede 'messages' and 'tools' in a request that does not fit the decode buffer.";
    private static final String MESSAGE_ROLE_MISSING = "Missing required parameter: 'role'.";
    private static final String MESSAGE_ROLE_ORDER = "The 'role' parameter must precede 'content' in each message.";
    private static final String MESSAGE_TOOL_CALL_ID_ORDER =
        "The 'tool_call_id' parameter must precede 'content' in a tool message.";
    private static final String MESSAGE_PART_TYPE_ORDER = "The 'type' parameter must be the first member of each content part.";
    private static final String MESSAGE_ATTRIBUTE_TOO_LARGE = "A content part attribute value is too large.";
    private static final int BLOCK_BUFFER_MAX = 4096;

    private static final int RESPONSE_ENCODE_PADDING = 128;
    private static final int RESPONSE_ESCAPE_EXPANSION = 6;
    private static final int REASONING_ESCAPE_EXPANSION = 6;
    private static final int MIN_TEXT_INPUT = "\"content\":\"\"".length();
    private static final String MEMBER_TEXT = "text";
    private static final byte[] REASONING_PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte[] REASONING_SUFFIX = "\"}".getBytes(UTF_8);
    private static final int RESPONSE_ENCODE_SLACK = 2048;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx DONE_BUFFER = new UnsafeBufferEx("[DONE]".getBytes(UTF_8));
    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);
    private static final DirectBufferEx STREAM_OPTIONS_SECTION =
        new UnsafeBufferEx("{\"stream_options\":{\"include_usage\":true}}".getBytes(UTF_8));
    private static final DirectBufferEx CONTROL_CLOSE = new UnsafeBufferEx("}".getBytes(UTF_8));

    private static final int MIN_WINDOW = 64;
    private static final int CONTROL_GROWTH = 3;
    private static final int CONTROL_OPEN_CAPACITY = 8;
    private static final int HOLD_MAX = 1024;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;


    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String ROLE_TOOL = "tool";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_REFUSAL = "refusal";


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
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;
    private final int decodeMax;
    private final int llmTypeId;
    private final int httpTypeId;

    private final LlmOpenaiServerDecoder decodeStart = this::decodeStart;
    private final LlmOpenaiServerDecoder decodeRootStart = this::decodeRootStart;
    private final LlmOpenaiServerDecoder decodeModelMember = this::decodeModelMember;
    private final LlmOpenaiServerDecoder decodeModelValue = this::decodeModelValue;
    private final LlmOpenaiServerDecoder decodeRoot = this::decodeRoot;
    private final LlmOpenaiServerDecoder decodeMessagesStart = this::decodeMessagesStart;
    private final LlmOpenaiServerDecoder decodeMessages = this::decodeMessages;
    private final LlmOpenaiServerDecoder decodeMessage = this::decodeMessage;
    private final LlmOpenaiServerDecoder decodeRole = this::decodeRole;
    private final LlmOpenaiServerDecoder decodeToolCallId = this::decodeToolCallId;
    private final LlmOpenaiServerDecoder decodeContent = this::decodeContent;
    private final LlmOpenaiServerDecoder decodeRefusal = this::decodeRefusal;
    private final LlmOpenaiServerDecoder decodeToolCallsStart = this::decodeToolCallsStart;
    private final LlmOpenaiServerDecoder decodeToolCalls = this::decodeToolCalls;
    private final LlmOpenaiServerDecoder decodeAnnotationsStart = this::decodeAnnotationsStart;
    private final LlmOpenaiServerDecoder decodeAnnotations = this::decodeAnnotations;
    private final LlmOpenaiServerDecoder decodeToolsStart = this::decodeToolsStart;
    private final LlmOpenaiServerDecoder decodeTools = this::decodeTools;
    private final LlmOpenaiServerDecoder decodeParts = this::decodeParts;
    private final LlmOpenaiServerDecoder decodeBlockType = this::decodeBlockType;
    private final LlmOpenaiServerDecoder decodeBlockTypeValue = this::decodeBlockTypeValue;
    private final LlmOpenaiServerDecoder decodePart = this::decodePart;
    private final LlmOpenaiServerDecoder decodeAttributes = this::decodeAttributes;
    private final LlmOpenaiServerDecoder decodeImageBlock = this::decodeImageBlock;
    private final LlmOpenaiServerDecoder decodeAudioBlock = this::decodeAudioBlock;
    private final LlmOpenaiServerDecoder decodeDocumentBlock = this::decodeDocumentBlock;
    private final LlmOpenaiServerDecoder decodeToolCall = this::decodeToolCall;
    private final LlmOpenaiServerDecoder decodeTool = this::decodeTool;
    private final LlmOpenaiServerDecoder decodeText = this::decodeText;
    private final LlmOpenaiServerDecoder decodeSkip = this::decodeSkip;
    private final LlmOpenaiServerDecoder decodeControlValue = this::decodeControlValue;
    private final LlmOpenaiServerDecoder decodeMaxOutputTokensValue = this::decodeMaxOutputTokensValue;
    private final LlmOpenaiServerDecoder decodeEnd = this::decodeEnd;
    private final LlmOpenaiServerDecoder decodeIgnore = this::decodeIgnore;

    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;

    public LlmOpenaiServerFactory(
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.contentTypeMatcher = CONTENT_TYPE.matcher("");
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
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
            PATH_CHAT_COMPLETIONS.equals(path) &&
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
                        newStream = new LlmOpenaiServer(
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

    @FunctionalInterface
    private interface LlmOpenaiServerDecoder
    {
        int decode(
            LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
                if (matches(key, "messages"))
                {
                    server.decoder = decodeMessagesStart;
                }
                else if (matches(key, "tools"))
                {
                    server.decoder = decodeToolsStart;
                }
                else if (matches(key, "max_completion_tokens") || matches(key, "max_tokens"))
                {
                    server.decoder = decodeMaxOutputTokensValue;
                }
                else if (matches(key, "model"))
                {
                    server.skip(decodeRoot);
                    server.decoder = decodeSkip;
                }
                else
                {
                    server.control(key, -1, decodeRoot);
                }
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

    private int decodeMessagesStart(
        LlmOpenaiServer server,
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
            if (event != JsonEvent.START_ARRAY)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.message = -1;
            server.decoder = decodeMessages;
        }

        return server.position();
    }

    private int decodeMessages(
        LlmOpenaiServer server,
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
                server.message++;
                server.role = null;
                server.toolCallId = null;
                server.decoder = decodeMessage;
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

    private int decodeMessage(
        LlmOpenaiServer server,
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
                if (matches(key, "role"))
                {
                    server.decoder = decodeRole;
                }
                else if (matches(key, "tool_call_id"))
                {
                    server.decoder = decodeToolCallId;
                }
                else if (matches(key, "content"))
                {
                    if (!textTyped(server))
                    {
                        server.onDecodeInvalidRequest(traceId,
                            server.role == null ? MESSAGE_ROLE_ORDER : MESSAGE_TOOL_CALL_ID_ORDER);
                        break decode;
                    }
                    server.decoder = decodeContent;
                }
                else if (matches(key, "refusal"))
                {
                    server.decoder = decodeRefusal;
                }
                else if (matches(key, "tool_calls"))
                {
                    server.decoder = decodeToolCallsStart;
                }
                else if (matches(key, "annotations"))
                {
                    server.decoder = decodeAnnotationsStart;
                }
                else
                {
                    server.control(key, server.message, decodeMessage);
                }
                break;
            case END_OBJECT:
                if (server.role == null)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_MISSING);
                    break decode;
                }
                server.decoder = decodeMessages;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRole(
        LlmOpenaiServer server,
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
                    server.decoder = decodeMessage;
                }
                else
                {
                    server.skipDepth = 1;
                    server.skipThen = decodeMessages;
                    server.decoder = decodeSkip;
                }
            }
        }

        return server.position();
    }

    private int decodeToolCallId(
        LlmOpenaiServer server,
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
                server.toolCallId = parser.getString();
                server.decoder = decodeMessage;
            }
        }

        return server.position();
    }

    private int decodeContent(
        LlmOpenaiServer server,
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
                server.text(textBlockType(server), textToolCallId(server), decodeMessage);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case START_ARRAY:
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeMessage;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeRefusal(
        LlmOpenaiServer server,
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
                server.text(LlmSectionType.ASSISTANT_REFUSAL, null, decodeMessage);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
                break decode;
            case VALUE_NULL:
                server.decoder = decodeMessage;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeAnnotationsStart(
        LlmOpenaiServer server,
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
                server.capture(LlmSectionType.CITATIONS, server.message, decodeMessage);
                server.decoder = decodeAnnotations;
                break;
            case VALUE_NULL:
                server.decoder = decodeMessage;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeAnnotations(
        LlmOpenaiServer server,
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

        while (server.decoder == decodeAnnotations && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

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
            case VALUE_STRING:
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

    private int decodeToolCallsStart(
        LlmOpenaiServer server,
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
                server.decoder = decodeToolCalls;
                break;
            case VALUE_NULL:
                server.decoder = decodeMessage;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolCalls(
        LlmOpenaiServer server,
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
                server.probe(LlmSectionType.TOOL_CALL, server.message, decodeToolCall, decodeToolCalls);
                server.decoder = decodeAttributes;
                break;
            case END_ARRAY:
                server.decoder = decodeMessage;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolsStart(
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
                server.probe(LlmSectionType.TOOL_DEFINITION, -1, decodeTool, decodeTools);
                server.decoder = decodeAttributes;
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

    private int decodeParts(
        LlmOpenaiServer server,
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
                server.decoder = decodeBlockType;
                break;
            case END_ARRAY:
                server.decoder = decodeMessage;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeBlockType(
        LlmOpenaiServer server,
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
                if (matches(parser.getStringView(), "type"))
                {
                    server.decoder = decodeBlockTypeValue;
                }
                else
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TYPE_ORDER);
                }
            }
        }

        return server.position();
    }

    private int decodeBlockTypeValue(
        LlmOpenaiServer server,
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

                if (TYPE_TEXT.equals(type) || TYPE_REFUSAL.equals(type))
                {
                    server.release();
                    server.decoder = decodePart;
                }
                else
                {
                    final LlmSectionType blockType = partBlockType(type);

                    if (blockType != null)
                    {
                        final LlmOpenaiServerDecoder decodeTypedBlock = switch (blockType)
                        {
                        case USER_IMAGE -> decodeImageBlock;
                        case USER_AUDIO -> decodeAudioBlock;
                        default -> decodeDocumentBlock;
                        };

                        server.probe(blockType, server.message, decodeTypedBlock, decodeParts);
                        server.decoder = decodeAttributes;
                    }
                    else
                    {
                        server.release();
                        server.skipDepth = 1;
                        server.skipThen = decodeParts;
                        server.decoder = decodeSkip;
                    }
                }
            }
        }

        return server.position();
    }

    private int decodePart(
        LlmOpenaiServer server,
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
                if (matches(key, TYPE_TEXT))
                {
                    server.text(textBlockType(server), textToolCallId(server), decodePart);
                    server.decoder = decodeText;
                }
                else if (matches(key, TYPE_REFUSAL))
                {
                    server.text(LlmSectionType.ASSISTANT_REFUSAL, null, decodePart);
                    server.decoder = decodeText;
                }
                else
                {
                    server.control(key, server.message, decodePart);
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

    private int decodeAttributes(
        LlmOpenaiServer server,
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

    private int decodeImageBlock(
        LlmOpenaiServer server,
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

        while (server.decoder == decodeImageBlock && parser.hasNextEvent())
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

    private int decodeAudioBlock(
        LlmOpenaiServer server,
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

        while (server.decoder == decodeAudioBlock && parser.hasNextEvent())
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

    private int decodeDocumentBlock(
        LlmOpenaiServer server,
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

        while (server.decoder == decodeDocumentBlock && parser.hasNextEvent())
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

    private int decodeToolCall(
        LlmOpenaiServer server,
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

        while (server.decoder == decodeToolCall && parser.hasNextEvent())
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

    private int decodeTool(
        LlmOpenaiServer server,
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

        while (server.decoder == decodeTool && parser.hasNextEvent())
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

    private int decodeText(
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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

    private int decodeControlValue(
        LlmOpenaiServer server,
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
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

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
            case VALUE_STRING:
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
        LlmOpenaiServer server,
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

    private static boolean textTyped(
        LlmOpenaiServer server)
    {
        return server.role != null && (!ROLE_TOOL.equals(server.role) || server.toolCallId != null);
    }

    private static LlmSectionType roleSectionType(
        String role)
    {
        return switch (role)
        {
        case "system", "developer" -> LlmSectionType.SYSTEM_INSTRUCTION;
        case "user" -> LlmSectionType.USER_TEXT;
        case "assistant" -> LlmSectionType.ASSISTANT_TEXT;
        case ROLE_TOOL -> LlmSectionType.TOOL_RESULT;
        default -> null;
        };
    }

    private LlmSectionType textBlockType(
        LlmOpenaiServer server)
    {
        return server.roleType;
    }

    private String textToolCallId(
        LlmOpenaiServer server)
    {
        return ROLE_TOOL.equals(server.role) ? server.toolCallId : null;
    }

    private static LlmSectionType partBlockType(
        String type)
    {
        return switch (type)
        {
        case "image_url" -> LlmSectionType.USER_IMAGE;
        case "input_audio" -> LlmSectionType.USER_AUDIO;
        case "file" -> LlmSectionType.USER_DOCUMENT;
        default -> null;
        };
    }

    private int encode(
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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
        LlmOpenaiServer server,
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

    private final class LlmOpenaiServer implements LlmReasoningScanner.Visitor
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

        private LlmOpenaiStream stream;
        private LlmOpenaiServerDecoder decoder;

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

        private int message;
        private String role;
        private String toolCallId;

        private LlmOpenaiServerDecoder skipThen;
        private int skipDepth;

        private boolean streaming;
        private boolean streamOptions;
        private boolean usageRequested;
        private boolean obfuscation = true;
        private int maxOutputTokens = NO_MAX_OUTPUT_TOKENS;

        private LlmOpenaiServerDecoder textThen;
        private LlmSectionType textType;
        private String textToolCallId;
        private boolean textOpen;
        private char textHighSurrogate;

        private LlmOpenaiServerDecoder probeCapture;
        private LlmOpenaiServerDecoder probeThen;
        private int probeMessage;

        private LlmOpenaiServerDecoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;

        private boolean holding;
        private boolean lookahead;
        private boolean replaying;
        private int holdFrom;
        private int retained;

        private final LlmOpenaiAttributeProbe probe;
        private final LlmSectionAttributes blockAttributes;
        private final LlmSectionAttributes sentAttributes;
        private final LlmSectionAttributes finAttributes;
        private boolean probing;

        private long decodeTraceId;
        private long decodeAuthorization;

        private LlmSectionType blockSection;
        private int blockMessage;
        private int blockFlags;
        private LlmSectionPipeline toolCallPipeline;
        private final StringBuilder controlKey = new StringBuilder();
        private LlmSectionPipeline controlPipeline;
        private LlmOpenaiControlToCanonical controlTransform;
        private LlmSectionType roleType;
        private final LlmReasoningScanner scanner;
        private LlmSectionType scanType;
        private int scanSections;
        private byte[] pieceBytes;
        private UnsafeBufferEx pieceBuffer;
        private final JsonGeneratorEx generator;
        private long scanTraceId;
        private long scanAuthorization;
        private LlmSectionPipeline blockPipeline;
        private final LlmSectionPipeline.Output blockOutput = (buffer, offset, length, last) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, last);

        private LlmOpenaiServer(
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
            this.scanner = new LlmReasoningScanner(LlmReasoningTags.DEFAULTS);
            this.pieceBytes = new byte[1024];
            this.pieceBuffer = new UnsafeBufferEx(pieceBytes);
            this.generator = JsonEx.createGenerator();
            this.probe = new LlmOpenaiAttributeProbe();
            this.lookahead = true;
            this.holding = true;
            this.blockAttributes = new LlmSectionAttributes();
            this.sentAttributes = new LlmSectionAttributes();
            this.finAttributes = new LlmSectionAttributes();
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
                final int window = stream != null ? (int) Math.min(limit - offset, decodeRoom()) : limit - offset;

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
            LlmOpenaiServerDecoder previous = null;
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

            if (windowLast && !replayed && decoder != decodeEnd && decoder != decodeIgnore)
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
            final int framing = REASONING_PREFIX.length + REASONING_SUFFIX.length;
            final boolean inside = decoder == decodeText && scanType != null;
            final boolean assistant = decoder == decodeText
                ? textType == LlmSectionType.ASSISTANT_TEXT
                : "assistant".equals(role);
            final int minBlock = scanner.minBlock();
            final int reserve = inside ? REASONING_SUFFIX.length : framing;
            final int sections = 1 + (framing + MIN_TEXT_INPUT - 1) / MIN_TEXT_INPUT;
            final int blocks = 1 + (2 * framing + minBlock - 1) / minBlock;
            final int textGrowth = assistant ? Math.max(sections, blocks) : sections;
            final int growth = decoder == decodeText ? textGrowth : Math.max(textGrowth, CONTROL_GROWTH);

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
            LlmOpenaiServerDecoder then)
        {
            skipThen = then;
            skipDepth = 0;
        }

        private void text(
            LlmSectionType type,
            String toolCallId,
            LlmOpenaiServerDecoder then)
        {
            textType = type;
            textToolCallId = toolCallId;
            textThen = then;
            textOpen = false;
        }

        private void probe(
            LlmSectionType type,
            int message,
            LlmOpenaiServerDecoder capturing,
            LlmOpenaiServerDecoder then)
        {
            probeMessage = message;
            probeCapture = capturing;
            probeThen = then;
            probe.begin(type);
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

        private void capture(
            LlmSectionType type,
            int blockMessage,
            LlmOpenaiServerDecoder then)
        {
            final int from = position() - 1;

            onDecodeBlock(type, blockMessage, null);

            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = from;
        }

        private void captureHeld(
            LlmSectionType type,
            int blockMessage,
            LlmOpenaiServerDecoder then,
            int depth)
        {
            onDecodeBlock(type, blockMessage, null);

            captureThen = then;
            captureOpen = true;
            captureDepth = depth;
            captureFrom = holdFrom;
            release();
        }

        private void captureProbed()
        {
            captureHeld(probe.type(), probeMessage, probeThen, probe.depth());
            probing = true;
            decoder = probeCapture;
        }

        private void control(
            CharSequence key,
            int message,
            LlmOpenaiServerDecoder then)
        {
            onDecodeBlock(LlmSectionType.CONTROL, message, null);

            if (matches(key, MEMBER_STREAM) || matches(key, MEMBER_STREAM_OPTIONS))
            {
                blockPipeline = controlPipeline();
                blockPipeline.begin(blockAttributes);
                onDecodeControlKey(key, false);
            }
            else
            {
                onDecodeControlKey(key, true);
            }

            captureThen = then;
            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            decoder = decodeControlValue;
        }

        private void endCapture(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, true);

            if (blockSection == LlmSectionType.CONTROL && blockPipeline != null)
            {
                onDecodeControl();
            }

            blockPipeline = null;
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
            stream = new LlmOpenaiStream(this);
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
            if (!textOpen)
            {
                scanner.begin();
                scanSections = 0;
                textOpen = true;
            }

            scanTraceId = traceId;
            scanAuthorization = authorization;

            final int length = chars.length();
            final int take = textAllowance(chars, fin);
            final boolean finished = fin && take == length;

            if (take > 0 || finished && textHighSurrogate != 0)
            {
                final int decoded = encode(this, chars, take, finished);

                if (textType == LlmSectionType.ASSISTANT_TEXT)
                {
                    scanner.accept(textBuffer, 0, decoded, this);
                }
                else
                {
                    text(textBuffer, 0, decoded);
                }
            }

            if (finished)
            {
                scanner.end(this);
                closeScan();

                if (scanSections == 0)
                {
                    openScan(textType);
                    closeScan();
                }
            }

            return take;
        }

        private int textAllowance(
            CharSequence chars,
            boolean fin)
        {
            final int length = chars.length();
            final boolean scanning = textType == LlmSectionType.ASSISTANT_TEXT;
            final int boundary = scanning ? REASONING_PREFIX.length + REASONING_SUFFIX.length : 0;
            final int closing = scanning ? scanner.capacity() : 0;

            final int credit = available();

            int cost = scanner.pending() + (scanType == null ? REASONING_PREFIX.length : 0);
            int take = 0;
            int index = 0;
            boolean fits = true;

            while (fits && index < length)
            {
                final char c = chars.charAt(index);
                final boolean pair = Character.isHighSurrogate(c) && index + 1 < length &&
                    Character.isLowSurrogate(chars.charAt(index + 1));
                final int width = pair ? 2 : 1;
                final int next = cost + (pair ? 4 : escapedWidth(c)) + (c == '<' ? boundary : 0);
                final boolean last = index + width == length;

                fits = next + (last && fin ? REASONING_SUFFIX.length + closing : 0) <= credit;

                if (fits)
                {
                    cost = next;
                    index += width;
                    take = index;
                }
            }

            if (length == 0 && fin && cost + REASONING_SUFFIX.length + closing > credit)
            {
                take = -1;
            }

            return take;
        }

        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (scanType != null && scanType != textType)
            {
                closeScan();
            }

            if (scanType == null)
            {
                openScan(textType);
            }

            final int encoded = encodePiece(buffer, offset, length);

            onDecodeData(scanTraceId, scanAuthorization, pieceBuffer, 0, encoded, false);
        }

        @Override
        public void reasoningBegin(
            String tag)
        {
            closeScan();
        }

        @Override
        public void reasoning(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (scanType != null && scanType != LlmSectionType.ASSISTANT_REASONING)
            {
                closeScan();
            }

            if (scanType == null)
            {
                openScan(LlmSectionType.ASSISTANT_REASONING);
                blockAttributes.reasoningTag(scanner.tag());
            }

            final int encoded = encodePiece(buffer, offset, length);

            onDecodeData(scanTraceId, scanAuthorization, pieceBuffer, 0, encoded, false);
        }

        @Override
        public void reasoningEnd()
        {
            closeScan();
        }

        private void openScan(
            LlmSectionType type)
        {
            onDecodeBlock(type, message, textToolCallId);
            scanType = type;
            scanSections++;
        }

        private void closeScan()
        {
            if (scanType != null)
            {
                final int length = encodePieceEnd();

                onDecodeData(scanTraceId, scanAuthorization, pieceBuffer, 0, length, true);
                scanType = null;
            }
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
            reservePiece(REASONING_PREFIX.length + length * REASONING_ESCAPE_EXPANSION);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);

            if (Flags.hasInit(blockFlags))
            {
                beginPieceObject();
            }

            generator.writeSegment(buffer, offset, length);

            return generator.length();
        }

        private int encodePieceEnd()
        {
            reservePiece(REASONING_PREFIX.length + REASONING_SUFFIX.length);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);

            if (Flags.hasInit(blockFlags))
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
            int message,
            String toolCallId)
        {
            blockSection = type;
            blockMessage = message;
            blockFlags = Flags.INIT;

            blockAttributes.clear();
            blockAttributes.toolCallId(toolCallId);

            blockPipeline = blockPipelineFor(type);

            if (blockPipeline != null)
            {
                blockPipeline.begin(blockAttributes);
            }
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
                    onDecodeParseError(traceId);
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
                accepted = blockPipeline.feed(buffer, offset, length, last, blockOutput);
            }

            return accepted;
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

        private LlmSectionPipeline blockPipelineFor(
            LlmSectionType type)
        {
            LlmSectionPipeline pipeline = null;

            if (type == LlmSectionType.TOOL_CALL)
            {
                if (toolCallPipeline == null)
                {
                    toolCallPipeline = new LlmSectionPipeline(new LlmOpenaiToolCallToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }

                pipeline = toolCallPipeline;
            }

            return pipeline;
        }

        private void onDecodeControlKey(
            CharSequence key,
            boolean prefixed)
        {
            controlKey.setLength(0);

            if (prefixed)
            {
                controlKey.append(NAME).append(':');
            }

            controlKey.append(key);

            reservePiece(controlKey.length() * REASONING_ESCAPE_EXPANSION + CONTROL_OPEN_CAPACITY);

            generator.wrap(pieceBuffer, 0, pieceBytes.length);
            generator.reset();
            generator.writeStartObject().writeKey(controlKey);

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
                buffer, offset, length);

            blockFlags = Flags.NONE;
        }

        private void onDecodeRootEnd(
            long traceId)
        {
            if (streaming && !streamOptions)
            {
                streamOptions = true;
                usageRequested = false;
                stream.doAppData(decodeTraceId, decodeAuthorization, Flags.COMPLETE, LlmSectionType.CONTROL, -1, null,
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

    private final class LlmOpenaiStream
    {
        private final LlmOpenaiServer server;
        private final LlmTextPipeline textPipeline;
        private final LlmSectionPipeline.Output encodeArguments = (buffer, offset, length, last) ->
            onEncodeArguments(buffer, offset, length);

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

        private LlmSectionPipeline argumentsPipeline;

        private boolean flushed;

        private LlmOpenaiStream(
            LlmOpenaiServer server)
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
            this.textPipeline = new LlmTextPipeline(this::onReasoningText);
            this.delimiterBytes = new byte[64];
            this.delimiterBuffer = new UnsafeBufferEx(delimiterBytes);
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

            responseId = llmBeginEx != null ? llmBeginEx.id().asString() : null;
            responseModel = llmBeginEx != null ? llmBeginEx.model().asString() : null;

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                server.streaming ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

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

            beginEncode();

            if (Flags.hasInit(flags))
            {
                final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);
                final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;
                final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                replyAttributes.readFrom(section);

                doEncodeSection(
                    type,
                    section != null ? section.message() : -1,
                    replyAttributes.toolCallId(),
                    replyAttributes.toolName(),
                    replyAttributes.reasoningTag());
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
            final long replyAckMax = replySeq - server.encodeSlotRawOffset;

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

            beginEncode();

            closeEncodedReasoning();
            doEncodeEnd();
            doEncodeFinish(
                llmEndEx != null ? llmEndEx.finishReason().asString() : null,
                usage != null ? usage.inputTokens() : -1,
                usage != null ? usage.cacheReadTokens() : -1,
                usage != null ? usage.outputTokens() : -1,
                usage != null ? usage.reasoningTokens() : -1,
                usage != null ? usage.totalTokens() : -1);

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

            final LlmAbortExFW llmAbortEx = server.streaming ? extension.get(llmAbortExRO::tryWrap) : null;
            final LlmErrorFW error = llmAbortEx != null ? llmAbortEx.error() : null;

            if (error != null && (error.type().asString() != null || error.message().asString() != null))
            {
                beginEncode();
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
                    })
                    .build();

                builder.extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof());
            }

            final DataFW data = builder.build();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            initialSeq += length;
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
                server.doNetData(copyBuffer, 0, encodeLength(), reserved, Flags.COMPLETE, EMPTY_OCTETS, budgetId, traceId,
                    authorization);
            }
            else
            {
                ackReply(traceId);
            }
        }

        private static final int EVENT_CAPACITY = 8;
        private static final int NO_EVENT = -1;

        private static final int KIND_NONE = 0;
        private static final int KIND_TEXT = 1;
        private static final int KIND_REFUSAL = 2;
        private static final int KIND_TOOL_CALL = 3;
        private static final int KIND_SKIPPED = 4;
        private static final int KIND_REASONING = 5;
        private static final int KIND_CONTROL = 6;

        private static final int CONTROL_DROP = 0;
        private static final int CONTROL_INLINE = 1;
        private static final int CONTROL_HELD = 2;
        private static final int CONTROL_MEMBERS_MAX = 8;
        private static final int CONTROL_HELD_MAX = 4096;
        private static final int OBFUSCATION_MIN = 4;
        private static final int OBFUSCATION_RANGE = 28;
        private static final String MEMBER_USAGE = "usage";
        private static final String MEMBER_LOGPROBS = "logprobs";
        private static final String MEMBER_OBFUSCATION = "obfuscation";
        private static final String OBFUSCATION_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        private static final int MEMBER_NONE = 0;
        private static final int MEMBER_CONTENT = 1;
        private static final int MEMBER_REFUSAL = 2;
        private static final int MEMBER_TOOL_CALLS = 3;

        private static final int ENVELOPE_PRELUDE = 0;
        private static final int ENVELOPE_CHOICE = 1;
        private static final int ENVELOPE_TAIL = 2;

        private static final int NONE = -1;
        private static final int MESSAGE_FIRST = 0;

        private static final String OBJECT_COMPLETION = "chat.completion";
        private static final String OBJECT_CHUNK = "chat.completion.chunk";

        private final JsonGeneratorEx generator;

        private String responseId;
        private String responseModel;

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
        private String[] controlKeys;
        private int[] controlOffsets;
        private int[] controlLengths;
        private UnsafeBufferEx controlBuffer;
        private int controlCount;
        private int controlLimit;
        private boolean usageVerbatim;
        private int usageOffset;
        private int usageLength;

        private int kind;
        private int member;
        private boolean contentWritten;
        private boolean refusalWritten;
        private int toolCalls;
        private int envelope;
        private boolean messageClosed;

        private String reasoningTag;
        private byte[] delimiterBytes;
        private UnsafeBufferEx delimiterBuffer;

        private void doEncodeBegin()
        {
            if (server.streaming)
            {
                chunkStart();
                generator.writeStartObject()
                    .writeKey("index").write(0)
                    .writeStartObject("delta").write("role", "assistant").writeEnd()
                    .writeNull("finish_reason")
                    .writeEnd();
                chunkEnd();
            }
            else
            {
                generator.writeStartObject();

                if (responseId != null)
                {
                    generator.write("id", responseId);
                }

                generator.write("object", OBJECT_COMPLETION);

                if (responseModel != null)
                {
                    generator.write("model", responseModel);
                }
            }
        }

        private void doEncodeSection(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName,
            String tag)
        {
            final int next = type == LlmSectionType.CONTROL
                ? KIND_CONTROL
                : message == MESSAGE_FIRST && !messageClosed && envelope != ENVELOPE_TAIL ? kindOf(type) : KIND_SKIPPED;

            if (next != KIND_REASONING)
            {
                closeEncodedReasoning();
            }

            kind = next;

            if (!server.streaming && kind != KIND_SKIPPED && kind != KIND_CONTROL && envelope == ENVELOPE_PRELUDE)
            {
                openChoices();
            }

            switch (kind)
            {
            case KIND_TEXT:
                sectionText();
                break;
            case KIND_REASONING:
                sectionReasoning(tag);
                break;
            case KIND_REFUSAL:
                sectionRefusal();
                break;
            case KIND_TOOL_CALL:
                sectionToolCall(toolCallId, toolName);
                argumentsPipeline().begin(replyAttributes);
                break;
            case KIND_CONTROL:
                controlMessage = message;
                controlOpen = false;
                controlPipeline().begin(replyAttributes);
                break;
            default:
                break;
            }

            if (kind == KIND_TEXT || kind == KIND_REFUSAL)
            {
                textPipeline.begin();
            }
        }

        private void doEncodeData(
            long traceId,
            DirectBufferEx payload,
            int payloadOffset,
            int payloadLength,
            boolean fin)
        {
            if (kind == KIND_REASONING || kind == KIND_TEXT || kind == KIND_REFUSAL)
            {
                textPipeline.feed(payload, payloadOffset, payloadLength, fin);
            }
            else if (kind == KIND_TOOL_CALL &&
                !argumentsPipeline.feed(payload, payloadOffset, payloadLength, fin, encodeArguments))
            {
                server.cleanup(traceId);
            }
            else if (kind == KIND_CONTROL &&
                !controlPipeline.feed(payload, payloadOffset, payloadLength, fin, encodeControl))
            {
                server.cleanup(traceId);
            }
        }

        private LlmSectionPipeline controlPipeline()
        {
            if (controlPipeline == null)
            {
                controlTransform = new LlmOpenaiControlFromCanonical();
                controlPipeline = new LlmSectionPipeline(controlTransform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return controlPipeline;
        }

        private LlmSectionPipeline argumentsPipeline()
        {
            if (argumentsPipeline == null)
            {
                argumentsPipeline = new LlmSectionPipeline(new LlmToolCallToValue(),
                    new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return argumentsPipeline;
        }

        private void onEncodeArguments(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (length > 0)
            {
                onEncodeText(payload, offset, length);
            }
        }

        private void onEncodeText(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (server.streaming)
            {
                dataChunk(payload, offset, length);
            }
            else
            {
                generator.writeSegment(payload, offset, length);
            }
        }

        private void onReasoningText(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            onEncodeText(payload, offset, length);
        }

        private void sectionReasoning(
            String tag)
        {
            if (!server.streaming)
            {
                sectionText();
            }

            if (kind != KIND_SKIPPED)
            {
                kind = KIND_REASONING;
                textPipeline.begin();

                if (reasoningTag == null)
                {
                    reasoningTag = tag != null ? tag : LlmReasoningTags.DEFAULT;
                    encodeDelimiter("<");
                }
            }
        }

        private void closeEncodedReasoning()
        {
            if (reasoningTag != null)
            {
                kind = KIND_REASONING;
                encodeDelimiter("</");
                reasoningTag = null;
            }
        }

        private void encodeDelimiter(
            String prefix)
        {
            final int length = prefix.length() + reasoningTag.length() + 1;

            if (delimiterBytes.length < length)
            {
                delimiterBytes = new byte[length];
                delimiterBuffer = new UnsafeBufferEx(delimiterBytes);
            }

            int position = delimiterBuffer.putStringWithoutLengthAscii(0, prefix);
            position += delimiterBuffer.putStringWithoutLengthAscii(position, reasoningTag);
            position += delimiterBuffer.putStringWithoutLengthAscii(position, ">");

            onEncodeText(delimiterBuffer, 0, position);
        }

        private void doEncodeEnd()
        {
            if (!server.streaming && kind == KIND_TOOL_CALL)
            {
                generator.writeEndEscaped().writeEnd().writeEnd();
            }
            else if (kind == KIND_CONTROL)
            {
                onEncodeControlEnd();
            }

            kind = KIND_NONE;
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
                    generator.writeSegment(payload, offset, length, Completion.INCOMPLETE);
                }
            }
        }

        private void onEncodeControlBegin(
            String name)
        {
            final boolean root = controlMessage == NONE;

            if (server.streaming)
            {
                controlMode = root ? CONTROL_HELD : CONTROL_DROP;
            }
            else if (root)
            {
                controlMode = envelope == ENVELOPE_PRELUDE ? CONTROL_INLINE : CONTROL_HELD;
            }
            else
            {
                controlMode = envelope == ENVELOPE_TAIL ? CONTROL_DROP : CONTROL_INLINE;
            }

            if (controlMode == CONTROL_HELD)
            {
                onEncodeControlHeldBegin(name);
            }
            else if (controlMode == CONTROL_INLINE)
            {
                onEncodeControlInlineBegin(name, root);
            }
        }

        private void onEncodeControlInlineBegin(
            String name,
            boolean root)
        {
            if (!root)
            {
                if (envelope == ENVELOPE_PRELUDE)
                {
                    openChoices();
                }

                closeMember();

                if (MEMBER_LOGPROBS.equals(name) && !messageClosed)
                {
                    generator.writeEnd();
                    messageClosed = true;
                }
            }

            generator.writeKey(name);
        }

        private void onEncodeControlHeldBegin(
            String name)
        {
            if (controlKeys == null)
            {
                controlKeys = new String[CONTROL_MEMBERS_MAX];
                controlOffsets = new int[CONTROL_MEMBERS_MAX];
                controlLengths = new int[CONTROL_MEMBERS_MAX];
                controlBuffer = new UnsafeBufferEx(new byte[CONTROL_HELD_MAX]);
            }

            if (controlCount < CONTROL_MEMBERS_MAX)
            {
                controlKeys[controlCount] = name;
                controlOffsets[controlCount] = controlLimit;
                controlLengths[controlCount] = 0;
            }
            else
            {
                controlMode = CONTROL_DROP;
            }
        }

        private void onEncodeControlHeld(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            if (controlLimit + length <= CONTROL_HELD_MAX)
            {
                controlBuffer.putBytes(controlLimit, payload, offset, length);
                controlLimit += length;
                controlLengths[controlCount] += length;
            }
            else
            {
                controlLimit = controlOffsets[controlCount];
                controlMode = CONTROL_DROP;
            }
        }

        private void onEncodeControlEnd()
        {
            if (controlMode == CONTROL_INLINE)
            {
                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
            }
            else if (controlMode == CONTROL_HELD)
            {
                final String name = controlKeys[controlCount];

                if (MEMBER_USAGE.equals(name))
                {
                    usageVerbatim = true;

                    if (server.streaming)
                    {
                        if (server.usageRequested)
                        {
                            usageOffset = controlOffsets[controlCount];
                            usageLength = controlLengths[controlCount];
                        }
                        else
                        {
                            controlLimit = controlOffsets[controlCount];
                        }
                    }
                    else
                    {
                        controlCount++;
                    }
                }
                else
                {
                    controlCount++;
                }
            }

            controlMode = CONTROL_DROP;
        }

        private void doEncodeUsageChunk()
        {
            chunkStart();
            generator.writeEnd();
            generator.writeKey(MEMBER_USAGE);
            generator.writeRaw(controlBuffer, usageOffset, usageLength);
            generator.writeEnd();
            eventEnd(generator.length());
        }

        private void writeControls()
        {
            for (int index = 0; index < controlCount; index++)
            {
                generator.writeKey(controlKeys[index]);
                generator.writeRaw(controlBuffer, controlOffsets[index], controlLengths[index]);
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
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            final String reason = finishReason(finishReason);

            if (server.streaming)
            {
                chunkStart();
                generator.writeStartObject()
                    .writeKey("index").write(0)
                    .writeStartObject("delta").writeEnd()
                    .write("finish_reason", reason)
                    .writeEnd();

                final boolean usage = server.usageRequested && !usageVerbatim &&
                    (inputTokens != NONE || outputTokens != NONE || totalTokens != NONE);

                chunkEnd();

                if (usageLength > 0)
                {
                    doEncodeUsageChunk();
                }
                else if (usage)
                {
                    chunkStart();
                    generator.writeEnd();
                    putUsage(inputTokens, cacheReadTokens, outputTokens, reasoningTokens, totalTokens);
                    generator.writeEnd();
                    eventEnd(generator.length());
                }

                eventBegin(generator.length());
                generator.writeRaw(DONE_BUFFER, 0, DONE_BUFFER.capacity());
                eventEnd(generator.length());
            }
            else
            {
                if (envelope == ENVELOPE_PRELUDE)
                {
                    openChoices();
                }

                if (envelope == ENVELOPE_CHOICE)
                {
                    closeChoice(reason);
                }

                writeControls();

                if (!usageVerbatim)
                {
                    putUsage(inputTokens, cacheReadTokens, outputTokens, reasoningTokens, totalTokens);
                }

                generator.writeEnd();
            }
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

            eventBegin(generator.length());
            generator.writeStartObject()
                .writeStartObject("error")
                .write("message", message != null ? message : "")
                .write("type", type != null ? type : "server_error");

            if (param != null)
            {
                generator.write("param", param);
            }
            else
            {
                generator.writeNull("param");
            }

            if (code != null)
            {
                generator.write("code", code);
            }
            else
            {
                generator.writeNull("code");
            }

            generator.writeEnd().writeEnd();
            eventEnd(generator.length());
        }

        private int encodeLength()
        {
            return generator.length();
        }

        private void eventBegin(
            int position)
        {
            eventBegin(position, null, 0, 0);
        }

        private void eventBegin(
            int position,
            DirectBufferEx attributes,
            int offset,
            int length)
        {
            this.eventOpen = true;
            this.eventStarted = false;
            this.eventStart = position;
            this.eventAttributeOffset = eventAttributesLimit;
            this.eventAttributeLength = eventAttributesLimit + length <= eventAttributesBuffer.capacity() ? length : 0;

            if (eventAttributeLength > 0)
            {
                eventAttributesBuffer.putBytes(eventAttributesLimit, attributes, offset, length);
                eventAttributesLimit += length;
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

        private static int kindOf(
            LlmSectionType type)
        {
            return switch (type)
            {
            case ASSISTANT_TEXT -> KIND_TEXT;
            case ASSISTANT_REASONING -> KIND_REASONING;
            case ASSISTANT_REFUSAL -> KIND_REFUSAL;
            case TOOL_CALL -> KIND_TOOL_CALL;
            default -> KIND_SKIPPED;
            };
        }

        private void sectionText()
        {
            if (!server.streaming)
            {
                if (member == MEMBER_CONTENT)
                {
                    kind = KIND_TEXT;
                }
                else if (member == MEMBER_NONE && !contentWritten)
                {
                    generator.writeKey("content");
                    generator.writeStartEscaped();
                    member = MEMBER_CONTENT;
                    contentWritten = true;
                }
                else
                {
                    kind = KIND_SKIPPED;
                }
            }
        }

        private void sectionRefusal()
        {
            if (!server.streaming && member != MEMBER_REFUSAL)
            {
                if (refusalWritten)
                {
                    kind = KIND_SKIPPED;
                }
                else
                {
                    closeMember();
                    generator.writeKey("refusal");
                    generator.writeStartEscaped();
                    member = MEMBER_REFUSAL;
                    refusalWritten = true;
                }
            }
        }

        private void sectionToolCall(
            String toolCallId,
            String toolName)
        {
            final int index = toolCalls++;
            final String id = toolCallId != null ? toolCallId : "";
            final String name = toolName != null ? toolName : "";

            if (server.streaming)
            {
                chunkStart();
                generator.writeStartObject()
                    .writeKey("index").write(0)
                    .writeStartObject("delta")
                    .writeStartArray("tool_calls")
                    .writeStartObject()
                    .writeKey("index").write(index)
                    .write("id", id)
                    .write("type", "function")
                    .writeStartObject("function")
                    .write("name", name)
                    .write("arguments", "")
                    .writeEnd()
                    .writeEnd()
                    .writeEnd()
                    .writeEnd()
                    .writeNull("finish_reason")
                    .writeEnd();
                chunkEnd();
            }
            else
            {
                if (member != MEMBER_TOOL_CALLS)
                {
                    closeMember();
                    generator.writeStartArray("tool_calls");
                    member = MEMBER_TOOL_CALLS;
                }

                generator.writeStartObject()
                    .write("id", id)
                    .write("type", "function")
                    .writeStartObject("function")
                    .write("name", name)
                    .writeKey("arguments");
                generator.writeStartEscaped();
            }
        }

        private void openChoices()
        {
            generator.writeStartArray("choices")
                .writeStartObject()
                .writeKey("index").write(0)
                .writeStartObject("message")
                .write("role", "assistant");
            envelope = ENVELOPE_CHOICE;
        }

        private void closeChoice(
            String reason)
        {
            closeMember();

            if (!messageClosed)
            {
                generator.writeEnd();
                messageClosed = true;
            }

            if (reason != null)
            {
                generator.write("finish_reason", reason);
            }
            else
            {
                generator.writeNull("finish_reason");
            }

            generator.writeEnd().writeEnd();
            envelope = ENVELOPE_TAIL;
        }

        private void closeMember()
        {
            switch (member)
            {
            case MEMBER_CONTENT, MEMBER_REFUSAL:
                generator.writeEndEscaped();
                break;
            case MEMBER_TOOL_CALLS:
                generator.writeEnd();
                break;
            default:
                break;
            }

            member = MEMBER_NONE;
        }

        private void dataChunk(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            dataChunkStart();

            generator.writeSegment(payload, offset, length);
            dataChunkEnd();
        }

        private void dataChunkStart()
        {
            chunkStart();
            generator.writeStartObject()
                .writeKey("index").write(0)
                .writeStartObject("delta");

            switch (kind)
            {
            case KIND_TEXT, KIND_REASONING:
                generator.writeKey("content");
                break;
            case KIND_REFUSAL:
                generator.writeKey("refusal");
                break;
            default:
                generator.writeStartArray("tool_calls")
                    .writeStartObject()
                    .writeKey("index").write(toolCalls - 1)
                    .writeStartObject("function")
                    .writeKey("arguments");
                break;
            }

            generator.writeStartEscaped();
        }

        private void dataChunkEnd()
        {
            generator.writeEndEscaped();

            if (kind == KIND_TOOL_CALL)
            {
                generator.writeEnd().writeEnd().writeEnd();
            }

            generator.writeEnd()
                .writeNull("finish_reason")
                .writeEnd();
            chunkEnd();
        }

        private void chunkStart()
        {
            eventBegin(generator.length());
            generator.writeStartObject();

            if (responseId != null)
            {
                generator.write("id", responseId);
            }

            generator.write("object", OBJECT_CHUNK);

            if (responseModel != null)
            {
                generator.write("model", responseModel);
            }

            writeControls();

            generator.writeStartArray("choices");
        }

        private void chunkEnd()
        {
            generator.writeEnd();

            if (server.obfuscation)
            {
                writeObfuscation();
            }

            generator.writeEnd();
            eventEnd(generator.length());
        }

        private void putUsage(
            int inputTokens,
            int cacheReadTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            final int total = totalTokens != NONE || inputTokens == NONE || outputTokens == NONE
                ? totalTokens
                : inputTokens + outputTokens;

            if (inputTokens != NONE || outputTokens != NONE || total != NONE)
            {
                generator.writeStartObject("usage");

                if (inputTokens != NONE)
                {
                    generator.writeKey("prompt_tokens").write(inputTokens);
                }

                if (outputTokens != NONE)
                {
                    generator.writeKey("completion_tokens").write(outputTokens);
                }

                if (total != NONE)
                {
                    generator.writeKey("total_tokens").write(total);
                }

                if (cacheReadTokens != NONE)
                {
                    generator.writeStartObject("prompt_tokens_details")
                        .writeKey("cached_tokens").write(cacheReadTokens)
                        .writeEnd();
                }

                if (reasoningTokens != NONE)
                {
                    generator.writeStartObject("completion_tokens_details")
                        .writeKey("reasoning_tokens").write(reasoningTokens)
                        .writeEnd();
                }

                generator.writeEnd();
            }
        }

        private String finishReason(
            String finishReason)
        {
            String reason = toolCalls > 0 ? "tool_calls" : "stop";

            if (finishReason != null)
            {
                reason = switch (finishReason)
                {
                case "tool-call" -> "tool_calls";
                case "content-filter" -> "content_filter";
                default -> finishReason;
                };
            }

            return reason;
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
