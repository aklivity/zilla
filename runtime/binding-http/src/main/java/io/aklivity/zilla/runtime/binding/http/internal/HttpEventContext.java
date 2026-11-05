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
package io.aklivity.zilla.runtime.binding.http.internal;

import static io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventType.CIRCUIT_CLOSED;
import static io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventType.CIRCUIT_HALF_OPENED;
import static io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventType.CIRCUIT_OPENED;
import static io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventType.CIRCUIT_REJECTED;
import static io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventType.REQUEST_ACCEPTED;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpCircuitCause;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class HttpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 8192;
    private static final String8FW HEADER_SCHEME = new String8FW(":scheme");
    private static final String8FW HEADER_METHOD = new String8FW(":method");
    private static final String8FW HEADER_AUTHORITY = new String8FW(":authority");
    private static final String8FW HEADER_PATH = new String8FW(":path");

    private final AtomicBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final HttpEventExFW.Builder httpEventExRW = new HttpEventExFW.Builder();
    private final int httpTypeId;
    private final int requestAcceptedEventId;
    private final int circuitOpenedEventId;
    private final int circuitHalfOpenedEventId;
    private final int circuitClosedEventId;
    private final int circuitRejectedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public HttpEventContext(
        EngineContext context)
    {
        this.httpTypeId = context.supplyTypeId(HttpBinding.NAME);
        this.requestAcceptedEventId = context.supplyEventId("binding.http.request.accepted");
        this.circuitOpenedEventId = context.supplyEventId("binding.http.circuit.opened");
        this.circuitHalfOpenedEventId = context.supplyEventId("binding.http.circuit.half.opened");
        this.circuitClosedEventId = context.supplyEventId("binding.http.circuit.closed");
        this.circuitRejectedEventId = context.supplyEventId("binding.http.circuit.rejected");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void requestAccepted(
        long traceId,
        long bindingId,
        GuardHandler guard,
        long authorization,
        Map<String, String> headers)
    {
        String identity = guard == null ? null : guard.identity(authorization);
        HttpEventExFW extension = httpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .requestAccepted(e -> e
                .typeId(REQUEST_ACCEPTED.value())
                .identity(identity)
                .scheme(headers.get(":scheme"))
                .method(headers.get(":method"))
                .authority(headers.get(":authority"))
                .path(headers.get(":path"))
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(requestAcceptedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void requestAccepted(
        long traceId,
        long bindingId,
        GuardHandler guard,
        long authorization,
        Array32FW<HttpHeaderFW> headers)
    {
        String identity = guard == null ? null : guard.identity(authorization);
        HttpEventExFW extension = httpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .requestAccepted(e -> e
                .typeId(REQUEST_ACCEPTED.value())
                .identity(identity)
                .scheme(headers.matchFirst(h -> HEADER_SCHEME.equals(h.name())).value().asString())
                .method(headers.matchFirst(h -> HEADER_METHOD.equals(h.name())).value().asString())
                .authority(headers.matchFirst(h -> HEADER_AUTHORITY.equals(h.name())).value().asString())
                .path(headers.matchFirst(h -> HEADER_PATH.equals(h.name())).value().asString())
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(requestAcceptedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void circuitOpened(
        long traceId,
        long bindingId,
        long originId,
        long circuitId,
        HttpCircuitCause cause,
        long remaining)
    {
        HttpEventExFW extension = httpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .circuitOpened(e -> e
                .typeId(CIRCUIT_OPENED.value())
                .originId(originId)
                .circuitId(circuitId)
                .cause(c -> c.set(cause))
                .remaining(remaining)
            )
            .build();
        publish(circuitOpenedEventId, traceId, bindingId, extension);
    }

    public void circuitHalfOpened(
        long traceId,
        long bindingId,
        long originId,
        long circuitId,
        HttpCircuitCause cause,
        long remaining)
    {
        HttpEventExFW extension = httpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .circuitHalfOpened(e -> e
                .typeId(CIRCUIT_HALF_OPENED.value())
                .originId(originId)
                .circuitId(circuitId)
                .cause(c -> c.set(cause))
                .remaining(remaining)
            )
            .build();
        publish(circuitHalfOpenedEventId, traceId, bindingId, extension);
    }

    public void circuitClosed(
        long traceId,
        long bindingId,
        long originId,
        long circuitId,
        HttpCircuitCause cause,
        long remaining)
    {
        HttpEventExFW extension = httpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .circuitClosed(e -> e
                .typeId(CIRCUIT_CLOSED.value())
                .originId(originId)
                .circuitId(circuitId)
                .cause(c -> c.set(cause))
                .remaining(remaining)
            )
            .build();
        publish(circuitClosedEventId, traceId, bindingId, extension);
    }

    public void circuitRejected(
        long traceId,
        long bindingId,
        long originId,
        long circuitId,
        HttpCircuitCause cause,
        long remaining)
    {
        HttpEventExFW extension = httpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .circuitRejected(e -> e
                .typeId(CIRCUIT_REJECTED.value())
                .originId(originId)
                .circuitId(circuitId)
                .cause(c -> c.set(cause))
                .remaining(remaining)
            )
            .build();
        publish(circuitRejectedEventId, traceId, bindingId, extension);
    }

    private void publish(
        int eventId,
        long traceId,
        long bindingId,
        HttpEventExFW extension)
    {
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(eventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }
}
