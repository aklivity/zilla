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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

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

    private static final String TOOL_DEFINITION_GEMINI_NATIVE =
        "{\"name\":\"get_weather\",\"description\":\"Get the weather\",\"parametersJsonSchema\":{\"type\":\"object\"," +
        "\"properties\":{\"city\":{\"type\":\"string\"},\"unit\":{\"type\":\"string\",\"enum\":[\"c\",\"f\"]}}," +
        "\"required\":[\"city\"]}}";
    private static final String TOOL_DEFINITION_GEMINI_CANONICAL =
        "{\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"description\":\"Get the weather\"," +
        "\"parameters\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"},\"unit\":" +
        "{\"type\":\"string\",\"enum\":[\"c\",\"f\"]}},\"required\":[\"city\"]}}}";
    private static final String TOOL_CALL_GEMINI_NATIVE =
        "{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"city\":\"Paris\",\"unit\":\"c\",\"days\":[1,2,3]}," +
        "\"id\":\"call_1\"},\"thoughtSignature\":\"c2ln\"}";
    private static final String TOOL_CALL_GEMINI_CANONICAL =
        "{\"arguments\":{\"city\":\"Paris\",\"unit\":\"c\",\"days\":[1,2,3]},\"gemini:id\":\"call_1\"," +
        "\"gemini:thoughtSignature\":\"c2ln\"}";
    private static final String IMAGE_GEMINI_NATIVE =
        "{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"" + DATA + "\"}}";

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

    private static final String REASONING_RESPONSES_CANONICAL =
        "{\"text\":\"Let me think about it\",\"openai:id\":\"rs_1\",\"openai:encrypted_content\":\"" + DATA + "\"}";
    private static final String IMAGE_RESPONSES_NATIVE =
        "{\"type\":\"input_image\",\"image_url\":\"data:image/png;base64," + DATA + "\",\"detail\":\"low\"}";
    private static final String IMAGE_RESPONSES_CANONICAL =
        "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64," + DATA + "\",\"detail\":\"low\"}}";
    private static final String CITATIONS_RESPONSES_NATIVE =
        "[{\"type\":\"url_citation\",\"end_index\":8,\"start_index\":0,\"title\":\"Sky colour\"," +
        "\"url\":\"https://example.com/sky\"},{\"type\":\"file_citation\",\"file_id\":\"file_1\"," +
        "\"filename\":\"a.pdf\",\"index\":0}]";

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

    private final LlmTextModelPipeline guardedPipeline = new LlmTextModelPipeline(new GuardChain(),
        new LlmTestBufferPool(32 * 1024, 1), 1L, new UnsafeBufferEx(new byte[OUTPUT]), true);
    private final MutableDirectBufferEx guardedOutput = new UnsafeBufferEx(new byte[OUTPUT]);
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
    private LlmSectionPipeline toolDefinitionGeminiToCanonical;
    private LlmSectionPipeline toolDefinitionGeminiFromCanonical;
    private LlmSectionPipeline toolCallGeminiToCanonical;
    private LlmSectionPipeline toolCallGeminiFromCanonical;
    private LlmSectionPipeline imageGeminiToCanonical;
    private LlmSectionPipeline imageGeminiFromCanonical;
    private LlmSectionPipeline imageToCanonical;
    private LlmSectionPipeline imageFromAnthropic;
    private LlmSectionPipeline citationsToCanonical;
    private LlmSectionPipeline citationsFromAnthropic;
    private LlmSectionPipeline citationsFromOpenai;
    private LlmSectionPipeline controlAnthropicToCanonical;
    private LlmSectionPipeline controlAnthropicFromCanonical;
    private LlmSectionPipeline controlOpenaiToCanonical;
    private LlmSectionPipeline controlOpenaiFromCanonical;
    private LlmSectionPipeline reasoningResponsesFromCanonical;
    private LlmSectionPipeline imageResponsesToCanonical;
    private LlmSectionPipeline imageResponsesFromCanonical;
    private LlmSectionPipeline citationsResponsesToCanonical;

    private UnsafeBufferEx toolDefinitionNative;
    private UnsafeBufferEx toolDefinitionCanonical;
    private UnsafeBufferEx toolCallNative;
    private UnsafeBufferEx toolCallCanonical;
    private UnsafeBufferEx toolCallOpenaiNative;
    private UnsafeBufferEx toolCallOpenaiCanonical;
    private UnsafeBufferEx toolDefinitionGeminiNative;
    private UnsafeBufferEx toolDefinitionGeminiCanonical;
    private UnsafeBufferEx toolCallGeminiNative;
    private UnsafeBufferEx toolCallGeminiCanonical;
    private UnsafeBufferEx imageGeminiNative;
    private UnsafeBufferEx imageNative;
    private UnsafeBufferEx imageCanonical;
    private UnsafeBufferEx citationsNative;
    private UnsafeBufferEx citationsCanonical;
    private UnsafeBufferEx controlNative;
    private UnsafeBufferEx controlAnthropicCanonical;
    private UnsafeBufferEx controlOpenaiCanonical;
    private UnsafeBufferEx textObject;
    private UnsafeBufferEx reasoningResponsesCanonical;
    private UnsafeBufferEx imageResponsesNative;
    private UnsafeBufferEx imageResponsesCanonical;
    private UnsafeBufferEx citationsResponsesNative;

    @Setup(Level.Trial)
    public void init()
    {
        toolDefinitionToCanonical = pipeline(new LlmAnthropicToolDefinitionToCanonical());
        toolDefinitionFromAnthropic = pipeline(new LlmAnthropicToolDefinitionFromCanonical(true));
        toolDefinitionFromOpenai = pipeline(new LlmOpenaiToolDefinitionFromCanonical("openai/ccs"));
        toolCallToCanonical = pipeline(new LlmAnthropicToolCallToCanonical());
        toolCallFromCanonical = pipeline(new LlmAnthropicToolCallFromCanonical("tool_use", true));
        toolCallOpenaiToCanonical = pipeline(new LlmOpenaiToolCallToCanonical());
        toolCallOpenaiFromCanonical = pipeline(new LlmOpenaiToolCallFromCanonical("openai/ccs"));
        toolDefinitionGeminiToCanonical = pipeline(new LlmGeminiToolDefinitionToCanonical());
        toolDefinitionGeminiFromCanonical = pipeline(new LlmGeminiToolDefinitionFromCanonical(true));
        toolCallGeminiToCanonical = pipeline(new LlmGeminiToolCallToCanonical());
        toolCallGeminiFromCanonical = pipeline(new LlmGeminiToolCallFromCanonical(true));
        imageGeminiToCanonical = pipeline(LlmGeminiMediaToCanonical.image());
        imageGeminiFromCanonical = pipeline(LlmGeminiMediaFromCanonical.image(true));
        imageToCanonical = pipeline(LlmAnthropicMediaToCanonical.image());
        imageFromAnthropic = pipeline(LlmAnthropicMediaFromCanonical.image(true));
        citationsToCanonical = pipeline(new LlmAnthropicCitationsToCanonical());
        citationsFromAnthropic = pipeline(new LlmAnthropicCitationsFromCanonical(true));
        citationsFromOpenai = pipeline(new LlmOpenaiCitationsFromCanonical("openai/ccs"));
        controlAnthropicToCanonical = pipeline(new LlmAnthropicControlToCanonical());
        controlAnthropicFromCanonical = pipeline(new LlmAnthropicControlFromCanonical(true));
        controlOpenaiToCanonical = pipeline(new LlmOpenaiControlToCanonical("openai/ccs"));
        controlOpenaiFromCanonical = pipeline(new LlmOpenaiControlFromCanonical("openai/ccs", true));

        reasoningResponsesFromCanonical = pipeline(new LlmOpenaiResponsesReasoningFromCanonical("openai/rs"));
        imageResponsesToCanonical = pipeline(LlmOpenaiResponsesMediaToCanonical.image());
        imageResponsesFromCanonical = pipeline(LlmOpenaiResponsesMediaFromCanonical.image("openai/rs"));
        citationsResponsesToCanonical = pipeline(LlmOpenaiResponsesCitationsToCanonical.array());

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
        toolDefinitionGeminiNative = buffer(TOOL_DEFINITION_GEMINI_NATIVE);
        toolDefinitionGeminiCanonical = buffer(TOOL_DEFINITION_GEMINI_CANONICAL);
        toolCallGeminiNative = buffer(TOOL_CALL_GEMINI_NATIVE);
        toolCallGeminiCanonical = buffer(TOOL_CALL_GEMINI_CANONICAL);
        imageGeminiNative = buffer(IMAGE_GEMINI_NATIVE);
        imageNative = buffer(IMAGE_NATIVE);
        imageCanonical = buffer(IMAGE_CANONICAL);
        citationsNative = buffer(CITATIONS_NATIVE);
        citationsCanonical = buffer(CITATIONS_CANONICAL);
        controlNative = buffer(CONTROL_NATIVE);
        controlAnthropicCanonical = buffer(CONTROL_ANTHROPIC_CANONICAL);
        controlOpenaiCanonical = buffer(CONTROL_OPENAI_CANONICAL);
        textObject = buffer(TEXT_OBJECT);
        reasoningResponsesCanonical = buffer(REASONING_RESPONSES_CANONICAL);
        imageResponsesNative = buffer(IMAGE_RESPONSES_NATIVE);
        imageResponsesCanonical = buffer(IMAGE_RESPONSES_CANONICAL);
        citationsResponsesNative = buffer(CITATIONS_RESPONSES_NATIVE);
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
    public int toolDefinitionToCanonicalGemini()
    {
        return run(toolDefinitionGeminiToCanonical, none, toolDefinitionGeminiNative, toolDefinitionGeminiNative.capacity());
    }

    @Benchmark
    public int toolDefinitionFromCanonicalGemini()
    {
        return run(toolDefinitionGeminiFromCanonical, none, toolDefinitionGeminiCanonical,
            toolDefinitionGeminiCanonical.capacity());
    }

    @Benchmark
    public int toolCallToCanonicalGemini()
    {
        return run(toolCallGeminiToCanonical, toolCallAttributes, toolCallGeminiNative, toolCallGeminiNative.capacity());
    }

    @Benchmark
    public int toolCallToCanonicalGeminiFragmented()
    {
        return run(toolCallGeminiToCanonical, toolCallAttributes, toolCallGeminiNative, WINDOW);
    }

    @Benchmark
    public int toolCallFromCanonicalGemini()
    {
        return run(toolCallGeminiFromCanonical, toolCallAttributes, toolCallGeminiCanonical,
            toolCallGeminiCanonical.capacity());
    }

    @Benchmark
    public int imageInlineToCanonicalGemini()
    {
        return run(imageGeminiToCanonical, imageAttributes, imageGeminiNative, imageGeminiNative.capacity());
    }

    @Benchmark
    public int imageInlineToCanonicalGeminiFragmented()
    {
        return run(imageGeminiToCanonical, imageAttributes, imageGeminiNative, WINDOW);
    }

    @Benchmark
    public int imageInlineFromCanonicalGemini()
    {
        return run(imageGeminiFromCanonical, imageAttributes, imageCanonical, imageCanonical.capacity());
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
    public int reasoningFromCanonicalResponses()
    {
        return run(reasoningResponsesFromCanonical, none, reasoningResponsesCanonical,
            reasoningResponsesCanonical.capacity());
    }

    @Benchmark
    public int reasoningFromCanonicalResponsesFragmented()
    {
        return run(reasoningResponsesFromCanonical, none, reasoningResponsesCanonical, WINDOW);
    }

    @Benchmark
    public int imageToCanonicalResponses()
    {
        return run(imageResponsesToCanonical, none, imageResponsesNative, imageResponsesNative.capacity());
    }

    @Benchmark
    public int imageFromCanonicalResponses()
    {
        return run(imageResponsesFromCanonical, none, imageResponsesCanonical, imageResponsesCanonical.capacity());
    }

    @Benchmark
    public int citationsToCanonicalResponses()
    {
        return run(citationsResponsesToCanonical, none, citationsResponsesNative, citationsResponsesNative.capacity());
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
    public int guardedSection()
    {
        return guard(reasoningResponsesCanonical.capacity());
    }

    @Benchmark
    public int guardedSectionFragmented()
    {
        return guard(WINDOW);
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

    private int guard(
        int window)
    {
        produced = 0;

        final int length = reasoningResponsesCanonical.capacity();

        for (int offset = 0; offset < length; offset += window)
        {
            final int size = Math.min(window, length - offset);

            int position = 0;
            ModelPipelineResult result;

            do
            {
                result = guardedPipeline.accept(1L, 2L, 3L, offset + position == 0, offset + size == length,
                    reasoningResponsesCanonical, offset + position, offset + size, guardedOutput, 0, OUTPUT);

                position += result.consumed();
                produced += result.produced();
            }
            while (result.status() == ModelStatus.OVERFLOW && (result.consumed() > 0 || result.produced() > 0));
        }

        ModelPipelineResult result;

        do
        {
            result = guardedPipeline.finish(1L, 2L, 3L, guardedOutput, 0, OUTPUT);
            produced += result.produced();
        }
        while (result.status() == ModelStatus.OVERFLOW && result.produced() > 0);

        produced += guardedPipeline.close(guardedOutput, 0);

        guardedPipeline.reset();

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

    private static final class GuardChain implements ModelPipeline
    {
        private final ModelPipelineResult result = new ModelPipelineResult();

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int length = Math.min(srcLimit - srcIndex, dstLimit - dstIndex);

            dst.putBytes(dstIndex, src, srcIndex, length);

            return result.set(Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW, length, length);
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }

        @Override
        public void reset()
        {
        }
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
