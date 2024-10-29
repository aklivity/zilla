/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tls.internal;

import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_FAILED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_HANDSHAKE_FAILED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_KEY_REJECTED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_PEER_NOT_VERIFIED;
import static io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventType.TLS_PROTOCOL_REJECTED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tls.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TlsEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final TlsEventExFW.Builder tlsEventExRW = new TlsEventExFW.Builder();
    private final int tlsTypeId;
    private final int tlsFailedEventId;
    private final int tlsProtocolRejectedEventId;
    private final int tlsKeyRejectedEventId;
    private final int tlsPeerNotVerifiedEventId;
    private final int tlsHandshakeFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public TlsEventContext(
        EngineContext context)
    {
        this.tlsTypeId = context.supplyTypeId(TlsBinding.NAME);
        this.tlsFailedEventId = context.supplyEventId("binding.tls.tls.failed");
        this.tlsProtocolRejectedEventId = context.supplyEventId("binding.tls.protocol.rejected");
        this.tlsKeyRejectedEventId = context.supplyEventId("binding.tls.key.rejected");
        this.tlsPeerNotVerifiedEventId = context.supplyEventId("binding.tls.peer.not.verified");
        this.tlsHandshakeFailedEventId = context.supplyEventId("binding.tls.handshake.failed");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void tlsFailed(
        long traceId,
        long bindingId)
    {
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsFailed(e -> e
                .typeId(TLS_FAILED.value())
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(tlsFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsProtocolRejected(
        long traceId,
        long bindingId)
    {
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsProtocolRejected(e -> e
                .typeId(TLS_PROTOCOL_REJECTED.value())
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(tlsProtocolRejectedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsKeyRejected(
        long traceId,
        long bindingId)
    {
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsKeyRejected(e -> e
                .typeId(TLS_KEY_REJECTED.value())
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(tlsKeyRejectedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsPeerNotVerified(
        long traceId,
        long bindingId)
    {
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsPeerNotVerified(e -> e
                .typeId(TLS_PEER_NOT_VERIFIED.value())
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(tlsPeerNotVerifiedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsHandshakeFailed(
        long traceId,
        long bindingId)
    {
        TlsEventExFW extension = tlsEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .tlsHandshakeFailed(e -> e
                .typeId(TLS_HANDSHAKE_FAILED.value())
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(tlsHandshakeFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }
}
