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
package io.aklivity.zilla.runtime.binding.llm.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineBench.DEFAULT_WINDOW;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;

/**
 * The engine, the caller and the mock provider a client benchmark of one dialect drives. A dialect supplies the
 * configuration, the canonical begin, the stream options and the provider response; the caller sends canonical
 * sections, advises the flush that triggers the provider request, and receives the canonical response.
 * {@code exchange} fails when the reply is shorter than the text, so a request the client rejects is never
 * measured as a fast one.
 */
@State(Scope.Benchmark)
public abstract class LlmClientFixture
{
    static final int IN_FLIGHT = 5_000;

    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int FRAME_BUFFER_CAPACITY = 64 * 1024;
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int INITIAL_STATE = StreamState.openInitial(0);
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);

    @Param({"small", "10k"})
    public String size;

    @Param({"false", "true"})
    public boolean streaming;

    @Param({"text", "toolCall"})
    public String request;

    @Param({"text", "toolCall"})
    public String response;

    final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

    private final boolean respond;
    private final MutableDirectBufferEx frameBuffer = new UnsafeBufferEx(new byte[FRAME_BUFFER_CAPACITY]);
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private EngineBench bench;
    private int textLength;
    private long appId;
    private UnsafeBufferEx beginEx;
    private UnsafeBufferEx[] sectionEx;
    private UnsafeBufferEx[] sectionPayload;

    LlmClientFixture(
        boolean respond)
    {
        this.respond = respond;
    }

    abstract String configuration();

    abstract byte[] beginEx(
        int llmTypeId);

    abstract String streamOptions();

    abstract Response response(
        String text,
        boolean toolCall,
        int httpTypeId);

    @Setup(Level.Trial)
    public void init() throws Exception
    {
        textLength = LlmBenchSupport.textLength(size);

        final String text = LlmBenchSupport.base64(textLength);

        bench = new EngineBench()
            .directory("target/zilla-bench")
            .external("net0")
            .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
            .start(configuration());

        final int llmTypeId = bench.labelId("llm");

        beginEx = new UnsafeBufferEx(beginEx(llmTypeId));
        appId = bench.bindingId("app0");

        final List<UnsafeBufferEx> exs = new ArrayList<>();
        final List<UnsafeBufferEx> payloads = new ArrayList<>();

        if (streaming)
        {
            LlmBenchSupport.add(exs, payloads, LlmBenchSupport.controlEx(llmTypeId), "{\"stream\":true}");
        }

        if ("toolCall".equals(request))
        {
            LlmBenchSupport.add(exs, payloads, LlmBenchSupport.toolCallEx(llmTypeId),
                String.format("{\"arguments\":{\"query\":\"%s\"}}", text));
        }
        else
        {
            LlmBenchSupport.add(exs, payloads, LlmBenchSupport.userTextEx(llmTypeId),
                String.format("{\"text\":\"%s\"}", text));
        }

        if (streaming)
        {
            LlmBenchSupport.add(exs, payloads, LlmBenchSupport.controlEx(llmTypeId), streamOptions());
        }

        sectionEx = exs.toArray(UnsafeBufferEx[]::new);
        sectionPayload = payloads.toArray(UnsafeBufferEx[]::new);

        bench.newServer("net0", new Responder(Trigger.END,
            respond ? response(text, "toolCall".equals(response), bench.labelId("http")) : null));
    }

    @TearDown(Level.Trial)
    public void destroy() throws Exception
    {
        inflight.forEach(Client::abort);
        bench.close();
    }

    void exchange()
    {
        final Client client = open();
        client.awaitReplyEnd();

        if (client.replyBytes() < textLength)
        {
            throw new IllegalStateException(String.format("reply of %d bytes is shorter than its %d chars of text",
                client.replyBytes(), textLength));
        }

        client.end();
    }

    Client open()
    {
        final Client client = bench.newClient(appId, MessageConsumer.NOOP);

        client.begin(beginEx, 0, beginEx.capacity());
        client.replyWindow(DEFAULT_WINDOW);

        long sequence = 0L;
        for (int section = 0; section < sectionEx.length; section++)
        {
            final UnsafeBufferEx payload = sectionPayload[section];
            final UnsafeBufferEx extension = sectionEx[section];

            int offset = 0;
            while (offset < payload.capacity())
            {
                final int length = Math.min(awaitCredit(client), payload.capacity() - offset);
                final int flags = (offset == 0 ? FLAGS_INIT : 0) | (offset + length == payload.capacity() ? FLAGS_FIN : 0);

                data(client, sequence, flags, payload, offset, length, extension, offset == 0 ? extension.capacity() : 0);

                offset += length;
                sequence += length;
            }
        }

        flush(client, sequence);

        return client;
    }

    private int awaitCredit(
        Client client)
    {
        final long deadline = System.nanoTime() + TIMEOUT_NANOS;

        int credit = client.initialCredit();
        while (credit <= 0)
        {
            if (bench.poll() == 0)
            {
                Thread.onSpinWait();
            }

            if (System.nanoTime() > deadline)
            {
                throw new IllegalStateException("request credit timed out");
            }

            credit = client.initialCredit();
        }

        return credit;
    }

    private void data(
        Client client,
        long sequence,
        int flags,
        UnsafeBufferEx payload,
        int offset,
        int length,
        UnsafeBufferEx extension,
        int extensionLength)
    {
        final DataFW data = dataRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(client.originId())
            .routedId(client.routedId())
            .streamId(client.initialId())
            .sequence(sequence)
            .acknowledge(sequence)
            .state(INITIAL_STATE)
            .maximum(DEFAULT_WINDOW)
            .traceId(0L)
            .authorization(0L)
            .flags(flags)
            .budgetId(0L)
            .reserved(length)
            .payload(payload, offset, length)
            .extension(extension, 0, extensionLength)
            .build();

        client.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void flush(
        Client client,
        long sequence)
    {
        final FlushFW flush = flushRW.wrap(frameBuffer, 0, frameBuffer.capacity())
            .originId(client.originId())
            .routedId(client.routedId())
            .streamId(client.initialId())
            .sequence(sequence)
            .acknowledge(sequence)
            .state(INITIAL_STATE)
            .maximum(DEFAULT_WINDOW)
            .traceId(0L)
            .authorization(0L)
            .budgetId(0L)
            .reserved(0)
            .extension(frameBuffer, 0, 0)
            .build();

        client.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }
}
