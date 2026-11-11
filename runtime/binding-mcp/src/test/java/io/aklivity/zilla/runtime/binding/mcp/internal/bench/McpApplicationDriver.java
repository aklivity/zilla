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

import java.util.function.BooleanSupplier;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

/**
 * Sends the application stream requests of an {@code mcp} client to a proxy or client binding: a lifecycle
 * stream that holds the session, and one stream per request. A request ends only after its reply has ended,
 * so the request side stays open for frames that may still flow on it while the reply is sent. Request
 * extensions are built ahead of time for the {@code ids} session identifiers that {@link McpBenchSessions}
 * cycles through, so a request allocates nothing in the driver.
 */
final class McpApplicationDriver
{
    private final EngineBench bench;
    private final long appId;
    private final int mask;
    private final UnsafeBufferEx lifecycleEx;
    private final UnsafeBufferEx[] callEx;
    private final UnsafeBufferEx[] listEx;
    private final UnsafeBufferEx callBody;
    private final FrameCounter lifecycleReplies = new FrameCounter();
    private final BooleanSupplier established = this::established;

    private int created;
    private int expectedReplies;

    McpApplicationDriver(
        EngineBench bench,
        long appId,
        int mcpTypeId,
        int ids,
        String size,
        boolean requests)
    {
        this.bench = bench;
        this.appId = appId;
        this.mask = ids - 1;
        this.lifecycleEx = McpBenchMessages.lifecycleEx(mcpTypeId);
        this.callEx = new UnsafeBufferEx[requests ? ids : 0];
        this.listEx = new UnsafeBufferEx[requests ? ids : 0];
        this.callBody = McpBenchMessages.buffer(McpBenchMessages.toolsCallParams(size));

        for (int ordinal = 0; requests && ordinal < ids; ordinal++)
        {
            final String sessionId = McpBenchSessions.identifier(ordinal, McpBenchSessions.EXTERNAL_AFFINITY);

            callEx[ordinal] = McpBenchMessages.toolsCallEx(mcpTypeId, sessionId, callBody.capacity());
            listEx[ordinal] = McpBenchMessages.toolsListEx(mcpTypeId, sessionId);
        }
    }

    Client open()
    {
        final Client lifecycle = bench.newClient(appId, lifecycleReplies);

        expectedReplies = lifecycleReplies.begins() + 1;
        lifecycle.begin(lifecycleEx, 0, lifecycleEx.capacity());
        lifecycle.replyWindow(DEFAULT_WINDOW);

        if (!bench.pollUntil(established))
        {
            throw new IllegalStateException("session not established");
        }

        created++;

        return lifecycle;
    }

    void close(
        Client lifecycle)
    {
        lifecycle.end();
        lifecycle.awaitReplyEnd();
    }

    void handshake()
    {
        close(open());
    }

    void exchangeToolsCall(
        int ordinal)
    {
        completeAfterReply(toolsCall(ordinal));
    }

    void exchangeToolsList(
        int ordinal)
    {
        completeAfterReply(toolsList(ordinal));
    }

    Client toolsCall(
        int ordinal)
    {
        final Client client = bench.newClient(appId, MessageConsumer.NOOP);

        client.begin(callEx[ordinal & mask], 0, callEx[ordinal & mask].capacity());
        client.replyWindow(DEFAULT_WINDOW);
        client.send(callBody, 0, callBody.capacity());

        return client;
    }

    Client toolsList(
        int ordinal)
    {
        final Client client = bench.newClient(appId, MessageConsumer.NOOP);

        client.begin(listEx[ordinal & mask], 0, listEx[ordinal & mask].capacity());
        client.replyWindow(DEFAULT_WINDOW);

        return client;
    }

    private void completeAfterReply(
        Client client)
    {
        client.awaitReplyEnd();
        client.end();
    }

    private boolean established()
    {
        return lifecycleReplies.begins() >= expectedReplies;
    }
}
