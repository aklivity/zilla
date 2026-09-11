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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonStream;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

public class LlmOpenAiResponseTransformTest
{
    @Test
    public void shouldRenameChoiceIndexToCanonical()
    {
        String nativeJson = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1," +
            "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hi\"}," +
            "\"logprobs\":null,\"finish_reason\":null}]}";
        String canonicalJson = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1," +
            "\"model\":\"gpt-4o\",\"choices\":[{\"choiceIndex\":0,\"delta\":{\"role\":\"assistant\"," +
            "\"content\":\"Hi\"},\"logProbability\":null,\"finishReason\":null}]}";

        assertThat(decode(nativeJson), equalTo(canonicalJson));
        assertThat(encode(canonicalJson), equalTo(nativeJson));
    }

    @Test
    public void shouldSupportMultipleChoicesWhenNGreaterThanOne()
    {
        String nativeJson = "{\"id\":\"chatcmpl-1\",\"choices\":[" +
            "{\"index\":0,\"delta\":{\"content\":\"a\"},\"finish_reason\":null}," +
            "{\"index\":1,\"delta\":{\"content\":\"b\"},\"finish_reason\":null}]}";
        String canonicalJson = "{\"id\":\"chatcmpl-1\",\"choices\":[" +
            "{\"choiceIndex\":0,\"delta\":{\"content\":\"a\"},\"finishReason\":null}," +
            "{\"choiceIndex\":1,\"delta\":{\"content\":\"b\"},\"finishReason\":null}]}";

        assertThat(decode(nativeJson), equalTo(canonicalJson));
    }

    @Test
    public void shouldRemapToolCallsFinishReasonValue()
    {
        String nativeJson = "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}";
        String canonicalJson = "{\"choices\":[{\"choiceIndex\":0,\"delta\":{},\"finishReason\":\"tool_call\"}]}";

        assertThat(decode(nativeJson), equalTo(canonicalJson));
        assertThat(encode(canonicalJson), equalTo(nativeJson));
    }

    @Test
    public void shouldForwardOtherFinishReasonValuesUnchanged()
    {
        String[] values = { "stop", "length", "content_filter" };
        for (String value : values)
        {
            String nativeJson = "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"" + value + "\"}]}";
            String canonicalJson =
                "{\"choices\":[{\"choiceIndex\":0,\"delta\":{},\"finishReason\":\"" + value + "\"}]}";

            assertThat(decode(nativeJson), equalTo(canonicalJson));
            assertThat(encode(canonicalJson), equalTo(nativeJson));
        }
    }

    @Test
    public void shouldPreserveToolCallArgumentFragmentStreamingUnchanged()
    {
        String nativeJson = "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0," +
            "\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
            "\"arguments\":\"{\\\"lo\"}}]},\"finish_reason\":null}]}";
        String canonicalJson = "{\"choices\":[{\"choiceIndex\":0,\"delta\":{\"tool_calls\":[{\"index\":0," +
            "\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
            "\"arguments\":\"{\\\"lo\"}}]},\"finishReason\":null}]}";

        assertThat(decode(nativeJson), equalTo(canonicalJson));
        assertThat(encode(canonicalJson), equalTo(nativeJson));
    }

    @Test
    public void shouldRenameUsageFields()
    {
        String nativeJson = "{\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5," +
            "\"total_tokens\":15}}";
        String canonicalJson = "{\"choices\":[],\"usage\":{\"inputTokens\":10,\"outputTokens\":5," +
            "\"totalTokens\":15}}";

        assertThat(decode(nativeJson), equalTo(canonicalJson));
        assertThat(encode(canonicalJson), equalTo(nativeJson));
    }

    @Test
    public void shouldRoundTripThroughCanonicalWithNoLoss()
    {
        String nativeJson = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1," +
            "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"," +
            "\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\"," +
            "\"function\":{\"name\":\"get_weather\",\"arguments\":\"{}\"}}]},\"logprobs\":null," +
            "\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5," +
            "\"total_tokens\":15}}";

        assertThat(roundTrip(nativeJson), equalTo(nativeJson));
    }

    private static String decode(
        String json)
    {
        return feed(json, new LlmOpenAiResponseTransform(true));
    }

    private static String encode(
        String json)
    {
        return feed(json, new LlmOpenAiResponseTransform(false));
    }

    private static String roundTrip(
        String json)
    {
        return feed(json, new LlmOpenAiResponseTransform(true), new LlmOpenAiResponseTransform(false));
    }

    private static String feed(
        String json,
        JsonTransform... transforms)
    {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final MutableDirectBufferEx outBuf = new UnsafeBufferEx(new byte[Math.max(1024, bytes.length * 2)]);
        generator.wrap(outBuf, 0, outBuf.capacity());

        JsonStream stream = JsonEx.stream(JsonEx.createParser());
        for (JsonTransform transform : transforms)
        {
            stream = stream.transform(transform);
        }
        final JsonPipeline pipeline = stream.lenient(false).into(JsonEx.createSink(generator));

        final Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        assertThat(status, equalTo(Status.COMPLETED));

        final byte[] out = new byte[generator.length()];
        outBuf.getBytes(0, out);
        return new String(out, StandardCharsets.UTF_8);
    }
}
