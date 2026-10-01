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

public class LlmModelExtractTransformTest
{
    @Test
    public void shouldExtractModelIntoEnvelope()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmModelExtractTransform(envelope);

        transform(transform, envelope, "{\"model\":\"claude-3-opus-20240229\"}");

        DirectBufferEx extracted = envelope.get("model", 0);
        assertThat(extracted.getStringWithoutLengthUtf8(0, extracted.capacity()), equalTo("claude-3-opus-20240229"));
    }

    @Test
    public void shouldNotExtractNestedFieldNamedModel()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmModelExtractTransform(envelope);

        transform(transform, envelope, "{\"tools\":[{\"function\":{\"model\":\"should-not-be-extracted\"}}]}");

        assertThat(envelope.get("model", 0), nullValue());
    }

    @Test
    public void shouldForwardEveryOtherFieldUnchanged()
    {
        TestJsonEnvelope envelope = new TestJsonEnvelope();
        JsonTransform transform = new LlmModelExtractTransform(envelope);

        JsonObject result = transform(transform, envelope,
            "{\"max_tokens\":1024,\"top_p\":0.9,\"stop_sequences\":[\"\\n\"]}");

        assertThat(result.getInt("max_tokens"), equalTo(1024));
        assertThat(result.getJsonNumber("top_p").doubleValue(), equalTo(0.9));
        assertThat(result.getJsonArray("stop_sequences").getString(0), equalTo("\n"));
    }

    @Test
    public void shouldBeIdentity()
    {
        JsonTransform transform = new LlmModelExtractTransform(new TestJsonEnvelope());

        assertThat(transform.identity(), is(true));
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
