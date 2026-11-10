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
package io.aklivity.zilla.runtime.binding.mcp.internal.bench;

import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;

/**
 * Accepts the {@code mcp} application streams of a server or proxy, handing each to the peer registered for its
 * kind: the lifecycle stream that holds the session, and one stream per request.
 */
final class McpApplicationPeer implements BindingHandler
{
    private final BeginFW beginRO = new BeginFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final BindingHandler lifecycle;
    private final BindingHandler toolsList;
    private final BindingHandler toolsCall;

    McpApplicationPeer(
        BindingHandler lifecycle,
        BindingHandler toolsList,
        BindingHandler toolsCall)
    {
        this.lifecycle = lifecycle;
        this.toolsList = toolsList;
        this.toolsCall = toolsCall;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final OctetsFW extension = begin.extension();
        final McpBeginExFW mcpBeginEx = mcpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());

        BindingHandler handler = null;

        if (mcpBeginEx != null)
        {
            switch (mcpBeginEx.kind())
            {
            case McpBeginExFW.KIND_LIFECYCLE:
                handler = lifecycle;
                break;
            case McpBeginExFW.KIND_TOOLS_LIST:
                handler = toolsList;
                break;
            case McpBeginExFW.KIND_TOOLS_CALL:
                handler = toolsCall;
                break;
            default:
                break;
            }
        }

        return handler != null ? handler.newStream(msgTypeId, buffer, index, length, sender) : null;
    }
}
