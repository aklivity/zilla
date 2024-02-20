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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tcp.internal.types.event.TcpEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TcpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final TcpEventFW.Builder tcpEventRW = new TcpEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int tcpTypeId;
    private final MessageConsumer eventWriter;
    private final LongSupplier timestamp;

    public TcpEventContext(
        EngineContext context)
    {
        this.tcpTypeId = context.supplyTypeId(TcpBinding.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.timestamp = context.timestamp();
    }

    public void dnsResolutionFailed(
        long traceId,
        long routedId,
        InetSocketAddress remoteAddress)
    {
        String address = remoteAddress == null ? "" : remoteAddress.toString();
        TcpEventFW event = tcpEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .dnsResolutionFailed(e -> e
                .timestamp(timestamp.getAsLong())
                .traceId(traceId)
                .namespacedId(routedId)
                .address(address)
            )
            .build();
        eventWriter.accept(tcpTypeId, event.buffer(), event.offset(), event.limit());
    }
}
