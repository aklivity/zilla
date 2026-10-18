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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmTextPipelineTest
{
    private static final int[] WINDOWS = {1, 2, 3, 7, 13, 64, 100, 1000};
    private static final String TEXT = "Let me think. ".repeat(400);

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final LlmTextPipeline pipeline = new LlmTextPipeline(this::collect);

    @Test
    public void shouldDeliverTextContent()
    {
        assertDelivers(json("{'text':'Let me think.'}"), "Let me think.");
    }

    @Test
    public void shouldDeliverDecodedContent()
    {
        assertDelivers(json("{'text':'line one\\nline \\\"two\\\"\\ttab\\\\ \\u0001'}"),
            "line one\nline \"two\"\ttab\\ \u0001");
    }

    @Test
    public void shouldDeliverUnicodeContent()
    {
        assertDelivers(json("{'text':'café 😀 \\ud83d\\ude00 \\u00e9'}"),
            "café 😀 😀 é");
    }

    @Test
    public void shouldDeliverContentLargerThanWindow()
    {
        assertDelivers(json("{'text':'" + TEXT + "'}"), TEXT);
    }

    @Test
    public void shouldDeliverEmptyContent()
    {
        assertDelivers(json("{'text':''}"), "");
    }

    @Test
    public void shouldDropOtherMembers()
    {
        assertDelivers(json("{'anthropic:signature':'sig','text':'t','openai:note':{'a':[1,2]}}"), "t");
    }

    @Test
    public void shouldDeliverSeveralSectionsInOrder()
    {
        for (String json : new String[] {json("{'text':'A '}"), json("{'text':'B'}")})
        {
            pipeline.begin();
            final byte[] bytes = json.getBytes(UTF_8);
            assertThat(pipeline.feed(new UnsafeBufferEx(bytes), 0, bytes.length, true), is(true));
        }

        assertThat(output.toString(UTF_8), equalTo("A B"));
    }

    private void assertDelivers(
        String json,
        String expected)
    {
        for (int window : WINDOWS)
        {
            final byte[] bytes = json.getBytes(UTF_8);
            final UnsafeBufferEx input = new UnsafeBufferEx(bytes);

            output.reset();
            pipeline.begin();

            boolean accepted = true;

            for (int offset = 0; accepted && offset < bytes.length; offset += window)
            {
                final int length = Math.min(window, bytes.length - offset);

                accepted = pipeline.feed(input, offset, length, offset + length == bytes.length);
            }

            assertThat("accepted window " + window, accepted, is(true));
            assertThat("window " + window, output.toString(UTF_8), equalTo(expected));
        }
    }

    private void collect(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final byte[] chunk = new byte[length];

        buffer.getBytes(offset, chunk);
        output.write(chunk, 0, length);
    }
}
