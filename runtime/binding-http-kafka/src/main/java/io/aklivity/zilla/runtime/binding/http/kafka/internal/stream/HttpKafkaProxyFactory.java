/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.time.Instant.now;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.HttpKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.config.HttpKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.config.HttpKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.config.HttpKafkaWithFetchResult;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.config.HttpKafkaWithProduceResult;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.KafkaMergedFetchDataExFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class HttpKafkaProxyFactory implements HttpKafkaStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String KAFKA_TYPE_NAME = "kafka";

    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final int DATA_FLAG_INCOMPLETE = 0x04;

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

    private static final int SIGNAL_WAIT_EXPIRED = 1;

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

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();

    private final HttpKafkaEtagHelper etagHelper = new HttpKafkaEtagHelper();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Signaler signaler;
    private final int httpTypeId;
    private final int kafkaTypeId;

    private final HttpBeginExFW httpBeginEx404;
    private final HttpBeginExFW httpBeginEx500;

    private final HttpHeaderFW httpStatus200;
    private final HttpHeaderFW httpStatus204;
    private final HttpHeaderFW httpStatus304;
    private final HttpHeaderFW httpMethodDelete;
    private final String8FW httpContentLength;
    private final String8FW httpContentType;
    private final String8FW httpEtag;

    private final Long2ObjectHashMap<HttpKafkaBindingConfig> bindings;

    public HttpKafkaProxyFactory(
        HttpKafkaConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.signaler = context.signaler();
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.httpBeginEx404 = initHttpBeginEx("404");
        this.httpBeginEx500 = initHttpBeginEx("500");
        this.httpStatus200 = initHttpHeader(":status", "200");
        this.httpStatus204 = initHttpHeader(":status", "204");
        this.httpStatus304 = initHttpHeader(":status", "304");
        this.httpMethodDelete = initHttpHeader(":method", "DELETE");
        this.httpContentLength = new String8FW("content-length");
        this.httpContentType = new String8FW("content-type");
        this.httpEtag = new String8FW("etag");
    }

    @Override
    public int originTypeId()
    {
        return httpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        HttpKafkaBindingConfig newBinding = new HttpKafkaBindingConfig(binding);
        bindings.put(binding.id, newBinding);
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
        MessageConsumer http)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        final HttpKafkaBindingConfig binding = bindings.get(routedId);

        HttpKafkaRouteConfig route = null;

        if (binding != null)
        {
            route = binding.resolve(authorization, httpBeginEx);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {
            final long resolvedId = route.id;

            switch (route.with.capability())
            {
            case FETCH:
            {
                final HttpKafkaWithFetchResult resolved = route.with.resolveFetch(authorization, httpBeginEx);

                if (resolved.merge())
                {
                    newStream = new HttpFetchManyProxy(
                            http,
                            originId,
                            routedId,
                            initialId,
                            resolvedId,
                            resolved)::onHttpMessage;
                }
                else
                {
                    newStream = new HttpFetchProxy(
                            http,
                            originId,
                            routedId,
                            initialId,
                            resolvedId,
                            resolved)::onHttpMessage;
                }
                break;
            }
            case PRODUCE:
            {
                final HttpKafkaWithProduceResult resolved = route.with.resolveProduce(authorization, httpBeginEx);

                if (resolved.reply())
                {
                    if (!resolved.idempotent())
                    {
                        newStream = new HttpCorrelateAsyncProxy(
                                http,
                                originId,
                                routedId,
                                initialId,
                                resolvedId,
                                resolved)::onHttpMessage;
                    }
                    else if (resolved.async())
                    {
                        newStream = new HttpProduceAsyncProxy(
                                http,
                                originId,
                                routedId,
                                initialId,
                                resolvedId,
                                resolved)::onHttpMessage;
                    }
                    else
                    {
                        newStream = new HttpProduceSyncProxy(
                                http,
                                originId,
                                routedId,
                                initialId,
                                resolvedId,
                                resolved)::onHttpMessage;
                    }
                }
                else
                {
                    newStream = new HttpProduceNoReplyProxy(
                            http,
                            originId,
                            routedId,
                            initialId,
                            resolvedId,
                            resolved)::onHttpMessage;
                }
                break;
            }
            }
        }

        return newStream;
    }

    private abstract class HttpProxy
    {
        protected final MessageConsumer http;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;

        protected long initialSeq;
        protected long initialAck;
        protected int initialMax;

        protected int state;

        protected long replySeq;
        protected long replyAck;
        protected int replyMax;
        protected long replyBud;
        protected int replyPad;
        protected int replyCap;

        private HttpProxy(
            MessageConsumer http,
            long originId,
            long routedId,
            long initialId)
        {
            this.http = http;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        protected abstract void onKafkaBegin(
            long traceId,
            long authorization,
            OctetsFW extension);

        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            OctetsFW extension)
        {
        }

        protected abstract void onKafkaEnd(
            long traceId,
            long authorization);

        protected void onKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
        }

        protected abstract void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities);

        protected abstract void onKafkaReset(
            long traceId,
            long authorization);

        protected abstract void onKafkaAbort(
            long traceId,
            long authorization);
    }

    private final class HttpFetchProxy extends HttpProxy
    {
        private final KafkaFetchProxy fetcher;

        private HttpFetchProxy(
            MessageConsumer http,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            HttpKafkaWithFetchResult resolved)
        {
            super(http, originId, routedId, initialId);
            this.fetcher = new KafkaFetchProxy(routedId, resolvedId, this, resolved);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onHttpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onHttpAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onHttpFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onHttpWindow(window);
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = HttpKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            fetcher.doKafkaBegin(traceId, authorization, affinity);
        }

        private void onHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            fetcher.initialSeq = initialSeq;
            fetcher.initialAck = initialSeq;

            doHttpWindow(authorization, traceId, budgetId, 0, 0);
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            if (HttpKafkaState.replyClosed(fetcher.state))
            {
                fetcher.doKafkaEnd(traceId, authorization);
            }
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            fetcher.doKafkaAbort(traceId, authorization);
        }

        protected void onHttpFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            fetcher.doKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            fetcher.doKafkaReset(traceId);
        }

        private void onHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = HttpKafkaState.openReply(state);

            assert replyAck <= replySeq;

            fetcher.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        private void onKafkaError(
            long traceId,
            long authorization)
        {
            doHttpReset(traceId);

            if (!HttpKafkaState.replyOpening(state))
            {
                doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
            }

            doHttpAbort(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            // nop
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            OctetsFW extension)
        {
            if (HttpKafkaState.replyClosing(state))
            {
                replySeq += reserved;

                fetcher.doKafkaWindow(traceId, authorization, budgetId, reserved, flags);
            }
            else
            {
                if ((flags & 0x02) != 0x00) // INIT
                {
                    Flyweight httpBeginEx = emptyRO;

                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                            dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;

                    if (payload == null)
                    {
                        httpBeginEx = httpBeginEx404;
                    }
                    else if (kafkaDataEx != null)
                    {
                        final KafkaMergedFetchDataExFW fetch = kafkaDataEx.merged().fetch();
                        final int contentLength = payload.sizeof() + fetch.deferred();

                        final HttpBeginExFW.Builder builder = httpBeginExRW
                                .wrap(extBuffer, 0, extBuffer.capacity())
                                .typeId(httpTypeId);

                        if (contentLength == 0)
                        {
                            builder.headersItem(h -> h.set(httpStatus204));
                        }
                        else
                        {
                            builder.headersItem(h -> h.set(httpStatus200))
                                   .headersItem(h -> h.name(httpContentLength).value(Integer.toString(contentLength)));
                        }

                        final Array32FW<KafkaHeaderFW> headers = fetch.headers();

                        // TODO: header inclusion configuration
                        final KafkaHeaderFW contentType =
                                headers.matchFirst(h -> httpContentType.value().equals(h.name().value()));
                        if (contentType != null)
                        {
                            builder.headersItem(h -> h.name(contentType.name().value(), 0, contentType.nameLen())
                                                      .value(contentType.value().value(), 0, contentType.valueLen()));
                        }

                        // TODO: header inclusion configuration
                        final KafkaHeaderFW etag =
                                headers.matchFirst(h -> httpEtag.value().equals(h.name().value()));
                        if (etag != null)
                        {
                            final String16FW progress64 = etagHelper.encode(fetch.progress());
                            String implicitEtag = String.format("%s/%s",
                                progress64.asString(),
                                etag.value().value().getStringWithoutLengthAscii(0, etag.valueLen()));
                            builder.headersItem(h -> h.name(httpEtag).value(implicitEtag));
                        }
                        else
                        {
                            final String16FW progress64 = etagHelper.encode(fetch.progress());

                            builder.headersItem(h -> h
                                .name(httpEtag.value(), 0, httpEtag.length())
                                .value(progress64.value(), 0, progress64.length()));
                        }

                        httpBeginEx = builder.build();
                    }

                    doHttpBegin(traceId, authorization, 0L, httpBeginEx);
                }

                if (HttpKafkaState.replyOpening(state) && payload != null && payload.sizeof() > 0)
                {
                    // TODO: await http response window if necessary (handle in doHttpData)
                    doHttpData(traceId, authorization, budgetId, reserved, flags, payload);
                }

                if ((flags & 0x01) != 0x00) // FIN
                {
                    fetcher.doKafkaEnd(traceId, authorization);
                    state = HttpKafkaState.closingReply(state);
                }
            }
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {

            if (!HttpKafkaState.replyOpening(state))
            {
                HttpBeginExFW httpBeginEx = httpBeginEx404;

                final String16FW etag = fetcher.resolved.etag();
                if (etag != null)
                {
                    httpBeginEx = httpBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headersItem(h -> h.set(httpStatus304))
                        .headersItem(h -> h.name(httpEtag).value(etag))
                        .build();
                }

                doHttpBegin(traceId, authorization, 0L, httpBeginEx);
            }

            if (HttpKafkaState.initialClosed(state))
            {
                fetcher.doKafkaEnd(traceId, authorization);
            }

            doHttpEnd(traceId, authorization);
        }

        @Override
        protected void onKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doHttpFlush(traceId, authorization, budgetId, reserved);
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
        }

        private void doHttpAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = fetcher.replySeq;
                state = HttpKafkaState.closeReply(state);

                doAbort(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doHttpData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            doData(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, emptyRO);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doHttpEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = fetcher.replySeq;
                state = HttpKafkaState.closeReply(state);

                doEnd(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doHttpFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = fetcher.replySeq;

            doFlush(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doHttpWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = fetcher.initialAck;
            initialMax = fetcher.initialMax;

            doWindow(http, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    private final class HttpFetchManyProxy extends HttpProxy
    {
        private final KafkaFetchProxy fetcher;

        private long replyBud;
        private int replyMsgs;

        private HttpFetchManyProxy(
            MessageConsumer http,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            HttpKafkaWithFetchResult resolved)
        {
            super(http, originId, routedId, initialId);
            this.fetcher = new KafkaFetchProxy(routedId, resolvedId, this, resolved);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onHttpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onHttpAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onHttpFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onHttpWindow(window);
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = HttpKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            doHttpWindow(traceId, authorization, 0, 0, 0);
            fetcher.doKafkaBegin(traceId, authorization, affinity);
        }

        private void onHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            fetcher.initialSeq = initialSeq;
            fetcher.initialAck = initialSeq;

            doHttpWindow(authorization, traceId, budgetId, 0, 0);
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            if (HttpKafkaState.replyClosed(fetcher.state))
            {
                fetcher.doKafkaEnd(traceId, authorization);
            }
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            fetcher.doKafkaAbort(traceId, authorization);
        }

        protected void onHttpFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            fetcher.doKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            fetcher.doKafkaReset(traceId);
        }

        private void onHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            state = HttpKafkaState.openReply(state);

            assert replyAck <= replySeq;

            final int mergePadding = fetcher.resolved.padding();
            fetcher.doKafkaWindow(traceId, authorization, budgetId, padding + mergePadding, capabilities);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        private void onKafkaError(
            long traceId,
            long authorization)
        {
            doHttpReset(traceId);

            if (!HttpKafkaState.replyOpening(state))
            {
                doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
            }

            doHttpAbort(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
            final KafkaBeginExFW kafkaBeginEx =
                    beginEx != null && beginEx.typeId() == kafkaTypeId ? extension.get(kafkaBeginExRO::tryWrap) : null;

            if (kafkaBeginEx != null)
            {
                final KafkaMergedBeginExFW kafkaMergedBeginEx = kafkaBeginEx.merged();
                final Array32FW<KafkaOffsetFW> partitions = kafkaMergedBeginEx.partitions();
                final String16FW etag = etagHelper.encodeLatest(partitions);

                if (fetcher.resolved.partitions(partitions))
                {
                    final HttpBeginExFW httpBeginEx = httpBeginExRW
                            .wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(httpTypeId)
                            .headersItem(h -> h.set(httpStatus304))
                            .headersItem(h -> h.name(httpContentType).value(fetcher.resolved.contentType()))
                            .headersItem(h -> h.name(httpEtag).value(etag))
                            .build();

                    doHttpBegin(traceId, authorization, 0L, httpBeginEx);
                    doHttpEnd(traceId, authorization);

                    fetcher.doKafkaEnd(traceId, authorization);
                    fetcher.doKafkaReset(traceId);
                }
                else
                {
                    final HttpBeginExFW.Builder builder = httpBeginExRW
                            .wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(httpTypeId);

                    builder.headers(fetcher.resolved::headers);

                    if (etag != null)
                    {
                        builder.headersItem(h -> h.name(httpEtag).value(etag));
                    }

                    Flyweight httpBeginEx = builder.build();

                    doHttpBegin(traceId, authorization, 0L, httpBeginEx);
                }
            }
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            OctetsFW extension)
        {
            if (payload != null && payload.sizeof() > 0)
            {
                int replyPadAdjust = 0;

                if (replyMsgs == 0)
                {
                    OctetsFW preamble = fetcher.resolved.header();
                    int reservedPre = preamble.sizeof();
                    doHttpData(traceId, authorization, replyBud, reservedPre, 0x03, preamble);

                    replyPadAdjust = reservedPre;
                }
                else if ((flags & DATA_FLAG_INIT) != 0x00 && replyMsgs > 0)
                {
                    OctetsFW preamble = fetcher.resolved.separator();
                    int reservedSep = preamble.sizeof();
                    doHttpData(traceId, authorization, replyBud, reservedSep, 0x03, preamble);

                    replyPadAdjust = reservedSep;
                }

                // TODO: await http response window if necessary (handle in doHttpData)
                doHttpData(traceId, authorization, budgetId, reserved - replyPadAdjust, flags, payload);

                replyMsgs++;
                replyPadAdjust = 0;
            }
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (HttpKafkaState.initialClosed(state))
            {
                fetcher.doKafkaEnd(traceId, authorization);
            }

            if (!HttpKafkaState.replyClosed(state))
            {
                if (replyMsgs == 0)
                {
                    OctetsFW preamble = fetcher.resolved.header();
                    int reservedPre = preamble.sizeof();
                    doHttpData(traceId, authorization, replyBud, reservedPre, 0x03, preamble);
                }

                OctetsFW postamble = fetcher.resolved.trailer();
                final int reservedPost = postamble.sizeof();
                doHttpData(traceId, authorization, replyBud, reservedPost, 0x03, postamble);

                doHttpEnd(traceId, authorization);
            }
        }

        @Override
        protected void onKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doHttpFlush(traceId, authorization, budgetId, reserved);
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
        }

        private void doHttpAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = fetcher.replySeq;
                state = HttpKafkaState.closeReply(state);

                doAbort(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doHttpData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            doData(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, emptyRO);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doHttpEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = fetcher.replySeq;
                state = HttpKafkaState.closeReply(state);

                doEnd(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doHttpFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = fetcher.replySeq;

            doFlush(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doHttpWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = fetcher.initialAck;
            initialMax = fetcher.initialMax;

            doWindow(http, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    private final class KafkaFetchProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final HttpKafkaWithFetchResult resolved;
        private final HttpProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long cancelWait = NO_CANCEL_ID;

        private KafkaFetchProxy(
            long originId,
            long routedId,
            HttpProxy delegate,
            HttpKafkaWithFetchResult resolved)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = HttpKafkaState.openingInitial(state);

            kafka = newKafkaFetcher(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);

            final long timeout = resolved.timeout();
            if (timeout > 0L)
            {
                cancelWait = signaler.signalAt(now().toEpochMilli() + timeout, originId, routedId, initialId,
                        traceId, SIGNAL_WAIT_EXPIRED, 0);
            }
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpKafkaState.closeInitial(state);

                signaler.cancel(cancelWait);
                cancelWait = NO_CANCEL_ID;

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpKafkaState.closeInitial(state);

                signaler.cancel(cancelWait);
                cancelWait = NO_CANCEL_ID;

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            initialSeq = delegate.initialSeq;

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onKafkaSignal(signal);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = HttpKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaBegin(traceId, authorization, extension);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            signaler.cancel(cancelWait);
            cancelWait = NO_CANCEL_ID;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                delegate.onKafkaReset(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();

                delegate.onKafkaData(traceId, authorization, budgetId, reserved, flags, payload, extension);
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaEnd(traceId, authorization);
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            delegate.onKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = HttpKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = HttpKafkaState.closeInitial(state);

            signaler.cancel(cancelWait);
            cancelWait = NO_CANCEL_ID;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                state = HttpKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }
    }

    private final class HttpProduceAsyncProxy extends HttpProxy
    {
        private final KafkaProduceProxy producer;
        private final KafkaCorrelateProxy correlater;
        private int producedFlags;

        private HttpProduceAsyncProxy(
            MessageConsumer http,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            HttpKafkaWithProduceResult resolved)
        {
            super(http, originId, routedId, initialId);
            this.producer = new KafkaProduceProxy(routedId, resolvedId, this, resolved);
            this.correlater = new KafkaCorrelateProxy(routedId, resolvedId, this, resolved);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onHttpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onHttpAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onHttpFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onHttpWindow(window);
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = HttpKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            producer.doKafkaBegin(traceId, authorization, affinity);

            Flyweight kafkaDataEx = emptyRO;
            if (httpBeginEx != null)
            {
                final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();

                int deferred = 0;
                final HttpHeaderFW contentLength = headers.matchFirst(h -> httpContentLength.equals(h.name()));
                if (contentLength != null)
                {
                    deferred = Integer.parseInt(contentLength.value().asString());
                }
                final int deferred0 = deferred;

                kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(mp -> mp
                            .deferred(deferred0)
                            .timestamp(now().toEpochMilli())
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(producer.resolved::key)
                            .headers(hs -> producer.resolved.headers(headers, hs))))
                        .build();

                producer.doKafkaData(traceId, authorization, 0L, 0, DATA_FLAG_INIT, emptyRO, kafkaDataEx);
                this.producedFlags |= DATA_FLAG_INIT;
            }
        }

        private void onHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            producer.resolved.updateHash(payload.value());

            producer.doKafkaData(traceId, authorization, budgetId, reserved, flags & ~producedFlags, payload, emptyRO);
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            producer.resolved.digestHash();

            correlater.doKafkaBegin(traceId, authorization, 0L);
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            producer.doKafkaAbort(traceId, authorization);
            correlater.doKafkaAbort(traceId, authorization);
        }

        protected void onHttpFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            producer.doKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaReset(traceId);
            correlater.doKafkaReset(traceId);
        }

        private void onHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;
            replyCap = capabilities;
            state = HttpKafkaState.openReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaWindow(traceId);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            doHttpReset(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            doHttpAbort(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            // nop
        }

        @Override
        protected void onKafkaData(
                long traceId,
                long authorization,
                long budgetId,
                int reserved,
                int flags,
                OctetsFW payload,
                OctetsFW extension)
        {
            if (!HttpKafkaState.initialClosing(producer.state))
            {
                Flyweight kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(mp -> mp
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .headers(producer.resolved::trailers)))
                        .build();

                producer.doKafkaData(traceId, authorization, 0L, 0, DATA_FLAG_INCOMPLETE, emptyRO, kafkaDataEx);

                producer.doKafkaEndDeferred(traceId, authorization);
            }
        }

        @Override
        protected void onKafkaFlush(
                long traceId,
                long authorization,
                long budgetId,
                int reserved)
        {
            if (!HttpKafkaState.initialClosing(producer.state))
            {
                Flyweight kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(mp -> mp
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .headers(producer.resolved::trailers)))
                        .build();

                producer.doKafkaData(traceId, authorization, 0L, 0, DATA_FLAG_FIN, emptyRO, kafkaDataEx);

                producer.doKafkaEndDeferred(traceId, authorization);
            }
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyOpening(state))
            {
                HttpBeginExFW httpBeginEx = httpBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headers(producer.resolved::async)
                        .build();

                doHttpBegin(traceId, authorization, 0L, httpBeginEx);

                state = HttpKafkaState.closingReply(state);
            }

            if (HttpKafkaState.initialClosed(state))
            {
                producer.doKafkaEnd(traceId, authorization);
                correlater.doKafkaEnd(traceId, authorization);
            }

            doHttpEnd(traceId, authorization);
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
        }

        private void doHttpAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = producer.replySeq;
                state = HttpKafkaState.closeReply(state);

                doAbort(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doHttpEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = producer.replySeq;
                state = HttpKafkaState.closeReply(state);

                doEnd(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doHttpWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = producer.initialAck;
            initialMax = producer.initialMax;

            doWindow(http, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            if (!HttpKafkaState.replyOpening(state))
            {
                HttpBeginExFW httpBeginEx = httpBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headers(producer.resolved::error)
                        .build();

                doHttpBegin(traceId, authorization, 0L, httpBeginEx);

                state = HttpKafkaState.closingReply(state);
            }
            doHttpEnd(traceId, authorization);
        }
    }

    private final class HttpProduceNoReplyProxy extends HttpProxy
    {
        private final KafkaProduceProxy delegate;

        private int producedFlags;

        private HttpProduceNoReplyProxy(
            MessageConsumer http,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            HttpKafkaWithProduceResult resolved)
        {
            super(http, originId, routedId, initialId);
            this.delegate = new KafkaProduceProxy(routedId, resolvedId, this, resolved);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onHttpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onHttpAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onHttpFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onHttpWindow(window);
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = HttpKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaBegin(traceId, authorization, affinity);

            Flyweight kafkaDataEx = emptyRO;
            if (httpBeginEx != null)
            {
                final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();

                int deferred = 0;

                final boolean produceNull = headers.anyMatch(httpMethodDelete::equals);
                final HttpHeaderFW contentLength = headers.matchFirst(h -> httpContentLength.equals(h.name()));
                if (!produceNull && contentLength != null)
                {
                    deferred = Integer.parseInt(contentLength.value().asString());
                }
                final int deferred0 = deferred;

                final HttpHeaderFW contentType = headers.matchFirst(h -> httpContentType.equals(h.name()));
                kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(mp -> mp
                            .deferred(deferred0)
                            .timestamp(now().toEpochMilli())
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(delegate.resolved::key)
                            .headers(hs -> delegate.resolved.headers(contentType, hs))))
                        .build();

                final int flags = produceNull ? DATA_FLAG_INIT | DATA_FLAG_FIN : DATA_FLAG_INIT;
                final OctetsFW payload = produceNull ? null : emptyRO;

                delegate.doKafkaData(traceId, authorization, 0L, 0, flags, payload, kafkaDataEx);

                this.producedFlags |= flags;
            }
        }

        private void onHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            delegate.doKafkaData(traceId, authorization, budgetId, reserved, flags & ~producedFlags, payload, emptyRO);
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            if ((producedFlags & DATA_FLAG_FIN) == 0x00)
            {
                delegate.doKafkaData(traceId, authorization, 0L, 0, DATA_FLAG_FIN, emptyRO, emptyRO);
            }

            delegate.doKafkaEndDeferred(traceId, authorization);
            doHttpErrorDeferred(traceId, authorization);
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaAbort(traceId, authorization);
        }

        protected void onHttpFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            delegate.doKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaReset(traceId);
        }

        private void onHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;
            replyCap = capabilities;
            state = HttpKafkaState.openReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaWindow(traceId);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            delegate.doKafkaReset(traceId);
            doHttpErrorDeferred(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            delegate.doKafkaAbort(traceId, authorization);
            doHttpAbort(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            delegate.doKafkaWindow(traceId);
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyOpening(state))
            {
                HttpBeginExFW httpBeginEx = httpBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headers(delegate.resolved::noreply)
                        .build();

                doHttpBegin(traceId, authorization, 0L, httpBeginEx);

                state = HttpKafkaState.closingReply(state);
            }

            if (HttpKafkaState.initialClosed(state))
            {
                delegate.doKafkaEnd(traceId, authorization);
            }

            doHttpEnd(traceId, authorization);
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
        }

        private void doHttpAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = HttpKafkaState.closeReply(state);

                doAbort(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doHttpEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = HttpKafkaState.closeReply(state);

                doEnd(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doHttpWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;

            doWindow(http, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpErrorDeferred(
                long traceId,
                long authorization)
        {
            if (HttpKafkaState.initialClosed(state) && HttpKafkaState.replyClosed(delegate.state))
            {
                if (!HttpKafkaState.replyOpening(state))
                {
                    HttpBeginExFW httpBeginEx = httpBeginExRW
                            .wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(httpTypeId)
                            .headers(delegate.resolved::error)
                            .build();

                    doHttpBegin(traceId, authorization, 0L, httpBeginEx);

                    state = HttpKafkaState.closingReply(state);
                }
                doHttpEnd(traceId, authorization);
            }
        }
    }

    private final class KafkaProduceProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final HttpKafkaWithProduceResult resolved;
        private final HttpProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int replyCap;

        private DirectBuffer deferredDataEx;
        private int deferredDataExFlags;
        private OctetsFW deferredPayload;

        private KafkaProduceProxy(
            long originId,
            long routedId,
            HttpProxy delegate,
            HttpKafkaWithProduceResult resolved)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = HttpKafkaState.openingInitial(state);

            kafka = newKafkaProducer(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            if (!HttpKafkaState.initialOpened(state))
            {
                assert payload == null || payload.sizeof() == 0;
                assert (flags & 0x02) != 0;

                if (extension.sizeof() > 0)
                {
                    // TODO: use buffer slot instead
                    final UnsafeBuffer buf = new UnsafeBuffer(new byte[extension.sizeof()]);
                    buf.putBytes(0, extension.buffer(), extension.offset(), extension.sizeof());
                    deferredDataEx = buf;
                    deferredPayload = payload;
                    deferredDataExFlags = flags;
                }
            }
            else
            {
                if (!HttpKafkaState.initialClosed(delegate.state) & payload != null)
                {
                    flags &= ~0x01;
                }

                if (deferredDataEx != null)
                {
                    flags &= ~0x02;
                    deferredDataEx = null;
                }

                flushKafkaData(traceId, authorization, budgetId, reserved, flags, payload, extension);
            }
        }

        private void flushKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaEndDeferred(
            long traceId,
            long authorization)
        {
            state = HttpKafkaState.closingInitial(state);
            doKafkaEndAck(traceId, authorization);
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpKafkaState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            initialSeq = delegate.initialSeq;

            doFlush(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = HttpKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            doKafkaWindow(traceId);
            delegate.onKafkaBegin(traceId, authorization, extension);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaEnd(traceId, authorization);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaAbort(traceId, authorization);

            doKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            if (initialMax == 0 &&
                maximum > 0 &&
                deferredDataEx != null)
            {
                final Flyweight kafkaDataEx = deferredDataEx != null
                        ? kafkaDataExRO.wrap(deferredDataEx, 0, deferredDataEx.capacity())
                        : emptyRO;

                flushKafkaData(traceId, authorization, budgetId, 0, deferredDataExFlags, deferredPayload, kafkaDataEx);
            }

            initialAck = acknowledge;
            initialMax = maximum;
            state = HttpKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);

            doKafkaEndAck(authorization, traceId);
        }

        private void doKafkaEndAck(long authorization, long traceId)
        {
            if (HttpKafkaState.initialClosing(state) && initialSeq == initialAck)
            {
                doKafkaEnd(traceId, authorization);
            }
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = HttpKafkaState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);

            doKafkaReset(traceId);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                state = HttpKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId);
            }
        }

        private void doKafkaWindow(
            long traceId)
        {
            if (kafka != null && !HttpKafkaState.replyClosed(state))
            {
                replyAck = delegate.replyAck;
                replyMax = delegate.replyMax;
                replyBud = delegate.replyBud;
                replyPad = delegate.replyPad;
                replyCap = delegate.replyCap;

                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, 0L, replyBud, replyPad, replyCap);
            }
        }
    }

    private final class HttpCorrelateAsyncProxy extends HttpProxy
    {
        private final KafkaCorrelateProxy delegate;

        private HttpCorrelateAsyncProxy(
            MessageConsumer http,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            HttpKafkaWithProduceResult resolved)
        {
            super(http, originId, routedId, initialId);
            this.delegate = new KafkaCorrelateProxy(routedId, resolvedId, this, resolved);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onHttpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onHttpAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onHttpWindow(window);
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = HttpKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaBegin(traceId, authorization, affinity);
        }

        private void onHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            delegate.initialSeq = initialSeq;
            delegate.initialAck = initialSeq;

            doHttpWindow(authorization, traceId, budgetId, 0, 0);
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            if (HttpKafkaState.replyClosed(delegate.state))
            {
                delegate.doKafkaEnd(traceId, authorization);
            }
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaAbort(traceId, authorization);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaReset(traceId);
        }

        private void onHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;
            replyCap = capabilities;
            state = HttpKafkaState.openReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaWindow(traceId);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            doHttpReset(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyOpening(state))
            {
                doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
            }

            doHttpAbort(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            // nop
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            OctetsFW extension)
        {
            if (!HttpKafkaState.replyClosing(state))
            {
                if ((flags & 0x02) != 0x00) // INIT
                {
                    Flyweight httpBeginEx = emptyRO;

                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                            dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;

                    if (kafkaDataEx != null)
                    {
                        final KafkaMergedFetchDataExFW kafkaMergedFetchDataEx = kafkaDataEx.merged().fetch();
                        final Array32FW<KafkaHeaderFW> kafkaHeaders = kafkaMergedFetchDataEx.headers();
                        final int contentLength = payload != null ? payload.sizeof() + kafkaMergedFetchDataEx.deferred() : 0;

                        httpBeginEx = httpBeginExRW
                                .wrap(extBuffer, 0, extBuffer.capacity())
                                .typeId(httpTypeId)
                                .headers(hs -> delegate.resolved.correlated(kafkaHeaders, hs, contentLength))
                                .build();
                    }

                    doHttpBegin(traceId, authorization, 0L, httpBeginEx);
                }

                if (HttpKafkaState.replyOpening(state) && payload != null)
                {
                    // TODO: await http response window if necessary (handle in doHttpData)
                    doHttpData(traceId, authorization, budgetId, reserved, flags, payload);
                }

                if ((flags & 0x01) != 0x00) // FIN
                {
                    delegate.doKafkaEnd(traceId, authorization);
                    state = HttpKafkaState.closingReply(state);
                }
            }
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyOpening(state))
            {
                HttpBeginExFW httpBeginEx = httpBeginExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(httpTypeId)
                    .headers(delegate.resolved::async)
                    .build();

                doHttpBegin(traceId, authorization, 0L, httpBeginEx);
            }

            if (HttpKafkaState.initialClosed(state))
            {
                delegate.doKafkaEnd(traceId, authorization);
            }

            doHttpEnd(traceId, authorization);
        }

        @Override
        protected void onKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doHttpFlush(traceId, authorization, budgetId, reserved);
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                state = HttpKafkaState.openingReply(state);

                doBegin(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, affinity, extension);
            }
        }

        private void doHttpAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = HttpKafkaState.closeReply(state);

                doAbort(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doHttpData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            doData(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, emptyRO);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doHttpEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = HttpKafkaState.closeReply(state);

                doEnd(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doHttpFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = delegate.replySeq;

            doFlush(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doHttpWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;

            doWindow(http, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            if (!HttpKafkaState.replyOpening(state))
            {
                HttpBeginExFW httpBeginEx = httpBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headers(delegate.resolved::error)
                        .build();

                doHttpBegin(traceId, authorization, 0L, httpBeginEx);

                state = HttpKafkaState.closingReply(state);
            }
            doHttpEnd(traceId, authorization);
        }
    }

    private final class KafkaCorrelateProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final HttpKafkaWithProduceResult resolved;
        private final HttpProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;
        private int replyCap;

        private long cancelWait = NO_CANCEL_ID;

        private KafkaCorrelateProxy(
            long originId,
            long routedId,
            HttpProxy delegate,
            HttpKafkaWithProduceResult resolved)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialMax = delegate.initialMax;
            state = HttpKafkaState.openingInitial(state);

            kafka = newKafkaCorrelater(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);

            final long timeout = resolved.timeout();
            if (timeout > 0L)
            {
                cancelWait = signaler.signalAt(now().toEpochMilli() + timeout, originId, routedId, initialId,
                        traceId, SIGNAL_WAIT_EXPIRED, 0);
            }
            doKafkaWindow(traceId);
        }

        private void doKafkaEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpKafkaState.closeInitial(state);

                signaler.cancel(cancelWait);
                cancelWait = NO_CANCEL_ID;

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state) && kafka != null)
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpKafkaState.closeInitial(state);

                signaler.cancel(cancelWait);
                cancelWait = NO_CANCEL_ID;

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onKafkaSignal(signal);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = HttpKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaBegin(traceId, authorization, extension);
            doKafkaWindow(traceId);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            signaler.cancel(cancelWait);
            cancelWait = NO_CANCEL_ID;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                delegate.onKafkaReset(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();

                delegate.onKafkaData(traceId, authorization, budgetId, reserved, flags, payload, extension);
            }
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaEnd(traceId, authorization);
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            delegate.onKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.onKafkaAbort(traceId, authorization);

            doKafkaAbort(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = HttpKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.onKafkaWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = HttpKafkaState.closeInitial(state);

            signaler.cancel(cancelWait);
            cancelWait = NO_CANCEL_ID;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.onKafkaReset(traceId, authorization);

            doKafkaReset(traceId);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, authorization);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!HttpKafkaState.replyClosed(state) && kafka != null)
            {
                state = HttpKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId);
            }
        }

        private void doKafkaWindow(
            long traceId)
        {
            if (kafka != null)
            {
                replyAck = delegate.replyAck;
                replyMax = delegate.replyMax;
                replyBud = delegate.replyBud;
                replyPad = delegate.replyPad;
                replyCap = delegate.replyCap;

                doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, 0L, replyBud, replyPad, replyCap);
            }
        }
    }

    private final class HttpProduceSyncProxy extends HttpProxy
    {
        private final KafkaProduceProxy producer;
        private final KafkaCorrelateProxy correlater;
        private int producedFlags;

        private HttpProduceSyncProxy(
            MessageConsumer http,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            HttpKafkaWithProduceResult resolved)
        {
            super(http, originId, routedId, initialId);
            this.producer = new KafkaProduceProxy(routedId, resolvedId, this, resolved);
            this.correlater = new KafkaCorrelateProxy(routedId, resolvedId, this, resolved);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onHttpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onHttpAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onHttpFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onHttpWindow(window);
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = HttpKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            producer.doKafkaBegin(traceId, authorization, affinity);

            Flyweight kafkaDataEx = emptyRO;
            if (httpBeginEx != null)
            {
                final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();

                int deferred = 0;
                final HttpHeaderFW contentLength = headers.matchFirst(h -> httpContentLength.equals(h.name()));
                if (contentLength != null)
                {
                    deferred = Integer.parseInt(contentLength.value().asString());
                }
                final int deferred0 = deferred;

                kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(mp -> mp
                            .deferred(deferred0)
                            .timestamp(now().toEpochMilli())
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(producer.resolved::key)
                            .headers(hs -> producer.resolved.headers(headers, hs))))
                        .build();

                producer.doKafkaData(traceId, authorization, 0L, 0, DATA_FLAG_INIT, emptyRO, kafkaDataEx);
                this.producedFlags |= DATA_FLAG_INIT;
            }
        }

        private void onHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            producer.resolved.updateHash(payload.value());

            producer.doKafkaData(traceId, authorization, budgetId, reserved, flags & ~producedFlags, payload, emptyRO);
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            producer.resolved.digestHash();

            correlater.doKafkaBegin(traceId, authorization, 0L);
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = HttpKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            producer.doKafkaAbort(traceId, authorization);
            correlater.doKafkaAbort(traceId, authorization);
        }

        protected void onHttpFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            producer.doKafkaFlush(traceId, authorization, budgetId, reserved);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = HttpKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaReset(traceId);
            correlater.doKafkaReset(traceId);
        }

        private void onHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = budgetId;
            replyPad = padding;
            replyCap = capabilities;
            state = HttpKafkaState.openReply(state);

            assert replyAck <= replySeq;

            producer.doKafkaWindow(traceId);
            correlater.doKafkaWindow(traceId);
        }

        @Override
        protected void onKafkaReset(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        @Override
        protected void onKafkaAbort(
            long traceId,
            long authorization)
        {
            onKafkaError(traceId, authorization);
        }

        @Override
        protected void onKafkaBegin(
            long traceId,
            long authorization, OctetsFW extension)
        {
            // nop
        }

        @Override
        protected void onKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            OctetsFW extension)
        {
            if (HttpKafkaState.replyClosing(state))
            {
                replySeq += reserved;

                correlater.doKafkaWindow(traceId);
            }
            else
            {
                if ((flags & 0x02) != 0x00) // INIT
                {
                    Flyweight httpBeginEx = emptyRO;

                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                            dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;


                    if (kafkaDataEx != null)
                    {
                        final KafkaMergedFetchDataExFW kafkaMergedFetchDataEx = kafkaDataEx.merged().fetch();
                        final Array32FW<KafkaHeaderFW> kafkaHeaders = kafkaMergedFetchDataEx.headers();
                        final int contentLength = payload != null ? payload.sizeof() + kafkaMergedFetchDataEx.deferred() : 0;

                        httpBeginEx = httpBeginExRW
                                .wrap(extBuffer, 0, extBuffer.capacity())
                                .typeId(httpTypeId)
                                .headers(hs -> correlater.resolved.correlated(kafkaHeaders, hs, contentLength))
                                .build();
                    }

                    doHttpBegin(traceId, authorization, 0L, httpBeginEx);
                }

                if (HttpKafkaState.replyOpening(state) && payload != null)
                {
                    // TODO: await http response window if necessary (handle in doHttpData)
                    doHttpData(traceId, authorization, budgetId, reserved, flags, payload);
                }

                if (!HttpKafkaState.initialClosing(producer.state))
                {
                    Flyweight kafkaDataEx = kafkaDataExRW
                            .wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(kafkaTypeId)
                            .merged(m -> m.produce(mp -> mp
                                .partition(p -> p.partitionId(-1).partitionOffset(-1))
                                .headers(producer.resolved::trailers)))
                            .build();

                    producer.doKafkaData(traceId, authorization, 0L, 0, DATA_FLAG_INCOMPLETE, emptyRO, kafkaDataEx);

                    producer.doKafkaEndDeferred(traceId, authorization);
                }

                if ((flags & 0x01) != 0x00) // FIN
                {
                    correlater.doKafkaEnd(traceId, authorization);
                    state = HttpKafkaState.closingReply(state);
                }
            }
        }

        @Override
        protected void onKafkaEnd(
            long traceId,
            long authorization)
        {
            if (HttpKafkaState.replyClosed(correlater.state))
            {
                if (!HttpKafkaState.replyOpening(state))
                {
                    doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
                }

                if (HttpKafkaState.initialClosed(state))
                {
                    producer.doKafkaEnd(traceId, authorization);
                    correlater.doKafkaEnd(traceId, authorization);
                }

                doHttpEnd(traceId, authorization);
            }
        }

        @Override
        protected void onKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            if (!HttpKafkaState.initialClosing(producer.state))
            {
                Flyweight kafkaDataEx = kafkaDataExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.produce(mp -> mp
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .headers(producer.resolved::trailers)))
                        .build();

                producer.doKafkaData(traceId, authorization, 0L, 0, DATA_FLAG_FIN, emptyRO, kafkaDataEx);

                producer.doKafkaEndDeferred(traceId, authorization);
            }
            else
            {
                doHttpFlush(traceId, authorization, budgetId, reserved);
            }
        }

        @Override
        protected void onKafkaWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaError(
            long traceId,
            long authorization)
        {
            doHttpReset(traceId, authorization);
            doHttpAbort(traceId, authorization);

            producer.doKafkaAbort(traceId, authorization);
            producer.doKafkaReset(traceId);
            correlater.doKafkaAbort(traceId, authorization);
            correlater.doKafkaReset(traceId);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
        }

        private void doHttpData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload)
        {
            doData(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, emptyRO);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doHttpAbort(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = correlater.replySeq;
                state = HttpKafkaState.closeReply(state);

                doAbort(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doHttpEnd(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                replySeq = correlater.replySeq;
                state = HttpKafkaState.closeReply(state);

                doEnd(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doHttpFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = correlater.replySeq;

            doFlush(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doHttpWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = producer.initialAck;
            initialMax = producer.initialMax;

            doWindow(http, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            if (!HttpKafkaState.replyOpening(state))
            {
                HttpBeginExFW httpBeginEx = httpBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headers(producer.resolved::error)
                        .build();

                doHttpBegin(traceId, authorization, 0L, httpBeginEx);

                state = HttpKafkaState.closingReply(state);
            }
            doHttpEnd(traceId, authorization);
        }
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
        long budgetId,
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
                .payload(payload)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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
        int reserved)
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
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private MessageConsumer newKafkaFetcher(
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
        HttpKafkaWithFetchResult resolved)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(FETCH_ONLY))
                              .topic(resolved.topic())
                              .partitions(resolved::partitions)
                              .filters(resolved::filters))
                .build();

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
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaProducer(
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
        HttpKafkaWithProduceResult resolved)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(PRODUCE_ONLY))
                              .topic(resolved.topic())
                              .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                              .ackMode(resolved::acks))
                .build();

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
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private MessageConsumer newKafkaCorrelater(
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
        HttpKafkaWithProduceResult resolved)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(FETCH_ONLY))
                              .topic(resolved.replyTo())
                              .partitions(resolved::partitions)
                              .filters(resolved::filters))
                .build();

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
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doWindow(
        MessageConsumer sender,
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

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private HttpBeginExFW initHttpBeginEx(
        String status)
    {
        return new HttpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":status").value(status))
            .headersItem(h -> h.name("content-length").value("0"))
            .build();
    }

    private HttpHeaderFW initHttpHeader(
        String name,
        String value)
    {
        return new HttpHeaderFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .name(name).value(value)
            .build();
    }
}
