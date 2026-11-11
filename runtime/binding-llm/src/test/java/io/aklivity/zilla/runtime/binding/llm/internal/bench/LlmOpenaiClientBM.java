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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.test.EngineBench;
import io.aklivity.zilla.runtime.engine.test.EngineBench.Client;
import io.aklivity.zilla.runtime.engine.test.Responder;
import io.aklivity.zilla.runtime.engine.test.Responder.Response;
import io.aklivity.zilla.runtime.engine.test.Responder.Trigger;
import io.aklivity.zilla.runtime.engine.test.RetainedHeapProfiler;
import io.aklivity.zilla.specs.binding.llm.internal.LlmFunctions;

/**
 * Drives an {@code llm} openai client through the real engine, between a caller that sends canonical llm
 * sections and a mock provider that replays a pre-built openai response. Each {@code exchange} operation is
 * one whole request: the canonical request is sent, the flush that triggers the provider request is advised
 * and the canonical response is received to its end. Each {@code leave} operation opens one request, sends
 * it completely and leaves it in flight while the provider withholds the response.
 * <p>
 * The provider response is a chat completion for {@code dialect} {@code auto} and {@code chat}, which differ
 * only in whether the binding declares its apis. {@code request} is the canonical section the caller sends and
 * {@code response} the content the provider returns, each either text or a tool call. The openai responses
 * client has its own benchmark, {@code LlmOpenaiResponsesClientBM}.
 * <p>
 * Read {@code gc.alloc.rate.norm} (B/op) from the {@code gc} profiler for {@code exchange}, and
 * {@code retained.heap.norm} (B/op) from {@link RetainedHeapProfiler} for {@code leave}. {@code leave} does
 * not depend on {@code streaming} or {@code response}, so run it with {@code -p streaming=false -p response=text}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class LlmOpenaiClientBM
{
    private static final String CONFIGURATION_AUTO = "io/aklivity/zilla/specs/binding/llm/config/client.openai.yaml";
    private static final String CONFIGURATION_CHAT = "io/aklivity/zilla/specs/binding/llm/config/client.openai.chat.yaml";
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final int STREAMING_FRAGMENT_LENGTH = 256;
    private static final int IN_FLIGHT = 5_000;
    private static final int SMALL_LENGTH = 400;
    private static final int LARGE_LENGTH = 10_000;
    private static final int FRAME_BUFFER_CAPACITY = 64 * 1024;
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_FIN = 0x01;
    private static final int INITIAL_STATE = StreamState.openInitial(0);
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(10L);
    private static final byte[] NO_BYTES = new byte[0];
    private static final String CHUNK = "{\"id\":\"chatcmpl_2\",\"object\":\"chat.completion.chunk\",\"model\":\"gpt-4\",";
    private static final String USAGE =
        "{\"prompt_tokens\":25,\"completion_tokens\":15,\"total_tokens\":40}";
    private static final String ARGUMENTS_PREFIX = "{\\\"query\\\":\\\"";
    private static final String ARGUMENTS_SUFFIX = "\\\"}";

    @Benchmark
    public void exchange(
        Replying fixture)
    {
        fixture.exchange();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(MILLISECONDS)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(IN_FLIGHT)
    public void leave(
        Withholding fixture)
    {
        for (int request = 0; request < IN_FLIGHT; request++)
        {
            fixture.inflight.add(fixture.open());
        }
    }

    @State(Scope.Benchmark)
    public abstract static class Fixture
    {
        @Param({"small", "10k"})
        public String size;

        @Param({"false", "true"})
        public boolean streaming;

        @Param({"text", "toolCall"})
        public String request;

        @Param({"text", "toolCall"})
        public String response;

        @Param({"auto", "chat"})
        public String dialect;

        final List<Client> inflight = new ArrayList<>(IN_FLIGHT * 4);

        private final boolean respond;
        private final MutableDirectBufferEx frameBuffer = new UnsafeBufferEx(new byte[FRAME_BUFFER_CAPACITY]);
        private final DataFW.Builder dataRW = new DataFW.Builder();
        private final FlushFW.Builder flushRW = new FlushFW.Builder();

        private EngineBench bench;
        private long appId;
        private UnsafeBufferEx beginEx;
        private UnsafeBufferEx[] sectionEx;
        private UnsafeBufferEx[] sectionPayload;

        Fixture(
            boolean respond)
        {
            this.respond = respond;
        }

        @Setup(Level.Trial)
        public void init() throws Exception
        {
            final String text = base64("10k".equals(size) ? LARGE_LENGTH : SMALL_LENGTH);

            bench = new EngineBench()
                .directory("target/zilla-bench")
                .external("net0")
                .configure(ENGINE_STREAMS_BUFFER_CAPACITY, STREAMS_BUFFER_CAPACITY)
                .start("chat".equals(dialect) ? CONFIGURATION_CHAT : CONFIGURATION_AUTO);

            final int llmTypeId = bench.labelId("llm");

            beginEx = new UnsafeBufferEx(LlmFunctions.beginEx()
                .typeId(llmTypeId)
                .dialect("openai/ccs")
                .model("gpt-4")
                .build());
            appId = bench.bindingId("app0");

            final List<UnsafeBufferEx> exs = new ArrayList<>();
            final List<UnsafeBufferEx> payloads = new ArrayList<>();

            if (streaming)
            {
                add(exs, payloads, controlEx(llmTypeId), "{\"stream\":true}");
            }

            if ("toolCall".equals(request))
            {
                add(exs, payloads, toolCallEx(llmTypeId), String.format("{\"arguments\":{\"query\":\"%s\"}}", text));
            }
            else
            {
                add(exs, payloads, userTextEx(llmTypeId), String.format("{\"text\":\"%s\"}", text));
            }

            if (streaming)
            {
                add(exs, payloads, controlEx(llmTypeId),
                    "{\"stream_options\":{\"openai:include_obfuscation\":false,\"include_usage\":true}}");
            }

            sectionEx = exs.toArray(UnsafeBufferEx[]::new);
            sectionPayload = payloads.toArray(UnsafeBufferEx[]::new);

            bench.newServer("net0", new Responder(Trigger.END, respond ? response(text) : null));
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

        private Response response(
            String text)
        {
            final boolean toolCall = "toolCall".equals(response);
            final List<byte[]> events = new ArrayList<>();
            final int httpTypeId = bench.labelId("http");

            if (streaming)
            {
                chatStreamingEvents(events, text, toolCall);
            }
            else
            {
                events.add(chatCompletion(text, toolCall).getBytes(UTF_8));
            }

            final byte[][] eventExtensions = new byte[events.size()][];
            Arrays.fill(eventExtensions, NO_BYTES);

            return new Response(
                httpResponseBeginEx(httpTypeId, streaming ? "text/event-stream" : "application/json"),
                eventExtensions,
                events.toArray(byte[][]::new),
                NO_BYTES,
                0);
        }

        private static String chatCompletion(
            String text,
            boolean toolCall)
        {
            final String message = toolCall
                ? String.format("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_1\"," +
                    "\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"query\\\":\\\"%s\\\"}\"}}]}",
                    text)
                : String.format("{\"role\":\"assistant\",\"content\":\"%s\"}", text);

            return String.format("{\"id\":\"chatcmpl_2\",\"object\":\"chat.completion\",\"model\":\"gpt-4\"," +
                "\"choices\":[{\"index\":0,\"message\":%s,\"finish_reason\":\"%s\"}],\"usage\":%s}",
                message, toolCall ? "tool_calls" : "stop", USAGE);
        }

        private static void chatStreamingEvents(
            List<byte[]> events,
            String text,
            boolean toolCall)
        {
            events.add((CHUNK + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}")
                .getBytes(UTF_8));

            if (toolCall)
            {
                events.add((CHUNK + "\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\"," +
                    "\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}")
                    .getBytes(UTF_8));
                events.add(toolCallDelta(ARGUMENTS_PREFIX));
            }

            for (String fragment : fragments(text))
            {
                events.add(toolCall
                    ? toolCallDelta(fragment)
                    : (CHUNK + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + fragment +
                        "\"},\"finish_reason\":null}]}").getBytes(UTF_8));
            }

            if (toolCall)
            {
                events.add(toolCallDelta(ARGUMENTS_SUFFIX));
            }

            events.add((CHUNK + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"" +
                (toolCall ? "tool_calls" : "stop") + "\"}]}").getBytes(UTF_8));
            events.add((CHUNK + "\"choices\":[],\"usage\":" + USAGE + "}").getBytes(UTF_8));
            events.add("[DONE]".getBytes(UTF_8));
        }

        private static List<String> fragments(
            String text)
        {
            final List<String> fragments = new ArrayList<>();

            for (int offset = 0; offset < text.length(); offset += STREAMING_FRAGMENT_LENGTH)
            {
                fragments.add(text.substring(offset, Math.min(offset + STREAMING_FRAGMENT_LENGTH, text.length())));
            }

            return fragments;
        }

        private static byte[] toolCallDelta(
            String arguments)
        {
            return (CHUNK + "\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":" +
                "{\"arguments\":\"" + arguments + "\"}}]},\"finish_reason\":null}]}").getBytes(UTF_8);
        }

        private static void add(
            List<UnsafeBufferEx> exs,
            List<UnsafeBufferEx> payloads,
            byte[] extension,
            String payload)
        {
            exs.add(new UnsafeBufferEx(extension));
            payloads.add(new UnsafeBufferEx(payload.getBytes(UTF_8)));
        }

        private static byte[] controlEx(
            int llmTypeId)
        {
            return LlmFunctions.dataEx().typeId(llmTypeId).type("CONTROL").message(-1).build();
        }

        private static byte[] userTextEx(
            int llmTypeId)
        {
            return LlmFunctions.dataEx().typeId(llmTypeId).type("USER_TEXT").message(0).build();
        }

        private static byte[] toolCallEx(
            int llmTypeId)
        {
            return LlmFunctions.dataEx()
                .typeId(llmTypeId)
                .type("TOOL_CALL")
                .message(0)
                .attribute("tool-name", "lookup")
                .attribute("tool-call-id", "call_1")
                .build();
        }

        private static String base64(
            int length)
        {
            final byte[] random = new byte[length];
            new Random(0L).nextBytes(random);

            return Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
        }

        private static byte[] httpResponseBeginEx(
            int httpTypeId,
            String contentType)
        {
            final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[256]);

            final HttpBeginExFW httpBeginEx = new HttpBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(":status").value("200"))
                .headersItem(h -> h.name("content-type").value(contentType))
                .build();

            final byte[] encoded = new byte[httpBeginEx.sizeof()];
            buffer.getBytes(0, encoded);

            return encoded;
        }
    }

    @State(Scope.Benchmark)
    public static class Replying extends Fixture
    {
        public Replying()
        {
            super(true);
        }
    }

    @State(Scope.Benchmark)
    public static class Withholding extends Fixture
    {
        public Withholding()
        {
            super(false);
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(LlmOpenaiClientBM.class.getSimpleName())
            .addProfiler("gc")
            .addProfiler(RetainedHeapProfiler.class)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
