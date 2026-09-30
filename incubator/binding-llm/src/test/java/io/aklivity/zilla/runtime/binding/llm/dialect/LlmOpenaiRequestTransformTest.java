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

public class LlmOpenaiRequestTransformTest
{
    @Test
    public void shouldRenameEachKnownFieldToCanonical()
    {
        JsonTransform decoder = new LlmOpenaiRequestTransform(true, JsonEnvelope.NONE);

        JsonObject result = transform(decoder, JsonEnvelope.NONE, "{" +
            "\"max_tokens\":256,\"top_p\":0.9,\"n\":2,\"presence_penalty\":0.1," +
            "\"frequency_penalty\":0.2,\"top_logprobs\":3,\"tool_choice\":\"auto\"," +
            "\"response_format\":\"json_object\"}");

        assertThat(result.getInt("maxOutputTokens"), equalTo(256));
        assertThat(result.getJsonNumber("topP").doubleValue(), equalTo(0.9));
        assertThat(result.getInt("choiceCount"), equalTo(2));
        assertThat(result.getJsonNumber("presencePenalty").doubleValue(), equalTo(0.1));
        assertThat(result.getJsonNumber("frequencyPenalty").doubleValue(), equalTo(0.2));
        assertThat(result.getInt("topLogprobs"), equalTo(3));
        assertThat(result.getString("toolChoice"), equalTo("auto"));
        assertThat(result.getString("responseFormat"), equalTo("json_object"));
    }

    @Test
    public void shouldRenameEachKnownFieldToNative()
    {
        JsonTransform encoder = new LlmOpenaiRequestTransform(false, JsonEnvelope.NONE);

        JsonObject result = transform(encoder, JsonEnvelope.NONE,
            "{\"maxOutputTokens\":256,\"topP\":0.9,\"choiceCount\":2}");

        assertThat(result.getInt("max_tokens"), equalTo(256));
        assertThat(result.getJsonNumber("top_p").doubleValue(), equalTo(0.9));
        assertThat(result.getInt("n"), equalTo(2));
    }

    @Test
    public void shouldForwardUnknownFieldUnchanged()
    {
        JsonTransform decoder = new LlmOpenaiRequestTransform(true, JsonEnvelope.NONE);

        JsonObject result = transform(decoder, JsonEnvelope.NONE, "{\"model\":\"gpt-4o\",\"stream\":true}");

        assertThat(result.getString("model"), equalTo("gpt-4o"));
        assertThat(result.getBoolean("stream"), is(true));
    }

    @Test
    public void shouldNotRenameNestedFieldResemblingTopLevelName()
    {
        JsonTransform decoder = new LlmOpenaiRequestTransform(true, JsonEnvelope.NONE);

        JsonObject result = transform(decoder, JsonEnvelope.NONE,
            "{\"tools\":[{\"function\":{\"parameters\":{\"n\":1}}}]}");

        JsonObject parameters = result.getJsonArray("tools").getJsonObject(0)
            .getJsonObject("function").getJsonObject("parameters");
        assertThat(parameters.getInt("n"), equalTo(1));
        assertThat(parameters.containsKey("choiceCount"), is(false));
    }

    @Test
    public void shouldNotRenameContainerValuedTopLevelMember()
    {
        JsonTransform decoder = new LlmOpenaiRequestTransform(true, JsonEnvelope.NONE);

        JsonObject result = transform(decoder, JsonEnvelope.NONE, "{\"response_format\":{\"type\":\"json_object\"}}");

        assertThat(result.containsKey("responseFormat"), is(false));
        assertThat(result.getJsonObject("response_format").getString("type"), equalTo("json_object"));
    }

    @Test
    public void shouldExtractModelIntoEnvelope()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform decoder = new LlmOpenaiRequestTransform(true, envelope);

        transform(decoder, envelope, "{\"model\":\"gpt-4o\"}");

        DirectBufferEx extracted = envelope.get("model", 0);
        assertThat(extracted.getStringWithoutLengthUtf8(0, extracted.capacity()), equalTo("gpt-4o"));
    }

    @Test
    public void shouldNotExtractNestedFieldNamedModel()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform decoder = new LlmOpenaiRequestTransform(true, envelope);

        transform(decoder, envelope, "{\"tools\":[{\"function\":{\"model\":\"should-not-be-extracted\"}}]}");

        assertThat(envelope.get("model", 0), nullValue());
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
