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

public class LlmElementSplitterTest
{
    @Test
    public void shouldSplitSingleElement()
    {
        assertSplits("[{\"a\":1}]", "{\"a\":1}");
    }

    @Test
    public void shouldSplitEveryElement()
    {
        assertSplits("[{\"a\":1},{\"b\":[2,{\"c\":\"]}\"}]},{}]", "{\"a\":1}", "{\"b\":[2,{\"c\":\"]}\"}]}", "{}");
    }

    @Test
    public void shouldSplitAcrossWhitespace()
    {
        assertSplits(" [ { \"a\" : 1 } ,\n{ \"b\" : \"two\" } ] ", "{ \"a\" : 1 }", "{ \"b\" : \"two\" }");
    }

    @Test
    public void shouldSplitAroundEscapes()
    {
        assertSplits("[{\"k\\\"ey\":\"v\\\\\"},{\"z\":\"}\\u00e9\\n\"}]", "{\"k\\\"ey\":\"v\\\\\"}", "{\"z\":\"}\\u00e9\\n\"}");
    }

    @Test
    public void shouldSplitEmptyArrayIntoNoElements()
    {
        assertSplits("[]");
        assertSplits("[ ]");
    }

    @Test
    public void shouldMarkFirstFrameOfEveryElementAsInit()
    {
        final List<Boolean> inits = new ArrayList<>();
        final LlmElementSplitter splitter = new LlmElementSplitter(
            (buffer, offset, length, init, last) -> inits.add(init));
        final byte[] bytes = "[{\"a\":\"xyz\"},{\"b\":1}]".getBytes(UTF_8);

        splitter.begin();

        for (int index = 0; index < bytes.length; index++)
        {
            splitter.put(new UnsafeBufferEx(bytes), index, 1);
        }

        assertThat(inits, contains(true, false, false, false, false, false, false, false, false, false, false,
            true, false, false, false, false, false, false));
    }

    @Test
    public void shouldRejectNonObjectElement()
    {
        final LlmElementSplitter splitter = new LlmElementSplitter((buffer, offset, length, init, last) -> true);
        final byte[] bytes = "[1]".getBytes(UTF_8);

        splitter.begin();

        assertThat(splitter.put(new UnsafeBufferEx(bytes), 0, bytes.length), is(false));
    }

    @Test
    public void shouldStopWhenElementRejected()
    {
        final LlmElementSplitter splitter = new LlmElementSplitter((buffer, offset, length, init, last) -> false);
        final byte[] bytes = "[{\"a\":1},{\"b\":2}]".getBytes(UTF_8);

        splitter.begin();

        assertThat(splitter.put(new UnsafeBufferEx(bytes), 0, bytes.length), is(false));
    }

    private static void assertSplits(
        String json,
        String... expected)
    {
        for (int window = 1; window <= json.length(); window++)
        {
            final List<String> elements = new ArrayList<>();
            final StringBuilder element = new StringBuilder();
            final LlmElementSplitter splitter = new LlmElementSplitter(
                (buffer, offset, length, init, last) -> collect(elements, element, buffer, offset, length, last));
            final byte[] bytes = json.getBytes(UTF_8);
            final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

            splitter.begin();

            for (int offset = 0; offset < bytes.length; offset += window)
            {
                splitter.put(buffer, offset, Math.min(window, bytes.length - offset));
            }

            if (expected.length == 0)
            {
                assertThat("window " + window, elements, empty());
            }
            else
            {
                assertThat("window " + window, elements, contains(expected));
            }
        }
    }

    private static boolean collect(
        List<String> elements,
        StringBuilder element,
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        element.append(buffer.getStringWithoutLengthUtf8(offset, length));

        if (last)
        {
            elements.add(element.toString());
            element.setLength(0);
        }

        return true;
    }
}
