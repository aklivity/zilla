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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.specs.binding.llm.internal.LlmFunctions;

/**
 * The engine, the mock provider and the request a server benchmark of one dialect drives. A dialect supplies the
 * configuration, the request path, headers and body; the mock provider replays a pre-built canonical response.
 * {@code exchange} fails when the reply is shorter than the text, so a request the server rejects is never
 * measured as a fast one.
 */
@State(Scope.Benchmark)
public abstract class LlmServerFixture
{
    static final int IN_FLIGHT = 5_000;

    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final String USAGE =
        "{\"openai/ccs:usage\":{\"prompt_tokens\":25,\"completion_tokens\":15,\"total_tokens\":40}}";

    @Param({"small", "10k"})
    public String size;

    @Param({"false", "true"})
    public boolean streaming;

    @Param({"text", "toolCall"})
    public String request;

    final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

    private final boolean respond;

    private EngineBench bench;
    private int textLength;
    private long netId;
    private UnsafeBufferEx beginEx;
    private UnsafeBufferEx requestBody;

    LlmServerFixture(
        boolean respond)
    {
        this.respond = respond;
    }

    abstract String configuration();

    abstract String path();

    abstract String requestBody(
        String text);

    void headers(
        HttpBeginExFW.Builder builder)
    {
    }

    @Setup(Level.Trial)
    public void init() throws Exception
    {
        textLength = LlmBenchSupport.textLength(size);

        final String text = LlmBenchSupport.base64(textLength);

        bench = new EngineBench()
            .directory("target/zilla-bench")
            .external("app0")
            .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
            .start(configuration());

        beginEx = httpBeginEx(bench.labelId("http"));
        requestBody = new UnsafeBufferEx(requestBody(text).getBytes(UTF_8));
        netId = bench.bindingId("net0");

        bench.newServer("app0", new Responder(Trigger.FLUSH, respond ? response(bench.labelId("llm"), text) : null));
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
    }

    Client open()
    {
        final Client client = bench.newClient(netId, MessageConsumer.NOOP);

        client.begin(beginEx, 0, beginEx.capacity());
        client.replyWindow(DEFAULT_WINDOW);
        client.send(requestBody, 0, requestBody.capacity());
        client.end();

        return client;
    }

    private Response response(
        int llmTypeId,
        String text)
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

        return new Response(
            beginEx,
            new byte[][] { textEx, usageEx },
            new byte[][] { String.format("{\"text\":\"%s\"}", text).getBytes(UTF_8), USAGE.getBytes(UTF_8) },
            endEx,
            streaming ? LlmBenchSupport.STREAMING_FRAGMENT_LENGTH : 0);
    }

    private UnsafeBufferEx httpBeginEx(
        int httpTypeId)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);

        final HttpBeginExFW.Builder builder = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(":method").value("POST"))
            .headersItem(h -> h.name(":scheme").value("http"))
            .headersItem(h -> h.name(":authority").value("example.com:8080"))
            .headersItem(h -> h.name(":path").value(path()));

        headers(builder);

        final HttpBeginExFW httpBeginEx = builder
            .headersItem(h -> h.name("content-type").value("application/json"))
            .build();

        final byte[] encoded = new byte[httpBeginEx.sizeof()];
        buffer.getBytes(0, encoded);

        return new UnsafeBufferEx(encoded);
    }
}
