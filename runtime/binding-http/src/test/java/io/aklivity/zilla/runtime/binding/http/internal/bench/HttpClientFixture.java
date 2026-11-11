/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

/**
 * Drives an http client through the real engine, behind a {@link NetPeer} that plays the server on the
 * network side. An {@code exchange} is one whole request sent by the application and a response received to
 * its end; the operation completes once up to {@code concurrency - 1} other requests are still in flight.
 * A {@code leave} opens a request, sends it completely and leaves it in flight while the peer withholds
 * the response.
 */
@State(Scope.Benchmark)
public abstract class HttpClientFixture
{
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;

    @Param({"none", "small", "10k"})
    public String requestBody;

    @Param({"none", "small", "10k"})
    public String responseBody;

    @Param({"0", "10"})
    public int headers;

    @Param({"false", "true"})
    public boolean streaming;

    final List<Client> inflight = new ArrayList<>();

    private final boolean respond;
    private final FrameCounter replies = new FrameCounter();
    private final BooleanSupplier canOpen = this::canOpen;

    private EngineBench bench;
    private NetPeer peer;
    private long appId;
    private UnsafeBufferEx beginEx;
    private UnsafeBufferEx body;
    private int started;

    HttpClientFixture(
        boolean respond)
    {
        this.respond = respond;
    }

    abstract String configuration();

    abstract int concurrency();

    abstract NetPeer newPeer(
        int headerCount,
        int bodyLength,
        boolean streaming,
        boolean respond);

    @Setup(Level.Trial)
    public void init() throws Exception
    {
        bench = new EngineBench()
            .directory("target/zilla-bench")
            .external("net0")
            .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
            .start(configuration());

        final int requestLength = BenchMessages.bodyLength(requestBody);
        final int responseLength = BenchMessages.bodyLength(responseBody);

        beginEx = BenchMessages.requestBeginEx(bench.labelId("http"), headers, requestLength);
        body = new UnsafeBufferEx(BenchMessages.body(requestLength));
        appId = bench.bindingId("app0");

        peer = newPeer(headers, responseLength, streaming, respond);
        bench.newServer("net0", peer);

        for (int request = 0; respond && request < concurrency(); request++)
        {
            open(replies);
        }

        await(() -> replies.ends() == started);
    }

    @TearDown(Level.Trial)
    public void destroy() throws Exception
    {
        final int connections = peer.opened();
        final int ends = replies.ends();

        inflight.forEach(Client::abort);
        bench.close();

        if (connections == 0 || replies.aborts() != 0 || replies.resets() != 0)
        {
            throw new IllegalStateException(String.format("exchange failed: connections=%d ends=%d aborts=%d resets=%d",
                connections, ends, replies.aborts(), replies.resets()));
        }
    }

    final void exchange()
    {
        await(canOpen);

        open(replies);
    }

    final Client open(
        MessageConsumer reply)
    {
        final Client client = bench.newClient(appId, reply);

        client.begin(beginEx, 0, beginEx.capacity());
        client.replyWindow(DEFAULT_WINDOW);

        if (body.capacity() != 0)
        {
            client.send(body, 0, body.capacity());
        }

        client.end();
        started++;

        return client;
    }

    final void leave(
        int requests,
        int onWire)
    {
        for (int request = 0; request < requests; request++)
        {
            inflight.add(open(MessageConsumer.NOOP));
        }

        await(() -> peer.received() >= onWire);
    }

    private boolean canOpen()
    {
        return started - replies.ends() < concurrency();
    }

    final void await(
        BooleanSupplier condition)
    {
        if (!bench.pollUntil(condition) || replies.aborts() != 0 || replies.resets() != 0)
        {
            throw new IllegalStateException("exchange did not complete");
        }
    }
}
