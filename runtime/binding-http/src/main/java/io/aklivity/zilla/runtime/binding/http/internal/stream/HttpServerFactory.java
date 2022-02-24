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

import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.CONNECTION;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.DEFAULT_ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.KEEP_ALIVE;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.PROXY_CONNECTION;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.TE;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.TRAILERS;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackContext.UPGRADE;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackHeaderFieldFW.HeaderFieldType.UNKNOWN;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.INCREMENTAL_INDEXING;
import static io.aklivity.zilla.runtime.binding.http.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.WITHOUT_INDEXING;
import static io.aklivity.zilla.runtime.binding.http.internal.types.ProxyInfoType.ALPN;
import static io.aklivity.zilla.runtime.binding.http.internal.types.ProxyInfoType.SECURE;
import static io.aklivity.zilla.runtime.binding.http.internal.types.ProxySecureInfoType.VERSION;
import static io.aklivity.zilla.runtime.binding.http.internal.util.BufferUtil.indexOfByte;
import static io.aklivity.zilla.runtime.binding.http.internal.util.BufferUtil.limitOfBytes;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import org.agrona.collections.MutableReference;
import org.agrona.concurrent.AtomicBuffer;
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
import io.aklivity.zilla.runtime.binding.http.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpEndExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.binding.http.internal.util.HttpUtil;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class HttpServerFactory implements HttpStreamFactory
{
    private static final int CLIENT_INITIATED = 1;
    private static final int SERVER_INITIATED = 0;

    private static final int CLEANUP_SIGNAL = 0;
    private static final int DELEGATE_SIGNAL = 1;

    private static final long MAX_REMOTE_BUDGET = Integer.MAX_VALUE;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(new byte[0]);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);

    private static final Array32FW<HttpHeaderFW> HEADERS_200_OK =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .item(h -> h.name(":status").value("200"))
                .build();

    private static final Array32FW<HttpHeaderFW> HEADERS_404_NOT_FOUND =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .item(h -> h.name(":status").value("404"))
                .build();

    private static final Array32FW<HttpHeaderFW> HEADERS_400_BAD_REQUEST =
        new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .item(h -> h.name(":status").value("400"))
            .build();

    private static final Array32FW<HttpHeaderFW> TRAILERS_EMPTY =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .build();

    private static final Pattern REQUEST_LINE_PATTERN =
            Pattern.compile("(?<method>[A-Z]+)\\s+(?<target>[^\\s]+)\\s+(?<version>HTTP/\\d\\.\\d)\r\n");
    private static final Pattern VERSION_PATTERN = Pattern.compile("HTTP/1\\.\\d");
    private static final Pattern HEADER_LINE_PATTERN = Pattern.compile("(?<name>[^\\s:]+):\\s*(?<value>[^\r\n]*)\r\n");
    private static final Pattern CONNECTION_CLOSE_PATTERN = Pattern.compile("(^|\\s*,\\s*)close(\\s*,\\s*|$)");

    private static final byte[] COLON_SPACE_BYTES = ": ".getBytes(US_ASCII);
    private static final byte[] CRLFCRLF_BYTES = "\r\n\r\n".getBytes(US_ASCII);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(US_ASCII);
    private static final byte[] SEMICOLON_BYTES = ";".getBytes(US_ASCII);

    private static final byte COLON_BYTE = ':';
    private static final byte HYPHEN_BYTE = '-';
    private static final byte SPACE_BYTE = ' ';
    private static final byte ZERO_BYTE = '0';

    private static final byte[] HTTP_1_1_BYTES = "HTTP/1.1".getBytes(US_ASCII);
    private static final byte[] REASON_UNRECOGNIZED_STATUS_BYTES = "Unrecognized Status".getBytes(US_ASCII);

    private static final DirectBuffer ZERO_CHUNK = new UnsafeBuffer("0\r\n\r\n".getBytes(US_ASCII));

    private static final DirectBuffer ERROR_400_BAD_REQUEST =
            initResponse(400, "Bad Request");
    private static final DirectBuffer ERROR_400_BAD_REQUEST_OBSOLETE_LINE_FOLDING =
            initResponse(400, "Bad Request - obsolete line folding not supported");
    private static final DirectBuffer ERROR_404_NOT_FOUND =
            initResponse(404, "Not Found");
    private static final DirectBuffer ERROR_414_REQUEST_URI_TOO_LONG =
            initResponse(414, "Request URI Too Long");
    private static final DirectBuffer ERROR_431_HEADERS_TOO_LARGE =
            initResponse(431, "Request Header Fields Too Large");
    private static final DirectBuffer ERROR_501_UNSUPPORTED_TRANSFER_ENCODING =
            initResponse(501, "Unsupported Transfer-Encoding");
    private static final DirectBuffer ERROR_501_METHOD_NOT_IMPLEMENTED =
            initResponse(501, "Not Implemented");
    private static final DirectBuffer ERROR_505_VERSION_NOT_SUPPORTED =
            initResponse(505, "HTTP Version Not Supported");
    private static final DirectBuffer ERROR_507_INSUFFICIENT_STORAGE =
            initResponse(507, "Insufficient Storage");

    private static final String8FW HEADER_AUTHORITY = new String8FW(":authority");
    private static final String8FW HEADER_CONNECTION = new String8FW("connection");
    private static final String8FW HEADER_CONTENT_LENGTH = new String8FW("content-length");
    private static final String8FW HEADER_METHOD = new String8FW(":method");
    private static final String8FW HEADER_PATH = new String8FW(":path");
    private static final String8FW HEADER_SCHEME = new String8FW(":scheme");
    private static final String8FW HEADER_STATUS = new String8FW(":status");
    private static final String8FW HEADER_TRANSFER_ENCODING = new String8FW("transfer-encoding");
    private static final String8FW HEADER_UPGRADE = new String8FW("upgrade");

    private static final String16FW CONNECTION_CLOSE = new String16FW("close");
    private static final String16FW SCHEME_HTTP = new String16FW("http");
    private static final String16FW SCHEME_HTTPS = new String16FW("https");
    private static final String16FW STATUS_200 = new String16FW("200");
    private static final String16FW STATUS_204 = new String16FW("204");
    private static final String16FW TRANSFER_ENCODING_CHUNKED = new String16FW("chunked");

    private static final HttpHeaderFW HEADER_CONNECTION_CLOSE = new HttpHeaderFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .name(HEADER_CONNECTION)
            .value(CONNECTION_CLOSE)
            .build();

    private static final Array32FW<HttpHeaderFW> DEFAULT_HEADERS =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                    .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                    .item(i -> i.name(HEADER_STATUS).value(STATUS_200))
                    .item(i -> i.name(HEADER_CONNECTION).value(CONNECTION_CLOSE))
                    .build();
    private static final Array32FW<HttpHeaderFW> DEFAULT_TRAILERS =
            new Array32FW.Builder<>(new HttpHeaderFW.Builder(), new HttpHeaderFW())
                         .wrap(new UnsafeBuffer(new byte[8]), 0, 8)
                         .build();

    private static final Map<String16FW, String> SCHEME_PORTS;

    private static final Int2ObjectHashMap<byte[]> STATUS_REASONS;

    private static final Set<String> SUPPORTED_METHODS =
            new HashSet<>(asList("GET",
                                 "HEAD",
                                 "POST",
                                 "PUT",
                                 "DELETE",
                                 "CONNECT",
                                 "OPTIONS",
                                 "TRACE"));

    private static final int MAXIMUM_METHOD_LENGTH = SUPPORTED_METHODS.stream().mapToInt(String::length).max().getAsInt();

    static
    {
        final Map<String16FW, String> schemePorts = new HashMap<>();
        schemePorts.put(SCHEME_HTTP, "80");
        schemePorts.put(SCHEME_HTTPS, "443");
        SCHEME_PORTS = schemePorts;
    }

    static
    {
        final Int2ObjectHashMap<byte[]> reasons = new Int2ObjectHashMap<>();

        reasons.put(100, "Continue".getBytes(US_ASCII));
        reasons.put(101, "Switching Protocols".getBytes(US_ASCII));
        reasons.put(102, "Processing".getBytes(US_ASCII));
        reasons.put(103, "Early Hints".getBytes(US_ASCII));

        reasons.put(200, "OK".getBytes(US_ASCII));
        reasons.put(201, "Created".getBytes(US_ASCII));
        reasons.put(202, "Accepted".getBytes(US_ASCII));
        reasons.put(203, "Not Authoritive Information".getBytes(US_ASCII));
        reasons.put(204, "No Content".getBytes(US_ASCII));
        reasons.put(205, "Reset Content".getBytes(US_ASCII));
        reasons.put(206, "Partial Content".getBytes(US_ASCII));
        reasons.put(207, "Multi-Status".getBytes(US_ASCII));
        reasons.put(208, "Already Supported".getBytes(US_ASCII));
        reasons.put(226, "IM Used".getBytes(US_ASCII));

        reasons.put(300, "Multiple Choices".getBytes(US_ASCII));
        reasons.put(301, "Moved Permanently".getBytes(US_ASCII));
        reasons.put(302, "Found".getBytes(US_ASCII));
        reasons.put(303, "See Other".getBytes(US_ASCII));
        reasons.put(304, "Not Modified".getBytes(US_ASCII));
        reasons.put(305, "Use Proxy".getBytes(US_ASCII));
        reasons.put(307, "Temporary Redirect".getBytes(US_ASCII));
        reasons.put(308, "Permanent Redirect".getBytes(US_ASCII));

        reasons.put(400, "Bad Request".getBytes(US_ASCII));
        reasons.put(401, "Unauthorized".getBytes(US_ASCII));
        reasons.put(402, "Payment required".getBytes(US_ASCII));
        reasons.put(403, "Forbidden".getBytes(US_ASCII));
        reasons.put(404, "Not Found".getBytes(US_ASCII));
        reasons.put(405, "Method Not Allowed".getBytes(US_ASCII));
        reasons.put(406, "Not Acceptable".getBytes(US_ASCII));
        reasons.put(407, "Proxy Authentication Required".getBytes(US_ASCII));
        reasons.put(408, "Request Timeout".getBytes(US_ASCII));
        reasons.put(409, "Conflict".getBytes(US_ASCII));
        reasons.put(410, "Gone".getBytes(US_ASCII));
        reasons.put(411, "Length Required".getBytes(US_ASCII));
        reasons.put(412, "Precondition Failed".getBytes(US_ASCII));
        reasons.put(413, "Content Too Large".getBytes(US_ASCII));
        reasons.put(414, "URI Too Long".getBytes(US_ASCII));
        reasons.put(415, "Unsupported Media Type".getBytes(US_ASCII));
        reasons.put(416, "Range Not Satisfiable".getBytes(US_ASCII));
        reasons.put(417, "Expectation Failed".getBytes(US_ASCII));
        reasons.put(421, "Misdirected Request".getBytes(US_ASCII));
        reasons.put(422, "Unprocessable Content".getBytes(US_ASCII));
        reasons.put(423, "Locked".getBytes(US_ASCII));
        reasons.put(424, "Failed Dependency".getBytes(US_ASCII));
        reasons.put(425, "Too Early".getBytes(US_ASCII));
        reasons.put(426, "Upgrade Required".getBytes(US_ASCII));
        reasons.put(428, "Precondition Required".getBytes(US_ASCII));
        reasons.put(429, "Too Many Requests".getBytes(US_ASCII));
        reasons.put(431, "Request Header Fields Too Large".getBytes(US_ASCII));
        reasons.put(451, "Unavailable For Legal Reasons".getBytes(US_ASCII));

        reasons.put(500, "Internal Server Error".getBytes(US_ASCII));
        reasons.put(501, "Not Implemented".getBytes(US_ASCII));
        reasons.put(502, "Bad Gateway".getBytes(US_ASCII));
        reasons.put(503, "Service Unavailable".getBytes(US_ASCII));
        reasons.put(504, "Gateway Timeout".getBytes(US_ASCII));
        reasons.put(505, "HTTP Version Not Supported".getBytes(US_ASCII));
        reasons.put(506, "Variant Also Negotiates".getBytes(US_ASCII));
        reasons.put(507, "Insufficient Storage".getBytes(US_ASCII));
        reasons.put(508, "Loop Detected".getBytes(US_ASCII));
        reasons.put(511, "Network Authentication Required".getBytes(US_ASCII));

        STATUS_REASONS = reasons;
    }

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final AtomicBuffer payloadRO = new UnsafeBuffer(0, 0);
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final HttpBeginExFW beginExRO = new HttpBeginExFW();
    private final HttpDataExFW dataExRO = new HttpDataExFW();
    private final HttpEndExFW endExRO = new HttpEndExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ProxyBeginExFW proxyBeginExRO = new ProxyBeginExFW();

    private final HttpBeginExFW.Builder beginExRW = new HttpBeginExFW.Builder();
    private final HttpBeginExFW.Builder newBeginExRW = new HttpBeginExFW.Builder();
    private final HttpEndExFW.Builder endExRW = new HttpEndExFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final HttpServerDecoder decodeHeaders = this::decodeHeaders;
    private final HttpServerDecoder decodeHeadersOnly = this::decodeHeadersOnly;
    private final HttpServerDecoder decodeChunkHeader = this::decodeChunkHeader;
    private final HttpServerDecoder decodeChunkBody = this::decodeChunkBody;
    private final HttpServerDecoder decodeChunkEnd = this::decodeChunkEnd;
    private final HttpServerDecoder decodeContent = this::decodeContent;
    private final HttpServerDecoder decodeTrailers = this::decodeTrailers;
    private final HttpServerDecoder decodeEmptyLines = this::decodeEmptyLines;
    private final HttpServerDecoder decodeUpgraded = this::decodeUpgraded;
    private final HttpServerDecoder decodeIgnore = this::decodeIgnore;

    private final MutableInteger codecOffset = new MutableInteger();
    private final MutableBoolean hasAuthority = new MutableBoolean();
    private final MutableReference<String> connectionRef = new MutableReference<>();

    private final Http2PrefaceFW http2PrefaceRO = new Http2PrefaceFW();
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

    private final Http2SettingsFW.Builder http2SettingsRW = new Http2SettingsFW.Builder();
    private final Http2GoawayFW.Builder http2GoawayRW = new Http2GoawayFW.Builder();
    private final Http2PingFW.Builder http2PingRW = new Http2PingFW.Builder();
    private final Http2DataFW.Builder http2DataRW = new Http2DataFW.Builder();
    private final Http2HeadersFW.Builder http2HeadersRW = new Http2HeadersFW.Builder();
    private final Http2WindowUpdateFW.Builder http2WindowUpdateRW = new Http2WindowUpdateFW.Builder();
    private final Http2RstStreamFW.Builder http2RstStreamRW = new Http2RstStreamFW.Builder();
    private final Http2PushPromiseFW.Builder http2PushPromiseRW = new Http2PushPromiseFW.Builder();

    private final HpackHeaderBlockFW headerBlockRO = new HpackHeaderBlockFW();

    private final MutableInteger payloadRemaining = new MutableInteger(0);

    private final Http2ServerDecoder decodeHttp2Preface = this::decodeHttp2Preface;
    private final Http2ServerDecoder decodeHttp2FrameType = this::decodeHttp2FrameType;
    private final Http2ServerDecoder decodeHttp2Settings = this::decodeHttp2Settings;
    private final Http2ServerDecoder decodeHttp2Ping = this::decodeHttp2Ping;
    private final Http2ServerDecoder decodeHttp2Goaway = this::decodeGoaway;
    private final Http2ServerDecoder decodeHttp2WindowUpdate = this::decodeHttp2WindowUpdate;
    private final Http2ServerDecoder decodeHttp2Headers = this::decodeHttp2Headers;
    private final Http2ServerDecoder decodeHttp2Continuation = this::decodeHttp2Continuation;
    private final Http2ServerDecoder decodeHttp2Data = this::decodeHttp2Data;
    private final Http2ServerDecoder decodeHttp2DataPayload = this::decodeHttp2DataPayload;
    private final Http2ServerDecoder decodeHttp2Priority = this::decodePriority;
    private final Http2ServerDecoder decodeHttp2RstStream = this::decodeHttp2RstStream;
    private final Http2ServerDecoder decodeHttp2IgnoreOne = this::decodeHttp2IgnoreOne;
    private final Http2ServerDecoder decodeHttp2IgnoreAll = this::decodeHttp2IgnoreAll;

    private final EnumMap<Http2FrameType, Http2ServerDecoder> decodersByFrameType;
    {
        final EnumMap<Http2FrameType, Http2ServerDecoder> decodersByFrameType = new EnumMap<>(Http2FrameType.class);
        decodersByFrameType.put(Http2FrameType.SETTINGS, decodeHttp2Settings);
        decodersByFrameType.put(Http2FrameType.PING, decodeHttp2Ping);
        decodersByFrameType.put(Http2FrameType.GO_AWAY, decodeHttp2Goaway);
        decodersByFrameType.put(Http2FrameType.WINDOW_UPDATE, decodeHttp2WindowUpdate);
        decodersByFrameType.put(Http2FrameType.HEADERS, decodeHttp2Headers);
        decodersByFrameType.put(Http2FrameType.CONTINUATION, decodeHttp2Continuation);
        decodersByFrameType.put(Http2FrameType.DATA, decodeHttp2Data);
        decodersByFrameType.put(Http2FrameType.PRIORITY, decodeHttp2Priority);
        decodersByFrameType.put(Http2FrameType.RST_STREAM, decodeHttp2RstStream);
        this.decodersByFrameType = decodersByFrameType;
    }


    private final Http2HeadersDecoder headersDecoder = new Http2HeadersDecoder();
    private final Http2HeadersEncoder headersEncoder = new Http2HeadersEncoder();

    private final HttpConfiguration config;
    private final MutableDirectBuffer codecBuffer;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer frameBuffer;
    private final BufferPool bufferPool;
    private final BudgetCreditor creditor;
    private final BindingHandler streamFactory;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyBudgetId;
    private final Signaler signaler;
    private final Http2Settings initialSettings;
    private final BufferPool headersPool;
    private final MutableDirectBuffer extensionBuffer;
    private final int decodeMax;
    private final int encodeMax;
    private final int proxyTypeId;
    private final int httpTypeId;
    private final Matcher requestLine;
    private final Matcher versionPart;
    private final Matcher headerLine;
    private final Matcher connectionClose;
    private final int maximumHeadersSize;
    private final Long2ObjectHashMap<HttpBindingConfig> bindings;

    public HttpServerFactory(
        HttpConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.writeBuffer = context.writeBuffer();
        this.bufferPool = context.bufferPool();
        this.creditor = context.creditor();
        this.streamFactory = context.streamFactory();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.signaler = context.signaler();
        this.headersPool = bufferPool.duplicate();
        this.initialSettings = new Http2Settings(config, headersPool);
        this.codecBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.frameBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.extensionBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.httpTypeId = context.supplyTypeId(HttpBinding.NAME);
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.requestLine = REQUEST_LINE_PATTERN.matcher("");
        this.headerLine = HEADER_LINE_PATTERN.matcher("");
        this.versionPart = VERSION_PATTERN.matcher("");
        this.connectionClose = CONNECTION_CLOSE_PATTERN.matcher("");
        this.maximumHeadersSize = bufferPool.slotCapacity();
        this.decodeMax = bufferPool.slotCapacity();
        this.encodeMax = bufferPool.slotCapacity();
        this.bindings = new Long2ObjectHashMap<>();
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();

        HttpBindingConfig binding = bindings.get(routeId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long initialId = begin.streamId();
            final long affinity = begin.affinity();

            HttpVersion version = null;
            boolean secure = false;

            ProxyBeginExFW beginEx = begin.extension().get(proxyBeginExRO::tryWrap);
            if (beginEx != null && beginEx.typeId() == proxyTypeId)
            {
                Array32FW<ProxyInfoFW> infos = beginEx.infos();
                ProxyInfoFW alpn = infos.matchFirst(i -> i.kind() == ALPN);

                secure = infos.matchFirst(i -> i.kind() == SECURE && i.secure().kind() == VERSION) != null;

                if (secure && alpn != null)
                {
                    version = HttpVersion.of(alpn.alpn().asString());
                }
            }

            SortedSet<HttpVersion> supportedVersions = binding.versions();

            if (version == null && !supportedVersions.isEmpty())
            {
                // defaults to HTTP/1.1 if supported
                version = supportedVersions.first();
            }

            if (supportedVersions.contains(version))
            {
                switch (version)
                {
                case HTTP_1_1:
                    final boolean upgrade = !secure && supportedVersions.contains(HttpVersion.HTTP_2);
                    final HttpServer http11 = new HttpServer(network, routeId, initialId, affinity, secure, upgrade);
                    newStream = upgrade ? http11::onNetworkUpgradeable : http11::onNetwork;
                    break;
                case HTTP_2:
                    final Http2Server http2 = new Http2Server(network, routeId, initialId, affinity);
                    newStream = http2::onNetwork;
                    break;
                }
            }
        }

        return newStream;
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long routeId,
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
                .routeId(routeId)
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
        long routeId,
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
                                     .routeId(routeId)
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
        long routeId,
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
                                  .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                               .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                     .routeId(routeId)
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

    private void doFlush(
        MessageConsumer receiver,
        long routeId,
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
                                     .routeId(routeId)
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

    private void doReset(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                     .routeId(routeId)
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
        long routeId,
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
                                        .routeId(routeId)
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


    private int decodeHeaders(
        HttpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final HttpBeginExFW.Builder httpBeginEx = beginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                                                           .typeId(httpTypeId);

        DirectBuffer error = null;

        final int endOfStartAt = limitOfBytes(buffer, offset, limit, CRLF_BYTES);
        if (endOfStartAt != -1)
        {
            if (server.upgrade &&
                endOfStartAt >= offset + 16 &&
                CharSequence.compare("PRI * HTTP/2.0\r\n", new AsciiSequenceView(buffer, offset, 16)) == 0)
            {
                server.delegate = new Http2Server(server);
                signaler.signalNow(server.routeId, server.replyId, DELEGATE_SIGNAL);
                return offset;
            }

            hasAuthority.value = false;
            error = decodeStartLine(buffer, offset, endOfStartAt, httpBeginEx, hasAuthority, server.decodeScheme);
        }
        else if (limit - offset >= maximumHeadersSize)
        {
            error = ERROR_414_REQUEST_URI_TOO_LONG;
        }
        else
        {
            final int endOfMethodLimit = Math.min(limit, offset + MAXIMUM_METHOD_LENGTH);
            final int endOfMethodAt = indexOfByte(buffer, offset, endOfMethodLimit, SPACE_BYTE);
            if (endOfMethodAt != -1)
            {
                final CharSequence method = new AsciiSequenceView(buffer, offset, endOfMethodAt - offset);
                if (!SUPPORTED_METHODS.contains(method))
                {
                    error = ERROR_501_METHOD_NOT_IMPLEMENTED;
                }
            }
            else if (limit > endOfMethodLimit)
            {
                error = ERROR_400_BAD_REQUEST;
            }
        }

        final int endOfHeadersAt = limitOfBytes(buffer, offset, limit, CRLFCRLF_BYTES);
        if (error == null && endOfHeadersAt != -1)
        {
            server.decoder = decodeHeadersOnly;
            connectionRef.ref = null;

            final int endOfHeaderLinesAt = endOfHeadersAt - CRLF_BYTES.length;
            int startOfLineAt = endOfStartAt;
            for (int endOfLineAt = limitOfBytes(buffer, startOfLineAt, endOfHeaderLinesAt, CRLF_BYTES);
                    endOfLineAt != -1 && error == null;
                    startOfLineAt = endOfLineAt,
                    endOfLineAt = limitOfBytes(buffer, startOfLineAt, endOfHeaderLinesAt, CRLF_BYTES))
            {
                error = decodeHeaderLine(server, buffer, offset, startOfLineAt, endOfLineAt,
                                         httpBeginEx, hasAuthority, connectionRef);
            }

            if (error == null && !hasAuthority.value)
            {
                error = ERROR_400_BAD_REQUEST;
            }

            if (error == null)
            {
                HttpBeginExFW beginEx = httpBeginEx.build();

                final Map<String, String> headers = new LinkedHashMap<>();
                beginEx.headers().forEach(h -> headers.put(h.name().asString(), h.value().asString()));

                HttpBindingConfig binding = bindings.get(server.routeId);
                HttpRouteConfig route = binding.resolve(authorization, headers::get);
                if (route != null)
                {
                    if (binding.options != null && binding.options.overrides != null)
                    {
                        binding.options.overrides.forEach((k, v) -> headers.put(k.asString(), v.asString()));

                        final HttpBeginExFW.Builder newBeginEx = newBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                                                                             .typeId(httpTypeId);
                        headers.forEach((k, v) -> newBeginEx.headersItem(i -> i.name(k).value(v)));
                        beginEx = newBeginEx.build();
                    }

                    server.onDecodeHeaders(route.id, traceId, authorization, beginEx);
                }
                else
                {
                    error = ERROR_404_NOT_FOUND;
                }
            }
        }
        else if (error == null && limit - offset >= maximumHeadersSize)
        {
            error = ERROR_414_REQUEST_URI_TOO_LONG;
        }

        if (error != null)
        {
            server.onDecodeHeadersError(traceId, authorization, error);
            server.decoder = decodeIgnore;
        }

        return error == null && endOfHeadersAt != -1 ? endOfHeadersAt : offset;
    }

    private DirectBuffer decodeStartLine(
        DirectBuffer buffer,
        int offset,
        int limit,
        HttpBeginExFW.Builder httpBeginEx,
        MutableBoolean hasAuthority,
        String16FW scheme)
    {
        DirectBuffer error = null;
        final CharSequence startLine = new AsciiSequenceView(buffer, offset, limit - offset);
        if (startLine.length() >= maximumHeadersSize)
        {
            error = ERROR_414_REQUEST_URI_TOO_LONG;
        }
        else if (requestLine.reset(startLine).matches())
        {
            final String method = requestLine.group("method");
            final String target = requestLine.group("target");
            final String version = requestLine.group("version");

            final URI targetURI = createTargetURI(target);

            if (targetURI == null)
            {
                error = ERROR_400_BAD_REQUEST;
            }
            else if (!versionPart.reset(version).matches())
            {
                error = ERROR_505_VERSION_NOT_SUPPORTED;
            }
            else if (targetURI.getUserInfo() != null)
            {
                error = ERROR_400_BAD_REQUEST;
            }
            else if (!SUPPORTED_METHODS.contains(method))
            {
                error = ERROR_501_METHOD_NOT_IMPLEMENTED;
            }
            else
            {
                final String path = targetURI.getRawPath();
                final String authority = targetURI.getAuthority();

                httpBeginEx.headersItem(h -> h.name(HEADER_SCHEME).value(scheme))
                           .headersItem(h -> h.name(HEADER_METHOD).value(method))
                           .headersItem(h -> h.name(HEADER_PATH).value(path));

                if (authority != null)
                {
                    httpBeginEx.headersItem(h -> h.name(HEADER_AUTHORITY).value(authority));
                    hasAuthority.value = true;
                }
            }
        }
        else
        {
            error = ERROR_400_BAD_REQUEST;
        }

        return error;
    }

    private DirectBuffer decodeHeaderLine(
        HttpServer server,
        DirectBuffer buffer,
        int startOfHeadersAt,
        int startOfLineAt,
        int endOfLineAt,
        HttpBeginExFW.Builder httpBeginEx,
        MutableBoolean hasAuthority,
        MutableReference<String> connection)
    {
        DirectBuffer error = null;

        if (endOfLineAt - startOfHeadersAt > maximumHeadersSize)
        {
            error = ERROR_431_HEADERS_TOO_LARGE;
        }
        else if (headerLine.reset(new AsciiSequenceView(buffer, startOfLineAt, endOfLineAt - startOfLineAt)).matches())
        {
            final String name = headerLine.group("name").toLowerCase();
            final String value = headerLine.group("value");

            switch (name)
            {
            case "connection":
                connection.ref = value;

                if (server.decoder == decodeUpgraded)
                {
                    httpBeginEx.headersItem(h -> h.name(HEADER_CONNECTION).value(connection.ref));
                }
                break;

            case "content-length":
                if (server.decoder != decodeHeadersOnly)
                {
                    error = ERROR_400_BAD_REQUEST;
                }
                else
                {
                    final int contentLength = parseInt(value);
                    if (contentLength > 0)
                    {
                        server.decodableContentLength = contentLength;
                        server.decoder = decodeContent;
                    }
                    httpBeginEx.headersItem(h -> h.name(HEADER_CONTENT_LENGTH).value(value));
                }
                break;

            case "host":
                if (!hasAuthority.value)
                {
                    String authority = (value.indexOf(':') == -1)
                            ? String.format("%s:%s", value, SCHEME_PORTS.get(server.decodeScheme))
                            : value;
                    httpBeginEx.headersItem(h -> h.name(HEADER_AUTHORITY).value(authority));
                    hasAuthority.value = true;
                }
                break;

            case "http2-settings":
                // TODO: h2c
                break;

            case "transfer-encoding":
                if (server.decoder != decodeHeadersOnly)
                {
                    error = ERROR_400_BAD_REQUEST;
                }
                else if (!"chunked".equals(value))
                {
                    error = ERROR_501_UNSUPPORTED_TRANSFER_ENCODING;
                }
                else
                {
                    server.decoder = decodeChunkHeader;
                    httpBeginEx.headersItem(h -> h.name(HEADER_TRANSFER_ENCODING).value(TRANSFER_ENCODING_CHUNKED));
                }
                break;

            case "upgrade":
                if (server.decoder != decodeHeadersOnly)
                {
                    error = ERROR_400_BAD_REQUEST;
                }
                else if ("h2c".equals(value))
                {
                    // TODO: h2c
                }
                else
                {
                    // TODO: wait for 101 first
                    server.decoder = decodeUpgraded;
                    httpBeginEx.headersItem(h -> h.name(HEADER_UPGRADE).value(value));

                    if (connection.ref != null)
                    {
                        httpBeginEx.headersItem(h -> h.name(HEADER_CONNECTION).value(connection.ref));
                    }
                }
                break;

            default:
                httpBeginEx.headersItem(h -> h.name(name).value(value));
                break;
            }
        }
        else if (buffer.getByte(startOfLineAt) == SPACE_BYTE)
        {
            error = ERROR_400_BAD_REQUEST_OBSOLETE_LINE_FOLDING;
        }
        else
        {
            error = ERROR_400_BAD_REQUEST;
        }

        return error;
    }

    private int decodeHeadersOnly(
        HttpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        server.onDecodeHeadersOnly(traceId, authorization, EMPTY_OCTETS);
        server.decoder = decodeEmptyLines;
        return offset;
    }

    private int decodeChunkHeader(
        HttpServer server,
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
                server.decodableChunkSize = Integer.parseInt(chunkSizeHex, 0, chunkSizeLength, 16);
                server.decoder = server.decodableChunkSize != 0 ? decodeChunkBody : decodeTrailers;
                progress = chunkHeaderLimit;
            }
            catch (NumberFormatException ex)
            {
                server.onDecodeHeadersError(traceId, authorization, ERROR_400_BAD_REQUEST);
                server.decoder = decodeIgnore;
            }
        }

        return progress;
    }

    private int decodeChunkBody(
        HttpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int decodableBytes = Math.min(limit - offset, server.decodableChunkSize);

        int progress = offset;
        if (decodableBytes > 0)
        {
            progress = server.onDecodeBody(traceId, authorization, budgetId,
                                           buffer, offset, offset + decodableBytes, EMPTY_OCTETS);
            server.decodableChunkSize -= progress - offset;

            if (server.decodableChunkSize == 0)
            {
                server.decoder = decodeChunkEnd;
            }
        }

        return progress;
    }

    private int decodeChunkEnd(
        HttpServer server,
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
                server.onDecodeBodyError(traceId, authorization, ERROR_400_BAD_REQUEST);
                server.decoder = decodeIgnore;
            }
            else
            {
                server.decoder = decodeChunkHeader;
                progress += 2;
            }
        }
        return progress;
    }

    private int decodeContent(
        HttpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int length = Math.min(limit - offset, server.decodableContentLength);

        int progress = offset;
        if (length > 0)
        {
            progress = server.onDecodeBody(traceId, authorization, budgetId, buffer, offset, offset + length, EMPTY_OCTETS);
            server.decodableContentLength -= progress - offset;
        }

        assert server.decodableContentLength >= 0;

        if (server.decodableContentLength == 0)
        {
            server.onDecodeTrailers(traceId, authorization, EMPTY_OCTETS);
            server.decoder = decodeEmptyLines;
        }

        return progress;
    }

    private int decodeTrailers(
        HttpServer server,
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

            server.onDecodeTrailers(traceId, authorization, httpEndEx);
            progress = endOfTrailersAt;
            server.decoder = decodeEmptyLines;
        }
        else if (buffer.getByte(offset) == '\r' &&
            buffer.getByte(offset + 1) == '\n')
        {
            server.onDecodeTrailers(traceId, authorization, EMPTY_OCTETS);
            progress += 2;
            server.decoder = decodeEmptyLines;
        }

        return progress;
    }

    private int decodeEmptyLines(
        HttpServer server,
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
                server.decoder = decodeHeaders;
            }
        }
        return progress;
    }

    private int decodeUpgraded(
        HttpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return server.onDecodeBody(traceId, authorization, budgetId, buffer, offset, limit, EMPTY_OCTETS);
    }

    private int decodeIgnore(
        HttpServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        server.initialAck += reserved;
        server.doNetworkWindow(traceId, authorization, budgetId, 0, decodeMax);
        return limit;
    }

    @FunctionalInterface
    private interface HttpServerDecoder
    {
        int decode(
            HttpServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private enum HttpExchangeState
    {
        PENDING,
        OPEN,
        CLOSED,
    }

    private final class HttpServer
    {
        private final MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final boolean upgrade;

        private int replyPad;
        private boolean replyCloseOnFlush;

        private int decodeSlot;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int encodeSlot;
        private int encodeSlotOffset;

        private MessageConsumer delegateNetwork;
        private Http2Server delegate;

        private HttpServerDecoder decoder;
        private String16FW decodeScheme;
        private int decodableChunkSize;
        private int decodableContentLength;

        private HttpExchange exchange;
        private  int state;
        private long initialSeq;
        private long initialAck;
        private long replySeq;
        private long replyAck;
        private long replyBudgetId;
        private int replyMax;

        private HttpServer(
            MessageConsumer network,
            long routeId,
            long initialId,
            long affinity,
            boolean secure,
            boolean upgrade)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.affinity = affinity;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.decoder = decodeEmptyLines;
            this.upgrade = upgrade;
            this.decodeScheme = secure ? SCHEME_HTTPS : SCHEME_HTTP;
            this.decodeSlot = NO_SLOT;
            this.encodeSlot = NO_SLOT;
            this.delegateNetwork = this::onNetwork;
        }

        private int replyPendingAck()
        {
            return (int)(replySeq - replyAck);
        }

        private int replyWindow()
        {
            return replyMax - replyPendingAck();
        }

        private void onNetworkUpgradeable(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
        {
            delegateNetwork.accept(msgTypeId, buffer, index, length);
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetworkFlush(flush);
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
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetworkSignal(signal);
                break;
            }
        }

        private void onNetworkBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            state = HttpState.openInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            doNetworkWindow(traceId, authorization, 0, 0, decodeMax);
            doNetworkBegin(traceId, authorization, affinity);
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
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            assert initialAck <= initialSeq;
            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
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
                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onNetworkFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final long authorization = flush.authorization();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + flush.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNetwork(traceId, authorization);
            }
            else if (exchange != null)
            {
                exchange.doRequestFlush(traceId, budgetId, reserved, authorization, extension);
            }
        }

        private void onNetworkEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            if (decodeSlot == NO_SLOT)
            {
                final long traceId = end.traceId();
                final long authorization = end.authorization();
                state = HttpState.closeInitial(state);

                cleanupDecodeSlotIfNecessary();

                if (exchange != null)
                {
                    exchange.onNetworkEnd(traceId, authorization);
                }
                else
                {
                    doNetworkEnd(traceId, authorization);
                }
            }

            replyCloseOnFlush = true;
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            state = HttpState.closeInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            cleanupDecodeSlotIfNecessary();

            if (exchange != null)
            {
                exchange.onNetworkAbort(traceId, authorization);
                exchange.onNetworkReset(traceId, authorization);
                doNetworkAbort(traceId, authorization);
            }
            else
            {
                doNetworkEnd(traceId, authorization);
            }
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            state = HttpState.closeReply(state);

            cleanupEncodeSlotIfNecessary();

            if (exchange != null)
            {
                exchange.onNetworkReset(traceId, authorization);
            }
            else
            {
                doNetworkReset(traceId, authorization);
            }
        }

        private void onNetworkWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final long authorization = window.authorization();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBudgetId = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            flushNetworkIfBuffered(traceId, authorization, budgetId);

            if (exchange != null && exchange.responseState == HttpExchangeState.OPEN)
            {
                exchange.doResponseWindow(traceId);
            }
        }

        private void onNetworkSignal(
            SignalFW signal)
        {
            long traceId = signal.traceId();
            int signalId = signal.signalId();

            assert signalId == DELEGATE_SIGNAL;

            delegate.state = state;
            delegate.initialSeq = initialSeq;
            delegate.initialAck = initialAck;
            delegate.initialMax = decodeMax;
            delegate.replySeq = replySeq;
            delegate.replyAck = replyAck;
            delegate.replyMax = replyMax;
            delegate.replyPad = replyPad;

            assert delegate.responseSharedBudgetIndex == NO_CREDITOR_INDEX;
            delegate.responseSharedBudgetIndex = creditor.acquire(delegate.budgetId);
            delegate.replySharedBudget = replyMax - replyPendingAck();

            delegate.decodeSlot = decodeSlot;
            delegate.decodeSlotOffset = decodeSlotOffset;
            delegate.decodeSlotReserved = decodeSlotReserved;
            delegate.decodeNetworkIfNecessary(traceId);

            this.delegateNetwork = delegate::onNetwork;
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
                final int reserved = limit + replyPad;
                doNetworkData(traceId, authorization, budgetId, reserved, buffer, 0, limit);
            }
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            doBegin(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, affinity, EMPTY_OCTETS);
            state = HttpState.openReply(state);
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
            final int length = Math.min(Math.max(replyWindow() - replyPad, 0), maxLength);

            if (length > 0)
            {
                final int required = length + replyPad;

                assert reserved >= required;

                doData(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, budgetId,
                       required, buffer, offset, length, EMPTY_OCTETS);

                replySeq += required;

                assert replySeq <= replyAck + replyMax :
                    String.format("%d <= %d + %d", replySeq, replyAck, replyMax);
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

                if (exchange == null && replyCloseOnFlush)
                {
                    doNetworkEnd(traceId, authorization);
                }
            }
        }

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            cleanupEncodeSlotIfNecessary();
            doEnd(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
            state = HttpState.closeReply(state);
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            cleanupEncodeSlotIfNecessary();
            doAbort(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
            state = HttpState.closeReply(state);
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlotIfNecessary();
            final int initialMax = exchange != null ? decodeMax : 0;
            doReset(network, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            state = HttpState.closeInitial(state);
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int maximum)
        {
            doWindow(network, routeId, initialId, initialSeq, initialAck, maximum, traceId, authorization, budgetId, padding);
        }

        private void flushNetWindow(
            long traceId,
            long budgetId,
            int initialPad)
        {
            final int initialMax = exchange != null ? decodeMax : 0;
            final int decodable = decodeMax - initialMax;

            final long initialAckMax = Math.min(initialAck + decodable, initialSeq);
            if (initialAckMax > initialAck || !HttpState.initialOpened(state))
            {
                initialAck = initialAckMax;
                assert initialAck <= initialSeq;

                doNetworkWindow(traceId, budgetId, 0, initialPad, initialMax);
            }
        }

        private void decodeNetworkIfBuffered(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer decodeBuffer = bufferPool.buffer(decodeSlot);
                final int decodeLength = decodeSlotOffset;
                decodeNetwork(traceId, authorization, budgetId, decodeSlotReserved, decodeBuffer, 0, decodeLength);
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
            HttpServerDecoder previous = null;
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
                }
            }
            else
            {
                cleanupDecodeSlotIfNecessary();
            }
        }

        private void onDecodeHeadersError(
            long traceId,
            long authorization,
            DirectBuffer error)
        {
            replyCloseOnFlush = true;

            doNetworkData(traceId, authorization, 0L, error.capacity() + replyPad, error, 0, error.capacity());

            assert exchange == null;
        }

        private void onDecodeBodyError(
            long traceId,
            long authorization,
            DirectBuffer error)
        {
            cleanupNetwork(traceId, authorization);
        }

        private void onDecodeHeaders(
            long routeId,
            long traceId,
            long authorization,
            HttpBeginExFW beginEx)
        {
            final HttpExchange exchange = new HttpExchange(routeId);
            exchange.doRequestBegin(traceId, authorization, beginEx);

            final HttpHeaderFW connection = beginEx.headers().matchFirst(h -> HEADER_CONNECTION.equals(h.name()));
            exchange.responseClosing = connection != null && connectionClose.reset(connection.value().asString()).matches();

            this.exchange = exchange;
        }

        private void onDecodeHeadersOnly(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            exchange.doRequestEnd(traceId, authorization, extension);
        }

        private int onDecodeBody(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit,
            Flyweight extension)
        {
            return exchange.doRequestData(traceId, authorization, budgetId, buffer, offset, limit, extension);
        }

        private void onDecodeTrailers(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            exchange.doRequestEnd(traceId, authorization, extension);

            if (exchange.requestState == HttpExchangeState.CLOSED &&
                exchange.responseState == HttpExchangeState.CLOSED)
            {
                exchange = null;
            }
        }

        private void doEncodeHeaders(
            HttpExchange exchange,
            long traceId,
            long authorization,
            long budgetId,
            Array32FW<HttpHeaderFW> headers)
        {
            // TODO: queue if pipelined responses arrive out of order
            assert exchange == this.exchange;

            final HttpHeaderFW transferEncoding = headers.matchFirst(h -> HEADER_TRANSFER_ENCODING.equals(h.name()));
            exchange.responseChunked = transferEncoding != null && TRANSFER_ENCODING_CHUNKED.equals(transferEncoding.value());

            final HttpHeaderFW connection = headers.matchFirst(h -> HEADER_CONNECTION.equals(h.name()));
            exchange.responseClosing |= connection != null && connectionClose.reset(connection.value().asString()).matches();

            final HttpHeaderFW upgrade = headers.matchFirst(h -> HEADER_UPGRADE.equals(h.name()));
            exchange.responseClosing |= upgrade != null;

            final HttpHeaderFW contentLength = headers.matchFirst(h -> HEADER_CONTENT_LENGTH.equals(h.name()));
            exchange.responseRemaining = contentLength != null
                    ? parseInt(contentLength.value().asString())
                    : Integer.MAX_VALUE - encodeMax; // avoids responseRemaining overflow

            final HttpHeaderFW status = headers.matchFirst(h -> HEADER_STATUS.equals(h.name()));
            final String16FW statusValue = status != null ? status.value() : STATUS_200;

            codecOffset.value = doEncodeStatus(codecBuffer, 0, statusValue);
            headers.forEach(h -> codecOffset.value = doEncodeHeader(codecBuffer, codecOffset.value, h));
            codecBuffer.putBytes(codecOffset.value, CRLF_BYTES);
            codecOffset.value += CRLF_BYTES.length;

            final int length = codecOffset.value;

            if (length > maximumHeadersSize)
            {
                exchange.onNetworkReset(traceId, authorization);

                replyCloseOnFlush = true;

                DirectBuffer error = ERROR_507_INSUFFICIENT_STORAGE;
                doNetworkData(traceId, authorization, 0L, error.capacity() + replyPad, error, 0, error.capacity());
            }
            else
            {
                final int reserved = length + replyPad;
                doNetworkData(traceId, authorization, budgetId, reserved, codecBuffer, 0, length);
            }
        }

        private int doEncodeStatus(
            MutableDirectBuffer buffer,
            int offset,
            String16FW status)
        {
            int progress = offset;

            buffer.putBytes(progress, HTTP_1_1_BYTES);
            progress += HTTP_1_1_BYTES.length;

            buffer.putByte(progress, SPACE_BYTE);
            progress++;

            final DirectBuffer value = status.value();
            buffer.putBytes(progress, value, 0, value.capacity());
            progress += value.capacity();

            buffer.putByte(progress, SPACE_BYTE);
            progress++;

            final int code = value.parseNaturalIntAscii(0, value.capacity());
            byte[] reason = STATUS_REASONS.get(code);
            if (reason == null)
            {
                reason = REASON_UNRECOGNIZED_STATUS_BYTES;
            }
            buffer.putBytes(progress, reason);
            progress += reason.length;

            buffer.putBytes(progress, CRLF_BYTES);
            progress += CRLF_BYTES.length;

            return progress;
        }

        private int doEncodeHeader(
            MutableDirectBuffer buffer,
            int offset,
            HttpHeaderFW header)
        {
            int progress = offset;
            final DirectBuffer name = header.name().value();
            if (name.getByte(0) != COLON_BYTE)
            {
                final DirectBuffer value = header.value().value();

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

        private void doEncodeBody(
            HttpExchange exchange,
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload)
        {
            assert exchange == this.exchange;

            DirectBuffer buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();

            if (exchange.responseChunked && flags != 0)
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

        private void doEncodeTrailers(
            HttpExchange exchange,
            long traceId,
            long authorization,
            long budgetId,
            Array32FW<HttpHeaderFW> trailers)
        {
            assert exchange == this.exchange;

            if (exchange.responseChunked)
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
                    trailers.forEach(h -> codecOffset.value = doEncodeHeader(writeBuffer, codecOffset.value, h));
                    codecBuffer.putBytes(codecOffset.value, CRLF_BYTES);
                    codecOffset.value += CRLF_BYTES.length;

                    buffer = codecBuffer;
                    offset = 0;
                    limit = codecOffset.value;
                }

                final int reserved = limit + replyPad;
                doNetworkData(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }

            if (replyCloseOnFlush || exchange.responseClosing)
            {
                doNetworkEnd(traceId, authorization);
            }

            if (exchange.requestState == HttpExchangeState.CLOSED &&
                exchange.responseState == HttpExchangeState.CLOSED)
            {
                this.exchange = null;
            }
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            doNetworkReset(traceId, authorization);
            doNetworkAbort(traceId, authorization);

            if (exchange != null)
            {
                exchange.onNetworkAbort(traceId, authorization);
                exchange.onNetworkReset(traceId, authorization);
                exchange = null;
            }
        }

        private void cleanupDecodeSlotIfNecessary()
        {
            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
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

        private final class HttpExchange
        {
            private MessageConsumer application;
            private final long routeId;
            private final long requestId;
            private final long responseId;

            private long requestSeq;
            private long requestAck;
            private int requestMax;
            private int requestPad;

            private long responseSeq;
            private long responseAck;
            private int responseMax;

            private HttpExchangeState requestState;
            private HttpExchangeState responseState;
            private boolean responseChunked;
            private boolean responseClosing;
            private int responseRemaining;

            private long authorization;

            private HttpExchange(
                long routeId)
            {
                this.routeId = routeId;
                this.requestId = supplyInitialId.applyAsLong(routeId);
                this.responseId = supplyReplyId.applyAsLong(requestId);
                this.requestState = HttpExchangeState.PENDING;
                this.responseState = HttpExchangeState.PENDING;
            }

            private void doRequestBegin(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                requestSeq = HttpServer.this.initialSeq;
                requestAck = requestSeq;

                application = newStream(this::onExchange, routeId, requestId, requestSeq, requestAck, requestMax,
                    traceId, authorization, affinity, extension);
            }

            private int doRequestData(
                long traceId,
                long authorization,
                long budgetId,
                DirectBuffer buffer,
                int offset,
                int limit,
                Flyweight extension)
            {
                int requestNoAck = (int)(requestSeq - requestAck);
                int length = Math.min(requestMax - requestNoAck - requestPad, limit - offset);

                if (length > 0)
                {
                    final int reserved = length + requestPad;

                    doData(application, routeId, requestId, requestSeq, requestAck, requestMax,
                        traceId, authorization, budgetId, reserved, buffer, offset, length, extension);

                    requestSeq += reserved;
                    assert requestSeq <= requestAck + requestMax;
                }

                return offset + length;
            }

            private void doRequestEnd(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                switch (requestState)
                {
                case OPEN:
                    doEnd(application, routeId, requestId, requestSeq, requestAck, requestMax,
                        traceId, authorization, extension);
                    break;
                default:
                    requestState = HttpExchangeState.CLOSED;
                    break;
                }
            }

            private void doRequestAbort(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                doAbort(application, routeId, requestId, requestSeq, requestAck, requestMax,
                    traceId, authorization, extension);
                requestState = HttpExchangeState.CLOSED;
            }

            private void doRequestFlush(
                long traceId,
                long budgetId,
                int reserved,
                long authorization,
                OctetsFW extension)
            {
                doFlush(application, routeId, requestId, requestSeq, requestAck, requestMax,
                    traceId, authorization, budgetId, reserved, extension);
            }

            private void onNetworkEnd(
                long traceId,
                long authorization)
            {
                if (requestState != HttpExchangeState.CLOSED)
                {
                    doRequestAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void onNetworkAbort(
                long traceId,
                long authorization)
            {
                if (requestState != HttpExchangeState.CLOSED)
                {
                    doRequestAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void onNetworkReset(
                long traceId,
                long authorization)
            {
                if (responseState == HttpExchangeState.OPEN)
                {
                    doResponseReset(traceId, authorization);
                }
            }

            private void onExchange(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onRequestReset(reset);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onRequestWindow(window);
                    break;
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onResponseBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onResponseData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onResponseEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onResponseAbort(abort);
                    break;
                }
            }

            private void onRequestReset(
                ResetFW reset)
            {
                final long traceId = reset.traceId();
                final long authorization = reset.authorization();

                if (requestState == HttpExchangeState.OPEN)
                {
                    doNetworkReset(traceId, authorization);
                }
                else
                {
                    doEncodeHeaders(this, traceId, authorization, 0L, HEADERS_404_NOT_FOUND);
                }

                requestState = HttpExchangeState.CLOSED;
            }

            private void onRequestWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final long budgetId = window.budgetId();
                final int maximum = window.maximum();
                final int padding = window.padding();

                if (requestState == HttpExchangeState.PENDING)
                {
                    requestState = HttpExchangeState.OPEN;
                }

                assert acknowledge <= sequence;
                assert sequence <= requestSeq;
                assert acknowledge >= requestAck;
                assert maximum >= requestMax;

                requestAck = acknowledge;
                requestMax = maximum;
                requestPad = padding;

                assert requestAck <= requestSeq;

                decodeNetworkIfBuffered(traceId, authorization, budgetId);

                if (decodeSlot == NO_SLOT && requestState == HttpExchangeState.CLOSED)
                {
                    // TODO: non-empty extension?
                    doEnd(application, routeId, requestId, requestSeq, requestAck, requestMax,
                        traceId, authorization, EMPTY_OCTETS);
                }
                else
                {
                    flushNetWindow(traceId, budgetId, requestPad);
                }
            }

            private void onResponseBegin(
                BeginFW begin)
            {
                final long sequence = begin.sequence();
                final long acknowledge = begin.acknowledge();
                final long traceId = begin.traceId();
                authorization = begin.authorization();

                assert acknowledge <= sequence;
                assert sequence >= responseSeq;
                assert acknowledge <= responseAck;

                responseSeq = sequence;
                responseAck = acknowledge;

                assert responseAck <= responseSeq;

                final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);
                final Array32FW<HttpHeaderFW> headers = beginEx != null ? beginEx.headers() : DEFAULT_HEADERS;

                responseState = HttpExchangeState.OPEN;
                doEncodeHeaders(this, traceId, authorization, 0L, headers);
            }

            private void onResponseData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final long authorization = data.authorization();

                assert acknowledge <= sequence;
                assert sequence >= responseSeq;
                assert acknowledge <= responseAck;

                responseSeq = sequence + data.reserved();

                assert responseAck <= responseSeq;

                if (responseSeq > responseAck + replyMax)
                {
                    doResponseReset(traceId, authorization);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    final int flags = data.flags();
                    final long budgetId = data.budgetId();
                    final int reserved = data.reserved();
                    final OctetsFW payload = data.payload();

                    responseRemaining -= data.length();
                    assert responseRemaining >= 0;

                    doEncodeBody(this, traceId, authorization, flags, budgetId, reserved, payload);
                }
            }

            private void onResponseEnd(
                EndFW end)
            {
                final long sequence = end.sequence();
                final long acknowledge = end.acknowledge();
                final long traceId = end.traceId();
                final long authorization = end.authorization();
                final long budgetId = 0L; // TODO

                assert acknowledge <= sequence;
                assert sequence >= responseSeq;
                assert acknowledge <= responseAck;

                responseSeq = sequence;

                final HttpEndExFW endEx = end.extension().get(endExRO::tryWrap);
                final Array32FW<HttpHeaderFW> trailers = endEx != null ? endEx.trailers() : DEFAULT_TRAILERS;

                responseState = HttpExchangeState.CLOSED;
                doEncodeTrailers(this, traceId, authorization, budgetId, trailers);
            }

            private void onResponseAbort(
                AbortFW abort)
            {
                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                responseState = HttpExchangeState.CLOSED;
                doNetworkAbort(traceId, authorization);
            }

            private void doResponseReset(
                long traceId,
                long authorization)
            {
                responseState = HttpExchangeState.CLOSED;
                doReset(application, routeId, responseId, responseSeq, responseAck, responseMax, traceId, authorization);
            }

            private void doResponseWindow(
                long traceId)
            {
                long responseAckMax = Math.max(responseSeq - replyPendingAck() - encodeSlotOffset, responseAck);
                int responseNoAckMin = (int)(responseSeq - responseAckMax);
                int minResponseMax = Math.min(responseRemaining - responseNoAckMin + replyPad, replyMax);

                if (responseAckMax > responseAck ||
                    minResponseMax > responseMax && encodeSlotOffset == 0)
                {
                    responseAck = responseAckMax;
                    assert responseAck <= responseSeq;

                    responseMax = minResponseMax;
                    assert responseMax >= 0;

                    doWindow(application, routeId, responseId, responseSeq, responseAck, responseMax,
                            traceId, authorization, replyBudgetId, HttpServer.this.replyPad);
                }
            }
        }
    }

    private int decodeHttp2Preface(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final Http2PrefaceFW http2Preface = http2PrefaceRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        if (http2Preface != null)
        {
            if (http2Preface.error())
            {
                server.onDecodeError(traceId, authorization, Http2ErrorCode.PROTOCOL_ERROR);
                server.decoder = decodeHttp2IgnoreAll;
            }
            else
            {
                server.onDecodePreface(traceId, authorization, http2Preface);
                progress = http2Preface.limit();
                server.decoder = decodeHttp2FrameType;
            }
        }

        return progress;
    }

    private int decodeHttp2FrameType(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.tryWrap(buffer, offset, limit);

        if (http2FrameInfo != null)
        {
            final int length = http2FrameInfo.length();
            final Http2FrameType type = http2FrameInfo.type();
            final Http2ServerDecoder decoder = decodersByFrameType.getOrDefault(type, decodeHttp2IgnoreOne);
            server.decodedStreamId = http2FrameInfo.streamId();
            server.decodedFlags = http2FrameInfo.flags();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

            if (length > server.localSettings.maxFrameSize)
            {
                error = Http2ErrorCode.FRAME_SIZE_ERROR;
            }
            else if (decoder == null || server.continuationStreamId != 0 && decoder != decodeHttp2Continuation)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                server.onDecodeError(traceId, authorization, error);
                server.decoder = decodeHttp2IgnoreAll;
            }
            else if (limit - http2FrameInfo.limit() >= length)
            {
                server.decoder = decoder;
            }
        }

        return offset;
    }

    private int decodeHttp2Settings(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

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
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            server.onDecodeSettings(traceId, authorization, http2Settings);
            server.decoder = decodeHttp2FrameType;
            progress = http2Settings.limit();
        }

        return progress;
    }

    private int decodeHttp2Ping(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
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
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            final Http2PingFW http2Ping = http2PingRO.wrap(buffer, offset, limit);
            server.onDecodePing(traceId, authorization, http2Ping);
            server.decoder = decodeHttp2FrameType;
            progress = http2Ping.limit();
        }

        return progress;
    }

    private int decodeGoaway(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
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
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            server.onDecodeGoaway(traceId, authorization, http2Goaway);
            server.decoder = decodeHttp2IgnoreAll;
            progress = http2Goaway.limit();
        }

        return progress;
    }

    private int decodeHttp2WindowUpdate(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
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
                if (server.remoteSharedBudget + size > Integer.MAX_VALUE)
                {
                    error = Http2ErrorCode.FLOW_CONTROL_ERROR;
                }
            }
            else
            {
                if (streamId > server.maxClientStreamId || size < 1)
                {
                    error = Http2ErrorCode.PROTOCOL_ERROR;
                }
            }

            if (error == Http2ErrorCode.NO_ERROR)
            {
                server.onDecodeWindowUpdate(traceId, authorization, http2WindowUpdate);
                server.decoder = decodeHttp2FrameType;
                progress = http2WindowUpdate.limit();
            }
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }

        return progress;
    }

    private int decodeHttp2Headers(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Http2HeadersFW http2Headers = http2HeadersRO.wrap(buffer, offset, limit);
        final int streamId = http2Headers.streamId();

        Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

        if ((streamId & 0x01) != 0x01)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            if (server.applicationHeadersProcessed.size() < config.maxConcurrentApplicationHeaders())
            {
                if (server.streams.containsKey(streamId))
                {
                    server.onDecodeTrailers(traceId, authorization, http2Headers);
                }
                else
                {
                    server.onDecodeHeaders(traceId, authorization, http2Headers);
                }
                server.decoder = decodeHttp2FrameType;
                progress = http2Headers.limit();
            }
        }

        return progress;
    }

    private int decodeHttp2Continuation(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
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
            streamId != server.continuationStreamId)
        {
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (server.headersSlotOffset + length > headersPool.slotCapacity())
        {
            // TODO: decoded header list size check, recoverable error instead
            error = Http2ErrorCode.PROTOCOL_ERROR;
        }

        if (error != Http2ErrorCode.NO_ERROR)
        {
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            server.onDecodeContinuation(traceId, authorization, http2Continuation);
            server.decoder = decodeHttp2FrameType;
            progress = http2Continuation.limit();
        }

        return progress;
    }

    private int decodeHttp2Data(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
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
            final int streamId = http2Data.streamId();

            if ((streamId & 0x01) != 0x01)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                server.onDecodeError(traceId, authorization, error);
                server.decoder = decodeHttp2IgnoreAll;
            }
            else
            {
                server.decodableDataBytes = http2Data.dataLength();
                progress = http2Data.dataOffset();

                server.decoder = decodeHttp2DataPayload;
            }
        }

        return progress;
    }

    private int decodeHttp2DataPayload(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final int available = limit - progress;
        final int decodableMax = Math.min(server.decodableDataBytes, bufferPool.slotCapacity());
        final int length = Math.min(available, decodableMax);

        if (available >= decodableMax)
        {
            payloadRO.wrap(buffer, progress, length);
            final int deferred = server.decodableDataBytes - length;

            final int decodedPayload = server.onDecodeData(
                traceId,
                authorization,
                server.decodedStreamId,
                server.decodedFlags,
                deferred,
                payloadRO);
            server.decodableDataBytes -= decodedPayload;
            progress += decodedPayload;
        }

        if (server.decodableDataBytes == 0)
        {
            server.decoder = decodeHttp2FrameType;
        }

        return progress;
    }

    private int decodePriority(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
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
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            final Http2PriorityFW http2Priority = http2PriorityRO.wrap(buffer, offset, limit);
            server.onDecodePriority(traceId, authorization, http2Priority);
            server.decoder = decodeHttp2FrameType;
            progress = http2Priority.limit();
        }

        return progress;
    }

    private int decodeHttp2RstStream(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
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
            server.onDecodeError(traceId, authorization, error);
            server.decoder = decodeHttp2IgnoreAll;
        }
        else
        {
            if (server.applicationHeadersProcessed.size() < config.maxConcurrentApplicationHeaders())
            {
                final Http2RstStreamFW http2RstStream = http2RstStreamRO.wrap(buffer, offset, limit);
                server.onDecodeRstStream(traceId, authorization, http2RstStream);
                server.decoder = decodeHttp2FrameType;
                progress = http2RstStream.limit();
            }
        }

        return progress;
    }

    private int decodeHttp2IgnoreOne(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final Http2FrameInfoFW http2FrameInfo = http2FrameInfoRO.wrap(buffer, offset, limit);
        final int progress = http2FrameInfo.limit() + http2FrameInfo.length();

        server.decoder = decodeHttp2FrameType;
        return progress;
    }

    private int decodeHttp2IgnoreAll(
        Http2Server server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    private static int http2FramePadding(
        final int dataLength,
        final int maxFrameSize)
    {
        final int frameCount = (dataLength + maxFrameSize - 1) / maxFrameSize;

        return frameCount * Http2FrameInfoFW.SIZE_OF_FRAME; // assumes H2 DATA not PADDED
    }

    @FunctionalInterface
    private interface Http2ServerDecoder
    {
        int decode(
            Http2Server server,
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private final class Http2Server
    {
        private final Http2Settings localSettings;
        private final Http2Settings remoteSettings;
        private final HpackContext decodeContext;
        private final HpackContext encodeContext;

        private final Int2ObjectHashMap<Http2Exchange> streams;
        private final LongHashSet applicationHeadersProcessed;
        private final int[] streamsActive = new int[2];

        private final MutableBoolean expectDynamicTableSizeUpdate = new MutableBoolean(true);

        private final MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long budgetId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private long authorization;

        private int replyBudgetReserved;
        private int replySharedBudget;

        private int remoteSharedBudget;
        private int responseSharedBudget;
        private long responseSharedBudgetIndex = NO_CREDITOR_INDEX;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotReserved;
        private int encodeSlotMarkOffset;
        private int encodeHeadersSlotMarkOffset;
        private int encodeReservedSlotMarkOffset;

        private MutableDirectBuffer encodeHeadersBuffer;
        private int encodeHeadersSlotOffset;
        private long encodeHeadersSlotTraceId;

        private MutableDirectBuffer encodeReservedBuffer;
        private int encodeReservedSlotOffset;
        private long encodeReservedSlotTraceId;

        private int headersSlot = NO_SLOT;
        private int headersSlotOffset;

        private Http2ServerDecoder decoder;

        private int state;
        private int maxClientStreamId;
        private int maxServerStreamId;
        private int continuationStreamId;
        private Http2ErrorCode decodeError;
        private LongLongConsumer cleanupHandler;

        private int decodedStreamId;
        private byte decodedFlags;
        private int decodableDataBytes;

        private Http2Server(
            MessageConsumer network,
            long routeId,
            long initialId,
            long affinity)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.affinity = affinity;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.budgetId = supplyBudgetId.getAsLong();
            this.localSettings = new Http2Settings();
            this.remoteSettings = new Http2Settings();
            this.streams = new Int2ObjectHashMap<>();
            this.applicationHeadersProcessed = new LongHashSet();
            this.decoder = decodeHttp2Preface;
            this.decodeContext = new HpackContext(localSettings.headerTableSize, false);
            this.encodeContext = new HpackContext(remoteSettings.headerTableSize, true);
            this.encodeHeadersBuffer = new ExpandableArrayBuffer();
            this.encodeReservedBuffer = new ExpandableArrayBuffer();
            this.remoteSharedBudget = remoteSettings.initialWindowSize;
        }

        private Http2Server(
            HttpServer server)
        {
            this(server.network, server.routeId, server.initialId, server.affinity);
        }

        private int replyPendingAck()
        {
            return (int)(replySeq - replyAck);
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            state = HttpState.openInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            doNetworkWindow(traceId, authorization, 0L, 0, decodeMax);
            doNetworkBegin(traceId, authorization);
        }

        private void onNetworkData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNetwork(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();
                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();
                int reserved = data.reserved();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;

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
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            if (decodeSlot == NO_SLOT)
            {
                final long traceId = end.traceId();
                final long authorization = end.authorization();
                state = HttpState.closeInitial(state);

                cleanupDecodeSlotIfNecessary();

                if (!HttpState.replyClosing(state))
                {
                    state = HttpState.closingReply(state);
                    cleanup(traceId, authorization, this::doNetworkEnd);
                }
            }

            decoder = decodeHttp2IgnoreAll;
        }

        private void onNetworkSignal(
            long traceId,
            long authorization,
            int signalId)
        {
            assert signalId == CLEANUP_SIGNAL;
            cleanupStreams(traceId, authorization);
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
            int remaining = config.maxConcurrentStreamsCleanup();
            for (Iterator<Http2Exchange> iterator = streams.values().iterator();
                 iterator.hasNext() && remaining > 0; remaining--)
            {
                final Http2Exchange stream = iterator.next();
                stream.cleanup(traceId, authorization);
            }

            if (!streams.isEmpty())
            {
                final long timeMillis = Instant.now().plusMillis(config.streamsCleanupDelay()).toEpochMilli();
                signaler.signalAt(timeMillis, CLEANUP_SIGNAL, s -> onNetworkSignal(traceId, authorization, s));
            }
            else
            {
                cleanupHandler.accept(traceId, authorization);
            }
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            state = HttpState.closeInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            cleanupDecodeSlotIfNecessary();

            if (!HttpState.replyClosing(state))
            {
                state = HttpState.closingReply(state);
                cleanup(traceId, authorization, this::doNetworkAbort);
            }
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            state = HttpState.closeReply(state);

            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            if (!HttpState.initialClosing(state))
            {
                state = HttpState.closingInitial(state);
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
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
            {
                System.out.format("[%d] [onNetworkWindow] [0x%016x] [0x%016x] replyBudget %d + %d => %d\n",
                        System.nanoTime(), encodeReservedSlotTraceId, budgetId,
                    replyMax, maximum, replyMax + maximum);
            }

            int credit = (int) (acknowledge - replyAck) + (maximum - replyMax);
            assert credit >= 0;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;

            assert replyAck <= replySeq;

            if (replyBudgetReserved > 0)
            {
                final int reservedCredit = Math.min(credit, replyBudgetReserved);
                replyBudgetReserved -= reservedCredit;
                credit -= reservedCredit;
            }

            if (credit > 0)
            {
                replySharedBudget += credit;
                assert replySharedBudget <= replyMax;
                credit -= credit;
            }

            assert credit == 0;

            encodeNetwork(traceId, authorization, budgetId);

            flushResponseSharedBudget(traceId);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization)
        {
            doBegin(network, routeId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, EMPTY_OCTETS);

            assert responseSharedBudgetIndex == NO_CREDITOR_INDEX;
            responseSharedBudgetIndex = creditor.acquire(budgetId);
            state = HttpState.openReply(state);
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

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();
            doEnd(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
            state = HttpState.closeReply(state);
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();
            doAbort(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
            state = HttpState.closeReply(state);
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlotIfNecessary();
            cleanupHeadersSlotIfNecessary();
            doReset(network, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            state = HttpState.closeInitial(state);
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !HttpState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = HttpState.openInitial(state);

                doWindow(network, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization, budgetId, 0);
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

        private void encodeNetworkData(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (encodeSlotOffset != 0 &&
                (encodeSlotMarkOffset != 0 || encodeHeadersSlotOffset == 0 && encodeReservedSlotMarkOffset == 0))
            {
                final int replyWin = replyMax - replyPendingAck();
                final int encodeLengthMax = encodeSlotMarkOffset != 0 ? encodeSlotMarkOffset : encodeSlotOffset;
                final int encodeLength = Math.max(Math.min(replyWin - replyPad, encodeLengthMax), 0);

                if (encodeLength > 0)
                {
                    final int encodeReserved = encodeLength + replyPad;
                    final int encodeReservedMin = (int) (((long) encodeSlotReserved * encodeLength) / encodeSlotOffset);

                    if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                    {
                        System.out.format("[%d] [encodeNetworkData] [0x%016x] [0x%016x] replyBudget %d - %d => %d\n",
                            System.nanoTime(), traceId, budgetId,
                            replyMax, encodeReserved, replyMax - encodeReserved);

                        System.out.format("[%d] [encodeNetworkData] [0x%016x]  [0x%016x] replySharedBudget %d - %d => %d\n",
                            System.nanoTime(), traceId, budgetId,
                            replySharedBudget, encodeReservedMin, replySharedBudget - encodeReservedMin);
                    }

                    replySharedBudget -= encodeReserved;
                    encodeSlotReserved -= encodeReservedMin;

                    assert encodeSlot != NO_SLOT;
                    final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);

                    doData(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, budgetId,
                        encodeReserved, encodeBuffer, 0, encodeLength, EMPTY_OCTETS);

                    replySeq += encodeReserved;

                    assert replySeq <= replyAck + replyMax :
                        String.format("%d <= %d + %d", replySeq, replyAck, replyMax);

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

                        if (streams.isEmpty() && decoder == decodeHttp2IgnoreAll)
                        {
                            doNetworkEnd(traceId, authorization);
                        }
                    }
                }
            }
        }

        private void encodeNetworkHeaders(
            long authorization,
            long budgetId)
        {
            if (encodeHeadersSlotOffset != 0 &&
                encodeSlotMarkOffset == 0 &&
                encodeReservedSlotMarkOffset == 0)
            {
                final int replyWin = replyMax - replyPendingAck();
                final int maxEncodeLength =
                    encodeHeadersSlotMarkOffset != 0 ? encodeHeadersSlotMarkOffset : encodeHeadersSlotOffset;
                final int encodeLength = Math.max(Math.min(replyWin - replyPad, maxEncodeLength), 0);

                if (encodeLength > 0)
                {
                    final int encodeReserved = encodeLength + replyPad;

                    if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                    {
                        System.out.format("[%d] [encodeNetworkHeaders] [0x%016x] [0x%016x] replyBudget %d - %d => %d\n",
                                System.nanoTime(), encodeHeadersSlotTraceId, budgetId,
                            replyMax, encodeReserved, replyMax - encodeReserved);
                    }

                    doData(network, routeId, replyId, replySeq, replyAck, replyMax, encodeHeadersSlotTraceId,
                        authorization, budgetId, encodeReserved, encodeHeadersBuffer, 0, encodeLength, EMPTY_OCTETS);

                    replySeq += encodeReserved;

                    assert replySeq <= replyAck + replyMax :
                        String.format("%d <= %d + %d", replySeq, replyAck, replyMax);

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

                    replyBudgetReserved += encodeReserved;
                }
            }
        }

        private void encodeNetworkReserved(
            long authorization,
            long budgetId)
        {
            if (encodeReservedSlotOffset != 0 &&
                (encodeReservedSlotMarkOffset != 0 || encodeHeadersSlotOffset == 0 && encodeSlotOffset == 0))
            {
                final int replyWin = replyMax - replyPendingAck();
                final int maxEncodeLength =
                    encodeReservedSlotMarkOffset != 0 ? encodeReservedSlotMarkOffset : encodeReservedSlotOffset;
                final int encodeLength = Math.max(Math.min(replyWin - replyPad, maxEncodeLength), 0);

                if (encodeLength > 0)
                {
                    final int encodeReserved = encodeLength + replyPad;

                    if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                    {
                        System.out.format("[%d] [encodeNetworkReserved] [0x%016x] [0x%016x] replyBudget %d - %d => %d\n",
                                System.nanoTime(), encodeReservedSlotTraceId, budgetId,
                            replyMax, encodeReserved, replyMax - encodeReserved);
                    }

                    doData(network, routeId, replyId, replySeq, replyAck, replyMax, encodeReservedSlotTraceId,
                        authorization, budgetId, encodeReserved, encodeReservedBuffer, 0, encodeLength, EMPTY_OCTETS);

                    replySeq += encodeReserved;

                    assert replySeq <= replyAck + replyMax :
                        String.format("%d <= %d + %d", replySeq, replyAck, replyMax);

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

                    replyBudgetReserved += encodeReserved;
                }
            }
        }

        private void decodeNetworkIfNecessary(
            long traceId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer decodeBuffer = bufferPool.buffer(decodeSlot);
                final int offset = 0;
                final int limit = decodeSlotOffset;
                final int reserved = decodeSlotReserved;

                decodeNetwork(traceId, authorization, 0L, reserved, decodeBuffer, offset, limit);
            }
        }

        private int decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            Http2ServerDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, buffer, progress, limit);
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
            }

            if (!HttpState.initialClosed(state))
            {
                doNetworkWindow(traceId, authorization, budgetId, decodeSlotReserved, initialMax);
            }

            return progress;
        }

        private void onDecodeError(
            long traceId,
            long authorization,
            Http2ErrorCode error)
        {
            this.decodeError = error;
            cleanup(traceId, authorization, this::doEncodeGoaway);
        }

        private void onDecodePreface(
            long traceId,
            long authorization,
            Http2PrefaceFW http2Preface)
        {
            doEncodeSettings(traceId, authorization);
        }

        private void onDecodeSettings(
            long traceId,
            long authorization,
            Http2SettingsFW http2Settings)
        {
            if (http2Settings.ack())
            {
                final int localInitialCredit = initialSettings.initialWindowSize - localSettings.initialWindowSize;

                // initial budget can become negative
                if (localInitialCredit != 0)
                {
                    for (Http2Exchange stream: streams.values())
                    {
                        stream.localBudget += localInitialCredit;
                        stream.flushRequestWindowUpdate(traceId, authorization);
                    }
                }

                localSettings.apply(initialSettings);
            }
            else
            {
                final int remoteInitialBudget = remoteSettings.initialWindowSize;
                http2Settings.forEach(this::onDecodeSetting);

                Http2ErrorCode decodeError = remoteSettings.error();

                if (decodeError == Http2ErrorCode.NO_ERROR)
                {
                    // reply budget can become negative
                    final long remoteInitialCredit = remoteSettings.initialWindowSize - remoteInitialBudget;
                    if (remoteInitialCredit != 0)
                    {
                        for (Http2Exchange stream: streams.values())
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
                    doEncodeSettingsAck(traceId, authorization);
                }
                else
                {
                    onDecodeError(traceId, authorization, decodeError);
                    decoder = decodeHttp2IgnoreAll;
                }
            }
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

        private void onDecodePing(
            long traceId,
            long authorization,
            Http2PingFW http2Ping)
        {
            if (!http2Ping.ack())
            {
                doEncodePingAck(traceId, authorization, http2Ping.payload());
            }
        }

        private void onDecodeGoaway(
            long traceId,
            long authorization,
            Http2GoawayFW http2Goaway)
        {
            final int lastStreamId = http2Goaway.lastStreamId();

            streams.entrySet()
                   .stream()
                   .filter(e -> e.getKey() > lastStreamId)
                   .map(Map.Entry::getValue)
                   .forEach(ex -> ex.cleanup(traceId, authorization));

            remoteSettings.enablePush = 0;
        }

        private void onDecodeWindowUpdate(
            long traceId,
            long authorization,
            Http2WindowUpdateFW http2WindowUpdate)
        {
            final int streamId = http2WindowUpdate.streamId();
            final int credit = http2WindowUpdate.size();

            if (streamId == 0)
            {
                if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                {
                    System.out.format("[%d] [onDecodeWindowUpdate] [0x%016x] [0x%016x] %d + %d => %d \n",
                        System.nanoTime(), traceId, budgetId, remoteSharedBudget, credit, remoteSharedBudget + credit);
                }

                remoteSharedBudget += credit;

                // TODO: instead use HttpState.replyClosed(state)
                if (responseSharedBudgetIndex != NO_CREDITOR_INDEX)
                {
                    flushResponseSharedBudget(traceId);
                }
            }
            else
            {
                final Http2Exchange stream = streams.get(streamId);
                if (stream != null)
                {
                    stream.onResponseWindowUpdate(traceId, authorization, credit);
                }
            }
        }

        private void onDecodeHeaders(
            long traceId,
            long authorization,
            Http2HeadersFW http2Headers)
        {
            final int streamId = http2Headers.streamId();
            final int parentStreamId = http2Headers.parentStream();
            final int dataLength = http2Headers.dataLength();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

            if (streamId <= maxClientStreamId ||
                parentStreamId == streamId ||
                dataLength < 0)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            maxClientStreamId = streamId;

            if (streamsActive[CLIENT_INITIATED] >= localSettings.maxConcurrentStreams)
            {
                error = Http2ErrorCode.REFUSED_STREAM;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                doEncodeRstStream(traceId, authorization, streamId, error);
            }

            final DirectBuffer dataBuffer = http2Headers.buffer();
            final int dataOffset = http2Headers.dataOffset();

            final boolean endHeaders = http2Headers.endHeaders();
            final boolean endRequest = http2Headers.endStream();

            if (endHeaders)
            {
                onDecodeHeaders(traceId, authorization, streamId, dataBuffer, dataOffset, dataOffset + dataLength, endRequest);
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

        private void onDecodeContinuation(
            long traceId,
            long authorization,
            Http2ContinuationFW http2Continuation)
        {
            assert headersSlot != NO_SLOT;
            assert headersSlotOffset != 0;

            final int streamId = http2Continuation.streamId();
            final DirectBuffer payload = http2Continuation.payload();
            final boolean endHeaders = http2Continuation.endHeaders();
            final boolean endRequest = http2Continuation.endStream();

            final MutableDirectBuffer headersBuffer = headersPool.buffer(headersSlot);
            headersBuffer.putBytes(headersSlotOffset, payload, 0, payload.capacity());
            headersSlotOffset += payload.capacity();

            if (endHeaders)
            {
                if (streams.containsKey(streamId))
                {
                    onDecodeTrailers(traceId, authorization, streamId, headersBuffer, 0, headersSlotOffset, endRequest);
                }
                else
                {
                    onDecodeHeaders(traceId, authorization, streamId, headersBuffer, 0, headersSlotOffset, endRequest);
                }
                continuationStreamId = 0;

                cleanupHeadersSlotIfNecessary();
            }
        }

        private void onDecodeHeaders(
            long traceId,
            long authorization,
            int streamId,
            DirectBuffer buffer,
            int offset,
            int limit,
            boolean endRequest)
        {
            final HpackHeaderBlockFW headerBlock = headerBlockRO.wrap(buffer, offset, limit);
            headersDecoder.decodeHeaders(decodeContext, localSettings.headerTableSize, expectDynamicTableSizeUpdate, headerBlock);

            if (headersDecoder.error())
            {
                if (headersDecoder.streamError != null)
                {
                    doEncodeRstStream(traceId, authorization, streamId, headersDecoder.streamError);
                }
                else if (headersDecoder.connectionError != null)
                {
                    onDecodeError(traceId, authorization, headersDecoder.connectionError);
                    decoder = decodeHttp2IgnoreAll;
                }
            }
            else if (headersDecoder.httpError())
            {
                doEncodeHeaders(traceId, authorization, streamId, headersDecoder.httpErrorHeader, true);
            }
            else
            {
                final Map<String, String> headers = headersDecoder.headers;
                final String authority = headers.get(":authority");
                if (authority != null && authority.indexOf(':') == -1)
                {
                    String scheme = headers.get(":scheme");
                    String defaultPort = "https".equals(scheme) ? ":443" : ":80";
                    headers.put(":authority", authority + defaultPort);
                }

                final HttpBindingConfig binding = bindings.get(routeId);
                final HttpRouteConfig route = binding.resolve(authorization, headers::get);
                if (route == null)
                {
                    doEncodeHeaders(traceId, authorization, streamId, HEADERS_404_NOT_FOUND, true);
                }
                else
                {
                    if (binding.options != null && binding.options.overrides != null)
                    {
                        binding.options.overrides.forEach((k, v) -> headers.put(k.asString(), v.asString()));
                    }

                    final long routeId = route.id;
                    final long contentLength = headersDecoder.contentLength;

                    final Http2Exchange exchange = new Http2Exchange(routeId, streamId, contentLength);

                    final HttpBeginExFW beginEx = beginExRW.wrap(extensionBuffer, 0, extensionBuffer.capacity())
                            .typeId(httpTypeId)
                            .headers(hs -> headers.forEach((n, v) -> hs.item(h -> h.name(n).value(v))))
                            .build();

                    exchange.doRequestBegin(traceId, authorization, beginEx);

                    if (endRequest)
                    {
                        exchange.doRequestEnd(traceId, authorization, EMPTY_OCTETS);
                    }
                }
            }
        }

        private void onDecodeTrailers(
            long traceId,
            long authorization,
            Http2HeadersFW http2Trailers)
        {
            final int streamId = http2Trailers.parentStream();
            final int dataLength = http2Trailers.dataLength();

            Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

            if (dataLength < 0)
            {
                error = Http2ErrorCode.PROTOCOL_ERROR;
            }

            if (error != Http2ErrorCode.NO_ERROR)
            {
                doEncodeRstStream(traceId, authorization, streamId, error);
            }

            final DirectBuffer dataBuffer = http2Trailers.buffer();
            final int dataOffset = http2Trailers.dataOffset();

            final boolean endHeaders = http2Trailers.endHeaders();
            final boolean endRequest = http2Trailers.endStream();

            if (endHeaders)
            {
                onDecodeTrailers(traceId, authorization, streamId, dataBuffer, dataOffset, dataOffset + dataLength, endRequest);
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

        private void onDecodeTrailers(
            long traceId,
            long authorization,
            int streamId,
            DirectBuffer buffer,
            int offset,
            int limit,
            boolean endRequest)
        {
            final Http2Exchange exchange = streams.get(streamId);
            if (exchange != null)
            {
                final HpackHeaderBlockFW headerBlock = headerBlockRO.wrap(buffer, offset, limit);
                headersDecoder.decodeTrailers(decodeContext, localSettings.headerTableSize,
                                              expectDynamicTableSizeUpdate, headerBlock);

                if (headersDecoder.error())
                {
                    if (headersDecoder.streamError != null)
                    {
                        doEncodeRstStream(traceId, authorization, streamId, headersDecoder.streamError);
                        exchange.cleanup(traceId, authorization);
                    }
                    else if (headersDecoder.connectionError != null)
                    {
                        onDecodeError(traceId, authorization, headersDecoder.connectionError);
                        decoder = decodeHttp2IgnoreAll;
                    }
                }
                else
                {
                    final Map<String, String> trailers = headersDecoder.headers;
                    final HttpEndExFW endEx = endExRW.wrap(extensionBuffer, 0, extensionBuffer.capacity())
                            .typeId(httpTypeId)
                            .trailers(ts -> trailers.forEach((n, v) -> ts.item(t -> t.name(n).value(v))))
                            .build();

                    exchange.doRequestEnd(traceId, authorization, endEx);
                }
            }
        }

        private int onDecodeData(
            long traceId,
            long authorization,
            int streamId,
            byte flags,
            int deferred,
            DirectBuffer payload)
        {
            int progress = 0;

            final Http2Exchange exchange = streams.get(streamId);

            if (exchange == null)
            {
                progress += payload.capacity();
            }
            else
            {
                Http2ErrorCode error = Http2ErrorCode.NO_ERROR;

                if (HttpState.initialClosing(exchange.state))
                {
                    error = Http2ErrorCode.STREAM_CLOSED;
                }

                if (error != Http2ErrorCode.NO_ERROR)
                {
                    exchange.cleanup(traceId, authorization);
                    doEncodeRstStream(traceId, authorization, streamId, error);
                    progress += payloadRemaining.value;
                }
                else
                {
                    final int payloadLength = payload.capacity();

                    if (payloadLength > 0)
                    {
                        payloadRemaining.set(payloadLength);
                        exchange.doRequestData(traceId, authorization, payload, payloadRemaining);
                        progress += payloadLength - payloadRemaining.value;
                        deferred += payloadRemaining.value;
                    }

                    if (deferred == 0 && Http2Flags.endStream(flags))
                    {
                        if (exchange.contentLength != -1 && exchange.contentObserved != exchange.contentLength)
                        {
                            doEncodeRstStream(traceId, authorization, streamId, Http2ErrorCode.PROTOCOL_ERROR);
                        }
                        else
                        {
                            exchange.doRequestEnd(traceId, authorization, EMPTY_OCTETS);
                        }
                    }
                }
            }

            return progress;
        }

        private void onDecodePriority(
            long traceId,
            long authorization,
            Http2PriorityFW http2Priority)
        {
            final int streamId = http2Priority.streamId();
            final int parentStream = http2Priority.parentStream();

            final Http2Exchange exchange = streams.get(streamId);
            if (exchange != null)
            {
                if (parentStream == streamId)
                {
                    doEncodeRstStream(traceId, authorization, streamId, Http2ErrorCode.PROTOCOL_ERROR);
                }
            }
        }

        private void onDecodeRstStream(
            long traceId,
            long authorization,
            Http2RstStreamFW http2RstStream)
        {
            final int streamId = http2RstStream.streamId();
            final Http2Exchange exchange = streams.get(streamId);

            if (exchange != null)
            {
                exchange.cleanup(traceId, authorization);
            }
        }

        private void flushResponseSharedBudget(
            long traceId)
        {
            final int slotCapacity = bufferPool.slotCapacity();
            final int responseSharedPadding = http2FramePadding(remoteSharedBudget, remoteSettings.maxFrameSize);
            final int remoteSharedBudgetMax = remoteSharedBudget + responseSharedPadding + replyPad;
            final int responseSharedCredit =
                Math.min(slotCapacity - responseSharedBudget - encodeSlotReserved, replySharedBudget);
            final int responseSharedBudgetDelta = remoteSharedBudgetMax - (responseSharedBudget + encodeSlotReserved);
            final int replySharedCredit = Math.min(responseSharedCredit, responseSharedBudgetDelta);

            if (replySharedCredit > 0)
            {
                final long responseSharedPrevious =
                    creditor.credit(traceId, responseSharedBudgetIndex, replySharedCredit);

                if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                {
                    System.out.format("[%d] [flushResponseSharedBudget] [0x%016x] [0x%016x] " +
                                      "responseSharedBudget %d + %d => %d\n",
                        System.nanoTime(), traceId, budgetId,
                        responseSharedBudget, replySharedCredit, responseSharedBudget + replySharedCredit);
                }

                responseSharedBudget += replySharedCredit;

                final long responseSharedBudgetUpdated = responseSharedPrevious + replySharedCredit;
                assert responseSharedBudgetUpdated <= slotCapacity
                    : String.format("%d <= %d, remoteSharedBudget = %d",
                    responseSharedBudgetUpdated, slotCapacity, remoteSharedBudget);

                assert responseSharedBudget <= slotCapacity
                    : String.format("%d <= %d", responseSharedBudget, slotCapacity);

                assert replySharedBudget <= slotCapacity
                    : String.format("%d <= %d", replySharedBudget, slotCapacity);
            }
        }

        private void onEncodePromise(
            long traceId,
            long authorization,
            int streamId,
            Array32FW<HttpHeaderFW> promise)
        {
            final Map<String, String> headers = headersDecoder.headers;
            headers.clear();
            promise.forEach(h -> headers.put(h.name().asString(), h.value().asString()));

            final HttpBindingConfig binding = bindings.get(routeId);
            final HttpRouteConfig route = binding.resolve(authorization, headers::get);
            if (route != null)
            {
                final int pushId =
                    remoteSettings.enablePush == 1 &&
                    streamsActive[SERVER_INITIATED] < remoteSettings.maxConcurrentStreams
                        ? (streamId & 0x01) == CLIENT_INITIATED
                            ? streamId
                            : streams.entrySet()
                                     .stream()
                                     .map(Map.Entry::getValue)
                                     .filter(ex -> (ex.streamId & 0x01) == CLIENT_INITIATED)
                                     .filter(Http2Exchange::isResponseOpen)
                                     .mapToInt(ex -> ex.streamId)
                                     .findAny()
                                     .orElse(-1)
                        : -1;

                if (pushId != -1)
                {
                    if (binding.options != null && binding.options.overrides != null)
                    {
                        binding.options.overrides.forEach((k, v) -> headers.put(k.asString(), v.asString()));
                    }

                    final long routeId = route.id;
                    final long contentLength = headersDecoder.contentLength;
                    final int promiseId = ++maxServerStreamId << 1;

                    doEncodePushPromise(traceId, authorization, pushId, promiseId, promise);

                    final Http2Exchange exchange = new Http2Exchange(routeId, promiseId, contentLength);

                    final HttpBeginExFW beginEx = beginExRW.wrap(extensionBuffer, 0, extensionBuffer.capacity())
                            .typeId(httpTypeId)
                            .headers(hs -> headers.forEach((n, v) -> hs.item(i -> i.name(n).value(v))))
                            .build();

                    exchange.doRequestBegin(traceId, authorization, beginEx);

                    exchange.doRequestEnd(traceId, authorization, EMPTY_OCTETS);
                }
            }
        }

        private void doEncodeSettings(
            long traceId,
            long authorization)
        {
            final Http2SettingsFW http2Settings = http2SettingsRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .maxConcurrentStreams(initialSettings.maxConcurrentStreams)
                    .initialWindowSize(initialSettings.initialWindowSize)
                    .maxHeaderListSize(initialSettings.maxHeaderListSize)
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2Settings);
        }

        private void doEncodeSettingsAck(
            long traceId,
            long authorization)
        {
            final Http2SettingsFW http2Settings = http2SettingsRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .ack()
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2Settings);
        }

        private void doEncodeGoaway(
            long traceId,
            long authorization)
        {
            final Http2GoawayFW http2Goaway = http2GoawayRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(0)
                    .lastStreamId(0) // TODO: maxClientStreamId?
                    .errorCode(decodeError)
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2Goaway);
            doNetworkEnd(traceId, authorization);
        }

        private void doEncodePingAck(
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

        private void doEncodeHeaders(
            long traceId,
            long authorization,
            int streamId,
            Array32FW<HttpHeaderFW> headers,
            boolean endResponse)
        {
            final Http2HeadersFW http2Headers = http2HeadersRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(streamId)
                    .headers(hb -> headersEncoder.encodeHeaders(encodeContext, headers, hb))
                    .endHeaders()
                    .endStream(endResponse)
                    .build();

            doNetworkHeadersData(traceId, authorization, 0L, http2Headers);
        }

        private void doEncodeData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            int streamId,
            OctetsFW payload)
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
                        .payload(buffer, progress, length)
                        .build();
                frameOffset = http2Data.limit();
                progress += length;
            }

            assert progress == limit;

            doNetworkData(traceId, authorization, 0L, reserved, frameBuffer, 0, frameOffset);
        }

        private void doEncodeTrailers(
            long traceId,
            long authorization,
            int streamId,
            Array32FW<HttpHeaderFW> trailers)
        {
            if (trailers.isEmpty())
            {
                final Http2DataFW http2Data = http2DataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                        .streamId(streamId)
                        .endStream()
                        .build();

                doNetworkReservedData(traceId, authorization, 0L, http2Data);
            }
            else
            {
                final Http2HeadersFW http2Headers = http2HeadersRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                        .streamId(streamId)
                        .headers(hb -> headersEncoder.encodeTrailers(encodeContext, trailers, hb))
                        .endHeaders()
                        .endStream()
                        .build();

                doNetworkReservedData(traceId, authorization, 0L, http2Headers);
            }
        }

        private void doEncodePushPromise(
            long traceId,
            long authorization,
            int streamId,
            int promiseId,
            Array32FW<HttpHeaderFW> promise)
        {
            final Http2PushPromiseFW http2PushPromise = http2PushPromiseRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(streamId)
                    .promisedStreamId(promiseId)
                    .headers(hb -> headersEncoder.encodePromise(encodeContext, promise, hb))
                    .endHeaders()
                    .build();

            doNetworkHeadersData(traceId, authorization, 0L, http2PushPromise);
        }

        private void doEncodeRstStream(
            long traceId,
            long authorization,
            int streamId,
            Http2ErrorCode error)
        {
            final Http2RstStreamFW http2RstStream = http2RstStreamRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .streamId(streamId)
                    .errorCode(error)
                    .build();

            doNetworkReservedData(traceId, authorization, 0L, http2RstStream);
        }

        private void doEncodeWindowUpdates(
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

        private void cleanupHeadersSlotIfNecessary()
        {
            if (headersSlot != NO_SLOT)
            {
                bufferPool.release(headersSlot);
                headersSlot = NO_SLOT;
                headersSlotOffset = 0;
            }
        }

        private void cleanupEncodeSlotIfNecessary()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;

                if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                {
                    System.out.format("[%d] [cleanupEncodeSlotIfNecessary] [0x%016x] [0x%016x] encode encodeSlotOffset => %d\n",
                        System.nanoTime(), 0, budgetId, encodeSlotOffset);
                }
            }
        }

        private void cleanupBudgetCreditorIfNecessary()
        {
            if (responseSharedBudgetIndex != NO_CREDITOR_INDEX)
            {
                creditor.release(responseSharedBudgetIndex);
                responseSharedBudgetIndex = NO_CREDITOR_INDEX;
            }
        }

        private final class Http2Exchange
        {
            private MessageConsumer application;
            private final long routeId;
            private final long requestId;
            private final long responseId;
            private final int streamId;
            private final long contentLength;

            private int state;
            private long contentObserved;

            private long requestSeq;
            private long requestAck;
            private int requestMax;
            private int requestPadding;
            private long requestBudgetId;
            private BudgetDebitor requestDebitor;
            private long requestDebitorIndex = NO_DEBITOR_INDEX;

            private int localBudget;
            private int remoteBudget;

            private long responseSeq;
            private long responseAck;
            private int responseMax;

            private Http2Exchange(
                long routeId,
                int streamId,
                long contentLength)
            {
                this.routeId = routeId;
                this.streamId = streamId;
                this.contentLength = contentLength;
                this.requestId = supplyInitialId.applyAsLong(routeId);
                this.responseId = supplyReplyId.applyAsLong(requestId);
            }


            private int initialWindow()
            {
                return requestMax - (int)(requestSeq - requestAck);
            }

            private void doRequestBegin(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                assert state == 0;
                state = HttpState.openingInitial(state);

                application = newStream(this::onExchange, routeId, requestId, requestSeq, requestAck, requestMax,
                    traceId, authorization, affinity, extension);
                streams.put(streamId, this);
                streamsActive[streamId & 0x01]++;
                applicationHeadersProcessed.add(streamId);
                localBudget = localSettings.initialWindowSize;

                onResponseWindowUpdate(traceId, authorization, remoteSettings.initialWindowSize);
            }

            private void doRequestData(
                long traceId,
                long authorization,
                DirectBuffer buffer,
                MutableInteger remaining)
            {
                assert HttpState.initialOpening(state);

                if (localBudget < remaining.value)
                {
                    doEncodeRstStream(traceId, authorization, streamId, Http2ErrorCode.FLOW_CONTROL_ERROR);
                    cleanup(traceId, authorization);
                }
                else
                {
                    int length = Math.max(Math.min(initialWindow() - requestPadding, remaining.value), 0);
                    int reserved = length + requestPadding;

                    if (requestDebitorIndex != NO_DEBITOR_INDEX && requestDebitor != null)
                    {
                        final int minimum = reserved; // TODO: fragmentation
                        reserved = requestDebitor.claim(requestDebitorIndex, requestId, minimum, reserved);
                        length = Math.max(reserved - requestPadding, 0);
                    }

                    if (length > 0)
                    {
                        doData(application, routeId, requestId, requestSeq, requestAck, requestMax, traceId,
                            authorization, requestBudgetId, reserved, buffer, 0, length, EMPTY_OCTETS);
                        contentObserved += length;

                        requestSeq += reserved;
                        assert requestSeq <= requestAck + requestMax;
                    }

                    localBudget -= length;
                    remaining.value -= length;
                    assert remaining.value >= 0;
                }
            }

            private void doRequestEnd(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                if (!HttpState.initialOpened(state))
                {
                    state = HttpState.closingInitial(state);
                }
                else
                {
                    flushRequestEnd(traceId, authorization, extension);
                }
            }

            private void doRequestAbort(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setRequestClosed();

                doAbort(application, routeId, requestId, requestSeq, requestAck, requestMax, traceId, authorization, extension);
            }

            private void doRequestAbortIfNecessary(
                long traceId,
                long authorization)
            {
                if (!HttpState.initialClosed(state))
                {
                    doRequestAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void onExchange(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onRequestReset(reset);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onRequestWindow(window);
                    break;
                case BeginFW.TYPE_ID:
                    final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onResponseBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    final DataFW data = dataRO.wrap(buffer, index, index + length);
                    onResponseData(data);
                    break;
                case EndFW.TYPE_ID:
                    final EndFW end = endRO.wrap(buffer, index, index + length);
                    onResponseEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onResponseAbort(abort);
                    break;
                case FlushFW.TYPE_ID:
                    final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    onResponseFlush(flush);
                    break;
                }
            }

            private void onRequestReset(
                ResetFW reset)
            {
                setRequestClosed();

                final long traceId = reset.traceId();

                if (HttpState.replyOpened(state))
                {
                    doEncodeRstStream(traceId, authorization, streamId, Http2ErrorCode.NO_ERROR);
                }
                else
                {
                    doEncodeHeaders(traceId, authorization, streamId, HEADERS_404_NOT_FOUND, true);
                }

                decodeNetworkIfNecessary(traceId);
                cleanup(traceId, authorization);
            }

            private void onRequestWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final long traceId = window.traceId();
                final long budgetId = window.budgetId();
                final int maximum = window.maximum();
                final int padding = window.padding();

                assert acknowledge <= sequence;
                assert sequence <= requestSeq;
                assert acknowledge >= requestAck;
                assert maximum >= requestMax;

                state = HttpState.openInitial(state);

                requestAck = acknowledge;
                requestMax = maximum;
                requestPadding = padding;
                requestBudgetId = budgetId;

                if (requestBudgetId != 0L && requestDebitorIndex == NO_DEBITOR_INDEX)
                {
                    requestDebitor = supplyDebitor.apply(budgetId);
                    requestDebitorIndex = requestDebitor.acquire(budgetId, initialId, Http2Server.this::decodeNetworkIfNecessary);
                }

                decodeNetworkIfNecessary(traceId);

                if (!HttpState.initialClosed(state))
                {
                    if (HttpState.initialClosing(state))
                    {
                        // TODO: trailers extension?
                        flushRequestEnd(traceId, authorization, EMPTY_OCTETS);
                    }
                    else
                    {
                        flushRequestWindowUpdate(traceId, authorization);
                    }
                }
                applicationHeadersProcessed.remove(streamId);
            }

            private void flushRequestEnd(
                long traceId,
                long authorization,
                Flyweight extension)
            {
                setRequestClosed();
                doEnd(application, routeId, requestId, requestSeq, requestAck, requestMax, traceId, authorization, extension);
            }

            private void flushRequestWindowUpdate(
                long traceId,
                long authorization)
            {
                final int initialWindow = initialWindow();
                final int size = initialWindow - localBudget;
                if (size > 0)
                {
                    localBudget = initialWindow;
                    doEncodeWindowUpdates(traceId, authorization, streamId, size);
                }
            }

            private void setRequestClosed()
            {
                assert !HttpState.initialClosed(state);

                state = HttpState.closeInitial(state);
                cleanupRequestDebitorIfNecessary();
                removeStreamIfNecessary();
            }

            private void cleanupRequestDebitorIfNecessary()
            {
                if (requestDebitorIndex != NO_DEBITOR_INDEX)
                {
                    requestDebitor.release(requestDebitorIndex, initialId);
                    requestDebitorIndex = NO_DEBITOR_INDEX;
                }
            }

            private boolean isResponseOpen()
            {
                return HttpState.replyOpened(state);
            }

            private void onResponseBegin(
                BeginFW begin)
            {
                final long sequence = begin.sequence();
                final long acknowledge = begin.acknowledge();
                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                state = HttpState.openReply(state);

                assert acknowledge <= sequence;
                assert sequence >= responseSeq;
                assert acknowledge >= responseAck;

                responseSeq = sequence;
                responseAck = acknowledge;

                final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);
                final Array32FW<HttpHeaderFW> headers = beginEx != null ? beginEx.headers() : HEADERS_200_OK;

                doEncodeHeaders(traceId, authorization, streamId, headers, false);
            }

            private void onResponseData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final long authorization = data.authorization();
                final int reserved = data.reserved();

                assert acknowledge <= sequence;
                assert sequence >= responseSeq;
                assert acknowledge <= responseAck;

                if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                {
                    System.out.format("[%d] [onResponseData] [0x%016x] [0x%016x] responseSharedBudget %d - %d => %d\n",
                            System.nanoTime(), traceId, budgetId,
                            responseSharedBudget, reserved, responseSharedBudget - reserved);
                }

                responseSeq = sequence + reserved;

                assert responseAck <= responseSeq;
                responseSharedBudget -= reserved;

                assert responseSharedBudget >= 0;

                if (responseSeq > responseAck + responseMax)
                {
                    doResponseReset(traceId, authorization);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    final OctetsFW payload = data.payload();
                    final OctetsFW extension = data.extension();
                    final HttpDataExFW dataEx = extension.get(dataExRO::tryWrap);

                    if (dataEx != null)
                    {
                        final Array32FW<HttpHeaderFW> promise = dataEx.promise();

                        onEncodePromise(traceId, authorization, streamId, promise);
                    }

                    if (payload != null)
                    {
                        final int flags = data.flags();
                        final long budgetId = data.budgetId();
                        final int length = data.length();

                        if (HttpConfiguration.DEBUG_HTTP2_BUDGETS)
                        {
                            System.out.format("[%d] [onResponseData] [0x%016x] [0x%016x] remoteBudget %d - %d => %d \n",
                                System.nanoTime(), traceId, budgetId, remoteBudget, length, remoteBudget - length);

                            System.out.format("[%d] [onResponseData] [0x%016x] [0x%016x] remoteSharedBudget %d - %d => %d \n",
                                System.nanoTime(), traceId, budgetId, remoteSharedBudget, length, remoteSharedBudget - length);
                        }

                        remoteBudget -= length;
                        remoteSharedBudget -= length;

                        doEncodeData(traceId, authorization, flags, budgetId, reserved, streamId, payload);

                        final int remotePaddableMax = Math.min(remoteBudget, bufferPool.slotCapacity());
                        final int remotePadding = http2FramePadding(remotePaddableMax, remoteSettings.maxFrameSize);
                        final int responsePadding = replyPad + remotePadding;

                        final int responseWin = responseMax - (int)(responseSeq - responseAck);
                        final int minimumClaim = 1024;
                        final int responseCreditMin = (responseWin <= responsePadding + minimumClaim) ? 0 : remoteBudget >> 1;

                        flushResponseWindow(traceId, authorization, responseCreditMin);
                    }
                }
            }

            private void onResponseFlush(
                FlushFW flush)
            {
                final long sequence = flush.sequence();
                final long acknowledge = flush.acknowledge();
                final long traceId = flush.traceId();
                final long budgetId = flush.budgetId();
                final int reserved = flush.reserved();
                final OctetsFW extension = flush.extension();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge <= replyAck;

                responseSeq = sequence;

                assert replyAck <= replySeq;

                if (responseSeq > responseAck + responseMax)
                {
                    doResponseReset(traceId, authorization);
                    doNetworkAbort(traceId, authorization);
                }
                else
                {
                    //doNetFlush(traceId, budgetId, reserved, extension);
                }
            }

            private void onResponseEnd(
                EndFW end)
            {
                setResponseClosed();

                final HttpEndExFW endEx = end.extension().get(endExRO::tryWrap);
                final Array32FW<HttpHeaderFW> trailers = endEx != null ? endEx.trailers() : TRAILERS_EMPTY;

                final long traceId = end.traceId();
                final long authorization = end.authorization();

                doEncodeTrailers(traceId, authorization, streamId, trailers);
            }

            private void onResponseAbort(
                AbortFW abort)
            {
                setResponseClosed();

                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                doEncodeRstStream(traceId, authorization, streamId, Http2ErrorCode.NO_ERROR);
                cleanup(traceId, authorization);
            }

            private void doResponseReset(
                long traceId,
                long authorization)
            {
                setResponseClosed();

                doReset(application, routeId, responseId, responseSeq, responseAck, responseMax, traceId, authorization);
            }

            private void doResponseResetIfNecessary(
                long traceId,
                long authorization)
            {
                if (!HttpState.replyClosed(state))
                {
                    doResponseReset(traceId, authorization);
                }
            }

            private void onResponseWindowUpdate(
                long traceId,
                long authorization,
                int size)
            {
                final long newRemoteBudget = (long) remoteBudget + size;

                if (newRemoteBudget > MAX_REMOTE_BUDGET)
                {
                    doEncodeRstStream(traceId, authorization, streamId, Http2ErrorCode.FLOW_CONTROL_ERROR);
                    cleanup(traceId, authorization);
                }
                else
                {
                    remoteBudget = (int) newRemoteBudget;

                    flushResponseWindow(traceId, authorization, 0);
                }
            }

            private void flushResponseWindow(
                long traceId,
                long authorization,
                int responseCreditMin)
            {
                if (!HttpState.replyClosed(state))
                {
                    final int remotePaddableMax = Math.min(remoteBudget, bufferPool.slotCapacity());
                    final int remotePad = http2FramePadding(remotePaddableMax, remoteSettings.maxFrameSize);
                    final int responsePad = replyPad + remotePad;
                    final int newResponseWin = remoteBudget + responsePad;
                    final int responseWin = responseMax - (int)(responseSeq - responseAck);
                    final int responseCredit = (int)(newResponseWin - responseWin);

                    if (responseCredit > 0 && responseCredit >= responseCreditMin && newResponseWin > responsePad)
                    {
                        final int responseNoAck = (int)(responseSeq - responseAck);
                        final int responseAcked = Math.min(responseNoAck, responseCredit);

                        responseAck += responseAcked;
                        assert responseAck <= responseSeq;

                        responseMax = newResponseWin + (int)(responseSeq - responseAck);
                        assert responseMax >= 0;

                        doWindow(application, routeId, responseId, responseSeq, responseAck, responseMax, traceId, authorization,
                                 budgetId, responsePad);
                    }
                }
            }

            private void setResponseClosed()
            {
                assert !HttpState.replyClosed(state);

                state = HttpState.closeReply(state);
                removeStreamIfNecessary();
            }

            private void removeStreamIfNecessary()
            {
                if (HttpState.closed(state))
                {
                    streams.remove(streamId);
                    streamsActive[streamId & 0x01]--;
                }
            }

            private void cleanup(
                long traceId,
                long authorization)
            {
                doRequestAbortIfNecessary(traceId, authorization);
                doResponseResetIfNecessary(traceId, authorization);
            }
        }
    }

    private final class Http2HeadersDecoder
    {
        private HpackContext context;
        private int headerTableSize;
        private boolean pseudoHeaders;
        private MutableBoolean expectDynamicTableSizeUpdate;

        private final Consumer<HpackHeaderFieldFW> decodeHeader;
        private final Consumer<HpackHeaderFieldFW> decodeTrailer;
        private int method;
        private int scheme;
        private int path;

        Http2ErrorCode connectionError;
        Http2ErrorCode streamError;
        Array32FW<HttpHeaderFW> httpErrorHeader;

        final Map<String, String> headers = new LinkedHashMap<>();
        long contentLength = -1;

        private Http2HeadersDecoder()
        {
            BiConsumer<DirectBuffer, DirectBuffer> nameValue =
                    ((BiConsumer<DirectBuffer, DirectBuffer>) this::collectHeaders)
                            .andThen(this::validatePseudoHeaders)
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

            // All HTTP/2 requests MUST include exactly one valid value for the
            // ":method", ":scheme", and ":path" pseudo-header fields, unless it is
            // a CONNECT request (Section 8.3).  An HTTP request that omits
            // mandatory pseudo-header fields is malformed
            if (!error() && (method != 1 || scheme != 1 || path != 1))
            {
                streamError = Http2ErrorCode.PROTOCOL_ERROR;
            }
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
            this.pseudoHeaders = true;
            this.method = 0;
            this.scheme = 0;
            this.path = 0;
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

        private void validatePseudoHeaders(
            DirectBuffer name,
            DirectBuffer value)
        {
            if (!error())
            {
                if (name.capacity() > 0 && name.getByte(0) == ':')
                {
                    // All pseudo-header fields MUST appear in the header block before regular header fields
                    if (!pseudoHeaders)
                    {
                        streamError = Http2ErrorCode.PROTOCOL_ERROR;
                        return;
                    }
                    // request pseudo-header fields MUST be one of :authority, :method, :path, :scheme,
                    int index = context.index(name);
                    switch (index)
                    {
                    case 1:             // :authority
                        break;
                    case 2:             // :method
                        method++;
                        break;
                    case 4:             // :path
                        if (value.capacity() > 0)       // :path MUST not be empty
                        {
                            path++;
                            if (!HttpUtil.isPathValid(value))
                            {
                                httpErrorHeader = HEADERS_400_BAD_REQUEST;
                            }
                        }
                        break;
                    case 6:             // :scheme
                        scheme++;
                        break;
                    default:
                        streamError = Http2ErrorCode.PROTOCOL_ERROR;
                        return;
                    }
                }
                else
                {
                    pseudoHeaders = false;
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

    private final class Http2HeadersEncoder
    {
        private HpackContext context;

        private boolean status;
        private boolean accessControlAllowOrigin;
        private boolean serverHeader;
        private final List<String> connectionHeaders = new ArrayList<>();

        private final Consumer<HttpHeaderFW> search = ((Consumer<HttpHeaderFW>) this::status)
                .andThen(this::accessControlAllowOrigin)
                .andThen(this::serverHeader)
                .andThen(this::connectionHeaders);

        void encodePromise(
            HpackContext encodeContext,
            Array32FW<HttpHeaderFW> headers,
            HpackHeaderBlockFW.Builder headerBlock)
        {
            reset(encodeContext);
            headers.forEach(h -> headerBlock.header(b -> encodeHeader(h, b)));
        }

        void encodeHeaders(
            HpackContext encodeContext,
            Array32FW<HttpHeaderFW> headers,
            HpackHeaderBlockFW.Builder headerBlock)
        {
            reset(encodeContext);

            headers.forEach(search);

            if (!status)
            {
                headerBlock.header(b -> b.indexed(8));
            }

            headers.forEach(h ->
            {
                if (includeHeader(h))
                {
                    headerBlock.header(b -> encodeHeader(h, b));
                }
            });

            if (config.accessControlAllowOrigin() && !accessControlAllowOrigin)
            {
                headerBlock.header(b -> b.literal(l -> l.type(WITHOUT_INDEXING)
                                                        .name(20)
                                                        .value(DEFAULT_ACCESS_CONTROL_ALLOW_ORIGIN)));
            }

            // add configured Server header if there is no Server header in response
            if (config.serverHeader() != null && !serverHeader)
            {
                DirectBuffer server = config.serverHeader();
                headerBlock.header(b -> b.literal(l -> l.type(WITHOUT_INDEXING).name(54).value(server)));
            }
        }

        void encodeTrailers(
            HpackContext encodeContext,
            Array32FW<HttpHeaderFW> headers,
            HpackHeaderBlockFW.Builder headerBlock)
        {
            reset(encodeContext);
            headers.forEach(h -> headerBlock.header(b -> encodeHeader(h, b)));
        }

        private void reset(HpackContext encodeContext)
        {
            context = encodeContext;
            status = false;
            accessControlAllowOrigin = false;
            serverHeader = false;
            connectionHeaders.clear();
        }

        private void status(
            HttpHeaderFW header)
        {
            status |= header.name().value().equals(context.nameBuffer(8));
        }

        private void accessControlAllowOrigin(
            HttpHeaderFW header)
        {
            accessControlAllowOrigin |= header.name().value().equals(context.nameBuffer(20));
        }

        // Checks if response has server header
        private void serverHeader(
            HttpHeaderFW header)
        {
            serverHeader |= header.name().value().equals(context.nameBuffer(54));
        }

        private void connectionHeaders(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();

            if (name.value().equals(CONNECTION))
            {
                final String16FW value = header.value();
                final String[] headerValues = value.asString().split(",");
                for (String headerValue : headerValues)
                {
                    connectionHeaders.add(headerValue.trim());
                }
            }
        }

        private boolean includeHeader(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();
            final DirectBuffer nameBuffer = name.value();

            // Excluding 8.1.2.1 pseudo-header fields
            if (nameBuffer.equals(context.nameBuffer(1)) ||      // :authority
                nameBuffer.equals(context.nameBuffer(2)) ||      // :method
                nameBuffer.equals(context.nameBuffer(4)) ||      // :path
                nameBuffer.equals(context.nameBuffer(6)))        // :scheme
            {
                return false;
            }

            // Excluding 8.1.2.2 connection-specific header fields
            if (nameBuffer.equals(context.nameBuffer(57)) ||     // transfer-encoding
                nameBuffer.equals(CONNECTION) ||                 // connection
                nameBuffer.equals(KEEP_ALIVE) ||                 // keep-alive
                nameBuffer.equals(PROXY_CONNECTION) ||           // proxy-connection
                nameBuffer.equals(UPGRADE))                      // upgrade
            {
                return false;
            }

            // Excluding header if nominated by connection header field
            if (connectionHeaders.contains(name.asString()))
            {
                return false;
            }

            return true;
        }

        private void encodeHeader(
            HttpHeaderFW header,
            HpackHeaderFieldFW.Builder builder)
        {
            final String8FW name = header.name();
            final String16FW value = header.value();

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

    private static DirectBuffer initResponse(
        int status,
        String reason)
    {
        return new UnsafeBuffer(String.format("HTTP/1.1 %d %s\r\n" +
                                              "Connection: close\r\n" +
                                              "\r\n",
                                              status, reason).getBytes(UTF_8));
    }

    private URI createTargetURI(
        String target)
    {
        URI targetURI = null;
        try
        {
            targetURI = URI.create(target);
        }
        catch (IllegalArgumentException e)
        {
            //Detect invalid chars
        }

        return targetURI;
    }
}
