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

public class LlmOpenAiRequestTransformTest
{
    @Test
    public void shouldRenameKnownFieldsToCanonical()
    {
        String nativeJson = "{\"model\":\"gpt-4o\",\"max_tokens\":256,\"top_p\":0.9,\"n\":2," +
            "\"presence_penalty\":0.1,\"frequency_penalty\":0.2,\"top_logprobs\":3," +
            "\"tool_choice\":\"auto\",\"response_format\":{\"type\":\"json_object\"}}";
        String canonicalJson = "{\"model\":\"gpt-4o\",\"maxOutputTokens\":256,\"topP\":0.9,\"choiceCount\":2," +
            "\"presencePenalty\":0.1,\"frequencyPenalty\":0.2,\"topLogprobs\":3," +
            "\"toolChoice\":\"auto\",\"responseFormat\":{\"type\":\"json_object\"}}";

        assertThat(decode(nativeJson), equalTo(canonicalJson));
    }

    @Test
    public void shouldRenameKnownFieldsToNative()
    {
        String canonicalJson = "{\"model\":\"gpt-4o\",\"maxOutputTokens\":256,\"topP\":0.9,\"choiceCount\":2," +
            "\"presencePenalty\":0.1,\"frequencyPenalty\":0.2,\"topLogprobs\":3," +
            "\"toolChoice\":\"auto\",\"responseFormat\":{\"type\":\"json_object\"}}";
        String nativeJson = "{\"model\":\"gpt-4o\",\"max_tokens\":256,\"top_p\":0.9,\"n\":2," +
            "\"presence_penalty\":0.1,\"frequency_penalty\":0.2,\"top_logprobs\":3," +
            "\"tool_choice\":\"auto\",\"response_format\":{\"type\":\"json_object\"}}";

        assertThat(encode(canonicalJson), equalTo(nativeJson));
    }

    @Test
    public void shouldForwardUnknownTopLevelFieldsUnchanged()
    {
        String json = "{\"model\":\"gpt-4o\",\"temperature\":0.7,\"stream\":true,\"stop\":[\"\\n\"]," +
            "\"user\":\"abc\",\"seed\":42,\"logprobs\":true}";

        assertThat(decode(json), equalTo(json));
        assertThat(encode(json), equalTo(json));
    }

    @Test
    public void shouldNotRenameNestedFieldsResemblingTopLevelNames()
    {
        String json = "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"n\"}]," +
            "\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"n\",\"parameters\":{\"n\":1}}}]}";

        assertThat(decode(json), equalTo(json));
    }

    @Test
    public void shouldRoundTripThroughCanonicalWithNoLoss()
    {
        String nativeJson = "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]," +
            "\"max_tokens\":256,\"top_p\":0.9,\"n\":2,\"presence_penalty\":0.1,\"frequency_penalty\":0.2," +
            "\"top_logprobs\":3,\"tool_choice\":\"auto\",\"response_format\":{\"type\":\"json_object\"}," +
            "\"stream\":true}";

        assertThat(roundTrip(nativeJson), equalTo(nativeJson));
    }

    private static String decode(
        String json)
    {
        return feed(json, new LlmOpenAiRequestTransform(true));
    }

    private static String encode(
        String json)
    {
        return feed(json, new LlmOpenAiRequestTransform(false));
    }

    private static String roundTrip(
        String json)
    {
        return feed(json, new LlmOpenAiRequestTransform(true), new LlmOpenAiRequestTransform(false));
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
