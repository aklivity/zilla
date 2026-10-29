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
package io.aklivity.zilla.runtime.binding.agui.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;
import java.util.Optional;
import java.util.function.LongUnaryOperator;

import jakarta.json.JsonException;
import jakarta.json.stream.JsonParser;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.agui.internal.AguiConfiguration;
import io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiContentMode;
import io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiEventEncoder;
import io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiJsonEscape;
import io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiMessageEncoding;
import io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiSequenceException;
import io.aklivity.zilla.runtime.binding.agui.internal.config.AguiAuthorizationResult;
import io.aklivity.zilla.runtime.binding.agui.internal.config.AguiBindingConfig;
import io.aklivity.zilla.runtime.binding.agui.internal.config.AguiRouteConfig;
import io.aklivity.zilla.runtime.binding.agui.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.agui.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiAbortExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiResetExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class AguiServerFactory implements AguiStreamFactory
{
    private static final Runnable NOOP = () ->
    {
    };

    private static final String HTTP_TYPE_NAME = "http";
    private static final String AGUI_TYPE_NAME = "agui";

    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final int EVENT_RECORD_HEADER = 3 * Integer.BYTES;
    private static final int ENVELOPE_MAX = 512;
    private static final int ENVELOPE_PIECES_PER_FRAME = 8;
    private static final int UTF8_CARRY_MAX = 3;

    private static final String HTTP_METHOD_POST = "POST";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_ACCEPT = "accept";
    private static final String HTTP_HEADER_ALLOW = "allow";
    private static final String HTTP_HEADER_WWW_AUTHENTICATE = "www-authenticate";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String ACCEPT_ANY = "*/*";
    private static final String ACCEPT_ANY_TEXT = "text/*";
    private static final String STATUS_200 = "200";
    private static final String STATUS_400 = "400";
    private static final String STATUS_401 = "401";
    private static final String STATUS_405 = "405";
    private static final String STATUS_406 = "406";
    private static final String STATUS_415 = "415";
    private static final String STATUS_500 = "500";
    private static final String STATUS_502 = "502";

    private static final String ERROR_MALFORMED_JSON = "Malformed JSON";
    private static final String ERROR_REQUEST_TOO_LARGE = "Request too large";
    private static final String ERROR_THREAD_ID_MISSING = "Missing required property: threadId";
    private static final String ERROR_RUN_ID_MISSING = "Missing required property: runId";
    private static final String ERROR_UNAUTHORIZED = "Unauthorized";
    private static final String ERROR_METHOD_NOT_ALLOWED = "Method Not Allowed";
    private static final String ERROR_NOT_ACCEPTABLE = "Not Acceptable";
    private static final String ERROR_UNSUPPORTED_MEDIA_TYPE = "Unsupported Media Type";
    private static final String ERROR_BAD_GATEWAY = "Bad Gateway";

    private static final String CODE_INVALID_MESSAGE = "invalid_message";
    private static final String CODE_INVALID_SEQUENCE = "invalid_sequence";
    private static final String CODE_AGENT_ABORTED = "agent_aborted";
    private static final String MESSAGE_AGENT_ABORTED = "Agent aborted";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final AguiBeginExFW aguiBeginExRO = new AguiBeginExFW();
    private final AguiDataExFW aguiDataExRO = new AguiDataExFW();
    private final AguiEndExFW aguiEndExRO = new AguiEndExFW();
    private final AguiAbortExFW aguiAbortExRO = new AguiAbortExFW();
    private final AguiResetExFW aguiResetExRO = new AguiResetExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final AguiBeginExFW.Builder aguiBeginExRW = new AguiBeginExFW.Builder();

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx codecBuffer;
    private final MutableDirectBufferEx eventBuffer;
    private final MutableDirectBufferEx carryBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int aguiTypeId;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final int decodeMax;
    private final int encodeMax;
    private final int appReplyMax;
    private final int appReplyPadding;

    private int eventReserved;

    private final AguiServerDecoder decodeStart = this::decodeStart;
    private final AguiServerDecoder decodeNext = this::decodeNext;
    private final AguiServerDecoder decodeId = this::decodeId;
    private final AguiServerDecoder decodeSkip = this::decodeSkip;
    private final AguiServerDecoder decodeBody = this::decodeBody;
    private final AguiServerDecoder decodeIgnore = this::decodeIgnore;

    private final Long2ObjectHashMap<AguiBindingConfig> bindings;

    public AguiServerFactory(
        AguiConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.aguiTypeId = context.supplyTypeId(AGUI_TYPE_NAME);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.appReplyMax = encodeMax / AguiJsonEscape.growth();
        this.appReplyPadding = (ENVELOPE_PIECES_PER_FRAME * ENVELOPE_MAX + AguiJsonEscape.growth() - 1) / AguiJsonEscape.growth();
        this.eventBuffer = new UnsafeBufferEx(new byte[encodeMax + EVENT_RECORD_HEADER + 2 * ENVELOPE_MAX]);
        this.carryBuffer = new UnsafeBufferEx(new byte[appReplyMax + UTF8_CARRY_MAX]);
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new AguiBindingConfig(binding, context));
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

        final AguiBindingConfig binding = bindings.get(routedId);
        final AguiRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        if (route != null)
        {
            final long traceId = begin.traceId();
            final HttpBeginExFW httpBeginEx = begin.extension().get(httpBeginExRO::wrap);

            final String method = header(httpBeginEx, HTTP_HEADER_METHOD);
            final String contentType = header(httpBeginEx, HTTP_HEADER_CONTENT_TYPE);
            final String accept = header(httpBeginEx, HTTP_HEADER_ACCEPT);

            if (!HTTP_METHOD_POST.equals(method))
            {
                newStream = new AguiRejectHandler(sender, STATUS_405, ERROR_METHOD_NOT_ALLOWED,
                    HTTP_HEADER_ALLOW, HTTP_METHOD_POST)::onNetMessage;
            }
            else if (!jsonContent(contentType))
            {
                newStream = new AguiRejectHandler(sender, STATUS_415, ERROR_UNSUPPORTED_MEDIA_TYPE, null, null)::onNetMessage;
            }
            else if (!eventStreamAcceptable(accept))
            {
                newStream = new AguiRejectHandler(sender, STATUS_406, ERROR_NOT_ACCEPTABLE, null, null)::onNetMessage;
            }
            else
            {
                final AguiAuthorizationResult authResult =
                    binding.authorize(traceId, routedId, initialId, authorization, httpBeginEx);

                if (authResult.error() != null)
                {
                    newStream = new AguiRejectHandler(sender, STATUS_401, ERROR_UNAUTHORIZED,
                        HTTP_HEADER_WWW_AUTHENTICATE, binding.challenge(authResult.error()))::onNetMessage;
                }
                else
                {
                    newStream = new AguiServer(sender, originId, routedId, initialId, authResult.authorization(),
                        authResult.deauthorize(), route.id)::onNetMessage;
                }
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

    private static boolean jsonContent(
        String contentType)
    {
        return contentType != null && contentType.regionMatches(true, 0, CONTENT_TYPE_JSON, 0, CONTENT_TYPE_JSON.length());
    }

    private static boolean eventStreamAcceptable(
        String accept)
    {
        return accept == null ||
            accept.contains(CONTENT_TYPE_EVENT_STREAM) ||
            accept.contains(ACCEPT_ANY) ||
            accept.contains(ACCEPT_ANY_TEXT);
    }

    @FunctionalInterface
    private interface AguiServerDecoder
    {
        int decode(
            AguiServer server,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }

    private int decodeStart(
        AguiServer server,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (server.decodableJson == null)
        {
            server.decodableJson = JsonEx.createParser(Map.of());
            server.decodableJson.wrap(buffer, progress, limit);
            server.decodedParserProgress = offset - progress;
        }

        final JsonParser parser = server.decodableJson;

        decode:
        if (parser.hasNext())
        {
            if (parser.next() != JsonParser.Event.START_OBJECT)
            {
                server.onDecodeMalformed(traceId, authorization);
                break decode;
            }

            server.decoder = decodeNext;
        }

        return offset;
    }

    private int decodeNext(
        AguiServer server,
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
        while (server.decoder == decodeNext && parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case KEY_NAME:
                final String name = parser.getString();
                if (server.identifier(name))
                {
                    server.decodedName = name;
                    server.decoder = decodeId;
                }
                else if (server.identified())
                {
                    server.onDecodeBegin(traceId, authorization);
                    break decode;
                }
                else
                {
                    server.decodedSkipDepth = 0;
                    server.decoder = decodeSkip;
                }
                break;
            case END_OBJECT:
                if (server.identified())
                {
                    server.onDecodeBegin(traceId, authorization);
                }
                else
                {
                    server.onDecodeMissing(traceId, authorization);
                }
                break decode;
            default:
                server.onDecodeMalformed(traceId, authorization);
                break decode;
            }
        }

        return offset;
    }

    private int decodeId(
        AguiServer server,
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
                server.decodedIdentifier(parser.getString());
                break;
            case VALUE_NULL:
                server.decodedIdentifier(null);
                break;
            default:
                server.onDecodeMalformed(traceId, authorization);
                break decode;
            }

            server.decoder = decodeNext;
        }

        return offset;
    }

    private int decodeSkip(
        AguiServer server,
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

        boolean skipped = false;
        while (!skipped && parser.hasNext())
        {
            switch (parser.next())
            {
            case START_OBJECT:
            case START_ARRAY:
                server.decodedSkipDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                server.decodedSkipDepth--;
                skipped = server.decodedSkipDepth == 0;
                break;
            default:
                skipped = server.decodedSkipDepth == 0;
                break;
            }
        }

        if (skipped)
        {
            server.decoder = decodeNext;
        }

        return offset;
    }

    private int decodeBody(
        AguiServer server,
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
        AguiServer server,
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

    private final class AguiServer
    {
        private final MessageConsumer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long resolvedId;
        private Runnable deauthorize;

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

        private AguiServerDecoder decoder;

        private JsonParserEx decodableJson;
        private String decodedName;
        private String decodedThreadId;
        private String decodedRunId;
        private String decodedParentRunId;
        private int decodedSkipDepth;

        private AguiStream stream;

        private AguiServer(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            Runnable deauthorize,
            long resolvedId)
        {
            this.net = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.deauthorize = deauthorize;
            this.resolvedId = resolvedId;
            this.decoder = decodeStart;
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

            state = AguiState.openedInitial(state);

            doNetWindow(traceId, authorization, 0, 0);
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

            state = AguiState.closingInitial(state);

            doDecodeEnd(traceId, authorization);
        }

        private void doDecodeEnd(
            long traceId,
            long authorization)
        {
            final boolean forwarding = decodeSlot != NO_SLOT && decoder == decodeBody;

            if (AguiState.initialClosing(state) && !AguiState.initialClosed(state) && !forwarding)
            {
                if (decoder != decodeBody && decoder != decodeIgnore)
                {
                    onDecodeMalformed(traceId, authorization);
                }

                cleanupDecodeSlot();
                state = AguiState.closedInitial(state);

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

            cleanupDecodeSlot();

            if (stream != null)
            {
                stream.doAppAbort(traceId, authorization);
                stream.doAppReset(traceId, authorization);
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

                encodeNet(encodeSlotTraceId, authorization, buffer, 0, limit);
            }

            if (stream != null)
            {
                stream.flushAppWindow(traceId, authorization);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            if (stream != null)
            {
                stream.doAppAbort(traceId, authorization);
                stream.doAppReset(traceId, authorization);
            }

            cleanupEncodeSlot();
        }

        private boolean identifier(
            String name)
        {
            return "threadId".equals(name) || "runId".equals(name) || "parentRunId".equals(name);
        }

        private boolean identified()
        {
            return decodedThreadId != null && decodedRunId != null;
        }

        private void decodedIdentifier(
            String value)
        {
            switch (decodedName)
            {
            case "threadId":
                decodedThreadId = value;
                break;
            case "runId":
                decodedRunId = value;
                break;
            default:
                decodedParentRunId = value;
                break;
            }
        }

        private void onDecodeMalformed(
            long traceId,
            long authorization)
        {
            onDecodeRejected(traceId, authorization, ERROR_MALFORMED_JSON);
        }

        private void onDecodeMissing(
            long traceId,
            long authorization)
        {
            onDecodeRejected(traceId, authorization, decodedThreadId == null ? ERROR_THREAD_ID_MISSING : ERROR_RUN_ID_MISSING);
        }

        private void onDecodeRejected(
            long traceId,
            long authorization,
            String message)
        {
            decodableJson = null;
            doEncodeResponseError(traceId, authorization, STATUS_400, message);
        }

        private void onDecodeBegin(
            long traceId,
            long authorization)
        {
            decodableJson = null;
            decoder = decodeBody;

            stream = new AguiStream(this);
            stream.doAppBegin(traceId, authorization, beginEx());
        }

        private AguiBeginExFW beginEx()
        {
            return aguiBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(aguiTypeId)
                .threadId(decodedThreadId)
                .runId(decodedRunId)
                .parentRunId(decodedParentRunId)
                .build();
        }

        private void doEncodeResponseError(
            long traceId,
            long authorization,
            String status,
            String message)
        {
            decoder = decodeIgnore;
            cleanupDecodeSlot();

            if (!AguiState.replyOpening(state))
            {
                doNetBegin(traceId, authorization, httpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                    .build());

                final byte[] body = errorBody(message, null);
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

        private void doEncodeResponseRejected(
            long traceId,
            long authorization,
            String status,
            String message,
            String code)
        {
            decoder = decodeIgnore;
            cleanupDecodeSlot();

            doNetReset(traceId, authorization);

            if (!AguiState.replyOpening(state))
            {
                doNetBegin(traceId, authorization, httpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                    .build());

                final byte[] body = errorBody(message, code);
                doNetData(traceId, authorization, new UnsafeBufferEx(body), 0, body.length);
                doEncodeResponseEnd(traceId, authorization);
            }
            else
            {
                doNetAbort(traceId, authorization);
            }
        }

        private void doEncodeResponseBegin(
            long traceId,
            long authorization)
        {
            if (!AguiState.replyOpening(state))
            {
                state = AguiState.framingReply(state);

                doNetBegin(traceId, authorization, httpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(STATUS_200))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_EVENT_STREAM))
                    .build());
            }
        }

        private void doEncodeResponseEnd(
            long traceId,
            long authorization)
        {
            state = AguiState.closingReply(state);

            if (encodeSlot == NO_SLOT)
            {
                doNetEnd(traceId, authorization);
            }
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
                state = AguiState.abortingReply(state);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            if (!AguiState.replyOpening(state))
            {
                doBegin(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization, 0,
                    extension);

                state = AguiState.openingReply(state);
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

                if (encodeSlotOffset + limit - offset > encodeMax)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                    encodeSlotOffset += limit - offset;
                    encodeSlotTraceId = traceId;

                    encodeNet(traceId, authorization, encodeBuffer, 0, encodeSlotOffset);
                }
            }
            else
            {
                encodeNet(traceId, authorization, buffer, offset, limit);
            }
        }

        private void doNetEvents(
            long traceId,
            long authorization,
            byte[][] events)
        {
            for (byte[] event : events)
            {
                doNetEvent(traceId, authorization, COMPLETE, event, null, 0, 0, false, null);
            }
        }

        private void doNetEvent(
            long traceId,
            long authorization,
            int flags,
            byte[] head,
            DirectBufferEx payload,
            int payloadOffset,
            int payloadLength,
            boolean escape,
            byte[] tail)
        {
            int progress = EVENT_RECORD_HEADER;

            if (head != null)
            {
                eventBuffer.putBytes(progress, head);
                progress += head.length;
            }

            if (payloadLength > 0)
            {
                progress = escape
                    ? AguiJsonEscape.escape(payload, payloadOffset, payloadOffset + payloadLength, eventBuffer, progress)
                    : copy(payload, payloadOffset, payloadLength, progress);
            }

            if (tail != null)
            {
                eventBuffer.putBytes(progress, tail);
                progress += tail.length;
            }

            eventBuffer.putInt(0, flags);
            eventBuffer.putInt(Integer.BYTES, 0);
            eventBuffer.putInt(2 * Integer.BYTES, progress - EVENT_RECORD_HEADER);

            doNetData(traceId, authorization, eventBuffer, 0, progress);
        }

        private int copy(
            DirectBufferEx payload,
            int payloadOffset,
            int payloadLength,
            int progress)
        {
            eventBuffer.putBytes(progress, payload, payloadOffset, payloadLength);

            return progress + payloadLength;
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!AguiState.replyClosed(state))
            {
                state = AguiState.closedReply(state);
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
            if (!AguiState.replyClosed(state))
            {
                state = AguiState.closedReply(state);
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
            state = AguiState.openedInitial(state);

            doWindow(net, originId, routedId, initialId,
                initialSeq, initialAck, decodeMax - decodeSlotReserved, traceId, authorization, budgetId, padding,
                FRAMING_CAPABILITIES_MASK);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!AguiState.initialClosed(state))
            {
                state = AguiState.closedInitial(state);
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
            if (initialAckMax > initialAck || !AguiState.initialOpened(state))
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
            AguiServerDecoder previous = null;
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
                onDecodeMalformed(traceId, authorization);
                progress = limit;
            }

            if (decoder != decodeBody && decoder != decodeIgnore && limit - offset >= decodeMax)
            {
                onDecodeRejected(traceId, authorization, ERROR_REQUEST_TOO_LARGE);
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

            if (AguiState.replyFraming(state))
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
                    encodeSlotTraceId = traceId;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (AguiState.replyAborting(state))
                {
                    doNetAbort(traceId, authorization);
                }
                else if (AguiState.replyClosing(state))
                {
                    doNetEnd(traceId, authorization);
                }
            }

            if (stream != null)
            {
                stream.flushAppWindow(traceId, authorization);
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
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);

            if (stream != null)
            {
                stream.doAppAbort(traceId, authorization);
                stream.doAppReset(traceId, authorization);
            }
        }
    }

    private final class AguiStream
    {
        private final AguiServer server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer app;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBud;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private AguiEventEncoder encoder;
        private AguiMessageEncoding encoding;
        private int messageBytes;
        private int carryLength;

        private AguiStream(
            AguiServer server)
        {
            this.server = server;
            this.originId = server.routedId;
            this.routedId = server.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(server.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            Flyweight extension)
        {
            app = newStream(this::onAppMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, 0,
                extension);

            state = AguiState.openingInitial(state);

            if (app == null)
            {
                server.doEncodeResponseRejected(traceId, authorization, STATUS_502, ERROR_BAD_GATEWAY, null);
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

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!AguiState.initialClosed(state) &&
                AguiState.initialClosed(server.state))
            {
                state = AguiState.closedInitial(state);
                doEnd(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (AguiState.initialOpening(state) &&
                !AguiState.initialClosed(state))
            {
                state = AguiState.closedInitial(state);
                doAbort(app, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = AguiState.openedReply(state);
            doWindow(app, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!AguiState.replyClosed(state))
            {
                state = AguiState.closedReply(state);
                doReset(app, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void flushAppWindow(
            long traceId,
            long authorization)
        {
            if (app != null && !AguiState.replyClosed(state))
            {
                final long replyAckMax = Math.max(replySeq - server.encodeSlotOffset, replyAck);
                if (replyAckMax > replyAck || appReplyMax > replyMax)
                {
                    replyAck = replyAckMax;
                    assert replyAck <= replySeq;

                    replyMax = appReplyMax;

                    doAppWindow(traceId, authorization, 0L, appReplyPadding);
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
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;

            assert replyAck <= replySeq;

            final AguiBeginExFW beginEx = extension.sizeof() > 0 ? extension.get(aguiBeginExRO::tryWrap) : null;

            encoder = beginEx != null
                ? new AguiEventEncoder(beginEx.threadId().asString(), beginEx.runId().asString(),
                    beginEx.parentRunId().asString())
                : new AguiEventEncoder(server.decodedThreadId, server.decodedRunId, server.decodedParentRunId);

            server.doEncodeResponseBegin(traceId, authorization);
            server.doNetEvents(traceId, authorization, encoder.runStarted());

            flushAppWindow(traceId, authorization);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                cleanupApp(traceId, authorization);
            }
            else if (!AguiState.replyClosed(state))
            {
                if (payload != null)
                {
                    onReplyData(traceId, authorization, flags, extension, payload.buffer(), payload.offset(), payload.limit());
                }
                else
                {
                    onReplyData(traceId, authorization, flags, extension, emptyRO.buffer(), 0, 0);
                }

                flushAppWindow(traceId, authorization);
            }
        }

        private void onReplyData(
            long traceId,
            long authorization,
            int flags,
            OctetsFW extension,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final boolean init = (flags & INIT) != 0;
            final boolean fin = (flags & FIN) != 0;

            if (init)
            {
                carryLength = 0;
                messageBytes = 0;
                onReplyInit(traceId, authorization, extension, fin && limit == offset);
            }

            if (encoding != null)
            {
                onReplyContent(traceId, authorization, init, fin, buffer, offset, limit);
            }
            else if (!init && !AguiState.replyClosed(state))
            {
                failRun(traceId, authorization, "Message without init frame", CODE_INVALID_MESSAGE);
            }
        }

        private void onReplyInit(
            long traceId,
            long authorization,
            OctetsFW extension,
            boolean payloadAbsent)
        {
            final AguiDataExFW dataEx = extension != null && extension.sizeof() > 0
                ? extension.get(aguiDataExRO::tryWrap)
                : null;

            encoding = null;

            if (dataEx == null || dataEx.typeId() != aguiTypeId)
            {
                failRun(traceId, authorization, "Message without extension", CODE_INVALID_MESSAGE);
            }
            else
            {
                try
                {
                    final AguiMessageEncoding next = encoder.message(dataEx, payloadAbsent);

                    if (next.within(ENVELOPE_MAX - EVENT_RECORD_HEADER))
                    {
                        encoding = next;
                        server.doNetEvents(traceId, authorization, next.open);
                    }
                    else
                    {
                        failRun(traceId, authorization, "Message envelope too large", CODE_INVALID_MESSAGE);
                    }
                }
                catch (AguiSequenceException ex)
                {
                    failRun(traceId, authorization, ex.getMessage(), CODE_INVALID_SEQUENCE);
                }
            }
        }

        private void onReplyContent(
            long traceId,
            long authorization,
            boolean init,
            boolean fin,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final AguiMessageEncoding current = encoding;

            switch (current.mode)
            {
            case DELTA_EVENTS:
                onReplyDelta(traceId, authorization, current, buffer, offset, limit);
                break;
            case STRING_MEMBER:
                onReplyString(traceId, authorization, current, init, fin, buffer, offset, limit);
                break;
            case JSON_MEMBER:
                onReplyJson(traceId, authorization, current, init, fin, buffer, offset, limit);
                break;
            default:
                break;
            }

            if (fin && encoding == current)
            {
                if (carryLength > 0)
                {
                    failRun(traceId, authorization, "Text ends inside a UTF-8 sequence", CODE_INVALID_MESSAGE);
                }
                else
                {
                    if (current.mode == AguiContentMode.DELTA_EVENTS && messageBytes > 0)
                    {
                        server.doNetEvents(traceId, authorization, current.contentClose);
                    }

                    server.doNetEvents(traceId, authorization, current.close);
                    encoding = null;
                }
            }
        }

        private void onReplyDelta(
            long traceId,
            long authorization,
            AguiMessageEncoding current,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final DirectBufferEx text = carried(buffer, offset, limit);
            final int textLimit = carryLength > 0 ? carryLength + limit - offset : limit - offset;
            final int textOffset = carryLength > 0 ? 0 : offset;
            final int complete = AguiJsonEscape.completeLength(text, textOffset, textOffset + textLimit);

            if (complete == AguiJsonEscape.MALFORMED)
            {
                failRun(traceId, authorization, "Text is not valid UTF-8", CODE_INVALID_MESSAGE);
            }
            else
            {
                if (complete > 0)
                {
                    if (messageBytes == 0)
                    {
                        server.doNetEvents(traceId, authorization, current.contentOpen);
                    }

                    server.doNetEvent(traceId, authorization, COMPLETE, current.prefix, text, textOffset, complete, true,
                        current.suffix);
                    messageBytes += complete;
                }

                remember(text, textOffset + complete, textOffset + textLimit);
            }
        }

        private void onReplyString(
            long traceId,
            long authorization,
            AguiMessageEncoding current,
            boolean init,
            boolean fin,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final DirectBufferEx text = carried(buffer, offset, limit);
            final int textLimit = carryLength > 0 ? carryLength + limit - offset : limit - offset;
            final int textOffset = carryLength > 0 ? 0 : offset;
            final int complete = AguiJsonEscape.completeLength(text, textOffset, textOffset + textLimit);

            if (complete == AguiJsonEscape.MALFORMED)
            {
                failRun(traceId, authorization, "Text is not valid UTF-8", CODE_INVALID_MESSAGE);
            }
            else
            {
                final int flags = (init ? INIT : 0) | (fin ? FIN : 0);

                if (flags != 0 || complete > 0)
                {
                    server.doNetEvent(traceId, authorization, flags, init ? current.prefix : null, text, textOffset, complete,
                        true, fin ? current.suffix : null);
                }

                messageBytes += complete;
                remember(text, textOffset + complete, textOffset + textLimit);
            }
        }

        private void onReplyJson(
            long traceId,
            long authorization,
            AguiMessageEncoding current,
            boolean init,
            boolean fin,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int flags = (init ? INIT : 0) | (fin ? FIN : 0);

            if (flags != 0 || limit > offset)
            {
                server.doNetEvent(traceId, authorization, flags, init ? current.prefix : null, buffer, offset, limit - offset,
                    false, fin ? current.suffix : null);
            }

            messageBytes += limit - offset;
        }

        private DirectBufferEx carried(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            DirectBufferEx text = buffer;

            if (carryLength > 0)
            {
                carryBuffer.putBytes(carryLength, buffer, offset, limit - offset);
                text = carryBuffer;
            }

            return text;
        }

        private void remember(
            DirectBufferEx text,
            int offset,
            int limit)
        {
            final int length = limit - offset;

            if (length > 0)
            {
                final byte[] pending = new byte[length];
                text.getBytes(offset, pending);
                carryBuffer.putBytes(0, pending);
            }

            carryLength = length;
        }

        private void failRun(
            long traceId,
            long authorization,
            String message,
            String code)
        {
            encoding = null;

            server.doNetEvents(traceId, authorization, encoder.runError(message, code));
            server.doEncodeResponseEnd(traceId, authorization);

            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);
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
            final OctetsFW extension = end.extension();

            final boolean replying = !AguiState.replyClosed(state);

            state = AguiState.closedReply(state);

            if (replying && encoder != null)
            {
                final AguiEndExFW endEx = extension.sizeof() > 0 ? extension.get(aguiEndExRO::tryWrap) : null;

                try
                {
                    server.doNetEvents(traceId, authorization, encoder.runFinished(endEx));
                }
                catch (AguiSequenceException ex)
                {
                    server.doNetEvents(traceId, authorization, encoder.runError(ex.getMessage(), CODE_INVALID_SEQUENCE));
                }

                server.doEncodeResponseEnd(traceId, authorization);
            }
            else if (replying)
            {
                server.doEncodeResponseRejected(traceId, authorization, STATUS_502, ERROR_BAD_GATEWAY, null);
            }

            server.doDecodeEnd(traceId, authorization);
            doAppEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            final boolean replying = !AguiState.replyClosed(state);

            state = AguiState.closedReply(state);

            if (replying && encoder != null)
            {
                final AguiAbortExFW abortEx = extension.sizeof() > 0 ? extension.get(aguiAbortExRO::tryWrap) : null;
                final String message = abortEx != null && abortEx.error().message().asString() != null
                    ? abortEx.error().message().asString()
                    : MESSAGE_AGENT_ABORTED;
                final String code = abortEx != null && abortEx.error().code().asString() != null
                    ? abortEx.error().code().asString()
                    : CODE_AGENT_ABORTED;

                server.doNetEvents(traceId, authorization, encoder.runError(message, code));
                server.doEncodeResponseEnd(traceId, authorization);
            }
            else if (replying)
            {
                server.doEncodeResponseRejected(traceId, authorization, STATUS_502, ERROR_BAD_GATEWAY, null);
            }

            doAppAbort(traceId, authorization);
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

            state = AguiState.openedInitial(state);

            server.decodeNet(traceId, authorization, budgetId);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();

            final AguiResetExFW resetEx = extension.sizeof() > 0 ? extension.get(aguiResetExRO::tryWrap) : null;

            if (AguiState.replyOpening(server.state))
            {
                server.doNetAbort(traceId, authorization);
            }
            else if (resetEx != null)
            {
                final int status = resetEx.error().status();
                final String message = resetEx.error().message().asString();

                server.doEncodeResponseRejected(traceId, authorization,
                    status >= 0 ? Integer.toString(status) : STATUS_500,
                    message != null ? message : ERROR_BAD_GATEWAY,
                    resetEx.error().code().asString());
            }
            else
            {
                server.doEncodeResponseRejected(traceId, authorization, STATUS_502, ERROR_BAD_GATEWAY, null);
            }

            state = AguiState.closedReply(state);
            doAppAbort(traceId, authorization);
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);

            server.cleanupNet(traceId, authorization);
        }
    }

    private final class AguiRejectHandler
    {
        private final MessageConsumer net;
        private final String status;
        private final byte[] body;
        private final String headerName;
        private final String headerValue;

        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;

        private long initialSeq;
        private long initialAck;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;

        private int state;

        private AguiRejectHandler(
            MessageConsumer sender,
            String status,
            String message,
            String headerName,
            String headerValue)
        {
            this.net = sender;
            this.status = status;
            this.body = errorBody(message, null);
            this.headerName = headerName;
            this.headerValue = headerValue;
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
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
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

            this.originId = begin.originId();
            this.routedId = begin.routedId();
            this.initialId = begin.streamId();
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.initialSeq = begin.sequence();
            this.initialAck = begin.acknowledge();

            doWindow(net, originId, routedId, initialId, initialSeq, initialAck, decodeMax,
                traceId, authorization, 0L, 0);

            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_STATUS).value(status))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON));

            if (headerName != null)
            {
                builder.headersItem(h -> h.name(headerName).value(headerValue));
            }

            doBegin(net, originId, routedId, replyId, 0L, 0L, 0, traceId, authorization, 0L, builder.build());
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

        private void onNetWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyBud = window.budgetId();
            replyPad = window.padding();

            if (!AguiState.replyClosed(state) && replyMax - (replySeq - replyAck) >= body.length + replyPad)
            {
                final long traceId = window.traceId();
                final long authorization = window.authorization();
                final int reserved = body.length + replyPad;

                state = AguiState.closedReply(state);

                doData(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    COMPLETE, replyBud, reserved, new UnsafeBufferEx(body), 0, body.length);
                replySeq += reserved;

                doEnd(net, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
            }
        }
    }

    private static byte[] errorBody(
        String message,
        String code)
    {
        final String body = code != null
            ? "{\"error\":\"" + AguiJsonEscape.escape(message) + "\",\"code\":\"" + AguiJsonEscape.escape(code) + "\"}"
            : "{\"error\":\"" + AguiJsonEscape.escape(message) + "\"}";

        return body.getBytes(UTF_8);
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
