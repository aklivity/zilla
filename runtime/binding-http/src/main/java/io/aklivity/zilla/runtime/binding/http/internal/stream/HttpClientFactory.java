/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.stream;

import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpVersion.HTTP_1_1;
import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpVersion.HTTP_2;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.TE;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.TRAILERS;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackHeaderFieldFW.HeaderFieldType.UNKNOWN;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.INCREMENTAL_INDEXING;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.WITHOUT_INDEXING;
import static io.aklivity.zilla.runtime.binding.http.internal.util.BufferUtil.limitOfBytes;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.emptyMap;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.agrona.collections.LongLongConsumer;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.HttpBinding;
import io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2ContinuationFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2DataFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2ErrorCode;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameInfoFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameType;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2GoawayFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2HeadersFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2PingFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2PrefaceFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2PriorityFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2PushPromiseFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2RstStreamFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2Setting;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2SettingsFW;
import io.aklivity.zilla.runtime.binding.http.internal.codec.Http2WindowUpdateFW;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpBindingConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpRouteConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpVersion;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackHeaderBlockFW;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackHeaderFieldFW;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackHuffman;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackLiteralHeaderFieldFW;
import io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackStringFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.queue.HttpQueueEntryFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpEndExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpFlushExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;


public final class HttpClientFactory implements HttpStreamFactory
{
    private static final Pattern RESPONSE_LINE_PATTERN =
            Pattern.compile("(?<version>HTTP/\\d\\.\\d)\\s+(?<status>\\d+)\\s+(?<reason>[^\\r\\n]+)\r\n");
    private static final Pattern VERSION_PATTERN = Pattern.compile("HTTP/1\\.\\d");
    private static final Pattern HEADER_LINE_PATTERN = Pattern.compile("(?<name>[^\\s:]+):\\s*(?<value>[^\r\n]*)\r\n");
    private static final Pattern CONNECTION_CLOSE_PATTERN = Pattern.compile("(^|\\s*,\\s*)close(\\s*,\\s*|$)");
    private static final Map<String, String> EMPTY_HEADERS = Collections.emptyMap();

    private static final byte[] HOST_BYTES = "Host".getBytes(US_ASCII);
    private static final byte[] COLON_SPACE_BYTES = ": ".getBytes(US_ASCII);
    private static final byte[] CRLFCRLF_BYTES = "\r\n\r\n".getBytes(US_ASCII);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(US_ASCII);
    private static final byte[] SEMICOLON_BYTES = ";".getBytes(US_ASCII);

    private static final byte COLON_BYTE = ':';
    private static final byte HYPHEN_BYTE = '-';
    private static final byte SPACE_BYTE = ' ';
    private static final byte ZERO_BYTE = '0';

    private static final int NO_CONTENT_LENGTH = -1;
    private static final int CLIENT_INITIATED = 1;
    private static final long MAX_REMOTE_BUDGET = Integer.MAX_VALUE;

    private static final byte[] HTTP_1_1_BYTES = "HTTP/1.1".getBytes(US_ASCII);

    private static final DirectBuffer ZERO_CHUNK = new UnsafeBuffer("0\r\n\r\n".getBytes(US_ASCII));

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final ProxyBeginExFW beginProxyExRO = new ProxyBeginExFW();
    private static final Array32FW<HttpHeaderFW> HEADERS_431_REQUEST_TOO_LARGE =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                    .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                    .item(h -> h.name(":status").value("431"))
                    .build();

    private static final Array32FW<HttpHeaderFW> HEADERS_503_RETRY_AFTER =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                    .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                    .item(h -> h.name(":status").value("503"))
                    .item(h -> h.name("retry-after").value("0"))
                    .build();

    private static final String GET_METHOD = "GET";
    private static final String HEAD_METHOD = "HEAD";
    private static final String CONTENT_LENGTH = "content-length";
    private static final String METHOD = ":method";
    private static final String PATH = ":path";
    private static final String AUTHORITY = ":authority";
    private static final String SCHEME = ":scheme";
    private static final String CACHE_CONTROL = "cache-control";
    private static final String8FW HEADER_AUTHORITY = new String8FW(":authority");
    private static final String8FW HEADER_USER_AGENT = new String8FW("user-agent");
    private static final String8FW HEADER_CONNECTION = new String8FW("connection");
    private static final String8FW HEADER_CONTENT_LENGTH = new String8FW("content-length");
    private static final String8FW HEADER_HTTP2_SETTINGS = new String8FW("HTTP2-Settings");
    private static final String8FW HEADER_METHOD = new String8FW(":method");
    private static final String8FW HEADER_PATH = new String8FW(":path");
    private static final String8FW HEADER_STATUS = new String8FW(":status");
    private static final String8FW HEADER_TRANSFER_ENCODING = new String8FW("transfer-encoding");
    private static final String8FW HEADER_UPGRADE = new String8FW("upgrade");

    private static final String8FW PROXY_ALPN_H2 = new String8FW("h2");
    private static final String16FW METHOD_HEAD = new String16FW("HEAD");
    private static final String16FW METHOD_GET = new String16FW("GET");
    private static final String16FW METHOD_DELETE = new String16FW("DELETE");
    private static final String16FW PATH_SLASH = new String16FW("/");
    private static final String16FW STATUS_101 = new String16FW("101");
    private static final String16FW UPGRADE_H2C = new String16FW("h2c");
    private static final String16FW CONNECTION_UPGRADE_HTTP2_SETTINGS = new String16FW("Upgrade, HTTP2-Settings");
    private static final String16FW TRANSFER_ENCODING_CHUNKED = new String16FW("chunked");

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final Array32FW<HttpHeaderFW> DEFAULT_HEADERS =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                    .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                    .item(i -> i.name(HEADER_METHOD).value(METHOD_GET))
                    .item(i -> i.name(HEADER_PATH).value(PATH_SLASH))
                    .build();
    private static final Array32FW<HttpHeaderFW> DEFAULT_TRAILERS =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                         .wrap(new UnsafeBuffer(new byte[8]), 0, 8)
                         .build();
    private static final Map<String8FW, String16FW> EMPTY_OVERRIDES = emptyMap();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final DirectBuffer payloadRO = new UnsafeBuffer(0, 0);
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final HttpBeginExFW beginExRO = new HttpBeginExFW();
    private final HttpEndExFW endExRO = new HttpEndExFW();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpQueueEntryFW queueEntryRO = new HttpQueueEntryFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final HttpBeginExFW.Builder beginExRW = new HttpBeginExFW.Builder();
    private final HttpFlushExFW.Builder flushExRW = new HttpFlushExFW.Builder();
    private final HttpEndExFW.Builder endExRW = new HttpEndExFW.Builder();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final HttpQueueEntryFW.Builder queueEntryRW = new HttpQueueEntryFW.Builder();

    private final AsciiSequenceView asciiRO = new AsciiSequenceView();

    private final HttpClientDecoder decodeHttp11Headers = this::decodeHttp11Headers;
    private final HttpClientDecoder decodeHttp11HeadersOnly = this::decodeHttp11HeadersOnly;
    private final HttpClientDecoder decodeHttp11ChunkHeader = this::decodeHttp11ChunkHeader;
    private final HttpClientDecoder decodeHttp11ChunkBody = this::decodeHttp11ChunkBody;
    private final HttpClientDecoder decodeHttp11ChunkEnd = this::decodeHttp11ChunkEnd;
    private final HttpClientDecoder decodeHttp11Content = this::decodeHttp11Content;
    private final HttpClientDecoder decodeHttp11Trailers = this::decodeHttp11Trailers;
    private final HttpClientDecoder decodeHttp11EmptyLines = this::decodeHttp11EmptyLines;
    private final HttpClientDecoder decodeHttp11Upgraded = this::decodeHttp11Upgraded;
    private final HttpClientDecoder decodeHttp11Ignore = this::decodeHttp11Ignore;

    private final MutableInteger codecOffset = new MutableInteger();
    private final Http2FrameInfoFW http2FrameInfoRO = new Http2FrameInfoFW();
    private final Http2SettingsFW http2SettingsRO = new Http2SettingsFW();
    private final Http2GoawayFW http2GoawayRO = new Http2GoawayFW();
    private final Http2PingFW http2PingRO = new Http2PingFW();
    private final Http2DataFW http2DataRO = new Http2DataFW();
    private final Http2HeadersFW http2HeadersRO = new Http2HeadersFW();
    private final Http2ContinuationFW http2ContinuationRO = new Http2ContinuationFW();
    private final Http2WindowUpdateFW http2WindowUpdateRO = new Http2WindowUpdateFW();
    private final Http2RstStreamFW http2RstStreamRO = new Http2RstStreamFW();
    private final Http2PriorityFW http2PriorityRO = new Http2PriorityFW();
    private final Http2PushPromiseFW http2PushPromiseRO = new Http2PushPromiseFW();

    private final Http2PrefaceFW.Builder http2PrefaceRW = new Http2PrefaceFW.Builder();
    private final Http2SettingsFW.Builder http2SettingsRW = new Http2SettingsFW.Builder();
    private final Http2GoawayFW.Builder http2GoawayRW = new Http2GoawayFW.Builder();
    private final Http2PingFW.Builder http2PingRW = new Http2PingFW.Builder();
    private final Http2DataFW.Builder http2DataRW = new Http2DataFW.Builder();
    private final Http2HeadersFW.Builder http2HeadersRW = new Http2HeadersFW.Builder();
    private final Http2WindowUpdateFW.Builder http2WindowUpdateRW = new Http2WindowUpdateFW.Builder();
    private final Http2RstStreamFW.Builder http2RstStreamRW = new Http2RstStreamFW.Builder();

    private final HttpClientDecoder decodeHttp2Settings = this::decodeHttp2Settings;
    private final HttpClientDecoder decodeHttp2FrameType = this::decodeHttp2FrameType;
    private final HttpClientDecoder decodeHttp2Ping = this::decodeHttp2Ping;
    private final HttpClientDecoder decodeHttp2Goaway = this::decodeHttp2Goaway;
    private final HttpClientDecoder decodeHttp2WindowUpdate = this::decodeHttp2WindowUpdate;
    private final HttpClientDecoder decodeHttp2Headers = this::decodeHttp2Headers;
    private final HttpClientDecoder decodeHttp2Continuation = this::decodeHttp2Continuation;
    private final HttpClientDecoder decodeHttp2Data = this::decodeHttp2Data;
    private final HttpClientDecoder decodeHttp2DataPayload = this::decodeHttp2DataPayload;
    private final HttpClientDecoder decodeHttp2PushPromise = this::decodeHttp2PusPromise;
    private final HttpClientDecoder decodeHttp2Priority = this::decodeHttp2Priority;
    private final HttpClientDecoder decodeHttp2RstStream = this::decodeHttp2RstStream;
    private final HttpClientDecoder decodeHttp2IgnoreOne = this::decodeHttp2IgnoreOne;
    private final HttpClientDecoder decodeHttp2IgnoreAll = this::decodeHttp2IgnoreAll;

    private final Http2HeadersEncoder headersEncoder = new Http2HeadersEncoder();
    private final Http2HeadersDecoder headersDecoder = new Http2HeadersDecoder();
    private final HpackHeaderBlockFW headerBlockRO = new HpackHeaderBlockFW();
    private final MutableInteger payloadRemaining = new MutableInteger(0);

    private final EnumMap<Http2FrameType, HttpClientDecoder> decodersByFrameType;
    {
        final EnumMap<Http2FrameType, HttpClientDecoder> decodersByFrameType = new EnumMap<>(Http2FrameType.class);
        decodersByFrameType.put(Http2FrameType.SETTINGS, decodeHttp2Settings);
        decodersByFrameType.put(Http2FrameType.PING, decodeHttp2Ping);
        decodersByFrameType.put(Http2FrameType.GO_AWAY, decodeHttp2Goaway);
        decodersByFrameType.put(Http2FrameType.WINDOW_UPDATE, decodeHttp2WindowUpdate);
        decodersByFrameType.put(Http2FrameType.HEADERS, decodeHttp2Headers);
        decodersByFrameType.put(Http2FrameType.CONTINUATION, decodeHttp2Continuation);
        decodersByFrameType.put(Http2FrameType.DATA, decodeHttp2Data);
        decodersByFrameType.put(Http2FrameType.PUSH_PROMISE, decodeHttp2PushPromise);
        decodersByFrameType.put(Http2FrameType.PRIORITY, decodeHttp2Priority);
        decodersByFrameType.put(Http2FrameType.RST_STREAM, decodeHttp2RstStream);
        this.decodersByFrameType = decodersByFrameType;
    }

    private final Map<String8FW, String16FW> headersMap;
    private final String16FW h2cSettingsPayload;
    private final HttpConfiguration config;
    private final Http2Settings initialSettings;
    private final MutableDirectBuffer frameBuffer;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BufferPool bufferPool;
    private final BufferPool headersPool;
    private final BudgetCreditor creditor;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongUnaryOperator supplyPromiseId;
    private final LongFunction<MessageConsumer> supplySender;
    private final LongSupplier supplyTraceId;
    private final LongSupplier supplyBudgetId;
    private final Long2ObjectHashMap<HttpClientPool> clientPools;
    private final Long2ObjectHashMap<HttpBindingConfig> bindings;
    private final Matcher responseLine;
    private final Matcher versionPart;
    private final Matcher headerLine;
    private final Matcher connectionClose;
    private final int httpTypeId;
    private final int proxyTypeId;
    private final int encodeMax;
    private final int decodeMax;
    private final int maximumRequestQueueSize;
    private final int maximumConnectionsPerRoute;
    private final int maximumPushPromiseListSize;

    private final LongSupplier countRequests;
    private final LongSupplier countRequestsRejected;
    private final LongSupplier countRequestsAbandoned;
    private final LongSupplier countResponses;
    private final LongSupplier countResponsesAbandoned;
    private final LongSupplier enqueues;
    private final LongSupplier dequeues;
    private final LongConsumer connectionInUse;

    public HttpClientFactory(
        HttpConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.frameBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.headersPool = bufferPool.duplicate();
        this.creditor = context.creditor();
        this.initialSettings = new Http2Settings(config, headersPool);
        this.streamFactory = context.streamFactory();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyPromiseId = context::supplyPromiseId;
        this.supplySender = context::supplySender;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.httpTypeId = context.supplyTypeId(HttpBinding.NAME);
        this.bindings = new Long2ObjectHashMap<>();
        this.headersMap = new LinkedHashMap<>();
        this.responseLine = RESPONSE_LINE_PATTERN.matcher("");
        this.headerLine = HEADER_LINE_PATTERN.matcher("");
        this.versionPart = VERSION_PATTERN.matcher("");
        this.connectionClose = CONNECTION_CLOSE_PATTERN.matcher("");
        this.maximumRequestQueueSize = bufferPool.slotCapacity();

        this.clientPools = new Long2ObjectHashMap<>();
        this.maximumConnectionsPerRoute = config.maximumConnectionsPerRoute();
        this.maximumPushPromiseListSize = config.maxPushPromiseListSize();
        this.countRequests = context.supplyCounter("http.requests");
        this.countRequestsRejected = context.supplyCounter("http.requests.rejected");
        this.countRequestsAbandoned = context.supplyCounter("http.requests.abandoned");
        this.countResponses = context.supplyCounter("http.responses");
        this.countResponsesAbandoned = context.supplyCounter("http.responses.abandoned");
        this.enqueues = context.supplyCounter("http.enqueues");
        this.dequeues = context.supplyCounter("http.dequeues");
        this.connectionInUse = context.supplyAccumulator("http.connections.in.use");
        this.decodeMax = bufferPool.slotCapacity();
        this.encodeMax = bufferPool.slotCapacity();

        final byte[] settingsPayload = new byte[12];
        http2SettingsRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .maxConcurrentStreams(initialSettings.maxConcurrentStreams)
                .initialWindowSize(initialSettings.initialWindowSize);
        frameBuffer.getBytes(9, settingsPayload);

        this.h2cSettingsPayload = new String16FW(Base64.getUrlEncoder().encodeToString(settingsPayload));
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        HttpBindingConfig httpBinding = new HttpBindingConfig(binding);
        bindings.put(binding.id, httpBinding);
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
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long bindingId = begin.routedId();
        final long authorization = begin.authorization();
        final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

        final HttpBindingConfig binding = bindings.get(bindingId);

        HttpRouteConfig route = null;

        if (binding != null)
        {
            // TODO: avoid object creation
            final Map<String, String> headers = beginEx != null ? asHeadersMap(beginEx.headers()) : EMPTY_HEADERS;
            route = binding.resolve(authorization, headers::get);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {
            final long resolvedId = route.id;
            final Map<String8FW, String16FW> overrides =
                binding.options != null && binding.options.overrides != null ? binding.options.overrides : EMPTY_OVERRIDES;

            // TODO: store client pools on HttpBindingConfig ?
            final HttpClientPool clientPool =
                    clientPools.computeIfAbsent(resolvedId, r -> new HttpClientPool(bindingId, r));
            newStream = clientPool.newStream(begin, application, overrides, binding.versions());
        }

        return newStream;
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

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

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

    private void doFlush(
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
        int reserved,
        Consumer<OctetsFW.Builder> extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .reserved(reserved)
                .extension(extension)
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doFlush(
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
        int reserved,
        OctetsFW extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .reserved(reserved)
                .extension(extension)
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int index,
        int length,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(buffer, index, length)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

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
        long authorization,
        Flyweight extension)
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        long authorization,
        Flyweight extension)
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        long authorization)
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
                .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private int decodeHttp11Headers(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final int endOfHeadersAt = limitOfBytes(buffer, offset, limit, CRLFCRLF_BYTES);

        decode:
        if (endOfHeadersAt != -1)
        {
            final HttpBeginExFW.Builder httpBeginEx = beginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                                                               .typeId(httpTypeId);

            final int endOfStartAt = limitOfBytes(buffer, offset, limit, CRLF_BYTES);

            if (endOfStartAt != -1)
            {
                final String status = decodeHttp1StartLine(buffer, offset, endOfStartAt);
                if (status == null)
                {
                    client.onDecodeHttp11HeadersError(traceId, authorization);
                    client.decoder = decodeHttp11Ignore;
                    break decode;
                }

                httpBeginEx.headersItem(h -> h.name(HEADER_STATUS).value(status));

                client.decoder = decodeHttp11HeadersOnly;

                final int endOfHeaderLinesAt = endOfHeadersAt - CRLF_BYTES.length;
                int startOfLineAt = endOfStartAt;
                for (int endOfLineAt = limitOfBytes(buffer, startOfLineAt, endOfHeaderLinesAt, CRLF_BYTES);
                        endOfLineAt != -1;
                        startOfLineAt = endOfLineAt,
                                endOfLineAt = limitOfBytes(buffer, startOfLineAt, endOfHeaderLinesAt, CRLF_BYTES))
                {
                    final AsciiSequenceView ascii = asciiRO.wrap(buffer, startOfLineAt, endOfLineAt - startOfLineAt);
                    if (!headerLine.reset(ascii).matches())
                    {
                        client.onDecodeHttp11HeadersError(traceId, authorization);
                        client.decoder = decodeHttp11Ignore;
                        break decode;
                    }

                    final String name = headerLine.group("name").toLowerCase();
                    final String value = headerLine.group("value");

                    switch (name)
                    {
                    case "content-length":
                        assert client.decoder == decodeHttp11HeadersOnly;
                        final int contentLength = parseInt(value);
                        if (contentLength > 0)
                        {
                            client.decodableContentLength = contentLength;
                            client.decoder = decodeHttp11Content;
                        }
                        break;

                    case "transfer-encoding":
                        assert client.decoder == decodeHttp11HeadersOnly;
                        if ("chunked".equals(value))
                        {
                            client.decoder = decodeHttp11ChunkHeader;
                        }
                        // skip header
                        continue;

                    case "upgrade":
                        assert client.decoder == decodeHttp11HeadersOnly;
                        if ("101".equals(status))
                        {
                            if (!value.equals(client.protocolUpgrade))
                            {
                                client.decoder = decodeHttp11Ignore;
                                break decode;
                            }

                            if (client.encoder == HttpEncoder.H2C)
                            {
                                client.encoder = HttpEncoder.HTTP_2;
                                client.decoder = decodeHttp2Settings;
                                client.doHttp2SettingsAck(traceId, authorization);
                            }
                            else
                            {
                                client.decoder = decodeHttp11Upgraded;
                            }
                        }
                        break;
                    }

                    httpBeginEx.headersItem(h -> h.name(name).value(value));
                }

                if (client.encoder == HttpEncoder.H2C)
                {
                    client.encoder = HttpEncoder.HTTP_1_1;
                }

                if (client.encoder == HttpEncoder.HTTP_1_1)
                {
                    client.onDecodeHttp11Headers(traceId, authorization, httpBeginEx.build());
                }

                progress = endOfHeadersAt;
            }
        }
        else if (limit - offset >= decodeMax)
        {
            client.decoder = decodeHttp11Ignore;
        }

        return progress;
    }

    private String decodeHttp1StartLine(
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final CharSequence startLine = new AsciiSequenceView(buffer, offset, limit - offset);

        return startLine.length() < decodeMax &&
                responseLine.reset(startLine).matches() &&
                versionPart.reset(responseLine.group("version")).matches() ? responseLine.group("status") : null;
    }

    private int decodeHttp11HeadersOnly(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        client.onDecodeHttp11HeadersOnly(traceId, authorization, EMPTY_OCTETS);
        client.decoder = decodeHttp11EmptyLines;
        return offset;
    }

    private int decodeHttp11ChunkHeader(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final int chunkHeaderLimit = limitOfBytes(buffer, offset, limit, CRLF_BYTES);
        if (chunkHeaderLimit != -1)
        {
            final int semicolonAt = limitOfBytes(buffer, offset, chunkHeaderLimit, SEMICOLON_BYTES);
            final int chunkSizeLimit = semicolonAt == -1 ? chunkHeaderLimit - 2 : semicolonAt - 1;
            final int chunkSizeLength = chunkSizeLimit - offset;

            try
            {
                final CharSequence chunkSizeHex = new AsciiSequenceView(buffer, offset, chunkSizeLength);
                client.decodableChunkSize = Integer.parseInt(chunkSizeHex, 0, chunkSizeLength, 16);
                client.decoder = client.decodableChunkSize != 0 ? decodeHttp11ChunkBody : decodeHttp11Trailers;
                progress = chunkHeaderLimit;
            }
            catch (NumberFormatException ex)
            {
                client.onDecodeHttp11HeadersError(traceId, authorization);
                client.decoder = decodeHttp11Ignore;
            }
        }

        return progress;
    }

    private int decodeHttp11ChunkBody(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int decodableBytes = Math.min(limit - offset, client.decodableChunkSize);

        int progress = offset;
        if (decodableBytes > 0)
        {
            progress = client.onDecodeHttp11Body(traceId, authorization, budgetId,
                                           buffer, offset, offset + decodableBytes, EMPTY_OCTETS);
            client.decodableChunkSize -= progress - offset;

            if (client.decodableChunkSize == 0)
            {
                client.decoder = decodeHttp11ChunkEnd;
            }
        }

        return progress;
    }

    private int decodeHttp11ChunkEnd(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        if (limit - progress >= 2)
        {
            if (buffer.getByte(offset) != '\r' ||
                buffer.getByte(offset + 1) != '\n')
            {
                client.onDecodeHttp11BodyError(traceId, authorization);
                client.decoder = decodeHttp11Ignore;
            }
            else
            {
                client.decoder = decodeHttp11ChunkHeader;
                progress += 2;
            }
        }
        return progress;
    }

    private int decodeHttp11Content(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int length = Math.min(limit - offset, client.decodableContentLength);

        int progress = offset;
        if (length > 0)
        {
            progress = client.onDecodeHttp11Body(traceId, authorization, budgetId, buffer, offset, offset + length, EMPTY_OCTETS);
            client.decodableContentLength -= progress - offset;
        }

        assert client.decodableContentLength >= 0;

        if (client.decodableContentLength == 0)
        {
            client.onDecodeHttp2Trailers(traceId, authorization, EMPTY_OCTETS);
            client.decoder = decodeHttp11EmptyLines;
        }

        return progress;
    }

    private int decodeHttp11Trailers(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final int endOfTrailersAt = limitOfBytes(buffer, offset, limit, CRLFCRLF_BYTES);
        if (endOfTrailersAt != -1)
        {
            // TODO
            final HttpEndExFW httpEndEx = endExRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                                 .typeId(httpTypeId)
                                                 .build();

            client.onDecodeHttp2Trailers(traceId, authorization, httpEndEx);
            progress = endOfTrailersAt;
            client.decoder = decodeHttp11EmptyLines;
        }
        else if (buffer.getByte(offset) == '\r' &&
            buffer.getByte(offset + 1) == '\n')
        {
            client.onDecodeHttp2Trailers(traceId, authorization, EMPTY_OCTETS);
            progress += 2;
            client.decoder = decodeHttp11EmptyLines;
        }

        return progress;
    }

    private int decodeHttp11EmptyLines(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        if (limit - progress >= 2)
        {
            if (buffer.getByte(offset) == '\r' &&
                buffer.getByte(offset + 1) == '\n')
            {
                progress += 2;
            }
            else
            {
                client.decoder = decodeHttp11Headers;
            }
        }
        return progress;
    }

    private int decodeHttp11Ignore(
            HttpClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
    {
        client.doNetworkWindow(traceId, budgetId, 0, 0);
        return limit;
    }

    private int decodeHttp11Upgraded(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return client.onDecodeHttp11Body(traceId, authorization, budgetId, buffer, offset, limit, EMPTY_OCTETS);
    }

    private static int http2FramePadding(
        final int dataLength,
        final int maxFrameSize)
    {
        final int frameCount = (dataLength + maxFrameSize - 1) / maxFrameSize;

        return frameCount * Http2FrameInfoFW.SIZE_OF_FRAME; // assumes H2 DATA not PADDED
    }

    @FunctionalInterface
    private interface HttpClientDecoder
    {
        int decode(
            HttpClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private enum HttpEncoder
    {
        HTTP_1_1
        {
            @Override
            public void doEncodeRequestHeaders(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                long budgetId,
                Array32FW<HttpHeaderFW> headers,
                Map<String8FW, String16FW> overrides)
            {
                client.doEncodeHttp11Headers(traceId, authorization, budgetId, headers, overrides);
            }

            @Override
            public void doEncodeRequestData(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                int flags,
                long budgetId,
                int reserved,
                int length,
                OctetsFW payload)
            {
                client.doEncodeHttp1Body(exchange, traceId, authorization, flags, budgetId, reserved, length, payload);
            }

            @Override
            public void doEncodeRequestEnd(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                long budgetId, Array32FW<HttpHeaderFW> trailers)
            {
                client.doEncodeHttp1Trailers(exchange, traceId, authorization, 0L, trailers);
            }

            @Override
            public void doEncodeRequestAbort(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                client.doNetworkAbort(traceId, authorization);
                client.doNetworkReset(traceId, authorization);
                exchange.doResponseAbort(traceId, authorization, EMPTY_OCTETS);
            }

            @Override
            public void doEncodeResponseReset(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                client.cleanupNetwork(traceId, authorization);
            }

            @Override
            public void onNetworkWindow(
                HttpClient client,
                long traceId,
                long authorization,
                long budgetId,
                long acknowledge,
                int maximum,
                int padding)
            {
                client.onHttp11NetworkWindow(traceId, authorization, budgetId, acknowledge, maximum, padding);
            }

            @Override
            public void onApplicationBegin(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                exchange.doRequestWindow(traceId);
            }

            @Override
            public void onApplicationWindow(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                //NOOP
            }
        },
        HTTP_2
        {
            @Override
            public void doEncodeRequestHeaders(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                long budgetId,
                Array32FW<HttpHeaderFW> headers,
                Map<String8FW, String16FW> overrides)
            {
                client.doEncodeHttp2Headers(exchange, traceId, authorization, headers, overrides);
            }

            @Override
            public void doEncodeRequestData(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                int flags,
                long budgetId,
                int reserved,
                int length,
                OctetsFW payload)
            {
                client.doEncodeHttp2Payload(exchange, traceId, authorization, reserved, length, payload);
            }

            @Override
            public void doEncodeRequestEnd(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                long budgetId,
                Array32FW<HttpHeaderFW> trailers)
            {
                if (exchange.requestContentLength != exchange.requestContentObserved &&
                    exchange.requestContentLength != NO_CONTENT_LENGTH)
                {
                    client.doEncodeHttp2RstStream(traceId, exchange.streamId, Http2ErrorCode.NO_ERROR);
                    exchange.cleanup(traceId, authorization);
                }
                else if (exchange.requestContentLength == NO_CONTENT_LENGTH)
                {
                    client.doEncodeHttp2Trailers(exchange, traceId, authorization, trailers);
                }
            }

            @Override
            public void doEncodeRequestAbort(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                client.doEncodeHttp2RstStream(traceId, exchange.streamId, Http2ErrorCode.NO_ERROR);
                exchange.cleanup(traceId, authorization);
            }

            @Override
            public void doEncodeResponseReset(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                client.doEncodeHttp2RstStream(traceId, exchange.streamId, Http2ErrorCode.NO_ERROR);
                exchange.cleanup(traceId, authorization);
            }

            @Override
            public void onNetworkWindow(
                HttpClient client,
                long traceId,
                long authorization,
                long budgetId,
                long acknowledge,
                int maximum,
                int padding)
            {
                client.onHttp2NetworkWindow(traceId, authorization, budgetId, acknowledge, maximum, padding);
            }

            @Override
            public void onApplicationBegin(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                exchange.flushRequestWindow(traceId, 0);
            }

            @Override
            public void onApplicationWindow(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                client.onHttp2ApplicationWindow(exchange, traceId, authorization);
            }
        },
        H2C
        {
            @Override
            public void doEncodeRequestHeaders(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                long budgetId,
                Array32FW<HttpHeaderFW> headers,
                Map<String8FW, String16FW> overrides)
            {
                client.doEncodeH2cHeaders(traceId, authorization, budgetId, headers, overrides);
            }

            @Override
            public void doEncodeRequestData(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                int flags,
                long budgetId,
                int reserved,
                int length,
                OctetsFW payload)
            {
                client.doEncodeHttp1Body(exchange, traceId, authorization, flags, budgetId, reserved, length, payload);
            }

            @Override
            public void doEncodeRequestEnd(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization,
                long budgetId, Array32FW<HttpHeaderFW> trailers)
            {
                client.doEncodeHttp1Trailers(exchange, traceId, authorization, 0L, trailers);
            }

            @Override
            public void doEncodeRequestAbort(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                client.doNetworkAbort(traceId, authorization);
                client.doNetworkReset(traceId, authorization);
                exchange.doResponseAbort(traceId, authorization, EMPTY_OCTETS);
            }

            @Override
            public void doEncodeResponseReset(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                client.cleanupNetwork(traceId, authorization);
            }

            @Override
            public void onNetworkWindow(
                HttpClient client,
                long traceId,
                long authorization,
                long budgetId,
                long acknowledge,
                int maximum,
                int padding)
            {
                client.onHttp11NetworkWindow(traceId, authorization, budgetId, acknowledge, maximum, padding);
            }

            @Override
            public void onApplicationBegin(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                exchange.flushRequestWindow(traceId, 0);
            }

            @Override
            public void onApplicationWindow(
                HttpClient client,
                HttpExchange exchange,
                long traceId,
                long authorization)
            {
                //NOOP
            }
        };

        public abstract void doEncodeRequestHeaders(
            HttpClient client,
            HttpExchange exchange,
            long traceId,
            long authorization,
            long budgetId,
            Array32FW<HttpHeaderFW> headers,
            Map<String8FW, String16FW> overrides);

        public abstract void doEncodeRequestData(
            HttpClient client,
            HttpExchange exchange,
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            int length,
            OctetsFW payload);

        public abstract void doEncodeRequestEnd(
            HttpClient client,
            HttpExchange exchange,
            long traceId,
            long authorization,
            long budgetId,
            Array32FW<HttpHeaderFW> trailers);

        public abstract void doEncodeRequestAbort(
            HttpClient client,
            HttpExchange exchange,
            long traceId,
            long authorization);

        public abstract void doEncodeResponseReset(
            HttpClient client,
            HttpExchange exchange,
            long traceId,
            long authorization);

        public abstract void onNetworkWindow(
            HttpClient client,
            long traceId,
            long authorization,
            long budgetId,
            long acknowledge,
            int maximum,
            int padding);

        public abstract void onApplicationBegin(
            HttpClient client,
            HttpExchange exchange,
            long traceId,
            long authorization
        );

        public abstract void onApplicationWindow(
            HttpClient client,
            HttpExchange exchange,
            long traceId,
            long authorization
        );
    }

    private int decodeHttp2Settings(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        if (limit > offset)
        {
            final Http2SettingsFW http2Settings = http2SettingsRO.wrap(buffer, offset, limit);
            final int streamId = http2Settings.streamId();
            final boolean ack = http2Settings.ack();
            final int length = http2Settings.length();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;
            if (ack && length != 0)
            {
                error = Http2ErrorCode.FRAME_SIZE_ERROR;
            }
            else if (streamId != 0)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                client.onDecodeHttp2Error(traceId, authorization, error);
                client.decoder = decodeHttp2IgnoreAll;
            }
            else
            {
                client.onDecodeHttp2Settings(traceId, authorization, http2Settings);
                client.decoder = decodeHttp2FrameType;
                progress = http2Settings.limit();
            }
        }

        return progress;
    }

    private int decodeHttp2IgnoreAll(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    private int decodeHttp2FrameType(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.tryWrap(buffer, offset, limit);

        if (http2FrameInfo != null)
        {
            final int length = http2FrameInfo.length();
            final Http2FrameType type = http2FrameInfo.type();
            final HttpClientDecoder decoder = decodersByFrameType.getOrDefault(type, decodeHttp2IgnoreOne);
            client.decodedStreamId = http2FrameInfo.streamId();
            client.decodedFlags = http2FrameInfo.flags();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

            if (length > client.localSettings.maxFrameSize)
            {
                error = Http2ErrorCode.FRAME_SIZE_ERROR;
            }
            else if (decoder == null || client.continuationStreamId != 0 && decoder != decodeHttp2Continuation)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                client.onDecodeHttp2Error(traceId, authorization, error);
                client.decoder = decodeHttp2IgnoreAll;
            }
            else if (limit - http2FrameInfo.limit() >= length)
            {
                client.decoder = decoder;
            }
        }

        return offset;
    }

    private int decodeHttp2Ping(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.wrap(buffer, offset, limit);
        final int streamId = http2FrameInfo.streamId();
        final int length = http2FrameInfo.length();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if (length != 8)
        {
            error = Http2ErrorCode.FRAME_SIZE_ERROR;
        }
        else if (streamId != 0)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            final Http2PingFW http2Ping = http2PingRO.wrap(buffer, offset, limit);
            client.onDecodePing(traceId, authorization, http2Ping);
            client.decoder = decodeHttp2FrameType;
            progress = http2Ping.limit();
        }

        return progress;
    }

    private int decodeHttp2Goaway(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2GoawayFW http2Goaway = http2GoawayRO.wrap(buffer, offset, limit);
        final int streamId = http2Goaway.streamId();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;
        if (streamId != 0)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            client.onHttp2DecodeGoaway(traceId, authorization, http2Goaway);
            client.decoder = decodeHttp2IgnoreAll;
            progress = http2Goaway.limit();
        }

        return progress;
    }

    private int decodeHttp2WindowUpdate(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.wrap(buffer, offset, limit);
        final int length = http2FrameInfo.length();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if (length != 4)
        {
            error = Http2ErrorCode.FRAME_SIZE_ERROR;
        }

        if (error == Http2ErrorCode.NO_ERROR)
        {
            final Http2WindowUpdateFW http2WindowUpdate = http2WindowUpdateRO.wrap(buffer, offset, limit);
            final int streamId = http2WindowUpdate.streamId();
            final int size = http2WindowUpdate.size();

            if (streamId == 0)
            {
                if (client.remoteSharedBudget + size > Integer.MAX_VALUE)
                {
                    error = Http2ErrorCode.FLOW_CONTROL_ERROR;
                }
            }
            else
            {
                if (streamId > client.maxClientStreamId || size < 1)
                {
                    error = Http2ErrorCode.PROTOCOL_ERROR;
                }
            }

            if (error == Http2ErrorCode.NO_ERROR)
            {
                client.onDecodeHttp2WindowUpdate(traceId, authorization, http2WindowUpdate);
                client.decoder = decodeHttp2FrameType;
                progress = http2WindowUpdate.limit();
            }
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }

        return progress;
    }

    private int decodeHttp2Headers(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2HeadersFW http2Headers = http2HeadersRO.wrap(buffer, offset, limit);
        final int streamId = http2Headers.streamId();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            if (client.applicationHeadersProcessed.size() < config.maxConcurrentApplicationHeaders())
            {
                final HttpExchange exchange = client.pool.exchanges.get(streamId);
                if (HttpState.replyOpening(exchange.state))
                {
                    client.onDecodeHttp2Trailers(traceId, authorization, http2Headers);
                }
                else
                {
                    client.onDecodeHttp2Headers(traceId, authorization, http2Headers);
                }
                client.decoder = decodeHttp2FrameType;
                progress = http2Headers.limit();
            }
        }

        return progress;
    }

    private int decodeHttp2Continuation(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2ContinuationFW http2Continuation = http2ContinuationRO.wrap(buffer, offset, limit);
        final int streamId = http2Continuation.streamId();
        final int length = http2Continuation.length();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if ((streamId & 0x01) != 0x01 ||
                streamId != client.continuationStreamId)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (client.headersSlotOffset + length > headersPool.slotCapacity())
        {
            // TODO: decoded header list size check, recoverable error instead
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            client.onDecodeHttp2Continuation(traceId, authorization, http2Continuation);
            client.decoder = decodeHttp2FrameType;
            progress = http2Continuation.limit();
        }

        return progress;
    }

    private int decodeHttp2Data(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final int length = limit - progress;

        if (length != 0)
        {
            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;
            Http2DataFW http2Data = http2DataRO.wrap(buffer, offset, limit);

            if (error != Http2ErrorCode.NO_ERROR)
            {
                client.onDecodeHttp2Error(traceId, authorization, error);
                client.decoder = decodeHttp2IgnoreAll;
            }
            else
            {
                client.decodableDataBytes = http2Data.dataLength();
                progress = http2Data.dataOffset();

                client.decoder = decodeHttp2DataPayload;
            }
        }

        return progress;
    }

    private int decodeHttp2DataPayload(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final int available = limit - progress;
        final int decodableMax = Math.min(client.decodableDataBytes, bufferPool.slotCapacity());
        final int length = Math.min(available, decodableMax);

        if (available >= decodableMax)
        {
            payloadRO.wrap(buffer, progress, length);
            final int deferred = client.decodableDataBytes - length;

            final int decodedPayload = client.onDecodeHttp2Data(
                    traceId,
                    authorization,
                    client.decodedStreamId,
                    client.decodedFlags,
                    deferred,
                    payloadRO);
            client.decodableDataBytes -= decodedPayload;
            progress += decodedPayload;
        }

        if (client.decodableDataBytes == 0)
        {
            client.decoder = decodeHttp2FrameType;
        }

        return progress;
    }

    private int decodeHttp2PusPromise(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2PushPromiseFW http2PushPromise = http2PushPromiseRO.wrap(buffer, offset, limit);
        final int streamId = http2PushPromise.streamId();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if ((streamId & 0x01) != 0x01)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            if (client.applicationHeadersProcessed.size() < config.maxConcurrentApplicationHeaders())
            {
                client.onDecodeHttp2PushPromise(traceId, authorization, http2PushPromise);
                client.decoder = decodeHttp2FrameType;
                progress = http2PushPromise.limit();
            }
        }

        return progress;
    }

    private int decodeHttp2Priority(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.wrap(buffer, offset, limit);
        final int streamId = http2FrameInfo.streamId();
        final int length = http2FrameInfo.length();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if (length != 5)
        {
            error = Http2ErrorCode.FRAME_SIZE_ERROR;
        }
        else if (streamId == 0)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            final Http2PriorityFW http2Priority = http2PriorityRO.wrap(buffer, offset, limit);
            client.onDecodeHttp2Priority(traceId, authorization, http2Priority);
            client.decoder = decodeHttp2FrameType;
            progress = http2Priority.limit();
        }

        return progress;
    }

    private int decodeHttp2RstStream(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.wrap(buffer, offset, limit);
        final int streamId = http2FrameInfo.streamId();
        final int length = http2FrameInfo.length();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if (streamId == 0)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (length != 4)
        {
            error = Http2ErrorCode.FRAME_SIZE_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            client.onDecodeHttp2Error(traceId, authorization, error);
            client.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            if (client.applicationHeadersProcessed.size() < config.maxConcurrentApplicationHeaders())
            {
                final Http2RstStreamFW http2RstStream = http2RstStreamRO.wrap(buffer, offset, limit);
                client.onDecodeHttp2RstStream(traceId, authorization, http2RstStream);
                client.decoder = decodeHttp2FrameType;
                progress = http2RstStream.limit();
            }
        }

        return progress;
    }

    private int decodeHttp2IgnoreOne(
        HttpClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.wrap(buffer, offset, limit);
        final int progress = http2FrameInfo.limit() + http2FrameInfo.length();

        client.decoder = decodeHttp2FrameType;
        return progress;
    }

    private final class HttpClientPool
    {
        private final long bindingId;
        private final long resolvedId;
        private final Int2ObjectHashMap<HttpExchange> exchanges;
        private final List<HttpClient> clients;

        private int httpQueueSlot = NO_SLOT;
        private int httpQueueSlotOffset;
        private int httpQueueSlotLimit;
        private SortedSet<HttpVersion> versions;

        private HttpClientPool(
            long bindingId,
            long resolvedId)
        {
            this.bindingId = bindingId;
            this.resolvedId = resolvedId;
            this.clients = new LinkedList<>();
            this.exchanges = new Int2ObjectHashMap<>();
        }

        public MessageConsumer newStream(
            BeginFW begin,
            MessageConsumer sender,
            Map<String8FW, String16FW> overrides,
            SortedSet<HttpVersion> versions)
        {
            // count all requests
            countRequests.getAsLong();

            this.versions = versions;

            MessageConsumer newStream;

            final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

            HttpClient client = supplyClient();
            final int queuedRequestLength = HttpQueueEntryFW.FIELD_OFFSET_VALUE_LENGTH + begin.extension().sizeof();

            if (client == null || queuedRequestLength > maximumRequestQueueSize)
            {
                newStream = rejectWithStatusCode(sender, begin, HEADERS_431_REQUEST_TOO_LARGE);
            }
            else if (queuedRequestLength <= availableSlotSize())
            {
                final HttpPromise promise = client.promises.stream().filter(p -> p.matches(beginEx.headers()))
                        .findFirst().orElse(null);

                final long originId = begin.originId();
                final long routedId = begin.routedId();
                final long initialId = begin.streamId();
                final long authorization = begin.authorization();

                if (promise != null)
                {
                    final HttpExchange exchange = exchanges.get(promise.promiseId);
                    newStream = exchange::onApplication;
                    client.promises.remove(promise);
                }
                else
                {
                    final int nextStreamId = client.nextStreamId();
                    final HttpExchange exchange = client.newExchange(sender, originId, routedId, initialId, authorization,
                            overrides, nextStreamId);
                    exchanges.put(nextStreamId, exchange);
                    newStream = exchange::onApplication;
                }
            }
            else
            {
                newStream = rejectWithStatusCode(sender, begin, HEADERS_503_RETRY_AFTER);
            }

            return newStream;
        }

        private MessageConsumer rejectWithStatusCode(
            MessageConsumer sender,
            BeginFW begin,
            Array32FW<HttpHeaderFW> headers)
        {
            MessageConsumer newStream;
            // count all responses
            countResponses.getAsLong();

            final long originId = begin.originId();
            final long routedId = begin.routedId();
            final long initialId = begin.streamId();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long replyId = supplyReplyId.applyAsLong(initialId);

            doWindow(sender, originId, routedId, initialId, 0, 0, 0, traceId, authorization, 0L, 0);

            HttpBeginExFW beginEx = beginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headers(headers)
                    .build();

            doBegin(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    supplyTraceId.getAsLong(), 0L, 0, beginEx);
            doEnd(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    supplyTraceId.getAsLong(), 0, EMPTY_OCTETS);

            // count rejected requests (no connection or no space in the queue)
            countRequestsRejected.getAsLong();

            // ignore DATA, FLUSH, END, ABORT
            newStream = (t, b, i, l) -> {};
            return newStream;
        }

        private void flushNext()
        {
            dequeue:
            while (httpQueueSlotOffset != httpQueueSlotLimit)
            {
                final MutableDirectBuffer httpQueueBuffer = bufferPool.buffer(httpQueueSlot);
                final HttpQueueEntryFW queueEntry = queueEntryRO.wrap(httpQueueBuffer, httpQueueSlotOffset, httpQueueSlotLimit);
                final int streamId = queueEntry.streamId();
                final long traceId = queueEntry.traceId();
                final long authorization = queueEntry.authorization();
                final HttpExchange httpExchange = exchanges.get(streamId);
                HttpClient client = null;

                if (httpExchange != null)
                {
                    final HttpEncoder encoder = httpExchange.client.encoder;
                    client = encoder == HttpEncoder.HTTP_2 ||
                            encoder == HttpEncoder.HTTP_1_1 && httpExchange.client.exchange == null ||
                            encoder == HttpEncoder.H2C ?
                            httpExchange.client : supplyClient();

                    if (client != null)
                    {
                        httpExchange.doRequestBegin(traceId, authorization, queueEntry.value());
                    }
                    else
                    {
                        httpExchange.doResponseAbort(traceId, authorization, EMPTY_OCTETS);
                    }
                }
                httpQueueSlotOffset += queueEntry.sizeof();
                dequeues.getAsLong();

                if (client != null && client.encoder == HttpEncoder.H2C)
                {
                    break dequeue;
                }
            }

            cleanupQueueSlotIfNecessary();
        }

        private void cleanupQueueSlotIfNecessary()
        {
            if (httpQueueSlot != NO_SLOT && httpQueueSlotOffset == httpQueueSlotLimit)
            {
                bufferPool.release(httpQueueSlot);
                httpQueueSlot = NO_SLOT;
                httpQueueSlotOffset = 0;
                httpQueueSlotLimit = 0;
            }
        }

        private void acquireQueueSlotIfNecessary()
        {
            if (httpQueueSlot == NO_SLOT)
            {
                httpQueueSlot = bufferPool.acquire(resolvedId);
            }
        }

        private HttpClient supplyClient()
        {
            HttpClient client = null;
            if (clients.size() == 1)
            {
                client = clients.stream().filter(
                        c -> !HttpState.replyOpened(c.state) ||
                        c.encoder == HttpEncoder.HTTP_2 ||
                        c.exchange == null && c.encoder == HttpEncoder.HTTP_1_1 ||
                        c.encoder == HttpEncoder.H2C)
                        .findFirst()
                        .orElse(null);
            }

            if (client == null && clients.size() < maximumConnectionsPerRoute)
            {
                client = new HttpClient(this);
                onCreated(client);
            }

            return client;
        }

        private void onCreated(
            HttpClient client)
        {
            if (clients.add(client))
            {
                connectionInUse.accept(1L);
            }

            assert clients.size() <= maximumConnectionsPerRoute;
        }

        private void onUpgradedOrClosed(
            HttpClient client)
        {
            if (clients.remove(client))
            {
                connectionInUse.accept(-1L);
            }

            assert clients.size() <= maximumConnectionsPerRoute;
        }

        private int availableSlotSize()
        {
            return bufferPool.slotCapacity() - httpQueueSlotLimit;
        }
    }

    private final class HttpClient
    {
        private final HttpClientPool pool;
        private final long originId;
        private final long routedId;
        private final long replyId;
        private final long initialId;
        private long budgetId;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private long replyAuth;

        private int decodedStreamId;
        private byte decodedFlags;
        private int continuationStreamId;
        private int remoteSharedBudget;
        private int maxClientStreamId = -1;
        private int decodableDataBytes;
        private MessageConsumer network;
        private int decodeSlot;
        private int decodeSlotOffset;
        private int decodeSlotReserved;
        private long decodeSlotBudgetId;
        private HttpClientDecoder decoder;
        private Http2ErrorCode decodeError;
        private int decodableChunkSize;
        private int decodableContentLength;

        private final MutableDirectBuffer encodeHeadersBuffer;
        private final MutableDirectBuffer encodeReservedBuffer;
        private int encodeSlot;
        private int encodeSlotOffset;
        private int encodeSlotMarkOffset;
        private int encodeHeadersSlotOffset;
        private long encodeHeadersSlotTraceId;
        private int headersSlot = NO_SLOT;
        private int headersSlotOffset;
        private int encodeHeadersSlotMarkOffset;
        private int encodeReservedSlotOffset;
        private long encodeReservedSlotTraceId;
        private int encodeReservedSlotMarkOffset;
        private HttpEncoder encoder = HttpEncoder.HTTP_1_1;
        private int initialBudgetReserved;

        private final long[] streamsActive = new long[2];
        private final MutableBoolean expectDynamicTableSizeUpdate = new MutableBoolean(true);
        private final Http2Settings localSettings;
        private final Http2Settings remoteSettings;
        private final HpackContext decodeContext;
        private final HpackContext encodeContext;
        private final LongHashSet applicationHeadersProcessed;

        private final List<HttpPromise> promises;
        private HttpExchange exchange;
        private LongLongConsumer cleanupHandler;
        private int requestSharedBudget;
        private int encodeSlotReserved;
        private int initialSharedBudget;
        private long requestSharedBudgetIndex = NO_CREDITOR_INDEX;
        private String protocolUpgrade = null;

        private HttpClient(
            HttpClientPool pool)
        {
            this.pool = pool;
            this.originId = pool.bindingId;
            this.routedId = pool.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.budgetId = 0;
            this.decoder = decodeHttp11EmptyLines;
            this.localSettings = new Http2Settings();
            this.remoteSettings = new Http2Settings();
            this.applicationHeadersProcessed = new LongHashSet();
            this.promises = new ArrayList<>(maximumPushPromiseListSize);
            this.decodeContext = new HpackContext(localSettings.headerTableSize, false);
            this.encodeContext = new HpackContext(remoteSettings.headerTableSize, true);
            this.encodeHeadersBuffer = new ExpandableArrayBuffer();
            this.encodeReservedBuffer = new ExpandableArrayBuffer();
            this.decodeSlot = NO_SLOT;
            this.encodeSlot = NO_SLOT;
        }

        public int initialPendingAck()
        {
            return (int)(initialSeq - initialAck);
        }

        private int initialWindow()
        {
            return initialMax - initialPendingAck();
        }

        private HttpExchange newExchange(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            Map<String8FW, String16FW> overrides,
            int streamId)
        {
            return new HttpExchange(this, sender, originId, routedId, initialId, authorization, overrides, streamId);
        }

        private void addNewPromise(
            long traceId,
            int promisedStreamId,
            Map<String, String> headers)
        {
            final String method = headers.get(METHOD);
            final String path = headers.get(PATH);
            final String scheme = headers.get(SCHEME);
            final String authority = headers.get(AUTHORITY);
            final HttpPromise promise = new HttpPromise(promisedStreamId, method, path, scheme, authority);

            if (promises.size() == maximumPushPromiseListSize)
            {
                final HttpPromise oldPromise = promises.remove(0);
                final HttpExchange httpExchange = pool.exchanges.remove(oldPromise.promiseId);
                doEncodeHttp2RstStream(traceId, httpExchange.streamId, Http2ErrorCode.CANCEL);

            }
            promises.add(promise);
        }

        private void onNetwork(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetworkBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetworkData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetworkEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetworkAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetworkWindow(window);
                break;
            }
        }

        private void onNetworkBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            replyAuth = authorization;

            final ExtensionFW extension = begin.extension().get(extensionRO::tryWrap);
            final ProxyBeginExFW beginEx = extension != null && extension.typeId() == proxyTypeId
                    ? begin.extension().get(beginProxyExRO::tryWrap)
                    : null;

            if (beginEx != null &&
                beginEx.infos().anyMatch(proxyInfo -> PROXY_ALPN_H2.equals(proxyInfo.alpn())) ||
                pool.versions.size() == 1 && pool.versions.contains(HTTP_2))
            {
                remoteSharedBudget = encodeMax;
                for (HttpExchange exchange: pool.exchanges.values())
                {
                    exchange.remoteBudget += encodeMax;
                    exchange.flushRequestWindow(traceId, 0);
                }

                assert !HttpState.initialOpened(state);
                this.budgetId = supplyBudgetId.getAsLong();
                assert requestSharedBudgetIndex == NO_CREDITOR_INDEX;
                requestSharedBudgetIndex = creditor.acquire(budgetId);

                doEncodeHttp2Preface(traceId, authorization);
                doEncodeHttp2Settings(traceId, authorization);

                this.decoder = decodeHttp2Settings;
                this.encoder = HttpEncoder.HTTP_2;
            }
            else if (beginEx == null &&
                     pool.versions.contains(HTTP_1_1) &&
                     pool.versions.contains(HTTP_2))
            {
                this.encoder = HttpEncoder.H2C;
                for (HttpExchange exchange: pool.exchanges.values())
                {
                    exchange.remoteBudget += encodeMax;
                }
            }

            doNetworkWindow(traceId, 0L, 0, 0);

            pool.flushNext();
        }

        private void onNetworkData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + data.reserved();
            replyAuth = authorization;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + decodeMax)
            {
                cleanupNetwork(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();
                int reserved = data.reserved();
                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;
                    decodeSlotBudgetId = budgetId;
                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onNetworkEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = HttpState.closingReply(state);

            pool.exchanges.forEach((id, exchange) ->
            {
                if (!HttpState.replyOpening(exchange.state) || decodeSlot == NO_SLOT)
                {
                    exchange.cleanup(traceId, authorization);
                    cleanupDecodeSlotIfNecessary();
                }
            });

            if (decodeSlot == NO_SLOT)
            {
                state = HttpState.closeReply(state);
                doNetworkEnd(traceId, authorization);
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = HttpState.closeReply(state);

            cleanupDecodeSlotIfNecessary();

            pool.exchanges.forEach((id, exchange) -> exchange.cleanup(traceId, authorization));

            if (!HttpState.initialClosing(state))
            {
                state = HttpState.closingInitial(state);
                cleanup(traceId, authorization, this::doNetworkAbort);
            }
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = HttpState.closeInitial(state);

            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            pool.exchanges.forEach((id, exchange) -> exchange.cleanup(traceId, authorization));

            if (!HttpState.replyClosing(state))
            {
                state = HttpState.closingReply(state);
                cleanup(traceId, authorization, this::doNetworkReset);
            }
        }

        private void onNetworkWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            encoder.onNetworkWindow(this, traceId, authorization, budgetId, acknowledge, maximum, padding);
        }

        private void flushNetworkIfBuffered(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = bufferPool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;
                final int reserved = limit + initialPad;
                doNetworkData(traceId, authorization, budgetId, reserved, buffer, 0, limit);
            }
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!HttpState.initialOpening(state))
            {
                state = HttpState.openingInitial(state);

                network = newStream(this::onNetwork, originId, routedId, initialId, initialSeq, initialAck,
                    initialMax, traceId, authorization, affinity, EMPTY_OCTETS);
            }
        }

        private void doNetFlush(
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(network, originId, routedId, initialId, initialSeq, initialAck,
                initialMax, traceId, replyAuth, budgetId, reserved, extension);
        }

        private void doHttp2NetworkData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = bufferPool.acquire(replyId);
            }

            if (encodeSlot == NO_SLOT)
            {
                cleanupNetwork(traceId, authorization);
            }
            else
            {
                final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotReserved += reserved;

                if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                {
                    System.out.format("[%d] [doNetworkData] [0x%016x] [0x%016x] encodeSlotOffset %d => %d " +
                                    "encodeSlotReserved=%d\n",
                            System.nanoTime(), traceId, budgetId, limit - offset, encodeSlotOffset, encodeSlotReserved);
                }

                encodeNetwork(traceId, authorization, budgetId);
            }

        }

        private void doNetworkData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int length = Math.max(Math.min(initialWindow() - initialPad, maxLength), 0);

            if (length > 0)
            {
                final int required = length + initialPad;

                assert reserved >= required;

                doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, required, buffer, offset, length, EMPTY_OCTETS);

                initialSeq += required;

                assert initialSeq <= initialAck + initialMax :
                    String.format("%d <= %d + %d", initialSeq, initialAck, initialMax);
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = bufferPool.acquire(replyId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlotIfNecessary();
            }
        }

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            if (!HttpState.initialClosed(state))
            {
                state = HttpState.closeInitial(state);

                cleanupBudgetCreditorIfNecessary();
                cleanupEncodeSlotIfNecessary();

                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_OCTETS);
            }

            if (HttpState.closed(state))
            {
                pool.onUpgradedOrClosed(this);
            }
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            if (!HttpState.initialClosed(state))
            {
                state = HttpState.closeInitial(state);

                cleanupBudgetCreditorIfNecessary();
                cleanupEncodeSlotIfNecessary();

                doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, EMPTY_OCTETS);

                if (HttpState.closed(state))
                {
                    pool.onUpgradedOrClosed(this);
                }
            }
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            if (!HttpState.replyClosed(state))
            {
                state = HttpState.closeReply(state);
                cleanupDecodeSlotIfNecessary();
                bindings.remove(replyId);
                doReset(network, originId, routedId, replyId, replySeq, replyAck, initialMax,
                        traceId, authorization);

                if (HttpState.closed(state))
                {
                    pool.onUpgradedOrClosed(this);
                }
            }
        }

        private void doNetworkWindow(
            long traceId,
            long budgetId,
            int padding,
            int minReplyNoAck)
        {
            final long newReplyAck = Math.min(replySeq - minReplyNoAck, replySeq);

            if (newReplyAck > replyAck || !HttpState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                state = HttpState.openReply(state);

                doWindow(network, originId, routedId, replyId,  replySeq, replyAck, decodeMax,
                        traceId, replyAuth, budgetId, padding);
            }
        }

        private void decodeNetworkIfBuffered(
            long traceId,
            long authorization)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer decodeBuffer = bufferPool.buffer(decodeSlot);
                final int decodeLength = decodeSlotOffset;
                decodeNetwork(traceId, authorization, decodeSlotBudgetId, decodeSlotReserved, decodeBuffer, 0, decodeLength);
            }
        }

        private void decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            HttpClientDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, reserved, buffer, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer decodeBuffer = bufferPool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (int)((long) reserved * (limit - progress) / (limit - offset));
                }
            }
            else
            {
                cleanupDecodeSlotIfNecessary();

                if (decoder == decodeHttp11Ignore)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else if (HttpState.replyClosing(state))
                {
                    state = HttpState.closeReply(state);

                    pool.exchanges.forEach((id, exchange) -> exchange.cleanup(traceId, authorization));

                    doNetworkEnd(traceId, authorization);
                }
            }

            if (exchange != null && !HttpState.replyClosed(exchange.state))
            {
                doNetworkWindow(traceId, budgetId, exchange.responsePad, decodeSlotReserved);
            }
        }

        private void onDecodeHttp11HeadersError(
            long traceId,
            long authorization)
        {
            cleanupNetwork(traceId, authorization);
        }

        private void onDecodeHttp11BodyError(
            long traceId,
            long authorization)
        {
            cleanupNetwork(traceId, authorization);
        }

        private void onDecodeHttp11Headers(
            long traceId,
            long authorization,
            HttpBeginExFW beginEx)
        {
            exchange.doResponseBegin(traceId, authorization, beginEx);

            final HttpHeaderFW connection = beginEx.headers().matchFirst(h -> HEADER_CONNECTION.equals(h.name()));
            if (connection != null && connectionClose.reset(connection.value().asString()).matches())
            {
                exchange.state = HttpState.closingReply(exchange.state);
            }

            final HttpHeaderFW status = beginEx.headers().matchFirst(h -> HEADER_STATUS.equals(h.name()));
            if (status != null &&
                encoder == HttpEncoder.HTTP_1_1 &&
                STATUS_101.equals(status.value()))
            {
                pool.onUpgradedOrClosed(this);
            }
        }

        private void onDecodeHttp11HeadersOnly(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            exchange.doResponseEnd(traceId, authorization, extension);
        }

        private int onDecodeHttp11Body(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit,
            Flyweight extension)
        {
            return exchange.doResponseData(traceId, authorization, buffer, offset, limit, extension);
        }

        private void onDecodeHttp2Trailers(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            exchange.doResponseEnd(traceId, authorization, extension);
        }

        private void doEncodeH2cHeaders(
            long traceId,
            long authorization,
            long budgetId,
            Array32FW<HttpHeaderFW> headers,
            Map<String8FW, String16FW> overrides)
        {
            final Map<String8FW, String16FW> headersMap = new LinkedHashMap<>();
            headers.forEach(h -> headersMap.put(newString8FW(h.name()), newString16FW(h.value())));
            headersMap.putAll(overrides);

            headersMap.put(HEADER_UPGRADE, UPGRADE_H2C);
            headersMap.put(HEADER_CONNECTION, CONNECTION_UPGRADE_HTTP2_SETTINGS);
            headersMap.put(HEADER_HTTP2_SETTINGS, h2cSettingsPayload);

            doEncodeHttp11Headers(traceId, authorization, budgetId, headersMap);
        }

        private void doEncodeHttp11Headers(
            long traceId,
            long authorization,
            long budgetId,
            Array32FW<HttpHeaderFW> headers,
            Map<String8FW, String16FW> overrides)
        {
            headersMap.clear();
            headers.forEach(h -> headersMap.put(newString8FW(h.name()), newString16FW(h.value())));
            headersMap.putAll(overrides);
            doEncodeHttp11Headers(traceId, authorization, budgetId, headersMap);
        }

        private void doEncodeHttp11Headers(
            long traceId,
            long authorization,
            long budgetId,
            Map<String8FW, String16FW> headers)
        {
            final String16FW contentLength = headers.get(HEADER_CONTENT_LENGTH);
            exchange.requestRemaining = contentLength != null ? parseInt(contentLength.asString()) : Integer.MAX_VALUE;

            final String16FW transferEncoding = headers.get(HEADER_TRANSFER_ENCODING);
            exchange.requestChunked = TRANSFER_ENCODING_CHUNKED.equals(transferEncoding);

            final String16FW connection = headers.get(HEADER_CONNECTION);
            final String16FW upgrade = headers.get(HEADER_UPGRADE);

            this.protocolUpgrade = upgrade != null ? upgrade.asString() : null;

            if (connection != null && connectionClose.reset(connection.asString()).matches() || upgrade != null)
            {
                exchange.state = HttpState.closingReply(exchange.state);
            }

            codecOffset.value = doEncodeStart(codecBuffer, 0, headers);
            codecOffset.value = doEncodeHost(codecBuffer, codecOffset.value, headers);
            headers.forEach((n, v) -> codecOffset.value = doEncodeHeader(codecBuffer, codecOffset.value, n, v));
            codecBuffer.putBytes(codecOffset.value, CRLF_BYTES);
            codecOffset.value += CRLF_BYTES.length;

            final int length = codecOffset.value;

            final int reserved = length + initialPad;
            doNetworkData(traceId, authorization, budgetId, reserved, codecBuffer, 0, length);
        }

        private int doEncodeHost(
            MutableDirectBuffer buffer,
            int offset,
            Map<String8FW, String16FW> headersMap)
        {
            int progress = offset;

            final String16FW authority = headersMap.get(HEADER_AUTHORITY);
            if (authority != null)
            {
                final DirectBuffer authorityValue = authority.value();

                codecBuffer.putBytes(progress, HOST_BYTES);
                progress += HOST_BYTES.length;
                codecBuffer.putBytes(progress, COLON_SPACE_BYTES);
                progress += COLON_SPACE_BYTES.length;
                codecBuffer.putBytes(progress, authorityValue, 0, authorityValue.capacity());
                progress += authorityValue.capacity();
                codecBuffer.putBytes(progress, CRLF_BYTES);
                progress += CRLF_BYTES.length;
            }

            return progress;
        }

        private int doEncodeStart(
            MutableDirectBuffer buffer,
            int offset,
            Map<String8FW, String16FW> headersMap)
        {
            int progress = offset;

            final DirectBuffer method = headersMap.getOrDefault(HEADER_METHOD, METHOD_GET).value();
            codecBuffer.putBytes(progress, method, 0, method.capacity());
            progress += method.capacity();

            codecBuffer.putByte(progress, SPACE_BYTE);
            progress++;

            final DirectBuffer path = headersMap.getOrDefault(HEADER_PATH, PATH_SLASH).value();
            codecBuffer.putBytes(progress, path, 0, path.capacity());
            progress += path.capacity();

            codecBuffer.putByte(progress, SPACE_BYTE);
            progress++;

            codecBuffer.putBytes(progress, HTTP_1_1_BYTES);
            progress += HTTP_1_1_BYTES.length;

            codecBuffer.putBytes(progress, CRLF_BYTES);
            progress += CRLF_BYTES.length;

            return progress;
        }

        private int doEncodeHeader(
            MutableDirectBuffer buffer,
            int offset,
            String8FW headerName,
            String16FW headerValue)
        {
            int progress = offset;
            final DirectBuffer name = headerName.value();
            if (name.getByte(0) != COLON_BYTE)
            {
                final DirectBuffer value = headerValue.value();

                boolean uppercase = true;
                for (int pos = 0, len = name.capacity(); pos < len; pos++, progress++)
                {
                    byte ch = name.getByte(pos);
                    if (uppercase)
                    {
                        ch = (byte) toUpperCase(ch);
                    }
                    else
                    {
                        ch |= (byte) toLowerCase(ch);
                    }
                    buffer.putByte(progress, ch);
                    uppercase = ch == HYPHEN_BYTE;
                }

                buffer.putBytes(progress, COLON_SPACE_BYTES);
                progress += COLON_SPACE_BYTES.length;
                buffer.putBytes(progress, value, 0, value.capacity());
                progress += value.capacity();
                buffer.putBytes(progress, CRLF_BYTES);
                progress += CRLF_BYTES.length;
            }
            return progress;
        }

        private void doEncodeHttp1Body(
            HttpExchange exchange,
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            int length,
            OctetsFW payload)
        {
            exchange.requestRemaining -= length;
            assert exchange.requestRemaining >= 0;

            DirectBuffer buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();

            if (exchange.requestChunked && flags != 0)
            {
                int chunkLimit = 0;

                if ((flags & 0x01) != 0)
                {
                    final String chunkSizeHex = Integer.toHexString(payload.sizeof());
                    chunkLimit += codecBuffer.putStringWithoutLengthAscii(chunkLimit, chunkSizeHex);
                    codecBuffer.putBytes(chunkLimit, CRLF_BYTES);
                    chunkLimit += 2;
                }

                codecBuffer.putBytes(chunkLimit, payload.buffer(), payload.offset(), payload.sizeof());

                if ((flags & 0x02) != 0)
                {
                    codecBuffer.putBytes(chunkLimit, CRLF_BYTES);
                    chunkLimit += 2;
                }

                buffer = codecBuffer;
                offset = 0;
                limit = chunkLimit;
            }

            doNetworkData(traceId, authorization, budgetId, reserved, buffer, offset, limit);
        }

        private void doEncodeHttp1Trailers(
            HttpExchange exchange,
            long traceId,
            long authorization,
            long budgetId,
            Array32FW<HttpHeaderFW> trailers)
        {
            if (exchange.requestChunked)
            {
                DirectBuffer buffer = ZERO_CHUNK;
                int offset = 0;
                int limit = ZERO_CHUNK.capacity();

                if (!trailers.isEmpty())
                {
                    codecOffset.value = 0;
                    codecBuffer.putByte(codecOffset.value, ZERO_BYTE);
                    codecOffset.value++;
                    codecBuffer.putBytes(codecOffset.value, CRLF_BYTES);
                    codecOffset.value += CRLF_BYTES.length;
                    trailers.forEach(
                            h -> codecOffset.value = doEncodeHeader(writeBuffer, codecOffset.value, h.name(), h.value()));
                    codecBuffer.putBytes(codecOffset.value, CRLF_BYTES);
                    codecOffset.value += CRLF_BYTES.length;

                    buffer = codecBuffer;
                    limit = codecOffset.value;
                }

                final int reserved = limit + initialPad;
                doNetworkData(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }

            if (HttpState.closed(exchange.state))
            {
                exchange.onExchangeClosed();
            }
        }

        private void onHttp11NetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            long acknowledge,
            int maximum,
            int padding)
        {
            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            flushNetworkIfBuffered(traceId, authorization, budgetId);

            if (exchange != null && !HttpState.initialClosed(exchange.state))
            {
                exchange.doRequestWindow(traceId);
            }
        }

        private void doEncodeHttp2Preface(
            long traceId,
            long authorization)
        {
            final Http2PrefaceFW http2Preface = http2PrefaceRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .preface()
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2Preface);
        }

        private void doEncodeHttp2PingAck(
            long traceId,
            long authorization,
            DirectBuffer payload)
        {
            final Http2PingFW http2Ping = http2PingRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .ack()
                    .payload(payload)
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2Ping);
        }

        private void doEncodeHttp2Settings(
            long traceId,
            long authorization)
        {
            final Http2SettingsFW.Builder http2SettingsBuilder = http2SettingsRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .maxConcurrentStreams(initialSettings.maxConcurrentStreams)
                    .initialWindowSize(initialSettings.initialWindowSize);

            final Http2SettingsFW http2Settings = http2SettingsBuilder.build();

            doNetworkReservedData(traceId, authorization, 0L, http2Settings);
        }

        private void doEncodeHttp2SettingsAck(
            long traceId,
            long authorization)
        {
            final Http2SettingsFW http2Settings = http2SettingsRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .ack()
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2Settings);
        }

        private void doEncodeHttp2WindowUpdates(
                long traceId,
                long authorization,
                int streamId,
                int size)
        {
            final int frameOffset = http2WindowUpdateRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .size(size)
                    .build()
                    .limit();

            final int frameLimit = http2WindowUpdateRW.wrap(frameBuffer, frameOffset, frameBuffer.capacity())
                    .streamId(streamId)
                    .size(size)
                    .build()
                    .limit();

            doNetworkReservedData(traceId, authorization, 0L, frameBuffer, 0, frameLimit);
        }

        private void onDecodeHttp2RstStream(
            long traceId,
            long authorization,
            Http2RstStreamFW http2RstStream)
        {
            final int streamId = http2RstStream.streamId();
            final HttpExchange exchange = pool.exchanges.get(streamId);

            if (exchange != null)
            {
                exchange.cleanup(traceId, authorization);
            }
        }

        private void onDecodeHttp2Priority(
            long traceId,
            long authorization,
            Http2PriorityFW http2Priority)
        {
            final int streamId = http2Priority.streamId();
            final int parentStream = http2Priority.parentStream();

            final HttpExchange exchange = pool.exchanges.get(streamId);
            if (exchange != null)
            {
                if (parentStream == streamId)
                {
                    doEncodeHttp2RstStream(traceId, streamId, Http2ErrorCode.PROTOCOL_ERROR);
                }
            }
        }

        private int onDecodeHttp2Data(
            long traceId,
            long authorization,
            int streamId,
            byte flags,
            int deferred,
            DirectBuffer payload)
        {
            int progress = 0;

            final HttpExchange exchange = pool.exchanges.get(streamId);

            if (exchange == null)
            {
                progress += payload.capacity();
            }
            else
            {
                Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

                if (HttpState.replyClosed(exchange.state))
                {
                    error = Http2ErrorCode.STREAM_CLOSED;
                }

                if (error != Http2ErrorCode.NO_ERROR)
                {
                    exchange.cleanup(traceId, authorization);
                    doEncodeHttp2RstStream(traceId, streamId, error);
                    progress += payloadRemaining.value;
                }
                else
                {
                    final int payloadLength = payload.capacity();

                    if (payloadLength > 0)
                    {
                        payloadRemaining.set(payloadLength);
                        if (exchange.localBudget < payloadRemaining.value)
                        {
                            doEncodeHttp2RstStream(traceId, streamId, Http2ErrorCode.FLOW_CONTROL_ERROR);
                            exchange.cleanup(traceId, authorization);
                        }
                        else
                        {
                            final int remainingProgress = exchange.doResponseData(traceId, authorization, payload,
                                    0, payloadLength, EMPTY_OCTETS);
                            payloadRemaining.value -= remainingProgress;
                            exchange.responseContentObserved += remainingProgress;
                            progress += payloadLength - payloadRemaining.value;
                            deferred += payloadRemaining.value;
                        }
                    }

                    if (deferred == 0 && Http2Flags.endStream(flags))
                    {
                        if (exchange.responseContentLength != -1 &&
                                exchange.responseContentObserved != exchange.responseContentLength)
                        {
                            doEncodeHttp2RstStream(traceId, streamId, Http2ErrorCode.PROTOCOL_ERROR);
                            exchange.cleanup(traceId, authorization);
                        }
                        else
                        {
                            exchange.doResponseEnd(traceId, authorization, EMPTY_OCTETS);
                        }
                    }
                }
            }

            return progress;
        }

        private void onDecodeHttp2Continuation(
            long traceId,
            long authorization,
            Http2ContinuationFW http2Continuation)
        {
            assert headersSlot != NO_SLOT;
            assert headersSlotOffset != 0;

            final int streamId = http2Continuation.streamId();
            final DirectBuffer payload = http2Continuation.payload();
            final boolean endHeaders = http2Continuation.endHeaders();
            final boolean endResponse = http2Continuation.endStream();

            final MutableDirectBuffer headersBuffer = headersPool.buffer(headersSlot);
            headersBuffer.putBytes(headersSlotOffset, payload, 0, payload.capacity());
            headersSlotOffset += payload.capacity();

            if (endHeaders)
            {
                final HttpExchange exchange = pool.exchanges.get(streamId);
                if (exchange != null && HttpState.replyOpening(exchange.state))
                {
                    onDecodeHttp2Trailers(traceId, authorization, streamId, headersBuffer, 0, headersSlotOffset);
                }
                else
                {
                    onDecodeHttp2Headers(traceId, authorization, streamId, headersBuffer, 0, headersSlotOffset, endResponse);
                }
                continuationStreamId = 0;

                cleanupHeadersSlotIfNecessary();
            }
        }

        private void onDecodeHttp2Headers(
            long traceId,
            long authorization,
            int streamId,
            DirectBuffer buffer,
            int offset,
            int limit,
            boolean endResponse)
        {
            final HpackHeaderBlockFW headerBlock = headerBlockRO.wrap(buffer, offset, limit);
            headersDecoder.decodeHeaders(decodeContext, localSettings.headerTableSize, expectDynamicTableSizeUpdate, headerBlock);

            if (headersDecoder.error())
            {
                if (headersDecoder.streamError != null)
                {
                    doEncodeHttp2RstStream(traceId, streamId, headersDecoder.streamError);
                }
                else if (headersDecoder.connectionError != null)
                {
                    onDecodeHttp2Error(traceId, authorization, headersDecoder.connectionError);
                    decoder = decodeHttp2IgnoreAll;
                }
            }
            else if (headersDecoder.httpError())
            {
                doEncodeHttp2Headers(traceId, authorization, streamId, headersDecoder.httpErrorHeader, EMPTY_OVERRIDES, true);
            }
            else
            {
                final Map<String, String> headers = headersDecoder.headers;
                final HttpExchange exchange = pool.exchanges.get(streamId);
                exchange.responseContentLength = headersDecoder.contentLength;

                final HttpBeginExFW beginEx = beginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headers(hs -> headers.forEach((n, v) -> hs.item(h -> h.name(n).value(v))))
                        .build();

                exchange.doResponseBegin(traceId, authorization, beginEx);

                if (endResponse)
                {
                    exchange.doResponseEnd(traceId, authorization, EMPTY_OCTETS);
                }
            }
        }

        private void onDecodeHttp2Promise(
            long traceId,
            long authorization,
            int streamId,
            int promisedStreamId,
            DirectBuffer buffer,
            int offset,
            int limit,
            boolean endPromise)
        {
            final HpackHeaderBlockFW headerBlock = headerBlockRO.wrap(buffer, offset, limit);
            headersDecoder.decodeHeaders(decodeContext, localSettings.headerTableSize, expectDynamicTableSizeUpdate, headerBlock);

            final Map<String, String> headers = headersDecoder.headers;

            if (headersDecoder.error())
            {
                if (headersDecoder.streamError != null)
                {
                    doEncodeHttp2RstStream(traceId, streamId, headersDecoder.streamError);
                }
                else if (headersDecoder.connectionError != null)
                {
                    onDecodeHttp2Error(traceId, authorization, headersDecoder.connectionError);
                    decoder = decodeHttp2IgnoreAll;
                }
            }
            else if (headersDecoder.httpError())
            {
                doEncodeHttp2Headers(traceId, authorization, streamId, headersDecoder.httpErrorHeader, EMPTY_OVERRIDES, true);
            }
            else if (!requestCacheable(headers) || headers.get(AUTHORITY) == null)
            {
                doEncodeHttp2RstStream(traceId, promisedStreamId, Http2ErrorCode.PROTOCOL_ERROR);
            }
            else
            {
                final HttpExchange exchange = pool.exchanges.get(streamId);

                final long promiseId = supplyPromiseId.applyAsLong(exchange.requestId);
                final MessageConsumer sender = supplySender.apply(promiseId);

                final HttpExchange promisedExchange =
                       new HttpExchange(this, sender, exchange.originId, exchange.routedId, promiseId, exchange.sessionId,
                               EMPTY_OVERRIDES, promisedStreamId);
                pool.exchanges.put(promisedStreamId, promisedExchange);


                addNewPromise(traceId, promisedStreamId, headers);

                exchange.doResponseFlush(traceId, authorization,
                        ex -> ex.set((b, o, l) -> flushExRW.wrap(b, o, l)
                        .typeId(httpTypeId)
                        .promiseId(promiseId)
                        .promise(hs -> headers.forEach((n, v) -> hs.item(h -> h.name(n).value(v))))
                        .build().sizeof()));

                if (endPromise)
                {
                    exchange.doResponseEnd(traceId, authorization, EMPTY_OCTETS);
                }
            }
        }

        private void onDecodeHttp2Trailers(
            long traceId,
            long authorization,
            int streamId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final HttpExchange exchange = pool.exchanges.get(streamId);
            if (exchange != null)
            {
                final HpackHeaderBlockFW headerBlock = headerBlockRO.wrap(buffer, offset, limit);
                headersDecoder.decodeTrailers(decodeContext, localSettings.headerTableSize,
                        expectDynamicTableSizeUpdate, headerBlock);

                if (headersDecoder.error())
                {
                    if (headersDecoder.streamError != null)
                    {
                        doEncodeHttp2RstStream(traceId, streamId, headersDecoder.streamError);
                        exchange.cleanup(traceId, authorization);
                    }
                    else if (headersDecoder.connectionError != null)
                    {
                        onDecodeHttp2Error(traceId, authorization, headersDecoder.connectionError);
                        decoder = decodeHttp2IgnoreAll;
                    }
                }
                else
                {
                    final Map<String, String> trailers = headersDecoder.headers;
                    final HttpEndExFW endEx = endExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(httpTypeId)
                            .trailers(ts -> trailers.forEach((n, v) -> ts.item(t -> t.name(n).value(v))))
                            .build();

                    exchange.doResponseEnd(traceId, authorization, endEx);
                }
            }
        }

        private void onDecodeHttp2Headers(
            long traceId,
            long authorization,
            Http2HeadersFW http2Headers)
        {
            final int streamId = http2Headers.streamId();
            final int parentStreamId = http2Headers.parentStream();
            final int dataLength = http2Headers.dataLength();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

            if (streamId > maxClientStreamId ||
                    parentStreamId == streamId ||
                    dataLength < 0)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (streamsActive[CLIENT_INITIATED] >= localSettings.maxConcurrentStreams)
            {
                error = Http2ErrorCode.REFUSED_STREAM;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                doEncodeHttp2RstStream(traceId, streamId, error);
            }

            final DirectBuffer dataBuffer = http2Headers.buffer();
            final int dataOffset = http2Headers.dataOffset();

            final boolean endHeaders = http2Headers.endHeaders();
            final boolean endRequest = http2Headers.endStream();

            if (endHeaders)
            {
                onDecodeHttp2Headers(traceId, authorization, streamId, dataBuffer, dataOffset,
                        dataOffset + dataLength, endRequest);
            }
            else
            {
                assert headersSlot == NO_SLOT;
                assert headersSlotOffset == 0;

                headersSlot = headersPool.acquire(initialId);
                if (headersSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer headersBuffer = headersPool.buffer(headersSlot);
                    headersBuffer.putBytes(headersSlotOffset, dataBuffer, dataOffset, dataLength);
                    headersSlotOffset = dataLength;

                    continuationStreamId = streamId;
                }
            }
        }

        private void onDecodeHttp2PushPromise(
            long traceId,
            long authorization,
            Http2PushPromiseFW http2PushPromise)
        {
            final int streamId = http2PushPromise.streamId();
            final int promisedStreamId = http2PushPromise.promisedStreamId();
            final int headerLength = http2PushPromise.headersLength();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

            if (streamId > maxClientStreamId ||
                    (promisedStreamId & 0x01) == 0x01 ||
                    headerLength < 0)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                doEncodeHttp2RstStream(traceId, streamId, error);
            }

            final DirectBuffer dataBuffer = http2PushPromise.buffer();
            final int headerOffset = http2PushPromise.headersOffset();

            final boolean endHeaders = http2PushPromise.endHeaders();
            final boolean endRequest = http2PushPromise.endStream();

            if (endHeaders)
            {
                onDecodeHttp2Promise(traceId, authorization, streamId, promisedStreamId, dataBuffer, headerOffset,
                        headerOffset + headerLength, endRequest);
            }
            else
            {
                assert headersSlot == NO_SLOT;
                assert headersSlotOffset == 0;

                headersSlot = headersPool.acquire(initialId);
                if (headersSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer headersBuffer = headersPool.buffer(headersSlot);
                    headersBuffer.putBytes(headersSlotOffset, dataBuffer, headerOffset, headerLength);
                    headersSlotOffset = headerLength;

                    continuationStreamId = streamId;
                }
            }
        }

        private void onDecodeHttp2Trailers(
            long traceId,
            long authorization,
            Http2HeadersFW http2Trailers)
        {
            final int streamId = http2Trailers.streamId();
            final int dataLength = http2Trailers.dataLength();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

            if (dataLength < 0)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                doEncodeHttp2RstStream(traceId, streamId, error);
            }

            final DirectBuffer dataBuffer = http2Trailers.buffer();
            final int dataOffset = http2Trailers.dataOffset();

            final boolean endHeaders = http2Trailers.endHeaders();

            if (endHeaders)
            {
                onDecodeHttp2Trailers(traceId, authorization, streamId, dataBuffer, dataOffset,
                        dataOffset + dataLength);
            }
            else
            {
                assert headersSlot == NO_SLOT;
                assert headersSlotOffset == 0;

                headersSlot = headersPool.acquire(initialId);
                if (headersSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer headersBuffer = headersPool.buffer(headersSlot);
                    headersBuffer.putBytes(headersSlotOffset, dataBuffer, dataOffset, dataLength);
                    headersSlotOffset = dataLength;

                    continuationStreamId = streamId;
                }
            }
        }

        private void onDecodeHttp2Settings(
            long traceId,
            long authorization,
            Http2SettingsFW http2Settings)
        {
            if (http2Settings.ack())
            {
                doHttp2SettingsAck(traceId, authorization);
            }
            else
            {
                final int remoteInitialBudget = remoteSettings.initialWindowSize;
                http2Settings.forEach(this::onDecodeSetting);

                Http2ErrorCode decodeError = remoteSettings.error();

                if (decodeError == Http2ErrorCode.NO_ERROR)
                {
                    // initial budget can become negative
                    final long remoteInitialCredit = remoteSettings.initialWindowSize - remoteInitialBudget;
                    if (remoteInitialCredit != 0)
                    {
                        for (HttpExchange stream: pool.exchanges.values())
                        {
                            final long newRemoteBudget = stream.remoteBudget + remoteInitialCredit;
                            if (newRemoteBudget > MAX_REMOTE_BUDGET)
                            {
                                decodeError = Http2ErrorCode.FLOW_CONTROL_ERROR;
                                break;
                            }
                            stream.remoteBudget = (int) newRemoteBudget;
                        }
                    }
                }

                if (decodeError == Http2ErrorCode.NO_ERROR)
                {
                    doEncodeHttp2SettingsAck(traceId, authorization);
                }
                else
                {
                    onDecodeHttp2Error(traceId, authorization, decodeError);
                    decoder = decodeHttp2IgnoreAll;
                }
            }
        }

        private void doHttp2SettingsAck(
            long traceId,
            long authorization)
        {
            final int localInitialCredit = initialSettings.initialWindowSize - localSettings.initialWindowSize;

            // initial budget can become negative
            if (localInitialCredit != 0)
            {
                for (HttpExchange stream: pool.exchanges.values())
                {
                    stream.localBudget += localInitialCredit;
                    stream.flushResponseWindowUpdate(traceId, authorization);
                }
            }

            localSettings.apply(initialSettings);
        }

        private void onDecodeSetting(
            Http2Setting setting,
            int value)
        {
            switch (setting)
            {
            case HEADER_TABLE_SIZE:
                remoteSettings.headerTableSize = value;
                break;
            case ENABLE_PUSH:
                remoteSettings.enablePush = value;
                break;
            case MAX_CONCURRENT_STREAMS:
                remoteSettings.maxConcurrentStreams = value;
                break;
            case INITIAL_WINDOW_SIZE:
                remoteSettings.initialWindowSize = value;
                break;
            case MAX_FRAME_SIZE:
                remoteSettings.maxFrameSize = value;
                break;
            case MAX_HEADER_LIST_SIZE:
                remoteSettings.maxHeaderListSize = value;
                break;
            case UNKNOWN:
                break;
            }
        }

        private void onDecodeHttp2WindowUpdate(
            long traceId,
            long authorization,
            Http2WindowUpdateFW http2WindowUpdate)
        {
            final int streamId = http2WindowUpdate.streamId();
            final int credit = http2WindowUpdate.size();

            if (streamId == 0)
            {
                remoteSharedBudget += credit;

                // TODO: instead use HttpState.replyClosed(state)
                if (requestSharedBudgetIndex != NO_CREDITOR_INDEX)
                {
                    flushRequestSharedBudget(traceId);
                }
            }
            else
            {
                final HttpExchange exchange = pool.exchanges.get(streamId);
                if (exchange != null)
                {
                    exchange.onRequestWindowUpdate(traceId, authorization, credit);
                }
            }
        }

        private void onDecodeHttp2Error(
            long traceId,
            long authorization,
            Http2ErrorCode error)
        {
            this.decodeError = error;
            cleanup(traceId, authorization, this::doEncodeHttp2Goaway);
        }

        private void onDecodePing(
            long traceId,
            long authorization,
            Http2PingFW http2Ping)
        {
            if (!http2Ping.ack())
            {
                doEncodeHttp2PingAck(traceId, authorization, http2Ping.payload());
            }
        }

        private void onHttp2DecodeGoaway(
            long traceId,
            long authorization,
            Http2GoawayFW http2Goaway)
        {
            final int lastStreamId = http2Goaway.lastStreamId();

            pool.exchanges.entrySet()
                    .stream()
                    .filter(e -> e.getKey() > lastStreamId)
                    .map(Map.Entry::getValue)
                    .forEach(ex -> ex.cleanup(traceId, authorization));

            remoteSettings.enablePush = 0;
        }

        private void flushRequestSharedBudget(
            long traceId)
        {
            final int requestSharedPadding = http2FramePadding(remoteSharedBudget, remoteSettings.maxFrameSize);
            final int remoteSharedBudgetMax = remoteSharedBudget + requestSharedPadding + initialPad;
            final int requestSharedCredit =
                    Math.min(encodeMax - encodeSlotReserved - requestSharedBudget, initialSharedBudget);
            final int requestSharedBudgetDelta = remoteSharedBudgetMax - (requestSharedBudget + encodeSlotReserved);
            final int initialSharedCredit = Math.min(requestSharedCredit, requestSharedBudgetDelta);

            if (initialSharedCredit > 0)
            {
                final long requestSharedPrevious =
                        creditor.credit(traceId, requestSharedBudgetIndex, initialSharedCredit);

                requestSharedBudget += initialSharedCredit;

                final long requestSharedBudgetUpdated = requestSharedPrevious + initialSharedCredit;
                assert requestSharedBudgetUpdated <= encodeMax
                        : String.format("%d <= %d, remoteSharedBudget = %d",
                        requestSharedBudgetUpdated, encodeMax, remoteSharedBudget);

                assert requestSharedBudget <= encodeMax
                        : String.format("%d <= %d", requestSharedBudget, encodeMax);

                assert initialSharedBudget <= encodeMax
                        : String.format("%d <= %d", initialSharedBudget, encodeMax);
            }
        }

        private void doEncodeHttp2Goaway(
                long traceId,
                long authorization)
        {
            final Http2GoawayFW http2Goaway = http2GoawayRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .lastStreamId(0)
                    .errorCode(decodeError)
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2Goaway);
            doNetworkEnd(traceId, authorization);
        }

        private void doEncodeHttp2RstStream(
            long traceId,
            int streamId,
            Http2ErrorCode error)
        {
            final Http2RstStreamFW http2RstStream = http2RstStreamRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(streamId)
                    .errorCode(error)
                    .build();

            doNetworkReservedData(traceId, 0L, 0L, http2RstStream);
        }

        private void doEncodeHttp2Headers(
            HttpExchange exchange,
            long traceId,
            long authorization,
            Array32FW<HttpHeaderFW> headers,
            Map<String8FW, String16FW> overrides)
        {
            final boolean endRequest = exchange.requestContentLength == exchange.requestContentObserved &&
                exchange.requestContentLength != NO_CONTENT_LENGTH;

            doEncodeHttp2Headers(traceId, authorization, exchange.streamId, headers, overrides, endRequest);
            exchange.flushResponseWindowUpdate(traceId, authorization);
        }

        private void doEncodeHttp2Headers(
            long traceId,
            long authorization,
            int streamId,
            Array32FW<HttpHeaderFW> headers,
            Map<String8FW, String16FW> overrides,
            boolean endRequest)
        {
            final Http2HeadersFW http2Headers = http2HeadersRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(streamId)
                    .headers(hb -> headersEncoder.encodeHeaders(encodeContext, headers, overrides, hb))
                    .endHeaders()
                    .endStream(endRequest)
                    .build();

            doNetworkHeadersData(traceId, authorization, 0L, http2Headers);
        }

        private void doEncodeHttp2Payload(
            HttpExchange exchange,
            long traceId,
            long authorization,
            int reserved,
            int length,
            OctetsFW payload)
        {
            exchange.requestContentObserved += length;
            exchange.remoteBudget -= length;
            remoteSharedBudget -= length;

            final boolean endRequest = exchange.requestContentLength == exchange.requestContentObserved &&
                exchange.requestContentLength != NO_CONTENT_LENGTH;
            doEncodeHttp2Data(traceId, authorization, reserved, exchange.streamId, payload, endRequest);

            final int remotePaddableMax = Math.min(exchange.remoteBudget, encodeMax);
            final int remotePadding = http2FramePadding(remotePaddableMax, remoteSettings.maxFrameSize);
            final int requestPadding = initialPad + remotePadding;

            final int requestWin = exchange.initialWindow();
            final int minimumClaim = 1024;
            final int requestCreditMin = (requestWin <= requestPadding + minimumClaim)
                    ? 0 : exchange.remoteBudget >> 1;

            exchange.flushRequestWindow(traceId, requestCreditMin);
        }

        private void doEncodeHttp2Data(
            long traceId,
            long authorization,
            int reserved,
            int streamId,
            OctetsFW payload,
            boolean endRequest)
        {
            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();

            int frameOffset = 0;
            int progress = offset;
            while (progress < limit)
            {
                final int length = Math.min(limit - progress, remoteSettings.maxFrameSize);
                final Http2DataFW http2Data = http2DataRW.wrap(frameBuffer, frameOffset, frameBuffer.capacity())
                        .streamId(streamId)
                        .endStream(endRequest && progress + length >= limit)
                        .payload(buffer, progress, length)
                        .build();

                frameOffset = http2Data.limit();
                progress += length;
            }

            assert progress == limit;

            doHttp2NetworkData(traceId, authorization, 0L, reserved, frameBuffer, 0, frameOffset);
        }

        private void doEncodeHttp2Trailers(
            HttpExchange exchange,
            long traceId,
            long authorization,
            Array32FW<HttpHeaderFW> trailers)
        {
            if (trailers.isEmpty())
            {
                final Http2DataFW http2Data = http2DataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(exchange.streamId)
                    .endStream()
                    .build();

                doNetworkReservedData(traceId, authorization, 0L, http2Data);
            }
            else
            {
                final Http2HeadersFW http2Headers = http2HeadersRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(exchange.streamId)
                    .headers(hb -> headersEncoder.encodeTrailers(encodeContext, trailers, hb))
                    .endHeaders()
                    .endStream()
                    .build();

                doNetworkReservedData(traceId, authorization, 0L, http2Headers);
            }
        }

        public void onHttp2NetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            long acknowledge,
            int maximum,
            int padding)
        {
            int credit = (int) (acknowledge - initialAck) + (maximum - initialMax);
            assert credit >= 0;

            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            assert initialAck <= initialSeq;

            if (initialBudgetReserved > 0)
            {
                final int reservedCredit = Math.min(credit, initialBudgetReserved);
                initialBudgetReserved -= reservedCredit;
                credit -= reservedCredit;
            }

            if (credit > 0)
            {
                initialSharedBudget += credit;
                assert initialSharedBudget <= initialMax;
                credit -= credit;
            }

            assert credit == 0;

            encodeNetwork(traceId, authorization, budgetId);

            flushRequestSharedBudget(traceId);
        }

        private void onHttp2ApplicationWindow(
            HttpExchange exchange,
            long traceId,
            long authorization)
        {
            if (!HttpState.replyClosed(exchange.state))
            {
                exchange.flushResponseWindowUpdate(traceId, authorization);
            }
        }

        private void doNetworkHeadersData(
            long traceId,
            long authorization,
            long budgetId,
            Flyweight payload)
        {
            doNetworkHeadersData(traceId, authorization, budgetId, payload.buffer(),
                    payload.offset(), payload.limit());
        }

        private void doNetworkHeadersData(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeHeadersSlotOffset == 0)
            {
                encodeSlotMarkOffset = encodeSlotOffset;
                assert encodeSlotMarkOffset >= 0;

                encodeReservedSlotMarkOffset = encodeReservedSlotOffset;
                assert encodeReservedSlotMarkOffset >= 0;
            }

            encodeHeadersBuffer.putBytes(encodeHeadersSlotOffset, buffer, offset, limit - offset);
            encodeHeadersSlotOffset += limit - offset;
            encodeHeadersSlotTraceId = traceId;

            encodeNetwork(traceId, authorization, budgetId);
        }

        private void encodeNetworkHeaders(
            long authorization,
            long budgetId)
        {
            if (encodeHeadersSlotOffset != 0 &&
                encodeSlotMarkOffset == 0 &&
                encodeReservedSlotMarkOffset == 0)
            {
                final int initialWin = initialMax - initialPendingAck();
                final int maxEncodeLength =
                        encodeHeadersSlotMarkOffset != 0 ? encodeHeadersSlotMarkOffset : encodeHeadersSlotOffset;
                final int encodeLength = Math.max(Math.min(initialWin - initialPad, maxEncodeLength), 0);

                if (encodeLength > 0)
                {
                    final int encodeReserved = encodeLength + initialPad;

                    doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax, encodeHeadersSlotTraceId,
                            authorization, budgetId, encodeReserved, encodeHeadersBuffer, 0, encodeLength, EMPTY_OCTETS);

                    initialSeq += encodeReserved;

                    assert initialSeq <= initialAck + initialMax :
                            String.format("%d <= %d + %d", initialSeq, initialAck, initialMax);

                    if (encodeHeadersSlotMarkOffset != 0)
                    {
                        encodeHeadersSlotMarkOffset -= encodeLength;
                        assert encodeHeadersSlotMarkOffset >= 0;
                    }

                    encodeHeadersSlotOffset -= encodeLength;
                    assert encodeHeadersSlotOffset >= 0;

                    if (encodeHeadersSlotOffset > 0)
                    {
                        encodeHeadersBuffer.putBytes(0, encodeHeadersBuffer, encodeLength, encodeHeadersSlotOffset);

                        if (encodeHeadersSlotMarkOffset == 0)
                        {
                            encodeHeadersSlotMarkOffset = encodeHeadersSlotOffset;
                        }
                    }

                    initialBudgetReserved += encodeReserved;
                }
            }
        }

        private void encodeNetworkData(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (encodeSlotOffset != 0 &&
                (encodeSlotMarkOffset != 0 || encodeHeadersSlotOffset == 0 && encodeReservedSlotMarkOffset == 0))
            {
                final int initialWin = initialMax - initialPendingAck();
                final int encodeLengthMax = encodeSlotMarkOffset != 0 ? encodeSlotMarkOffset : encodeSlotOffset;
                final int encodeLength = Math.max(Math.min(initialWin - initialPad, encodeLengthMax), 0);

                if (encodeLength > 0)
                {
                    final int encodeReserved = encodeLength + initialPad;
                    final int encodeReservedMin = (int) (((long) encodeSlotReserved * encodeLength) / encodeSlotOffset);


                    initialSharedBudget -= encodeReserved;
                    encodeSlotReserved -= encodeReservedMin;

                    assert encodeSlot != NO_SLOT;
                    final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);

                    doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, budgetId, encodeReserved, encodeBuffer, 0, encodeLength, EMPTY_OCTETS);

                    initialSeq += encodeReserved;

                    assert initialSeq <= initialAck + initialMax :
                            String.format("%d <= %d + %d", initialSeq, initialAck, initialMax);

                    if (encodeSlotMarkOffset != 0)
                    {
                        encodeSlotMarkOffset -= encodeLength;
                        assert encodeSlotMarkOffset >= 0;
                    }

                    encodeSlotOffset -= encodeLength;
                    assert encodeSlotOffset >= 0;

                    if (encodeSlotOffset > 0)
                    {
                        encodeBuffer.putBytes(0, encodeBuffer, encodeLength, encodeSlotOffset);

                        if (encodeSlotMarkOffset == 0 && encodeHeadersSlotOffset == 0)
                        {
                            encodeSlotMarkOffset += encodeSlotOffset;
                        }
                    }
                    else
                    {
                        cleanupEncodeSlotIfNecessary();

                        if (pool.exchanges.isEmpty() && decoder == decodeHttp2IgnoreAll)
                        {
                            doNetworkEnd(traceId, authorization);
                        }
                    }
                }
            }
        }

        private void doNetworkReservedData(
            long traceId,
            long authorization,
            long budgetId,
            Flyweight payload)
        {
            doNetworkReservedData(traceId, authorization, budgetId, payload.buffer(),
                    payload.offset(), payload.limit());
        }

        private void doNetworkReservedData(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            encodeReservedBuffer.putBytes(encodeReservedSlotOffset, buffer, offset, limit - offset);
            encodeReservedSlotOffset += limit - offset;
            encodeReservedSlotTraceId = traceId;

            encodeNetwork(traceId, authorization, budgetId);
        }

        private void encodeNetworkReserved(
            long authorization,
            long budgetId)
        {
            if (encodeReservedSlotOffset != 0 &&
                (encodeReservedSlotMarkOffset != 0 || encodeHeadersSlotOffset == 0 && encodeSlotOffset == 0))
            {
                final int initialWin = initialMax - initialPendingAck();
                final int maxEncodeLength =
                        encodeReservedSlotMarkOffset != 0 ? encodeReservedSlotMarkOffset : encodeReservedSlotOffset;
                final int encodeLength = Math.max(Math.min(initialWin - initialPad, maxEncodeLength), 0);

                if (encodeLength > 0)
                {
                    final int encodeReserved = encodeLength + initialPad;

                    doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax, encodeReservedSlotTraceId,
                            authorization, budgetId, encodeReserved, encodeReservedBuffer, 0, encodeLength, EMPTY_OCTETS);

                    initialSeq += encodeReserved;

                    assert initialSeq <= initialAck + initialMax :
                            String.format("%d <= %d + %d", initialSeq, initialAck, initialSeq);

                    if (encodeReservedSlotMarkOffset != 0)
                    {
                        encodeReservedSlotMarkOffset -= encodeLength;
                        assert encodeReservedSlotMarkOffset >= 0;
                    }

                    encodeReservedSlotOffset -= encodeLength;
                    assert encodeReservedSlotOffset >= 0;

                    if (encodeReservedSlotOffset > 0)
                    {
                        encodeReservedBuffer.putBytes(0, encodeReservedBuffer, encodeLength, encodeReservedSlotOffset);

                        if (encodeReservedSlotMarkOffset == 0 &&
                                encodeHeadersSlotOffset == 0 &&
                                encodeSlotOffset == 0)
                        {
                            encodeReservedSlotMarkOffset = encodeReservedSlotOffset;
                        }
                    }

                    initialBudgetReserved += encodeReserved;
                }
            }
        }

        private void encodeNetwork(
            long traceId,
            long authorization,
            long budgetId)
        {
            encodeNetworkHeaders(authorization, budgetId);
            encodeNetworkData(traceId, authorization, budgetId);
            encodeNetworkReserved(authorization, budgetId);
        }

        private int nextStreamId()
        {
            maxClientStreamId = maxClientStreamId + 2;
            return maxClientStreamId;
        }

        private void cleanup(
            long traceId,
            long authorization,
            LongLongConsumer cleanupHandler)
        {
            assert this.cleanupHandler == null;
            this.cleanupHandler = cleanupHandler;
            cleanupStreams(traceId, authorization);
        }

        private void cleanupStreams(
            long traceId,
            long authorization)
        {
            int remaining = config.concurrentStreamsCleanup();
            for (Iterator<HttpExchange> iterator = pool.exchanges.values().iterator();
                    iterator.hasNext() && remaining > 0; remaining--)
            {
                final HttpExchange stream = iterator.next();
                if (stream.client == this)
                {
                    stream.cleanup(traceId, authorization);
                }
            }

            cleanupHandler.accept(traceId, authorization);
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization, this::doNetworkResetAndAbort);
        }

        private void doNetworkResetAndAbort(
            long traceId,
            long authorization)
        {
            doNetworkReset(traceId, authorization);
            doNetworkAbort(traceId, authorization);
        }

        private void cleanupDecodeSlotIfNecessary()
        {
            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }

        private void cleanupEncodeSlotIfNecessary()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
            }
        }

        private void cleanupHeadersSlotIfNecessary()
        {
            if (headersSlot != NO_SLOT)
            {
                bufferPool.release(headersSlot);
                headersSlot = NO_SLOT;
                headersSlotOffset = 0;
            }
        }

        private void cleanupBudgetCreditorIfNecessary()
        {
            if (requestSharedBudgetIndex != NO_CREDITOR_INDEX)
            {
                creditor.release(requestSharedBudgetIndex);
                requestSharedBudgetIndex = NO_CREDITOR_INDEX;
            }
        }
    }


    private final class HttpExchange
    {
        private final HttpClient client;
        private final Map<String8FW, String16FW> overrides;
        private final int streamId;
        private final long originId;
        private final long routedId;
        private final long requestId;
        private final long responseId;
        private final long sessionId;
        private int requestContentLength;
        private int requestContentObserved;

        private long responseContentLength;
        private long responseContentObserved;
        private int localBudget;
        private int remoteBudget;

        private long requestSeq;
        private long requestAck;
        private int requestMax;
        private long requestAuth;

        private MessageConsumer application;
        private BudgetDebitor responseDeb;
        private long responseDebIndex = NO_DEBITOR_INDEX;
        private long responseSeq;
        private long responseAck;
        private int responseMax;
        private long responseAuth;
        private long responseBud;
        private int responsePad;

        private int state;

        private boolean requestChunked;
        private int requestRemaining;

        private HttpExchange(
            HttpClient client,
            MessageConsumer application,
            long originId,
            long routedId,
            long requestId,
            long authorization,
            Map<String8FW, String16FW> overrides,
            int streamId)
        {
            this.client = client;
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.requestId = requestId;
            this.sessionId = authorization;
            this.responseId = supplyReplyId.applyAsLong(requestId);
            this.overrides = overrides;
            this.streamId = streamId;
            localBudget = client.localSettings.initialWindowSize;
        }

        private int initialWindow()
        {
            return requestMax - (int)(requestSeq - requestAck);
        }

        private int replyWindow()
        {
            return responseMax - (int)(responseSeq - responseAck);
        }

        private void onApplication(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onRequestBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onRequestData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onRequestEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onRequestAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onRequestFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onResponseReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onResponseWindow(window);
                break;
            }
        }

        private void onRequestBegin(
            BeginFW begin)
        {
            final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);
            final Array32FW<HttpHeaderFW> headers = beginEx != null ? beginEx.headers() : DEFAULT_HEADERS;

            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= requestSeq;
            assert acknowledge >= requestAck;

            requestSeq = sequence;
            requestAck = acknowledge;
            requestAuth = authorization;

            state = HttpState.openingInitial(state);

            if ((streamId & 0x01) == 0x01)
            {
                final HttpHeaderFW method = headers.matchFirst(header ->
                    HEADER_METHOD.equals(header.name()));

                boolean isBodilessMethod = method != null &&
                    (METHOD_HEAD.equals(method.value()) ||
                    METHOD_GET.equals(method.value()) ||
                    METHOD_DELETE.equals(method.value()));

                final HttpHeaderFW contentLengthHeader = headers.matchFirst(header ->
                        HEADER_CONTENT_LENGTH.equals(header.name()));

                requestContentLength = contentLengthHeader != null ? parseInt(contentLengthHeader.value().asString()) :
                    isBodilessMethod ? 0 : NO_CONTENT_LENGTH;

                client.doNetworkBegin(traceId, authorization, 0);

                if (HttpState.replyOpened(client.state))
                {
                    doRequestBegin(traceId, authorization, begin.extension());
                }
                else
                {
                    client.pool.acquireQueueSlotIfNecessary();
                    final MutableDirectBuffer httpQueueBuffer = bufferPool.buffer(client.pool.httpQueueSlot);
                    final int headerSlotLimit = client.pool.httpQueueSlotLimit;
                    final HttpQueueEntryFW queueEntry = queueEntryRW
                            .wrap(httpQueueBuffer, headerSlotLimit, httpQueueBuffer.capacity())
                            .streamId(streamId)
                            .traceId(traceId)
                            .authorization(authorization)
                            .value(beginEx.buffer(), beginEx.offset(), beginEx.sizeof())
                            .build();

                    client.pool.httpQueueSlotLimit += queueEntry.sizeof();
                    enqueues.getAsLong();
                }
            }

            if (HttpState.replyOpened(client.state))
            {
                remoteBudget = client.remoteSharedBudget;
            }

            client.encoder.onApplicationBegin(client, this, traceId, authorization);
        }

        private void doRequestBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final HttpBeginExFW beginEx = extension.get(beginExRO::tryWrap);
            final Array32FW<HttpHeaderFW> headers = beginEx != null ? beginEx.headers() : DEFAULT_HEADERS;

            if (client.encoder != HttpEncoder.HTTP_2)
            {
                client.exchange = this;
            }
            client.encoder.doEncodeRequestHeaders(client, this, traceId, authorization, 0, headers, overrides);
        }

        private void onRequestFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= requestSeq;

            requestSeq = sequence;
            requestAuth = authorization;

            assert requestAck <= requestSeq;

            if (requestSeq > requestAck + client.initialMax)
            {
                doRequestReset(traceId, authorization);
                client.doNetworkAbort(traceId, authorization);
            }
            else
            {
                client.doNetFlush(traceId, budgetId, reserved, extension);
            }
        }

        private void onRequestData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= requestSeq;

            requestSeq = sequence + data.reserved();
            requestAuth = authorization;

            assert requestAck <= requestSeq;

            if (requestSeq > requestAck + encodeMax)
            {
                doRequestReset(traceId, authorization);
                client.doNetworkAbort(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final long budgetId = data.budgetId();
                final int reserved = data.reserved();
                final int length = data.length();
                final OctetsFW payload = data.payload();

                client.encoder.doEncodeRequestData(client, this, traceId, authorization,
                        flags, budgetId, reserved, length, payload);
            }
        }

        private void onRequestEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= requestSeq;

            requestSeq = sequence;
            requestAuth = authorization;

            assert requestAck <= requestSeq;

            state = HttpState.closeInitial(state);

            if ((streamId & 0x01) == 0x01)
            {
                final HttpEndExFW endEx = end.extension().get(endExRO::tryWrap);
                final Array32FW<HttpHeaderFW> trailers = endEx != null ? endEx.trailers() : DEFAULT_TRAILERS;

                client.encoder.doEncodeRequestEnd(client, this, traceId, authorization, 0, trailers);
            }
        }

        private void onRequestAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= requestSeq;

            requestSeq = sequence;
            requestAuth = authorization;

            assert requestAck <= requestSeq;

            state = HttpState.closeInitial(state);
            client.encoder.doEncodeRequestAbort(client, this, traceId, authorization);
        }

        private void doRequestReset(
            long traceId,
            long authorization)
        {
            if (!HttpState.initialClosed(state))
            {
                state = HttpState.closeInitial(state);
                doReset(application, originId, routedId, requestId, requestSeq, requestAck,
                        client.initialMax, traceId, authorization);

                if (HttpState.closed(state))
                {
                    onExchangeClosed();
                }
            }
        }

        private void doRequestWindow(
            long traceId)
        {
            long requestAckMax = Math.max(requestSeq - client.initialPendingAck() - client.encodeSlotOffset, requestAck);
            int requestNoAckMin = (int)(requestSeq - requestAckMax);
            int minRequestMax = Math.min(requestRemaining - requestNoAckMin + client.initialPad, client.initialMax);

            if (requestAckMax > requestAck ||
                minRequestMax > requestMax && client.encodeSlotOffset == 0 ||
                minRequestMax == 0 && requestRemaining == 0 && !HttpState.initialOpened(state))
            {
                requestAck = requestAckMax;
                assert requestAck <= requestSeq;

                requestMax = minRequestMax;
                assert requestMax >= 0;

                state = HttpState.openInitial(state);

                doWindow(application, originId, routedId, requestId, requestSeq, requestAck, requestMax,
                    traceId, requestAuth, client.budgetId, client.initialPad);
            }
        }

        private void doResponseBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            // count all responses
            countResponses.getAsLong();

            state = HttpState.openingReply(state);

            doBegin(application, originId, routedId, responseId, responseSeq, responseAck, responseMax,
                    traceId, authorization, 0, extension);
        }

        private int doResponseData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit,
            Flyweight extension)
        {
            int responseNoAck = (int)(responseSeq - responseAck);
            int length = Math.min(responseMax - responseNoAck - responsePad, limit - offset);
            int reserved = length + responsePad;

            if (responseDebIndex != NO_DEBITOR_INDEX && responseDeb != null)
            {
                final int minimum = reserved; // TODO: fragmentation
                reserved = responseDeb.claim(traceId, responseDebIndex, responseId, minimum, reserved, 0);
                length = Math.max(reserved - responsePad, 0);
            }

            if (length > 0)
            {
                doData(application, originId, routedId, responseId, responseSeq, responseAck, requestMax,
                        traceId, authorization, responseBud,
                        reserved, buffer, offset, length, extension);

                responseSeq += reserved;
                localBudget -= length;

                assert responseSeq <= responseAck + responseMax;
            }

            return offset + length;
        }

        private void doResponseFlush(
            long traceId,
            long authorization,
            Consumer<OctetsFW.Builder> extension)
        {
            doFlush(application, originId, routedId, responseId, responseSeq, responseAck, requestMax,
                    traceId, authorization, responseBud, 0, extension);
        }

        private void doResponseEnd(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!HttpState.replyClosed(state))
            {
                if (HttpState.replyClosing(client.state))
                {
                    client.doNetworkEnd(traceId, authorization);
                }

                state = HttpState.closeReply(state);
                doEnd(application, originId, routedId, responseId, responseSeq, responseAck, requestMax,
                        traceId, authorization, extension);

                if (HttpState.closed(state))
                {
                    onExchangeClosed();
                }
            }
        }

        private void doResponseAbort(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!HttpState.replyClosed(state))
            {
                if (HttpState.replyOpening(state))
                {
                    state = HttpState.closeReply(state);
                    doAbort(application, originId, routedId, responseId, responseSeq, responseAck, requestMax,
                            traceId, authorization, extension);

                    // count abandoned responses
                    countResponsesAbandoned.getAsLong();

                    if (HttpState.closed(state))
                    {
                        onExchangeClosed();
                    }
                }
                else
                {
                    HttpBeginExFW beginEx = beginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                            .typeId(httpTypeId)
                            .headers(HEADERS_503_RETRY_AFTER)
                            .build();
                    doResponseBegin(traceId, authorization, beginEx);
                    doResponseEnd(traceId, authorization, EMPTY_OCTETS);

                    // count abandoned requests
                    countRequestsAbandoned.getAsLong();
                }
            }
        }

        private void onResponseReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = HttpState.closeReply(state);
            if (HttpState.closed(state))
            {
                onExchangeClosed();
            }
            client.encoder.doEncodeResponseReset(client, this, traceId, authorization);
        }

        private void onResponseWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert acknowledge >= responseAck;
            assert maximum >= responseMax;

            responseAck = acknowledge;
            responseMax = maximum;
            responseAuth = authorization;
            responseBud = budgetId;
            responsePad = padding;

            assert responseAck <= responseSeq;

            state = HttpState.openReply(state);

            if (responseBud != 0L && responseDebIndex == NO_DEBITOR_INDEX)
            {
                responseDeb = supplyDebitor.apply(budgetId);
                responseDebIndex = responseDeb.acquire(budgetId, responseId, this::onResponseFlush);
            }

            client.decodeNetworkIfBuffered(traceId, authorization);

            client.encoder.onApplicationWindow(client, this, traceId, authorization);
        }

        private void onResponseFlush(
            long traceId)
        {
            client.decodeNetworkIfBuffered(traceId, responseAuth);
        }

        private void onExchangeClosed()
        {
            final HttpExchange exchange = this.client.pool.exchanges.remove(streamId);
            if (exchange != null)
            {
                client.exchange = null;
                client.pool.flushNext();
            }
        }

        private void onRequestWindowUpdate(
            long traceId,
            long authorization,
            int size)
        {
            final long newRemoteBudget = (long) remoteBudget + size;

            if (newRemoteBudget > MAX_REMOTE_BUDGET)
            {
                client.doEncodeHttp2RstStream(traceId, streamId, Http2ErrorCode.FLOW_CONTROL_ERROR);
                cleanup(traceId, authorization);
            }
            else
            {
                remoteBudget = (int) newRemoteBudget;
                flushRequestWindow(traceId, 0);
            }
        }

        private void flushRequestWindow(
            long traceId,
            int requestCreditMin)
        {
            if (!HttpState.initialClosed(state))
            {
                final int remotePaddableMax = Math.min(remoteBudget, encodeMax);
                final int remotePad = http2FramePadding(remotePaddableMax, client.remoteSettings.maxFrameSize);
                final int requestPad = client.initialPad + remotePad;
                final int newRequestWin = remoteBudget;
                final int requestWin = requestMax - (int)(requestSeq - requestAck);
                final int requestCredit = newRequestWin - requestWin;

                if (requestCredit >= 0 && requestCredit >= requestCreditMin && newRequestWin > requestPad)
                {
                    final int requestNoAck = (int)(requestSeq - requestAck);
                    final int requestAcked = Math.min(requestNoAck, requestCredit);

                    requestAck += requestAcked;
                    assert requestAck <= requestSeq;

                    requestMax = newRequestWin + (int)(requestSeq - requestAck);
                    assert requestMax >= 0;

                    doWindow(application, originId, routedId, requestId, requestSeq, requestAck, requestMax, traceId, sessionId,
                            client.budgetId, requestPad);
                }
            }
        }

        private void flushResponseWindowUpdate(
            long traceId,
            long authorization)
        {
            final int replyWindow = replyWindow();
            final int size = replyWindow - localBudget;
            if (size > 0)
            {
                localBudget = replyWindow;
                client.doEncodeHttp2WindowUpdates(traceId, authorization, streamId, size);
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            doRequestReset(traceId, authorization);
            doResponseAbort(traceId, authorization, EMPTY_OCTETS);
        }
    }

    private final class HttpPromise
    {
        private final AtomicBoolean matches = new AtomicBoolean(false);

        private final int promiseId;
        private final String method;
        private final String scheme;
        private final String path;
        private final String authority;

        HttpPromise(
            int promiseId,
            String method,
            String scheme,
            String path,
            String authority)
        {
            this.promiseId = promiseId;

            this.method = method;
            this.scheme = scheme;
            this.path = path;
            this.authority = authority;
        }

        public boolean matches(
            Array32FW<HttpHeaderFW> headers)
        {
            headers.forEach(header ->
            {
                final String name = header.name().asString();
                final String value = header.value().asString();
                if (METHOD.equals(name))
                {
                    matches.set(method.equals(value));
                }
                else if (SCHEME.equals(name))
                {
                    matches.set(scheme.equals(value));
                }
                else if (PATH.equals(name))
                {
                    matches.set(path.equals(value));
                }
                else if (AUTHORITY.equals(name))
                {
                    matches.set(authority.equals(value));
                }
                else if (CACHE_CONTROL.equals(name))
                {
                    matches.set(value.contains("no-store"));
                }
            });

            return matches.get();
        }
    }

    private final class Http2HeadersEncoder
    {
        private HpackContext context;

        void encodePromise(
            HpackContext encodeContext,
            Array32FW<HttpHeaderFW> headers,
            HpackHeaderBlockFW.Builder headerBlock)
        {
            reset(encodeContext);
            headers.forEach(h -> headerBlock.header(b -> encodeHeader(h.name(), h.value(), b)));
        }

        void encodeHeaders(
            HpackContext encodeContext,
            Array32FW<HttpHeaderFW> headers,
            Map<String8FW, String16FW> overrides,
            HpackHeaderBlockFW.Builder headerBlock)
        {
            reset(encodeContext);

            Map<String8FW, String16FW> headersMap = new LinkedHashMap<>();
            headers.forEach(h -> headersMap.put(newString8FW(h.name()), newString16FW(h.value())));
            headersMap.putAll(overrides);

            final String16FW authority = headersMap.get(HEADER_AUTHORITY);
            if (authority != null)
            {
                final String authorityValue = authority.asString();
                int columnIndex = authorityValue.indexOf(":");
                final String port = authorityValue.substring(columnIndex);
                if (":80".equals(port) || ":443".equals(port))
                {
                    headersMap.put(HEADER_AUTHORITY,  new String16FW(authorityValue.substring(0, columnIndex)));
                }
            }

            final String16FW userAgentHeader = config.userAgentHeader();
            if (userAgentHeader != null)
            {
                headersMap.put(HEADER_USER_AGENT, userAgentHeader);
            }

            headersMap.forEach((n, v) -> headerBlock.header(b -> encodeHeader(n, v, b)));
        }

        void encodeTrailers(
            HpackContext encodeContext,
            Array32FW<HttpHeaderFW> headers,
            HpackHeaderBlockFW.Builder headerBlock)
        {
            reset(encodeContext);
            headers.forEach(h -> headerBlock.header(b -> encodeHeader(h.name(), h.value(), b)));
        }

        private void reset(
            HpackContext encodeContext)
        {
            context = encodeContext;
        }

        private void encodeHeader(
            String8FW name,
            String16FW value,
            HpackHeaderFieldFW.Builder builder)
        {
            final int index = context.index(name.value(), value.value());
            if (index != -1)
            {
                builder.indexed(index);
            }
            else
            {
                builder.literal(literal -> encodeLiteral(literal, context, name.value(), value.value()));
            }
        }

        // TODO dynamic table, Huffman, never indexed
        private void encodeLiteral(
            HpackLiteralHeaderFieldFW.Builder builder,
            HpackContext hpackContext,
            DirectBuffer nameBuffer,
            DirectBuffer valueBuffer)
        {
            builder.type(WITHOUT_INDEXING);
            final int nameIndex = hpackContext.index(nameBuffer);
            if (nameIndex != -1)
            {
                builder.name(nameIndex);
            }
            else
            {
                builder.name(nameBuffer, 0, nameBuffer.capacity());
            }
            builder.value(valueBuffer, 0, valueBuffer.capacity());
        }
    }

    private final class Http2HeadersDecoder
    {
        private HpackContext context;
        private int headerTableSize;
        private MutableBoolean expectDynamicTableSizeUpdate;

        private final Consumer<HpackHeaderFieldFW> decodeHeader;
        private final Consumer<HpackHeaderFieldFW> decodeTrailer;


        Http2ErrorCode connectionError;
        Http2ErrorCode streamError;
        Array32FW<HttpHeaderFW> httpErrorHeader;

        final Map<String, String> headers = new LinkedHashMap<>();
        long contentLength = -1;

        private Http2HeadersDecoder()
        {
            BiConsumer<DirectBuffer, DirectBuffer> nameValue =
                    ((BiConsumer<DirectBuffer, DirectBuffer>) this::collectHeaders)
                            .andThen(this::uppercaseHeaders)
                            .andThen(this::connectionHeaders)
                            .andThen(this::contentLengthHeader)
                            .andThen(this::teHeader);

            Consumer<HpackHeaderFieldFW> consumer = this::validateHeaderFieldType;
            consumer = consumer.andThen(this::dynamicTableSizeUpdate);
            this.decodeHeader = consumer.andThen(h -> decodeHeaderField(h, nameValue));
            this.decodeTrailer = h -> decodeHeaderField(h, this::validateTrailerFieldName);
        }

        void decodeHeaders(
            HpackContext context,
            int headerTableSize,
            MutableBoolean expectDynamicTableSizeUpdate,
            HpackHeaderBlockFW headerBlock)
        {
            reset(context, headerTableSize, expectDynamicTableSizeUpdate);
            headerBlock.forEach(decodeHeader);
        }

        void decodeTrailers(
            HpackContext context,
            int headerTableSize,
            MutableBoolean expectDynamicTableSizeUpdate,
            HpackHeaderBlockFW headerBlock)
        {
            reset(context, headerTableSize, expectDynamicTableSizeUpdate);
            headerBlock.forEach(decodeTrailer);
        }

        boolean error()
        {
            return streamError != null || connectionError != null;
        }

        boolean httpError()
        {
            return httpErrorHeader != null;
        }

        private void reset(
                HpackContext context,
                int headerTableSize,
                MutableBoolean expectDynamicTableSizeUpdate)
        {
            this.context = context;
            this.headerTableSize = headerTableSize;
            this.expectDynamicTableSizeUpdate = expectDynamicTableSizeUpdate;
            this.headers.clear();
            this.connectionError = null;
            this.streamError = null;
            this.httpErrorHeader = null;
            this.contentLength = -1;
        }

        private void validateHeaderFieldType(
                HpackHeaderFieldFW hf)
        {
            if (!error() && hf.type() == UNKNOWN)
            {
                connectionError = Http2ErrorCode.COMPRESSION_ERROR;
            }
        }

        private void dynamicTableSizeUpdate(
                HpackHeaderFieldFW hf)
        {
            if (!error())
            {
                switch (hf.type())
                {
                case INDEXED:
                case LITERAL:
                    expectDynamicTableSizeUpdate.value = false;
                    break;
                case UPDATE:
                    if (!expectDynamicTableSizeUpdate.value)
                    {
                        // dynamic table size update MUST occur at the beginning of the first header block
                        connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                        return;
                    }
                    int maxTableSize = hf.tableSize();
                    if (maxTableSize > headerTableSize)
                    {
                        connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                        return;
                    }
                    context.updateSize(hf.tableSize());
                    break;
                default:
                    break;
                }
            }
        }

        private void validateTrailerFieldName(
                DirectBuffer name,
                DirectBuffer value)
        {
            if (!error())
            {
                if (name.capacity() > 0 && name.getByte(0) == ':')
                {
                    streamError = Http2ErrorCode.PROTOCOL_ERROR;
                    return;
                }
                headers.put(name.getStringWithoutLengthUtf8(0, name.capacity()),
                        value.getStringWithoutLengthUtf8(0, value.capacity()));
            }
        }

        private void connectionHeaders(
                DirectBuffer name,
                DirectBuffer value)
        {
            if (!error() && name.equals(HpackContext.CONNECTION))
            {
                streamError = Http2ErrorCode.PROTOCOL_ERROR;
            }
        }

        private void contentLengthHeader(
                DirectBuffer name,
                DirectBuffer value)
        {
            if (!error() && name.equals(context.nameBuffer(28)))
            {
                String contentLength = value.getStringWithoutLengthUtf8(0, value.capacity());
                this.contentLength = Long.parseLong(contentLength);
            }
        }

        // 8.1.2.2 TE header MUST NOT contain any value other than "trailers".
        private void teHeader(
                DirectBuffer name,
                DirectBuffer value)
        {
            if (!error() && name.equals(TE) && !value.equals(TRAILERS))
            {
                streamError = Http2ErrorCode.PROTOCOL_ERROR;
            }
        }

        private void uppercaseHeaders(
                DirectBuffer name,
                DirectBuffer value)
        {
            if (!error())
            {
                for (int i = 0; i < name.capacity(); i++)
                {
                    if (name.getByte(i) >= 'A' && name.getByte(i) <= 'Z')
                    {
                        streamError = Http2ErrorCode.PROTOCOL_ERROR;
                    }
                }
            }
        }

        // Collect headers into map to resolve target
        // TODO avoid this
        private void collectHeaders(
                DirectBuffer name,
                DirectBuffer value)
        {
            if (!error())
            {
                String nameStr = name.getStringWithoutLengthUtf8(0, name.capacity());
                String valueStr = value.getStringWithoutLengthUtf8(0, value.capacity());
                // TODO cookie needs to be appended with ';'
                headers.merge(nameStr, valueStr, (o, n) -> String.format("%s, %s", o, n));
            }
        }

        private void decodeHeaderField(
            HpackHeaderFieldFW hf,
            BiConsumer<DirectBuffer, DirectBuffer> nameValue)
        {
            if (!error())
            {
                decodeHF(hf, nameValue);
            }
        }

        private void decodeHF(
            HpackHeaderFieldFW hf,
            BiConsumer<DirectBuffer, DirectBuffer> nameValue)
        {
            int index;
            DirectBuffer name = null;
            DirectBuffer value = null;

            switch (hf.type())
            {
            case INDEXED :
                index = hf.index();
                if (!context.valid(index))
                {
                    connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                    return;
                }
                name = context.nameBuffer(index);
                value = context.valueBuffer(index);
                nameValue.accept(name, value);
                break;

            case LITERAL :
                HpackLiteralHeaderFieldFW hpackLiteral = hf.literal();
                if (hpackLiteral.error())
                {
                    connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                    return;
                }

                HpackStringFW hpackValue = hpackLiteral.valueLiteral();

                switch (hpackLiteral.nameType())
                {
                case INDEXED:
                    index = hpackLiteral.nameIndex();
                    if (!context.valid(index))
                    {
                        connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                        return;
                    }
                    name = context.nameBuffer(index);

                    value = hpackValue.payload();
                    if (hpackValue.huffman())
                    {
                        MutableDirectBuffer dst = new UnsafeBuffer(new byte[4096]); // TODO
                        int length = HpackHuffman.decode(value, dst);
                        if (length == -1)
                        {
                            connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                            return;
                        }
                        value = new UnsafeBuffer(dst, 0, length);
                    }
                    nameValue.accept(name, value);
                    break;
                case NEW:
                    HpackStringFW hpackName = hpackLiteral.nameLiteral();
                    name = hpackName.payload();
                    if (hpackName.huffman())
                    {
                        MutableDirectBuffer dst = new UnsafeBuffer(new byte[4096]); // TODO
                        int length = HpackHuffman.decode(name, dst);
                        if (length == -1)
                        {
                            connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                            return;
                        }
                        name = new UnsafeBuffer(dst, 0, length);
                    }

                    value = hpackValue.payload();
                    if (hpackValue.huffman())
                    {
                        MutableDirectBuffer dst = new UnsafeBuffer(new byte[4096]); // TODO
                        int length = HpackHuffman.decode(value, dst);
                        if (length == -1)
                        {
                            connectionError = Http2ErrorCode.COMPRESSION_ERROR;
                            return;
                        }
                        value = new UnsafeBuffer(dst, 0, length);
                    }
                    nameValue.accept(name, value);
                    break;
                }
                if (hpackLiteral.literalType() == INCREMENTAL_INDEXING)
                {
                    // make a copy for name and value as they go into dynamic table (outlives current frame)
                    MutableDirectBuffer nameCopy = new UnsafeBuffer(new byte[name.capacity()]);
                    nameCopy.putBytes(0, name, 0, name.capacity());
                    MutableDirectBuffer valueCopy = new UnsafeBuffer(new byte[value.capacity()]);
                    valueCopy.putBytes(0, value, 0, value.capacity());
                    context.add(nameCopy, valueCopy);
                }
                break;
            default:
                break;
            }
        }
    }

    private boolean requestCacheable(
        Map<String, String> headers)
    {
        final String method = headers.get(METHOD);
        final String contentLength = headers.get(CONTENT_LENGTH);
        return GET_METHOD.equals(method) || method.equals(HEAD_METHOD) && contentLength == null;
    }

    private Map<String, String> asHeadersMap(
        Array32FW<HttpHeaderFW> headers)
    {
        Map<String, String> headersMap = new LinkedHashMap<>();
        headers.forEach(h -> headersMap.put(h.name().asString(), h.value().asString()));
        return headersMap;
    }

    private String8FW newString8FW(
        String8FW value)
    {
        return new String8FW().wrap(value.buffer(), value.offset(), value.limit());
    }

    private String16FW newString16FW(
        String16FW value)
    {
        return new String16FW().wrap(value.buffer(), value.offset(), value.limit());
    }
}
