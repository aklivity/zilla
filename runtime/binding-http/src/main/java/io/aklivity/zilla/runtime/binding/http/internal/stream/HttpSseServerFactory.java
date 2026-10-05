/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.engine.util.Flags.hasFin;
import static io.aklivity.zilla.runtime.engine.util.Flags.hasInit;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class HttpSseServerFactory implements BindingHandler
{
    private static final int ABSENT = -1;
    private static final int EVENT_TYPE_MAX = 64;
    private static final int EVENT_ID_MAX = 64;
    private static final int EVENT_OVERHEAD_MAX = 6 + EVENT_TYPE_MAX + 1 + 5 + 1 + 3 + EVENT_ID_MAX + 1 + 1;

    private static final byte[] EVENT_FIELD = "event:".getBytes(UTF_8);
    private static final byte[] DATA_FIELD = "data:".getBytes(UTF_8);
    private static final byte[] ID_FIELD = "id:".getBytes(UTF_8);
    private static final byte[] NAME_TYPE = "type".getBytes(UTF_8);
    private static final byte[] NAME_ID = "id".getBytes(UTF_8);
    private static final byte LINE_FEED = 0x0a;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final HttpBeginExFW beginExRO = new HttpBeginExFW();
    private final HttpDataExFW dataExRO = new HttpDataExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpSseFraming framing = new HttpSseFraming();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx eventBuffer;
    private final int httpTypeId;
    private final BindingHandler next;

    private final Consumer<HttpHeaderFW> captureField = this::captureField;

    private HttpSseServer fieldsTarget;

    public HttpSseServerFactory(
        EngineContext context,
        BindingHandler next)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.eventBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.httpTypeId = context.supplyTypeId("http");
        this.next = next;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer exchange)
    {
        final HttpSseServer server = new HttpSseServer(exchange);

        server.app = next.newStream(msgTypeId, buffer, index, length, server::onApp);

        return server::onExchange;
    }

    private final class HttpSseServer
    {
        private final MessageConsumer exchange;
        private final byte[] eventType;
        private final byte[] eventId;

        private MessageConsumer app;
        private boolean replyBegun;
        private boolean framed;
        private int eventTypeLength = ABSENT;
        private int eventIdLength = ABSENT;
        private boolean eventValid;

        private boolean windowHeld;
        private long windowOriginId;
        private long windowRoutedId;
        private long windowStreamId;
        private long windowSequence;
        private long windowAcknowledge;
        private int windowMaximum;
        private long windowTimestamp;
        private long windowTraceId;
        private long windowAuthorization;
        private long windowBudgetId;
        private int windowPadding;
        private int windowMinimum;
        private int windowCapabilities;

        private HttpSseServer(
            MessageConsumer exchange)
        {
            this.exchange = exchange;
            this.eventType = new byte[EVENT_TYPE_MAX];
            this.eventId = new byte[EVENT_ID_MAX];
        }

        private void onExchange(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            if (msgTypeId == WindowFW.TYPE_ID)
            {
                onExchangeWindow(windowRO.wrap(buffer, index, index + length));
            }
            else
            {
                app.accept(msgTypeId, buffer, index, length);
            }
        }

        private void onExchangeWindow(
            WindowFW window)
        {
            if (!replyBegun)
            {
                windowHeld = true;
                windowOriginId = window.originId();
                windowRoutedId = window.routedId();
                windowStreamId = window.streamId();
                windowSequence = window.sequence();
                windowAcknowledge = window.acknowledge();
                windowMaximum = window.maximum();
                windowTimestamp = window.timestamp();
                windowTraceId = window.traceId();
                windowAuthorization = window.authorization();
                windowBudgetId = window.budgetId();
                windowPadding = window.padding();
                windowMinimum = window.minimum();
                windowCapabilities = window.capabilities();
            }
            else if (framed)
            {
                doAppWindow(window.originId(), window.routedId(), window.streamId(), window.sequence(),
                    window.acknowledge(), window.maximum(), window.timestamp(), window.traceId(), window.authorization(),
                    window.budgetId(), window.padding() + EVENT_OVERHEAD_MAX, window.minimum(), window.capabilities());
            }
            else
            {
                app.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
            }
        }

        private void onApp(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            if (msgTypeId == BeginFW.TYPE_ID)
            {
                onAppBegin(beginRO.wrap(buffer, index, index + length));
            }
            else if (msgTypeId == DataFW.TYPE_ID && framed)
            {
                onAppData(dataRO.wrap(buffer, index, index + length));
            }
            else
            {
                exchange.accept(msgTypeId, buffer, index, length);
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

            replyBegun = true;
            framed = beginEx != null && framing.detect(beginEx);

            if (windowHeld)
            {
                windowHeld = false;
                doAppWindow(windowOriginId, windowRoutedId, windowStreamId, windowSequence, windowAcknowledge,
                    windowMaximum, windowTimestamp, windowTraceId, windowAuthorization, windowBudgetId,
                    windowPadding + (framed ? EVENT_OVERHEAD_MAX : 0), windowMinimum, windowCapabilities);
            }

            if (framed)
            {
                final HttpBeginExFW retained = framing.strip(beginEx, extBuffer, httpTypeId);

                final BeginFW forward = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(begin.originId())
                    .routedId(begin.routedId())
                    .streamId(begin.streamId())
                    .sequence(begin.sequence())
                    .acknowledge(begin.acknowledge())
                    .maximum(begin.maximum())
                    .traceId(begin.traceId())
                    .authorization(begin.authorization())
                    .affinity(begin.affinity())
                    .extension(retained.buffer(), retained.offset(), retained.sizeof())
                    .build();

                exchange.accept(forward.typeId(), forward.buffer(), forward.offset(), forward.sizeof());
            }
            else
            {
                exchange.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            }
        }

        private void onAppData(
            DataFW data)
        {
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            if (hasInit(flags))
            {
                eventTypeLength = ABSENT;
                eventIdLength = ABSENT;
                eventValid = true;

                final HttpDataExFW dataEx = data.extension().get(dataExRO::tryWrap);

                if (dataEx != null)
                {
                    fieldsTarget = this;
                    dataEx.headers().forEach(captureField);
                    fieldsTarget = null;
                }
            }

            final int length = eventValid ? encodeEvent(flags, payload) : 0;

            if (eventValid && length <= data.reserved())
            {
                final DataFW forward = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(data.originId())
                    .routedId(data.routedId())
                    .streamId(data.streamId())
                    .sequence(data.sequence())
                    .acknowledge(data.acknowledge())
                    .maximum(data.maximum())
                    .traceId(data.traceId())
                    .authorization(data.authorization())
                    .flags(flags)
                    .budgetId(data.budgetId())
                    .reserved(data.reserved())
                    .payload(eventBuffer, 0, length)
                    .build();

                exchange.accept(forward.typeId(), forward.buffer(), forward.offset(), forward.sizeof());
            }
            else
            {
                doAppReset(data);
            }
        }

        private int encodeEvent(
            int flags,
            OctetsFW payload)
        {
            int progress = 0;

            if (hasInit(flags))
            {
                if (eventTypeLength != ABSENT)
                {
                    progress = put(progress, EVENT_FIELD, EVENT_FIELD.length);
                    progress = put(progress, eventType, eventTypeLength);
                    eventBuffer.putByte(progress++, LINE_FEED);
                }

                progress = put(progress, DATA_FIELD, DATA_FIELD.length);
            }

            if (payload != null)
            {
                final DirectBufferEx body = payload.buffer();
                final int limit = payload.limit();

                for (int offset = payload.offset(); offset < limit; offset++)
                {
                    final byte value = body.getByte(offset);

                    eventBuffer.putByte(progress++, value);

                    if (value == LINE_FEED)
                    {
                        progress = put(progress, DATA_FIELD, DATA_FIELD.length);
                    }
                }
            }

            if (hasFin(flags))
            {
                eventBuffer.putByte(progress++, LINE_FEED);

                if (eventIdLength != ABSENT)
                {
                    progress = put(progress, ID_FIELD, ID_FIELD.length);
                    progress = put(progress, eventId, eventIdLength);
                    eventBuffer.putByte(progress++, LINE_FEED);
                }

                eventBuffer.putByte(progress++, LINE_FEED);
            }

            return progress;
        }

        private int put(
            int offset,
            byte[] bytes,
            int length)
        {
            eventBuffer.putBytes(offset, bytes, 0, length);

            return offset + length;
        }

        private void captureEventField(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();
            final DirectBufferEx value = header.value().value();
            final int length = value != null ? value.capacity() : 0;

            if (HttpSseFraming.equalsBytes(name.value(), NAME_TYPE))
            {
                eventValid &= length <= EVENT_TYPE_MAX;

                if (eventValid)
                {
                    eventTypeLength = length;
                    value.getBytes(0, eventType, 0, length);
                }
            }
            else if (HttpSseFraming.equalsBytes(name.value(), NAME_ID))
            {
                eventValid &= length <= EVENT_ID_MAX;

                if (eventValid)
                {
                    eventIdLength = length;
                    value.getBytes(0, eventId, 0, length);
                }
            }
        }

        private void doAppWindow(
            long originId,
            long routedId,
            long streamId,
            long sequence,
            long acknowledge,
            int maximum,
            long timestamp,
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int minimum,
            int capabilities)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .timestamp(timestamp)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .padding(padding)
                .minimum(minimum)
                .capabilities(capabilities)
                .build();

            app.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doAppReset(
            DataFW data)
        {
            final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(data.originId())
                .routedId(data.routedId())
                .streamId(data.streamId())
                .sequence(data.sequence())
                .acknowledge(data.acknowledge())
                .maximum(data.maximum())
                .traceId(data.traceId())
                .build();

            app.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
        }
    }

    private void captureField(
        HttpHeaderFW header)
    {
        fieldsTarget.captureEventField(header);
    }
}
