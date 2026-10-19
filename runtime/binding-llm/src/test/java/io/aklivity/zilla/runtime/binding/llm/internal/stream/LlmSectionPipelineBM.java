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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Measures the canonical conversions of the llm section kinds: tool definition, tool call, inline media,
 * citations, control members and the decoded text of an object-shaped text section. Each benchmark drives one section through
 * its {@code LlmSectionPipeline} (or {@code LlmTextPipeline}), so throughput and, under {@code -prof gc},
 * allocation per operation are reported for the conversion alone. The fragmented variants feed the same
 * section in {@code WINDOW}-byte windows to exercise the carried tail.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class LlmSectionPipelineBM
{
    private static final int WINDOW = 64;
    private static final int OUTPUT = 16 * 1024;
    private static final String DATA = "QUJD".repeat(1024);
    private static final String TEXT = "The quick brown fox jumps over the lazy dog, \"twice\".\n".repeat(32);

    private static final String TOOL_DEFINITION_NATIVE =
        "{\"name\":\"get_weather\",\"description\":\"Get the weather\",\"input_schema\":{\"type\":\"object\"," +
        "\"properties\":{\"city\":{\"type\":\"string\"},\"unit\":{\"type\":\"string\",\"enum\":[\"c\",\"f\"]}}," +
        "\"required\":[\"city\"]},\"cache_control\":{\"type\":\"ephemeral\"}}";
    private static final String TOOL_DEFINITION_CANONICAL =
        "{\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"description\":\"Get the weather\"," +
        "\"parameters\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"},\"unit\":" +
        "{\"type\":\"string\",\"enum\":[\"c\",\"f\"]}},\"required\":[\"city\"]}," +
        "\"anthropic:cache_control\":{\"type\":\"ephemeral\"}}}";

    private static final String TOOL_CALL_NATIVE =
        "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"get_weather\",\"input\":{\"city\":\"Paris\"," +
        "\"unit\":\"c\",\"days\":[1,2,3]},\"cache_control\":{\"type\":\"ephemeral\"}}";
    private static final String TOOL_CALL_CANONICAL =
        "{\"arguments\":{\"city\":\"Paris\",\"unit\":\"c\",\"days\":[1,2,3]}," +
        "\"anthropic:cache_control\":{\"type\":\"ephemeral\"}}";
    private static final String TOOL_CALL_OPENAI_NATIVE =
        "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":" +
        "\"{\\\"city\\\":\\\"Paris\\\",\\\"unit\\\":\\\"c\\\",\\\"days\\\":[1,2,3]}\"}}";
    private static final String TOOL_CALL_OPENAI_CANONICAL =
        "{\"arguments\":{\"city\":\"Paris\",\"unit\":\"c\",\"days\":[1,2,3]}}";

    private static final String IMAGE_NATIVE =
        "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/png\",\"data\":\"" + DATA + "\"}}";
    private static final String IMAGE_CANONICAL =
        "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64," + DATA + "\"}}";

    private static final String CITATIONS_NATIVE =
        "[{\"type\":\"web_search_result_location\",\"url\":\"https://example.com\",\"title\":\"Example\"," +
        "\"encrypted_index\":\"idx\",\"cited_text\":\"blue\"},{\"type\":\"char_location\",\"cited_text\":\"blue\"," +
        "\"document_index\":0,\"start_char_index\":0,\"end_char_index\":4}]";
    private static final String CITATIONS_CANONICAL =
        "[{\"type\":\"url_citation\",\"url_citation\":{\"url\":\"https://example.com\",\"title\":\"Example\"}," +
        "\"anthropic:encrypted_index\":\"idx\",\"anthropic:cited_text\":\"blue\"},{\"anthropic:type\":" +
        "\"char_location\",\"anthropic:cited_text\":\"blue\",\"anthropic:document_index\":0," +
        "\"anthropic:start_char_index\":0,\"anthropic:end_char_index\":4}]";

    private static final String CONTROL_VALUE =
        "{\"user_id\":\"u_1\",\"tags\":[\"a\",\"b\",\"c\"],\"nested\":{\"k\":\"v\",\"n\":[1,2,3]}}";
    private static final String CONTROL_NATIVE = "{\"metadata\":" + CONTROL_VALUE + "}";
    private static final String CONTROL_ANTHROPIC_CANONICAL = "{\"anthropic:metadata\":" + CONTROL_VALUE + "}";
    private static final String CONTROL_OPENAI_CANONICAL = "{\"openai:metadata\":" + CONTROL_VALUE + "}";

    private static final String TEXT_OBJECT = "{\"text\":\"" + TEXT.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n") + "\"}";

    private int produced;

    private final LlmSectionPipeline.Output counter = (buffer, offset, length, last) -> produced += length;
    private final LlmTextPipeline textPipeline = new LlmTextPipeline((buffer, offset, length) -> produced += length);

    private final LlmSectionAttributes toolCallAttributes = new LlmSectionAttributes();
    private final LlmSectionAttributes imageAttributes = new LlmSectionAttributes();
    private final LlmSectionAttributes none = new LlmSectionAttributes();

    private LlmSectionPipeline toolDefinitionToCanonical;
    private LlmSectionPipeline toolDefinitionFromAnthropic;
    private LlmSectionPipeline toolDefinitionFromOpenai;
    private LlmSectionPipeline toolCallToCanonical;
    private LlmSectionPipeline toolCallFromCanonical;
    private LlmSectionPipeline toolCallOpenaiToCanonical;
    private LlmSectionPipeline toolCallOpenaiFromCanonical;
    private LlmSectionPipeline imageToCanonical;
    private LlmSectionPipeline imageFromAnthropic;
    private LlmSectionPipeline citationsToCanonical;
    private LlmSectionPipeline citationsFromAnthropic;
    private LlmSectionPipeline citationsFromOpenai;
    private LlmSectionPipeline controlAnthropicToCanonical;
    private LlmSectionPipeline controlAnthropicFromCanonical;
    private LlmSectionPipeline controlOpenaiToCanonical;
    private LlmSectionPipeline controlOpenaiFromCanonical;

    private UnsafeBufferEx toolDefinitionNative;
    private UnsafeBufferEx toolDefinitionCanonical;
    private UnsafeBufferEx toolCallNative;
    private UnsafeBufferEx toolCallCanonical;
    private UnsafeBufferEx toolCallOpenaiNative;
    private UnsafeBufferEx toolCallOpenaiCanonical;
    private UnsafeBufferEx imageNative;
    private UnsafeBufferEx imageCanonical;
    private UnsafeBufferEx citationsNative;
    private UnsafeBufferEx citationsCanonical;
    private UnsafeBufferEx controlNative;
    private UnsafeBufferEx controlAnthropicCanonical;
    private UnsafeBufferEx controlOpenaiCanonical;
    private UnsafeBufferEx textObject;

    @Setup(Level.Trial)
    public void init()
    {
        toolDefinitionToCanonical = pipeline(new LlmAnthropicToolDefinitionToCanonical());
        toolDefinitionFromAnthropic = pipeline(new LlmAnthropicToolDefinitionFromCanonical());
        toolDefinitionFromOpenai = pipeline(new LlmOpenaiToolDefinitionFromCanonical());
        toolCallToCanonical = pipeline(new LlmAnthropicToolCallToCanonical());
        toolCallFromCanonical = pipeline(new LlmAnthropicToolCallFromCanonical("tool_use"));
        toolCallOpenaiToCanonical = pipeline(new LlmOpenaiToolCallToCanonical());
        toolCallOpenaiFromCanonical = pipeline(new LlmOpenaiToolCallFromCanonical());
        imageToCanonical = pipeline(LlmAnthropicMediaToCanonical.image());
        imageFromAnthropic = pipeline(LlmAnthropicMediaFromCanonical.image());
        citationsToCanonical = pipeline(new LlmAnthropicCitationsToCanonical());
        citationsFromAnthropic = pipeline(new LlmAnthropicCitationsFromCanonical());
        citationsFromOpenai = pipeline(new LlmOpenaiCitationsFromCanonical());
        controlAnthropicToCanonical = pipeline(new LlmAnthropicControlToCanonical());
        controlAnthropicFromCanonical = pipeline(new LlmAnthropicControlFromCanonical());
        controlOpenaiToCanonical = pipeline(new LlmOpenaiControlToCanonical());
        controlOpenaiFromCanonical = pipeline(new LlmOpenaiControlFromCanonical());

        toolCallAttributes.toolCallId("toolu_1");
        toolCallAttributes.toolName("get_weather");
        imageAttributes.source(LlmSectionAttributes.SOURCE_INLINE);
        imageAttributes.mediaType("image/png");

        toolDefinitionNative = buffer(TOOL_DEFINITION_NATIVE);
        toolDefinitionCanonical = buffer(TOOL_DEFINITION_CANONICAL);
        toolCallNative = buffer(TOOL_CALL_NATIVE);
        toolCallCanonical = buffer(TOOL_CALL_CANONICAL);
        toolCallOpenaiNative = buffer(TOOL_CALL_OPENAI_NATIVE);
        toolCallOpenaiCanonical = buffer(TOOL_CALL_OPENAI_CANONICAL);
        imageNative = buffer(IMAGE_NATIVE);
        imageCanonical = buffer(IMAGE_CANONICAL);
        citationsNative = buffer(CITATIONS_NATIVE);
        citationsCanonical = buffer(CITATIONS_CANONICAL);
        controlNative = buffer(CONTROL_NATIVE);
        controlAnthropicCanonical = buffer(CONTROL_ANTHROPIC_CANONICAL);
        controlOpenaiCanonical = buffer(CONTROL_OPENAI_CANONICAL);
        textObject = buffer(TEXT_OBJECT);
    }

    @Benchmark
    public int toolDefinitionToCanonical()
    {
        return run(toolDefinitionToCanonical, none, toolDefinitionNative, toolDefinitionNative.capacity());
    }

    @Benchmark
    public int toolDefinitionToCanonicalFragmented()
    {
        return run(toolDefinitionToCanonical, none, toolDefinitionNative, WINDOW);
    }

    @Benchmark
    public int toolDefinitionFromCanonicalAnthropic()
    {
        return run(toolDefinitionFromAnthropic, none, toolDefinitionCanonical, toolDefinitionCanonical.capacity());
    }

    @Benchmark
    public int toolDefinitionFromCanonicalOpenai()
    {
        return run(toolDefinitionFromOpenai, none, toolDefinitionCanonical, toolDefinitionCanonical.capacity());
    }

    @Benchmark
    public int toolCallToCanonical()
    {
        return run(toolCallToCanonical, toolCallAttributes, toolCallNative, toolCallNative.capacity());
    }

    @Benchmark
    public int toolCallFromCanonical()
    {
        return run(toolCallFromCanonical, toolCallAttributes, toolCallCanonical, toolCallCanonical.capacity());
    }

    @Benchmark
    public int toolCallToCanonicalFragmented()
    {
        return run(toolCallToCanonical, toolCallAttributes, toolCallNative, WINDOW);
    }

    @Benchmark
    public int toolCallFromCanonicalFragmented()
    {
        return run(toolCallFromCanonical, toolCallAttributes, toolCallCanonical, WINDOW);
    }

    @Benchmark
    public int toolCallToCanonicalOpenai()
    {
        return run(toolCallOpenaiToCanonical, toolCallAttributes, toolCallOpenaiNative, toolCallOpenaiNative.capacity());
    }

    @Benchmark
    public int toolCallFromCanonicalOpenai()
    {
        return run(toolCallOpenaiFromCanonical, toolCallAttributes, toolCallOpenaiCanonical,
            toolCallOpenaiCanonical.capacity());
    }

    @Benchmark
    public int toolCallToCanonicalOpenaiFragmented()
    {
        return run(toolCallOpenaiToCanonical, toolCallAttributes, toolCallOpenaiNative, WINDOW);
    }

    @Benchmark
    public int imageInlineToCanonical()
    {
        return run(imageToCanonical, imageAttributes, imageNative, imageNative.capacity());
    }

    @Benchmark
    public int imageInlineToCanonicalFragmented()
    {
        return run(imageToCanonical, imageAttributes, imageNative, WINDOW);
    }

    @Benchmark
    public int imageInlineFromCanonicalAnthropic()
    {
        return run(imageFromAnthropic, imageAttributes, imageCanonical, imageCanonical.capacity());
    }

    @Benchmark
    public int citationsToCanonical()
    {
        return run(citationsToCanonical, none, citationsNative, citationsNative.capacity());
    }

    @Benchmark
    public int citationsFromCanonicalAnthropic()
    {
        return run(citationsFromAnthropic, none, citationsCanonical, citationsCanonical.capacity());
    }

    @Benchmark
    public int citationsFromCanonicalOpenai()
    {
        return run(citationsFromOpenai, none, citationsCanonical, citationsCanonical.capacity());
    }

    @Benchmark
    public int controlToCanonicalAnthropic()
    {
        return run(controlAnthropicToCanonical, none, controlNative, controlNative.capacity());
    }

    @Benchmark
    public int controlToCanonicalAnthropicFragmented()
    {
        return run(controlAnthropicToCanonical, none, controlNative, WINDOW);
    }

    @Benchmark
    public int controlFromCanonicalAnthropic()
    {
        return run(controlAnthropicFromCanonical, none, controlAnthropicCanonical, controlAnthropicCanonical.capacity());
    }

    @Benchmark
    public int controlToCanonicalOpenai()
    {
        return run(controlOpenaiToCanonical, none, controlNative, controlNative.capacity());
    }

    @Benchmark
    public int controlFromCanonicalOpenai()
    {
        return run(controlOpenaiFromCanonical, none, controlOpenaiCanonical, controlOpenaiCanonical.capacity());
    }

    @Benchmark
    public int textDecode()
    {
        return decode(textObject.capacity());
    }

    @Benchmark
    public int textDecodeFragmented()
    {
        return decode(WINDOW);
    }

    private int run(
        LlmSectionPipeline pipeline,
        LlmSectionAttributes attributes,
        UnsafeBufferEx input,
        int window)
    {
        produced = 0;

        pipeline.begin(attributes);

        final int length = input.capacity();

        for (int offset = 0; offset < length; offset += window)
        {
            final int size = Math.min(window, length - offset);

            pipeline.feed(input, offset, size, offset + size == length, counter);
        }

        return produced;
    }

    private int decode(
        int window)
    {
        produced = 0;

        textPipeline.begin();

        final int length = textObject.capacity();

        for (int offset = 0; offset < length; offset += window)
        {
            final int size = Math.min(window, length - offset);

            textPipeline.feed(textObject, offset, size, offset + size == length);
        }

        return produced;
    }

    private static LlmSectionPipeline pipeline(
        JsonTransform transform)
    {
        return new LlmSectionPipeline(transform, new UnsafeBufferEx(new byte[OUTPUT]));
    }

    private static UnsafeBufferEx buffer(
        String json)
    {
        return new UnsafeBufferEx(json.getBytes(UTF_8));
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(LlmSectionPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
