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
package io.aklivity.zilla.runtime.binding.http.filesystem.internal.stream;

import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.filesystem.internal.HttpFileSystemConfiguration;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.config.HttpFileSystemBindingConfig;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.config.HttpFileSystemRouteConfig;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.config.HttpFileSystemWithResult;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.FileSystemCapabilities;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.FileSystemBeginExFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.http.filesystem.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class HttpFileSystemProxyFactory implements HttpFileSystemStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String FILE_SYSTEM_TYPE_NAME = "filesystem";

    private static final String8FW HEADER_STATUS_NAME = new String8FW(":status");
    private static final String16FW HEADER_STATUS_VALUE_200 = new String16FW("200");
    private static final String16FW HEADER_STATUS_VALUE_304 = new String16FW("304");
    private static final String8FW HEADER_ETAG_NAME = new String8FW("Etag");
    private static final String8FW HEADER_CONTENT_TYPE_NAME = new String8FW("content-type");
    private static final String8FW HEADER_CONTENT_LENGTH_NAME = new String8FW("content-length");
    private static final int READ_PAYLOAD_MASK = 1 << FileSystemCapabilities.READ_PAYLOAD.ordinal();

    private static final Predicate<HttpHeaderFW> HEADER_METHOD_GET_OR_HEAD;

    static
    {
        HttpHeaderFW headerMethodGet = new HttpHeaderFW.Builder()
                .wrap(new UnsafeBuffer(new byte[512]), 0, 512)
                .name(":method")
                .value("GET")
                .build();

        HttpHeaderFW headerMethodHead = new HttpHeaderFW.Builder()
                .wrap(new UnsafeBuffer(new byte[512]), 0, 512)
                .name(":method")
                .value("HEAD")
                .build();

        Predicate<HttpHeaderFW> test = headerMethodGet::equals;
        test = test.or(headerMethodHead::equals);
        HEADER_METHOD_GET_OR_HEAD = test;
    }

    private final OctetsFW emptyExRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

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

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    private final FileSystemBeginExFW fsBeginExRO = new FileSystemBeginExFW();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();

    private final FileSystemBeginExFW.Builder fsBeginExRW = new FileSystemBeginExFW.Builder();
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int fsTypeId;

    private final Long2ObjectHashMap<HttpFileSystemBindingConfig> bindings;

    public HttpFileSystemProxyFactory(
        HttpFileSystemConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.fsTypeId = context.supplyTypeId(FILE_SYSTEM_TYPE_NAME);
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
        HttpFileSystemBindingConfig httpFileSystemBinding = new HttpFileSystemBindingConfig(binding);
        bindings.put(binding.id, httpFileSystemBinding);
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
        final HttpBeginExFW beginEx = extension.get(httpBeginExRO::tryWrap);

        final HttpFileSystemBindingConfig binding = bindings.get(routedId);

        HttpFileSystemRouteConfig route = null;

        if (binding != null && beginEx.headers().anyMatch(HEADER_METHOD_GET_OR_HEAD))
        {
            route = binding.resolve(authorization, beginEx);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {
            final long resolvedId = route.id;
            final HttpFileSystemWithResult resolved = route.with
                    .map(r -> r.resolve(beginEx))
                    .orElse(null);

            newStream = new HttpProxy(
                    http,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    resolved)::onHttpMessage;
        }

        return newStream;
    }

    private final class HttpProxy
    {
        private final MessageConsumer http;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final FileSystemProxy delegate;
        private final HttpFileSystemWithResult resolved;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private HttpProxy(
            MessageConsumer sse,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            HttpFileSystemWithResult resolved)
        {
            this.http = sse;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new FileSystemProxy(routedId, resolvedId, this);
            this.resolved = resolved;
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
            state = HttpFileSystemState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doFileSystemBegin(traceId, authorization, affinity, resolved);
        }

        private void onHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            doHttpReset(traceId);
            delegate.doFileSystemAbort(traceId, authorization);
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
            state = HttpFileSystemState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doFileSystemEnd(traceId, initialSeq, authorization);
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
            state = HttpFileSystemState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doFileSystemAbort(traceId, authorization);
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
            state = HttpFileSystemState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doFileSystemReset(traceId);
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
            state = HttpFileSystemState.openReply(state);

            assert replyAck <= replySeq;

            delegate.doFileSystemWindow(traceId, authorization, budgetId, padding, capabilities);
        }

        private void doHttpBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            replySeq = delegate.replySeq;
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;
            state = HttpFileSystemState.openingReply(state);

            doBegin(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
        }

        private void doHttpData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            Flyweight payload)
        {
            doData(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
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

        private void doHttpAbort(
            long traceId,
            long authorization)
        {
            if (!HttpFileSystemState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = HttpFileSystemState.closeReply(state);

                doAbort(http, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doHttpEnd(
            long traceId,
            long authorization)
        {
            if (!HttpFileSystemState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = HttpFileSystemState.closeReply(state);

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

        private void doHttpReset(
            long traceId)
        {
            if (!HttpFileSystemState.initialClosed(state))
            {
                state = HttpFileSystemState.closeInitial(state);

                doReset(http, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    final class FileSystemProxy
    {
        private MessageConsumer filesystem;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final HttpProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private FileSystemProxy(
            long originId,
            long routedId,
            HttpProxy delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doFileSystemBegin(
            long traceId,
            long authorization,
            long affinity,
            HttpFileSystemWithResult resolved)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = HttpFileSystemState.openingInitial(state);

            filesystem = newFileSystemStream(this::onFileSystemMessage,
                    originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);
        }

        private void doFileSystemEnd(
            long traceId,
            long sequence,
            long authorization)
        {
            if (!HttpFileSystemState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpFileSystemState.closeInitial(state);

                doEnd(filesystem, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doFileSystemAbort(
            long traceId,
            long authorization)
        {
            if (!HttpFileSystemState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = HttpFileSystemState.closeInitial(state);

                doAbort(filesystem, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void onFileSystemMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onFileSystemBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onFileSystemData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onFileSystemEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onFileSystemAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onFileSystemFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onFileSystemWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onFileSystemReset(reset);
                break;
            }
        }

        private void onFileSystemBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long affinity = begin.affinity();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = HttpFileSystemState.openingReply(state);

            assert replyAck <= replySeq;

            final OctetsFW extension = begin.extension();
            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            final FileSystemBeginExFW fsBeginEx =
                    dataEx != null && dataEx.typeId() == fsTypeId ? extension.get(fsBeginExRO::tryWrap) : null;
            final String length = fsBeginEx != null ? Long.toString(fsBeginEx.payloadSize()) : null;
            final String16FW type = fsBeginEx != null ? fsBeginEx.type() : null;
            final String16FW tag = fsBeginEx != null ? fsBeginEx.tag() : null;
            Flyweight httpBeginEx = emptyExRO;
            if (fsBeginEx != null)
            {
                final HttpBeginExFW.Builder httpBeginExBuilder =
                    httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(httpTypeId)
                        .headersItem(h -> h.name(HEADER_STATUS_NAME).value(getStatus(fsBeginEx)))
                        .headersItem(h -> h.name(HEADER_CONTENT_TYPE_NAME).value(type))
                        .headersItem(h -> h.name(HEADER_CONTENT_LENGTH_NAME).value(length));
                if (tag.length() != -1 && tag.asString() != null)
                {
                    httpBeginExBuilder.headersItem(h -> h.name(HEADER_ETAG_NAME).value(tag));
                }
                httpBeginEx = httpBeginExBuilder.build();
            }

            delegate.doHttpBegin(traceId, authorization, affinity, httpBeginEx);
        }

        private String16FW getStatus(
            FileSystemBeginExFW fsBeginEx)
        {
            if (fsBeginEx.tag().length() == -1)
            {
                return HEADER_STATUS_VALUE_200;
            }
            return canReadPayload(fsBeginEx.capabilities()) ? HEADER_STATUS_VALUE_200 : HEADER_STATUS_VALUE_304;
        }

        private boolean canReadPayload(
            int capabilities)
        {
            return (capabilities & READ_PAYLOAD_MASK) != 0;
        }

        private void onFileSystemData(
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
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doFileSystemReset(traceId);
                delegate.doHttpAbort(traceId, authorization);
            }
            else
            {
                delegate.doHttpData(traceId, authorization, budgetId, reserved, flags, payload);
            }
        }

        private void onFileSystemEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpFileSystemState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doHttpEnd(traceId, authorization);
        }

        private void onFileSystemFlush(
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

        private void onFileSystemAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = HttpFileSystemState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doHttpAbort(traceId, authorization);
        }

        private void onFileSystemWindow(
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
            state = HttpFileSystemState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onFileSystemReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doHttpReset(traceId);
        }

        private void doFileSystemReset(
            long traceId)
        {
            if (!HttpFileSystemState.replyClosed(state))
            {
                state = HttpFileSystemState.closeReply(state);

                doReset(filesystem, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId);
            }
        }

        private void doFileSystemWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;

            doWindow(filesystem, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, capabilities);
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
        Flyweight payload)
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
                .payload(payload.buffer(), payload.offset(), payload.sizeof())
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

    private MessageConsumer newFileSystemStream(
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
        HttpFileSystemWithResult resolved)
    {
        final FileSystemBeginExFW fsBeginEx =
            fsBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(fsTypeId)
                .capabilities(resolved.capabilities())
                .path(resolved.path())
                .tag(resolved.tag())
                .timeout(resolved.timeout())
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
                .extension(fsBeginEx.buffer(), fsBeginEx.offset(), fsBeginEx.sizeof())
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
}
