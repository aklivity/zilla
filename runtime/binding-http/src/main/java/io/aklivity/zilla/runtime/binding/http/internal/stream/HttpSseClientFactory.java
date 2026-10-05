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

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static io.aklivity.zilla.runtime.engine.util.Flags.NONE;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.IntConsumer;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class HttpSseClientFactory implements HttpStreamFactory
{
    private static final int FIELD_VALUE_MAX = 255;
    private static final int SIGNAL_HTTP_WINDOW = 1;

    private static final int STATE_LINE_START = 0;
    private static final int STATE_FIELD_NAME = 1;
    private static final int STATE_VALUE_START = 2;
    private static final int STATE_FIELD_VALUE = 3;
    private static final int STATE_COMMENT = 4;

    private static final int FIELD_IGNORED = 0;
    private static final int FIELD_DATA = 1;
    private static final int FIELD_ID = 2;
    private static final int FIELD_EVENT = 3;

    private static final byte[] NAME_DATA = "data".getBytes(UTF_8);
    private static final byte[] NAME_ID = "id".getBytes(UTF_8);
    private static final byte[] NAME_EVENT = "event".getBytes(UTF_8);
    private static final byte[] BOM = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
    private static final byte[] STATUS_NAME = ":status".getBytes(UTF_8);
    private static final byte[] STATUS_OK = "200".getBytes(UTF_8);
    private static final byte[] CONTENT_TYPE_NAME = "content-type".getBytes(UTF_8);
    private static final byte[] CONTENT_TYPE_EVENT_STREAM = "text/event-stream".getBytes(UTF_8);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final HttpBeginExFW beginExRO = new HttpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpDataExFW.Builder dataExRW = new HttpDataExFW.Builder();

    private final HttpSseFraming framing = new HttpSseFraming();
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx requestBuffer;
    private final BufferPool bufferPool;
    private final BufferPool decodePool;
    private final BufferPool eventPool;
    private final Signaler signaler;
    private final int httpTypeId;
    private final HttpStreamFactory next;

    public HttpSseClientFactory(
        EngineContext context,
        HttpStreamFactory next)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.requestBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.decodePool = bufferPool.duplicate();
        this.eventPool = bufferPool.duplicate();
        this.signaler = context.signaler();
        this.httpTypeId = context.supplyTypeId("http");
        this.next = next;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        next.attach(binding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        next.detach(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer app)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

        final MessageConsumer receiver;

        if (beginEx != null && framing.detect(beginEx))
        {
            final HttpSseClient client = new HttpSseClient(app);
            final BeginFW stripped = stripFraming(begin, beginEx);

            client.http = next.newStream(stripped.typeId(), stripped.buffer(), stripped.offset(), stripped.sizeof(),
                client::onHttp);

            receiver = client::onApp;
        }
        else
        {
            receiver = next.newStream(msgTypeId, buffer, index, length, app);
        }

        return receiver;
    }

    private BeginFW stripFraming(
        BeginFW begin,
        HttpBeginExFW beginEx)
    {
        final HttpBeginExFW retained = framing.strip(beginEx, extBuffer, httpTypeId);

        return beginRW.wrap(requestBuffer, 0, requestBuffer.capacity())
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
    }

    private final class HttpSseClient
    {
        private final MessageConsumer app;
        private final IntConsumer onHttpWindowSignal;
        private final byte[] fieldName;
        private final MutableDirectBufferEx eventId;
        private final MutableDirectBufferEx eventType;

        private MessageConsumer http;

        private boolean decoding;
        private boolean httpEnded;
        private boolean appWindowHeld;
        private boolean httpWindowPending;
        private boolean closed;

        private long replyOriginId;
        private long replyRoutedId;
        private long replyStreamId;
        private long replyAuthorization;
        private long replyTraceId;

        private long httpSeq;
        private long httpAck;
        private int httpMax;

        private long appSeq;
        private long appAck;
        private int appMax;
        private int appPad;
        private long appBud;
        private long appWindowOriginId;
        private long appWindowRoutedId;
        private long appWindowStreamId;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int eventSlot = NO_SLOT;
        private int payloadLength;
        private int payloadSent;

        private int state;
        private int field;
        private int fieldNameLength;
        private int fieldValueLength;
        private boolean afterReturn;
        private int bomMatched;
        private boolean bomChecked;
        private int dataLines;
        private int eventIdLength;
        private int eventTypeLength;
        private boolean eventIdPresent;
        private boolean eventTypePresent;
        private boolean eventReady;
        private boolean initSent;

        private HttpSseClient(
            MessageConsumer app)
        {
            this.app = app;
            this.onHttpWindowSignal = this::onHttpWindowSignal;
            this.fieldName = new byte[8];
            this.eventId = new UnsafeBufferEx(new byte[FIELD_VALUE_MAX]);
            this.eventType = new UnsafeBufferEx(new byte[FIELD_VALUE_MAX]);
        }

        private void onApp(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            if (msgTypeId == BeginFW.TYPE_ID)
            {
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

                final BeginFW forward = beginEx != null ? stripFraming(begin, beginEx) : begin;

                http.accept(forward.typeId(), forward.buffer(), forward.offset(), forward.sizeof());
            }
            else if (msgTypeId == WindowFW.TYPE_ID)
            {
                onAppWindow(windowRO.wrap(buffer, index, index + length));
            }
            else if (msgTypeId == ResetFW.TYPE_ID)
            {
                cleanup();
                http.accept(msgTypeId, buffer, index, length);
            }
            else
            {
                http.accept(msgTypeId, buffer, index, length);
            }
        }

        private void onAppWindow(
            WindowFW window)
        {
            appAck = window.acknowledge();
            appMax = window.maximum();
            appPad = window.padding();
            appBud = window.budgetId();
            appWindowOriginId = window.originId();
            appWindowRoutedId = window.routedId();
            appWindowStreamId = window.streamId();
            replyTraceId = window.traceId();
            replyAuthorization = window.authorization();
            appWindowHeld = true;

            if (closed)
            {
                appWindowHeld = false;
            }
            else if (decoding)
            {
                drain();
                doHttpWindow();
            }
            else if (replyStreamId != 0L)
            {
                http.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
            }
        }

        private void onHttp(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onHttpBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                if (decoding)
                {
                    onHttpData(dataRO.wrap(buffer, index, index + length));
                }
                else
                {
                    app.accept(msgTypeId, buffer, index, length);
                }
                break;
            case EndFW.TYPE_ID:
                if (decoding)
                {
                    onHttpEnd(endRO.wrap(buffer, index, index + length));
                }
                else
                {
                    app.accept(msgTypeId, buffer, index, length);
                }
                break;
            case AbortFW.TYPE_ID:
                cleanup();
                app.accept(msgTypeId, buffer, index, length);
                break;
            default:
                app.accept(msgTypeId, buffer, index, length);
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final HttpBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

            replyOriginId = begin.originId();
            replyRoutedId = begin.routedId();
            replyStreamId = begin.streamId();
            replyAuthorization = begin.authorization();
            replyTraceId = begin.traceId();
            decoding = beginEx != null && isEventStream(beginEx);

            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            if (appWindowHeld)
            {
                if (decoding)
                {
                    doHttpWindow();
                }
                else
                {
                    doHttpPassthroughWindow();
                }
            }
        }

        private void onHttpData(
            DataFW data)
        {
            final OctetsFW payload = data.payload();
            final int payloadSize = payload != null ? payload.sizeof() : 0;

            replyTraceId = data.traceId();
            httpSeq = data.sequence() + data.reserved();

            if (payloadSize > 0)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(replyStreamId);
                    decodeSlotOffset = 0;
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanup();
                    doHttpReset();
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);

                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payloadSize);
                    decodeSlotOffset += payloadSize;

                    drain();
                    scheduleHttpWindow();
                }
            }
        }

        private void onHttpEnd(
            EndFW end)
        {
            replyTraceId = end.traceId();
            httpEnded = true;

            drain();
        }

        private void drain()
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                final int progress = decode(slot, 0, decodeSlotOffset);
                final int remaining = decodeSlotOffset - progress;

                if (remaining > 0 && progress > 0)
                {
                    slot.putBytes(0, slot, progress, remaining);
                }

                decodeSlotOffset = remaining;

                if (remaining == 0)
                {
                    bufferPool.release(decodeSlot);
                    decodeSlot = NO_SLOT;
                }
            }

            flushEvent();

            if (httpEnded && decodeSlot == NO_SLOT && payloadLength == 0 && !eventReady)
            {
                httpEnded = false;
                doAppEnd();
                cleanup();
            }
        }

        private int decode(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            while (progress < limit)
            {
                if (eventPending())
                {
                    flushEvent();
                }

                if (eventPending())
                {
                    break;
                }

                if (onByte(buffer.getByte(progress)))
                {
                    progress++;
                }
                else
                {
                    flushEvent();

                    if (eventPending())
                    {
                        break;
                    }
                }
            }

            return progress;
        }

        private boolean eventPending()
        {
            return eventReady || payloadLength > 0 && payloadLength == payloadCapacity();
        }

        private int payloadCapacity()
        {
            return bufferPool.slotCapacity();
        }

        private boolean onByte(
            byte value)
        {
            boolean consumed = true;

            if (!bomChecked)
            {
                consumed = onBom(value);
            }
            else if (afterReturn && value == '\n')
            {
                afterReturn = false;
            }
            else
            {
                afterReturn = false;

                switch (state)
                {
                case STATE_LINE_START:
                    consumed = onLineStart(value);
                    break;
                case STATE_FIELD_NAME:
                    consumed = onFieldName(value);
                    break;
                case STATE_VALUE_START:
                    consumed = onValueStart(value);
                    break;
                case STATE_FIELD_VALUE:
                    consumed = onFieldValue(value);
                    break;
                default:
                    onComment(value);
                    break;
                }
            }

            return consumed;
        }

        private boolean onBom(
            byte value)
        {
            if (bomMatched < BOM.length && value == BOM[bomMatched])
            {
                bomMatched++;
                bomChecked = bomMatched == BOM.length;
            }
            else
            {
                bomChecked = true;
                bomMatched = 0;
                onByte(value);
            }

            return true;
        }

        private boolean onLineStart(
            byte value)
        {
            if (value == '\n' || value == '\r')
            {
                afterReturn = value == '\r';
                dispatchEvent();
            }
            else if (value == ':')
            {
                state = STATE_COMMENT;
            }
            else
            {
                fieldNameLength = 0;
                state = STATE_FIELD_NAME;
                onFieldName(value);
            }

            return true;
        }

        private boolean onFieldName(
            byte value)
        {
            if (value == ':')
            {
                beginField();
                state = STATE_VALUE_START;
            }
            else if (value == '\n' || value == '\r')
            {
                beginField();
                endLine(value);
            }
            else if (fieldNameLength < fieldName.length)
            {
                fieldName[fieldNameLength++] = value;
            }
            else
            {
                fieldNameLength = fieldName.length + 1;
            }

            return true;
        }

        private boolean onValueStart(
            byte value)
        {
            boolean consumed = true;

            state = STATE_FIELD_VALUE;

            if (value != ' ')
            {
                consumed = onFieldValue(value);
            }

            return consumed;
        }

        private boolean onFieldValue(
            byte value)
        {
            boolean consumed = true;

            if (value == '\n' || value == '\r')
            {
                endLine(value);
            }
            else
            {
                switch (field)
                {
                case FIELD_DATA:
                    consumed = appendPayload(value);
                    break;
                case FIELD_ID:
                    if (fieldValueLength < FIELD_VALUE_MAX)
                    {
                        eventId.putByte(fieldValueLength++, value);
                        eventIdLength = fieldValueLength;
                    }
                    break;
                case FIELD_EVENT:
                    if (fieldValueLength < FIELD_VALUE_MAX)
                    {
                        eventType.putByte(fieldValueLength++, value);
                        eventTypeLength = fieldValueLength;
                    }
                    break;
                default:
                    break;
                }
            }

            return consumed;
        }

        private void onComment(
            byte value)
        {
            if (value == '\n' || value == '\r')
            {
                endLine(value);
            }
        }

        private void beginField()
        {
            field = fieldKind();
            fieldValueLength = 0;

            switch (field)
            {
            case FIELD_DATA:
                if (dataLines++ > 0 && payloadLength < payloadCapacity())
                {
                    appendPayload((byte) '\n');
                }
                break;
            case FIELD_ID:
                eventIdLength = 0;
                eventIdPresent = true;
                break;
            case FIELD_EVENT:
                eventTypeLength = 0;
                eventTypePresent = true;
                break;
            default:
                break;
            }
        }

        private int fieldKind()
        {
            final int kind;

            if (matches(fieldName, fieldNameLength, NAME_DATA))
            {
                kind = FIELD_DATA;
            }
            else if (matches(fieldName, fieldNameLength, NAME_ID))
            {
                kind = FIELD_ID;
            }
            else if (matches(fieldName, fieldNameLength, NAME_EVENT))
            {
                kind = FIELD_EVENT;
            }
            else
            {
                kind = FIELD_IGNORED;
            }

            return kind;
        }

        private void endLine(
            byte value)
        {
            afterReturn = value == '\r';
            state = STATE_LINE_START;
            field = FIELD_IGNORED;
        }

        private boolean appendPayload(
            byte value)
        {
            if (eventSlot == NO_SLOT)
            {
                eventSlot = bufferPool.acquire(replyStreamId);
            }

            final boolean appended = eventSlot != NO_SLOT && payloadLength < payloadCapacity();

            if (appended)
            {
                eventPool.buffer(eventSlot).putByte(payloadLength++, value);
            }

            return appended;
        }

        private void dispatchEvent()
        {
            if (dataLines > 0)
            {
                eventReady = true;
                flushEvent();
            }
            else
            {
                resetEvent();
            }
        }

        private void flushEvent()
        {
            boolean progressed = true;

            while (progressed && (eventReady || payloadLength > payloadSent))
            {
                final int remaining = payloadLength - payloadSent;
                final int window = (int) (appMax - (appSeq - appAck)) - appPad;
                final int length = Math.max(0, Math.min(remaining, window));
                final boolean last = eventReady && length == remaining;
                final boolean complete = eventReady || initSent || payloadLength == payloadCapacity();
                final boolean sendable = complete && window >= 0 && (length > 0 || last);

                progressed = sendable;

                if (sendable)
                {
                    final int flags = (initSent ? NONE : INIT) | (last ? FIN : NONE);

                    doAppData(flags, length);

                    payloadSent += length;
                    initSent = true;

                    if (last)
                    {
                        resetEvent();
                    }
                    else if (payloadSent == payloadLength)
                    {
                        payloadLength = 0;
                        payloadSent = 0;
                    }
                }
            }
        }

        private void resetEvent()
        {
            payloadLength = 0;
            payloadSent = 0;
            dataLines = 0;
            eventIdLength = 0;
            eventTypeLength = 0;
            eventIdPresent = false;
            eventTypePresent = false;
            eventReady = false;
            initSent = false;

            if (eventSlot != NO_SLOT)
            {
                bufferPool.release(eventSlot);
                eventSlot = NO_SLOT;
            }
        }

        private void doAppData(
            int flags,
            int length)
        {
            final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(replyOriginId)
                .routedId(replyRoutedId)
                .streamId(replyStreamId)
                .sequence(appSeq)
                .acknowledge(appAck)
                .maximum(appMax)
                .traceId(replyTraceId)
                .authorization(replyAuthorization)
                .flags(flags)
                .budgetId(appBud)
                .reserved(length + appPad);

            builder.payload(length > 0 ? eventPool.buffer(eventSlot) : writeBuffer, length > 0 ? payloadSent : 0, length);

            if ((flags & INIT) != 0 && (eventTypePresent || eventIdPresent))
            {
                dataExRW.wrap(extBuffer, 0, extBuffer.capacity()).typeId(httpTypeId);

                if (eventTypePresent)
                {
                    dataExRW.headersItem(h -> h.name("type").value(eventType, 0, eventTypeLength));
                }

                if (eventIdPresent)
                {
                    dataExRW.headersItem(h -> h.name("id").value(eventId, 0, eventIdLength));
                }

                final HttpDataExFW dataEx = dataExRW.build();

                builder.extension(dataEx.buffer(), dataEx.offset(), dataEx.sizeof());
            }

            final DataFW data = builder.build();

            appSeq += data.reserved();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private void doAppEnd()
        {
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(replyOriginId)
                .routedId(replyRoutedId)
                .streamId(replyStreamId)
                .sequence(appSeq)
                .acknowledge(appAck)
                .maximum(appMax)
                .traceId(replyTraceId)
                .authorization(replyAuthorization)
                .build();

            app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }

        private void scheduleHttpWindow()
        {
            if (!httpWindowPending)
            {
                httpWindowPending = true;
                signaler.signalAt(System.currentTimeMillis(), SIGNAL_HTTP_WINDOW, onHttpWindowSignal);
            }
        }

        private void onHttpWindowSignal(
            int signalId)
        {
            httpWindowPending = false;

            if (!closed)
            {
                drain();
                doHttpWindow();
            }
        }

        private void doHttpWindow()
        {
            httpMax = payloadCapacity();
            httpAck = Math.max(httpAck, httpSeq - decodeSlotOffset);

            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(appWindowOriginId)
                .routedId(appWindowRoutedId)
                .streamId(appWindowStreamId)
                .sequence(httpSeq)
                .acknowledge(httpAck)
                .maximum(httpMax)
                .traceId(replyTraceId)
                .authorization(replyAuthorization)
                .budgetId(0L)
                .padding(0)
                .build();

            http.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doHttpPassthroughWindow()
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(appWindowOriginId)
                .routedId(appWindowRoutedId)
                .streamId(appWindowStreamId)
                .sequence(appSeq)
                .acknowledge(appAck)
                .maximum(appMax)
                .traceId(replyTraceId)
                .authorization(replyAuthorization)
                .budgetId(appBud)
                .padding(appPad)
                .build();

            http.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doHttpReset()
        {
            final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(appWindowOriginId)
                .routedId(appWindowRoutedId)
                .streamId(appWindowStreamId)
                .sequence(httpSeq)
                .acknowledge(httpAck)
                .maximum(httpMax)
                .traceId(replyTraceId)
                .build();

            http.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
        }

        private void cleanup()
        {
            closed = true;

            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }

            if (eventSlot != NO_SLOT)
            {
                bufferPool.release(eventSlot);
                eventSlot = NO_SLOT;
            }
        }
    }

    private static boolean isEventStream(
        HttpBeginExFW beginEx)
    {
        return beginEx.headers().anyMatch(h -> HttpSseFraming.equalsBytes(h.name().value(), STATUS_NAME) &&
                HttpSseFraming.equalsBytes(h.value().value(), STATUS_OK)) &&
            beginEx.headers().anyMatch(h -> HttpSseFraming.equalsBytes(h.name().value(), CONTENT_TYPE_NAME) &&
                startsWith(h.value().value(), CONTENT_TYPE_EVENT_STREAM));
    }

    private static boolean startsWith(
        DirectBufferEx value,
        byte[] prefix)
    {
        final int length = value != null ? value.capacity() : 0;

        boolean starts = length >= prefix.length;

        for (int i = 0; starts && i < prefix.length; i++)
        {
            starts = value.getByte(i) == prefix[i];
        }

        return starts;
    }

    private static boolean matches(
        byte[] value,
        int length,
        byte[] expected)
    {
        boolean matches = length == expected.length;

        for (int i = 0; matches && i < length; i++)
        {
            matches = value[i] == expected[i];
        }

        return matches;
    }
}
