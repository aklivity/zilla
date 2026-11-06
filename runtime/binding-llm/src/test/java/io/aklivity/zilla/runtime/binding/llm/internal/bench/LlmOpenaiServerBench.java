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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Base64;
import java.util.Random;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;
import io.aklivity.zilla.specs.binding.llm.internal.LlmFunctions;

final class LlmOpenaiServerBench implements AutoCloseable
{
    private static final String CONFIGURATION = "io/aklivity/zilla/specs/binding/llm/config/server.yaml";
    private static final int WINDOW = 8 * 1024;
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int STREAMING_FRAGMENT_LENGTH = 256;
    private static final int LARGE_LENGTH = 10_000;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);

    private static final String SMALL_TEXT = "Hello! How can I help you today?";
    private static final String USAGE =
        "{\"openai/ccs:usage\":{\"prompt_tokens\":25,\"completion_tokens\":15,\"total_tokens\":40}}";

    private final EngineBench bench = new EngineBench();
    private final FrameCounter replies = new FrameCounter();
    private final LlmResponder responder;
    private final UnsafeBufferEx beginEx;
    private final UnsafeBufferEx requestBody;
    private final long netId;

    LlmOpenaiServerBench(
        String size,
        boolean streaming,
        boolean respond) throws Exception
    {
        final boolean large = "10k".equals(size);
        final String text = large ? base64(LARGE_LENGTH) : SMALL_TEXT;

        bench.directory("target/zilla-bench")
             .external("app0")
             .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
             .start(CONFIGURATION);

        this.beginEx = httpBeginEx(bench.labelId("http"));
        this.requestBody = new UnsafeBufferEx(requestBody(text, streaming).getBytes(UTF_8));
        this.responder = new LlmResponder(WINDOW, respond ? response(bench.labelId("llm"), text, streaming) : null);
        this.netId = bench.bindingId("net0");

        bench.newServer("app0", responder);
    }

    EngineBench bench()
    {
        return bench;
    }

    FrameCounter replies()
    {
        return replies;
    }

    FrameCounter requests()
    {
        return responder.counter();
    }

    void exchange()
    {
        final int ends = replies.ends() + 1;

        open();
        awaitReplyEnds(ends);
    }

    Client open()
    {
        final Client client = bench.newClient(netId, replies);
        final long deadline = System.nanoTime() + TIMEOUT_NANOS;

        client.begin(beginEx, 0, beginEx.capacity());
        client.replyWindow(WINDOW);

        int offset = 0;
        while (offset < requestBody.capacity())
        {
            bench.poll();

            final int credit = client.initialCredit();
            if (credit > 0)
            {
                final int length = Math.min(credit, requestBody.capacity() - offset);
                client.data(requestBody, offset, length);
                offset += length;
            }

            checkProgress(deadline);
        }

        client.end();

        return client;
    }

    void awaitReplyEnds(
        int ends)
    {
        final long deadline = System.nanoTime() + TIMEOUT_NANOS;

        while (replies.ends() < ends)
        {
            bench.poll();
            checkProgress(deadline);
        }
    }

    void awaitRequestFlushes(
        int flushes)
    {
        final long deadline = System.nanoTime() + TIMEOUT_NANOS;

        while (responder.counter().flushes() < flushes)
        {
            bench.poll();
            checkProgress(deadline);
        }
    }

    void awaitRequestAborts(
        int aborts)
    {
        final long deadline = System.nanoTime() + TIMEOUT_NANOS;

        while (responder.counter().aborts() < aborts)
        {
            bench.poll();
            checkProgress(deadline);
        }
    }

    @Override
    public void close() throws Exception
    {
        bench.close();
    }

    private void checkProgress(
        long deadline)
    {
        if (replies.resets() != 0 || replies.aborts() != 0)
        {
            throw new IllegalStateException(String.format("request rejected: resets=%d aborts=%d",
                replies.resets(), replies.aborts()));
        }

        if (System.nanoTime() > deadline)
        {
            throw new IllegalStateException("request timed out");
        }
    }

    private static String base64(
        int length)
    {
        final byte[] random = new byte[length];
        new Random(0L).nextBytes(random);

        return Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
    }

    private static String requestBody(
        String text,
        boolean streaming)
    {
        final String stream = streaming ? "\"stream\":true," : "";

        return String.format("{\"model\":\"gpt-4\",%s\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}", stream, text);
    }

    private static UnsafeBufferEx httpBeginEx(
        int httpTypeId)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);

        final HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":method").value("POST"))
            .headersItem(h -> h.name(":scheme").value("http"))
            .headersItem(h -> h.name(":authority").value("example.com:8080"))
            .headersItem(h -> h.name(":path").value("/v1/chat/completions"))
            .headersItem(h -> h.name("content-type").value("application/json"))
            .build();

        final byte[] encoded = new byte[httpBeginEx.sizeof()];
        buffer.getBytes(0, encoded);

        return new UnsafeBufferEx(encoded);
    }

    private static LlmResponder.Response response(
        int llmTypeId,
        String text,
        boolean streaming)
    {
        final byte[] beginEx = LlmFunctions.beginEx()
            .typeId(llmTypeId)
            .dialect("openai/ccs")
            .model("gpt-4")
            .id("chatcmpl_2")
            .build();
        final byte[] textEx = LlmFunctions.dataEx()
            .typeId(llmTypeId)
            .type("ASSISTANT_TEXT")
            .message(0)
            .build();
        final byte[] usageEx = LlmFunctions.dataEx()
            .typeId(llmTypeId)
            .type("CONTROL")
            .message(-1)
            .build();
        final byte[] endEx = LlmFunctions.endEx()
            .typeId(llmTypeId)
            .inputTokens(25)
            .outputTokens(15)
            .totalTokens(40)
            .finishReason("stop")
            .build();

        return new LlmResponder.Response(
            beginEx,
            new byte[][] { textEx, usageEx },
            new byte[][] { String.format("{\"text\":\"%s\"}", text).getBytes(UTF_8), USAGE.getBytes(UTF_8) },
            endEx,
            streaming ? STREAMING_FRAGMENT_LENGTH : 0);
    }
}
