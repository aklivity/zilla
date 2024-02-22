/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tls.internal.types.event.TlsEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TlsEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final TlsEventFW.Builder tlsEventRW = new TlsEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int tlsTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public TlsEventContext(
        EngineContext context)
    {
        this.tlsTypeId = context.supplyTypeId(TlsBinding.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void tlsFailed(
        long traceId,
        long bindingId)
    {
        TlsEventFW event = tlsEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .tlsFailed(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(bindingId)
            )
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsProtocolRejected(
        long traceId,
        long bindingId)
    {
        TlsEventFW event = tlsEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .tlsProtocolRejected(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(bindingId)
            )
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsKeyRejected(
        long traceId,
        long bindingId)
    {
        TlsEventFW event = tlsEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .tlsKeyRejected(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(bindingId)
            )
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsPeerNotVerified(
        long traceId,
        long bindingId)
    {
        TlsEventFW event = tlsEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .tlsPeerNotVerified(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(bindingId)
            )
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void tlsHandshakeFailed(
        long traceId,
        long bindingId)
    {
        TlsEventFW event = tlsEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .tlsHandshakeFailed(e -> e
                .timestamp(clock.millis())
                .traceId(traceId)
                .namespacedId(bindingId)
            )
            .build();
        eventWriter.accept(tlsTypeId, event.buffer(), event.offset(), event.limit());
    }
}
