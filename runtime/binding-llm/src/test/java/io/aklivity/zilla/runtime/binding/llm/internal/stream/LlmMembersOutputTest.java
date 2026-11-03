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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmMembersOutputTest
{
    @Test
    public void shouldTrimObjectBracesAtEveryChunkBoundary()
    {
        final String json = "{\"a\":1,\"b\":{\"c\":[2]}}";

        for (int split = 0; split <= json.length(); split++)
        {
            assertThat("split " + split, trim(json.substring(0, split), json.substring(split)),
                equalTo("\"a\":1,\"b\":{\"c\":[2]}"));
        }
    }

    @Test
    public void shouldTrimObjectBracesOneByteAtATime()
    {
        final String json = "{\"a\":1,\"b\":2}";
        final String[] chunks = new String[json.length()];

        for (int index = 0; index < chunks.length; index++)
        {
            chunks[index] = json.substring(index, index + 1);
        }

        assertThat(trim(chunks), equalTo("\"a\":1,\"b\":2"));
    }

    @Test
    public void shouldTrimEmptyObjectToNothing()
    {
        assertThat(trim("{}"), equalTo(""));
    }

    @Test
    public void shouldTrimWhenLastIsSignalledOnEmptyChunk()
    {
        assertThat(trim("{\"a\":1}", ""), equalTo("\"a\":1"));
    }

    @Test
    public void shouldTrimAgainAfterBegin()
    {
        final ByteArrayOutputStream collected = new ByteArrayOutputStream();
        final LlmMembersOutput output = new LlmMembersOutput(
            (buffer, offset, length) -> collect(collected, buffer, offset, length));

        put(output, "{\"a\":1}", true);
        output.begin();
        put(output, "{\"b\":2}", true);

        assertThat(collected.toString(UTF_8), equalTo("\"a\":1\"b\":2"));
    }

    private static String trim(
        String... chunks)
    {
        final ByteArrayOutputStream collected = new ByteArrayOutputStream();
        final LlmMembersOutput output = new LlmMembersOutput(
            (buffer, offset, length) -> collect(collected, buffer, offset, length));

        for (int index = 0; index < chunks.length; index++)
        {
            put(output, chunks[index], index == chunks.length - 1);
        }

        return collected.toString(UTF_8);
    }

    private static void put(
        LlmMembersOutput output,
        String chunk,
        boolean last)
    {
        final byte[] bytes = chunk.getBytes(UTF_8);

        output.put(new UnsafeBufferEx(bytes), 0, bytes.length, last);
    }

    private static void collect(
        ByteArrayOutputStream collected,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        collected.writeBytes(bytes);
    }
}
