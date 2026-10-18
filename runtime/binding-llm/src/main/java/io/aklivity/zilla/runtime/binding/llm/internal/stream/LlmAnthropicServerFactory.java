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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonException;

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
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAttributeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmChallengeExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDialectDataExFW;
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

public final class LlmAnthropicServerFactory implements LlmDialectHandler
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final int QUEUE_ENTRY_OVERHEAD = 5 * Integer.BYTES + 3 * Long.BYTES + Byte.BYTES;
    private static final int EVENT_ATTRIBUTES_CAPACITY = 2048;
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_STATUS = ":status";
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_API_KEY = "x-api-key";
    private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";
    private static final String HEADER_ANTHROPIC_BETA = "anthropic-beta";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String METHOD_POST = "POST";
    private static final String PATH_MESSAGES = "/v1/messages";
    private static final String STATUS_OK = "200";
    private static final String STATUS_UNAUTHORIZED = "401";
    private static final int NO_MAX_OUTPUT_TOKENS = -1;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Pattern CONTENT_TYPE = Pattern.compile("\\s*([^;\\s]+)\\s*(?:;.*)?");
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String UNAUTHORIZED_BODY = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"," +
        "\"message\":\"invalid x-api-key\"}}";

    private static final String MESSAGE_PARSE_ERROR = "There was an error parsing the body";
    private static final String MESSAGE_MODEL_MISSING = "model: Field required";
    private static final String MESSAGE_MODEL_ORDER =
        "model: must precede messages, system and tools in a request that does not fit the decode buffer";
    private static final String MESSAGE_ROLE_MISSING = "messages.%d.role: Field required";
    private static final String MESSAGE_ROLE_ORDER = "role must precede content in each message";
    private static final String MESSAGE_TOOL_USE_ID_ORDER = "tool_use_id must precede content in a tool_result block";
    private static final String MESSAGE_PART_TYPE_ORDER = "type must be the first member of each content block";
    private static final String MESSAGE_ATTRIBUTE_TOO_LARGE = "a content block attribute value is too large";
    private static final String MESSAGE_BLOCK_INVALID = "Invalid content block";
    private static final int BLOCK_BUFFER_MAX = 4096;
    private static final String BLOCK_TOOL_USE = "tool_use";

    private static final int RESPONSE_ENCODE_PADDING = 128;
    private static final int RESPONSE_ESCAPE_EXPANSION = 6;
    private static final int RESPONSE_ENCODE_SLACK = 2048;
    private static final int REQUEST_CANONICAL_GROWTH = 4;
    private static final int REQUEST_ENCODE_SLACK = 512;
    private static final int REQUEST_RECORD_HEADER = 28;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final DirectBufferEx EMPTY_BUFFER = new UnsafeBufferEx(new byte[0]);

    private static final int MIN_WINDOW = 64;
    private static final int HOLD_MAX = 1024;

    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private static final String MEMBER_TEXT = "text";
    private static final byte[] TEXT_PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte[] TEXT_SUFFIX = "\"}".getBytes(UTF_8);
    private static final int TEXT_ESCAPE_EXPANSION = 6;
    private static final int MIN_TEXT_INPUT = "\"system\":\"\"".length();

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
    private final LlmAnthropicServerDecoder decodeBlockType = this::decodeBlockType;
    private final LlmAnthropicServerDecoder decodeBlockTypeValue = this::decodeBlockTypeValue;
    private final LlmAnthropicServerDecoder decodePart = this::decodePart;
    private final LlmAnthropicServerDecoder decodeAttributes = this::decodeAttributes;
    private final LlmAnthropicServerDecoder decodeToolIsError = this::decodeToolIsError;
    private final LlmAnthropicServerDecoder decodeUnknownBlock = this::decodeUnknownBlock;
    private final LlmAnthropicServerDecoder decodeThinkingBlock = this::decodeThinkingBlock;
    private final LlmAnthropicServerDecoder decodeToolUseBlock = this::decodeToolUseBlock;
    private final LlmAnthropicServerDecoder decodeImageBlock = this::decodeImageBlock;
    private final LlmAnthropicServerDecoder decodeDocumentBlock = this::decodeDocumentBlock;
    private final LlmAnthropicServerDecoder decodeServerToolBlock = this::decodeServerToolBlock;
    private final LlmAnthropicServerDecoder decodeCitationsBlock = this::decodeCitationsBlock;
    private final LlmAnthropicServerDecoder decodeTool = this::decodeTool;
    private final LlmAnthropicServerDecoder decodeText = this::decodeText;
    private final LlmAnthropicServerDecoder decodeSkip = this::decodeSkip;
    private final LlmAnthropicServerDecoder decodeModelOverride = this::decodeModelOverride;
    private final LlmAnthropicServerDecoder decodeStreamValue = this::decodeStreamValue;
    private final LlmAnthropicServerDecoder decodeMaxOutputTokensValue = this::decodeMaxOutputTokensValue;
    private final LlmAnthropicServerDecoder decodeEnd = this::decodeEnd;
    private final LlmAnthropicServerDecoder decodeIgnore = this::decodeIgnore;

    private byte[] textBytes;
    private UnsafeBufferEx textBuffer;
    private byte[] pieceBytes;
    private UnsafeBufferEx pieceBuffer;

    public LlmAnthropicServerFactory(
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
        this.pieceBytes = new byte[1024];
        this.pieceBuffer = new UnsafeBufferEx(pieceBytes);
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

        MessageConsumer newStream = null;

        if (binding != null && httpBeginEx != null)
        {
            final LlmRequestHeaders headers = LlmRequestHeaders.of(httpBeginEx);
            final String contentType = header(headers, HEADER_CONTENT_TYPE);
            final String version = header(headers, HEADER_ANTHROPIC_VERSION);
            final String beta = header(headers, HEADER_ANTHROPIC_BETA);

            if (!CONTENT_TYPE_JSON.equals(mediaType(contentType)))
            {
                newStream = new LlmAnthropicRejectHandler(network, originId, routedId, initialId,
                    Integer.toString(STATUS_UNSUPPORTED_MEDIA_TYPE),
                    errorBody(STATUS_UNSUPPORTED_MEDIA_TYPE, null, null))::onNetMessage;
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
                        errorBody(STATUS_BAD_REQUEST, null, version == null
                            ? "anthropic-version: header is required"
                            : "anthropic-version: unsupported version " + version))::onNetMessage;
                }
                else
                {
                    final LlmRouteConfig route = binding.resolve(authResult.authorization());

                    if (route != null)
                    {
                        newStream = new LlmAnthropicServer(
                                network,
                                originId,
                                routedId,
                                initialId,
                                route.id,
                                authResult.authorization(),
                                contentType,
                                beta,
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
        String message)
    {
        return Json.createObjectBuilder()
            .add("type", "error")
            .add("error", Json.createObjectBuilder()
                .add("type", type != null ? type : errorType(status))
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
                else if (matches(key, "stream"))
                {
                    server.decoder = decodeStreamValue;
                }
                else if (matches(key, "max_tokens"))
                {
                    server.decoder = decodeMaxOutputTokensValue;
                }
                else if (server.modelOverride != null && matches(key, "model"))
                {
                    server.decoder = decodeModelOverride;
                }
                else
                {
                    server.skip(decodeRoot);
                    server.decoder = decodeSkip;
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
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.context = CONTEXT_SYSTEM;
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server), decodeRoot);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
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

    private int decodeModelOverride(
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

        final int eventFrom = server.position();

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
                server.onDecodeModelOverride(eventFrom);
                server.decoder = decodeRoot;
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
        final int eventFrom = server.position();

        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                server.text(textBlockType(server), textToolCallId(server), textIsError(server), textMessage(server),
                    decodeMessage);
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
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
                server.probe(LlmSectionType.TOOL_DEFINITION, -1, null, decodeTool, decodeTools);
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
                server.flushHeld();
                server.hold();
                server.decoder = decodeBlockType;
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

    private int decodeBlockType(
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
                    server.decoder = decodeToolResult;
                }
                else
                {
                    final LlmSectionType blockType = partBlockType(server, type);
                    final String extension = blockType == LlmSectionType.SERVER_TOOL_RESULT ? type : null;

                    final LlmAnthropicServerDecoder decodeTypedBlock = switch (blockType)
                    {
                    case ASSISTANT_REASONING -> decodeThinkingBlock;
                    case TOOL_CALL, SERVER_TOOL_CALL -> decodeToolUseBlock;
                    case USER_IMAGE -> decodeImageBlock;
                    case USER_DOCUMENT -> decodeDocumentBlock;
                    case SERVER_TOOL_RESULT -> decodeServerToolBlock;
                    default -> decodeUnknownBlock;
                    };

                    if (LlmAnthropicAttributeProbe.carries(blockType))
                    {
                        server.probe(blockType, textMessage(server), extension, decodeTypedBlock, decodeParts);
                        server.decoder = decodeAttributes;
                    }
                    else
                    {
                        server.captureHeld(blockType, textMessage(server), extension, decodeParts, 1);
                        server.decoder = decodeTypedBlock;
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
                    server.skip(decodePart);
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
                server.capture(LlmSectionType.CITATIONS, textMessage(server), null, decodePart);
                server.decoder = decodeCitationsBlock;
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
                    server.skip(decodeToolResult);
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
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
        final int eventFrom = server.position();

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
                progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
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

    private int decodeThinkingBlock(
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

        while (server.decoder == decodeThinkingBlock && parser.hasNextEvent())
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

    private int decodeToolUseBlock(
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

        while (server.decoder == decodeToolUseBlock && parser.hasNextEvent())
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

    private int decodeImageBlock(
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

    private int decodeDocumentBlock(
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

    private int decodeServerToolBlock(
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

        while (server.decoder == decodeServerToolBlock && parser.hasNextEvent())
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

    private int decodeCitationsBlock(
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

        while (server.decoder == decodeCitationsBlock && parser.hasNextEvent())
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

    private int decodeUnknownBlock(
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

        while (server.decoder == decodeUnknownBlock && parser.hasNextEvent())
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

    private int decodeTool(
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

        int eventFrom = server.position();

        while (server.decoder == decodeText && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            progress = decodeTextValue(server, traceId, authorization, buffer, progress, limit, event, eventFrom);
            eventFrom = server.position();
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
        JsonEvent event,
        int eventFrom)
    {
        final JsonParserEx parser = server.parser;

        server.decoder = decodeText;

        if (event == JsonEvent.VALUE_STRING)
        {
            if (!server.textOpen)
            {
                server.onDecodeBlock(server.textType, server.textMessage, null, eventFrom);
                server.blockAttributes.toolCallId(server.textToolCallId);
                server.blockAttributes.isError(server.textIsError);
                server.textOpen = true;
            }

            final boolean fin = !parser.deferredBytes();
            final CharSequence chars = parser.getStringView();
            final int taken = chars.length();
            final int length = encode(server, chars, fin);
            final int piece = encodePiece(server, length, fin);

            if (fin && server.context == CONTEXT_TOOL_RESULT && server.toolIsError == null)
            {
                server.hold(traceId, authorization, pieceBuffer, piece);
                server.hold();
                server.holdFrom = server.position();
            }
            else
            {
                server.onDecodeData(traceId, authorization, pieceBuffer, 0, piece, fin);

                if (fin)
                {
                    server.dialectResume(server.position());
                }
            }

            if (fin)
            {
                server.textOpen = false;
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
            if (event != JsonEvent.VALUE_TRUE && event != JsonEvent.VALUE_FALSE && event != JsonEvent.VALUE_NULL)
            {
                server.onDecodeParseError(traceId);
                break decode;
            }

            server.streaming = event == JsonEvent.VALUE_TRUE;
            server.decoder = decodeRoot;
        }

        return server.position();
    }

    private int decodeMaxOutputTokensValue(
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
            case "server_tool_use" -> LlmSectionType.SERVER_TOOL_CALL;
            default -> type.endsWith(SUFFIX_TOOL_RESULT) ? LlmSectionType.SERVER_TOOL_RESULT : LlmSectionType.UNKNOWN;
            };
        }

        return blockType;
    }

    private int encodePiece(
        LlmAnthropicServer server,
        int length,
        boolean fin)
    {
        final int capacity = TEXT_PREFIX.length + length * TEXT_ESCAPE_EXPANSION + TEXT_SUFFIX.length;

        if (pieceBytes.length < capacity)
        {
            pieceBytes = new byte[capacity];
            pieceBuffer = new UnsafeBufferEx(pieceBytes);
        }

        final JsonGeneratorEx generator = server.generator;

        generator.wrap(pieceBuffer, 0, pieceBytes.length);

        if (Flags.hasInit(server.blockFlags))
        {
            generator.reset();
            generator.writeStartObject().writeKey(MEMBER_TEXT).writeStartEscaped();
        }

        generator.writeSegment(textBuffer, 0, length);

        if (fin)
        {
            generator.writeEndEscaped().writeEnd();
        }

        return generator.length();
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
        private final JsonGeneratorEx generator;
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long exitId;
        private final long initialAuthorization;
        private final String contentType;
        private final String beta;
        private final Runnable deauthorize;
        private final JsonParserEx parser;
        private final LlmQueueEntryFW queueEntryRO = new LlmQueueEntryFW();
        private final LlmQueueEntryFW.Builder queueEntryRW = new LlmQueueEntryFW.Builder();
        private final Array32FW<HttpHeaderFW> eventHeadersRO = new Array32FW<>(new HttpHeaderFW());

        private LlmAnthropicStream stream;
        private LlmAnthropicServerDecoder decoder;

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
        private String toolUseId;
        private String toolIsError;
        private int context;
        private LlmAnthropicServerDecoder partsThen;

        private LlmAnthropicServerDecoder skipThen;
        private int skipDepth;

        private boolean streaming;
        private int maxOutputTokens = NO_MAX_OUTPUT_TOKENS;

        private LlmAnthropicServerDecoder textThen;
        private LlmSectionType textType;
        private String textToolCallId;
        private String textIsError;
        private int textMessage;
        private boolean textOpen;
        private char textHighSurrogate;

        private LlmAnthropicServerDecoder probeCapture;
        private LlmAnthropicServerDecoder probeThen;
        private int probeMessage;
        private String probeExtension;

        private LlmAnthropicServerDecoder captureThen;
        private boolean captureOpen;
        private int captureDepth;
        private int captureFrom;

        private boolean dialectActive;
        private boolean dialectOpen;
        private int dialectFrom;
        private DirectBufferEx modelOverride;

        private boolean holding;
        private boolean lookahead;
        private boolean toolContentSeen;
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

        private long decodeTraceId;
        private long decodeAuthorization;
        private LlmSectionType blockSection;
        private int blockMessage;
        private String blockExtension;
        private int blockFlags;

        private LlmSectionPipeline toolPipeline;
        private LlmSectionPipeline toolUsePipeline;
        private LlmSectionPipeline reasoningPipeline;
        private LlmSectionPipeline citationsPipeline;
        private LlmSectionPipeline imagePipeline;
        private LlmSectionPipeline documentPipeline;
        private LlmSectionPipeline blockPipeline;
        private final LlmSectionPipeline.Output blockOutput = (buffer, offset, length, last) ->
            onDecodeSectionData(decodeTraceId, decodeAuthorization, buffer, offset, length, last);

        private LlmAnthropicServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long exitId,
            long authorization,
            String contentType,
            String beta,
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
            this.beta = beta;
            this.deauthorize = deauthorize;
            this.parser = JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
            this.generator = JsonEx.createGenerator();
            this.probe = new LlmAnthropicAttributeProbe();
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
            decodeTraceId = traceId;
            decodeAuthorization = authorization;
            captureFrom = offset;
            holdFrom = offset;
            dialectFrom = offset;
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
                flushDialect(holdFrom, false, false);
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
            else
            {
                flushDialect(progress, false, false);
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
            if (LlmState.initialEndDeferred(state) && decodeSlot == NO_SLOT && decoder != decodeIgnore &&
                (stream == null || stream.encodeSlot == NO_SLOT))
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
            final int framing = TEXT_PREFIX.length + TEXT_SUFFIX.length;
            final int reserve = decoder == decodeText && textOpen ? TEXT_SUFFIX.length : framing;
            final int growth = 1 + (framing + MIN_TEXT_INPUT - 1) / MIN_TEXT_INPUT;

            return stream != null
                ? (int) Math.min(limit - offset, Math.max(available() - reserved - reserve, 0) / growth)
                : limit - offset;
        }

        private int available()
        {
            final int credit = stream != null ? (int) Math.max(stream.initialAvailable(), 0L) : 0;
            final int available = stream != null ? Math.min(credit, stream.encodeRoom()) : 0;
            final int room = modelOverride != null ? Math.min(modelOverride.capacity(), available >> 1) : 0;

            return available - room;
        }

        private void onModelOverride(
            String model)
        {
            final StringBuilder escaped = new StringBuilder(model.length() + 2).append('"');
            for (int i = 0; i < model.length(); i++)
            {
                final char ch = model.charAt(i);
                if (ch == '"' || ch == '\\')
                {
                    escaped.append('\\').append(ch);
                }
                else if (ch < ' ')
                {
                    escaped.append(String.format("\\u%04x", (int) ch));
                }
                else
                {
                    escaped.append(ch);
                }
            }

            this.modelOverride = new UnsafeBufferEx(escaped.append('"').toString().getBytes(UTF_8));
        }

        private void onDecodeModelOverride(
            int eventFrom)
        {
            flushDialect(eventFrom, false, false);

            stream.doAppData(decodeTraceId, decodeAuthorization, dialectOpen ? Flags.NONE : Flags.INIT,
                true, false, null, -1, null, null, modelOverride, 0, modelOverride.capacity());

            this.dialectOpen = true;
            this.modelOverride = null;
            dialectResume(position());
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
            LlmSectionType type,
            int blockMessage,
            String extension,
            LlmAnthropicServerDecoder then)
        {
            final int from = position() - 1;

            onDecodeBlock(type, blockMessage, extension, from);

            captureThen = then;
            captureOpen = true;
            captureDepth = 1;
            captureFrom = from;
        }

        private void probe(
            LlmSectionType type,
            int message,
            String extension,
            LlmAnthropicServerDecoder capturing,
            LlmAnthropicServerDecoder then)
        {
            probeMessage = message;
            probeExtension = extension;
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

                release();
                dialectResume(holdFrom);
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
            onDecodeBlock(type, blockMessage, extension, holdFrom);

            captureThen = then;
            captureOpen = true;
            captureDepth = depth;
            captureFrom = holdFrom;
            release();
        }

        private void captureProbed()
        {
            captureHeld(probe.type(), probeMessage, probeExtension, probeThen, probe.depth());
            probing = true;
            decoder = probeCapture;
        }

        private void endCapture(
            long traceId,
            long authorization)
        {
            flushCapture(traceId, authorization, true);
            blockPipeline = null;
            captureOpen = false;
            probing = false;
            dialectResume(position());
            decoder = captureThen;
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

        private void dialectResume(
            int from)
        {
            dialectFrom = from;
            dialectActive = true;
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

                    stream.doAppData(decodeTraceId, decodeAuthorization, fin ? Flags.fin(flags) : flags,
                        true, complete, null, -1, null, null, decodedInput, dialectFrom, length);

                    dialectOpen = !fin;
                }

                dialectFrom = to;
            }
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
            dialectActive = true;
            dialectOpen = false;
        }

        private void onDecodeBlock(
            LlmSectionType type,
            int message,
            String extension,
            int sectionFrom)
        {
            flushHeld();
            flushDialect(sectionFrom, true, false);
            dialectActive = false;

            blockSection = type;
            blockMessage = message;
            blockExtension = extension;
            blockFlags = Flags.INIT;

            blockAttributes.clear();

            blockPipeline = blockPipelineFor(type);

            if (blockPipeline != null)
            {
                blockPipeline.begin(LlmAnthropicAttributeProbe.carries(type) ? probe.attributes() : blockAttributes);
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
                if (blockPipeline == null)
                {
                    onDecodeSectionData(traceId, authorization, buffer, offset, length, last);
                }
                else if (!blockPipeline.feed(buffer, offset, length, last, blockOutput))
                {
                    onDecodeInvalidRequest(traceId, MESSAGE_BLOCK_INVALID);
                }
            }
        }

        private LlmSectionPipeline blockPipelineFor(
            LlmSectionType type)
        {
            LlmSectionPipeline pipeline = null;

            switch (type)
            {
            case TOOL_DEFINITION:
                if (toolPipeline == null)
                {
                    toolPipeline = new LlmSectionPipeline(new LlmAnthropicToolDefinitionToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = toolPipeline;
                break;
            case TOOL_CALL, SERVER_TOOL_CALL:
                if (toolUsePipeline == null)
                {
                    toolUsePipeline = new LlmSectionPipeline(new LlmAnthropicToolCallToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = toolUsePipeline;
                break;
            case CITATIONS:
                if (citationsPipeline == null)
                {
                    citationsPipeline = new LlmSectionPipeline(new LlmAnthropicCitationsToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = citationsPipeline;
                break;
            case ASSISTANT_REASONING:
                if (reasoningPipeline == null)
                {
                    reasoningPipeline = new LlmSectionPipeline(new LlmAnthropicReasoningToCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = reasoningPipeline;
                break;
            case USER_IMAGE:
                if (imagePipeline == null)
                {
                    imagePipeline = new LlmSectionPipeline(LlmAnthropicMediaToCanonical.image(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = imagePipeline;
                break;
            case USER_DOCUMENT:
                if (documentPipeline == null)
                {
                    documentPipeline = new LlmSectionPipeline(LlmAnthropicMediaToCanonical.document(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }
                pipeline = documentPipeline;
                break;
            default:
                break;
            }

            return pipeline;
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

            stream.doAppData(traceId, authorization, flags, false, false, init ? blockSection : null, blockMessage,
                init ? blockExtension : null, attributes, buffer, offset, length);

            blockFlags = Flags.NONE;
        }

        private void onDecodeRootEnd(
            long traceId)
        {
            flushHeld();
            flushDialect(position(), true, true);
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

            doNetError(traceId, authorization, status, error.type().asString(), error.message().asString());
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String message)
        {
            doNetError(traceId, authorization, status, null, message);
        }

        private void doNetError(
            long traceId,
            long authorization,
            int status,
            String type,
            String message)
        {
            final DirectBufferEx body = asBuffer(errorBody(status, type, message));

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
            if (stream != null)
            {
                stream.cleanupEncodeSlot();
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

    private final class LlmAnthropicStream
    {
        private final LlmAnthropicServer server;
        private final LlmSectionPipeline.Output blockOutput;
        private final LlmSectionPipeline.Output replayOutput;
        private final LlmSectionPipeline.Output deltaOutput;
        private final LlmTextPipeline sectionText;
        private final LlmTextPipeline replayText;

        private LlmTextPipeline textPipeline;
        private LlmSectionPipeline blockPipeline;
        private LlmSectionPipeline reasoningBlock;
        private LlmSectionPipeline reasoningDelta;
        private LlmAnthropicReasoningToDelta reasoningDeltaTransform;
        private LlmSectionPipeline replayPipeline;
        private LlmSectionPipeline replayReasoning;
        private LlmSectionPipeline replayValue;
        private LlmSectionPipeline toolBlock;
        private LlmSectionPipeline toolValue;
        private LlmSectionPipeline replayToolValue;
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

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotProgress;
        private int encodeHeadSent;

        private boolean replyBegun;
        private boolean replyDialect;
        private boolean crossDialect;
        private int replyKind;
        private LlmSectionType replyType;
        private boolean flushed;

        private LlmAnthropicStream(
            LlmAnthropicServer server)
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
            this.blockOutput = (buffer, offset, length, last) ->
                generator.writeSegment(buffer, offset, length, Completion.INCOMPLETE);
            this.replayOutput = (buffer, offset, length, last) -> putReplay(buffer, offset, length);
            this.deltaOutput = (buffer, offset, length, last) -> onToolDelta(buffer, offset, length);
            this.sectionText = new LlmTextPipeline(this::onSectionText);
            this.replayText = new LlmTextPipeline(this::onReplayText);
        }

        private long initialAvailable()
        {
            return initialMax - (initialSeq - initialAck);
        }

        private int encodeRoom()
        {
            final int room = encodePool.slotCapacity() - (encodeSlotOffset - encodeSlotProgress) - REQUEST_ENCODE_SLACK;

            return Math.max(room, 0) / REQUEST_CANONICAL_GROWTH;
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();
            final LlmResetExFW resetEx = extension.get(llmResetExRO::tryWrap);

            state = LlmState.closeInitial(state);
            cleanupEncodeSlot();
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

            drainEncodeSlot(traceId);

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

            if (server.beta != null)
            {
                final byte[] beta = server.beta.getBytes(UTF_8);
                builder.extension(e -> e.set(beta));
            }

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

            responseId = llmBeginEx != null ? llmBeginEx.id().asString() : null;
            responseModel = llmBeginEx != null ? llmBeginEx.model().asString() : null;
            replyBegun = true;

            server.doNetBegin(traceId, authorization, affinity, STATUS_OK,
                server.streaming ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON);

            if (!replyDialect)
            {
                beginEncode();
                doEncodeBegin();
                doEncodeFlush(0, 0L, traceId, authorization);
            }
            else if (server.streaming)
            {
                envelope = ENVELOPE_CONTENT;
            }

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

                replyKind = llmDataEx != null ? llmDataEx.kind() : LlmDataExFW.KIND_SECTION;
                replyComplete = replyKind == LlmDataExFW.KIND_DIALECT && llmDataEx.dialect().complete() != 0;

                if (replyKind == LlmDataExFW.KIND_SECTION)
                {
                    final LlmSectionDataExFW section = llmDataEx != null ? llmDataEx.section() : null;
                    final LlmSectionType type = section != null ? section.type().get() : LlmSectionType.NONE;

                    replyAttributes.readFrom(section);
                    replyType = type;

                    if (replaying())
                    {
                        doReplaySection(type);
                    }
                    else
                    {
                        doEncodeSection(
                            type,
                            section != null ? section.message() : -1,
                            replyAttributes.toolCallId(),
                            replyAttributes.toolName());
                    }
                }
                else if (replyDialect)
                {
                    replyType = LlmSectionType.NONE;
                    doReplayDialect(llmDataEx.dialect());
                }
            }
            else if (Flags.hasFin(flags) && replyKind == LlmDataExFW.KIND_DIALECT)
            {
                final LlmDataExFW llmDataEx = data.extension().get(llmDataExRO::tryWrap);

                replyComplete = llmDataEx != null && llmDataEx.kind() == LlmDataExFW.KIND_DIALECT &&
                    llmDataEx.dialect().complete() != 0;
            }

            if (replaying())
            {
                doReplayData(traceId, payload.buffer(), payload.offset(), payload.sizeof(), Flags.hasFin(flags));
            }
            else if (replyKind == LlmDataExFW.KIND_SECTION)
            {
                if (!textSection && (kind == KIND_REASONING || kind == KIND_TOOL_CALL && !server.streaming))
                {
                    if (!blockPipeline.feed(payload.buffer(), payload.offset(), payload.sizeof(), Flags.hasFin(flags),
                        blockOutput))
                    {
                        server.cleanup(traceId);
                    }
                }
                else
                {
                    doEncodeData(traceId, payload.buffer(), payload.offset(), payload.sizeof(), Flags.hasFin(flags));
                }

                if (Flags.hasFin(flags))
                {
                    doEncodeEnd();
                }
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

            if (replyDialect)
            {
                doReplayEnd();
            }
            else
            {
                doEncodeEnd();
                doEncodeFinish(
                    llmEndEx != null ? llmEndEx.finishReason().asString() : null,
                    usage != null ? usage.inputTokens() : -1,
                    usage != null ? usage.cacheWriteTokens() : -1,
                    usage != null ? usage.cacheReadTokens() : -1,
                    usage != null ? usage.outputTokens() : -1,
                    usage != null ? usage.reasoningTokens() : -1,
                    usage != null ? usage.totalTokens() : -1);
            }

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

            final LlmAbortExFW llmAbortEx = replyBegun && server.streaming
                ? extension.get(llmAbortExRO::tryWrap)
                : null;
            final LlmErrorFW error = llmAbortEx != null ? llmAbortEx.error() : null;

            if (error != null && (error.type().asString() != null || error.message().asString() != null))
            {
                beginEncode();
                doEncodeError(error.type().asString(), error.param().asString(), error.message().asString());
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

            if (llmChallengeEx != null && llmChallengeEx.typeId() == llmTypeId)
            {
                crossDialect = !NAME.equals(llmChallengeEx.dialect().asString());

                final String model = llmChallengeEx.model().asString();
                if (model != null)
                {
                    server.onModelOverride(model);
                }
            }
            else
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
            cleanupEncodeSlot();

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
            boolean dialect,
            boolean complete,
            LlmSectionType type,
            int message,
            String extension,
            LlmSectionAttributes attributes,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (!(dialect && crossDialect) && !LlmState.initialClosed(state))
            {
                int extLength = 0;

                if (dialect && (Flags.hasInit(flags) || complete))
                {
                    final LlmDataExFW dataEx = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId)
                        .dialect(d -> d.complete(complete ? 1 : 0))
                        .build();

                    extLength = dataEx.sizeof();
                }
                else if (type != null || attributes != null && attributes.present())
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

                    extLength = dataEx.sizeof();
                }

                if (enqueueData(traceId, authorization, flags, extLength, buffer, offset, length))
                {
                    drainEncodeSlot(traceId);
                }
                else
                {
                    server.doNetReset(traceId);
                    doAppAbort(traceId);
                    server.cleanup(traceId);
                }
            }
        }

        private boolean enqueueData(
            long traceId,
            long authorization,
            int flags,
            int extLength,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            final int required = REQUEST_RECORD_HEADER + extLength + length;

            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(initialId);
            }

            boolean encoded = encodeSlot != NO_SLOT;

            if (encoded)
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);

                if (encodeSlotOffset + required > encodePool.slotCapacity() && encodeSlotProgress > 0)
                {
                    final int remaining = encodeSlotOffset - encodeSlotProgress;

                    slot.putBytes(0, slot, encodeSlotProgress, remaining);
                    encodeSlotOffset = remaining;
                    encodeSlotProgress = 0;
                }

                encoded = encodeSlotOffset + required <= encodePool.slotCapacity();

                if (encoded)
                {
                    slot.putLong(encodeSlotOffset, traceId);
                    slot.putLong(encodeSlotOffset + 8, authorization);
                    slot.putInt(encodeSlotOffset + 16, flags);
                    slot.putInt(encodeSlotOffset + 20, extLength);
                    slot.putInt(encodeSlotOffset + 24, length);
                    slot.putBytes(encodeSlotOffset + REQUEST_RECORD_HEADER, extBuffer, 0, extLength);
                    slot.putBytes(encodeSlotOffset + REQUEST_RECORD_HEADER + extLength, buffer, offset, length);
                    encodeSlotOffset += required;
                }
            }

            return encoded;
        }

        private void drainEncodeSlot(
            long traceId)
        {
            while (encodeSlot != NO_SLOT && encodeSlotProgress < encodeSlotOffset && !LlmState.initialClosed(state))
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);
                final int record = encodeSlotProgress;
                final long recordTraceId = slot.getLong(record);
                final long recordAuthorization = slot.getLong(record + 8);
                final int flags = slot.getInt(record + 16);
                final int extLength = slot.getInt(record + 20);
                final int payloadLength = slot.getInt(record + 24);
                final int payloadOffset = record + REQUEST_RECORD_HEADER + extLength;

                final int remaining = payloadLength - encodeHeadSent;
                final int length = (int) Math.min(remaining, Math.max(initialAvailable(), 0L));

                if (length < remaining && length == 0)
                {
                    break;
                }

                final boolean first = encodeHeadSent == 0;
                final boolean last = length == remaining;
                final int sentFlags = (first ? flags & Flags.INIT : 0) | (last ? flags & Flags.FIN : 0);

                final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(recordTraceId)
                    .authorization(recordAuthorization)
                    .flags(sentFlags)
                    .budgetId(initialBud)
                    .reserved(length)
                    .payload(slot, payloadOffset + encodeHeadSent, length);

                if (first && extLength > 0)
                {
                    builder.extension(slot, record + REQUEST_RECORD_HEADER, extLength);
                }

                final DataFW data = builder.build();

                app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

                initialSeq += length;
                encodeHeadSent += length;

                if (last)
                {
                    encodeSlotProgress = payloadOffset + payloadLength;
                    encodeHeadSent = 0;
                }
            }

            if (encodeSlotProgress == encodeSlotOffset || LlmState.initialClosed(state))
            {
                cleanupEncodeSlot();
                server.flushNetEnd(traceId);
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
            }

            encodeSlotOffset = 0;
            encodeSlotProgress = 0;
            encodeHeadSent = 0;
        }

        private void beginEncode()
        {
            generator.wrap(copyBuffer, 0, copyBuffer.capacity());
            replayPosition = 0;
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
        private static final int KIND_REASONING = 2;
        private static final int KIND_TOOL_CALL = 3;
        private static final int KIND_SKIPPED = 4;

        private static final int ENVELOPE_PRELUDE = 0;
        private static final int ENVELOPE_CONTENT = 1;
        private static final int ENVELOPE_TAIL = 2;

        private static final int NONE = -1;
        private static final int MESSAGE_FIRST = 0;

        private static final String ATTRIBUTE_TYPE = "type";
        private static final String EVENT_MESSAGE_START = "message_start";
        private static final String EVENT_BLOCK_START = "content_block_start";
        private static final String EVENT_BLOCK_DELTA = "content_block_delta";
        private static final String EVENT_BLOCK_STOP = "content_block_stop";
        private static final String EVENT_MESSAGE_DELTA = "message_delta";
        private static final String EVENT_MESSAGE_STOP = "message_stop";
        private static final String EVENT_ERROR = "error";
        private static final Map<String, DirectBufferEx> EVENT_ATTRIBUTES = Map.of(
            EVENT_MESSAGE_START, eventType(EVENT_MESSAGE_START),
            EVENT_BLOCK_START, eventType(EVENT_BLOCK_START),
            EVENT_BLOCK_DELTA, eventType(EVENT_BLOCK_DELTA),
            EVENT_BLOCK_STOP, eventType(EVENT_BLOCK_STOP),
            EVENT_MESSAGE_DELTA, eventType(EVENT_MESSAGE_DELTA),
            EVENT_MESSAGE_STOP, eventType(EVENT_MESSAGE_STOP),
            EVENT_ERROR, eventType(EVENT_ERROR));

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

        private int kind;
        private boolean textSection;
        private boolean blockOpen;
        private int blocks;
        private int toolCalls;
        private int envelope;

        private int replayPosition;
        private final Consumer<LlmAttributeFW> replayAttribute = this::onReplayAttribute;
        private boolean replyComplete;

        private void doEncodeBegin()
        {
            if (server.streaming)
            {
                eventBegin(generator.length(), EVENT_MESSAGE_START);
                generator.writeStartObject()
                    .write("type", EVENT_MESSAGE_START)
                    .writeStartObject("message");
                messageIdentity();
                generator.writeStartArray("content").writeEnd();
            }
            else
            {
                generator.writeStartObject();
                messageIdentity();
            }
        }

        private void doEncodeSection(
            LlmSectionType type,
            int message,
            String toolCallId,
            String toolName)
        {
            kind = message == MESSAGE_FIRST && envelope != ENVELOPE_TAIL ? kindOf(type) : KIND_SKIPPED;
            textSection = kind == KIND_TEXT;
            blockOpen = false;

            if (textSection)
            {
                sectionText.begin();
                textPipeline = sectionText;
            }
            else
            {
                textPipeline = null;
            }

            if (kind != KIND_SKIPPED && !textSection)
            {
                openBlock(toolCallId, toolName);

                if (kind == KIND_REASONING && server.streaming)
                {
                    beginReasoningDelta();
                }
                else if (kind == KIND_TOOL_CALL && server.streaming)
                {
                    toolValue().begin(replyAttributes);
                }
            }
        }

        private void beginReasoningDelta()
        {
            if (reasoningDelta == null)
            {
                reasoningDeltaTransform = new LlmAnthropicReasoningToDelta();
                reasoningDelta = new LlmSectionPipeline(reasoningDeltaTransform, new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            eventBegin(generator.length(), EVENT_BLOCK_DELTA);
            reasoningDeltaTransform.index(blocks - 1);
            blockPipeline = reasoningDelta;
            blockPipeline.begin(replyAttributes);
        }

        private void onSectionText(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (server.streaming)
            {
                dataChunk(buffer, offset, length);
            }
            else
            {
                generator.writeSegment(buffer, offset, length);
            }
        }

        private void onReplayText(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            generator.wrap(copyBuffer, replayPosition, copyBuffer.capacity());
            generator.writeSegment(buffer, offset, length);
            replayPosition += generator.length();
        }

        private void doEncodeData(
            long traceId,
            DirectBufferEx payload,
            int payloadOffset,
            int payloadLength,
            boolean fin)
        {
            if (textSection && !blockOpen)
            {
                openBlock(null, null);
            }

            if (textPipeline != null)
            {
                if (!textPipeline.feed(payload, payloadOffset, payloadLength, fin))
                {
                    server.cleanup(traceId);
                }
            }
            else if (kind == KIND_TOOL_CALL && server.streaming)
            {
                if (!toolValue.feed(payload, payloadOffset, payloadLength, fin, deltaOutput))
                {
                    server.cleanup(traceId);
                }
            }
            else if (payloadLength > 0 && kind != KIND_NONE && kind != KIND_SKIPPED)
            {
                if (server.streaming)
                {
                    dataChunk(payload, payloadOffset, payloadLength);
                }
                else
                {
                    generator.writeSegment(payload, payloadOffset, payloadLength);
                }
            }
        }

        private void onToolDelta(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                dataChunk(buffer, offset, length);
            }
        }

        private LlmSectionPipeline toolBlock()
        {
            if (toolBlock == null)
            {
                toolBlock = new LlmSectionPipeline(new LlmAnthropicToolCallFromCanonical(BLOCK_TOOL_USE),
                    new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return toolBlock;
        }

        private LlmSectionPipeline toolValue()
        {
            if (toolValue == null)
            {
                toolValue = new LlmSectionPipeline(new LlmToolCallToValue(), new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return toolValue;
        }

        private LlmSectionPipeline replayToolValue()
        {
            if (replayToolValue == null)
            {
                replayToolValue = new LlmSectionPipeline(new LlmToolCallToValue(),
                    new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
            }

            return replayToolValue;
        }

        private void doEncodeEnd()
        {
            if (textSection && !blockOpen)
            {
                kind = KIND_TEXT;
                openBlock(null, null);
            }

            if (blockOpen)
            {
                closeBlock();
            }

            kind = KIND_NONE;
            textSection = false;
        }

        private void openBlock(
            String toolCallId,
            String toolName)
        {
            if (envelope == ENVELOPE_PRELUDE)
            {
                closePrelude();
            }

            if (server.streaming)
            {
                sectionStream(toolCallId, toolName);
            }
            else
            {
                sectionBlock(toolCallId, toolName);
            }

            blockOpen = true;
        }

        private void closeBlock()
        {
            if (server.streaming)
            {
                if (kind == KIND_REASONING && !textSection)
                {
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                    eventEnd(generator.length());
                }

                eventBegin(generator.length(), EVENT_BLOCK_STOP);
                generator.writeStartObject()
                    .write("type", EVENT_BLOCK_STOP)
                    .writeKey("index").write(blocks - 1)
                    .writeEnd();
                eventEnd(generator.length());
            }
            else
            {
                sectionBlockEnd();
            }

            blockOpen = false;
        }

        private void doEncodeFinish(
            String finishReason,
            int inputTokens,
            int cacheWriteTokens,
            int cacheReadTokens,
            int outputTokens,
            int reasoningTokens,
            int totalTokens)
        {
            final String reason = stopReason(finishReason);

            if (envelope == ENVELOPE_PRELUDE)
            {
                closePrelude();
            }

            if (server.streaming)
            {
                if (envelope == ENVELOPE_CONTENT)
                {
                    openTail();
                }

                generator.write("stop_reason", reason);
                generator.writeNull("stop_sequence");

                generator.writeEnd();
                putUsage(inputTokens, cacheWriteTokens, cacheReadTokens, outputTokens);
                generator.writeEnd();
                eventEnd(generator.length());
                eventBegin(generator.length(), EVENT_MESSAGE_STOP);
                generator.writeStartObject().write("type", EVENT_MESSAGE_STOP).writeEnd();
                eventEnd(generator.length());
            }
            else
            {
                if (envelope == ENVELOPE_CONTENT)
                {
                    generator.writeEnd();
                }

                generator.write("stop_reason", reason);
                generator.writeNull("stop_sequence");
                putUsage(inputTokens, cacheWriteTokens, cacheReadTokens, outputTokens);
                generator.writeEnd();
            }

            envelope = ENVELOPE_TAIL;
        }

        private void doEncodeError(
            String type,
            String param,
            String message)
        {
            if (eventOpen)
            {
                eventEnd(encodeLength());
            }

            if (server.streaming && envelope == ENVELOPE_PRELUDE && !replyDialect)
            {
                closePrelude();
            }
            else if (server.streaming && envelope == ENVELOPE_TAIL && !replyDialect)
            {
                generator.writeEnd().writeEnd();
                eventEnd(generator.length());
            }

            eventBegin(generator.length(), EVENT_ERROR);
            generator.writeStartObject()
                .write("type", EVENT_ERROR)
                .writeStartObject("error")
                .write("type", type != null ? type : "api_error")
                .write("message", message != null ? message : "")
                .writeEnd()
                .writeEnd();
            eventEnd(generator.length());
        }

        private void messageIdentity()
        {
            if (responseId != null)
            {
                generator.write("id", responseId);
            }

            generator.write("type", "message").write("role", "assistant");

            if (responseModel != null)
            {
                generator.write("model", responseModel);
            }
        }

        private void sectionStream(
            String toolCallId,
            String toolName)
        {
            eventBegin(generator.length(), EVENT_BLOCK_START);
            generator.writeStartObject()
                .write("type", EVENT_BLOCK_START)
                .writeKey("index").write(blocks++)
                .writeStartObject("content_block");

            switch (kind)
            {
            case KIND_TEXT:
                generator.write("type", "text").write("text", "");
                break;
            case KIND_REASONING:
                generator.write("type", "thinking").write("thinking", "").write("signature", "");
                break;
            default:
                toolCalls++;
                generator.write("type", "tool_use")
                    .write("id", toolCallId != null ? toolCallId : "")
                    .write("name", toolName != null ? toolName : "")
                    .writeStartObject("input").writeEnd();
                break;
            }

            generator.writeEnd().writeEnd();
            eventEnd(generator.length());
        }

        private void sectionBlock(
            String toolCallId,
            String toolName)
        {
            blocks++;

            switch (kind)
            {
            case KIND_TEXT:
                generator.writeStartObject().write("type", "text").writeKey("text");
                generator.writeStartEscaped();
                break;
            case KIND_REASONING:
                if (textSection)
                {
                    generator.writeStartObject().write("type", "thinking").writeKey("thinking");
                    generator.writeStartEscaped();
                }
                else
                {
                    if (reasoningBlock == null)
                    {
                        reasoningBlock = new LlmSectionPipeline(new LlmAnthropicReasoningFromCanonical(true),
                            new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                    }

                    blockPipeline = reasoningBlock;
                    blockPipeline.begin(replyAttributes);
                }
                break;
            default:
                toolCalls++;
                blockPipeline = toolBlock();
                blockPipeline.begin(replyAttributes);
                break;
            }
        }

        private void sectionBlockEnd()
        {
            switch (kind)
            {
            case KIND_TEXT:
                generator.writeEndEscaped().writeEnd();
                break;
            case KIND_REASONING:
                if (textSection)
                {
                    generator.writeEndEscaped().write("signature", "").writeEnd();
                }
                else
                {
                    generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                }
                break;
            default:
                generator.writeSegment(EMPTY_BUFFER, 0, 0, Completion.COMPLETE);
                break;
            }
        }

        private void dataChunkStart()
        {
            eventBegin(generator.length(), EVENT_BLOCK_DELTA);
            generator.writeStartObject()
                .write("type", EVENT_BLOCK_DELTA)
                .writeKey("index").write(blocks - 1)
                .writeStartObject("delta");

            switch (kind)
            {
            case KIND_TEXT:
                generator.write("type", "text_delta").writeKey("text");
                break;
            case KIND_REASONING:
                generator.write("type", "thinking_delta").writeKey("thinking");
                break;
            default:
                generator.write("type", "input_json_delta").writeKey("partial_json");
                break;
            }

            generator.writeStartEscaped();
        }

        private void dataChunkEnd()
        {
            generator.writeEndEscaped().writeEnd().writeEnd();
            eventEnd(generator.length());
        }

        private void putUsage(
            int inputTokens,
            int cacheWriteTokens,
            int cacheReadTokens,
            int outputTokens)
        {
            generator.writeStartObject("usage")
                .writeKey("input_tokens").write(inputTokens != NONE ? inputTokens : 0);

            if (cacheWriteTokens != NONE)
            {
                generator.writeKey("cache_creation_input_tokens").write(cacheWriteTokens);
            }

            if (cacheReadTokens != NONE)
            {
                generator.writeKey("cache_read_input_tokens").write(cacheReadTokens);
            }

            generator.writeKey("output_tokens").write(outputTokens != NONE ? outputTokens : 0)
                .writeEnd();
        }

        private String stopReason(
            String finishReason)
        {
            String reason = toolCalls > 0 ? "tool_use" : "end_turn";

            if (finishReason != null)
            {
                reason = switch (finishReason)
                {
                case "stop" -> "end_turn";
                case "length" -> "max_tokens";
                case "tool-call" -> "tool_use";
                case "content-filter" -> "refusal";
                default -> finishReason;
                };
            }

            return reason;
        }

        private static int kindOf(
            LlmSectionType type)
        {
            return switch (type)
            {
            case ASSISTANT_TEXT, ASSISTANT_REFUSAL -> KIND_TEXT;
            case ASSISTANT_REASONING -> KIND_REASONING;
            case TOOL_CALL -> KIND_TOOL_CALL;
            default -> KIND_SKIPPED;
            };
        }

        private static boolean regenerated(
            LlmSectionType type)
        {
            return type == LlmSectionType.TOOL_CALL ||
                type == LlmSectionType.ASSISTANT_TEXT ||
                type == LlmSectionType.ASSISTANT_REFUSAL ||
                type == LlmSectionType.ASSISTANT_REASONING;
        }

        private boolean replaying()
        {
            return replyDialect && !(server.streaming && regenerated(replyType));
        }

        private int encodeLength()
        {
            return replaying() ? replayPosition : generator.length();
        }

        private void doReplaySection(
            LlmSectionType type)
        {
            replayPipeline = switch (type)
            {
            case ASSISTANT_REASONING -> replayReasoningPipeline();
            case TOOL_CALL -> replayToolValue();
            default -> null;
            };

            if (type == LlmSectionType.ASSISTANT_TEXT || type == LlmSectionType.ASSISTANT_REFUSAL)
            {
                replayText.begin();
                textPipeline = replayText;
            }
            else
            {
                textPipeline = null;
            }

            if (replayPipeline != null)
            {
                replayPipeline.begin(replyAttributes);
            }

            if (server.streaming && !eventOpen)
            {
                eventBegin(replayPosition);
            }

            if (textPipeline != null)
            {
                generator.reset();
                generator.wrap(copyBuffer, replayPosition, copyBuffer.capacity());
                generator.writeStartEscaped();
                replayPosition += generator.length();
            }
        }

        private LlmSectionPipeline replayReasoningPipeline()
        {
            final LlmSectionPipeline pipeline;

            if (server.streaming)
            {
                if (replayValue == null)
                {
                    replayValue = new LlmSectionPipeline(new LlmAnthropicReasoningToValue(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }

                pipeline = replayValue;
            }
            else
            {
                if (replayReasoning == null)
                {
                    replayReasoning = new LlmSectionPipeline(new LlmAnthropicReasoningFromCanonical(),
                        new UnsafeBufferEx(new byte[BLOCK_BUFFER_MAX]));
                }

                pipeline = replayReasoning;
            }

            return pipeline;
        }

        private void putReplay(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            copyBuffer.putBytes(replayPosition, buffer, offset, length);
            replayPosition += length;
        }

        private void onReplayAttribute(
            LlmAttributeFW attribute)
        {
            if (ATTRIBUTE_TYPE.equals(attribute.name().asString()) && EVENT_BLOCK_START.equals(attribute.value().asString()))
            {
                blocks++;
            }
        }

        private void doReplayDialect(
            LlmDialectDataExFW dialect)
        {
            replayPipeline = null;
            textPipeline = null;

            if (server.streaming && !eventOpen)
            {
                final Array32FW<LlmAttributeFW> attributes = dialect.attributes();

                attributes.forEach(replayAttribute);

                if (attributes.fieldCount() > 0)
                {
                    eventBegin(replayPosition, attributes.buffer(), attributes.offset(), attributes.sizeof());
                }
                else
                {
                    eventBegin(replayPosition);
                }
            }
        }

        private void doReplayData(
            long traceId,
            DirectBufferEx payload,
            int offset,
            int length,
            boolean fin)
        {
            if (replayPipeline != null)
            {
                if (!replayPipeline.feed(payload, offset, length, fin, replayOutput))
                {
                    server.cleanup(traceId);
                }
            }
            else if (textPipeline != null)
            {
                if (!textPipeline.feed(payload, offset, length, fin))
                {
                    server.cleanup(traceId);
                }
            }
            else if (length > 0)
            {
                copyBuffer.putBytes(replayPosition, payload, offset, length);
                replayPosition += length;
            }

            if (fin && textPipeline != null)
            {
                generator.wrap(copyBuffer, replayPosition, copyBuffer.capacity());
                generator.writeEndEscaped();
                replayPosition += generator.length();
            }

            if (fin && replyComplete && replyKind == LlmDataExFW.KIND_DIALECT)
            {
                replyComplete = false;
                eventEnd(replayPosition);
            }
        }

        private void doReplayEnd()
        {
            eventEnd(replayPosition);
        }

        private void eventBegin(
            int position)
        {
            eventBegin(position, null, 0, 0);
        }

        private void eventBegin(
            int position,
            String type)
        {
            final DirectBufferEx attributes = EVENT_ATTRIBUTES.get(type);

            eventBegin(position, attributes, 0, attributes.capacity());
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

        private void dataChunk(
            DirectBufferEx payload,
            int offset,
            int length)
        {
            dataChunkStart();

            generator.writeSegment(payload, offset, length);
            dataChunkEnd();
        }

        private void closePrelude()
        {
            if (server.streaming)
            {
                generator.writeNull("stop_reason");
                generator.writeNull("stop_sequence");
                generator.writeStartObject("usage")
                    .writeKey("input_tokens").write(0)
                    .writeKey("output_tokens").write(0)
                    .writeEnd()
                    .writeEnd()
                    .writeEnd();
                eventEnd(generator.length());
            }
            else
            {
                generator.writeStartArray("content");
            }

            envelope = ENVELOPE_CONTENT;
        }

        private void openTail()
        {
            if (server.streaming)
            {
                eventBegin(generator.length(), EVENT_MESSAGE_DELTA);
                generator.writeStartObject()
                    .write("type", EVENT_MESSAGE_DELTA)
                    .writeStartObject("delta");
            }
            else
            {
                generator.writeEnd();
            }

            envelope = ENVELOPE_TAIL;
        }
    }

    private String mediaType(
        String contentType)
    {
        contentTypeMatcher.reset(contentType != null ? contentType : "");

        return contentTypeMatcher.matches() ? contentTypeMatcher.group(1) : null;
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
