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

import io.aklivity.zilla.runtime.binding.mcp.internal.types.StringFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpAuthorizationFailedExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpElicitationTimeoutExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpEventExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpSessionClosedExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.event.McpSessionEstablishedExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class McpEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final McpEventExFW mcpEventExRO = new McpEventExFW();

    McpEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final McpEventExFW extension = mcpEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case SESSION_ESTABLISHED:
        {
            McpSessionEstablishedExFW ex = extension.sessionEstablished();
            result = String.format("MCP session (%s) was established.",
                    asString(ex.sessionId()));
            break;
        }
        case SESSION_CLOSED:
        {
            McpSessionClosedExFW ex = extension.sessionClosed();
            result = String.format("MCP session (%s) was closed. %s",
                    asString(ex.sessionId()),
                    asString(ex.reason()));
            break;
        }
        case AUTHORIZATION_FAILED:
        {
            McpAuthorizationFailedExFW ex = extension.authorizationFailed();
            result = String.format("MCP bearer authorization failed (%s) for realm (%s).",
                    ex.error().get(),
                    asString(ex.realm()));
            break;
        }
        case ELICITATION_TIMEOUT:
        {
            McpElicitationTimeoutExFW ex = extension.elicitationTimeout();
            result = String.format("Elicitation (%s) timed out for session (%s).",
                    asString(ex.elicitationId()),
                    asString(ex.sessionId()));
            break;
        }
        }
        return result;
    }

    private static String asString(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
