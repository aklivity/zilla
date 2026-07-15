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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.event;

import static io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.types.event.OpenapiAsyncapiEventType.OPERATION_DENIED;
import static io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.types.event.OpenapiAsyncapiEventType.UNRESOLVED_REF;

import java.nio.ByteBuffer;
import java.time.Clock;

import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.OpenapiAsyncapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.types.event.OpenapiAsyncapiEventExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class OpenapiAsyncapiEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final OpenapiAsyncapiEventExFW.Builder openapiAsyncapiEventExRW = new OpenapiAsyncapiEventExFW.Builder();
    private final int openapiAsyncapiTypeId;
    private final int unresolvedRef;
    private final int operationDenied;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public OpenapiAsyncapiEventContext(
        EngineContext context)
    {
        this.openapiAsyncapiTypeId = context.supplyTypeId(OpenapiAsyncapiBinding.NAME);
        this.unresolvedRef = context.supplyEventId("binding.openapi.asyncapi.unresolved.ref");
        this.operationDenied = context.supplyEventId("binding.openapi.asyncapi.operation.denied");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void unresolvedRef(
        long bindingId,
        String ref)
    {
        OpenapiAsyncapiEventExFW extension = openapiAsyncapiEventExRW
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
            .traceId(0L)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(openapiAsyncapiTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void operationDenied(
        long bindingId,
        String detail)
    {
        OpenapiAsyncapiEventExFW extension = openapiAsyncapiEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .operationDenied(e -> e
                .typeId(OPERATION_DENIED.value())
                .detail(detail)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(operationDenied)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(openapiAsyncapiTypeId, event.buffer(), event.offset(), event.limit());
    }
}
