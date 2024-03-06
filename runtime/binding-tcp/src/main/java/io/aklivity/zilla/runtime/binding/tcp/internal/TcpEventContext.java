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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static io.aklivity.zilla.runtime.binding.tcp.internal.types.event.TcpEventType.DNS_FAILED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tcp.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.event.TcpEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TcpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final TcpEventExFW.Builder tcpEventExRW = new TcpEventExFW.Builder();

    private final int tcpTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public TcpEventContext(
        EngineContext context)
    {
        this.tcpTypeId = context.supplyTypeId(TcpBinding.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void dnsResolutionFailed(
        long traceId,
        long bindingId,
        String address)
    {
        TcpEventExFW extension = tcpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .dnsFailed(e -> e
                .typeId(DNS_FAILED.value())
                .address(address)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(tcpTypeId, event.buffer(), event.offset(), event.limit());
    }
}
