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

import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;

/**
 * Sends the HTTP-shaped requests of an {@code mcp} client to the binding that faces the network. Request
 * headers are built ahead of time for the {@code ids} session identifiers that {@link McpBenchSessions}
 * cycles through, so a request allocates nothing in the driver.
 */
final class McpHttpDriver
{
    private final EngineBench bench;
    private final long netId;
    private final int mask;
    private final UnsafeBufferEx initializeEx;
    private final UnsafeBufferEx initializeBody = McpBenchMessages.buffer(McpBenchMessages.INITIALIZE);
    private final UnsafeBufferEx initializedBody = McpBenchMessages.buffer(McpBenchMessages.INITIALIZED);
    private final UnsafeBufferEx[] initializedEx;
    private final UnsafeBufferEx[] deleteEx;
    private final UnsafeBufferEx[] callEx;
    private final UnsafeBufferEx[] listEx;
    private final UnsafeBufferEx callBody;
    private final UnsafeBufferEx listBody = McpBenchMessages.buffer(McpBenchMessages.TOOLS_LIST);

    private int created;

    McpHttpDriver(
        EngineBench bench,
        long netId,
        int httpTypeId,
        int ids,
        String size,
        boolean progress,
        boolean shutdown,
        boolean requests)
    {
        this.bench = bench;
        this.netId = netId;
        this.mask = ids - 1;
        this.initializeEx = McpBenchMessages.httpRequestEx(httpTypeId, "POST", null, 0);
        this.initializedEx = new UnsafeBufferEx[ids];
        this.deleteEx = new UnsafeBufferEx[shutdown ? ids : 0];
        this.callEx = new UnsafeBufferEx[requests ? ids : 0];
        this.listEx = new UnsafeBufferEx[requests ? ids : 0];

        final String request = progress
            ? McpBenchMessages.toolsCallProgressRequest(size)
            : McpBenchMessages.toolsCallRequest(size);
        this.callBody = McpBenchMessages.buffer(request);

        for (int ordinal = 0; ordinal < ids; ordinal++)
        {
            final String sessionId = McpBenchSessions.identifier(ordinal, 0L);

            initializedEx[ordinal] = McpBenchMessages.httpRequestEx(httpTypeId, "POST", sessionId, 0);

            if (shutdown)
            {
                deleteEx[ordinal] = McpBenchMessages.httpRequestEx(httpTypeId, "DELETE", sessionId, 0);
            }

            if (requests)
            {
                callEx[ordinal] = McpBenchMessages.httpRequestEx(httpTypeId, "POST", sessionId, callBody.capacity());
                listEx[ordinal] = McpBenchMessages.httpRequestEx(httpTypeId, "POST", sessionId, 0);
            }
        }
    }

    void initialize()
    {
        post(initializeEx, initializeBody).awaitReplyEnd();
        post(initializedEx[created & mask], initializedBody).awaitReplyEnd();
        created++;
    }

    void handshake()
    {
        initialize();
        shutdown(created - 1);
    }

    private void shutdown(
        int ordinal)
    {
        final Client client = bench.newClient(netId, MessageConsumer.NOOP);

        client.begin(deleteEx[ordinal & mask], 0, deleteEx[ordinal & mask].capacity());
        client.replyWindow(DEFAULT_WINDOW);
        client.end();
        client.awaitReplyEnd();
    }

    Client toolsCall(
        int ordinal)
    {
        return post(callEx[ordinal & mask], callBody);
    }

    Client toolsList(
        int ordinal)
    {
        return post(listEx[ordinal & mask], listBody);
    }

    private Client post(
        UnsafeBufferEx beginEx,
        UnsafeBufferEx body)
    {
        final Client client = bench.newClient(netId, MessageConsumer.NOOP);

        client.begin(beginEx, 0, beginEx.capacity());
        client.replyWindow(DEFAULT_WINDOW);
        client.send(body, 0, body.capacity());
        client.end();

        return client;
    }
}
