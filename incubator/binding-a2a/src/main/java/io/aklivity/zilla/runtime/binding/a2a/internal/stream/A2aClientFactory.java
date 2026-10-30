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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_NOTIFY;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCard;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCardParser;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentInterface;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aCardConsumer;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aCardRoles;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aClientBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aClientRouteConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aDialect;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aDialects;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aOperation;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aRequestEncoder;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aRequestParams;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aResultInspector;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public final class A2aClientFactory implements A2aStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String A2A_TYPE_NAME = "a2a";

    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_SCHEME = ":scheme";
    private static final String HTTP_HEADER_AUTHORITY = ":authority";
    private static final String HTTP_HEADER_PATH = ":path";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_CONTENT_LENGTH = "content-length";
    private static final String HTTP_HEADER_ACCEPT = "accept";
    private static final String HTTP_HEADER_AUTHORIZATION = "authorization";
    private static final String HTTP_HEADER_WWW_AUTHENTICATE = "www-authenticate";
    private static final String HTTP_HEADER_A2A_VERSION = "a2a-version";
    private static final String HTTP_HEADER_A2A_EXTENSIONS = "a2a-extensions";
    private static final String HTTP_HEADER_NOTIFICATION_TOKEN = "x-a2a-notification-token";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_A2A_JSON = "application/a2a+json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String STATUS_NOT_FOUND = "404";
    private static final String STATUS_UNAUTHORIZED = "401";
    private static final String STATUS_FORBIDDEN = "403";
    private static final String STATUS_SUCCESSFUL_PREFIX = "2";
    private static final String CARD_PATH_WELL_KNOWN = "/.well-known/agent-card.json";
    private static final String CARD_PATH_WELL_KNOWN_LEGACY = "/.well-known/agent.json";
    private static final String PUSH_SCHEME_DEFAULT = "Bearer";
    private static final String BEARER_REALM = "a2a";
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String JSON_RPC_END = "}";
    private static final String VERSION_CANONICAL = "1.0";
    private static final String VERSION_LEGACY = "0.3";
    private static final String JSON_NULL = "null";
    private static final int ERROR_CODE_FORBIDDEN = 403;
    private static final int PEEK_NO = 0;
    private static final int PEEK_YES = 1;
    private static final int PEEK_MORE = -1;
    private static final byte[] PEEK_MESSAGE = "\"message\"".getBytes(StandardCharsets.US_ASCII);

    private static final int ERROR_CODE_NOT_FOUND = 404;
    private static final int ERROR_CODE_BAD_GATEWAY = 502;
    private static final int ERROR_CODE_PAYLOAD_TOO_LARGE = 413;
    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final int ERROR_CODE_UNSUPPORTED_OPERATION = -32004;
    private static final int ERROR_CODE_VERSION_NOT_SUPPORTED = -32009;
    private static final String ERROR_MESSAGE_AGENT_NOT_FOUND = "Agent not found";
    private static final String ERROR_MESSAGE_BAD_GATEWAY = "Bad Gateway";
    private static final String ERROR_MESSAGE_PAYLOAD_TOO_LARGE = "Payload Too Large";
    private static final String ERROR_MESSAGE_INVALID_PARAMS = "Invalid params";
    private static final String ERROR_MESSAGE_UNSUPPORTED_OPERATION = "This operation is not supported";
    private static final String ERROR_MESSAGE_VERSION_NOT_SUPPORTED = "Version not supported";

    private static final Pattern BEARER_CHALLENGE_PATTERN = Pattern.compile(
        "^\\s*Bearer\\b" +
        "(?=.*?\\brealm\\s*=\\s*\"(?<realm>[^\"]*)\")?" +
        "(?=.*?\\bscope\\s*=\\s*\"(?<scope>[^\"]*)\")?" +
        "(?=.*?\\berror\\s*=\\s*\"(?<error>invalid_request|invalid_token|insufficient_scope)\")?" +
        ".*$",
        Pattern.CASE_INSENSITIVE);

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpDataExFW httpDataExRO = new HttpDataExFW();
    private final A2aBeginExFW a2aBeginExRO = new A2aBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final A2aBeginExFW.Builder a2aBeginExRW = new A2aBeginExFW.Builder();
    private final A2aDataExFW.Builder a2aDataExRW = new A2aDataExFW.Builder();
    private final A2aResetExFW.Builder a2aResetExRW = new A2aResetExFW.Builder();

    private final Matcher bearerChallengeMatcher = BEARER_CHALLENGE_PATTERN.matcher("");

    private final A2aConfiguration config;
    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx codecBuffer;
    private final MutableDirectBufferEx translateBuffer;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final long affinity;
    private final int httpTypeId;
    private final int a2aTypeId;
    private final int decodeMax;
    private final int encodeMax;

    private final Long2ObjectHashMap<A2aClientBindingConfig> bindings = new Long2ObjectHashMap<>();
    private final Map<String, Deque<A2aDeliverStream>> deliveries = new HashMap<>();
    private final A2aDialects dialects = new A2aDialects(null);
    private final A2aRequestEncoder requestEncoder = new A2aRequestEncoder();
    private final A2aAgentCardParser cardParser = new A2aAgentCardParser();
    private final A2aResultInspector resultInspector = new A2aResultInspector();
    private final JsonParserEx errorParser = JsonEx.createParser(Map.of());

    public A2aClientFactory(
        A2aConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.codecBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.translateBuffer = new UnsafeBufferEx(new byte[decodePool.slotCapacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.affinity = context.affinity();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.a2aTypeId = context.supplyTypeId(A2A_TYPE_NAME);
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = decodePool.slotCapacity();
    }

    @FunctionalInterface
    private interface HttpResponseDecoder
    {
        int decode(
            HttpStream http,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private final HttpResponseDecoder decodeEvent = this::decodeEvent;
    private final HttpResponseDecoder decodeJsonRpc = this::decodeJsonRpc;
    private final HttpResponseDecoder decodeJsonRpcStart = this::decodeJsonRpcStart;
    private final HttpResponseDecoder decodeJsonRpcNext = this::decodeJsonRpcNext;
    private final HttpResponseDecoder decodeJsonRpcEnd = this::decodeJsonRpcEnd;
    private final HttpResponseDecoder decodeJsonRpcSkipValue = this::decodeJsonRpcSkipValue;
    private final HttpResponseDecoder decodeJsonRpcResultStart = this::decodeJsonRpcResultStart;
    private final HttpResponseDecoder decodeJsonRpcResultEnd = this::decodeJsonRpcResultEnd;
    private final HttpResponseDecoder decodeJsonRpcErrorStart = this::decodeJsonRpcErrorStart;
    private final HttpResponseDecoder decodeJsonRpcErrorEnd = this::decodeJsonRpcErrorEnd;
    private final HttpResponseDecoder decodeJsonRpcSkipObject = this::decodeJsonRpcSkipObject;
    private final HttpResponseDecoder decodeHold = this::decodeHold;
    private final HttpResponseDecoder decodeIgnore = this::decodeIgnore;

    @FunctionalInterface
    private interface ResultDecoder
    {
        int decode(
            HttpCallStream http,
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean endOfInput);
    }

    private final ResultDecoder decodeResultStart = this::decodeResultStart;
    private final ResultDecoder decodeResultStream = this::decodeResultStream;
    private final ResultDecoder decodeResultBuffer = this::decodeResultBuffer;

    private int decodeEvent(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (progress < limit)
        {
            http.decoder = decodeJsonRpc;
        }

        return progress;
    }

    private int decodeJsonRpc(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        http.decodableJson = JsonEx.createParser(Map.of());
        http.decodableJson.wrap(buffer, progress, limit);
        http.decoder = decodeJsonRpcStart;
        http.decodedParserProgress = offset - progress;

        return offset + (int) (http.decodableJson.getLocation().getStreamOffset() - http.decodedParserProgress);
    }

    private int decodeJsonRpcStart(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            http.decoder = decodeJsonRpcNext;
            progress = offset + (int) (parser.getLocation().getStreamOffset() - http.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcNext(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case KEY_NAME:
                switch (parser.getString())
                {
                case "result":
                    http.decoder = decodeJsonRpcResultStart;
                    break;
                case "error":
                    http.decoder = decodeJsonRpcErrorStart;
                    break;
                default:
                    http.decoder = decodeJsonRpcSkipValue;
                    break;
                }
                break;
            case END_OBJECT:
                http.decoder = decodeJsonRpcEnd;
                break;
            default:
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            progress = offset + (int) (parser.getLocation().getStreamOffset() - http.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcSkipValue(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_OBJECT || event == JsonParser.Event.START_ARRAY)
            {
                http.decodedSkipDepth = 1;
                http.decodedSkipThen = decodeJsonRpcNext;
                http.decoder = decodeJsonRpcSkipObject;
            }
            else if (!parser.deferredBytes())
            {
                http.decoder = decodeJsonRpcNext;
            }

            progress = offset + (int) (parser.getLocation().getStreamOffset() - http.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcResultStart(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                http.decodedResultProgress = (int) parser.getLocation().getStreamOffset() - 1;
                http.decodedSkipDepth = 1;
                http.decodedSkipThen = decodeJsonRpcResultEnd;
                http.decoder = decodeJsonRpcSkipObject;
                break;
            case VALUE_NULL:
                http.decodedResultProgress = (int) parser.getLocation().getStreamOffset() - "null".length();
                http.decoder = decodeJsonRpcResultEnd;
                break;
            default:
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            progress = offset + http.decodedResultProgress - http.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcResultEnd(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        final int decodedResultLimit = (int) parser.getLocation().getStreamOffset();
        if (decodedResultLimit > http.decodedResultProgress)
        {
            final int decodedOffset = offset + http.decodedResultProgress - http.decodedParserProgress;
            final int decodedLimit = offset + decodedResultLimit - http.decodedParserProgress;
            final int decodedProgress =
                http.onDecodeResponseResult(traceId, authorization, buffer, decodedOffset, decodedLimit, true);

            http.decodedResultProgress = decodedProgress - offset + http.decodedParserProgress;
        }

        progress = offset + http.decodedResultProgress - http.decodedParserProgress;

        if (http.decodedResultProgress == decodedResultLimit)
        {
            http.decoder = decodeJsonRpcNext;
        }

        return progress;
    }

    private int decodeJsonRpcErrorStart(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event != JsonParser.Event.START_OBJECT)
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            http.decodedResultProgress = (int) parser.getLocation().getStreamOffset() - 1;
            http.decodedSkipDepth = 1;
            http.decodedSkipThen = decodeJsonRpcErrorEnd;
            http.decoder = decodeJsonRpcSkipObject;

            progress = offset + http.decodedResultProgress - http.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcErrorEnd(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        final int decodedErrorLimit = (int) parser.getLocation().getStreamOffset();
        final int decodedOffset = offset + http.decodedResultProgress - http.decodedParserProgress;
        final int decodedLimit = offset + decodedErrorLimit - http.decodedParserProgress;

        http.decoder = http.decodedMessageThen;
        http.onDecodeResponseError(traceId, authorization, buffer, decodedOffset, decodedLimit);

        return limit;
    }

    private int decodeJsonRpcSkipObject(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_OBJECT || event == JsonParser.Event.START_ARRAY)
            {
                http.decodedSkipDepth++;
            }
            else if (event == JsonParser.Event.END_OBJECT || event == JsonParser.Event.END_ARRAY)
            {
                http.decodedSkipDepth--;
                if (http.decodedSkipDepth == 0)
                {
                    http.decoder = http.decodedSkipThen;
                    break;
                }
            }
        }

        if (http.decodedSkipThen == decodeJsonRpcResultEnd)
        {
            final int decodedResultLimit = (int) parser.getLocation().getStreamOffset();
            if (decodedResultLimit > http.decodedResultProgress)
            {
                final int decodedOffset = offset + http.decodedResultProgress - http.decodedParserProgress;
                final int decodedLimit = offset + decodedResultLimit - http.decodedParserProgress;
                final boolean endOfInput = http.decoder == decodeJsonRpcResultEnd;
                final int decodedProgress =
                    http.onDecodeResponseResult(traceId, authorization, buffer, decodedOffset, decodedLimit, endOfInput);

                http.decodedResultProgress = decodedProgress - offset + http.decodedParserProgress;
            }

            progress = offset + http.decodedResultProgress - http.decodedParserProgress;
        }
        else if (http.decodedSkipThen == decodeJsonRpcErrorEnd)
        {
            progress = offset + http.decodedResultProgress - http.decodedParserProgress;
        }
        else
        {
            progress = offset + (int) (parser.getLocation().getStreamOffset() - http.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcEnd(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParserEx parser = http.decodableJson;

        decode:
        {
            if (parser.hasNext())
            {
                http.onDecodeParseError(traceId, authorization);
                http.decoder = decodeIgnore;
                break decode;
            }

            parser.close();
            http.decodableJson = null;
            http.decoder = http.decodedMessageThen;

            progress = offset + (int) (parser.getLocation().getStreamOffset() - http.decodedParserProgress);
        }

        return progress;
    }

    private int decodeHold(
        HttpStream http,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (limit - progress >= decodeMax)
        {
            http.onDecodeParseError(traceId, authorization);
            http.decoder = decodeIgnore;
        }

        return progress;
    }

    private int decodeIgnore(
        HttpStream http,
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

    @Override
    public int originTypeId()
    {
        return a2aTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return httpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new A2aClientBindingConfig(binding, context));
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();

        final A2aClientBindingConfig binding = bindings.get(routedId);
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        if (binding != null && extension.sizeof() > 0)
        {
            final A2aBeginExFW beginEx = a2aBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final A2aClientRouteConfig route = beginEx != null ? binding.resolve(authorization) : null;

            if (route != null)
            {
                final int kind = beginEx.kind();
                final A2aStream stream = switch (kind)
                {
                case KIND_AGENT_CARD -> new A2aCardStream(sender, originId, routedId, initialId, route.id, binding);
                case KIND_PUSH_NOTIFY -> new A2aDeliverStream(sender, originId, routedId, initialId, route.id, binding);
                default -> new A2aCallStream(sender, originId, routedId, initialId, route.id, binding, kind);
                };
                newStream = stream::onAppMessage;
            }
        }

        return newStream;
    }

    private abstract class A2aStream
    {
        protected final MessageConsumer sender;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final long resolvedId;
        protected final A2aClientBindingConfig binding;

        protected long authorization;
        protected String credentials;
        protected HttpStream http;
        protected int decodeSlot = NO_SLOT;
        protected int decodeSlotOffset;
        protected int state;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;
        protected long replyBud;
        protected int replyPad;

        A2aStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            A2aClientBindingConfig binding)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.binding = binding;
        }

        void onAppMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAppData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAppFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = A2aState.openingInitial(state);

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = encodeMax;

            this.authorization = authorization;
            this.credentials = resolveCredentials(authorization);

            final OctetsFW extension = begin.extension();
            final A2aBeginExFW beginEx = a2aBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());

            onAppBeginEx(traceId, authorization, beginEx);
        }

        abstract void onAppBeginEx(
            long traceId,
            long authorization,
            A2aBeginExFW beginEx);

        void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            final OctetsFW payload = data.payload();
            if (payload != null && payload.sizeof() > 0)
            {
                onAppPayload(traceId, authorization, payload);
            }

            flushAppWindow(traceId, authorization);
        }

        void onAppPayload(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
        }

        void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = A2aState.closedInitial(state);

            assert initialAck <= initialSeq;

            onAppEnded(traceId, authorization);
        }

        void onAppEnded(
            long traceId,
            long authorization)
        {
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = A2aState.closedInitial(state);

            assert initialAck <= initialSeq;

            if (http != null)
            {
                http.doNetAbort(traceId, authorization);
            }

            cleanupDecodeSlot();
            onAppClosed(traceId, authorization);
        }

        void onAppClosed(
            long traceId,
            long authorization)
        {
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + flush.reserved();

            assert initialAck <= initialSeq;
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            state = A2aState.openedReply(state);
            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            onAppWindowed(traceId, authorization, budgetId);
        }

        void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (http != null)
            {
                http.decodeNet(traceId, authorization, budgetId);
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;

            state = A2aState.closedReply(state);

            assert replyAck <= replySeq;

            if (http != null)
            {
                http.doNetReset(traceId, authorization);
            }

            cleanupDecodeSlot();
            onAppClosed(traceId, authorization);
        }

        private String resolveCredentials(
            long authorization)
        {
            final GuardHandler guard = binding.guard;

            return guard != null && (authorization & GuardHandler.MASK_AUTHORIZED) != 0L
                ? guard.credentials(authorization)
                : null;
        }

        final String authorizationHeader()
        {
            return binding.authorizationOf(credentials);
        }

        void doAppBegin(
            long traceId,
            long authorization)
        {
            doAppBegin(traceId, authorization, null);
        }

        void doAppBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            state = A2aState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);
        }

        int replyWindow()
        {
            return Math.max(replyMax - (int) (replySeq - replyAck) - replyPad, 0);
        }

        int doAppData(
            long traceId,
            long authorization,
            Flyweight extension,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int length = Math.min(replyWindow(), limit - offset);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, COMPLETE, replyBud, reserved, buffer, offset, length, extension);

                replySeq += reserved;

                assert replySeq <= replyAck + replyMax;
            }

            return offset + length;
        }

        void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        void doAppAbort(
            long traceId,
            long authorization)
        {
            if (A2aState.replyOpening(state) && !A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        void doAppReset(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
            }
        }

        void doAppResetError(
            long traceId,
            long authorization,
            int code,
            String message)
        {
            final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .error(e -> e.code(code).message(message))
                .build();

            doAppReset(traceId, authorization, resetEx);
        }

        void doAppResetBearer(
            long traceId,
            long authorization,
            String realm,
            String scopes,
            A2aBearerError error)
        {
            final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .bearer(b -> b.realm(realm).scopes(scopes).error(e -> e.set(error)))
                .build();

            doAppReset(traceId, authorization, resetEx);
        }

        void doAppRejected(
            long traceId,
            long authorization,
            int status,
            String challenge,
            String notFound)
        {
            final String challenged = status == 401 || status == 403 ? challenge : null;
            if (challenged != null && bearerChallengeMatcher.reset(challenged).matches())
            {
                final String realm = bearerChallengeMatcher.group("realm");
                final String scopes = bearerChallengeMatcher.group("scope");
                final String errorParam = bearerChallengeMatcher.group("error");
                final A2aBearerError error = errorParam != null
                    ? A2aBearerError.valueOf(errorParam.toUpperCase())
                    : status == 403 ? A2aBearerError.INSUFFICIENT_SCOPE : A2aBearerError.INVALID_TOKEN;

                doAppResetBearer(traceId, authorization, realm, scopes, error);
            }
            else if (status == 404 && notFound != null)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_NOT_FOUND, notFound);
            }
            else if (status > 0)
            {
                doAppResetError(traceId, authorization, status, reasonOf(status));
            }
            else
            {
                doAppResetError(traceId, authorization, ERROR_CODE_BAD_GATEWAY, ERROR_MESSAGE_BAD_GATEWAY);
            }
        }

        void doAppWindow(
            long traceId,
            long authorization)
        {
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0);
        }

        void flushAppWindow(
            long traceId,
            long authorization)
        {
            final long newAck = Math.max(initialSeq - pendingAppBytes(), initialAck);
            if (newAck > initialAck)
            {
                initialAck = newAck;

                assert initialAck <= initialSeq;

                doAppWindow(traceId, authorization);
            }
        }

        int pendingAppBytes()
        {
            return http != null ? http.encodeSlotOffset : 0;
        }

        void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization, null);
            doAppAbort(traceId, authorization);
            cleanupDecodeSlot();
        }

        void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
        }

        void onNetBegin(
            long traceId,
            long authorization,
            String extensions)
        {
        }

        void onNetEnd(
            long traceId,
            long authorization)
        {
            doAppEnd(traceId, authorization);
        }

        void onNetAbort(
            long traceId,
            long authorization)
        {
            doAppAbort(traceId, authorization);
        }

        void onNetReset(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization, null);
        }

        void onNetRejected(
            long traceId,
            long authorization,
            int status,
            String challenge)
        {
            doAppRejected(traceId, authorization, status, challenge, null);
        }
    }

    private final class A2aCallStream extends A2aStream implements A2aCardConsumer
    {
        private final int kind;
        private final A2aOperation operation;

        private String agent;
        private String extensions;
        private int contentLength = -1;
        private UnsafeBufferEx beginExBuffer;
        private A2aDialect dialect;
        private A2aAgentInterface selected;
        private int requestReceived;
        private int requestForwarded;

        A2aCallStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            A2aClientBindingConfig binding,
            int kind)
        {
            super(sender, originId, routedId, initialId, resolvedId, binding);
            this.kind = kind;
            this.operation = operationOf(kind);
        }

        @Override
        void onAppBeginEx(
            long traceId,
            long authorization,
            A2aBeginExFW beginEx)
        {
            this.agent = agentOf(beginEx);
            this.extensions = extensionsOf(beginEx);
            this.contentLength = contentLengthOf(beginEx);

            final byte[] copy = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), copy);
            this.beginExBuffer = new UnsafeBufferEx(copy);

            doAppWindow(traceId, authorization);

            if (binding.serverScheme == null)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_NOT_FOUND, ERROR_MESSAGE_AGENT_NOT_FOUND);
            }
            else
            {
                fetchCard(this, binding, agent, resolvedId, traceId, authorization);
            }
        }

        @Override
        void onNetBegin(
            long traceId,
            long authorization,
            String extensions)
        {
            doAppBegin(traceId, authorization, extensions != null ? replyBeginEx(extensions) : null);
        }

        private A2aBeginExFW replyBeginEx(
            String extensions)
        {
            final A2aRequestParams params = paramsOf(a2aBeginExRO.wrap(beginExBuffer, 0, beginExBuffer.capacity()));
            final A2aBeginExFW.Builder builder = a2aBeginExRW.wrap(extBuffer, 0, extBuffer.capacity()).typeId(a2aTypeId);

            switch (kind)
            {
            case KIND_MESSAGE_SEND:
                builder.messageSend(b -> b.extensions(extensions));
                break;
            case KIND_MESSAGE_STREAM:
                builder.messageStream(b -> b.extensions(extensions));
                break;
            case KIND_TASKS_GET:
                builder.tasksGet(b -> b.extensions(extensions).taskId(params.taskId()));
                break;
            case KIND_TASKS_LIST:
                builder.tasksList(b -> b.extensions(extensions));
                break;
            case KIND_TASKS_CANCEL:
                builder.tasksCancel(b -> b.extensions(extensions).taskId(params.taskId()));
                break;
            case KIND_TASKS_SUBSCRIBE:
                builder.tasksSubscribe(b -> b.extensions(extensions).taskId(params.taskId()));
                break;
            case KIND_PUSH_CONFIG_CREATE:
                builder.pushConfigCreate(b -> b.extensions(extensions).taskId(params.taskId()));
                break;
            case KIND_PUSH_CONFIG_GET:
                builder.pushConfigGet(b -> b.extensions(extensions).taskId(params.taskId()).configId(params.configId()));
                break;
            case KIND_PUSH_CONFIG_LIST:
                builder.pushConfigList(b -> b.extensions(extensions).taskId(params.taskId()));
                break;
            case KIND_PUSH_CONFIG_DELETE:
                builder.pushConfigDelete(b -> b.extensions(extensions).taskId(params.taskId()).configId(params.configId()));
                break;
            default:
                builder.extendedAgentCard(b -> b.extensions(extensions));
                break;
            }

            return builder.build();
        }

        @Override
        public void onCard(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (!A2aState.replyClosed(state))
            {
                A2aAgentCard card = null;

                try
                {
                    card = cardParser.parse(buffer, offset, limit);
                }
                catch (RuntimeException ex)
                {
                    onCardRejected(traceId, authorization, 0, null);
                }

                if (card != null)
                {
                    onCardParsed(traceId, authorization, card);
                }
            }
        }

        private void onCardParsed(
            long traceId,
            long authorization,
            A2aAgentCard card)
        {
            this.selected = card.select(binding.versions);

            final List<String> denied = binding.deniedRoles(card.skills, authorization);

            if (!denied.isEmpty())
            {
                doAppResetBearer(traceId, authorization, BEARER_REALM, String.join(" ", denied),
                    A2aBearerError.INSUFFICIENT_SCOPE);
            }
            else if (selected == null)
            {
                if (card.offersJsonRpc())
                {
                    doAppResetError(traceId, authorization,
                        ERROR_CODE_VERSION_NOT_SUPPORTED, ERROR_MESSAGE_VERSION_NOT_SUPPORTED);
                }
                else
                {
                    doAppResetError(traceId, authorization, ERROR_CODE_UNSUPPORTED_OPERATION,
                        ERROR_MESSAGE_UNSUPPORTED_OPERATION);
                }
            }
            else
            {
                this.dialect = dialects.resolveCard(A2aAgentCard.versionOf(selected.version));
                onDialect(traceId, authorization);
            }
        }

        private int encodeRequest(
            MutableDirectBufferEx output)
        {
            final A2aBeginExFW beginEx = a2aBeginExRO.wrap(beginExBuffer, 0, beginExBuffer.capacity());

            requestEncoder.wrap(output, 0).method(dialect.verbatim() ? operation.method() : operation.legacyMethod());

            if (operation.content())
            {
                requestEncoder.params();
            }
            else
            {
                final JsonObject params = dialect.requestParams(operation, paramsOf(beginEx), selected.tenant);
                if (params != null)
                {
                    requestEncoder.params().raw(params.toString());
                }
                requestEncoder.end();
            }

            return requestEncoder.limit();
        }

        private boolean translatesRequest()
        {
            return !dialect.verbatim() || selected.tenant != null;
        }

        private void onDialect(
            long traceId,
            long authorization)
        {
            final boolean body = operation.content();

            if (!dialect.verbatim() && operation.legacyMethod() == null)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_UNSUPPORTED_OPERATION, ERROR_MESSAGE_UNSUPPORTED_OPERATION);
            }
            else if (body && contentLength < 0)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_INVALID_PARAMS, ERROR_MESSAGE_INVALID_PARAMS);
            }
            else if (body && translatesRequest() && contentLength > decodeMax)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_PAYLOAD_TOO_LARGE, ERROR_MESSAGE_PAYLOAD_TOO_LARGE);
            }
            else
            {
                onRequest(traceId, authorization);
            }
        }

        @Override
        public void onCardRejected(
            long traceId,
            long authorization,
            int status,
            String challenge)
        {
            if (!A2aState.replyClosed(state))
            {
                doAppRejected(traceId, authorization, status, challenge, ERROR_MESSAGE_AGENT_NOT_FOUND);
            }
        }

        private void onRequest(
            long traceId,
            long authorization)
        {
            final HttpCallStream call = new HttpCallStream(this, selected, dialect);
            this.http = call;

            if (!operation.content())
            {
                final int limit = encodeRequest(codecBuffer);

                call.doEncodeRequestBegin(traceId, authorization, limit);
                call.doEncodeRequestData(traceId, authorization, codecBuffer, 0, limit);
                call.doEncodeRequestEnd(traceId, authorization);
            }
            else if (!translatesRequest())
            {
                final int limit = encodeRequest(codecBuffer);

                call.doEncodeRequestBegin(traceId, authorization, limit + contentLength + JSON_RPC_END.length());
                call.doEncodeRequestData(traceId, authorization, codecBuffer, 0, limit);

                if (decodeSlotOffset > 0)
                {
                    final int length = decodeSlotOffset;
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    forwardRequest(traceId, authorization, slotBuffer, 0, length);
                    cleanupDecodeSlot();
                }
            }
            else if (requestReceived == contentLength)
            {
                translateRequest(traceId, authorization, decodePool.buffer(decodeSlot));
            }

            flushAppWindow(traceId, authorization);
        }

        @Override
        void onAppPayload(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
            requestReceived += payload.sizeof();

            if (!operation.content() || requestReceived > contentLength)
            {
                cleanupApp(traceId, authorization);
            }
            else if (http == null || translatesRequest())
            {
                bufferRequest(traceId, authorization, payload);
            }
            else
            {
                forwardRequest(traceId, authorization, payload.buffer(), payload.offset(), payload.limit());
            }
        }

        private void forwardRequest(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            requestForwarded += limit - offset;

            http.doEncodeRequestData(traceId, authorization, buffer, offset, limit);

            if (requestForwarded == contentLength)
            {
                final int length = codecBuffer.putStringWithoutLengthAscii(0, JSON_RPC_END);
                http.doEncodeRequestData(traceId, authorization, codecBuffer, 0, length);
                http.doEncodeRequestEnd(traceId, authorization);
            }
        }

        private void bufferRequest(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupApp(traceId, authorization);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                slotBuffer.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();

                if (http != null && requestReceived == contentLength)
                {
                    translateRequest(traceId, authorization, slotBuffer);
                }
            }
        }

        private void translateRequest(
            long traceId,
            long authorization,
            DirectBufferEx body)
        {
            try
            {
                final int prefix = encodeRequest(translateBuffer);
                final JsonObject params = dialect.requestParams(operation,
                    new A2aRequestParams(null, null, null, -1, readValue(body, 0, decodeSlotOffset)), selected.tenant);
                final int translated = prefix + translateBuffer.putStringWithoutLengthUtf8(prefix, params.toString());
                final int limit = translated + translateBuffer.putStringWithoutLengthAscii(translated, JSON_RPC_END);

                cleanupDecodeSlot();

                final HttpCallStream call = (HttpCallStream) http;
                call.doEncodeRequestBegin(traceId, authorization, limit);
                call.doEncodeRequestData(traceId, authorization, translateBuffer, 0, limit);
                call.doEncodeRequestEnd(traceId, authorization);
            }
            catch (RuntimeException ex)
            {
                cleanupApp(traceId, authorization);
            }
        }

        @Override
        void onAppEnded(
            long traceId,
            long authorization)
        {
            if (operation.content() && requestReceived != contentLength)
            {
                cleanupApp(traceId, authorization);
            }
        }

        @Override
        int pendingAppBytes()
        {
            return decodeSlotOffset + (http != null ? http.encodeSlotOffset : 0);
        }

        @Override
        void cleanupApp(
            long traceId,
            long authorization)
        {
            if (http != null)
            {
                http.doNetAbort(traceId, authorization);
                http.doNetReset(traceId, authorization);
            }

            super.cleanupApp(traceId, authorization);
        }
    }

    private final class A2aCardStream extends A2aStream implements A2aCardConsumer
    {
        private int cardSlot = NO_SLOT;
        private int cardSlotLimit;
        private int cardDelivered;

        A2aCardStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            A2aClientBindingConfig binding)
        {
            super(sender, originId, routedId, initialId, resolvedId, binding);
        }

        @Override
        void onAppBeginEx(
            long traceId,
            long authorization,
            A2aBeginExFW beginEx)
        {
            final String agent = beginEx.agentCard().agent().asString();

            if (binding.serverScheme == null)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_NOT_FOUND, ERROR_MESSAGE_AGENT_NOT_FOUND);
            }
            else
            {
                doAppBegin(traceId, authorization);
                doAppWindow(traceId, authorization);

                fetchCard(this, binding, agent, resolvedId, traceId, authorization);
            }
        }

        @Override
        public void onCard(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (!A2aState.replyClosed(state))
            {
                cardSlot = encodePool.acquire(initialId);

                if (cardSlot == NO_SLOT)
                {
                    cleanupApp(traceId, authorization);
                }
                else
                {
                    encodePool.buffer(cardSlot).putBytes(0, buffer, offset, limit - offset);
                    cardSlotLimit = limit - offset;

                    flushCard(traceId, authorization);
                }
            }
        }

        @Override
        public void onCardRejected(
            long traceId,
            long authorization,
            int status,
            String challenge)
        {
            doAppRejected(traceId, authorization, status, challenge, ERROR_MESSAGE_AGENT_NOT_FOUND);
            doAppAbort(traceId, authorization);
        }

        @Override
        void onAppWindowed(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (cardSlot != NO_SLOT)
            {
                flushCard(traceId, authorization);
            }
        }

        private void flushCard(
            long traceId,
            long authorization)
        {
            try
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(cardSlot);
                final A2aAgentCard card = cardParser.parse(slotBuffer, 0, cardSlotLimit);
                final A2aDialect wire = dialects.resolveCard(card.legacy ? VERSION_LEGACY : VERSION_CANONICAL);
                final JsonObject canonical = wire.cardFromWire(readValue(slotBuffer, 0, cardSlotLimit).asJsonObject());
                final byte[] unified = A2aCardRoles.inject(canonical, binding::rolesOf).toString()
                    .getBytes(StandardCharsets.UTF_8);
                translateBuffer.putBytes(0, unified);
                final int limit = unified.length;

                cardDelivered = doAppData(traceId, authorization, null, translateBuffer, cardDelivered, limit);

                if (cardDelivered == limit)
                {
                    cleanupCardSlot();
                    doAppEnd(traceId, authorization);
                }
            }
            catch (RuntimeException ex)
            {
                cleanupApp(traceId, authorization);
            }
        }

        @Override
        void cleanupApp(
            long traceId,
            long authorization)
        {
            cleanupCardSlot();
            super.cleanupApp(traceId, authorization);
        }

        private void cleanupCardSlot()
        {
            if (cardSlot != NO_SLOT)
            {
                encodePool.release(cardSlot);
                cardSlot = NO_SLOT;
                cardSlotLimit = 0;
            }
        }
    }

    private final class A2aDeliverStream extends A2aStream
    {
        private String configId;
        private String url;
        private String token;
        private String scheme;
        private String pushCredentials;
        private int contentLength;
        private int attempts;
        private int bodyReceived;
        private long retryId = Signaler.NO_CANCEL_ID;
        private Deque<A2aDeliverStream> turn;

        A2aDeliverStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            A2aClientBindingConfig binding)
        {
            super(sender, originId, routedId, initialId, resolvedId, binding);
        }

        @Override
        void onAppBeginEx(
            long traceId,
            long authorization,
            A2aBeginExFW beginEx)
        {
            final var pushNotify = beginEx.pushNotify();

            this.configId = pushNotify.configId().asString();
            this.url = pushNotify.url().asString();
            this.token = pushNotify.token().asString();
            this.scheme = pushNotify.scheme().asString();
            this.pushCredentials = pushNotify.credentials().asString();
            this.contentLength = pushNotify.contentLength();

            if (url == null || contentLength < 0 || hostOf(url) == null)
            {
                doAppResetError(traceId, authorization, ERROR_CODE_INVALID_PARAMS, ERROR_MESSAGE_INVALID_PARAMS);
            }
            else if (retains() && !acquireDecodeSlot())
            {
                doAppResetError(traceId, authorization, ERROR_CODE_BAD_GATEWAY, ERROR_MESSAGE_BAD_GATEWAY);
            }
            else
            {
                doAppBegin(traceId, authorization);

                if (configId != null)
                {
                    turn = deliveries.computeIfAbsent(deliveryKey(), key -> new ArrayDeque<>());
                    turn.addLast(this);
                }

                if (retains())
                {
                    doAppWindow(traceId, authorization);
                }

                if (inTurn())
                {
                    onTurn(traceId, authorization);
                }
            }
        }

        @Override
        void onAppPayload(
            long traceId,
            long authorization,
            OctetsFW payload)
        {
            bodyReceived += payload.sizeof();

            if (bodyReceived > contentLength)
            {
                cleanupApp(traceId, authorization);
            }
            else if (retains())
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                slotBuffer.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();

                if (bodyReceived == contentLength && inTurn())
                {
                    onTurn(traceId, authorization);
                }
            }
            else if (http != null)
            {
                http.doEncodeRequestData(traceId, authorization, payload.buffer(), payload.offset(), payload.limit());

                if (bodyReceived == contentLength)
                {
                    http.doEncodeRequestEnd(traceId, authorization);
                }
            }
        }

        String authorizationOfPush()
        {
            return pushCredentials != null
                ? (scheme != null ? scheme : PUSH_SCHEME_DEFAULT) + " " + pushCredentials
                : null;
        }

        String token()
        {
            return token;
        }

        @Override
        void onNetEnd(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlot();
            doAppEnd(traceId, authorization);
            releaseTurn(traceId, authorization);
        }

        @Override
        void onNetRejected(
            long traceId,
            long authorization,
            int status,
            String challenge)
        {
            if (status >= 500 || status == 0)
            {
                onDeliveryFailed(traceId, authorization, status);
            }
            else
            {
                giveUp(traceId, authorization, status);
            }
        }

        @Override
        void onNetAbort(
            long traceId,
            long authorization)
        {
            onDeliveryFailed(traceId, authorization, 0);
        }

        @Override
        void onNetReset(
            long traceId,
            long authorization)
        {
            onDeliveryFailed(traceId, authorization, 0);
        }

        @Override
        void onAppClosed(
            long traceId,
            long authorization)
        {
            cancelRetry();
            releaseTurn(traceId, authorization);
        }

        @Override
        void cleanupApp(
            long traceId,
            long authorization)
        {
            if (http != null)
            {
                http.doNetAbort(traceId, authorization);
                http.doNetReset(traceId, authorization);
            }

            super.cleanupApp(traceId, authorization);
            onAppClosed(traceId, authorization);
        }

        private boolean retains()
        {
            return contentLength <= retainedMax(binding, decodeMax);
        }

        private boolean inTurn()
        {
            return turn == null || turn.peekFirst() == this;
        }

        private String deliveryKey()
        {
            return binding.id + "/" + configId;
        }

        private boolean acquireDecodeSlot()
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            return decodeSlot != NO_SLOT;
        }

        private void onTurn(
            long traceId,
            long authorization)
        {
            if (!retains())
            {
                attempt(traceId, authorization);
                doAppWindow(traceId, authorization);
            }
            else if (bodyReceived == contentLength)
            {
                attempt(traceId, authorization);
            }
        }

        private void attempt(
            long traceId,
            long authorization)
        {
            InetAddress[] addresses = null;

            try
            {
                addresses = context.resolveHost(hostOf(url));
            }
            catch (Exception ex)
            {
                addresses = null;
            }

            if (addresses == null)
            {
                onDeliveryFailed(traceId, authorization, 0);
            }
            else if (!binding.pushPolicy.permits(url, addresses))
            {
                giveUp(traceId, authorization, ERROR_CODE_FORBIDDEN);
            }
            else
            {
                deliver(traceId, authorization);
            }
        }

        private void deliver(
            long traceId,
            long authorization)
        {
            final HttpDeliverStream delivery = new HttpDeliverStream(this, url);
            this.http = delivery;

            delivery.doEncodeRequestBegin(traceId, authorization, contentLength);

            if (retains())
            {
                if (decodeSlotOffset > 0)
                {
                    delivery.doEncodeRequestData(traceId, authorization, decodePool.buffer(decodeSlot), 0, decodeSlotOffset);
                }

                delivery.doEncodeRequestEnd(traceId, authorization);
            }
            else if (bodyReceived == contentLength)
            {
                delivery.doEncodeRequestEnd(traceId, authorization);
            }
        }

        private void onDeliveryFailed(
            long traceId,
            long authorization,
            int status)
        {
            http = null;

            if (retains() && attempts < config.pushDeliveryRetries())
            {
                final long delay = config.pushDeliveryBackoff().toMillis() << attempts;

                attempts++;
                retryId = context.signaler().signalAt(context.clock().millis() + delay, 0, id -> onRetry());
            }
            else
            {
                giveUp(traceId, authorization, status);
            }
        }

        private void onRetry()
        {
            retryId = Signaler.NO_CANCEL_ID;
            attempt(context.supplyTraceId(), authorization);
        }

        private void giveUp(
            long traceId,
            long authorization,
            int status)
        {
            cleanupDecodeSlot();
            doAppRejected(traceId, authorization, status, null, null);
            releaseTurn(traceId, authorization);
        }

        private void cancelRetry()
        {
            if (retryId != Signaler.NO_CANCEL_ID)
            {
                context.signaler().cancel(retryId);
                retryId = Signaler.NO_CANCEL_ID;
            }
        }

        private void releaseTurn(
            long traceId,
            long authorization)
        {
            final Deque<A2aDeliverStream> queue = turn;

            if (queue != null)
            {
                final boolean head = queue.peekFirst() == this;

                turn = null;
                queue.remove(this);

                if (queue.isEmpty())
                {
                    deliveries.remove(deliveryKey());
                }
                else if (head)
                {
                    queue.peekFirst().onTurn(traceId, authorization);
                }
            }
        }
    }

    private static int retainedMax(
        A2aClientBindingConfig binding,
        int decodeMax)
    {
        return binding.pushMaxLength > 0 ? Math.min(binding.pushMaxLength, decodeMax) : decodeMax;
    }

    private static String hostOf(
        String url)
    {
        String host = null;

        try
        {
            host = URI.create(url).getHost();
        }
        catch (IllegalArgumentException ex)
        {
            host = null;
        }

        return host;
    }

    private void fetchCard(
        A2aCardConsumer consumer,
        A2aClientBindingConfig binding,
        String agent,
        long routedId,
        long traceId,
        long authorization)
    {
        final String base = agent != null ? binding.serverPath + "/" + agent : "";
        final HttpCardStream card = new HttpCardStream(consumer, binding, binding.id, routedId,
            base + CARD_PATH_WELL_KNOWN, base + CARD_PATH_WELL_KNOWN_LEGACY);

        card.doEncodeRequestBegin(traceId, authorization, 0);
    }

    private String agentOf(
        A2aBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_MESSAGE_SEND -> beginEx.messageSend().agent().asString();
        case KIND_MESSAGE_STREAM -> beginEx.messageStream().agent().asString();
        case KIND_TASKS_GET -> beginEx.tasksGet().agent().asString();
        case KIND_TASKS_LIST -> beginEx.tasksList().agent().asString();
        case KIND_TASKS_CANCEL -> beginEx.tasksCancel().agent().asString();
        case KIND_TASKS_SUBSCRIBE -> beginEx.tasksSubscribe().agent().asString();
        case KIND_PUSH_CONFIG_CREATE -> beginEx.pushConfigCreate().agent().asString();
        case KIND_PUSH_CONFIG_GET -> beginEx.pushConfigGet().agent().asString();
        case KIND_PUSH_CONFIG_LIST -> beginEx.pushConfigList().agent().asString();
        case KIND_PUSH_CONFIG_DELETE -> beginEx.pushConfigDelete().agent().asString();
        case KIND_EXTENDED_AGENT_CARD -> beginEx.extendedAgentCard().agent().asString();
        default -> null;
        };
    }

    private String extensionsOf(
        A2aBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_MESSAGE_SEND -> beginEx.messageSend().extensions().asString();
        case KIND_MESSAGE_STREAM -> beginEx.messageStream().extensions().asString();
        case KIND_TASKS_GET -> beginEx.tasksGet().extensions().asString();
        case KIND_TASKS_LIST -> beginEx.tasksList().extensions().asString();
        case KIND_TASKS_CANCEL -> beginEx.tasksCancel().extensions().asString();
        case KIND_TASKS_SUBSCRIBE -> beginEx.tasksSubscribe().extensions().asString();
        case KIND_PUSH_CONFIG_CREATE -> beginEx.pushConfigCreate().extensions().asString();
        case KIND_PUSH_CONFIG_GET -> beginEx.pushConfigGet().extensions().asString();
        case KIND_PUSH_CONFIG_LIST -> beginEx.pushConfigList().extensions().asString();
        case KIND_PUSH_CONFIG_DELETE -> beginEx.pushConfigDelete().extensions().asString();
        case KIND_EXTENDED_AGENT_CARD -> beginEx.extendedAgentCard().extensions().asString();
        default -> null;
        };
    }

    private int contentLengthOf(
        A2aBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_MESSAGE_SEND -> beginEx.messageSend().contentLength();
        case KIND_MESSAGE_STREAM -> beginEx.messageStream().contentLength();
        case KIND_TASKS_LIST -> beginEx.tasksList().contentLength();
        case KIND_PUSH_CONFIG_CREATE -> beginEx.pushConfigCreate().contentLength();
        default -> -1;
        };
    }

    private static A2aOperation operationOf(
        int kind)
    {
        return switch (kind)
        {
        case KIND_MESSAGE_SEND -> A2aOperation.MESSAGE_SEND;
        case KIND_MESSAGE_STREAM -> A2aOperation.MESSAGE_STREAM;
        case KIND_TASKS_GET -> A2aOperation.TASKS_GET;
        case KIND_TASKS_LIST -> A2aOperation.TASKS_LIST;
        case KIND_TASKS_CANCEL -> A2aOperation.TASKS_CANCEL;
        case KIND_TASKS_SUBSCRIBE -> A2aOperation.TASKS_SUBSCRIBE;
        case KIND_PUSH_CONFIG_CREATE -> A2aOperation.PUSH_CONFIG_CREATE;
        case KIND_PUSH_CONFIG_GET -> A2aOperation.PUSH_CONFIG_GET;
        case KIND_PUSH_CONFIG_LIST -> A2aOperation.PUSH_CONFIG_LIST;
        case KIND_PUSH_CONFIG_DELETE -> A2aOperation.PUSH_CONFIG_DELETE;
        default -> A2aOperation.EXTENDED_AGENT_CARD;
        };
    }

    private static A2aRequestParams paramsOf(
        A2aBeginExFW beginEx)
    {
        return switch (beginEx.kind())
        {
        case KIND_TASKS_GET -> new A2aRequestParams(beginEx.tasksGet().taskId().asString(), null, null,
            beginEx.tasksGet().historyLength(), null);
        case KIND_TASKS_CANCEL -> new A2aRequestParams(beginEx.tasksCancel().taskId().asString(), null, null, -1, null);
        case KIND_TASKS_SUBSCRIBE -> new A2aRequestParams(beginEx.tasksSubscribe().taskId().asString(), null, null, -1, null);
        case KIND_PUSH_CONFIG_GET -> new A2aRequestParams(beginEx.pushConfigGet().taskId().asString(),
            beginEx.pushConfigGet().configId().asString(), null, -1, null);
        case KIND_PUSH_CONFIG_LIST -> new A2aRequestParams(beginEx.pushConfigList().taskId().asString(), null, null, -1, null);
        case KIND_PUSH_CONFIG_DELETE -> new A2aRequestParams(beginEx.pushConfigDelete().taskId().asString(),
            beginEx.pushConfigDelete().configId().asString(), null, -1, null);
        default -> new A2aRequestParams(null, null, null, -1, null);
        };
    }

    private static JsonValue readValue(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final byte[] json = new byte[limit - offset];
        buffer.getBytes(offset, json);
        return Json.createReader(new ByteArrayInputStream(json)).readValue();
    }

    private static String reasonOf(
        int status)
    {
        return switch (status)
        {
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 405 -> "Method Not Allowed";
        case 408 -> "Request Timeout";
        case 409 -> "Conflict";
        case 413 -> "Payload Too Large";
        case 429 -> "Too Many Requests";
        case 500 -> "Internal Server Error";
        case 502 -> ERROR_MESSAGE_BAD_GATEWAY;
        case 503 -> "Service Unavailable";
        case 504 -> "Gateway Timeout";
        default -> "Error";
        };
    }

    private abstract class HttpStream
    {
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;

        protected MessageConsumer net;
        protected int state;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;

        protected int encodeSlot = NO_SLOT;
        protected int encodeSlotOffset;
        protected long encodeSlotTraceId;
        protected long encodeSlotAuthorization;

        protected int decodeSlot = NO_SLOT;
        protected int decodeSlotOffset;
        protected int decodeSlotReserved;

        protected HttpResponseDecoder decoder = decodeIgnore;
        protected JsonParserEx decodableJson;
        protected int decodedParserProgress;
        protected int decodedResultProgress;
        protected int decodedSkipDepth;
        protected HttpResponseDecoder decodedSkipThen;
        protected HttpResponseDecoder decodedMessageThen = decodeIgnore;

        HttpStream(
            long originId,
            long routedId)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.replyMax = decodeMax;
        }

        abstract void doEncodeRequestBegin(
            long traceId,
            long authorization,
            int contentLength);

        abstract void onNetAccepted(
            long traceId,
            long authorization,
            String extensions);

        abstract void onNetRejected(
            long traceId,
            long authorization,
            int status,
            String challenge);

        abstract void onNetCompleted(
            long traceId,
            long authorization);

        abstract void onNetAborted(
            long traceId,
            long authorization);

        abstract void onNetResetted(
            long traceId,
            long authorization);

        abstract void onDecodeParseError(
            long traceId,
            long authorization);

        void onNetEnded(
            long traceId,
            long authorization)
        {
        }

        int onDecodeResponseResult(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean endOfInput)
        {
            return limit;
        }

        void onDecodeResponseError(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            onDecodeParseError(traceId, authorization);
        }

        final void onNetMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;

            state = A2aState.openingReply(state);
            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = decodeMax;

            assert replyAck <= replySeq;

            final OctetsFW ext = begin.extension();
            final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(ext.buffer(), ext.offset(), ext.limit());
            String status = null;
            String challenge = null;
            String extensions = null;
            if (httpBeginEx != null)
            {
                status = headerOf(httpBeginEx, HTTP_HEADER_STATUS);
                extensions = headerOf(httpBeginEx, HTTP_HEADER_A2A_EXTENSIONS);
                challenge = headerOf(httpBeginEx, HTTP_HEADER_WWW_AUTHENTICATE);

                if (CONTENT_TYPE_EVENT_STREAM.equals(headerOf(httpBeginEx, HTTP_HEADER_CONTENT_TYPE)))
                {
                    decoder = decodeEvent;
                    decodedMessageThen = decodeEvent;
                }
            }

            if (status != null && status.startsWith(STATUS_SUCCESSFUL_PREFIX))
            {
                onNetAccepted(traceId, authorization, extensions);
                flushNetWindow(traceId, authorization, 0L);
            }
            else
            {
                onNetRejected(traceId, authorization, status != null ? Integer.parseInt(status) : 0, challenge);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + data.reserved();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + decodeMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload() != null ? data.payload() : EMPTY_OCTETS;
                int reserved = data.reserved();
                DirectBufferEx buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();
                    decodeSlotReserved += reserved;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                if (decodableJson != null)
                {
                    final int delta = (int) (decodableJson.getLocation().getStreamOffset() - decodedParserProgress);
                    decodableJson.wrap(buffer, offset + delta, limit);
                }

                decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);

                if (decodedMessageThen == decodeEvent && hasFin(data.flags()))
                {
                    onEventEnd(traceId, authorization);
                }
            }
        }

        private void onEventEnd(
            long traceId,
            long authorization)
        {
            if (decoder != decodeEvent && decoder != decodeIgnore && decodeSlot == NO_SLOT)
            {
                onDecodeParseError(traceId, authorization);
            }
        }

        final void decodeNet(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = decodePool.buffer(decodeSlot);
                final int reserved = decodeSlotReserved;
                final int offset = 0;
                final int limit = decodeSlotOffset;

                if (decodableJson != null)
                {
                    final int delta = (int) (decodableJson.getLocation().getStreamOffset() - decodedParserProgress);
                    decodableJson.wrap(buffer, offset + delta, limit);
                }

                decodeNet(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }

            flushNetWindow(traceId, authorization, budgetId);
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
            HttpResponseDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
            }

            decodedParserProgress += progress - offset;

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(replyId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (int) ((long) reserved * (limit - progress) / (limit - offset));
                }
            }
            else
            {
                cleanupDecodeSlot();
            }

            if (A2aState.replyClosing(state) &&
                decodeSlot == NO_SLOT &&
                !A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                onNetCompleted(traceId, authorization);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            state = A2aState.closingReply(state);

            assert replyAck <= replySeq;

            onNetEnded(traceId, authorization);

            if (decodeSlot == NO_SLOT && !A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                onNetCompleted(traceId, authorization);
            }
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + flush.reserved();
            replyAck = replySeq;

            assert replyAck <= replySeq;
            doNetWindow(traceId, authorization, 0L, 0);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = A2aState.closedReply(state);

            assert replyAck <= replySeq;

            cleanupDecodeSlot();
            onNetAborted(traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            state = A2aState.openedInitial(state);
            initialAck = acknowledge;
            initialMax = maximum;

            assert initialAck <= initialSeq;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;
                final long slotTraceId = encodeSlotTraceId;
                final long slotAuthorization = encodeSlotAuthorization;

                encodeSlotOffset = 0;
                encodeNet(slotTraceId, slotAuthorization, encodeBuffer, 0, limit);
            }

            onNetWindowed(traceId, authorization);
        }

        void onNetWindowed(
            long traceId,
            long authorization)
        {
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;
            assert acknowledge <= initialAck;

            initialAck = acknowledge;
            state = A2aState.closedInitial(state);

            assert initialAck <= initialSeq;

            cleanupEncodeSlot();
            onNetResetted(traceId, authorization);
        }

        private void flushNetWindow(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (net != null)
            {
                final long replyAckMax = Math.max(replySeq - decodeSlotReserved, replyAck);
                if (replyAckMax > replyAck || !A2aState.replyOpened(state))
                {
                    replyAck = replyAckMax;

                    assert replyAck <= replySeq;

                    doNetWindow(traceId, authorization, budgetId, 0);
                }
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = A2aState.openedReply(state);

            doWindow(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, FRAMING_CAPABILITIES_MASK);
        }

        protected final void doNetBegin(
            long traceId,
            long authorization,
            HttpBeginExFW httpBeginEx)
        {
            state = A2aState.openingInitial(state);

            net = newStream(this::onNetMessage,
                originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, 0L, affinity, httpBeginEx);

            assert net != null;
        }

        final void doEncodeRequestData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotTraceId = traceId;
                encodeSlotAuthorization = authorization;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, buffer, offset, limit);
        }

        final void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            if (net != null)
            {
                state = A2aState.closingInitial(state);

                if (!A2aState.initialClosed(state) &&
                    encodeSlot == NO_SLOT)
                {
                    state = A2aState.closedInitial(state);
                    doEnd(net, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);

                    cleanupEncodeSlot();
                }
            }
        }

        final void doNetAbort(
            long traceId,
            long authorization)
        {
            if (net != null && !A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doAbort(net, originId, routedId, initialId, 0L, 0L, 0, traceId, authorization);

                cleanupEncodeSlot();
            }
        }

        final void doNetReset(
            long traceId,
            long authorization)
        {
            if (net != null && !A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doReset(net, originId, routedId, replyId, 0L, 0L, 0, traceId, authorization, null);

                cleanupDecodeSlot();
            }
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int initialWin = initialMax - (int) (initialSeq - initialAck);
            final int length = Math.max(Math.min(initialWin, maxLength), 0);

            if (length > 0)
            {
                doData(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, COMPLETE, 0L, length, buffer, offset, length, null);

                initialSeq += length;
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                    encodeSlotTraceId = traceId;
                    encodeSlotAuthorization = authorization;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (A2aState.initialClosing(state))
                {
                    doEncodeRequestEnd(traceId, authorization);
                }
            }
        }

        final void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
                encodeSlotAuthorization = 0;
            }
        }

        final void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        final void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetAbort(traceId, authorization);
            doNetReset(traceId, authorization);
            onNetAborted(traceId, authorization);
        }
    }

    private final class HttpCallStream extends HttpStream
    {
        private final A2aCallStream call;
        private final A2aAgentInterface selected;
        private final A2aDialect dialect;

        private ResultDecoder resultDecoder = decodeResultStart;
        private int resultDelivered;

        HttpCallStream(
            A2aCallStream call,
            A2aAgentInterface selected,
            A2aDialect dialect)
        {
            super(call.binding.id, call.resolvedId);
            this.call = call;
            this.selected = selected;
            this.dialect = dialect;
            this.decoder = decodeJsonRpc;
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization,
            int contentLength)
        {
            final String accept = call.operation.streaming() ? CONTENT_TYPE_EVENT_STREAM : CONTENT_TYPE_JSON;
            final String extensions = call.extensions;
            final String authorizationHeader = call.authorizationHeader();
            final String length = Integer.toString(contentLength);

            final HttpBeginExFW.Builder builder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_SCHEME).value(selected.scheme()))
                .headersItem(h -> h.name(HTTP_HEADER_AUTHORITY).value(selected.authority()))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(selected.path()))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(accept))
                .headersItem(h -> h.name(HTTP_HEADER_A2A_VERSION).value(dialect.version()));

            if (extensions != null)
            {
                builder.headersItem(h -> h.name(HTTP_HEADER_A2A_EXTENSIONS).value(extensions));
            }

            if (authorizationHeader != null)
            {
                builder.headersItem(h -> h.name(HTTP_HEADER_AUTHORIZATION).value(authorizationHeader));
            }

            doNetBegin(traceId, authorization,
                builder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(length)).build());
        }

        @Override
        void onNetAccepted(
            long traceId,
            long authorization,
            String extensions)
        {
            call.onNetBegin(traceId, authorization, extensions);
        }

        @Override
        void onNetRejected(
            long traceId,
            long authorization,
            int status,
            String challenge)
        {
            call.onNetRejected(traceId, authorization, status, challenge);
            doNetReset(traceId, authorization);
        }

        @Override
        void onNetCompleted(
            long traceId,
            long authorization)
        {
            call.onNetEnd(traceId, authorization);
        }

        @Override
        void onNetAborted(
            long traceId,
            long authorization)
        {
            call.onNetAbort(traceId, authorization);
        }

        @Override
        void onNetResetted(
            long traceId,
            long authorization)
        {
            call.onNetReset(traceId, authorization);
        }

        @Override
        void onNetWindowed(
            long traceId,
            long authorization)
        {
            call.flushAppWindow(traceId, authorization);
        }

        @Override
        void onDecodeParseError(
            long traceId,
            long authorization)
        {
            decoder = decodeIgnore;
            call.cleanupApp(traceId, authorization);
        }

        @Override
        int onDecodeResponseResult(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit,
            boolean endOfInput)
        {
            return resultDecoder.decode(this, traceId, authorization, buffer, offset, limit, endOfInput);
        }

        @Override
        void onDecodeResponseError(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int code = ERROR_CODE_BAD_GATEWAY;
            String message = ERROR_MESSAGE_BAD_GATEWAY;

            errorParser.wrap(buffer, offset, limit);
            int depth = 0;
            String key = null;
            while (errorParser.hasNext())
            {
                final JsonParser.Event event = errorParser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    key = depth == 1 ? errorParser.getString() : null;
                    break;
                case VALUE_NUMBER:
                    if ("code".equals(key))
                    {
                        code = errorParser.getInt();
                    }
                    break;
                case VALUE_STRING:
                    if ("message".equals(key))
                    {
                        message = errorParser.getString();
                    }
                    break;
                default:
                    break;
                }
            }
            errorParser.reset();

            call.doAppResetError(traceId, authorization, code, message);
            doNetReset(traceId, authorization);
        }
    }

    private final class HttpCardStream extends HttpStream
    {
        private final A2aCardConsumer consumer;
        private final A2aClientBindingConfig binding;
        private final String path;
        private final String fallbackPath;

        HttpCardStream(
            A2aCardConsumer consumer,
            A2aClientBindingConfig binding,
            long originId,
            long routedId,
            String path,
            String fallbackPath)
        {
            super(originId, routedId);
            this.consumer = consumer;
            this.binding = binding;
            this.path = path;
            this.fallbackPath = fallbackPath;
            this.decoder = decodeHold;
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization,
            int contentLength)
        {
            final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_GET))
                .headersItem(h -> h.name(HTTP_HEADER_SCHEME).value(binding.serverScheme))
                .headersItem(h -> h.name(HTTP_HEADER_AUTHORITY).value(binding.serverAuthority))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(path))
                .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_A2A_VERSION).value(binding.versions.get(0)))
                .build();

            doNetBegin(traceId, authorization, httpBeginEx);
            doEncodeRequestEnd(traceId, authorization);
        }

        @Override
        void onNetAccepted(
            long traceId,
            long authorization,
            String extensions)
        {
        }

        @Override
        void onNetRejected(
            long traceId,
            long authorization,
            int status,
            String challenge)
        {
            doNetReset(traceId, authorization);

            if (status == 404 && fallbackPath != null)
            {
                final HttpCardStream fallback = new HttpCardStream(consumer, binding, originId, routedId, fallbackPath, null);
                fallback.doEncodeRequestBegin(traceId, authorization, 0);
            }
            else
            {
                consumer.onCardRejected(traceId, authorization, status, challenge);
            }
        }

        @Override
        void onNetEnded(
            long traceId,
            long authorization)
        {
            final MutableDirectBufferEx buffer = decodeSlot != NO_SLOT ? decodePool.buffer(decodeSlot) : codecBuffer;
            final int limit = decodeSlot != NO_SLOT ? decodeSlotOffset : 0;

            if (limit > 0)
            {
                consumer.onCard(traceId, authorization, buffer, 0, limit);
            }
            else
            {
                consumer.onCardRejected(traceId, authorization, 0, null);
            }

            cleanupDecodeSlot();
        }

        @Override
        void onNetCompleted(
            long traceId,
            long authorization)
        {
        }

        @Override
        void onNetAborted(
            long traceId,
            long authorization)
        {
            consumer.onCardRejected(traceId, authorization, 0, null);
        }

        @Override
        void onNetResetted(
            long traceId,
            long authorization)
        {
            consumer.onCardRejected(traceId, authorization, 0, null);
        }

        @Override
        void onDecodeParseError(
            long traceId,
            long authorization)
        {
            decoder = decodeIgnore;
            doNetAbort(traceId, authorization);
            doNetReset(traceId, authorization);
            consumer.onCardRejected(traceId, authorization, 0, null);
        }
    }

    private final class HttpDeliverStream extends HttpStream
    {
        private final A2aDeliverStream delivery;
        private final String url;

        HttpDeliverStream(
            A2aDeliverStream delivery,
            String url)
        {
            super(delivery.binding.id, delivery.resolvedId);
            this.delivery = delivery;
            this.url = url;
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization,
            int contentLength)
        {
            final URI uri = URI.create(url);
            final String authority = String.format("%s:%d", uri.getHost(),
                uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80);
            final String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            final String token = delivery.token();
            final String authorizationHeader = delivery.authorizationOfPush();
            final String length = Integer.toString(contentLength);

            final HttpBeginExFW.Builder builder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_SCHEME).value(uri.getScheme()))
                .headersItem(h -> h.name(HTTP_HEADER_AUTHORITY).value(authority))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(path))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_A2A_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_A2A_VERSION).value(VERSION_CANONICAL));

            if (token != null)
            {
                builder.headersItem(h -> h.name(HTTP_HEADER_NOTIFICATION_TOKEN).value(token));
            }

            if (authorizationHeader != null)
            {
                builder.headersItem(h -> h.name(HTTP_HEADER_AUTHORIZATION).value(authorizationHeader));
            }

            doNetBegin(traceId, authorization,
                builder.headersItem(h -> h.name(HTTP_HEADER_CONTENT_LENGTH).value(length)).build());
        }

        @Override
        void onNetAccepted(
            long traceId,
            long authorization,
            String extensions)
        {
        }

        @Override
        void onNetRejected(
            long traceId,
            long authorization,
            int status,
            String challenge)
        {
            doNetReset(traceId, authorization);

            if (delivery.http == this)
            {
                delivery.onNetRejected(traceId, authorization, status, challenge);
            }
        }

        @Override
        void onNetCompleted(
            long traceId,
            long authorization)
        {
            if (delivery.http == this)
            {
                delivery.onNetEnd(traceId, authorization);
            }
        }

        @Override
        void onNetAborted(
            long traceId,
            long authorization)
        {
            if (delivery.http == this)
            {
                delivery.onNetAbort(traceId, authorization);
            }
        }

        @Override
        void onNetResetted(
            long traceId,
            long authorization)
        {
            if (delivery.http == this)
            {
                delivery.onNetReset(traceId, authorization);
            }
        }

        @Override
        void onNetWindowed(
            long traceId,
            long authorization)
        {
            delivery.flushAppWindow(traceId, authorization);
        }

        @Override
        void onDecodeParseError(
            long traceId,
            long authorization)
        {
            cleanupNet(traceId, authorization);
        }
    }

    private int decodeResultStart(
        HttpCallStream http,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean endOfInput)
    {
        final int kind = http.call.kind;
        final boolean wrapped = kind == KIND_MESSAGE_SEND || kind == KIND_MESSAGE_STREAM || kind == KIND_TASKS_SUBSCRIBE;
        final int peek = wrapped && http.dialect.verbatim() ? peekMessage(buffer, offset, limit) : PEEK_NO;

        int progress = offset;

        if (peek != PEEK_MORE || endOfInput)
        {
            http.resultDecoder = peek == PEEK_YES ? decodeResultStream : decodeResultBuffer;
            progress = http.resultDecoder.decode(http, traceId, authorization, buffer, offset, limit, endOfInput);
        }

        return progress;
    }

    private int decodeResultStream(
        HttpCallStream http,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean endOfInput)
    {
        final A2aDataExFW dataEx = http.resultDelivered == 0
            ? a2aDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .message(m -> m.contextId((String) null))
                .build()
            : null;

        final int progress = http.call.doAppData(traceId, authorization, dataEx, buffer, offset, limit);

        http.resultDelivered += progress - offset;

        if (endOfInput && progress == limit)
        {
            http.resultDelivered = 0;
            http.resultDecoder = decodeResultStart;
        }

        return progress;
    }

    private int decodeResultBuffer(
        HttpCallStream http,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean endOfInput)
    {
        int progress = offset;

        if (!endOfInput)
        {
            if (limit - offset >= decodeMax)
            {
                http.onDecodeParseError(traceId, authorization);
                progress = limit;
            }
        }
        else
        {
            try
            {
                DirectBufferEx result = buffer;
                int resultOffset = offset;
                int resultLimit = limit;

                if (!http.dialect.verbatim())
                {
                    final byte[] translated = http.dialect.resultFromWire(http.call.operation, readValue(buffer, offset, limit))
                        .toString().getBytes(StandardCharsets.UTF_8);
                    translateBuffer.putBytes(0, translated);
                    result = translateBuffer;
                    resultOffset = 0;
                    resultLimit = translated.length;
                }

                final boolean empty = resultLimit - resultOffset == JSON_NULL.length() &&
                    JSON_NULL.equals(result.getStringWithoutLengthUtf8(resultOffset, JSON_NULL.length()));

                final A2aDataExFW dataEx = !empty && http.resultDelivered == 0 && resultInspector.inspect(http.call.kind,
                    result, resultOffset, resultLimit)
                    ? dataExOf(resultInspector)
                    : null;

                final int delivered = empty
                    ? resultLimit
                    : http.call.doAppData(traceId, authorization, dataEx, result,
                        resultOffset + http.resultDelivered, resultLimit);

                http.resultDelivered = delivered - resultOffset;

                if (delivered == resultLimit)
                {
                    http.resultDelivered = 0;
                    http.resultDecoder = decodeResultStart;
                    progress = limit;
                }
            }
            catch (RuntimeException ex)
            {
                http.onDecodeParseError(traceId, authorization);
                progress = limit;
            }
        }

        return progress;
    }

    private int peekMessage(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int peek = PEEK_NO;
        int index = offset;

        if (index < limit && buffer.getByte(index) == '{')
        {
            index++;

            while (index < limit && Character.isWhitespace((char) buffer.getByte(index)))
            {
                index++;
            }

            if (limit - index < PEEK_MESSAGE.length)
            {
                peek = PEEK_MORE;
            }
            else
            {
                boolean match = true;
                for (int i = 0; match && i < PEEK_MESSAGE.length; i++)
                {
                    match = buffer.getByte(index + i) == PEEK_MESSAGE[i];
                }
                peek = match ? PEEK_YES : PEEK_NO;
            }
        }

        return peek;
    }

    private A2aDataExFW dataExOf(
        A2aResultInspector inspector)
    {
        final A2aDataExFW.Builder builder = a2aDataExRW.wrap(extBuffer, 0, extBuffer.capacity()).typeId(a2aTypeId);
        final String taskId = inspector.taskId;
        final String contextId = inspector.contextId;
        final A2aTaskState taskState = taskStateOf(inspector.state);

        A2aDataExFW dataEx = null;

        switch (inspector.kind)
        {
        case A2aDataExFW.KIND_MESSAGE:
            dataEx = builder.message(m -> m.contextId((String) null)).build();
            break;
        case A2aDataExFW.KIND_TASK:
            dataEx = taskId != null
                ? builder.task(t -> t.taskId(taskId).contextId(contextId).state(s -> s.set(taskState))).build()
                : null;
            break;
        case A2aDataExFW.KIND_STATUS_UPDATE:
            dataEx = taskId != null
                ? builder.statusUpdate(t -> t.taskId(taskId).contextId(contextId).state(s -> s.set(taskState))).build()
                : null;
            break;
        case A2aDataExFW.KIND_ARTIFACT_UPDATE:
            final String artifactId = inspector.artifactId;
            dataEx = taskId != null
                ? builder.artifactUpdate(t -> t.taskId(taskId).contextId(contextId).artifactId(artifactId)).build()
                : null;
            break;
        default:
            break;
        }

        return dataEx;
    }

    private static A2aTaskState taskStateOf(
        String state)
    {
        A2aTaskState taskState = A2aTaskState.UNSPECIFIED;

        try
        {
            taskState = state != null ? A2aTaskState.valueOf(state) : A2aTaskState.UNSPECIFIED;
        }
        catch (IllegalArgumentException ex)
        {
            taskState = A2aTaskState.UNSPECIFIED;
        }

        return taskState;
    }

    private static String headerOf(
        HttpBeginExFW httpBeginEx,
        String name)
    {
        return Optional.ofNullable(httpBeginEx.headers().matchFirst(h -> name.equals(h.name().asString())))
            .map(HttpHeaderFW::value)
            .map(String16FW::asString)
            .orElse(null);
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        HttpBeginExFW extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        final MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        if (receiver != null)
        {
            receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        return receiver;
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW.Builder builder = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final BeginFW begin = builder.build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBufferEx payload,
        int offset,
        int length,
        Flyweight extension)
    {
        final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload, offset, length);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final DataFW data = builder.build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW.Builder builder = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final ResetFW reset = builder.build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding)
    {
        doWindow(receiver, originId, routedId, streamId, sequence, acknowledge, maximum,
            traceId, authorization, budgetId, padding, 0);
    }

    private void doWindow(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding,
        int capabilities)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .capabilities(capabilities)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
