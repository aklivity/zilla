/*
 * Copyright 2021-2022 Aklivity Inc
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
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.OctetsFW;
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
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.KafkaMergedDataExFW;
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

    private final OctetsFW emptyExRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

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
        this.httpContentLength = new String8FW("content-length");
        this.httpContentType = new String8FW("content-type");
        this.httpEtag = new String8FW("etag");
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
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        final HttpKafkaBindingConfig binding = bindings.get(routeId);

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
                final HttpKafkaWithFetchResult resolved = route.with.resolveFetch(httpBeginEx);

                newStream = new HttpFetchProxy(http, routeId, initialId, resolvedId, resolved)::onHttpMessage;
                break;
            }
            case PRODUCE:
            {
                final HttpKafkaWithProduceResult resolved = route.with.resolveProduce(httpBeginEx);

                if (resolved.correlated())
                {
                    newStream = new HttpCorrelateAsyncProxy(http, routeId, initialId, resolvedId, resolved)::onHttpMessage;
                }
                else if (resolved.async())
                {
                    newStream = new HttpProduceAsyncProxy(http, routeId, initialId, resolvedId, resolved)::onHttpMessage;
                }
                else
                {
                    // TODO: HttpProduceSync
                }
                break;
            }
            }
        }

        return newStream;
    }

    private final class HttpFetchProxy
    {
        private final MessageConsumer http;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final KafkaFetchProxy delegate;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private int state;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private HttpFetchProxy(
            MessageConsumer http,
            long routeId,
            long initialId,
            long resolvedId,
            HttpKafkaWithFetchResult resolved)
        {
            this.http = http;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new KafkaFetchProxy(resolvedId, this, resolved);
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
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            delegate.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload);
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
                delegate.doKafkaEnd(traceId, sequence, authorization);
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

            delegate.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, routeId, replyId, replySeq, replyAck, replyMax,
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

                doAbort(http, routeId, replyId, replySeq, replyAck, replyMax,
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
            doData(http, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, emptyExRO);

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

                doEnd(http, routeId, replyId, replySeq, replyAck, replyMax,
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

            doFlush(http, routeId, replyId, replySeq, replyAck, replyMax,
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

            doWindow(http, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    private final class KafkaFetchProxy
    {
        private MessageConsumer kafka;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final HttpKafkaWithFetchResult resolved;
        private final HttpFetchProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long cancelWait = NO_CANCEL_ID;

        private KafkaFetchProxy(
            long routeId,
            HttpFetchProxy delegate,
            HttpKafkaWithFetchResult resolved)
        {
            this.routeId = routeId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routeId);
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

            kafka = newKafkaStream(this::onKafkaMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);

            final long timeout = resolved.timeout();
            if (timeout > 0L)
            {
                cancelWait = signaler.signalAt(now().toEpochMilli() + timeout, routeId, initialId, SIGNAL_WAIT_EXPIRED, 0);
            }
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            Flyweight payload)
        {
            delegate.doHttpReset(traceId);
            doKafkaAbort(traceId, authorization);
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
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

                doEnd(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
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

                doAbort(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = delegate.replySeq;

            doFlush(kafka, routeId, replyId, replySeq, replyAck, replyMax,
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

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = HttpKafkaState.openingReply(state);

            assert replyAck <= replySeq;
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
                delegate.doHttpAbort(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();

                if ((flags & 0x02) != 0x00) // INIT
                {
                    Flyweight httpBeginEx = emptyExRO;

                    final OctetsFW extension = data.extension();
                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                            dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;

                    if (payload == null)
                    {
                        httpBeginEx = httpBeginEx404;
                    }
                    else if (kafkaDataEx != null)
                    {
                        final KafkaMergedDataExFW kafkaMergedDataEx = kafkaDataEx.merged();
                        final int contentLength = payload.sizeof() + kafkaMergedDataEx.deferred();

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

                        final Array32FW<KafkaHeaderFW> headers = kafkaMergedDataEx.headers();

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
                            final String8FW progress64 = etagHelper.encode(kafkaMergedDataEx.progress());
                            String httpEtag = String.format("%s/%s",
                                progress64.asString(),
                                etag.value().value().getStringWithoutLengthAscii(0, etag.valueLen()));
                            builder.headersItem(h -> h
                                .name(etag.name().value(), 0, etag.nameLen())
                                .value(httpEtag));
                        }
                        else
                        {
                            final String8FW progress64 = etagHelper.encode(kafkaMergedDataEx.progress());

                            builder.headersItem(h -> h
                                .name(httpEtag.value(), 0, httpEtag.length())
                                .value(progress64.value(), 0, progress64.length()));
                        }

                        httpBeginEx = builder.build();
                    }

                    delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx);
                }

                if (HttpKafkaState.replyOpening(delegate.state) && payload != null)
                {
                    // TODO: await http response window if necessary (handle in doHttpData)
                    delegate.doHttpData(traceId, authorization, budgetId, reserved, flags, payload);
                }

                if ((flags & 0x01) != 0x00) // FIN
                {
                    doKafkaEnd(traceId, sequence, authorization);
                    delegate.doHttpEnd(traceId, authorization);
                }
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

            if (!HttpKafkaState.replyOpening(delegate.state))
            {
                HttpBeginExFW httpBeginEx = httpBeginEx404;

                final String etag = resolved.etag();
                if (etag != null)
                {
                    httpBeginEx = httpBeginExRW
                        .wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headersItem(h -> h.set(httpStatus304))
                        .headersItem(h -> h.name(httpEtag).value(etag))
                        .build();
                }

                delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx);
            }

            if (HttpKafkaState.initialClosed(delegate.state))
            {
                doKafkaEnd(traceId, sequence, authorization);
            }

            delegate.doHttpEnd(traceId, authorization);
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

            delegate.doHttpFlush(traceId, authorization, budgetId, reserved);
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

            if (!HttpKafkaState.replyOpening(delegate.state))
            {
                delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
            }

            delegate.doHttpAbort(traceId, authorization);
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

            delegate.doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = HttpKafkaState.closeInitial(state);

            signaler.cancel(cancelWait);
            cancelWait = NO_CANCEL_ID;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doHttpReset(traceId);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long sequence = signal.sequence();
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, sequence, authorization);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                state = HttpKafkaState.closeReply(state);

                doReset(kafka, routeId, replyId, replySeq, replyAck, replyMax,
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

            doWindow(kafka, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }
    }

    private final class HttpProduceAsyncProxy
    {
        private final MessageConsumer http;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final KafkaProduceProxy delegate;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private int state;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private HttpProduceAsyncProxy(
            MessageConsumer http,
            long routeId,
            long initialId,
            long resolvedId,
            HttpKafkaWithProduceResult resolved)
        {
            this.http = http;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new KafkaProduceProxy(resolvedId, this, resolved);
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

            Flyweight kafkaDataEx = emptyExRO;
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
                        .merged(m -> m
                            .deferred(deferred0)
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(delegate.resolved::key)
                            .headers(hs -> delegate.resolved.headers(headers, hs)))
                        .build();
            }

            delegate.doKafkaData(traceId, authorization, 0L, 0, 0x02, emptyExRO, kafkaDataEx);
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

            delegate.doKafkaData(traceId, authorization, budgetId, reserved, flags & ~0x02, payload, emptyExRO);
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

            delegate.doKafkaEnd(traceId, sequence, authorization);

            HttpBeginExFW httpBeginEx = httpBeginExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(httpTypeId)
                    .headers(delegate.resolved::async)
                    .build();

            doHttpBegin(traceId, authorization, 0L, httpBeginEx);
            doHttpEnd(traceId, authorization);
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

            delegate.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, routeId, replyId, replySeq, replyAck, replyMax,
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

                doAbort(http, routeId, replyId, replySeq, replyAck, replyMax,
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

                doEnd(http, routeId, replyId, replySeq, replyAck, replyMax,
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

            doFlush(http, routeId, replyId, replySeq, replyAck, replyMax,
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

            doWindow(http, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    private final class KafkaProduceProxy
    {
        private MessageConsumer kafka;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final HttpKafkaWithProduceResult resolved;
        private final HttpProduceAsyncProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaProduceProxy(
            long routeId,
            HttpProduceAsyncProxy delegate,
            HttpKafkaWithProduceResult resolved)
        {
            this.routeId = routeId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routeId);
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

            kafka = newKafkaStream(this::onKafkaMessage, routeId, initialId, initialSeq, initialAck, initialMax,
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
            doData(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
            long authorization)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpKafkaState.closeInitial(state);

                doEnd(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
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

                doAbort(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = delegate.replySeq;

            doFlush(kafka, routeId, replyId, replySeq, replyAck, replyMax,
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

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = HttpKafkaState.openingReply(state);

            assert replyAck <= replySeq;
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            doKafkaReset(traceId);
            delegate.doHttpAbort(traceId, authorization);
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

            if (!HttpKafkaState.replyOpening(delegate.state))
            {
                delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
            }

            if (HttpKafkaState.initialClosed(delegate.state))
            {
                doKafkaEnd(traceId, sequence, authorization);
            }

            delegate.doHttpEnd(traceId, authorization);
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

            delegate.doHttpFlush(traceId, authorization, budgetId, reserved);
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

            if (!HttpKafkaState.replyOpening(delegate.state))
            {
                delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
            }

            delegate.doHttpAbort(traceId, authorization);
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

            delegate.doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = HttpKafkaState.closeInitial(state);

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doHttpReset(traceId);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long sequence = signal.sequence();
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, sequence, authorization);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                state = HttpKafkaState.closeReply(state);

                doReset(kafka, routeId, replyId, replySeq, replyAck, replyMax,
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

            doWindow(kafka, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }
    }

    private final class HttpCorrelateAsyncProxy
    {
        private final MessageConsumer http;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final KafkaCorrelateProxy delegate;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private int state;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private HttpCorrelateAsyncProxy(
            MessageConsumer http,
            long routeId,
            long initialId,
            long resolvedId,
            HttpKafkaWithProduceResult resolved)
        {
            this.http = http;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new KafkaCorrelateProxy(resolvedId, this, resolved);
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
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            delegate.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload);
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
                delegate.doKafkaEnd(traceId, sequence, authorization);
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

            delegate.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            state = HttpKafkaState.openingReply(state);

            doBegin(http, routeId, replyId, replySeq, replyAck, replyMax,
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

                doAbort(http, routeId, replyId, replySeq, replyAck, replyMax,
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
            doData(http, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, emptyExRO);

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

                doEnd(http, routeId, replyId, replySeq, replyAck, replyMax,
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

            doFlush(http, routeId, replyId, replySeq, replyAck, replyMax,
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

            doWindow(http, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpReset(
            long traceId)
        {
            if (!HttpKafkaState.initialClosed(state))
            {
                state = HttpKafkaState.closeInitial(state);

                doReset(http, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    private final class KafkaCorrelateProxy
    {
        private MessageConsumer kafka;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final HttpKafkaWithProduceResult resolved;
        private final HttpCorrelateAsyncProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private long cancelWait = NO_CANCEL_ID;

        private KafkaCorrelateProxy(
            long routeId,
            HttpCorrelateAsyncProxy delegate,
            HttpKafkaWithProduceResult resolved)
        {
            this.routeId = routeId;
            this.delegate = delegate;
            this.resolved = resolved;
            this.initialId = supplyInitialId.applyAsLong(routeId);
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

            kafka = newKafkaStream(this::onKafkaMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);

            final long timeout = resolved.timeout();
            if (timeout > 0L)
            {
                cancelWait = signaler.signalAt(now().toEpochMilli() + timeout, routeId, initialId, SIGNAL_WAIT_EXPIRED, 0);
            }
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            Flyweight payload)
        {
            delegate.doHttpReset(traceId);
            doKafkaAbort(traceId, authorization);
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
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

                doEnd(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
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

                doAbort(kafka, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = delegate.replySeq;

            doFlush(kafka, routeId, replyId, replySeq, replyAck, replyMax,
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

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = HttpKafkaState.openingReply(state);

            assert replyAck <= replySeq;
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
                delegate.doHttpAbort(traceId, authorization);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();

                if ((flags & 0x02) != 0x00) // INIT
                {
                    Flyweight httpBeginEx = emptyExRO;

                    final OctetsFW extension = data.extension();
                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                            dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;

                    if (kafkaDataEx != null)
                    {
                        final KafkaMergedDataExFW kafkaMergedDataEx = kafkaDataEx.merged();
                        final Array32FW<KafkaHeaderFW> kafkaHeaders = kafkaMergedDataEx.headers();

                        httpBeginEx = httpBeginExRW
                                .wrap(extBuffer, 0, extBuffer.capacity())
                                .typeId(httpTypeId)
                                .headers(hs -> resolved.correlated(kafkaHeaders, hs))
                                .build();
                    }

                    delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx);
                }

                if (HttpKafkaState.replyOpening(delegate.state) && payload != null)
                {
                    // TODO: await http response window if necessary (handle in doHttpData)
                    delegate.doHttpData(traceId, authorization, budgetId, reserved, flags, payload);
                }

                if ((flags & 0x01) != 0x00) // FIN
                {
                    doKafkaEnd(traceId, sequence, authorization);
                    delegate.doHttpEnd(traceId, authorization);
                }
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

            if (!HttpKafkaState.replyOpening(delegate.state))
            {
                HttpBeginExFW httpBeginEx = httpBeginExRW
                    .wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(httpTypeId)
                    .headers(resolved::async)
                    .build();

                delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx);
            }

            if (HttpKafkaState.initialClosed(delegate.state))
            {
                doKafkaEnd(traceId, sequence, authorization);
            }

            delegate.doHttpEnd(traceId, authorization);
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

            delegate.doHttpFlush(traceId, authorization, budgetId, reserved);
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

            if (!HttpKafkaState.replyOpening(delegate.state))
            {
                delegate.doHttpBegin(traceId, authorization, 0L, httpBeginEx500);
            }

            delegate.doHttpAbort(traceId, authorization);
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

            delegate.doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;
            state = HttpKafkaState.closeInitial(state);

            signaler.cancel(cancelWait);
            cancelWait = NO_CANCEL_ID;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doHttpReset(traceId);
        }

        private void onKafkaSignal(
            SignalFW signal)
        {
            final long sequence = signal.sequence();
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            doKafkaEnd(traceId, sequence, authorization);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!HttpKafkaState.replyClosed(state))
            {
                state = HttpKafkaState.closeReply(state);

                doReset(kafka, routeId, replyId, replySeq, replyAck, replyMax,
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

            doWindow(kafka, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, capabilities);
        }
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
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
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
                .payload(payload)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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

    private MessageConsumer newKafkaStream(
        MessageConsumer sender,
        long routeId,
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
                .routeId(routeId)
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

    private MessageConsumer newKafkaStream(
        MessageConsumer sender,
        long routeId,
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
                .merged(m -> m.capabilities(c -> c.set(resolved.correlated() ? FETCH_ONLY : PRODUCE_ONLY))
                              .topic(resolved.correlated() ? resolved.replyTo() : resolved.topic())
                              .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                              .filters(resolved::filters))
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
                .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
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

    private HttpBeginExFW initHttpBeginEx(
        String status)
    {
        return new HttpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":status").value(status))
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
