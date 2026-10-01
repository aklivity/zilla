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

public class LlmAnthropicResponseExtractTransformTest
{
    @Test
    public void shouldExtractInputAndCacheTokensFromMessageStart()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(envelope);

        transform(transform, envelope,
            "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-3-opus-20240229\"," +
                "\"role\":\"assistant\",\"usage\":{\"input_tokens\":25,\"cache_creation_input_tokens\":5," +
                "\"cache_read_input_tokens\":10}}}");

        assertThat(intValue(envelope, "usage.inputTokens"), equalTo(25));
        assertThat(intValue(envelope, "usage.cacheWriteTokens"), equalTo(5));
        assertThat(intValue(envelope, "usage.cacheReadTokens"), equalTo(10));
        assertThat(envelope.get("usage.outputTokens", 0), nullValue());
    }

    @Test
    public void shouldExtractOutputTokensFromMessageDeltaAcrossDocuments()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(envelope);

        transform(transform, envelope,
            "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-3-opus-20240229\"," +
                "\"role\":\"assistant\",\"usage\":{\"input_tokens\":25}}}");
        transform(transform, envelope,
            "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":15}}");

        assertThat(intValue(envelope, "usage.inputTokens"), equalTo(25));
        assertThat(intValue(envelope, "usage.outputTokens"), equalTo(15));
    }

    @Test
    public void shouldExtractFromWholeNonStreamingMessage()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(envelope);

        transform(transform, envelope,
            "{\"id\":\"msg_1\",\"model\":\"claude-3-opus-20240229\",\"role\":\"assistant\"," +
                "\"usage\":{\"input_tokens\":25,\"output_tokens\":15}}");

        assertThat(intValue(envelope, "usage.inputTokens"), equalTo(25));
        assertThat(intValue(envelope, "usage.outputTokens"), equalTo(15));
    }

    @Test
    public void shouldNotExtractFromDocumentWithoutUsage()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(envelope);

        transform(transform, envelope,
            "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");

        assertThat(envelope.get("usage.inputTokens", 0), nullValue());
        assertThat(envelope.get("usage.outputTokens", 0), nullValue());
    }

    @Test
    public void shouldForwardEveryOtherFieldUnchanged()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(envelope);

        JsonObject result = transform(transform, envelope,
            "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-3-opus-20240229\"," +
                "\"usage\":{\"input_tokens\":25}}}");

        assertThat(result.getString("type"), equalTo("message_start"));
        assertThat(result.getJsonObject("message").getString("id"), equalTo("msg_1"));
        assertThat(result.getJsonObject("message").getString("model"), equalTo("claude-3-opus-20240229"));
        assertThat(result.getJsonObject("message").getJsonObject("usage").getInt("input_tokens"), equalTo(25));
    }

    @Test
    public void shouldBeIdentity()
    {
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(new TestJsonEnvelope());

        assertThat(transform.identity(), is(true));
    }

    @Test
    public void shouldExtractErrorIntoEnvelope()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(envelope);

        transform(transform, envelope,
            "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");

        assertThat(stringValue(envelope, "error.type"), equalTo("overloaded_error"));
        assertThat(stringValue(envelope, "error.message"), equalTo("Overloaded"));
        assertThat(envelope.get("error.status", 0), nullValue());
    }

    @Test
    public void shouldNotExtractErrorFromMessage()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmAnthropicResponseExtractTransform(envelope);

        transform(transform, envelope,
            "{\"type\":\"message_delta\",\"delta\":{\"type\":\"text\",\"stop_reason\":\"end_turn\"}}");

        assertThat(envelope.get("error.type", 0), nullValue());
        assertThat(envelope.get("error.message", 0), nullValue());
    }

    private static String stringValue(
        JsonEnvelope envelope,
        String name)
    {
        DirectBufferEx value = envelope.get(name, 0);
        return value.getStringWithoutLengthUtf8(0, value.capacity());
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
