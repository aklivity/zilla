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

import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;

/**
 * Accepts the HTTP requests of an {@code mcp} client binding and replays the response to each kind of request:
 * {@code initialize}, {@code notifications/initialized}, {@code tools/call}, {@code tools/list} and the
 * {@code DELETE} that ends the session.
 * <p>
 * A request is told apart without parsing its body. A {@code POST} without a session is {@code initialize}, a
 * {@code DELETE} ends the session, and a {@code POST} with a session is told by its {@code content-length}: that
 * of {@code notifications/initialized} or of {@code tools/list}, and otherwise {@code tools/call}.
 */
final class McpHttpPeer implements BindingHandler
{
    private static final String HTTP_SESSION_ID = "5ca1ab1e-c0de-4a11-b0a7-000100000000";
    private static final int STREAMING_FRAGMENT_LENGTH = 256;
    private static final int INITIALIZED_LENGTH = McpBenchMessages.INITIALIZED.length();
    private static final int TOOLS_LIST_LENGTH = McpBenchMessages.TOOLS_LIST.length();

    private final BeginFW beginRO = new BeginFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final Responder initialize;
    private final Responder initialized;
    private final Responder shutdown;
    private final Responder toolsCall;
    private final Responder toolsList;

    private boolean delete;
    private boolean session;
    private int contentLength;

    McpHttpPeer(
        int httpTypeId,
        String size,
        boolean streaming,
        boolean respond)
    {
        this.initialize = reply(McpBenchMessages.httpResponseEx(httpTypeId, 200, "application/json", HTTP_SESSION_ID),
            McpBenchMessages.INITIALIZE_RESULT, 0);
        this.initialized = reply(McpBenchMessages.httpResponseEx(httpTypeId, 202, null, null), null, 0);
        this.shutdown = reply(McpBenchMessages.httpResponseEx(httpTypeId, 200, null, null), null, 0);
        this.toolsCall = respond
            ? reply(McpBenchMessages.httpResponseEx(httpTypeId, 200, "application/json", null),
                McpBenchMessages.toolsCallResponse(size), streaming ? STREAMING_FRAGMENT_LENGTH : 0)
            : new Responder(Trigger.END, null);
        this.toolsList = respond
            ? reply(McpBenchMessages.httpResponseEx(httpTypeId, 200, "application/json", null),
                McpBenchMessages.toolsListResponse(size), streaming ? STREAMING_FRAGMENT_LENGTH : 0)
            : new Responder(Trigger.END, null);
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

        delete = false;
        session = false;
        contentLength = 0;

        httpBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit())
            .headers()
            .forEach(this::scan);

        final Responder responder;

        if (delete)
        {
            responder = shutdown;
        }
        else if (!session)
        {
            responder = initialize;
        }
        else if (contentLength == INITIALIZED_LENGTH)
        {
            responder = initialized;
        }
        else if (contentLength == TOOLS_LIST_LENGTH)
        {
            responder = toolsList;
        }
        else
        {
            responder = toolsCall;
        }

        return responder.newStream(msgTypeId, buffer, index, length, sender);
    }

    private void scan(
        HttpHeaderFW header)
    {
        final DirectBufferEx name = header.name().value();
        final int nameLength = header.name().length();
        final int valueLength = header.value().length();

        if (nameLength == 7 && name.getByte(1) == 'm')
        {
            delete = valueLength == 6;
        }
        else if (nameLength == 14 && name.getByte(0) == 'm')
        {
            session = true;
        }
        else if (nameLength == 14 && name.getByte(0) == 'c')
        {
            final DirectBufferEx value = header.value().value();

            for (int i = 0; i < valueLength; i++)
            {
                contentLength = contentLength * 10 + value.getByte(i) - '0';
            }
        }
    }

    private static Responder reply(
        byte[] beginEx,
        String body,
        int fragmentLength)
    {
        return new Responder(Trigger.END, body != null
            ? new Response(beginEx, new byte[][] { new byte[0] }, new byte[][] { McpBenchMessages.bytes(body) }, new byte[0],
                fragmentLength)
            : new Response(beginEx, new byte[0][], new byte[0][], new byte[0], 0));
    }
}
