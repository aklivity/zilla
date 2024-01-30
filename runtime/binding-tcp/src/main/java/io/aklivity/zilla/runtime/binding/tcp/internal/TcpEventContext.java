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

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.tcp.internal.types.event.ProxyEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TcpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final ProxyEventFW.Builder proxyEventRW = new ProxyEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int typeId;
    private final MessageConsumer logEvent;

    public TcpEventContext(
        int typeId,
        EngineContext context)
    {
        this.typeId = typeId;
        this.logEvent = context::logEvent;
    }

    public void remoteAccessFailure(
        long traceId,
        long routedId,
        String address)
    {
        ProxyEventFW event = proxyEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .remoteAccessFailure(e -> e
                .traceId(traceId)
                .bindingId(routedId)
                .address(address)
            )
            .build();
        System.out.println(event); // TODO: Ati
        logEvent.accept(typeId, event.buffer(), event.offset(), event.limit());
    }
}
