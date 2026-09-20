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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;

// Drives a genuine JsonPipeline (JsonEx.stream(parser).transform(decode).into(encode)) exactly as
// LlmClientFactory wires it for a cross-dialect response stream -- to prove the Anthropic decode /
// OpenAI encode pair, including cross-chunk state (openToolCallIndex/nextToolCallIndex) surviving
// nextDocument() between native chunks.
public class LlmAnthropicToOpenaiResponseTransformTest
{
    private final List<JsonObject> chunks = new ArrayList<>();
    private LlmAnthropicDecodeTransform decode;
    private JsonPipeline pipeline;

    @Before
    public void setup()
    {
        JsonParserEx parser = JsonEx.createParser();
        this.decode = new LlmAnthropicDecodeTransform();
        JsonSink encode = new LlmOpenaiEncodeSink(JsonEnvelope.NONE, this::onEvent);
        this.pipeline = JsonEx.stream(parser).transform(decode).into(encode);
    }

    @Test
    public void shouldEmitMessageStartAsDeltaRole()
    {
        feed("message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-3\"," +
            "\"role\":\"assistant\",\"usage\":{\"input_tokens\":10}}}");

        assertThat(chunks.size(), equalTo(1));
        JsonObject choice = choice(chunks.get(0), 0);
        assertThat(choice.getJsonObject("delta").getString("role"), equalTo("assistant"));
        assertThat(chunks.get(0).getString("id"), equalTo("msg_1"));
        assertThat(chunks.get(0).getString("model"), equalTo("claude-3"));
    }

    @Test
    public void shouldNotEmitAnythingForTextBlockStart()
    {
        feed("content_block_start", "{\"type\":\"content_block_start\",\"index\":0," +
            "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");

        assertThat(chunks.size(), equalTo(0));
    }

    @Test
    public void shouldEmitToolCallDeltaForToolUseBlockStart()
    {
        feed("content_block_start", "{\"type\":\"content_block_start\",\"index\":1," +
            "\"content_block\":{\"type\":\"tool_use\",\"id\":\"call_1\",\"name\":\"lookup\"}}");

        assertThat(chunks.size(), equalTo(1));
        JsonObject toolCall = choice(chunks.get(0), 0).getJsonObject("delta")
            .getJsonArray("tool_calls").getJsonObject(0);
        assertThat(toolCall.getInt("index"), equalTo(0));
        assertThat(toolCall.getString("id"), equalTo("call_1"));
        assertThat(toolCall.getJsonObject("function").getString("name"), equalTo("lookup"));
    }

    @Test
    public void shouldStreamTextDeltaAsContent()
    {
        feed("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0," +
            "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");

        assertThat(chunks.size(), equalTo(1));
        assertThat(choice(chunks.get(0), 0).getJsonObject("delta").getString("content"), equalTo("Hi"));
    }

    @Test
    public void shouldEmitFinishAndUsageAsSeparateChunks()
    {
        feed("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}," +
            "\"usage\":{\"output_tokens\":42}}");

        assertThat(chunks.size(), equalTo(2));
        assertThat(choice(chunks.get(0), 0).getString("finish_reason"), equalTo("stop"));
        assertThat(chunks.get(1).getJsonObject("usage").getInt("completion_tokens"), equalTo(42));
    }

    @Test
    public void shouldEmitDoneOnMessageStop()
    {
        feed("message_stop", "{\"type\":\"message_stop\"}");

        assertThat(rawChunks.size(), equalTo(1));
        assertThat(rawChunks.get(0), equalTo("[DONE]"));
    }

    @Test
    public void shouldSurviveNextDocumentAcrossMultipleToolCallsWithDistinctIndexes()
    {
        feed("content_block_start", "{\"type\":\"content_block_start\",\"index\":1," +
            "\"content_block\":{\"type\":\"tool_use\",\"id\":\"call_1\",\"name\":\"a\"}}");
        pipeline.nextDocument();
        chunks.clear();

        feed("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":1}");
        pipeline.nextDocument();
        chunks.clear();

        feed("content_block_start", "{\"type\":\"content_block_start\",\"index\":2," +
            "\"content_block\":{\"type\":\"tool_use\",\"id\":\"call_2\",\"name\":\"b\"}}");

        JsonObject toolCall = choice(chunks.get(0), 0).getJsonObject("delta")
            .getJsonArray("tool_calls").getJsonObject(0);
        assertThat(toolCall.getInt("index"), equalTo(1));
        assertThat(toolCall.getString("id"), equalTo("call_2"));
    }

    private final List<String> rawChunks = new ArrayList<>();

    private void feed(
        String eventName,
        String json)
    {
        decode.event(eventName);
        byte[] bytes = json.getBytes(UTF_8);
        Status status = pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        assertThat(status, equalTo(Status.COMPLETED));
    }

    private void onEvent(
        String name,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        String text = buffer.getStringWithoutLengthUtf8(offset, length);
        rawChunks.add(text);
        if (!"[DONE]".equals(text))
        {
            try (JsonReader reader = Json.createReader(new StringReader(text)))
            {
                JsonStructure structure = reader.read();
                if (structure.getValueType() == JsonValue.ValueType.OBJECT)
                {
                    chunks.add((JsonObject) structure);
                }
            }
        }
    }

    private static JsonObject choice(
        JsonObject chunk,
        int index)
    {
        JsonArray choices = chunk.getJsonArray("choices");
        return choices.getJsonObject(index);
    }
}
