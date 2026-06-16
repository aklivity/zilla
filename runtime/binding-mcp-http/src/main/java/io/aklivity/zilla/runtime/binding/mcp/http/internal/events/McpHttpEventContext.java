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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.events;

import static io.aklivity.zilla.runtime.binding.mcp.http.internal.types.event.McpHttpEventType.SCHEMA_ACCESSOR_UNRESOLVED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpBinding;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.event.McpHttpEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class McpHttpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final McpHttpEventExFW.Builder mcpHttpEventExRW = new McpHttpEventExFW.Builder();
    private final int mcpHttpTypeId;
    private final int schemaAccessorUnresolvedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public McpHttpEventContext(
        EngineContext context)
    {
        this.mcpHttpTypeId = context.supplyTypeId(McpHttpBinding.NAME);
        this.schemaAccessorUnresolvedEventId = context.supplyEventId("binding.mcp_http.schema.accessor.unresolved");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void schemaAccessorUnresolved(
        long traceId,
        long bindingId,
        String name,
        String accessor)
    {
        final McpHttpEventExFW extension = mcpHttpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .schemaAccessorUnresolved(e -> e
                .typeId(SCHEMA_ACCESSOR_UNRESOLVED.value())
                .name(name)
                .accessor(accessor))
            .build();
        final EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(schemaAccessorUnresolvedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mcpHttpTypeId, event.buffer(), event.offset(), event.limit());
    }
}
