/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.openapi.internal.event;

import static io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.event.A2aOpenapiEventType.OPERATION_DENIED;

import java.nio.ByteBuffer;
import java.time.Clock;

import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.A2aOpenapiBinding;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.event.A2aOpenapiEventExFW;
import io.aklivity.zilla.runtime.binding.a2a.openapi.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class A2aOpenapiEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final A2aOpenapiEventExFW.Builder a2aOpenapiEventExRW = new A2aOpenapiEventExFW.Builder();
    private final int a2aOpenapiTypeId;
    private final int operationDeniedId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public A2aOpenapiEventContext(
        EngineContext context)
    {
        this.a2aOpenapiTypeId = context.supplyTypeId(A2aOpenapiBinding.TYPE);
        this.operationDeniedId = context.supplyEventId("binding.a2a.openapi.operation.denied");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void operationDenied(
        long bindingId,
        String detail)
    {
        A2aOpenapiEventExFW extension = a2aOpenapiEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .operationDenied(e -> e
                .typeId(OPERATION_DENIED.value())
                .detail(detail)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(operationDeniedId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(a2aOpenapiTypeId, event.buffer(), event.offset(), event.limit());
    }
}
