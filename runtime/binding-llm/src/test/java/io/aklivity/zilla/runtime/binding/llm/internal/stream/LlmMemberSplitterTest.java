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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmMemberSplitterTest
{
    @Test
    public void shouldSplitSingleMember()
    {
        assertSplits("{\"a\":1}", "{\"a\":1}");
    }

    @Test
    public void shouldSplitEveryMember()
    {
        assertSplits("{\"a\":1,\"b\":\"two\",\"c\":[3,{\"x\":\"y]\"}],\"d\":null,\"e\":true,\"f\":{\"g\":{}}}",
            "{\"a\":1}", "{\"b\":\"two\"}", "{\"c\":[3,{\"x\":\"y]\"}]}", "{\"d\":null}", "{\"e\":true}",
            "{\"f\":{\"g\":{}}}");
    }

    @Test
    public void shouldSplitAcrossWhitespace()
    {
        assertSplits(" { \"a\" : 1 , \"b\" :\n\"two\" ,\t\"c\" : [ 3 ] } ",
            "{\"a\" : 1}", "{\"b\" :\n\"two\"}", "{\"c\" : [ 3 ]}");
    }

    @Test
    public void shouldSplitAroundEscapes()
    {
        assertSplits("{\"k\\\"ey\":\"v\\\\\",\"z\":\"}\\u00e9\\n\",\"y\":\"\\\\\"}",
            "{\"k\\\"ey\":\"v\\\\\"}", "{\"z\":\"}\\u00e9\\n\"}", "{\"y\":\"\\\\\"}");
    }

    @Test
    public void shouldSplitEmptyObjectIntoNoMembers()
    {
        assertSplits("{}");
        assertSplits("{ }");
    }

    @Test
    public void shouldSplitScalarFollowedByObjectEnd()
    {
        assertSplits("{\"a\":-1.5e3}", "{\"a\":-1.5e3}");
        assertSplits("{\"a\":true}", "{\"a\":true}");
    }

    @Test
    public void shouldPassMalformedPayloadThrough()
    {
        assertSplits("[1,2]", "{[1,2]}");
    }

    @Test
    public void shouldStopWhenMemberRejected()
    {
        final LlmMemberSplitter splitter = new LlmMemberSplitter((buffer, offset, length, init, last) -> false);
        final byte[] bytes = "{\"a\":1,\"b\":2}".getBytes(UTF_8);

        splitter.begin();

        assertThat(splitter.put(new UnsafeBufferEx(bytes), 0, bytes.length, true), is(false));
    }

    @Test
    public void shouldSplitAgainAfterBegin()
    {
        final List<String> members = new ArrayList<>();
        final StringBuilder member = new StringBuilder();
        final LlmMemberSplitter splitter = new LlmMemberSplitter(
            (buffer, offset, length, init, last) -> collect(members, member, buffer, offset, length, last));

        feed(splitter, "{\"a\":1}", 3);
        feed(splitter, "{\"b\":2}", 3);

        assertThat(members, contains("{\"a\":1}", "{\"b\":2}"));
    }

    private static void assertSplits(
        String json,
        String... expected)
    {
        for (int window = 1; window <= json.length(); window++)
        {
            final List<String> members = new ArrayList<>();
            final StringBuilder member = new StringBuilder();
            final LlmMemberSplitter splitter = new LlmMemberSplitter(
                (buffer, offset, length, init, last) -> collect(members, member, buffer, offset, length, last));

            feed(splitter, json, window);

            if (expected.length == 0)
            {
                assertThat("window " + window, members, empty());
            }
            else
            {
                assertThat("window " + window, members, contains(expected));
            }
        }
    }

    private static void feed(
        LlmMemberSplitter splitter,
        String json,
        int window)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

        splitter.begin();

        for (int offset = 0; offset < bytes.length; offset += window)
        {
            final int length = Math.min(window, bytes.length - offset);

            splitter.put(buffer, offset, length, offset + length == bytes.length);
        }
    }

    private static boolean collect(
        List<String> members,
        StringBuilder member,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        member.append(buffer.getStringWithoutLengthUtf8(offset, length));

        if (last)
        {
            members.add(member.toString());
            member.setLength(0);
        }

        return true;
    }
}
