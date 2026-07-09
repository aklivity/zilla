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
package io.aklivity.zilla.runtime.binding.mcp.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpAuthorizationError;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class McpEventFormatterTest
{
    private final EngineContext context = mock(EngineContext.class);
    private final AtomicReference<DirectBufferEx> captured = new AtomicReference<>();

    private McpEventContext newEvents()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        MessageConsumer writer = (msgTypeId, buffer, index, length) ->
        {
            MutableDirectBufferEx copy = new UnsafeBufferEx(new byte[length]);
            copy.putBytes(0, buffer, index, length);
            captured.set(copy);
        };
        when(context.supplyEventWriter()).thenReturn(writer);
        return new McpEventContext(context);
    }

    private String format()
    {
        McpEventFormatterFactory factory = new McpEventFormatterFactory();
        assertEquals(McpBinding.NAME, factory.type());
        McpEventFormatter formatter = factory.create(new Configuration());

        DirectBufferEx event = captured.get();
        return formatter.format(event, 0, event.capacity());
    }

    @Test
    public void shouldFormatSessionEstablishedEvent()
    {
        McpEventContext events = newEvents();
        events.sessionEstablished(0L, 0L, "session-1");

        assertEquals("MCP session (session-1) was established.", format());
    }

    @Test
    public void shouldFormatSessionClosedEvent()
    {
        McpEventContext events = newEvents();
        events.sessionClosed(0L, 0L, "session-1", "inactivity timeout");

        assertEquals("MCP session (session-1) was closed. inactivity timeout", format());
    }

    @Test
    public void shouldFormatAuthorizationFailedEvent()
    {
        McpEventContext events = newEvents();
        events.authorizationFailed(0L, 0L, "realm-1", "tools:read", null, McpAuthorizationError.INVALID_TOKEN);

        assertEquals("MCP bearer authorization failed (INVALID_TOKEN) for realm (realm-1).", format());
    }

    @Test
    public void shouldFormatElicitationTimeoutEvent()
    {
        McpEventContext events = newEvents();
        events.elicitationTimeout(0L, 0L, "session-1", "elicit-1");

        assertEquals("Elicitation (elicit-1) timed out for session (session-1).", format());
    }
}
