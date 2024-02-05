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
package io.aklivity.zilla.runtime.binding.http.internal;

import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.event.Result;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class HttpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final HttpEventFW.Builder httpEventRW = new HttpEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int httpTypeId;
    private final MessageConsumer logEvent;
    private final LongSupplier timestamp;

    public HttpEventContext(
        int httpTypeId,
        EngineContext context)
    {
        this.httpTypeId = httpTypeId;
        this.logEvent = context.logEvent();
        this.timestamp = context.timestamp();
    }

    public void accessDenied(
        long traceId,
        long routedId)
    {
        HttpEventFW event = httpEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .accessDenied(e -> e
                .timestamp(timestamp.getAsLong())
                .traceId(traceId)
                .bindingId(routedId)
            )
            .build();
        System.out.println(event); // TODO: Ati
        logEvent.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void authorization(
        Result result,
        long traceId,
        long routedId,
        String identity)
    {
        HttpEventFW event = httpEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .authorization(e -> e
                .timestamp(timestamp.getAsLong())
                .traceId(traceId)
                .bindingId(routedId)
                .result(r -> r.set(result))
                .identity(identity)
            )
            .build();
        System.out.println(event); // TODO: Ati
        logEvent.accept(httpTypeId, event.buffer(), event.offset(), event.limit());
    }
}
