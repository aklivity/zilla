/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http.internal.stream;

import static io.aklivity.zilla.runtime.cog.http.internal.util.BufferUtil.indexOfByte;
import static io.aklivity.zilla.runtime.cog.http.internal.util.BufferUtil.limitOfBytes;
import static io.aklivity.zilla.runtime.engine.cog.buffer.BufferPool.NO_SLOT;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.http.internal.HttpCog;
import io.aklivity.zilla.runtime.cog.http.internal.HttpConfiguration;
import io.aklivity.zilla.runtime.cog.http.internal.config.HttpBinding;
import io.aklivity.zilla.runtime.cog.http.internal.config.HttpRoute;
import io.aklivity.zilla.runtime.cog.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.cog.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.cog.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.cog.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.HttpEndExFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.Binding;

public final class HttpServerFactory implements HttpStreamFactory
{
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
    private static final byte[] REASON_OK_BYTES = "OK".getBytes(US_ASCII);
    private static final byte[] REASON_SWITCHING_PROTOCOLS_BYTES = "Switching Protocols".getBytes(US_ASCII);

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
    private static final String16FW STATUS_101 = new String16FW("101");
    private static final String16FW STATUS_200 = new String16FW("200");
    private static final String16FW TRANSFER_ENCODING_CHUNKED = new String16FW("chunked");

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
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

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();

    private final HttpBeginExFW beginExRO = new HttpBeginExFW();
    private final HttpEndExFW endExRO = new HttpEndExFW();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private AbortFW.Builder abortRW = new AbortFW.Builder();
    private FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();

    private final HttpBeginExFW.Builder beginExRW = new HttpBeginExFW.Builder();
    private final HttpBeginExFW.Builder newBeginExRW = new HttpBeginExFW.Builder();
    private final HttpEndExFW.Builder endExRW = new HttpEndExFW.Builder();

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

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BufferPool bufferPool;
    private final StreamFactory streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int tlsTypeId;
    private final Long2ObjectHashMap<HttpBinding> bindings;
    private final Matcher requestLine;
    private final Matcher versionPart;
    private final Matcher headerLine;
    private final Matcher connectionClose;
    private final int maximumHeadersSize;
    private final int decodeMax;
    private final int encodeMax;

    public HttpServerFactory(
        HttpConfiguration config,
        AxleContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HttpCog.NAME);
        this.tlsTypeId = context.supplyTypeId("tls");
        this.bindings = new Long2ObjectHashMap<>();
        this.requestLine = REQUEST_LINE_PATTERN.matcher("");
        this.headerLine = HEADER_LINE_PATTERN.matcher("");
        this.versionPart = VERSION_PATTERN.matcher("");
        this.connectionClose = CONNECTION_CLOSE_PATTERN.matcher("");
        this.maximumHeadersSize = bufferPool.slotCapacity();
        this.decodeMax = bufferPool.slotCapacity();
        this.encodeMax = bufferPool.slotCapacity();
    }

    @Override
    public void attach(
        Binding binding)
    {
        HttpBinding httpBinding = new HttpBinding(binding);
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

        HttpBinding binding = bindings.get(routeId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long initialId = begin.streamId();
            final long affinity = begin.affinity();

            final HttpServer server = new HttpServer(network, routeId, initialId, affinity);
            newStream = server::onNetwork;
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

            final int endOfHeaderLinesAt = endOfHeadersAt - CRLF_BYTES.length;
            int startOfLineAt = endOfStartAt;
            for (int endOfLineAt = limitOfBytes(buffer, startOfLineAt, endOfHeaderLinesAt, CRLF_BYTES);
                    endOfLineAt != -1 && error == null;
                    startOfLineAt = endOfLineAt,
                    endOfLineAt = limitOfBytes(buffer, startOfLineAt, endOfHeaderLinesAt, CRLF_BYTES))
            {
                error = decodeHeaderLine(server, buffer, offset, startOfLineAt, endOfLineAt,
                                         httpBeginEx, hasAuthority);
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

                HttpBinding binding = bindings.get(server.routeId);
                HttpRoute route = binding.resolve(authorization, headers::get);
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
        MutableBoolean hasAuthority)
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
                else
                {
                    // TODO: wait for 101 first
                    server.decoder = decodeUpgraded;
                    httpBeginEx.headersItem(h -> h.name(HEADER_UPGRADE).value(value));
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

    private enum HttpState
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

        private int replyPad;
        private boolean replyCloseOnFlush;

        private int decodeSlot;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int encodeSlot;
        private int encodeSlotOffset;

        private HttpServerDecoder decoder;
        private String16FW decodeScheme;
        private int decodableChunkSize;
        private int decodableContentLength;

        private HttpExchange exchange;
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
            long affinity)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.affinity = affinity;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.decoder = decodeEmptyLines;
            this.decodeSlot = NO_SLOT;
            this.encodeSlot = NO_SLOT;
        }

        private int replyPendingAck()
        {
            return (int)(replySeq - replyAck);
        }

        private int replyWindow()
        {
            return replyMax - replyPendingAck();
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
            }
        }

        private void onNetworkBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final ExtensionFW extension = begin.extension().get(extensionRO::tryWrap);

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            decodeScheme = extension != null && extension.typeId() == tlsTypeId ? SCHEME_HTTPS : SCHEME_HTTP;
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            doNetworkWindow(traceId, authorization, 0, 0, bufferPool.slotCapacity());
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
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

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

            if (exchange != null && exchange.responseState == HttpState.OPEN)
            {
                exchange.doResponseWindow(traceId);
            }
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
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            cleanupEncodeSlotIfNecessary();
            doAbort(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlotIfNecessary();
            final int initialMax = exchange != null ? decodeMax : 0;
            doReset(network, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
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
            if (initialAckMax > initialAck)
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
            doNetworkData(traceId, authorization, 0L, error.capacity() + replyPad, error, 0, error.capacity());
            doNetworkEnd(traceId, authorization);

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

            if (exchange.requestState == HttpState.CLOSED &&
                exchange.responseState == HttpState.CLOSED)
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

            byte[] reason = STATUS_101.equals(status) ? REASON_SWITCHING_PROTOCOLS_BYTES : REASON_OK_BYTES;
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

            if (exchange.requestState == HttpState.CLOSED &&
                exchange.responseState == HttpState.CLOSED)
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

            private HttpState requestState;
            private HttpState responseState;
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
                this.requestState = HttpState.PENDING;
                this.responseState = HttpState.PENDING;
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
                    requestState = HttpState.CLOSED;
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
                requestState = HttpState.CLOSED;
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
                if (requestState != HttpState.CLOSED)
                {
                    doRequestAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void onNetworkAbort(
                long traceId,
                long authorization)
            {
                if (requestState != HttpState.CLOSED)
                {
                    doRequestAbort(traceId, authorization, EMPTY_OCTETS);
                }
            }

            private void onNetworkReset(
                long traceId,
                long authorization)
            {
                if (responseState == HttpState.OPEN)
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
                requestState = HttpState.CLOSED;
                doNetworkReset(traceId, authorization);
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

                if (requestState == HttpState.PENDING)
                {
                    requestState = HttpState.OPEN;
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

                if (decodeSlot == NO_SLOT && requestState == HttpState.CLOSED)
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

                responseState = HttpState.OPEN;
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

                responseState = HttpState.CLOSED;
                doEncodeTrailers(this, traceId, authorization, budgetId, trailers);
            }

            private void onResponseAbort(
                AbortFW abort)
            {
                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                responseState = HttpState.CLOSED;
                doNetworkAbort(traceId, authorization);
            }

            private void doResponseReset(
                long traceId,
                long authorization)
            {
                responseState = HttpState.CLOSED;
                doReset(application, routeId, responseId, responseSeq, responseAck, responseMax, traceId, authorization);
            }

            private void doResponseWindow(
                long traceId)
            {
                long responseAckMax = Math.max(responseSeq - replyPendingAck() - encodeSlotOffset, responseAck);
                int responseNoAckMin = (int)(responseSeq - responseAckMax);
                int minResponseMax = Math.min(responseRemaining - responseNoAckMin + replyPad, replyMax);

                if (responseAckMax > responseAck ||
                    (minResponseMax > responseMax && encodeSlotOffset == 0))
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
