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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonException;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.llm.config.LlmAuthorizationResult;
import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.codec.LlmContentCodecFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
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
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockOwner;
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
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.util.Flags;

public final class LlmAnthropicServerFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_TYPE = "type";
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-api-key";
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String METHOD_POST = "POST";
    private static final String PATH_MESSAGES = "/v1/messages";
    private static final String STATUS_OK = "200";
    private static final String STATUS_UNAUTHORIZED = "401";
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String UNAUTHORIZED_BODY = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"," +
        "\"message\":\"invalid x-api-key\"}}";

    private static final String MESSAGE_PARSE_ERROR = "There was an error parsing the body";
    private static final String MESSAGE_MODEL_MISSING = "model: Field required";
    private static final String MESSAGE_MODEL_ORDER =
        "model: must precede messages, system and tools in a request that does not fit the decode buffer";
    private static final String MESSAGE_PART_TEXT_MISSING = "text: Field required";
    private static final String MESSAGE_TOOL_RESULT_CONTENT_MISSING = "content: Field required";
    private static final String MESSAGE_ROLE_MISSING = "messages.%d.role: Field required";
    private static final String MESSAGE_ROLE_ORDER = "role must precede content in each message";
    private static final String MESSAGE_TOOL_USE_ID_ORDER = "tool_use_id must precede content in a tool_result block";
    private static final String MESSAGE_PART_TYPE_ORDER = "type must be the first member of each content block";
    private static final String MESSAGE_ATTRIBUTE_TOO_LARGE = "a content block attribute value is too large";

    private static final int RESPONSE_ENCODE_PADDING = 128;
    private static final int RESPONSE_ESCAPE_EXPANSION = 6;
    private static final int RESPONSE_ENCODE_SLACK = 2048;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx BRACKET = new UnsafeBufferEx(new byte[] {'['});

    private static final DirectBufferEx STREAM_TRUE = new UnsafeBufferEx("true".getBytes(UTF_8));
    private static final DirectBufferEx STREAM_FALSE = new UnsafeBufferEx("false".getBytes(UTF_8));
    private static final DirectBufferEx STREAM_NULL = new UnsafeBufferEx("null".getBytes(UTF_8));
    private static final int BLOCK_SECTION = 0;
    private static final int BLOCK_STREAM = 1;
    private static final int BLOCK_BEFORE = 2;
    private static final int BLOCK_AFTER = 3;
    private static final int MIN_WINDOW = 64;
    private static final int HOLD_MAX = 1024;

    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_TOOL_RESULT = "tool_result";
    private static final String SUFFIX_TOOL_RESULT = "_tool_result";

    private static final int CONTEXT_MESSAGE = 0;
    private static final int CONTEXT_SYSTEM = 1;
    private static final int CONTEXT_TOOL_RESULT = 2;


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
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();
    private final LlmSectionAttributes replyAttributes = new LlmSectionAttributes();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmResetExFW llmResetExRO = new LlmResetExFW();
    private final LlmEndExFW llmEndExRO = new LlmEndExFW();
    private final LlmAbortExFW llmAbortExRO = new LlmAbortExFW();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
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

    private final LlmAnthropicServerDecoder decodeStart = this::decodeStart;
    private final LlmAnthropicServerDecoder decodeRootStart = this::decodeRootStart;
    private final LlmAnthropicServerDecoder decodeModelMember = this::decodeModelMember;
    private final LlmAnthropicServerDecoder decodeModelValue = this::decodeModelValue;
    private final LlmAnthropicServerDecoder decodeRoot = this::decodeRoot;
    private final LlmAnthropicServerDecoder decodeMessagesStart = this::decodeMessagesStart;
    private final LlmAnthropicServerDecoder decodeMessages = this::decodeMessages;
    private final LlmAnthropicServerDecoder decodeMessage = this::decodeMessage;
    private final LlmAnthropicServerDecoder decodeRole = this::decodeRole;
    private final LlmAnthropicServerDecoder decodeSystemStart = this::decodeSystemStart;
    private final LlmAnthropicServerDecoder decodeToolResult = this::decodeToolResult;
    private final LlmAnthropicServerDecoder decodeToolUseId = this::decodeToolUseId;
    private final LlmAnthropicServerDecoder decodeToolResultContent = this::decodeToolResultContent;
    private final LlmAnthropicServerDecoder decodeCitationsStart = this::decodeCitationsStart;
    private final LlmAnthropicServerDecoder decodeContent = this::decodeContent;
    private final LlmAnthropicServerDecoder decodeToolsStart = this::decodeToolsStart;
    private final LlmAnthropicServerDecoder decodeTools = this::decodeTools;
    private final LlmAnthropicServerDecoder decodeParts = this::decodeParts;
    private final LlmAnthropicServerDecoder decodePartType = this::decodePartType;
    private final LlmAnthropicServerDecoder decodePartTypeValue = this::decodePartTypeValue;
    private final LlmAnthropicServerDecoder decodePart = this::decodePart;
    private final LlmAnthropicServerDecoder decodeAttributes = this::decodeAttributes;
    private final LlmAnthropicServerDecoder decodeToolIsError = this::decodeToolIsError;
    private final LlmAnthropicServerDecoder decodeCapture = this::decodeCapture;
    private final LlmAnthropicServerDecoder decodeText = this::decodeText;
    private final LlmAnthropicServerDecoder decodeSkip = this::decodeSkip;
    private final LlmAnthropicServerDecoder decodeStreamValue = this::decodeStreamValue;
    private final LlmAnthropicServerDecoder decodeEnd = this::decodeEnd;
    private final LlmAnthropicServerDecoder decodeIgnore = this::decodeIgnore;

    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;

    public LlmAnthropicServerFactory(
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
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
        final boolean pathMatched = METHOD_POST.equalsIgnoreCase(header(headers, HEADER_METHOD)) &&
            PATH_MESSAGES.equals(header(headers, HEADER_PATH));
        final boolean versionPresent = header(headers, HEADER_ANTHROPIC_VERSION) != null;
        final boolean apiKeyWithoutAuthorization = header(headers, HEADER_API_KEY) != null &&
            header(headers, HEADER_AUTHORIZATION) == null;

        return pathMatched || versionPresent || apiKeyWithoutAuthorization;
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
        final LlmRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        MessageConsumer newStream = null;

        if (route != null && httpBeginEx != null)
        {
            final LlmRequestHeaders headers = LlmRequestHeaders.of(httpBeginEx);
            final String contentType = header(headers, HEADER_CONTENT_TYPE);
            final String version = header(headers, HEADER_ANTHROPIC_VERSION);

            if (!CONTENT_TYPE_JSON.equals(LlmContentCodecFactory.mediaType(contentType)))
            {
                newStream = new LlmAnthropicRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_UNSUPPORTED_MEDIA_TYPE),
                    errorBody(STATUS_UNSUPPORTED_MEDIA_TYPE, null))::onNetMessage;
            }
            else
            {
                final LlmAuthorizationResult authResult = binding.authorize(
                    begin.traceId(), routedId, initialId, authorization, headers, HEADER_API_KEY);

                if (!authResult.authorized())
                {
                    newStream = new LlmAnthropicRejectHandler(network, originId, routedId, initialId,
                        STATUS_UNAUTHORIZED, UNAUTHORIZED_BODY)::onNetMessage;
                }
                else if (version == null || !ANTHROPIC_VERSION.equals(version))
                {
                    newStream = new LlmAnthropicRejectHandler(network, originId, routedId, initialId,
                        Integer.toString(STATUS_BAD_REQUEST),
                        errorBody(STATUS_BAD_REQUEST, version == null
                            ? "anthropic-version: header is required"
                            : "anthropic-version: unsupported version " + version))::onNetMessage;
                }
                else
                {
                    newStream = new LlmAnthropicServer(
                            network,
                            originId,
                            routedId,
                            initialId,
                            route.id,
                            authResult.authorization(),
                            contentType,
                            authResult.deauthorize())::onNetMessage;
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
        String message)
    {
        return Json.createObjectBuilder()
            .add("type", "error")
            .add("error", Json.createObjectBuilder()
                .add("type", errorType(status))
                .add("message", message != null ? message : reason(status)))
            .build()
            .toString();
    }

    private static String errorType(
        int status)
    {
        return switch (status)
        {
        case 401 -> "authentication_error";
        case 403 -> "permission_error";
        case 404 -> "not_found_error";
        case 413 -> "request_too_large";
        case 429 -> "rate_limit_error";
        case 529 -> "overloaded_error";
        default -> status >= 500 ? "api_error" : "invalid_request_error";
        };
    }

    @FunctionalInterface
    private interface LlmAnthropicServerDecoder
    {
        int decode(
            LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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
                else if (matches(key, "system"))
                {
                    server.decoder = decodeSystemStart;
                }
                else if (matches(key, "tools"))
                {
                    server.decoder = decodeToolsStart;
                }
                else
                {
                    server.onDecodeOption(key.toString(), decodeRoot);
                }
                break;
            case END_OBJECT:
                server.decoder = decodeEnd;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeSystemStart(
        LlmAnthropicServer server,
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
            case VALUE_STRING:
                server.context = CONTEXT_SYSTEM;
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server), decodeRoot);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event);
                break decode;
            case START_ARRAY:
                server.context = CONTEXT_SYSTEM;
                server.partsThen = decodeRoot;
                server.decoder = decodeParts;
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

    private int decodeMessagesStart(
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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
                server.toolUseId = null;
                server.toolIsError = null;
                server.context = CONTEXT_MESSAGE;
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
        LlmAnthropicServer server,
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
                else if (matches(key, "content"))
                {
                    if (server.role == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_ROLE_ORDER);
                        break decode;
                    }
                    server.decoder = decodeContent;
                }
                else
                {
                    server.skip(decodeMessage);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                if (server.role == null)
                {
                    server.onDecodeInvalidRequest(traceId, String.format(MESSAGE_ROLE_MISSING, server.message));
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
        LlmAnthropicServer server,
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
                server.role = parser.getString();
                server.decoder = decodeMessage;
            }
        }

        return server.position();
    }

    private int decodeContent(
        LlmAnthropicServer server,
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
            case VALUE_STRING:
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server),
                    decodeMessage);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event);
                break decode;
            case START_ARRAY:
                server.partsThen = decodeMessage;
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

    private int decodeToolsStart(
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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
                server.probe(LlmSectionType.TOOL_DEFINITION, -1, null, null, decodeTools);
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
        LlmAnthropicServer server,
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
                server.partTextDone = false;
                server.partPre = false;
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
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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

                if (TYPE_TEXT.equals(type))
                {
                    server.release();
                    server.decoder = decodePart;
                }
                else if (TYPE_TOOL_RESULT.equals(type) && server.context == CONTEXT_MESSAGE)
                {
                    server.release();
                    server.toolUseId = null;
                    server.toolIsError = null;
                    server.toolContentSeen = false;
                    server.containerPre = false;
                    server.decoder = decodeToolResult;
                }
                else
                {
                    final LlmSectionType blockType = partBlockType(server, type);
                    final String extension = blockType == LlmSectionType.SERVER_TOOL_RESULT ? type : null;

                    if (LlmAnthropicAttributeProbe.carries(blockType))
                    {
                        server.probe(blockType, textMessage(server), extension, type, decodeParts);
                        server.decoder = decodeAttributes;
                    }
                    else
                    {
                        server.captureHeld(blockType, textMessage(server), extension, decodeParts, 1);
                        server.decoder = decodeCapture;
                    }
                }
            }
        }

        return server.position();
    }

    private int decodePart(
        LlmAnthropicServer server,
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
                    server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server),
                        decodePart);
                    server.decoder = decodeText;
                }
                else if (matches(key, "citations"))
                {
                    server.decoder = decodeCitationsStart;
                }
                else
                {
                    server.onDecodePartOption(key.toString(), textBlockType(server), textMessage(server),
                        textToolCallId(server));
                }
                break;
            case END_OBJECT:
                if (server.partPre)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_PART_TEXT_MISSING);
                    break decode;
                }
                server.decoder = decodeParts;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeCitationsStart(
        LlmAnthropicServer server,
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
                server.capture(traceId, authorization, LlmSectionType.CITATIONS, textMessage(server), null, BRACKET, decodePart);
                server.decoder = decodeCapture;
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

    private int decodeToolResult(
        LlmAnthropicServer server,
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
                if (matches(key, "tool_use_id"))
                {
                    server.decoder = decodeToolUseId;
                }
                else if (matches(key, "is_error"))
                {
                    server.decoder = decodeToolIsError;
                }
                else if (matches(key, "content"))
                {
                    if (server.toolUseId == null)
                    {
                        server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_USE_ID_ORDER);
                        break decode;
                    }
                    server.toolContentSeen = true;
                    server.decoder = decodeToolResultContent;
                }
                else if (!server.toolContentSeen && server.toolUseId == null)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_USE_ID_ORDER);
                    break decode;
                }
                else
                {
                    server.onDecodeContainerOption(key.toString());
                }
                break;
            case END_OBJECT:
                if (server.containerPre)
                {
                    server.onDecodeInvalidRequest(traceId, MESSAGE_TOOL_RESULT_CONTENT_MISSING);
                    break decode;
                }
                server.flushHeld();
                server.context = CONTEXT_MESSAGE;
                server.partsThen = decodeMessage;
                server.decoder = decodeParts;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeToolUseId(
        LlmAnthropicServer server,
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
                server.toolUseId = parser.getString();
                server.decoder = decodeToolResult;
            }
        }

        return server.position();
    }

    private int decodeToolIsError(
        LlmAnthropicServer server,
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
            case VALUE_TRUE:
                server.onDecodeToolIsError(LlmSectionAttributes.TRUE);
                server.decoder = decodeToolResult;
                break;
            case VALUE_FALSE:
                server.onDecodeToolIsError(LlmSectionAttributes.FALSE);
                server.decoder = decodeToolResult;
                break;
            case VALUE_NULL:
                server.decoder = decodeToolResult;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }
        }

        return server.position();
    }

    private int decodeToolResultContent(
        LlmAnthropicServer server,
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
            case VALUE_STRING:
                server.context = CONTEXT_TOOL_RESULT;
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server),
                    decodeToolResult);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event);
                break decode;
            case START_ARRAY:
                server.context = CONTEXT_TOOL_RESULT;
                server.partsThen = decodeToolResult;
                server.decoder = decodeParts;
                break;
            case VALUE_NULL:
                server.decoder = decodeToolResult;
                break;
            default:
                server.onDecodeParseError(traceId);
                break decode;
            }

        }

        return server.position();
    }

    private int decodeAttributes(
        LlmAnthropicServer server,
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
                server.decoder = decodeCapture;
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
                    server.decoder = decodeCapture;
                }
                break;
            }
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeCapture(
        LlmAnthropicServer server,
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

        while (server.decoder == decodeCapture && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            final boolean deferred = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (server.probing)
            {
                server.probe.onEvent(event, parser);
                server.captureDepth = server.probe.depth();
            }
            else
            {
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
                    if (parser.deferredBytes())
                    {
                        parser.consumed(parser.getStringView().length());
                    }
                    break;
                default:
                    break;
                }
            }

            if (server.captureDepth == 0 && !deferred)
            {
                server.endCapture(traceId, authorization);
            }

        }

        return server.position();
    }

    private int decodeText(
        LlmAnthropicServer server,
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

        while (server.decoder == decodeText && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event);
        }

        return server.position();
    }

    private int decodeTextValue(
        LlmAnthropicServer server,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int progress,
        int limit,
        JsonEvent event)
    {
        final JsonParserEx parser = server.parser;

        server.decoder = decodeText;

        if (event == JsonEvent.VALUE_STRING)
        {
            if (!server.textOpen)
            {
                server.onDecodeBlock(server.textType, server.textMessage, null);
                server.blockAttributes.toolCallId(server.textToolCallId);
                server.blockAttributes.isError(server.textIsError);
                server.textOpen = true;
            }

            final boolean fin = !parser.deferredBytes();
            final CharSequence chars = parser.getStringView();
            final int taken = chars.length();
            final int length = encode(server, chars, fin);

            if (fin && server.context == CONTEXT_TOOL_RESULT && server.toolIsError == null)
            {
                server.hold(traceId, authorization, textBuffer, length);
            }
            else
            {
                server.onDecodeData(traceId, authorization, textBuffer, 0, length, fin);
            }

            if (fin)
            {
                server.textOpen = false;
                server.partTextDone = true;
                server.partPre = false;
                server.decoder = server.textThen;
            }
            else
            {
                parser.consumed(taken);
            }
        }
        else if (event == JsonEvent.VALUE_NULL && !server.textOpen)
        {
            server.decoder = server.textThen;
        }
        else
        {
            server.onDecodeParseError(traceId);
        }

        return server.decoder == decodeIgnore ? limit : server.position();
    }

    private int decodeStreamValue(
        LlmAnthropicServer server,
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
            final DirectBufferEx value = switch (event)
            {
            case VALUE_TRUE -> STREAM_TRUE;
            case VALUE_FALSE -> STREAM_FALSE;
            case VALUE_NULL -> STREAM_NULL;
            default -> null;
            };

            if (value == null)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.streaming = event == JsonEvent.VALUE_TRUE;
            server.onDecodeData(traceId, authorization, value, 0, value.capacity(), true);
            server.decoder = server.captureThen;
        }

        return server.position();
    }

    private static boolean separator(
        byte value)
    {
        return value == ':' || value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private int decodeSkip(
        LlmAnthropicServer server,
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

    private static LlmSectionType textBlockType(
        LlmAnthropicServer server)
    {
        final LlmSectionType type;

        if (server.context == CONTEXT_SYSTEM)
        {
            type = LlmSectionType.SYSTEM_INSTRUCTION;
        }
        else if (server.context == CONTEXT_TOOL_RESULT)
        {
            type = LlmSectionType.TOOL_RESULT;
        }
        else if (ROLE_USER.equals(server.role))
        {
            type = LlmSectionType.USER_TEXT;
        }
        else if (ROLE_ASSISTANT.equals(server.role))
        {
            type = LlmSectionType.ASSISTANT_TEXT;
        }
        else
        {
            type = LlmSectionType.UNKNOWN;
        }

        return type;
    }

    private static String textToolCallId(
        LlmAnthropicServer server)
    {
        return server.context == CONTEXT_TOOL_RESULT ? server.toolUseId : null;
    }

    private static String textIsError(
        LlmAnthropicServer server)
    {
        return server.context == CONTEXT_TOOL_RESULT ? server.toolIsError : null;
    }

    private static int textMessage(
        LlmAnthropicServer server)
    {
        return server.context == CONTEXT_SYSTEM ? -1 : server.message;
    }

    private static LlmSectionType partBlockType(
        LlmAnthropicServer server,
        String type)
    {
        LlmSectionType blockType = LlmSectionType.UNKNOWN;

        if (server.context == CONTEXT_MESSAGE)
        {
            blockType = switch (type)
            {
            case "thinking", "redacted_thinking" -> LlmSectionType.ASSISTANT_REASONING;
            case "tool_use" -> LlmSectionType.TOOL_CALL;
            case "image" -> LlmSectionType.USER_IMAGE;
            case "document" -> LlmSectionType.USER_DOCUMENT;
            case "server_tool_use" -> LlmSectionType.SERVER_TOOL_RESULT;
            default -> type.endsWith(SUFFIX_TOOL_RESULT) ? LlmSectionType.SERVER_TOOL_RESULT : LlmSectionType.UNKNOWN;
            };
        }

        return blockType;
    }

    private int encode(
        LlmAnthropicServer server,
        CharSequence chars,
        boolean fin)
    {
        final int length = chars.length();

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
        LlmAnthropicServer server,
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
        LlmAnthropicServer server,
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

    private final class LlmAnthropicServer
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
        private final Deque<SlotChunk> encodeChunks;

        private LlmAnthropicStream stream;
        private LlmAnthropicServerDecoder decoder;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

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
        private boolean flushingReply;

        private long pendingReplyEndTraceId;
        private long pendingReplyEndAuthorization;

        private int message;
        private String role;
        private String toolUseId;
        private String toolIsError;
        private int context;
        private LlmAnthropicServerDecoder partsThen;

        private LlmAnthropicServerDecoder skipThen;
        private int skipDepth;

        private boolean streaming;

        private LlmAnthropicServerDecoder textThen;
        private LlmSectionType textType;
        private String textToolCallId;
        private String textIsError;
        private int textMessage;
        private boolean textOpen;
        private char textHighSurrogate;

        private LlmAnthropicServerDecoder probeThen;
        private LlmSectionType probeType;
        private int probeMessage;
        private String probeExtension;

        private LlmAnthropicServerDecoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;
        private boolean captureTrim;

        private boolean holding;
        private boolean lookahead;
        private boolean partTextDone;
        private boolean partPre;
        private boolean toolContentSeen;
        private boolean containerPre;
        private boolean replaying;
        private int holdFrom;
        private int retained;

        private boolean heldPending;
        private MutableDirectBufferEx heldBuffer;
        private int heldLength;
        private long heldTraceId;
        private long heldAuthorization;
        private String lateIsError;

        private final LlmAnthropicAttributeProbe probe;
        private final LlmSectionAttributes blockAttributes;
        private final LlmSectionAttributes sentAttributes;
        private final LlmSectionAttributes finAttributes;
        private boolean probing;

        private String blockName;
        private int blockKind;
        private LlmSectionType blockSection;
        private LlmBlockOwner blockOwner;
        private int blockMessage;
        private String blockExtension;
        private int blockFlags;

        private LlmAnthropicServer(
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
            this.parser = JsonEx.createParser();
            this.probe = new LlmAnthropicAttributeProbe();
            this.lookahead = true;
            this.holding = true;
            this.blockAttributes = new LlmSectionAttributes();
            this.sentAttributes = new LlmSectionAttributes();
            this.finAttributes = new LlmSectionAttributes();
            this.encodeChunks = new ArrayDeque<>();
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
                int window = windowFor(offset, limit);

                if (heldPending && window < held + Math.min(MIN_WINDOW, limit - offset - held))
                {
                    flushHeld();
                    window = windowFor(offset, limit);
                }

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
            captureFrom = offset;
            holdFrom = offset;
            parser.wrap(buffer, offset + retained, windowLimit, windowLast);

            boolean replayed = false;
            LlmAnthropicServerDecoder previous = null;
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

        private int windowFor(
            int offset,
            int limit)
        {
            final int reserved = heldPending ? heldLength : 0;

            return stream != null ? (int) Math.min(limit - offset, available() - reserved) : limit - offset;
        }

        private int available()
        {
            return stream != null ? (int) Math.max(stream.initialAvailable(), 0L) : 0;
        }

        private int position()
        {
            return decodedWindowLimit - parser.remaining();
        }

        private void skip(
            LlmAnthropicServerDecoder then)
        {
            skipThen = then;
            skipDepth = 0;
        }

        private void text(
            LlmSectionType type,
            String toolCallId,
            String isError,
            int message,
            LlmAnthropicServerDecoder then)
        {
            textType = type;
            textToolCallId = toolCallId;
            textIsError = isError;
            textMessage = message;
            textThen = then;
            textOpen = false;
        }

        private void capture(
            long traceId,
            long authorization,
            LlmSectionType type,
            int blockMessage,
            String extension,
            DirectBufferEx open,
            LlmAnthropicServerDecoder then)
        {
            onDecodeBlock(type, blockMessage, extension);
            onDecodeData(traceId, authorization, open, 0, open.capacity(), false);

            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = position();
        }

        private void probe(
            LlmSectionType type,
            int message,
            String extension,
            String nativeType,
            LlmAnthropicServerDecoder then)
        {
            probeType = type;
            probeMessage = message;
            probeExtension = extension;
            probeThen = then;
            probe.begin(type, nativeType);
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

        private void hold(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int length)
        {
            if (heldBuffer == null || heldBuffer.capacity() < length)
            {
                heldBuffer = new UnsafeBufferEx(new byte[length]);
            }

            heldBuffer.putBytes(0, buffer, 0, length);
            heldLength = length;
            heldTraceId = traceId;
            heldAuthorization = authorization;
            heldPending = true;
        }

        private void flushHeld()
        {
            if (heldPending)
            {
                heldPending = false;
                onDecodeData(heldTraceId, heldAuthorization, heldBuffer, 0, heldLength, true);
                lateIsError = null;
            }
        }

        private void onDecodeToolIsError(
            String isError)
        {
            toolIsError = isError;

            if (heldPending)
            {
                lateIsError = isError;
                blockAttributes.isError(isError);
                flushHeld();
            }
        }

        private void captureHeld(
            LlmSectionType type,
            int blockMessage,
            String extension,
            LlmAnthropicServerDecoder then,
            int depth)
        {
            onDecodeBlock(type, blockMessage, extension);

            captureThen = then;
            captureOpen = true;
            captureDepth = depth;
            captureFrom = holdFrom;
            release();
        }

        private void captureProbed()
        {
            captureHeld(probeType, probeMessage, probeExtension, probeThen, probe.depth());
            probing = true;
        }

        private void endCapture(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, true);
            captureOpen = false;
            captureTrim = false;
            probing = false;
            decoder = captureThen;
        }

        private void flushCapture(
            long traceId,
            long authorization,
            boolean fin)
        {
            final int end = position();
            int from = captureFrom;

            if (captureTrim)
            {
                while (from < end && separator(decodedInput.getByte(from)))
                {
                    from++;
                }

                captureTrim = from == end;
            }

            if (end > from || fin)
            {
                onDecodeData(traceId, authorization, decodedInput, from, end - from, fin);
            }

            captureFrom = end;
        }

        private void onDecodeOption(
            String member,
            LlmAnthropicServerDecoder then)
        {
            captureThen = then;
            onDecodeDialectBlock(member);

            if ("stream".equals(member))
            {
                decoder = decodeStreamValue;
            }
            else
            {
                captureOpen = true;
                captureDepth = 0;
                captureFrom = position();
                captureTrim = true;
                decoder = decodeCapture;
            }
        }

        private void onDecodePartOption(
            String member,
            LlmSectionType sectionType,
            int message,
            String toolCallId)
        {
            final boolean before = !partTextDone;

            captureThen = decodePart;
            partPre |= before;

            onDecodeSectionBlock(member, before ? BLOCK_BEFORE : BLOCK_AFTER, LlmBlockOwner.SECTION, sectionType, message,
                before ? toolCallId : null);

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            captureTrim = true;
            decoder = decodeCapture;
        }

        private void onDecodeContainerOption(
            String member)
        {
            final boolean before = !toolContentSeen;

            captureThen = decodeToolResult;
            containerPre |= before;

            onDecodeSectionBlock(member, before ? BLOCK_BEFORE : BLOCK_AFTER, LlmBlockOwner.CONTAINER,
                LlmSectionType.TOOL_RESULT, this.message, before ? toolUseId : null);

            captureOpen = true;
            captureDepth = 0;
            captureFrom = position();
            captureTrim = true;
            decoder = decodeCapture;
        }

        private void onDecodeSectionBlock(
            String block,
            int kind,
            LlmBlockOwner owner,
            LlmSectionType sectionType,
            int message,
            String toolCallId)
        {
            flushHeld();

            blockName = block;
            blockKind = kind;
            blockOwner = owner;
            blockSection = sectionType;
            blockMessage = message;
            blockExtension = null;
            blockFlags = Flags.INIT;

            blockAttributes.clear();
            blockAttributes.toolCallId(toolCallId);
        }

        private void onDecodeDialectBlock(
            String block)
        {
            flushHeld();

            blockName = block;
            blockKind = BLOCK_STREAM;
            blockSection = null;
            blockMessage = -1;
            blockExtension = null;
            blockFlags = Flags.INIT;

            blockAttributes.clear();
        }

        private void onDecodeModel(
            long traceId,
            long authorization,
            String model)
        {
            decoder = decodeRoot;
            stream = new LlmAnthropicStream(this);
            stream.doAppBegin(traceId, authorization, model);

            parser.reset();
            decoder = decodeStart;
            replaying = true;

            lookahead = false;
            holding = false;
            retained = 0;
        }

        private void onDecodeBlock(
            LlmSectionType type,
            int message,
            String extension)
        {
            flushHeld();

            containerPre = false;
            blockName = null;
            blockKind = BLOCK_SECTION;
            blockSection = type;
            blockMessage = message;
            blockExtension = extension;
            blockFlags = Flags.INIT;

            blockAttributes.clear();
        }

        private void onDecodeData(
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
            else if (lateIsError != null && last)
            {
                finAttributes.clear();
                finAttributes.isError(lateIsError);
                attributes = finAttributes;
            }
            else
            {
                attributes = null;
            }

            stream.doAppBlockData(traceId, authorization, flags, blockKind, init ? blockName : null, blockOwner,
                init ? blockSection : null, blockMessage, init ? blockExtension : null, attributes, buffer, offset, length);

            blockFlags = Flags.NONE;
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
                stream.doAppFlush(traceId, initialAuthorization);

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
            heldPending = false;

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

            doNetError(traceId, authorization, status, error.message().asString());
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String message)
        {
            final DirectBufferEx body = asBuffer(errorBody(status, message));

            cleanupDecodeSlot();

            doNetBegin(traceId, authorization, 0L, Integer.toString(status), CONTENT_TYPE_JSON);
            doNetData(body, 0, body.capacity(), 0, Flags.COMPLETE, null, 0L, traceId, authorization);

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
                encodeChunks.clear();
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
            doNetData(payload.buffer(), payload.offset(), payload.sizeof(), payload.sizeof(), flags, null, budgetId,
                traceId, authorization);
        }

        private void doNetEvents(
            DirectBufferEx buffer,
            LlmEventFrames frames,
            int length,
            int reserved,
            long budgetId,
            long traceId,
            long authorization)
        {
            if (streaming)
            {
                frames.flush(length);

                final int count = frames.count();

                for (int index = 0; index < count; index++)
                {
                    doNetData(buffer, frames.offset(index), frames.length(index), index == count - 1 ? reserved : 0,
                        frames.flags(index), frames.type(index), budgetId, traceId, authorization);
                }

                frames.clear();

                if (count == 0 && stream != null)
                {
                    stream.ackReply(traceId);
                }
            }
            else
            {
                doNetData(buffer, 0, length, reserved, Flags.COMPLETE, null, budgetId, traceId, authorization);
            }
        }

        private void doNetData(
            DirectBufferEx buffer,
            int offset,
            int length,
            int rawLength,
            int flags,
            String type,
            long budgetId,
            long traceId,
            long authorization)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            if (encodeSlot == NO_SLOT || encodeSlotOffset + length > encodePool.slotCapacity())
            {
                doNetReset(traceId);
                stream.doAppAbort(traceId);
                cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                slotBuffer.putBytes(encodeSlotOffset, buffer, offset, length);
                encodeSlotOffset += length;
                encodeSlotRawOffset += rawLength;
                encodeChunks.add(new SlotChunk(length, rawLength, flags, type, budgetId, traceId, authorization));

                flushEncodeSlot(traceId);
            }
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            String type,
            MutableDirectBufferEx buffer,
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

            if (type != null)
            {
                final HttpDataExFW dataEx = httpDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HEADER_TYPE).value(type))
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
                while (!encodeChunks.isEmpty())
                {
                    final long available = replyMax - (replySeq - replyAck) - replyPad;
                    if (available <= 0)
                    {
                        break;
                    }

                    final SlotChunk chunk = encodeChunks.peek();
                    final int remaining = chunk.length - chunk.sent;
                    final int sliceLength = (int) Math.min(remaining, available);
                    if (sliceLength <= 0 && remaining > 0)
                    {
                        break;
                    }

                    final int sliceOffset = chunk.sent;
                    chunk.sent += sliceLength;

                    final int initFlags = sliceOffset == 0 && Flags.hasInit(chunk.flags) ? Flags.INIT : Flags.NONE;
                    final int flags = chunk.sent == chunk.length && Flags.hasFin(chunk.flags) ? Flags.fin(initFlags) : initFlags;
                    doNetData(chunk.traceId, chunk.authorization, flags, chunk.budgetId,
                        sliceLength + replyPad, initFlags == Flags.INIT ? chunk.type : null, buffer, sliceOffset, sliceLength);

                    if (chunk.sent >= chunk.length)
                    {
                        encodeChunks.poll();
                        if (encodeSlotOffset > chunk.length)
                        {
                            buffer.putBytes(0, buffer, chunk.length, encodeSlotOffset - chunk.length);
                        }
                        encodeSlotOffset -= chunk.length;
                        encodeSlotRawOffset -= chunk.rawLength;
                    }
                }

                if (encodeChunks.isEmpty())
                {
                    encodePool.release(encodeSlot);
                    encodeSlot = NO_SLOT;
                    encodeSlotOffset = 0;
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

    private final class LlmAnthropicStream
    {
        private final LlmAnthropicServer server;

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

        private LlmAnthropicResponseEncoder responseEncoder;
        private boolean replyDialect;
        private int replyKind;
        private boolean flushed;

        private LlmAnthropicStream(
            LlmAnthropicServer server)
        {
            this.server = server;
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

            if (resetEx != null && !LlmState.replyOpening(server.state))
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
            replyDialect = llmBeginEx != null && NAME.equals(llmBeginEx.dialect().asString());
            responseEncoder = new LlmAnthropicResponseEncoder(server.streaming,
                llmBeginEx != null ? llmBeginEx.id().asString() : null,
                llmBeginEx != null ? llmBeginEx.model().asString() : null);

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                server.streaming ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

            final int length = responseEncoder.begin(copyBuffer, 0);
            server.doNetEvents(copyBuffer, responseEncoder.frames(), length, 0, 0L, traceId, authorization);

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
            final OctetsFW payload = data.payload();

            int length = 0;

            if (Flags.hasInit(flags))
            {
                final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);

                replyKind = llmDataEx != null ? llmDataEx.kind() : LlmDataExFW.KIND_SECTION;

                switch (replyKind)
                {
                case LlmDataExFW.KIND_BLOCK:
                    length += responseEncoder.blockBegin(copyBuffer, length, llmDataEx.block().block().asString(), replyDialect);
                    break;
                case LlmDataExFW.KIND_SECTION:
                    final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;

                    replyAttributes.readFrom(section);

                    length += responseEncoder.section(copyBuffer, length,
                        section != null ? section.type().get() : LlmSectionType.NONE,
                        section != null ? section.message() : -1,
                        replyAttributes.toolCallId(),
                        replyAttributes.toolName());
                    break;
                default:
                    break;
                }
            }

            switch (replyKind)
            {
            case LlmDataExFW.KIND_BLOCK:
                length += responseEncoder.blockData(copyBuffer, length, payload.buffer(), payload.offset(), payload.sizeof());
                break;
            case LlmDataExFW.KIND_SECTION:
                if (payload.sizeof() > 0)
                {
                    length += responseEncoder.data(copyBuffer, length, payload.buffer(), payload.offset(), payload.sizeof());
                }

                if (Flags.hasFin(flags))
                {
                    length += responseEncoder.end(copyBuffer, length);
                }
                break;
            default:
                break;
            }

            if (length > 0)
            {
                server.doNetEvents(copyBuffer, responseEncoder.frames(), length, reserved, budgetId, traceId, authorization);
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

            int length = responseEncoder.end(copyBuffer, 0);

            length += responseEncoder.finish(copyBuffer, length,
                llmEndEx != null ? llmEndEx.finishReason().asString() : null,
                usage != null ? usage.inputTokens() : -1,
                usage != null ? usage.cacheWriteTokens() : -1,
                usage != null ? usage.cacheReadTokens() : -1,
                usage != null ? usage.outputTokens() : -1,
                usage != null ? usage.reasoningTokens() : -1,
                usage != null ? usage.totalTokens() : -1);

            server.doNetEvents(copyBuffer, responseEncoder.frames(), length, 0, 0L, traceId, authorization);

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

            final LlmAbortExFW llmAbortEx = responseEncoder != null && server.streaming
                ? extension.get(llmAbortExRO::tryWrap)
                : null;
            final LlmErrorFW error = llmAbortEx != null ? llmAbortEx.error() : null;

            if (error != null && (error.type().asString() != null || error.message().asString() != null))
            {
                final int length = responseEncoder.error(copyBuffer, 0,
                    error.type().asString(), error.param().asString(), error.message().asString());

                server.doNetEvents(copyBuffer, responseEncoder.frames(), length, 0, 0L, traceId, authorization);

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

            server.doNetChallenge(traceId, authorization, extension);
        }

        private void doAppFlush(
            long traceId,
            long authorization)
        {
            if (!flushed && !LlmState.initialClosed(state))
            {
                flushed = true;

                final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(0L)
                    .reserved(0)
                    .build();

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

        private void doAppBlockData(
            long traceId,
            long authorization,
            int flags,
            int kind,
            String block,
            LlmBlockOwner owner,
            LlmSectionType type,
            int message,
            String extension,
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

            if (block != null || type != null || attributes != null && attributes.present())
            {
                final LlmDataExFW.Builder dataExBuilder = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId);

                if (kind == BLOCK_STREAM)
                {
                    dataExBuilder.block(d -> d.block(block));
                }
                else if (kind == BLOCK_BEFORE)
                {
                    dataExBuilder.before(d ->
                    {
                        d.type(t -> t.set(type)).message(message);

                        if (attributes != null)
                        {
                            attributes.writeTo(d);
                        }

                        d.owner(o -> o.set(owner)).block(block);
                    });
                }
                else if (kind == BLOCK_AFTER)
                {
                    dataExBuilder.after(d -> d.owner(o -> o.set(owner)).block(block));
                }
                else
                {
                    dataExBuilder.section(x ->
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
                            x.extensionLength(bytes.length).extension(e -> e.set(bytes));
                        }
                    });
                }

                final LlmDataExFW dataEx = dataExBuilder.build();
                builder.extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof());
            }

            final DataFW data = builder.build();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            initialSeq += length;
        }
    }

    private static final class SlotChunk
    {
        private final int length;
        private final int rawLength;
        private final int flags;
        private final String type;
        private final long budgetId;
        private final long traceId;
        private final long authorization;
        private int sent;

        private SlotChunk(
            int length,
            int rawLength,
            int flags,
            String type,
            long budgetId,
            long traceId,
            long authorization)
        {
            this.length = length;
            this.rawLength = rawLength;
            this.flags = flags;
            this.type = type;
            this.budgetId = budgetId;
            this.traceId = traceId;
            this.authorization = authorization;
        }
    }

    private final class LlmAnthropicRejectHandler
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

        private LlmAnthropicRejectHandler(
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
