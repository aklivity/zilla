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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.event;

import static io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.event.McpOpenapiEventType.OPERATION_DENIED;
import static io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.event.McpOpenapiEventType.ROUTES_EMPTY;

import java.nio.ByteBuffer;
import java.time.Clock;

import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenapiBinding;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.event.McpOpenapiEventExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class McpOpenapiEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final McpOpenapiEventExFW.Builder mcpOpenapiEventExRW = new McpOpenapiEventExFW.Builder();
    private final int mcpOpenapiTypeId;
    private final int operationDeniedId;
    private final int routesEmptyId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public McpOpenapiEventContext(
        EngineContext context)
    {
        this.mcpOpenapiTypeId = context.supplyTypeId(McpOpenapiBinding.NAME);
        this.operationDeniedId = context.supplyEventId("binding.mcp.openapi.operation.denied");
        this.routesEmptyId = context.supplyEventId("binding.mcp.openapi.routes.empty");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void operationDenied(
        long bindingId,
        String detail)
    {
        McpOpenapiEventExFW extension = mcpOpenapiEventExRW
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
        eventWriter.accept(mcpOpenapiTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void routesEmpty(
        long bindingId)
    {
        McpOpenapiEventExFW extension = mcpOpenapiEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .routesEmpty(e -> e
                .typeId(ROUTES_EMPTY.value())
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(routesEmptyId)
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mcpOpenapiTypeId, event.buffer(), event.offset(), event.limit());
    }
}
