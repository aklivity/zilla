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

import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.event.McpHttpEventExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.event.McpHttpSchemaAccessorUnresolvedExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class McpHttpEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final McpHttpEventExFW mcpHttpEventExRO = new McpHttpEventExFW();

    McpHttpEventFormatter(
        Configuration config)
    {
    }

    @Override
    public String format(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final McpHttpEventExFW extension = mcpHttpEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case SCHEMA_ACCESSOR_UNRESOLVED:
        {
            final McpHttpSchemaAccessorUnresolvedExFW ex = extension.schemaAccessorUnresolved();
            result = String.format("Expression \"%s\" is unresolved against the schema for \"%s\".",
                ex.accessor().asString(), ex.name().asString());
            break;
        }
        default:
            break;
        }
        return result;
    }
}
