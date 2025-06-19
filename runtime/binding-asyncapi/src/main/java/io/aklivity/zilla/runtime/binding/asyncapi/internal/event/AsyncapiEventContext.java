/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.event;

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.types.event.AsyncapiEventType.UNRESOLVED_REF;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.event.AsyncapiEventExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class AsyncapiEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final AsyncapiEventExFW.Builder asyncapiEventExRW = new AsyncapiEventExFW.Builder();
    private final int asyncapiTypeId;
    private final int unresolvedRef;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public AsyncapiEventContext(
        EngineContext context)
    {
        this.asyncapiTypeId = context.supplyTypeId(AsyncapiBinding.NAME);
        this.unresolvedRef = context.supplyEventId("binding.asyncapi.unresolved.ref");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void unresolvedRef(
        long traceId,
        long bindingId,
        String ref)
    {
        AsyncapiEventExFW extension = asyncapiEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unresolvedRef(e -> e
                .typeId(UNRESOLVED_REF.value())
                .ref(ref)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(unresolvedRef)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(asyncapiTypeId, event.buffer(), event.offset(), event.limit());
    }
}
