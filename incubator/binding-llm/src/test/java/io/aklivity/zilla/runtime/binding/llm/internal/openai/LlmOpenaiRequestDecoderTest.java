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
package io.aklivity.zilla.runtime.binding.llm.internal.openai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.openai.LlmOpenaiRequestDecoder.Sink;
import io.aklivity.zilla.runtime.binding.llm.internal.openai.LlmOpenaiRequestDecoder.Status;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmOpenaiRequestDecoderTest
{
    private static final int[] CHUNKS = {Integer.MAX_VALUE, 1, 7, 128};
    private static final int HOLD = 32 * 1024;

    private static final String CALL_1 =
        "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
            "\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\"}}";
    private static final String CALL_2 =
        "{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
            "\"arguments\":\"{\\\"city\\\":\\\"London\\\"}\"}}";
    private static final String IMAGE = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/cat.png\"}}";
    private static final String FILE = "{\"type\":\"file\",\"file\":{\"file_id\":\"file-abc123\"}}";
    private static final String AUDIO = "{\"type\":\"input_audio\",\"input_audio\":{\"data\":\"UklGRg==\",\"format\":\"wav\"}}";
    private static final String TOOL = "{\"type\":\"function\",\"function\":{\"name\":\"get_weather\"," +
        "\"parameters\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}}}";

    @Test
    public void shouldDecodeSystemInstruction()
    {
        assertDecoded(request("{\"role\":\"system\",\"content\":\"Be brief\"}," +
                "{\"role\":\"developer\",\"content\":\"Answer in French\"}"),
            "model:gpt-4",
            "system-instruction|0||Be brief",
            "system-instruction|1||Answer in French");
    }

    @Test
    public void shouldDecodeUserText()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":\"Hello\"}"),
            "model:gpt-4",
            "user-text|0||Hello");
    }

    @Test
    public void shouldDecodeUserImage()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":[" + IMAGE + "]}"),
            "model:gpt-4",
            "user-image|0||" + IMAGE);
    }

    @Test
    public void shouldDecodeUserDocument()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":[" + FILE + "]}"),
            "model:gpt-4",
            "user-document|0||" + FILE);
    }

    @Test
    public void shouldDecodeUserAudio()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":[" + AUDIO + "]}"),
            "model:gpt-4",
            "user-audio|0||" + AUDIO);
    }

    @Test
    public void shouldDecodeUnknownPart()
    {
        final String video = "{\"type\":\"video_url\",\"video_url\":{\"url\":\"https://example.com/cat.mp4\"}}";

        assertDecoded(request("{\"role\":\"user\",\"content\":[" + video + "]}"),
            "model:gpt-4",
            "unknown|0||" + video);
    }

    @Test
    public void shouldDecodeAssistantText()
    {
        assertDecoded(request("{\"role\":\"assistant\",\"content\":\"Hi there\"}"),
            "model:gpt-4",
            "assistant-text|0||Hi there");
    }

    @Test
    public void shouldDecodeAssistantRefusal()
    {
        assertDecoded(request("{\"role\":\"assistant\",\"refusal\":\"I cannot help with that\"}"),
            "model:gpt-4",
            "assistant-refusal|0||I cannot help with that");
    }

    @Test
    public void shouldDecodeToolDefinition()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":\"Weather in Paris?\"}", TOOL),
            "model:gpt-4",
            "user-text|0||Weather in Paris?",
            "tool-definition|-1||" + TOOL);
    }

    @Test
    public void shouldDecodeToolCalls()
    {
        assertDecoded(request("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[" + CALL_1 + "," + CALL_2 + "]}"),
            "model:gpt-4",
            "tool-call|0||" + CALL_1,
            "tool-call|0||" + CALL_2);
    }

    @Test
    public void shouldDecodeToolResult()
    {
        assertDecoded(request("{\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"sunny\"}"),
            "model:gpt-4",
            "tool-result|0|call_1|sunny");
    }

    @Test
    public void shouldDecodeMixedContent()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"What is in this image?\"}," +
                IMAGE + "]}"),
            "model:gpt-4",
            "user-text|0||What is in this image?",
            "user-image|0||" + IMAGE);
    }

    @Test
    public void shouldDecodeAssistantTextAndToolCall()
    {
        assertDecoded(request("{\"role\":\"assistant\",\"content\":\"Let me check\",\"tool_calls\":[" + CALL_1 + "]}"),
            "model:gpt-4",
            "assistant-text|0||Let me check",
            "tool-call|0||" + CALL_1);
    }

    @Test
    public void shouldDecodeConversation()
    {
        assertDecoded(request("{\"role\":\"system\",\"content\":\"Be brief\"}," +
                "{\"role\":\"user\",\"content\":\"Weather in Paris?\"}," +
                "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[" + CALL_1 + "]}," +
                "{\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"sunny\"}," +
                "{\"role\":\"user\",\"content\":\"And in London?\"}", TOOL),
            "model:gpt-4",
            "system-instruction|0||Be brief",
            "user-text|1||Weather in Paris?",
            "tool-call|2||" + CALL_1,
            "tool-result|3|call_1|sunny",
            "user-text|4||And in London?",
            "tool-definition|-1||" + TOOL);
    }

    @Test
    public void shouldDecodeRoleAfterContent()
    {
        assertDecoded("{\"model\":\"gpt-4\",\"messages\":[{\"content\":\"Hello\",\"role\":\"user\"}]}",
            "model:gpt-4",
            "user-text|0||Hello");
    }

    @Test
    public void shouldDecodeModelAfterMessages()
    {
        assertDecoded("{\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}],\"model\":\"gpt-4\"}",
            "model:gpt-4",
            "user-text|0||Hello");
    }

    @Test
    public void shouldDecodeToolCallIdAfterContent()
    {
        assertDecoded("{\"model\":\"gpt-4\",\"messages\":[{\"content\":\"sunny\",\"tool_call_id\":\"call_1\"," +
                "\"role\":\"tool\"}]}",
            "model:gpt-4",
            "tool-result|0|call_1|sunny");
    }

    @Test
    public void shouldDecodeToolsBeforeMessages()
    {
        assertDecoded("{\"tools\":[" + TOOL + "],\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}",
            "model:gpt-4",
            "tool-definition|-1||" + TOOL,
            "user-text|0||Hi");
    }

    @Test
    public void shouldIgnoreOtherMembers()
    {
        assertDecoded("{\"stream\":false,\"temperature\":0.5,\"logit_bias\":{\"50256\":-100},\"model\":\"gpt-4\"," +
                "\"messages\":[{\"name\":\"alice\",\"role\":\"user\",\"content\":\"Hello\"}],\"stop\":[\"a\",\"b\"]}",
            "model:gpt-4",
            "user-text|0||Hello");
    }

    @Test
    public void shouldDecodeEscapedText()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":\"line\\nbreak \\u0041 \\\"quoted\\\" \\\\ \\/\"}"),
            "model:gpt-4",
            "user-text|0||line\nbreak A \"quoted\" \\ /");
    }

    @Test
    public void shouldDecodeMultibyteText()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":\"héllo ✓ 😀 \\ud83d\\ude00\"}"),
            "model:gpt-4",
            "user-text|0||héllo ✓ 😀 😀");
    }

    @Test
    public void shouldDecodeEmptyText()
    {
        assertDecoded(request("{\"role\":\"user\",\"content\":\"\"}"),
            "model:gpt-4",
            "user-text|0||");
    }

    @Test
    public void shouldDecodeWhitespaceBetweenTokens()
    {
        assertDecoded(" {\n  \"model\" : \"gpt-4\" ,\n  \"messages\" : [ { \"role\" : \"user\" , \"content\" : \"Hello\" } ]\n} ",
            "model:gpt-4",
            "user-text|0||Hello");
    }

    @Test
    public void shouldDecodeLargeText10k()
    {
        final String text = randomBase64(10_000);

        assertDecoded(request("{\"role\":\"user\",\"content\":\"" + text + "\"}"),
            "model:gpt-4",
            "user-text|0||" + text);
    }

    @Test
    public void shouldDecodeLargeText100k()
    {
        final String text = randomBase64(100_000);

        assertDecoded(request("{\"role\":\"user\",\"content\":\"" + text + "\"}"),
            "model:gpt-4",
            "user-text|0||" + text);
    }

    @Test
    public void shouldDecodeLargeToolResult100k()
    {
        final String text = randomBase64(100_000);

        assertDecoded(request("{\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"" + text + "\"}"),
            "model:gpt-4",
            "tool-result|0|call_1|" + text);
    }

    @Test
    public void shouldDecodeLargeObjectPart()
    {
        final String url = "https://example.com/" + randomBase64(50_000);
        final String image = "{\"type\":\"image_url\",\"image_url\":{\"url\":\"" + url + "\"}}";

        assertDecoded(request("{\"role\":\"user\",\"content\":[" + image + "]}"),
            "model:gpt-4",
            "user-image|0||" + image);
    }

    @Test
    public void shouldDecodeWithLimitedAvailability()
    {
        final String text = randomBase64(100_000);
        final String json = request("{\"role\":\"user\",\"content\":\"" + text + "\"}");

        final Run run = run(json, 4096, 256, HOLD);

        assertThat(run.decoder.status(), equalTo(Status.COMPLETE));
        assertThat(run.sink.events, equalTo(List.of("model:gpt-4", "user-text|0||" + text)));
    }

    @Test
    public void shouldBlockWithoutAvailability()
    {
        final String json = request("{\"role\":\"user\",\"content\":\"Hello\"}");

        final Run run = run(json, Integer.MAX_VALUE, 0, HOLD);

        assertThat(run.decoder.status(), equalTo(Status.BLOCKED));
        assertThat(run.sink.events, equalTo(List.of("model:gpt-4")));
    }

    @Test
    public void shouldRejectMissingModel()
    {
        assertRejected("{\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}");
    }

    @Test
    public void shouldRejectMissingRole()
    {
        assertRejected(request("{\"content\":\"Hello\"}"));
    }

    @Test
    public void shouldRejectInvalidJson()
    {
        assertRejected("{\"model\":\"gpt-4\",\"messages\":[}");
    }

    @Test
    public void shouldRejectNonObjectRequest()
    {
        assertRejected("[]");
    }

    @Test
    public void shouldRejectNonArrayMessages()
    {
        assertRejected("{\"model\":\"gpt-4\",\"messages\":\"Hello\"}");
    }

    @Test
    public void shouldRejectTrailingContent()
    {
        assertRejected(request("{\"role\":\"user\",\"content\":\"Hello\"}") + "x");
    }

    @Test
    public void shouldRejectTruncatedRequest()
    {
        assertRejected("{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hel");
    }

    @Test
    public void shouldRejectRoleBeyondHold()
    {
        final String text = randomBase64(200);

        final Run run = run("{\"model\":\"gpt-4\",\"messages\":[{\"content\":\"" + text + "\",\"role\":\"user\"}]}",
            16, Integer.MAX_VALUE, 64);

        assertThat(run.decoder.status(), equalTo(Status.REJECTED));
        assertThat(run.sink.events, equalTo(List.of("model:gpt-4")));
    }

    @Test
    public void shouldDecodeRoleWithinHold()
    {
        final String text = randomBase64(40);

        final Run run = run("{\"model\":\"gpt-4\",\"messages\":[{\"content\":\"" + text + "\",\"role\":\"user\"}]}",
            16, Integer.MAX_VALUE, 128);

        assertThat(run.decoder.status(), equalTo(Status.COMPLETE));
        assertThat(run.sink.events, equalTo(List.of("model:gpt-4", "user-text|0||" + text)));
    }

    @Test
    public void shouldRejectModelBeyondHold()
    {
        final String text = randomBase64(200);

        final Run run = run("{\"messages\":[{\"role\":\"user\",\"content\":\"" + text + "\"}],\"model\":\"gpt-4\"}",
            16, Integer.MAX_VALUE, 64);

        assertThat(run.decoder.status(), equalTo(Status.REJECTED));
        assertThat(run.sink.events, equalTo(List.of()));
    }

    private static String request(
        String messages)
    {
        return request(messages, null);
    }

    private static String request(
        String messages,
        String tools)
    {
        return "{\"model\":\"gpt-4\",\"messages\":[" + messages + "]" + (tools != null ? ",\"tools\":[" + tools + "]" : "") + "}";
    }

    private static String randomBase64(
        int length)
    {
        final byte[] bytes = new byte[length];
        new Random(length).nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private static void assertDecoded(
        String json,
        String... expected)
    {
        for (int chunk : CHUNKS)
        {
            final Run run = run(json, chunk, Integer.MAX_VALUE, HOLD);

            assertThat("status at chunk " + chunk, run.decoder.status(), equalTo(Status.COMPLETE));
            assertThat("events at chunk " + chunk, run.sink.events, equalTo(List.of(expected)));
        }
    }

    private static void assertRejected(
        String json)
    {
        for (int chunk : CHUNKS)
        {
            final Run run = run(json, chunk, Integer.MAX_VALUE, HOLD);

            assertThat("status at chunk " + chunk, run.decoder.status(), equalTo(Status.REJECTED));
        }
    }

    private static Run run(
        String json,
        int chunk,
        int availability,
        int hold)
    {
        final Recorder sink = new Recorder(availability);
        final LlmOpenaiRequestDecoder decoder = new LlmOpenaiRequestDecoder(sink, hold);

        final byte[] input = json.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[input.length + 16]);

        int held = 0;
        int fed = 0;

        while (fed < input.length || held > 0)
        {
            final int take = (int) Math.min(chunk, input.length - fed);
            buffer.putBytes(held, input, fed, take);
            held += take;
            fed += take;

            final boolean last = fed == input.length;

            int consumed;
            do
            {
                sink.replenish();
                consumed = decoder.decode(buffer, 0, held, last);
                buffer.putBytes(0, buffer, consumed, held - consumed);
                held -= consumed;
            }
            while (consumed > 0 && held > 0);

            if (decoder.status() == Status.COMPLETE || decoder.status() == Status.REJECTED || last && consumed == 0)
            {
                break;
            }
        }

        return new Run(decoder, sink);
    }

    private static final class Run
    {
        private final LlmOpenaiRequestDecoder decoder;
        private final Recorder sink;

        private Run(
            LlmOpenaiRequestDecoder decoder,
            Recorder sink)
        {
            this.decoder = decoder;
            this.sink = sink;
        }
    }

    private static final class Recorder implements Sink
    {
        private final List<String> events = new ArrayList<>();
        private final int capacity;

        private int available;
        private String type;
        private int message;
        private String extension;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private Recorder(
            int capacity)
        {
            this.capacity = capacity;
        }

        private void replenish()
        {
            available = capacity;
        }

        @Override
        public int available()
        {
            return available;
        }

        @Override
        public void model(
            String model)
        {
            events.add("model:" + model);
        }

        @Override
        public void block(
            String type,
            int message,
            String extension)
        {
            this.type = type;
            this.message = message;
            this.extension = extension;
            this.bytes.reset();
        }

        @Override
        public void data(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last)
        {
            final byte[] chunk = new byte[length];
            buffer.getBytes(offset, chunk);
            bytes.writeBytes(chunk);
            available -= length;

            if (last)
            {
                events.add(type + "|" + message + "|" + (extension != null ? extension : "") + "|" +
                    new String(bytes.toByteArray(), UTF_8));
            }
        }
    }
}
