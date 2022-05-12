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
package io.aklivity.zilla.runtime.binding.sse.internal.stream;

import static io.aklivity.zilla.runtime.binding.sse.internal.util.Flags.FIN;
import static io.aklivity.zilla.runtime.binding.sse.internal.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.sse.internal.SseBinding;
import io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration;
import io.aklivity.zilla.runtime.binding.sse.internal.config.SseBindingConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.config.SseRouteConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.sse.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.codec.SseEventFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.HttpChallengeExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.SseDataExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.SseEndExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.binding.sse.internal.util.Flags;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class SseServerFactory implements SseStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";

    private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
    private static final String8FW HEADER_NAME_PATH = new String8FW(":path");
    private static final String8FW HEADER_NAME_STATUS = new String8FW(":status");
    private static final String8FW HEADER_NAME_ACCEPT = new String8FW("accept");
    private static final String8FW HEADER_NAME_LAST_EVENT_ID = new String8FW("last-event-id");
    private static final String8FW HEADER_NAME_CONTENT_LENGTH = new String8FW("content-length");

    private static final String16FW HEADER_VALUE_STATUS_405 = new String16FW("405");
    private static final String16FW HEADER_VALUE_STATUS_400 = new String16FW("400");
    private static final String16FW HEADER_VALUE_METHOD_GET = new String16FW("GET");
    private static final String16FW HEADER_VALUE_CONTENT_LENGTH_0 = new String16FW("0");

    private static final Pattern QUERY_PARAMS_PATTERN = Pattern.compile("(?<path>[^?]*)(?<query>[\\?].*)");
    private static final Pattern LAST_EVENT_ID_PATTERN = Pattern.compile("(\\?|&)lastEventId=(?<lastEventId>[^&]*)(&|$)");

    private static final String8FW LAST_EVENT_ID_NULL = new String8FW(null);

    private static final byte ASCII_COLON = 0x3a;
    private static final String METHOD_PROPERTY = "method";
    private static final String HEADERS_PROPERTY = "headers";

    private static final int MAXIMUM_LAST_EVENT_ID_SIZE = 254;

    public static final int MAXIMUM_HEADER_SIZE =
            5 +         // data:
            3 +         // id:
            255 +       // id string
            6 +         // event:
            16 +        // event string
            3;          // \n for data:, id:, event

    private static final int CHALLENGE_CAPABILITIES_MASK = 1 << Capability.CHALLENGE.ordinal();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ChallengeFW challengeRO = new ChallengeFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final SseBeginExFW.Builder sseBeginExRW = new SseBeginExFW.Builder();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();

    private final HttpChallengeExFW httpChallengeExRO = new HttpChallengeExFW();

    private final SseDataExFW sseDataExRO = new SseDataExFW();
    private final SseEndExFW sseEndExRO = new SseEndExFW();

    private final SseEventFW.Builder sseEventRW = new SseEventFW.Builder();

    private final HttpDecodeHelper httpHelper = new HttpDecodeHelper();

    private final String8FW challengeEventType;

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer challengeBuffer;
    private final BufferPool bufferPool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final DirectBuffer initialComment;
    private final int httpTypeId;
    private final int sseTypeId;

    private final Long2ObjectHashMap<SseBindingConfig> bindings;
    private final Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> setHttpResponseHeaders;
    private final Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> setHttpResponseHeadersWithTimestampExt;

    public SseServerFactory(
        SseConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.challengeBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyDebitor = context::supplyDebitor;
        this.bindings = new Long2ObjectHashMap<>();
        this.initialComment = config.initialComment();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.sseTypeId = context.supplyTypeId(SseBinding.NAME);
        this.setHttpResponseHeaders = this::setHttpResponseHeaders;
        this.setHttpResponseHeadersWithTimestampExt = this::setHttpResponseHeadersWithTimestampExt;
        this.challengeEventType = new String8FW(config.getChallengeEventType());
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        SseBindingConfig sseBinding = new SseBindingConfig(binding);
        bindings.put(binding.id, sseBinding);
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
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        MessageConsumer newStream = null;

        if (!isSseRequestMethod(httpBeginEx))
        {
            doHttpResponse(begin, network, HEADER_VALUE_STATUS_405);
            newStream = (t, b, i, l) -> {};
        }
        else
        {
            newStream = newInitialSseStream(begin, network, httpBeginEx);
        }

        return newStream;
    }

    public MessageConsumer newInitialSseStream(
        final BeginFW begin,
        final MessageConsumer network,
        final HttpBeginExFW httpBeginEx)
    {
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();
        httpHelper.reset();
        headers.forEach(httpHelper::onHttpHeader);

        String16FW pathInfo = httpHelper.path; // TODO: ":pathinfo" ?
        String16FW lastEventId = httpHelper.lastEventId;

        // extract lastEventId query parameter from pathInfo
        // use query parameter value as default for missing Last-Event-ID header
        if (pathInfo != null)
        {
            Matcher matcher = QUERY_PARAMS_PATTERN.matcher(pathInfo.asString());
            if (matcher.matches())
            {
                String path = matcher.group("path");
                String query = matcher.group("query");

                matcher = LAST_EVENT_ID_PATTERN.matcher(query);
                StringBuffer builder = new StringBuffer(path);
                while (matcher.find())
                {
                    if (lastEventId == null)
                    {
                        lastEventId = decodeLastEventId(matcher.group("lastEventId"));
                    }

                    String replacement = matcher.group(3).isEmpty() ? "$3" : "$1";
                    matcher.appendReplacement(builder, replacement);
                }
                matcher.appendTail(builder);
                pathInfo = new String16FW(builder.toString());
            }
        }

        MessageConsumer newStream = null;

        if (lastEventId == null || lastEventId.length() <= MAXIMUM_LAST_EVENT_ID_SIZE)
        {
            final SseBindingConfig binding = bindings.get(routeId);
            final SseRouteConfig resolved = binding != null ?  binding.resolve(authorization, pathInfo.asString()) : null;

            if (resolved != null)
            {
                final boolean timestampRequested = httpBeginEx.headers().anyMatch(header ->
                    HEADER_NAME_ACCEPT.equals(header.name()) &&
                    header.value().asString().contains("ext=timestamp"));

                final String8FW lastEventId8 = httpHelper.asLastEventId(lastEventId);

                final SseServer server = new SseServer(
                    network,
                    routeId,
                    initialId,
                    resolved.id,
                    timestampRequested);

                server.onNetBegin(begin);
                server.stream.doAppBegin(traceId, authorization, affinity, pathInfo, lastEventId8);

                newStream = server::onNetMessage;
            }
        }
        else
        {
            doHttpResponse(begin, network, HEADER_VALUE_STATUS_400);

            newStream = (t, b, i, l) -> {};
        }

        return newStream;
    }

    private final class SseServer
    {
        private final MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> setHttpHeaders;
        private final SseStream stream;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int state;

        private long httpReplySeq;
        private long httpReplyAck;
        private int httpReplyMax;

        private int networkSlot = NO_SLOT;
        private int networkSlotOffset;

        private boolean initialCommentPending;
        private int deferredClaim;
        private boolean deferredEnd;

        private long httpReplyBud;
        private int httpReplyPad;
        private long httpReplyAuth;
        private BudgetDebitor replyDebitor;
        private long replyDebitorIndex = NO_DEBITOR_INDEX;

        private SseServer(
            MessageConsumer network,
            long routeId,
            long initialId,
            long resolvedId,
            boolean timestampRequested)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.setHttpHeaders = timestampRequested ? setHttpResponseHeadersWithTimestampExt : setHttpResponseHeaders;
            this.initialCommentPending = initialComment != null;
            this.stream = new SseStream(resolvedId, timestampRequested ? SseDataExFW::timestamp : ex -> 0L);
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
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
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onNetChallenge(challenge);
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = SseState.openingInitial(state);

            assert initialAck <= initialSeq;
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();

            doReset(network, routeId, initialId, sequence, acknowledge, maximum);
        }

        private void onNetEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = SseState.closeInitial(state);

            assert initialAck <= initialSeq;

            stream.doAppEndDeferred(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = SseState.closeInitial(state);

            assert initialAck <= initialSeq;

            stream.doAppAbort(traceId, authorization);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert sequence <= httpReplySeq;
            assert acknowledge >= httpReplyAck;
            assert maximum >= httpReplyMax;

            httpReplyAck = acknowledge;
            httpReplyMax = maximum;
            state = SseState.closeReply(state);

            assert httpReplyAck <= httpReplySeq;

            stream.doAppEndDeferred(traceId, authorization);
            stream.doAppReset(traceId);
            cleanupDebitorIfNecessary();
        }

        private void onNetWindow(
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
            assert sequence <= httpReplySeq;
            assert acknowledge >= httpReplyAck;
            assert maximum >= httpReplyMax;

            httpReplyAck = acknowledge;
            httpReplyMax = maximum;
            httpReplyPad = padding;
            httpReplyBud = budgetId;
            httpReplyAuth = authorization;
            state = SseState.openReply(state);

            assert httpReplyAck <= httpReplySeq;

            if (httpReplyBud != 0L && replyDebitorIndex == NO_DEBITOR_INDEX)
            {
                replyDebitor = supplyDebitor.apply(budgetId);
                replyDebitorIndex = replyDebitor.acquire(budgetId, replyId, this::flushNetwork);
            }

            if (httpReplyBud != 0L && replyDebitorIndex == NO_DEBITOR_INDEX)
            {
                doNetAbort(traceId, authorization);
                stream.doAppEndDeferred(traceId, authorization);
                stream.doAppReset(traceId);
            }
            else
            {
                flushNetwork(traceId);
            }
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long sequence = challenge.sequence();
            final long acknowledge = challenge.acknowledge();
            final int maximum = challenge.maximum();

            assert acknowledge <= sequence;
            assert sequence <= httpReplySeq;
            assert acknowledge >= httpReplyAck;
            assert maximum >= httpReplyMax;

            httpReplyAck = acknowledge;
            httpReplyMax = maximum;

            assert httpReplyAck <= httpReplySeq;

            final HttpChallengeExFW httpChallengeEx = challenge.extension().get(httpChallengeExRO::tryWrap);
            if (httpChallengeEx != null)
            {
                final JsonObjectBuilder challengeObject = Json.createObjectBuilder();
                final JsonObjectBuilder challengeHeaders = Json.createObjectBuilder();
                final Array32FW<HttpHeaderFW> httpHeaders = httpChallengeEx.headers();

                httpHeaders.forEach(header ->
                {
                    final String8FW name = header.name();
                    final String16FW value = header.value();
                    if (name != null)
                    {
                        if (name.sizeof() > SIZE_OF_BYTE &&
                            name.buffer().getByte(name.offset() + SIZE_OF_BYTE) != ASCII_COLON)
                        {
                            final String propertyName = name.asString();
                            final String propertyValue = value.asString();
                            challengeHeaders.add(propertyName, propertyValue);
                        }
                        else if (name.equals(HEADER_NAME_METHOD))
                        {
                            final String propertyValue = value.asString();
                            challengeObject.add(METHOD_PROPERTY, propertyValue);
                        }
                    }
                });
                challengeObject.add(HEADERS_PROPERTY, challengeHeaders);

                final String challengeJson = challengeObject.build().toString();
                final int challengeBytes = challengeBuffer.putStringWithoutLengthUtf8(0, challengeJson);

                final SseEventFW sseEvent = sseEventRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                        .flags(Flags.INIT | Flags.FIN)
                        .type(challengeEventType.value())
                        .data(challengeBuffer, 0, challengeBytes)
                        .build();

                final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .routeId(routeId)
                        .streamId(replyId)
                        .sequence(httpReplySeq)
                        .acknowledge(httpReplyAck)
                        .maximum(httpReplyMax)
                        .traceId(challenge.traceId())
                        .authorization(0)
                        .budgetId(httpReplyBud)
                        .reserved(sseEvent.sizeof() + httpReplyPad)
                        .payload(sseEvent.buffer(), sseEvent.offset(), sseEvent.sizeof())
                        .build();

                if (networkSlot == NO_SLOT)
                {
                    networkSlot = bufferPool.acquire(replyId);
                }

                if (networkSlot != NO_SLOT)
                {
                    MutableDirectBuffer buffer = bufferPool.buffer(networkSlot);
                    buffer.putBytes(networkSlotOffset, data.buffer(), data.offset(), data.sizeof());
                    networkSlotOffset += data.sizeof();

                    if (replyDebitorIndex != NO_DEBITOR_INDEX)
                    {
                        deferredClaim += data.reserved();
                    }
                }

                flushNetwork(challenge.traceId());
            }
        }

        private void doNetBegin(
            long replySeq,
            long replyAck,
            int replyMax,
            long traceId,
            long authorization,
            long affinity)
        {
            this.httpReplySeq = replySeq;
            this.httpReplyAck = replyAck;
            this.httpReplyMax = replyMax;

            doHttpBegin(network, routeId, replyId,
                    replySeq, replyAck, replyMax, traceId, authorization, affinity,
                    setHttpHeaders);

            encodeNetwork(traceId);
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            Flyweight payload)
        {
            doHttpData(network, routeId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                    traceId, authorization, budgetId, flags, reserved, payload);

            httpReplySeq += reserved;

            assert httpReplySeq <= httpReplyAck + httpReplyMax;
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doHttpFlush(network, routeId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closeReply(state);

                doHttpAbort(network, routeId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                        traceId, authorization);

                cleanupDebitorIfNecessary();
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closeReply(state);

                doHttpEnd(network, routeId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                        traceId, authorization);

                cleanupDebitorIfNecessary();
            }
        }

        private void doNetWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doWindow(network, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doNetReset(
            long traceId)
        {
            if (!SseState.initialClosed(state))
            {
                state = SseState.closeInitial(state);

                doReset(network, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }

        private void doEncodeEvent(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            DirectBuffer id,
            DirectBuffer type,
            long timestamp)
        {
            final SseEventFW sseEvent = sseEventRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                    .flags(flags)
                    .dataFinOnly(payload)
                    .timestamp(timestamp)
                    .id(id)
                    .type(type)
                    .dataInit(payload)
                    .dataContOnly(payload)
                    .build();

            doNetData(traceId, authorization, budgetId, reserved, flags, sseEvent);

            initialCommentPending = false;
        }

        private void flushNetwork(
            long traceId)
        {
            if (SseState.replyOpening(state))
            {
                encodeNetwork(traceId);
            }

            stream.flushAppWindow(traceId);
        }

        private void encodeNetwork(
            long traceId)
        {
            comment:
            if (initialCommentPending)
            {
                assert initialComment != null;

                final int flags = FIN | INIT;
                final SseEventFW sseEvent =
                        sseEventRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                  .flags(flags)
                                  .comment(initialComment)
                                  .build();

                final int reserved = sseEvent.sizeof() + httpReplyPad;

                if (reserved > (int)(httpReplySeq - httpReplyAck + httpReplyMax))
                {
                    break comment;
                }

                int claimed = reserved;
                if (replyDebitorIndex != NO_DEBITOR_INDEX)
                {
                    claimed = replyDebitor.claim(traceId, replyDebitorIndex, replyId,
                        reserved, reserved, 0);
                }

                if (claimed == reserved)
                {
                    doHttpData(network, routeId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                            traceId, httpReplyAuth, httpReplyBud, flags, reserved, sseEvent);

                    httpReplySeq += reserved;

                    assert httpReplySeq <= httpReplyAck + httpReplyMax;

                    initialCommentPending = false;
                }
            }

            if (deferredClaim > 0)
            {
                assert replyDebitorIndex != NO_DEBITOR_INDEX;

                int claimed = replyDebitor.claim(traceId, replyDebitorIndex, replyId,
                    deferredClaim, deferredClaim, 0);

                if (claimed == deferredClaim)
                {
                    deferredClaim = 0;
                }
            }

            if (deferredClaim == 0)
            {
                if (networkSlot != NO_SLOT)
                {
                    final MutableDirectBuffer buffer = bufferPool.buffer(networkSlot);
                    final DataFW data = dataRO.wrap(buffer,  0,  networkSlotOffset);
                    final int reserved = data.reserved();

                    if (httpReplySeq + reserved <= httpReplyAck + httpReplyMax)
                    {
                        network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                        httpReplySeq += reserved;
                        networkSlotOffset -= data.sizeof();
                        assert networkSlotOffset == 0;
                        bufferPool.release(networkSlot);
                        networkSlot = NO_SLOT;

                        if (deferredEnd)
                        {
                            final long authorization = data.authorization();

                            doHttpEnd(network, routeId, replyId, httpReplySeq, httpReplyAck, httpReplyMax,
                                    data.traceId(), authorization);
                            cleanupDebitorIfNecessary();
                            deferredEnd = false;

                            stream.doAppEndDeferred(data.traceId(), authorization);
                        }
                    }
                }
            }
        }

        private void cleanupDebitorIfNecessary()
        {
            if (replyDebitorIndex != NO_DEBITOR_INDEX)
            {
                replyDebitor.release(replyDebitorIndex, replyId);
                replyDebitor = null;
                replyDebitorIndex = NO_DEBITOR_INDEX;
            }
        }

        final class SseStream
        {
            private MessageConsumer application;
            private final long routeId;
            private final long initialId;
            private final long replyId;
            private final ToLongFunction<SseDataExFW> supplyTimestamp;

            private int state;

            private long sseReplySeq;
            private long sseReplyAck;
            private int sseReplyMax;

            private SseStream(
                long routeId,
                ToLongFunction<SseDataExFW> supplyTimestamp)
            {
                this.routeId = routeId;
                this.initialId = supplyInitialId.applyAsLong(routeId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
                this.supplyTimestamp = supplyTimestamp;
            }

            private void doAppBegin(
                long traceId,
                long authorization,
                long affinity,
                String16FW pathInfo,
                String8FW lastEventId)
            {
                application = newSseStream(this::onAppMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, affinity, pathInfo, lastEventId);
            }

            private void doAppEndDeferred(
                long traceId,
                long authorization)
            {
                if (SseState.initialClosing(state))
                {
                    doAppEnd(traceId, authorization);
                }

                state = SseState.closingInitial(state);
            }

            private void doAppEnd(
                long traceId,
                long authorization)
            {
                if (!SseState.initialClosed(state))
                {
                    state = SseState.closeInitial(state);

                    doEnd(application, routeId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization);
                }
            }

            private void doAppAbort(
                long traceId,
                long authorization)
            {
                if (!SseState.initialClosed(state))
                {
                    state = SseState.closeInitial(state);

                    doAbort(application, routeId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization);
                }
            }

            private void onAppMessage(
                int msgTypeId,
                DirectBuffer buffer,
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
                }
            }

            private void onAppBegin(
                BeginFW begin)
            {
                final long sequence = begin.sequence();
                final long acknowledge = begin.acknowledge();
                final int maximum = begin.maximum();
                final long traceId = begin.traceId();
                final long authorization = begin.authorization();
                final long affinity = begin.affinity();

                assert acknowledge <= sequence;
                assert sequence >= sseReplySeq;
                assert acknowledge >= sseReplyAck;

                sseReplySeq = sequence;
                sseReplyAck = acknowledge;
                sseReplyMax = maximum;
                state = SseState.openingReply(state);

                assert sseReplyAck <= sseReplySeq;

                doNetBegin(sseReplySeq, sseReplyAck, sseReplyMax,
                        traceId, authorization, affinity);
            }

            private void onAppData(
                DataFW data)
            {
                final long sequence = data.sequence();
                final long acknowledge = data.acknowledge();
                final long traceId = data.traceId();
                final long authorization = data.authorization();
                final long budgetId = data.budgetId();
                final int reserved = data.reserved();

                assert acknowledge <= sequence;
                assert sequence >= sseReplySeq;

                sseReplySeq = sequence + reserved;

                assert sseReplyAck <= sseReplySeq;

                if (sseReplySeq > sseReplyAck + sseReplyMax)
                {
                    doAppReset(traceId);
                    doAppEndDeferred(traceId, authorization);
                    doNetAbort(traceId, authorization);
                }
                else
                {
                    final int flags = data.flags();
                    final OctetsFW payload = data.payload();
                    final OctetsFW extension = data.extension();

                    DirectBuffer id = null;
                    DirectBuffer type = null;
                    long timestamp = 0L;
                    if (flags != 0x00 && extension.sizeof() > 0)
                    {
                        final SseDataExFW sseDataEx = extension.get(sseDataExRO::wrap);
                        id = sseDataEx.id().value();
                        type = sseDataEx.type().value();
                        timestamp = supplyTimestamp.applyAsLong(sseDataEx);
                    }

                    doEncodeEvent(traceId, authorization, budgetId, reserved, flags, payload, id, type, timestamp);
                }
            }

            private void onAppEnd(
                EndFW end)
            {
                final long sequence = end.sequence();
                final long acknowledge = end.acknowledge();
                final long traceId = end.traceId();
                final long authorization = end.authorization();
                final OctetsFW extension = end.extension();

                assert acknowledge <= sequence;
                assert sequence >= sseReplySeq;

                sseReplySeq = sequence;
                state = SseState.closeReply(state);

                assert sseReplyAck <= sseReplySeq;

                if (extension.sizeof() > 0)
                {
                    final SseEndExFW sseEndEx = extension.get(sseEndExRO::wrap);
                    final DirectBuffer id = sseEndEx.id().value();

                    int flags = FIN | INIT;

                    final SseEventFW sseEvent = sseEventRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                            .flags(flags)
                            .id(id)
                            .build();

                    final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .routeId(SseServer.this.routeId)
                        .streamId(SseServer.this.replyId)
                        .sequence(httpReplySeq)
                        .acknowledge(httpReplyAck)
                        .maximum(httpReplyMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .flags(flags)
                        .budgetId(httpReplyBud)
                        .reserved(sseEvent.sizeof() + httpReplyPad)
                        .payload(sseEvent.buffer(), sseEvent.offset(), sseEvent.sizeof())
                        .build();

                    if (networkSlot == NO_SLOT)
                    {
                        networkSlot = bufferPool.acquire(SseServer.this.replyId);
                    }

                    if (networkSlot != NO_SLOT)
                    {
                        MutableDirectBuffer buffer = bufferPool.buffer(networkSlot);
                        buffer.putBytes(networkSlotOffset, data.buffer(), data.offset(), data.sizeof());
                        networkSlotOffset += data.sizeof();

                        if (replyDebitorIndex != NO_DEBITOR_INDEX)
                        {
                            deferredClaim += data.reserved();
                        }

                        deferredEnd = true;
                        flushNetwork(traceId);
                    }
                    else
                    {
                        doNetAbort(traceId, authorization);
                    }
                }
                else
                {
                    doNetEnd(traceId, authorization);
                }

                doAppEndDeferred(traceId, authorization);
            }

            private void onAppFlush(
                FlushFW flush)
            {
                final long sequence = flush.sequence();
                final long acknowledge = flush.acknowledge();
                final long traceId = flush.traceId();
                final long authorization = flush.authorization();
                final long budgetId = flush.budgetId();
                final int reserved = flush.reserved();

                assert acknowledge <= sequence;
                assert sequence >= sseReplySeq;

                sseReplySeq = sequence;

                assert sseReplyAck <= sseReplySeq;

                flushNetwork(traceId);
                doNetFlush(traceId, authorization, budgetId, reserved);
            }

            private void onAppAbort(
                AbortFW abort)
            {
                final long sequence = abort.sequence();
                final long acknowledge = abort.acknowledge();
                final long traceId = abort.traceId();
                final long authorization = abort.authorization();

                assert acknowledge <= sequence;
                assert sequence >= sseReplySeq;

                sseReplySeq = sequence;
                state = SseState.closeReply(state);

                assert sseReplyAck <= sseReplySeq;

                doNetAbort(traceId, authorization);
                doAppEndDeferred(traceId, authorization);
            }

            private void onAppWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final int maximum = window.maximum();
                final long authorization = window.authorization();
                final long traceId = window.traceId();
                final long budgetId = window.budgetId();
                final int padding = window.padding();
                final int capabilities = window.capabilities() | CHALLENGE_CAPABILITIES_MASK;

                assert acknowledge <= sequence;
                assert acknowledge >= initialAck;
                assert maximum >= initialMax;

                initialAck = acknowledge;
                initialMax = maximum;

                assert initialAck <= initialSeq;

                doNetWindow(authorization, traceId, budgetId, padding, capabilities);
            }

            private void onAppReset(
                ResetFW reset)
            {
                final long sequence = reset.sequence();
                final long acknowledge = reset.acknowledge();
                final long traceId = reset.traceId();

                assert acknowledge <= sequence;
                assert acknowledge >= initialAck;

                initialAck = acknowledge;

                assert initialAck <= initialSeq;

                doNetReset(traceId);
            }

            private void doAppReset(
                long traceId)
            {
                if (!SseState.replyClosed(state))
                {
                    state = SseState.closeReply(state);

                    doReset(application, routeId, replyId, sseReplySeq, sseReplyAck, sseReplyMax,
                            traceId);
                }
            }

            private void flushAppWindow(
                long traceId)
            {
                int httpReplyPendingAck = (int)(httpReplySeq - httpReplyAck) + networkSlotOffset;
                if (initialCommentPending)
                {
                    assert initialComment != null;
                    httpReplyPendingAck += initialComment.capacity() + 3 + httpReplyPad;
                }

                int sseReplyPad = httpReplyPad + MAXIMUM_HEADER_SIZE;
                int sseReplyAckMax = (int)(sseReplySeq - httpReplyPendingAck);
                if (sseReplyAckMax > sseReplyAck || httpReplyMax > sseReplyMax)
                {
                    sseReplyAck = sseReplyAckMax;
                    assert sseReplyAck <= sseReplySeq;

                    sseReplyMax = httpReplyMax;

                    doWindow(application, routeId, replyId, sseReplySeq, sseReplyAck, sseReplyMax,
                            traceId, httpReplyAuth, httpReplyBud, sseReplyPad, 0);
                }
            }
        }
    }

    private final class HttpDecodeHelper
    {
        private final String8FW.Builder lastEventIdRW = new String8FW.Builder().wrap(new UnsafeBuffer(new byte[256]), 0, 256);

        private final String16FW pathRO = new String16FW();
        private final String16FW lastEventIdRO = new String16FW();

        private String16FW path;
        private String16FW lastEventId;

        private void onHttpHeader(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();
            final String16FW value = header.value();

            if (HEADER_NAME_PATH.equals(name))
            {
                path = pathRO.wrap(value.buffer(), value.offset(), value.limit());
            }
            else if (HEADER_NAME_LAST_EVENT_ID.equals(name))
            {
                lastEventId = lastEventIdRO.wrap(value.buffer(), value.offset(), value.limit());
            }
        }

        private String8FW asLastEventId(
            String16FW lastEventId)
        {
            lastEventIdRW.rewrap();
            return lastEventId != null ? lastEventIdRW.set(lastEventId).build() : LAST_EVENT_ID_NULL;
        }

        private void reset()
        {
            path = null;
            lastEventId = null;
        }
    }

    private void setHttpResponseHeaders(
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> headers)
    {
        headers.item(h -> h.name(":status").value("200"));
        headers.item(h -> h.name("content-type").value("text/event-stream"));
    }

    private void setHttpResponseHeadersWithTimestampExt(
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> headers)
    {
        headers.item(h -> h.name(":status").value("200"));
        headers.item(h -> h.name("content-type").value("text/event-stream;ext=timestamp"));
    }

    private void doHttpBegin(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> mutator)
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
                .extension(e -> e.set(visitHttpBeginEx(mutator)))
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private Flyweight.Builder.Visitor visitHttpBeginEx(
        Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> headers)
    {
        return (buffer, offset, limit) ->
            httpBeginExRW.wrap(buffer, offset, limit)
                         .typeId(httpTypeId)
                         .headers(headers)
                         .build()
                         .sizeof();
    }

    private void doHttpData(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int flags,
        int reserved,
        Flyweight payload)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload.buffer(), payload.offset(), payload.sizeof())
                .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
    }

    private void doHttpEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doHttpAbort(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doHttpFlush(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved)
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
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doHttpResponse(
        BeginFW begin,
        MessageConsumer acceptReply,
        String16FW status)
    {
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();
        final long acceptRouteId = begin.routeId();
        final long acceptInitialId = begin.streamId();
        final long acceptReplyId = supplyReplyId.applyAsLong(acceptInitialId);
        final long affinity = begin.affinity();
        final long traceId = begin.traceId();

        doWindow(acceptReply, acceptRouteId, acceptInitialId, sequence, acknowledge, 0, traceId, 0L, 0, 0, 0);
        doHttpBegin(acceptReply, acceptRouteId, acceptReplyId, 0L, 0L, 0, traceId, 0L, affinity, hs ->
            hs.item(h -> h.name(HEADER_NAME_STATUS).value(status))
              .item(h -> h.name(HEADER_NAME_CONTENT_LENGTH).value(HEADER_VALUE_CONTENT_LENGTH_0)));
        doHttpEnd(acceptReply, acceptRouteId, acceptReplyId, 0L, 0L, 0, traceId, 0L);
    }

    private MessageConsumer newSseStream(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        String16FW pathInfo,
        String8FW lastEventId)
    {
        final SseBeginExFW sseBegin = sseBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(sseTypeId)
                .pathInfo(pathInfo)
                .lastEventId(lastEventId)
                .build();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(sseBegin.buffer(), sseBegin.offset(), sseBegin.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doAbort(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        // TODO: SseAbortEx
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        long routeId,
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
                .routeId(routeId)
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

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
               .routeId(routeId)
               .streamId(streamId)
               .sequence(sequence)
               .acknowledge(acknowledge)
               .maximum(maximum)
               .traceId(traceId)
               .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum)
    {
        doReset(sender, routeId, streamId, sequence, acknowledge, maximum, supplyTraceId.getAsLong());
    }

    private static String16FW decodeLastEventId(
        String lastEventId)
    {
        if (lastEventId != null && lastEventId.indexOf('%') != -1)
        {
            try
            {
                lastEventId = URLDecoder.decode(lastEventId, UTF_8.toString());
            }
            catch (UnsupportedEncodingException ex)
            {
                // unexpected, UTF-8 is a supported character set
                rethrowUnchecked(ex);
            }
        }

        return lastEventId != null ? new String16FW(lastEventId) : null;
    }

    private static boolean isSseRequestMethod(
        HttpBeginExFW httpBeginEx)
    {
        return httpBeginEx != null &&
               httpBeginEx.headers().anyMatch(h -> HEADER_NAME_METHOD.equals(h.name()) &&
                                                   HEADER_VALUE_METHOD_GET.equals(h.value()));
    }
}
