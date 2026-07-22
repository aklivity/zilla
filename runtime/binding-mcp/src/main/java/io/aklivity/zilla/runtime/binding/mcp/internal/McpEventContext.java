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
package io.aklivity.zilla.runtime.binding.mcp.internal;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpEventType.AUTHORIZATION_FAILED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpEventType.ELICITATION_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpEventType.SESSION_CLOSED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpEventType.SESSION_ESTABLISHED;

import java.nio.ByteBuffer;
import java.time.Clock;

import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpAuthorizationError;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpEventExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class McpEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final MutableDirectBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final MutableDirectBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final McpEventExFW.Builder mcpEventExRW = new McpEventExFW.Builder();
    private final int mcpTypeId;
    private final int sessionEstablishedEventId;
    private final int sessionClosedEventId;
    private final int authorizationFailedEventId;
    private final int elicitationTimeoutEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public McpEventContext(
        EngineContext context)
    {
        this.mcpTypeId = context.supplyTypeId(McpBinding.NAME);
        this.sessionEstablishedEventId = context.supplyEventId("binding.mcp.session.established");
        this.sessionClosedEventId = context.supplyEventId("binding.mcp.session.closed");
        this.authorizationFailedEventId = context.supplyEventId("binding.mcp.authorization.failed");
        this.elicitationTimeoutEventId = context.supplyEventId("binding.mcp.elicitation.timeout");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void sessionEstablished(
        long traceId,
        long bindingId,
        String sessionId)
    {
        McpEventExFW extension = mcpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .sessionEstablished(e -> e
                .typeId(SESSION_ESTABLISHED.value())
                .sessionId(sessionId))
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(sessionEstablishedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mcpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void sessionClosed(
        long traceId,
        long bindingId,
        String sessionId,
        String reason)
    {
        McpEventExFW extension = mcpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .sessionClosed(e -> e
                .typeId(SESSION_CLOSED.value())
                .sessionId(sessionId)
                .reason(reason))
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(sessionClosedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mcpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void authorizationFailed(
        long traceId,
        long bindingId,
        String realm,
        String scopes,
        String resourceMetadata,
        McpAuthorizationError error)
    {
        McpEventExFW extension = mcpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .authorizationFailed(e -> e
                .typeId(AUTHORIZATION_FAILED.value())
                .realm(realm)
                .scopes(scopes)
                .resourceMetadata(resourceMetadata)
                .error(s -> s.set(error)))
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(authorizationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mcpTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void elicitationTimeout(
        long traceId,
        long bindingId,
        String sessionId,
        String elicitationId)
    {
        McpEventExFW extension = mcpEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .elicitationTimeout(e -> e
                .typeId(ELICITATION_TIMEOUT.value())
                .sessionId(sessionId)
                .elicitationId(elicitationId))
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(elicitationTimeoutEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mcpTypeId, event.buffer(), event.offset(), event.limit());
    }
}
