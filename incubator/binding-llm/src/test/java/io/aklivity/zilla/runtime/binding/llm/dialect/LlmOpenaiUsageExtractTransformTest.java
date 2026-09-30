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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

public class LlmOpenaiUsageExtractTransformTest
{
    @Test
    public void shouldExtractUsageIntoEnvelope()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmOpenaiUsageExtractTransform(envelope);

        transform(transform, envelope,
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":15,\"total_tokens\":40}}");

        assertThat(intValue(envelope, "usage.inputTokens"), equalTo(25));
        assertThat(intValue(envelope, "usage.outputTokens"), equalTo(15));
        assertThat(intValue(envelope, "usage.totalTokens"), equalTo(40));
        assertThat(envelope.get("usage.cacheReadTokens", 0), nullValue());
        assertThat(envelope.get("usage.cacheWriteTokens", 0), nullValue());
        assertThat(envelope.get("usage.reasoningTokens", 0), nullValue());
    }

    @Test
    public void shouldExtractCacheAndReasoningTokensIntoEnvelope()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmOpenaiUsageExtractTransform(envelope);

        transform(transform, envelope,
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":15," +
                "\"prompt_tokens_details\":{\"cached_tokens\":10}," +
                "\"completion_tokens_details\":{\"reasoning_tokens\":5}}}");

        assertThat(intValue(envelope, "usage.cacheReadTokens"), equalTo(10));
        assertThat(intValue(envelope, "usage.reasoningTokens"), equalTo(5));
    }

    @Test
    public void shouldNotExtractFromDocumentWithoutUsage()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmOpenaiUsageExtractTransform(envelope);

        transform(transform, envelope,
            "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}");

        assertThat(envelope.get("usage.inputTokens", 0), nullValue());
        assertThat(envelope.get("usage.outputTokens", 0), nullValue());
    }

    @Test
    public void shouldForwardEveryOtherFieldUnchanged()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmOpenaiUsageExtractTransform(envelope);

        JsonObject result = transform(transform, envelope,
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":15}}");

        assertThat(result.getJsonArray("choices").size(), equalTo(0));
        assertThat(result.getJsonObject("usage").getInt("prompt_tokens"), equalTo(25));
        assertThat(result.getJsonObject("usage").getInt("completion_tokens"), equalTo(15));
    }

    @Test
    public void shouldBeIdentity()
    {
        JsonTransform transform = new LlmOpenaiUsageExtractTransform(new TestJsonEnvelope());

        assertThat(transform.identity(), is(true));
    }

    private static int intValue(
        JsonEnvelope envelope,
        String name)
    {
        DirectBufferEx value = envelope.get(name, 0);
        return Integer.parseInt(value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    private static JsonObject transform(
        JsonTransform transform,
        JsonEnvelope envelope,
        String json)
    {
        JsonParserEx parser = JsonEx.createParser();
        JsonGeneratorEx generator = JsonEx.createGenerator();
        JsonPipeline pipeline = JsonEx.stream(parser).envelope(envelope).transform(transform).into(generator);

        byte[] bytes = json.getBytes(UTF_8);
        MutableDirectBufferEx output = new UnsafeBufferEx(new byte[8192]);
        JsonPipelineResult result = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true, output, 0,
            output.capacity());

        assertThat(result.status(), equalTo(Status.COMPLETED));

        String text = output.getStringWithoutLengthUtf8(0, result.produced());
        try (JsonReader reader = Json.createReader(new StringReader(text)))
        {
            return reader.readObject();
        }
    }
}
