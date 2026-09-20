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
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

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
import io.aklivity.zilla.runtime.common.json.JsonTransform;

// Drives a genuine JsonPipeline (JsonEx.stream(parser).transform(decode).into(encode)) exactly as
// LlmClientFactory wires it for a cross-dialect response stream -- never calling transform()/write()
// by hand -- to prove the fan-out mechanism and the OpenAI decode / Anthropic encode pair together.
public class LlmOpenaiToAnthropicResponseTransformTest
{
    private final List<Event> events = new ArrayList<>();
    private JsonPipeline pipeline;

    @Before
    public void setup()
    {
        JsonParserEx parser = JsonEx.createParser();
        JsonTransform decode = new LlmOpenaiDecodeTransform();
        JsonSink encode = new LlmAnthropicEncodeSink(JsonEnvelope.NONE, this::onEvent);
        this.pipeline = JsonEx.stream(parser).transform(decode).into(encode);
    }

    @Test
    public void shouldEmitMessageStartAndTextBlockStart()
    {
        feed("{\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o\"," +
            "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}");

        assertThat(events.size(), equalTo(2));
        assertThat(events.get(0).name, equalTo("message_start"));
        JsonObject message = events.get(0).body.getJsonObject("message");
        assertThat(message.getString("id"), equalTo("chatcmpl-1"));
        assertThat(message.getString("model"), equalTo("gpt-4o"));
        assertThat(message.getString("role"), equalTo("assistant"));

        assertThat(events.get(1).name, equalTo("content_block_start"));
        assertThat(events.get(1).body.getInt("index"), equalTo(0));
        assertThat(events.get(1).body.getJsonObject("content_block").getString("type"), equalTo("text"));
    }

    @Test
    public void shouldStreamTextContentAcrossChunks()
    {
        feed("{\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}");
        pipeline.nextDocument();
        events.clear();

        feed("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}");

        assertThat(events.size(), equalTo(1));
        assertThat(events.get(0).name, equalTo("content_block_delta"));
        JsonObject delta = events.get(0).body.getJsonObject("delta");
        assertThat(delta.getString("type"), equalTo("text_delta"));
        assertThat(delta.getString("text"), equalTo("Hello"));
    }

    @Test
    public void shouldCloseTextBlockAndFinishOnFinishReason()
    {
        feed("{\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}");
        pipeline.nextDocument();
        events.clear();

        feed("{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}");

        assertThat(events.size(), equalTo(2));
        assertThat(events.get(0).name, equalTo("content_block_stop"));
        assertThat(events.get(0).body.getInt("index"), equalTo(0));
        assertThat(events.get(1).name, equalTo("message_delta"));
        assertThat(events.get(1).body.getJsonObject("delta").getString("stop_reason"), equalTo("end_turn"));
    }

    @Test
    public void shouldStreamMultipleToolCallsWithDistinctBlockIds()
    {
        feed("{\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}");
        pipeline.nextDocument();
        events.clear();

        feed("{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\"," +
            "\"function\":{\"name\":\"lookup\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}");
        pipeline.nextDocument();

        assertThat(events.size(), equalTo(2));
        assertThat(events.get(0).name, equalTo("content_block_stop"));
        assertThat(events.get(0).body.getInt("index"), equalTo(0));
        assertThat(events.get(1).name, equalTo("content_block_start"));
        assertThat(events.get(1).body.getInt("index"), equalTo(1));
        JsonObject block = events.get(1).body.getJsonObject("content_block");
        assertThat(block.getString("type"), equalTo("tool_use"));
        assertThat(block.getString("id"), equalTo("call_1"));
        assertThat(block.getString("name"), equalTo("lookup"));
        events.clear();

        feed("{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"call_2\"," +
            "\"function\":{\"name\":\"other\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}");

        assertThat(events.get(0).name, equalTo("content_block_stop"));
        assertThat(events.get(0).body.getInt("index"), equalTo(1));
        assertThat(events.get(1).name, equalTo("content_block_start"));
        assertThat(events.get(1).body.getInt("index"), equalTo(2));
        assertThat(events.get(1).body.getJsonObject("content_block").getString("id"), equalTo("call_2"));
    }

    @Test
    public void shouldTerminateOnDoneBypassingPipeline()
    {
        LlmAnthropicEncodeSink encode = new LlmAnthropicEncodeSink(JsonEnvelope.NONE, this::onEvent);
        ((LlmDialectTerminator) encode).terminate();

        assertThat(events.size(), equalTo(1));
        assertThat(events.get(0).name, equalTo("message_stop"));
        assertThat(events.get(0).body.getString("type"), equalTo("message_stop"));
    }

    private void feed(
        String json)
    {
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
        JsonObject body;
        try (JsonReader reader = Json.createReader(new StringReader(text)))
        {
            body = reader.readObject();
        }
        events.add(new Event(name != null ? name : body.getString("type"), body));
    }

    private static final class Event
    {
        private final String name;
        private final JsonObject body;

        private Event(
            String name,
            JsonObject body)
        {
            this.name = name;
            this.body = body;
        }
    }
}
