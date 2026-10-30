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

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aAuthorizationResult;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aRouteConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aDialect;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aError;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aOperation;
import io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aRequestParams;
import io.aklivity.zilla.runtime.binding.a2a.internal.push.A2aPushCapabilities;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBearerResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aErrorResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.HttpResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.agrona.io.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class A2aServerFactory implements A2aStreamFactory
{
    private static final Runnable NOOP = () ->
    {
    };

    private static final String HTTP_TYPE_NAME = "http";
    private static final String A2A_TYPE_NAME = "a2a";

    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final int EVENT_RECORD_HEADER = 3 * Integer.BYTES;

    private static final String HTTP_METHOD_POST = "POST";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_PATH = ":path";
    private static final String HTTP_HEADER_SCHEME = ":scheme";
    private static final String HTTP_HEADER_AUTHORITY = ":authority";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_CONTENT_LENGTH = "content-length";
    private static final String HTTP_HEADER_AUTHORIZATION = "authorization";
    private static final String HTTP_HEADER_WWW_AUTHENTICATE = "www-authenticate";
    private static final String HTTP_HEADER_A2A_VERSION = "a2a-version";
    private static final String HTTP_HEADER_A2A_EXTENSIONS = "a2a-extensions";
    private static final String HTTP_HEADER_NOTIFICATION_TOKEN = "x-a2a-notification-token";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String STATUS_200 = "200";
    private static final String STATUS_401 = "401";
    private static final String STATUS_403 = "403";
    private static final String STATUS_404 = "404";
    private static final String STATUS_405 = "405";
    private static final String STATUS_502 = "502";

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String JSON_RPC_RESULT_PREFIX = "{\"jsonrpc\":\"2.0\",\"id\":";
    private static final String JSON_RPC_RESULT_MIDDLE = ",\"result\":";
    private static final String JSON_NULL = "null";
    private static final String JSON_RPC_ID_NULL = "null";

    private static final int STATUS_RANGE_MIN = 400;
    private static final int STATUS_RANGE_MAX = 599;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final A2aBeginExFW a2aBeginExRO = new A2aBeginExFW();
    private final A2aResetExFW a2aResetExRO = new A2aResetExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpResetExFW.Builder httpResetExRW = new HttpResetExFW.Builder();
    private final A2aBeginExFW.Builder a2aBeginExRW = new A2aBeginExFW.Builder();

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx codecBuffer;
    private final MutableDirectBufferEx eventBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int a2aTypeId;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final int decodeMax;
    private final int encodeMax;

    private int eventReserved;

    private final A2aServerDecoder decodeJsonRpc = this::decodeJsonRpc;
    private final A2aServerDecoder decodeJsonRpcStart = this::decodeJsonRpcStart;
    private final A2aServerDecoder decodeJsonRpcNext = this::decodeJsonRpcNext;
    private final A2aServerDecoder decodeJsonRpcVersion = this::decodeJsonRpcVersion;
    private final A2aServerDecoder decodeJsonRpcId = this::decodeJsonRpcId;
    private final A2aServerDecoder decodeJsonRpcMethod = this::decodeJsonRpcMethod;
    private final A2aServerDecoder decodeJsonRpcParamsStart = this::decodeJsonRpcParamsStart;
    private final A2aServerDecoder decodeJsonRpcParamsStream = this::decodeJsonRpcParamsStream;
    private final A2aServerDecoder decodeJsonRpcParamsBuffer = this::decodeJsonRpcParamsBuffer;
    private final A2aServerDecoder decodeJsonRpcParamsEnd = this::decodeJsonRpcParamsEnd;
    private final A2aServerDecoder decodeJsonRpcEnd = this::decodeJsonRpcEnd;
    private final A2aServerDecoder decodePushBody = this::decodePushBody;
    private final A2aServerDecoder decodeDone = this::decodeIgnore;
    private final A2aServerDecoder decodeIgnore = this::decodeIgnore;

    private final A2aReplyDecoder decodeReplyJson = this::decodeReplyJson;
    private final A2aReplyDecoder decodeReplyEventStart = this::decodeReplyEventStart;
    private final A2aReplyDecoder decodeReplyEvent = this::decodeReplyEvent;
    private final A2aReplyDecoder decodeReplyTranslated = this::decodeReplyTranslated;
    private final A2aReplyDecoder decodeReplyIgnore = this::decodeReplyIgnore;

    private final A2aReplyEnd endReplyJson = this::endReplyJson;
    private final A2aReplyEnd endReplyEvents = this::endReplyEvents;
    private final A2aReplyEnd endReplyTranslated = this::endReplyTranslated;
    private final A2aReplyEnd endReplyPush = this::endReplyPush;

    private final A2aServerStart startRpc = this::startRpc;
    private final A2aServerStart startCard = this::startCard;
    private final A2aServerStart startPush = this::startPush;

    private final A2aConfiguration config;
    private final Long2ObjectHashMap<A2aBindingConfig> bindings;

    public A2aServerFactory(
        A2aConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.eventBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity() + EVENT_RECORD_HEADER + 1024]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.a2aTypeId = context.supplyTypeId(A2A_TYPE_NAME);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new A2aBindingConfig(binding, context));
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

        MessageConsumer newStream = null;

        final A2aBindingConfig binding = bindings.get(routedId);
        final A2aRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        if (route != null)
        {
            final long traceId = begin.traceId();
            final HttpBeginExFW httpBeginEx = begin.extension().get(httpBeginExRO::wrap);

            final String method = header(httpBeginEx, HTTP_HEADER_METHOD);
            final String path = pathOf(header(httpBeginEx, HTTP_HEADER_PATH));
            final String version = header(httpBeginEx, HTTP_HEADER_A2A_VERSION);
            final int contentLength = Optional.ofNullable(header(httpBeginEx, HTTP_HEADER_CONTENT_LENGTH))
                .map(Integer::parseInt)
                .orElse(-1);

            if (HTTP_METHOD_POST.equals(method) && binding.isPushPath(path))
            {
                final String credentials = header(httpBeginEx, HTTP_HEADER_AUTHORIZATION);
                final int separator = credentials != null ? credentials.indexOf(' ') : -1;
                final A2aServerRequest request = new A2aServerRequest(
                    null,
                    null,
                    version,
                    contentLength,
                    null,
                    binding.pushConfigId(path),
                    header(httpBeginEx, HTTP_HEADER_NOTIFICATION_TOKEN),
                    separator > 0 ? credentials.substring(0, separator) : null,
                    separator > 0 ? credentials.substring(separator + 1) : null,
                    null);

                newStream = new A2aServer(sender, originId, routedId, initialId, authorization, NOOP,
                    route.id, binding, request, startPush, decodePushBody)::onNetMessage;
            }
            else if (HTTP_METHOD_POST.equals(method))
            {
                final A2aTarget target = targetOf(binding, path);

                if (!target.matched())
                {
                    newStream = new A2aRejectHandler(sender, STATUS_404)::onNetBegin;
                }
                else
                {
                    final A2aAuthorizationResult authResult =
                        binding.authorize(traceId, routedId, initialId, authorization, httpBeginEx);

                    if (authResult.error() != null)
                    {
                        newStream = new A2aBearerRejectHandler(sender, authResult.error())::onNetMessage;
                    }
                    else
                    {
                        final A2aServerRequest request = new A2aServerRequest(
                            target.agent(),
                            header(httpBeginEx, HTTP_HEADER_A2A_EXTENSIONS),
                            version,
                            contentLength,
                            urlOf(binding, httpBeginEx, target, path),
                            null,
                            null,
                            null,
                            null,
                            null);

                        newStream = new A2aServer(sender, originId, routedId, initialId, authResult.authorization(),
                            authResult.deauthorize(), route.id, binding, request, startRpc, decodeJsonRpc)::onNetMessage;
                    }
                }
            }
            else if (HTTP_METHOD_GET.equals(method) && A2aBindingConfig.isCardPath(path))
            {
                final String prefix = A2aBindingConfig.cardPrefix(path);
                final A2aTarget target = prefix.isEmpty() ? new A2aTarget(true, null) : targetOf(binding, prefix);
                final A2aDialect dialect = A2aBindingConfig.isLegacyCardPath(path)
                    ? binding.dialects.legacy()
                    : binding.dialects.resolveCard(version);

                if (!target.matched() || dialect == null)
                {
                    newStream = new A2aRejectHandler(sender, STATUS_404)::onNetBegin;
                }
                else
                {
                    final String url = urlOf(binding, httpBeginEx, target, prefix);

                    final A2aServerRequest request = new A2aServerRequest(
                        target.agent(), null, version, -1, url, null, null, null, null, dialect);

                    newStream = new A2aServer(sender, originId, routedId, initialId, authorization, NOOP,
                        route.id, binding, request, startCard, decodeDone)::onNetMessage;
                }
            }
            else
            {
                newStream = new A2aRejectHandler(sender, method == null || HTTP_METHOD_GET.equals(method) ||
                    HTTP_METHOD_POST.equals(method) ? STATUS_404 : STATUS_405)::onNetBegin;
            }
        }

        return newStream;
    }

    private static String header(
        HttpBeginExFW httpBeginEx,
        String name)
    {
        return Optional.ofNullable(httpBeginEx.headers().matchFirst(h -> name.equals(h.name().asString())))
            .map(h -> h.value().asString())
            .orElse(null);
    }

    private static String urlOf(
        A2aBindingConfig binding,
        HttpBeginExFW httpBeginEx,
        A2aTarget target,
        String prefix)
    {
        return binding.url != null
            ? target.agent() != null ? binding.url + "/" + target.agent() : binding.url
            : header(httpBeginEx, HTTP_HEADER_SCHEME) + "://" + header(httpBeginEx, HTTP_HEADER_AUTHORITY) + prefix;
    }

    private static String pathOf(
        String target)
    {
        final int query = target != null ? target.indexOf('?') : -1;
        return query >= 0 ? target.substring(0, query) : target;
    }

    private static A2aTarget targetOf(
        A2aBindingConfig binding,
        String path)
    {
        A2aTarget target = new A2aTarget(false, null);

        if (binding.basePath == null)
        {
            target = new A2aTarget(true, null);
        }
        else
        {
            String normalized = path != null ? path : "";
            while (normalized.endsWith("/"))
            {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            final String base = binding.basePath;
            if (normalized.equals(base))
            {
                target = new A2aTarget(true, null);
            }
            else if (normalized.startsWith(base + "/"))
            {
                final String agent = normalized.substring(base.length() + 1);
                target = new A2aTarget(!agent.isEmpty() && agent.indexOf('/') < 0, agent);
            }
        }

        return target;
    }

    private record A2aTarget(
        boolean matched,
        String agent)
    {
    }

    private record A2aServerRequest(
        String agent,
        String extensions,
        String version,
        int contentLength,
        String url,
        String configId,
        String token,
        String scheme,
        String credentials,
        A2aDialect dialect)
    {
    }

    @FunctionalInterface
    private interface A2aServerDecoder
    {
        int decode(
            A2aServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    @FunctionalInterface
    private interface A2aServerStart
    {
        void start(
            A2aServer server,
            long traceId,
            long authorization);
    }

    @FunctionalInterface
    private interface A2aReplyDecoder
    {
        int decode(
            A2aStream stream,
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit);
    }

    @FunctionalInterface
    private interface A2aReplyEnd
    {
        void end(
            A2aStream stream,
            long traceId,
            long authorization);
    }

    private int decodeJsonRpc(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        server.decodedId = null;
        server.decodableJson = JsonEx.createParser(Map.of());
        server.decodableJson.wrap(buffer, progress, limit);
        server.decoder = decodeJsonRpcStart;
        server.decodedParserProgress = offset - progress;

        return offset + (int) (server.decodableJson.getLocation().getStreamOffset() - server.decodedParserProgress);
    }

    private int decodeJsonRpcStart(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            if (parser.next() != JsonParser.Event.START_OBJECT)
            {
                server.onDecodeParseError(traceId, authorization);
                break decode;
            }

            server.decoder = decodeJsonRpcNext;

            progress = offset + (int) (parser.getLocation().getStreamOffset() - server.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcNext(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case KEY_NAME:
                switch (parser.getString())
                {
                case "jsonrpc":
                    server.decoder = decodeJsonRpcVersion;
                    break;
                case "id":
                    server.decoder = decodeJsonRpcId;
                    break;
                case "method":
                    server.decoder = decodeJsonRpcMethod;
                    break;
                case "params":
                    if (server.operation == null)
                    {
                        server.onDecodeInvalidRequest(traceId, authorization);
                        break decode;
                    }
                    server.decoder = decodeJsonRpcParamsStart;
                    break;
                default:
                    server.onDecodeInvalidRequest(traceId, authorization);
                    break decode;
                }
                break;
            case END_OBJECT:
                server.decoder = decodeJsonRpcEnd;
                break;
            default:
                server.onDecodeParseError(traceId, authorization);
                break decode;
            }

            progress = offset + (int) (parser.getLocation().getStreamOffset() - server.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcVersion(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            if (parser.next() != JsonParser.Event.VALUE_STRING || !JSON_RPC_VERSION.equals(parser.getString()))
            {
                server.onDecodeInvalidRequest(traceId, authorization);
                break decode;
            }

            server.decoder = decodeJsonRpcNext;

            progress = offset + (int) (parser.getLocation().getStreamOffset() - server.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcId(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case VALUE_STRING:
                server.decodedId = quote(parser.getString());
                break;
            case VALUE_NUMBER:
                server.decodedId = parser.getString();
                break;
            case VALUE_NULL:
                server.decodedId = JSON_RPC_ID_NULL;
                break;
            default:
                server.onDecodeInvalidRequest(traceId, authorization);
                break decode;
            }

            server.decoder = decodeJsonRpcNext;

            progress = offset + (int) (parser.getLocation().getStreamOffset() - server.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcMethod(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            if (parser.next() != JsonParser.Event.VALUE_STRING)
            {
                server.onDecodeInvalidRequest(traceId, authorization);
                break decode;
            }

            final String method = parser.getString();
            final A2aDialect dialect = server.binding.dialects.resolve(server.request.version(), method);
            final A2aOperation operation = dialect != null ? dialect.operation(method) : null;

            if (dialect == null)
            {
                server.onDecodeVersionNotSupported(traceId, authorization);
                break decode;
            }
            else if (operation == null)
            {
                server.onDecodeMethodNotFound(traceId, authorization);
                break decode;
            }

            server.dialect = dialect;
            server.operation = operation;
            server.decoder = decodeJsonRpcNext;

            progress = offset + (int) (parser.getLocation().getStreamOffset() - server.decodedParserProgress);
        }

        return progress;
    }

    private int decodeJsonRpcParamsStart(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            if (parser.next() != JsonParser.Event.START_OBJECT)
            {
                server.onDecodeInvalidParams(traceId, authorization);
                break decode;
            }

            server.decodedParamsProgress = (int) parser.getLocation().getStreamOffset() - 1;
            server.decodedSkipObjectDepth = 1;

            if (server.operation.message() && server.dialect.verbatim())
            {
                server.onDecodeMessageParams(traceId, authorization);
                server.decoder = decodeJsonRpcParamsStream;
            }
            else
            {
                server.decoder = decodeJsonRpcParamsBuffer;
            }

            progress = offset + server.decodedParamsProgress - server.decodedParserProgress;
        }

        return progress;
    }

    private int decodeJsonRpcParamsStream(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_OBJECT)
            {
                server.decodedSkipObjectDepth++;
            }
            else if (event == JsonParser.Event.END_OBJECT)
            {
                server.decodedSkipObjectDepth--;
                if (server.decodedSkipObjectDepth == 0)
                {
                    server.decoder = decodeJsonRpcParamsEnd;
                    break;
                }
            }
        }

        server.decodeParams(traceId, authorization, buffer, offset, (int) parser.getLocation().getStreamOffset());

        return offset + server.decodedParamsProgress - server.decodedParserProgress;
    }

    private int decodeJsonRpcParamsEnd(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;
        final int decodedParamsLimit = (int) parser.getLocation().getStreamOffset();

        server.decodeParams(traceId, authorization, buffer, offset, decodedParamsLimit);

        if (server.decodedParamsProgress == decodedParamsLimit)
        {
            server.decoder = decodeJsonRpcNext;
        }

        return offset + server.decodedParamsProgress - server.decodedParserProgress;
    }

    private int decodeJsonRpcParamsBuffer(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        boolean complete = false;
        while (!complete && parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.START_OBJECT)
            {
                server.decodedSkipObjectDepth++;
            }
            else if (event == JsonParser.Event.END_OBJECT)
            {
                server.decodedSkipObjectDepth--;
                complete = server.decodedSkipObjectDepth == 0;
            }
        }

        progress = offset + server.decodedParamsProgress - server.decodedParserProgress;

        if (complete)
        {
            final int paramsLimit = (int) parser.getLocation().getStreamOffset();
            final int paramsStart = progress;
            final int paramsEnd = offset + paramsLimit - server.decodedParserProgress;

            server.decodedParamsProgress = paramsLimit;
            server.decoder = decodeJsonRpcNext;
            server.onDecodeParams(traceId, authorization, buffer, paramsStart, paramsEnd);

            progress = paramsEnd;
        }
        else if (limit - progress >= decodeMax)
        {
            server.onDecodeInvalidRequest(traceId, authorization);
        }

        return progress;
    }

    private int decodeJsonRpcEnd(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        final JsonParser parser = server.decodableJson;

        decode:
        {
            if (parser.hasNext())
            {
                server.onDecodeParseError(traceId, authorization);
                break decode;
            }

            parser.close();

            if (server.operation == null || server.decodedId == null)
            {
                server.onDecodeInvalidRequest(traceId, authorization);
                break decode;
            }

            server.decoder = decodeDone;

            if (server.stream == null)
            {
                server.onDecodeOperation(traceId, authorization, null, null, 0, 0);
            }

            progress = offset + (int) (parser.getLocation().getStreamOffset() - server.decodedParserProgress);
        }

        return progress;
    }

    private int decodePushBody(
        A2aServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return server.stream.doAppData(traceId, authorization, buffer, progress, limit);
    }

    private int decodeIgnore(
        A2aServer server,
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

    private final class A2aServer
    {
        private final MessageConsumer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long resolvedId;
        private final A2aBindingConfig binding;
        private final A2aServerRequest request;
        private final A2aServerStart start;
        private Runnable deauthorize;

        private A2aDialect dialect;
        private A2aOperation operation;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;

        private int state;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;
        private int decodedParserProgress;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;

        private A2aServerDecoder decoder;

        private JsonParserEx decodableJson;
        private String decodedId;
        private String activatedExtensions;
        private int decodedParamsProgress;
        private int decodedSkipObjectDepth;

        private A2aStream stream;

        private A2aServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            Runnable deauthorize,
            long resolvedId,
            A2aBindingConfig binding,
            A2aServerRequest request,
            A2aServerStart start,
            A2aServerDecoder decoder)
        {
            this.net = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.deauthorize = deauthorize;
            this.resolvedId = resolvedId;
            this.binding = binding;
            this.request = request;
            this.start = start;
            this.decoder = decoder;
            this.dialect = request.dialect();
            this.initialMax = decodeMax;
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

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();

            state = A2aState.openedInitial(state);

            doNetWindow(traceId, authorization, 0, 0);

            start.start(this, traceId, authorization);
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();
                int reserved = data.reserved();
                DirectBufferEx buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
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
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = A2aState.closingInitial(state);

            doDecodeEnd(traceId, authorization);
        }

        private void doDecodeEnd(
            long traceId,
            long authorization)
        {
            final boolean forwarding = decodeSlot != NO_SLOT &&
                (decoder == decodeJsonRpcParamsEnd || decoder == decodePushBody);

            if (A2aState.initialClosing(state) && !A2aState.initialClosed(state) && !forwarding)
            {
                if (decoder == decodePushBody)
                {
                    decoder = decodeDone;
                }

                if (decoder != decodeDone && decoder != decodeIgnore)
                {
                    onDecodeParseError(traceId, authorization);
                }

                cleanupDecodeSlot();
                state = A2aState.closedInitial(state);

                if (stream != null)
                {
                    stream.doAppEnd(traceId, authorization);
                }
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = A2aState.closedInitial(state);

            if (stream != null)
            {
                stream.doAppAbort(traceId, authorization);
            }
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNet(traceId, authorization);
            }
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;

                encodeNet(encodeSlotTraceId, budgetId, buffer, 0, limit);
            }

            if (stream != null)
            {
                stream.flushAppWindow(traceId, authorization, 0L, 0, encodeSlotOffset, encodeMax);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            if (stream != null)
            {
                stream.doAppReset(traceId, authorization);
                stream.doAppEnd(traceId, authorization);
            }

            cleanupNet(traceId, authorization);
        }

        private void onDecodeParseError(
            long traceId,
            long authorization)
        {
            doEncodeResponseError(traceId, authorization, A2aError.PARSE_ERROR.code(), null, JSON_RPC_ID_NULL);
        }

        private void onDecodeInvalidRequest(
            long traceId,
            long authorization)
        {
            doEncodeResponseError(traceId, authorization, A2aError.INVALID_REQUEST.code(), null, idOrNull());
        }

        private void onDecodeInvalidParams(
            long traceId,
            long authorization)
        {
            doEncodeResponseError(traceId, authorization, A2aError.INVALID_PARAMS.code(), null, idOrNull());
        }

        private void onDecodeMethodNotFound(
            long traceId,
            long authorization)
        {
            doEncodeResponseError(traceId, authorization, A2aError.METHOD_NOT_FOUND.code(), null, idOrNull());
        }

        private void onDecodeVersionNotSupported(
            long traceId,
            long authorization)
        {
            doEncodeResponseError(traceId, authorization, A2aError.VERSION_NOT_SUPPORTED.code(), null, idOrNull());
        }

        private String idOrNull()
        {
            return decodedId != null ? decodedId : JSON_RPC_ID_NULL;
        }

        private void onDecodeMessageParams(
            long traceId,
            long authorization)
        {
            final int contentLength = request.contentLength() >= 0
                ? request.contentLength() - decodedParamsProgress - 1
                : -1;

            stream = new A2aStream(this);
            stream.doAppBegin(traceId, authorization, beginEx(null, contentLength));
        }

        private void decodeParams(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int parsed)
        {
            if (parsed > decodedParamsProgress)
            {
                final int decodedOffset = offset + decodedParamsProgress - decodedParserProgress;
                final int decodedLimit = offset + parsed - decodedParserProgress;
                final int decodedProgress = stream.doAppData(traceId, authorization, buffer, decodedOffset, decodedLimit);

                decodedParamsProgress = decodedProgress - offset + decodedParserProgress;
            }
        }

        private void onDecodeParams(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int start,
            int end)
        {
            JsonObject params = null;

            try
            {
                final DirectBufferInputStreamEx input = new DirectBufferInputStreamEx();
                input.wrap(buffer, start, end - start);
                params = Json.createReader(input).readObject();
            }
            catch (JsonException | IllegalStateException ex)
            {
                onDecodeInvalidParams(traceId, authorization);
            }

            if (params != null)
            {
                onDecodeOperation(traceId, authorization, params, buffer, start, end);
            }
        }

        private void onDecodeOperation(
            long traceId,
            long authorization,
            JsonObject params,
            DirectBufferEx buffer,
            int start,
            int end)
        {
            final A2aRequestParams routed = params != null || !operation.message()
                ? dialect.request(operation, params)
                : null;

            if (routed == null)
            {
                onDecodeInvalidParams(traceId, authorization);
            }
            else
            {
                byte[] body = null;
                if (operation.content() && params != null)
                {
                    if (routed.body() != null)
                    {
                        body = routed.body().toString().getBytes(UTF_8);
                    }
                    else
                    {
                        body = new byte[end - start];
                        buffer.getBytes(start, body);
                    }
                }

                stream = new A2aStream(this);
                stream.pending = body != null ? new UnsafeBufferEx(body) : null;
                stream.doAppBegin(traceId, authorization, beginEx(routed, body != null ? body.length : -1));
            }
        }

        private A2aBeginExFW beginEx(
            A2aRequestParams routed,
            int contentLength)
        {
            final String agent = request.agent();
            final String extensions = request.extensions();

            final A2aBeginExFW.Builder builder = a2aBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity()).typeId(a2aTypeId);

            switch (operation)
            {
            case MESSAGE_SEND:
                builder.messageSend(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.contentLength(contentLength);
                });
                break;
            case MESSAGE_STREAM:
                builder.messageStream(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.contentLength(contentLength);
                });
                break;
            case TASKS_GET:
                builder.tasksGet(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(routed.taskId()).historyLength(routed.historyLength());
                });
                break;
            case TASKS_LIST:
                builder.tasksList(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    ifPresent(routed.contextId(), b::contextId);
                    b.contentLength(contentLength);
                });
                break;
            case TASKS_CANCEL:
                builder.tasksCancel(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(routed.taskId());
                });
                break;
            case TASKS_SUBSCRIBE:
                builder.tasksSubscribe(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(routed.taskId());
                });
                break;
            case PUSH_CONFIG_CREATE:
                builder.pushConfigCreate(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(routed.taskId()).contentLength(contentLength);
                });
                break;
            case PUSH_CONFIG_GET:
                builder.pushConfigGet(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(routed.taskId()).configId(routed.configId());
                });
                break;
            case PUSH_CONFIG_LIST:
                builder.pushConfigList(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(routed.taskId());
                });
                break;
            case PUSH_CONFIG_DELETE:
                builder.pushConfigDelete(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                    b.taskId(routed.taskId()).configId(routed.configId());
                });
                break;
            default:
                builder.extendedAgentCard(b ->
                {
                    ifPresent(agent, b::agent);
                    ifPresent(extensions, b::extensions);
                });
                break;
            }

            return builder.build();
        }

        private void doEncodeResponseError(
            long traceId,
            long authorization,
            int code,
            String message,
            String id)
        {
            decoder = decodeIgnore;

            if (!A2aState.replyOpening(state))
            {
                doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_JSON);

                final String reason = A2aError.reason(code);
                final String data = reason != null
                    ? ",\"data\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"%s\",\"domain\":\"%s\"}]"
                        .formatted(reason, A2aError.DOMAIN)
                    : "";
                final byte[] body = "{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":%d,\"message\":%s%s}}"
                    .formatted(id, code, quote(message != null ? message : A2aError.message(code)), data)
                    .getBytes(UTF_8);

                doNetData(traceId, authorization, new UnsafeBufferEx(body), 0, body.length);
                doEncodeResponseEnd(traceId, authorization);
            }
            else
            {
                doNetAbort(traceId, authorization);
            }

            if (stream != null)
            {
                stream.doAppAbort(traceId, authorization);
            }
        }

        private void doEncodeResponseStatus(
            long traceId,
            long authorization,
            String status)
        {
            decoder = decodeIgnore;

            if (!A2aState.replyOpening(state))
            {
                doNetBegin(traceId, authorization, httpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                    .build());
                doEncodeResponseEnd(traceId, authorization);
            }
            else
            {
                doNetAbort(traceId, authorization);
            }
        }

        private void doEncodeResponseBearer(
            long traceId,
            long authorization,
            A2aBearerResetExFW bearer)
        {
            decoder = decodeIgnore;

            final A2aBearerError error = bearer.error().get();
            final String status = error == A2aBearerError.INSUFFICIENT_SCOPE ? STATUS_403 : STATUS_401;
            final String wwwAuthenticate = bearerChallenge(bearer.realm().asString(), bearer.scopes().asString(), error);

            doNetBegin(traceId, authorization, httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                .headersItem(h -> h.name(HTTP_HEADER_WWW_AUTHENTICATE).value(wwwAuthenticate))
                .build());
            doEncodeResponseEnd(traceId, authorization);
        }

        private void doEncodeResponseBegin(
            long traceId,
            long authorization,
            String contentType)
        {
            if (!A2aState.replyOpening(state))
            {
                if (CONTENT_TYPE_EVENT_STREAM.equals(contentType))
                {
                    state = A2aState.framingReply(state);
                }

                final HttpBeginExFW.Builder builder = httpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(contentType));

                if (activatedExtensions != null)
                {
                    builder.headersItem(h -> h.name(HTTP_HEADER_A2A_EXTENSIONS).value(activatedExtensions));
                }

                doNetBegin(traceId, authorization, builder.build());
            }
        }

        private void doEncodeResponseEnd(
            long traceId,
            long authorization)
        {
            state = A2aState.closingReply(state);

            if (encodeSlot == NO_SLOT)
            {
                doNetEnd(traceId, authorization);
            }
        }

        private byte[] resultPreamble()
        {
            return (JSON_RPC_RESULT_PREFIX + idOrNull() + JSON_RPC_RESULT_MIDDLE).getBytes(UTF_8);
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!A2aState.replyOpening(state))
            {
                doBegin(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization, 0,
                    extension);

                state = A2aState.openingReply(state);
            }
        }

        private void doNetData(
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

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, buffer, offset, limit);
        }

        private void doNetBytes(
            long traceId,
            long authorization,
            byte[] bytes)
        {
            doNetData(traceId, authorization, new UnsafeBufferEx(bytes), 0, bytes.length);
        }

        private void doNetEvent(
            long traceId,
            long authorization,
            int flags,
            DirectBufferEx payload,
            int payloadOffset,
            int payloadLength,
            boolean preamble,
            boolean postamble)
        {
            int progress = EVENT_RECORD_HEADER;

            if (preamble)
            {
                final byte[] prefix = resultPreamble();
                eventBuffer.putBytes(progress, prefix);
                progress += prefix.length;
            }

            if (payloadLength > 0)
            {
                eventBuffer.putBytes(progress, payload, payloadOffset, payloadLength);
                progress += payloadLength;
            }

            if (postamble)
            {
                eventBuffer.putByte(progress, (byte) '}');
                progress++;
            }

            eventBuffer.putInt(0, flags);
            eventBuffer.putInt(Integer.BYTES, 0);
            eventBuffer.putInt(2 * Integer.BYTES, progress - EVENT_RECORD_HEADER);

            doNetData(traceId, authorization, eventBuffer, 0, progress);
        }

        private void doEncodeResponseAbort(
            long traceId,
            long authorization)
        {
            if (encodeSlot == NO_SLOT)
            {
                doNetAbort(traceId, authorization);
            }
            else
            {
                state = A2aState.abortingReply(state);
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doEnd(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }

            cleanupEncodeSlot();
            onNetClosed();
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyOpening(state) && !A2aState.replyClosed(state))
            {
                doNetBegin(traceId, authorization, httpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_502))
                    .build());
            }

            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                doAbort(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization);
            }

            cleanupEncodeSlot();
            onNetClosed();
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = A2aState.openedInitial(state);

            doWindow(net, originId, routedId, initialId,
                initialSeq, initialAck, decodeMax - decodeSlotReserved, traceId, authorization, budgetId, padding,
                FRAMING_CAPABILITIES_MASK);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doReset(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization, emptyRO);
            }

            onNetClosed();
        }

        private void onNetClosed()
        {
            deauthorize.run();
            deauthorize = NOOP;
        }

        private void flushNetWindow(
            long traceId,
            long authorization,
            long budgetId)
        {
            final long initialAckMax = Math.max(initialSeq - decodeSlotReserved, initialAck);
            if (initialAckMax > initialAck || !A2aState.initialOpened(state))
            {
                initialAck = initialAckMax;
                assert initialAck <= initialSeq;

                doNetWindow(traceId, authorization, budgetId, 0);
            }
        }

        private void decodeNet(
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
            A2aServerDecoder previous = null;
            int progress = offset;

            try
            {
                while (progress <= limit && previous != decoder)
                {
                    previous = decoder;
                    progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, offset, progress, limit);
                }
            }
            catch (JsonException | IllegalStateException ex)
            {
                onDecodeParseError(traceId, authorization);
                progress = limit;
            }

            decodedParserProgress += progress - offset;

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
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

            doDecodeEnd(traceId, authorization);
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int length;

            if (A2aState.replyFraming(state))
            {
                length = doDataEvents(net, originId, routedId, replyId, replySeq, replyAck, replyMax, replyPad, replyBud,
                    traceId, authorization, (MutableDirectBufferEx) buffer, offset, limit);
                replySeq += eventReserved;
            }
            else
            {
                final int replyWin = replyMax - (int) (replySeq - replyAck);
                length = Math.max(Math.min(replyWin - replyPad, maxLength), 0);

                if (length > 0)
                {
                    final int reserved = length + replyPad;

                    doData(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                        COMPLETE, replyBud, reserved, buffer, offset, length);

                    replySeq += reserved;
                }
            }

            assert replySeq <= replyAck + replyMax :
                String.format("%d <= %d + %d", replySeq, replyAck, replyMax);

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(replyId);
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
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (A2aState.replyAborting(state))
                {
                    doNetAbort(traceId, authorization);
                }
                else if (A2aState.replyClosing(state))
                {
                    doNetEnd(traceId, authorization);
                }
            }

            if (stream != null)
            {
                stream.flushAppWindow(traceId, authorization, 0L, 0, encodeSlotOffset, encodeMax);
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            decoder = decodeIgnore;
            cleanupDecodeSlot();
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);

            if (stream != null)
            {
                stream.doAppAbort(traceId, authorization);
                stream.doAppReset(traceId, authorization);
            }
        }
    }

    private void startRpc(
        A2aServer server,
        long traceId,
        long authorization)
    {
    }

    private void startCard(
        A2aServer server,
        long traceId,
        long authorization)
    {
        final String agent = server.request.agent();

        server.stream = new A2aStream(server);
        server.stream.doAppBegin(traceId, authorization, a2aBeginExRW
            .wrap(codecBuffer, 0, codecBuffer.capacity())
            .typeId(a2aTypeId)
            .agentCard(b -> ifPresent(agent, b::agent))
            .build());
    }

    private void startPush(
        A2aServer server,
        long traceId,
        long authorization)
    {
        final A2aServerRequest request = server.request;

        server.stream = new A2aStream(server);
        server.stream.doAppBegin(traceId, authorization, a2aBeginExRW
            .wrap(codecBuffer, 0, codecBuffer.capacity())
            .typeId(a2aTypeId)
            .pushNotify(b ->
            {
                b.configId(request.configId());
                ifPresent(request.token(), b::token);
                ifPresent(request.scheme(), b::scheme);
                ifPresent(request.credentials(), b::credentials);
                b.contentLength(request.contentLength());
            })
            .build());
    }

    private static void ifPresent(
        String value,
        Consumer<String> setter)
    {
        if (value != null)
        {
            setter.accept(value);
        }
    }

    private final class A2aStream
    {
        private final A2aServer server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final A2aReplyEnd replyEnd;
        private final JsonParserEx parser;

        private A2aReplyDecoder decoder;

        private MessageConsumer app;
        private DirectBufferEx pending;
        private int pendingOffset;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private int decodeLimit;
        private int decodedDepth;
        private int eventFrom;
        private int eventFlags;

        private A2aStream(
            A2aServer server)
        {
            this.server = server;
            this.originId = server.routedId;
            this.routedId = server.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(server.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            JsonParserEx parser = null;

            if (server.start == startPush)
            {
                this.decoder = decodeReplyIgnore;
                this.replyEnd = endReplyPush;
            }
            else if (server.operation == null || server.operation == A2aOperation.EXTENDED_AGENT_CARD ||
                !server.dialect.verbatim())
            {
                parser = createReplyParser();
                this.decoder = decodeReplyTranslated;
                this.replyEnd = endReplyTranslated;
            }
            else if (server.operation.streaming())
            {
                parser = createReplyParser();
                this.decoder = decodeReplyEventStart;
                this.replyEnd = endReplyEvents;
            }
            else
            {
                this.decoder = decodeReplyJson;
                this.replyEnd = endReplyJson;
            }

            this.parser = parser;
        }

        private JsonParserEx createReplyParser()
        {
            return JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
        }

        private int position()
        {
            return decodeLimit - parser.remaining();
        }

        private void flushEvent(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int to,
            int flags)
        {
            final int eventFlags = this.eventFlags | flags;

            server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_EVENT_STREAM);
            server.doNetEvent(traceId, authorization, eventFlags, buffer, eventFrom, to - eventFrom,
                (eventFlags & INIT) != 0, (eventFlags & FIN) != 0);

            this.eventFlags = 0;
            this.eventFrom = to;
        }

        private void decodeReply(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            final boolean carried = decodeSlot != NO_SLOT;

            if (carried && decodeSlotOffset + length > encodeMax)
            {
                cleanupApp(traceId, authorization);
            }
            else
            {
                if (carried)
                {
                    final MutableDirectBufferEx slot = encodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, buffer, offset, length);
                    decodeSlotOffset += length;

                    buffer = slot;
                    offset = 0;
                    limit = decodeSlotOffset;
                }

                decodeLimit = limit;
                eventFrom = offset;

                if (parser != null)
                {
                    parser.wrap(buffer, offset, limit, false);
                }

                int progress = offset;
                int previous;
                A2aReplyDecoder previousDecoder;

                try
                {
                    do
                    {
                        previous = progress;
                        previousDecoder = decoder;
                        progress = decoder.decode(this, traceId, authorization, buffer, progress, limit);
                    }
                    while (progress != previous && progress < limit || decoder != previousDecoder);

                    if (decoder == decodeReplyEvent && progress > eventFrom)
                    {
                        flushEvent(traceId, authorization, buffer, progress, 0);
                    }
                }
                catch (JsonException | IllegalStateException ex)
                {
                    cleanupApp(traceId, authorization);
                }

                if (decoder != decodeReplyIgnore)
                {
                    carryDecode(traceId, authorization, buffer, progress, limit, carried);
                }
            }
        }

        private void carryDecode(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int progress,
            int limit,
            boolean carried)
        {
            final int remaining = limit - progress;

            if (remaining == 0)
            {
                cleanupDecodeSlot();
            }
            else if (decodeSlot == NO_SLOT && (decodeSlot = encodePool.acquire(replyId)) == NO_SLOT)
            {
                cleanupApp(traceId, authorization);
            }
            else
            {
                final MutableDirectBufferEx slot = encodePool.buffer(decodeSlot);

                if (carried)
                {
                    codecBuffer.putBytes(0, slot, progress, remaining);
                    slot.putBytes(0, codecBuffer, 0, remaining);
                }
                else
                {
                    slot.putBytes(0, buffer, progress, remaining);
                }

                decodeSlotOffset = remaining;
            }
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            app = newStream(this::onAppMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0,
                extension);

            state = A2aState.openingInitial(state);

            if (app == null)
            {
                server.doEncodeResponseStatus(traceId, authorization, STATUS_502);
            }
        }

        private int doAppData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int initialNoAck = (int) (initialSeq - initialAck);
            final int length = Math.min(Math.max(initialMax - initialNoAck - initialPad, 0), limit - offset);

            if (length > 0)
            {
                final int reserved = length + initialPad;

                doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, COMPLETE, initialBud, reserved, buffer, offset, length);

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            return offset + length;
        }

        private void flushPending(
            long traceId,
            long authorization)
        {
            if (pending != null)
            {
                pendingOffset = doAppData(traceId, authorization, pending, pendingOffset, pending.capacity());

                if (pendingOffset == pending.capacity())
                {
                    pending = null;
                    pendingOffset = 0;
                }
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!A2aState.initialClosed(state) &&
                A2aState.replyClosed(state) &&
                A2aState.initialClosed(server.state) &&
                pending == null)
            {
                state = A2aState.closedInitial(state);
                doEnd(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);

                cleanupDecodeSlot();
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (A2aState.initialOpening(state) &&
                !A2aState.initialClosed(state))
            {
                state = A2aState.closedInitial(state);
                doAbort(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);

                if (A2aState.replyClosed(state))
                {
                    cleanupDecodeSlot();
                }
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = A2aState.openedReply(state);
            doWindow(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closedReply(state);
                cleanupDecodeSlot();
                doReset(app, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void flushAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int minReplyNoAck,
            int minReplyMax)
        {
            if (app != null && !A2aState.replyClosed(state))
            {
                final long replyAckMax = Math.max(replySeq - minReplyNoAck - decodeSlotOffset, 0);
                if (replyAckMax > replyAck || minReplyMax > replyMax)
                {
                    replyAck = replyAckMax;
                    assert replyAck <= replySeq;

                    replyMax = Math.max(minReplyMax, replyMax);

                    doAppWindow(traceId, authorization, budgetId, padding);
                }
            }
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;

            assert replyAck <= replySeq;

            state = A2aState.openingReply(state);

            final OctetsFW extension = begin.extension();
            final A2aBeginExFW beginEx = extension.sizeof() > 0
                ? a2aBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            if (beginEx != null)
            {
                server.activatedExtensions = extensionsOf(beginEx);
            }

            flushAppWindow(traceId, authorization, 0L, 0, 0, encodeMax);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + encodeMax)
            {
                cleanupApp(traceId, authorization);
            }
            else if (payload != null && !A2aState.replyClosed(state))
            {
                decodeReply(traceId, authorization, payload.buffer(), payload.offset(), payload.limit());
                flushAppWindow(traceId, authorization, 0L, 0, server.encodeSlotOffset, encodeMax);
            }
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final int reserved = flush.reserved();

            replySeq = sequence + reserved;
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            final boolean replying = !A2aState.replyClosed(state);

            state = A2aState.closedReply(state);

            if (replying)
            {
                replyEnd.end(this, traceId, authorization);
            }

            cleanupDecodeSlot();
            server.doDecodeEnd(traceId, authorization);
            doAppEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            final boolean replying = !A2aState.replyClosed(state);

            state = A2aState.closedReply(state);

            cleanupDecodeSlot();

            if (replying)
            {
                server.doEncodeResponseAbort(traceId, authorization);
            }
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            initialPad = padding;

            state = A2aState.openedInitial(state);

            flushPending(traceId, authorization);

            server.decodeNet(traceId, authorization, budgetId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();

            final A2aResetExFW resetEx = extension.sizeof() > 0 ? extension.get(a2aResetExRO::tryWrap) : null;

            if (A2aState.replyOpening(server.state))
            {
                server.doNetAbort(traceId, authorization);
            }
            else if (resetEx != null && resetEx.kind() == A2aResetExFW.KIND_BEARER)
            {
                server.doEncodeResponseBearer(traceId, authorization, resetEx.bearer());
            }
            else if (resetEx != null && resetEx.kind() == A2aResetExFW.KIND_ERROR)
            {
                final A2aErrorResetExFW error = resetEx.error();
                final int code = error.code();

                if (code >= STATUS_RANGE_MIN && code <= STATUS_RANGE_MAX)
                {
                    server.doEncodeResponseStatus(traceId, authorization, Integer.toString(code));
                }
                else if (server.operation != null)
                {
                    server.doEncodeResponseError(traceId, authorization, code, error.message().asString(), server.idOrNull());
                }
                else
                {
                    server.doEncodeResponseStatus(traceId, authorization, STATUS_502);
                }
            }
            else
            {
                server.doEncodeResponseStatus(traceId, authorization, STATUS_502);
            }

            state = A2aState.closedInitial(state);

            if (!A2aState.replyOpening(state))
            {
                state = A2aState.closedReply(state);
                cleanupDecodeSlot();
            }
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                encodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            decoder = decodeReplyIgnore;
            cleanupDecodeSlot();
            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);

            server.cleanupNet(traceId, authorization);
        }
    }

    private static String extensionsOf(
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

    private int decodeReplyJson(
        A2aStream stream,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final A2aServer server = stream.server;

        if (limit > offset)
        {
            if (!A2aState.replyOpening(server.state))
            {
                server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_JSON);
                server.doNetBytes(traceId, authorization, server.resultPreamble());
            }

            server.doNetData(traceId, authorization, buffer, offset, limit);
        }

        return limit;
    }

    private void endReplyJson(
        A2aStream stream,
        long traceId,
        long authorization)
    {
        final A2aServer server = stream.server;

        if (!A2aState.replyOpening(server.state))
        {
            server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_JSON);
            server.doNetBytes(traceId, authorization, server.resultPreamble());
            server.doNetBytes(traceId, authorization, JSON_NULL.getBytes(UTF_8));
        }

        server.doNetBytes(traceId, authorization, "}".getBytes(UTF_8));
        server.doEncodeResponseEnd(traceId, authorization);
    }

    private int decodeReplyEventStart(
        A2aStream stream,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.parser;

        parser.reset();
        parser.wrap(buffer, offset, limit, false);

        if (parser.hasNextEvent() && parser.nextEvent() == JsonEvent.START_DOCUMENT && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
            {
                stream.decodedDepth = 1;
                stream.eventFrom = offset;
                stream.eventFlags = INIT;
                stream.decoder = decodeReplyEvent;
            }
            else
            {
                stream.cleanupApp(traceId, authorization);
            }
        }

        return stream.position();
    }

    private int decodeReplyEvent(
        A2aStream stream,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.parser;

        while (stream.decoder == decodeReplyEvent && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            stream.decodedDepth += nesting(event);

            if (event == JsonEvent.KEY_NAME && parser.deferredBytes())
            {
                parser.consumed(0);
            }
            else if ((event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER) && parser.deferredBytes())
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event != JsonEvent.KEY_NAME && stream.decodedDepth == 0)
            {
                stream.flushEvent(traceId, authorization, buffer, stream.position(), FIN);
                stream.decoder = decodeReplyEventStart;
            }
        }

        return stream.position();
    }

    private void endReplyEvents(
        A2aStream stream,
        long traceId,
        long authorization)
    {
        final A2aServer server = stream.server;

        server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_EVENT_STREAM);

        if (stream.decoder == decodeReplyEvent)
        {
            server.doNetAbort(traceId, authorization);
        }
        else
        {
            server.doEncodeResponseEnd(traceId, authorization);
        }
    }

    private int decodeReplyTranslated(
        A2aStream stream,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final JsonParserEx parser = stream.parser;

        parser.reset();
        parser.wrap(buffer, offset, limit, false);

        int progress = offset;
        int depth = 0;
        boolean started = false;

        while (stream.decoder == decodeReplyTranslated && parser.hasNextEvent() && (!started || depth > 0))
        {
            final JsonEvent event = parser.nextEvent();

            started |= event != JsonEvent.START_DOCUMENT;
            depth += nesting(event);

            if (deferred(parser, event))
            {
                parser.consumed(event == JsonEvent.KEY_NAME ? 0 : parser.getStringView().length());
            }
        }

        if (started && depth == 0 && stream.decoder == decodeReplyTranslated)
        {
            progress = stream.position();
            doEncodeTranslated(stream, traceId, authorization, buffer, offset, progress);
        }

        return progress;
    }

    private static boolean deferred(
        JsonParserEx parser,
        JsonEvent event)
    {
        return (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER) &&
            parser.deferredBytes();
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

    private void doEncodeTranslated(
        A2aStream stream,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final A2aServer server = stream.server;
        final A2aOperation operation = server.operation;

        JsonValue canonical = null;

        try
        {
            final DirectBufferInputStreamEx input = new DirectBufferInputStreamEx();
            input.wrap(buffer, offset, limit - offset);
            canonical = Json.createReader(input).readValue();
        }
        catch (JsonException | IllegalStateException ex)
        {
            stream.cleanupApp(traceId, authorization);
        }

        if (canonical != null)
        {
            final boolean secured = operation == null && server.binding.secured();
            final boolean card = operation == null || operation == A2aOperation.EXTENDED_AGENT_CARD;
            final JsonValue rendered = card
                ? server.dialect.card(canonical.asJsonObject(), server.request.url(), secured)
                : server.dialect.result(operation, canonical);
            final JsonValue translated = card && server.binding.pushConfigured()
                ? A2aPushCapabilities.advertise(rendered.asJsonObject())
                : rendered;

            final byte[] json = translated.toString().getBytes(UTF_8);

            if (operation == null)
            {
                server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_JSON);
                server.doNetBytes(traceId, authorization, json);
            }
            else if (operation.streaming())
            {
                server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_EVENT_STREAM);
                server.doNetEvent(traceId, authorization, INIT | FIN, new UnsafeBufferEx(json), 0, json.length, true, true);
            }
            else
            {
                server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_JSON);
                server.doNetBytes(traceId, authorization, server.resultPreamble());
                server.doNetBytes(traceId, authorization, json);
                server.doNetBytes(traceId, authorization, "}".getBytes(UTF_8));
            }
        }
    }

    private void endReplyTranslated(
        A2aStream stream,
        long traceId,
        long authorization)
    {
        final A2aServer server = stream.server;
        final A2aOperation operation = server.operation;

        if (stream.decodeSlotOffset > 0)
        {
            server.doNetAbort(traceId, authorization);
        }
        else if (operation == null && !A2aState.replyOpening(server.state))
        {
            server.doEncodeResponseStatus(traceId, authorization, STATUS_502);
        }
        else
        {
            if (!A2aState.replyOpening(server.state))
            {
                if (operation.streaming())
                {
                    server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_EVENT_STREAM);
                }
                else
                {
                    server.doEncodeResponseBegin(traceId, authorization, CONTENT_TYPE_JSON);
                    server.doNetBytes(traceId, authorization, server.resultPreamble());
                    server.doNetBytes(traceId, authorization, JSON_NULL.getBytes(UTF_8));
                    server.doNetBytes(traceId, authorization, "}".getBytes(UTF_8));
                }
            }

            server.doEncodeResponseEnd(traceId, authorization);
        }
    }

    private int decodeReplyIgnore(
        A2aStream stream,
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    private void endReplyPush(
        A2aStream stream,
        long traceId,
        long authorization)
    {
        stream.server.doEncodeResponseStatus(traceId, authorization, STATUS_200);
    }

    private final class A2aRejectHandler
    {
        private final MessageConsumer net;
        private final String status;

        private A2aRejectHandler(
            MessageConsumer sender,
            String status)
        {
            this.net = sender;
            this.status = status;
        }

        private void onNetBegin(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            if (msgTypeId == BeginFW.TYPE_ID)
            {
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                doReset(net, begin.originId(), begin.routedId(), begin.streamId(),
                    begin.sequence(), begin.acknowledge(), 0,
                    begin.traceId(), begin.authorization(), httpResetExRW
                        .wrap(codecBuffer, 0, codecBuffer.capacity())
                        .typeId(httpTypeId)
                        .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                        .build());
            }
        }
    }

    private final class A2aBearerRejectHandler
    {
        private final MessageConsumer net;
        private final A2aBearerError error;

        private long originId;
        private long routedId;
        private long initialId;

        private long initialSeq;
        private long initialAck;

        private A2aBearerRejectHandler(
            MessageConsumer sender,
            A2aBearerError error)
        {
            this.net = sender;
            this.error = error;
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
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                initialSeq = end.sequence();
                initialAck = initialSeq;
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                initialSeq = abort.sequence();
                initialAck = initialSeq;
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long replyId = supplyReplyId.applyAsLong(begin.streamId());
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            this.originId = begin.originId();
            this.routedId = begin.routedId();
            this.initialId = begin.streamId();
            this.initialSeq = begin.sequence();
            this.initialAck = begin.acknowledge();

            doWindow(net, originId, routedId, initialId, initialSeq, initialAck, decodeMax,
                traceId, authorization, 0L, 0);

            final String status = error == A2aBearerError.INSUFFICIENT_SCOPE ? STATUS_403 : STATUS_401;
            final String wwwAuthenticate = bearerChallenge(null, null, error);

            doBegin(net, originId, routedId, replyId, 0L, 0L, 0, traceId, authorization, 0L, httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                .headersItem(h -> h.name(HTTP_HEADER_WWW_AUTHENTICATE).value(wwwAuthenticate))
                .build());

            doEnd(net, originId, routedId, replyId, 0L, 0L, 0, traceId, authorization);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            initialSeq = data.sequence() + data.reserved();

            if (initialSeq > initialAck + decodeMax)
            {
                doReset(net, originId, routedId, initialId, initialSeq, initialAck, decodeMax,
                    traceId, authorization, emptyRO);
            }
            else
            {
                initialAck = initialSeq;
                doWindow(net, originId, routedId, initialId, initialSeq, initialAck, decodeMax,
                    traceId, authorization, budgetId, 0);
            }
        }
    }

    private static String bearerChallenge(
        String realm,
        String scopes,
        A2aBearerError error)
    {
        final StringBuilder challenge = new StringBuilder("Bearer");
        String separator = " ";
        if (realm != null)
        {
            challenge.append(separator).append("realm=\"").append(realm).append('"');
            separator = ", ";
        }
        if (scopes != null)
        {
            challenge.append(separator).append("scope=\"").append(scopes).append('"');
            separator = ", ";
        }
        challenge.append(separator).append("error=\"").append(error.name().toLowerCase()).append('"');
        return challenge.toString();
    }

    private static String quote(
        String value)
    {
        final StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++)
        {
            final char ch = value.charAt(index);
            switch (ch)
            {
            case '"':
                quoted.append("\\\"");
                break;
            case '\\':
                quoted.append("\\\\");
                break;
            case '\n':
                quoted.append("\\n");
                break;
            case '\r':
                quoted.append("\\r");
                break;
            case '\t':
                quoted.append("\\t");
                break;
            default:
                if (ch < 0x20)
                {
                    quoted.append(String.format("\\u%04x", (int) ch));
                }
                else
                {
                    quoted.append(ch);
                }
                break;
            }
        }
        return quoted.append('"').toString();
    }

    private int doDataEvents(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        int padding,
        long budgetId,
        long traceId,
        long authorization,
        MutableDirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        long replySeq = sequence;
        boolean blocked = false;

        while (!blocked && progress < limit)
        {
            final int flags = buffer.getInt(progress);
            final int extensionLength = buffer.getInt(progress + Integer.BYTES);
            final int payloadLength = buffer.getInt(progress + 2 * Integer.BYTES);
            final int extensionAt = progress + EVENT_RECORD_HEADER;
            final int payloadAt = extensionAt + extensionLength;

            final int window = maximum - (int) (replySeq - acknowledge) - padding;
            final int length = Math.min(Math.max(window, 0), payloadLength);

            if (window >= 0 && (length > 0 || payloadLength == 0))
            {
                final boolean whole = length == payloadLength;
                final int reserved = length + padding;

                doData(receiver, originId, routedId, streamId, replySeq, acknowledge, maximum,
                    traceId, authorization, whole ? flags : flags & ~FIN, budgetId, reserved,
                    length > 0 ? buffer : null, payloadAt, length);
                replySeq += reserved;

                if (whole)
                {
                    progress = payloadAt + payloadLength;
                }
                else
                {
                    final int remainderAt = payloadAt + length - EVENT_RECORD_HEADER;
                    buffer.putInt(remainderAt, flags & ~INIT);
                    buffer.putInt(remainderAt + Integer.BYTES, 0);
                    buffer.putInt(remainderAt + 2 * Integer.BYTES, payloadLength - length);
                    progress = remainderAt;
                    blocked = true;
                }
            }
            else
            {
                blocked = true;
            }
        }

        eventReserved = (int) (replySeq - sequence);

        return progress - offset;
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
        Flyweight extension)
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
        int length)
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
            .reserved(reserved);

        if (payload != null)
        {
            builder.payload(payload, offset, length);
        }
        else
        {
            builder.payload((OctetsFW) null);
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
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

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
