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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmMemberCollectorTest
{
    private static final String[] CONFIG = {"generationConfig"};
    private static final String[] BOTH = {"generationConfig", "toolConfig"};
    private static final int CAPACITY = 4096;

    @Test
    public void shouldPassMembersThatAreNotContainers()
    {
        final String members = "\"a\":1,\"b\":{\"c\":[2,{\"d\":\"e\"}]},\"f\":\"g\"";

        assertCollects(CONFIG, CAPACITY, members, members, List.of());
    }

    @Test
    public void shouldHoldContainerContentsAndOmitTheContainer()
    {
        assertCollects(CONFIG, CAPACITY,
            "\"generationConfig\":{\"temperature\":0.7}",
            "",
            List.of("\"temperature\":0.7"));
    }

    @Test
    public void shouldHoldEachFragmentOfTheSameContainer()
    {
        assertCollects(CONFIG, CAPACITY,
            "\"generationConfig\":{\"temperature\":0.7},\"generationConfig\":{\"topP\":0.9,\"topK\":40}",
            "",
            List.of("\"temperature\":0.7", "\"topP\":0.9,\"topK\":40"));
    }

    @Test
    public void shouldJoinPassedMembersAroundHeldContainer()
    {
        assertCollects(CONFIG, CAPACITY,
            "\"a\":1,\"generationConfig\":{\"x\":1},\"b\":2",
            "\"a\":1,\"b\":2",
            List.of("\"x\":1"));
    }

    @Test
    public void shouldNotLeadWithSeparatorWhenContainerComesFirst()
    {
        assertCollects(CONFIG, CAPACITY,
            "\"generationConfig\":{\"x\":1},\"a\":1,\"generationConfig\":{\"y\":2},\"b\":2",
            "\"a\":1,\"b\":2",
            List.of("\"x\":1", "\"y\":2"));
    }

    @Test
    public void shouldNotTrailWithSeparatorWhenContainerComesLast()
    {
        assertCollects(CONFIG, CAPACITY,
            "\"a\":1,\"generationConfig\":{\"x\":1}",
            "\"a\":1",
            List.of("\"x\":1"));
    }

    @Test
    public void shouldHoldNestedObjectsAndArrays()
    {
        final String contents = "\"a\":{\"b\":[1,{\"c\":[2,3]}],\"d\":{}},\"e\":[]";

        assertCollects(CONFIG, CAPACITY,
            "\"generationConfig\":{" + contents + "},\"z\":0",
            "\"z\":0",
            List.of(contents));
    }

    @Test
    public void shouldHoldStringsWithEscapesAndBraces()
    {
        final String contents = "\"s\":\"a}{b\\\"}\\\\\",\"t\":\"]\",\"u\":\"\\\\\"";

        assertCollects(CONFIG, CAPACITY,
            "\"generationConfig\":{" + contents + "},\"z\":\"}{\\\"\"",
            "\"z\":\"}{\\\"\"",
            List.of(contents));
    }

    @Test
    public void shouldPassStringValueThatNamesTheContainer()
    {
        final String members = "\"a\":\"generationConfig\",\"b\":\"\\\"generationConfig\\\":{\"";

        assertCollects(CONFIG, CAPACITY, members, members, List.of());
    }

    @Test
    public void shouldHoldEmptyFragmentAsPresentContainer()
    {
        final Run run = collect(CONFIG, CAPACITY, 1, "\"generationConfig\":{}");

        assertThat(run.output, equalTo(""));
        assertThat(run.collector.holds(0), is(true));
        assertThat(run.fragments(0), equalTo(List.of()));
    }

    @Test
    public void shouldHoldNothingWhenAbsent()
    {
        final Run run = collect(CONFIG, CAPACITY, 1, "\"a\":1");

        assertThat(run.collector.holds(0), is(false));
        assertThat(run.collector.held(), equalTo(0));
    }

    @Test
    public void shouldPassContainerKeyWhoseValueIsNotAnObject()
    {
        assertCollects(CONFIG, CAPACITY,
            "\"generationConfig\":null,\"a\":1",
            "\"generationConfig\":null,\"a\":1",
            List.of());
    }

    @Test
    public void shouldPassKeyLongerThanTheHead()
    {
        final String members = "\"" + "k".repeat(200) + "\":{\"a\":1},\"b\":2";

        assertCollects(CONFIG, CAPACITY, members, members, List.of());
    }

    @Test
    public void shouldPassKeyWithEscapesLongerThanTheHead()
    {
        final String members = "\"" + "k\\\"".repeat(60) + "\":[1],\"b\":2";

        assertCollects(CONFIG, CAPACITY, members, members, List.of());
    }

    @Test
    public void shouldPassWhitespaceAfterColonThatExceedsTheHead()
    {
        final String members = "\"a\":" + " ".repeat(100) + "1,\"b\":2";

        assertCollects(CONFIG, CAPACITY, members, members, List.of());
    }

    @Test
    public void shouldHoldContainerAfterWhitespace()
    {
        assertCollects(CONFIG, CAPACITY,
            "\"generationConfig\": {\"x\":1} ,\"a\":1",
            "\"a\":1",
            List.of("\"x\":1"));
    }

    @Test
    public void shouldHoldEachContainerSeparately()
    {
        final Run run = collect(BOTH, CAPACITY, 3,
            "\"generationConfig\":{\"a\":1},\"toolConfig\":{\"b\":2},\"x\":0,\"generationConfig\":{\"c\":3}");

        assertThat(run.output, equalTo("\"x\":0"));
        assertThat(run.fragments(0), equalTo(List.of("\"a\":1", "\"c\":3")));
        assertThat(run.fragments(1), equalTo(List.of("\"b\":2")));
        assertThat(run.collector.holds(0), is(true));
        assertThat(run.collector.holds(1), is(true));
    }

    @Test
    public void shouldHoldAcrossRuns()
    {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final UnsafeBufferEx store = new UnsafeBufferEx(new byte[CAPACITY]);
        final LlmMemberCollector collector = new LlmMemberCollector(CONFIG,
            (buffer, offset, length) -> collect(output, buffer, offset, length), () -> store);

        collector.begin();
        feed(collector, "\"a\":1,\"generationConfig\":{\"x\":1}", 5);
        collector.begin();
        feed(collector, "\"b\":2,\"generationConfig\":{\"y\":2}", 5);

        assertThat(output.toString(UTF_8), equalTo("\"a\":1\"b\":2"));
        assertThat(drained(collector, 0), equalTo(List.of("\"x\":1", "\"y\":2")));
    }

    @Test
    public void shouldForgetHeldOnReset()
    {
        final Run run = collect(CONFIG, CAPACITY, 4, "\"generationConfig\":{\"x\":1}");

        run.collector.reset();

        assertThat(run.collector.held(), equalTo(0));
        assertThat(run.collector.holds(0), is(false));
        assertThat(run.collector.overflowed(), is(false));
    }

    @Test
    public void shouldOverflowWhenContentsExceedTheStore()
    {
        for (int window = 1; window <= 12; window++)
        {
            final Run run = collect(CONFIG, 32, window,
                "\"generationConfig\":{\"temperature\":0.7,\"topP\":0.9,\"topK\":40},\"a\":1");

            assertThat("window " + window, run.collector.overflowed(), is(true));
            assertThat("window " + window, run.collector.overflow(), equalTo("generationConfig"));
        }
    }

    @Test
    public void shouldOverflowWhenTheStoreIsUnavailable()
    {
        final LlmMemberCollector collector = new LlmMemberCollector(CONFIG, (buffer, offset, length) ->
        {
        }, () -> null);

        collector.begin();
        feed(collector, "\"generationConfig\":{\"x\":1}", 3);

        assertThat(collector.overflowed(), is(true));
        assertThat(collector.overflow(), equalTo("generationConfig"));
    }

    @Test
    public void shouldHoldExactlyToCapacity()
    {
        final String contents = "\"a\":1";
        final int capacity = 5 + contents.length();

        for (int window = 1; window <= contents.length() + 2; window++)
        {
            final Run run = collect(CONFIG, capacity, window, "\"generationConfig\":{" + contents + "}");

            assertThat("window " + window, run.collector.overflowed(), is(false));
            assertThat("window " + window, run.fragments(0), equalTo(List.of(contents)));
        }
    }

    @Test
    public void shouldNotReportOverflowWhenNothingOverflowed()
    {
        final Run run = collect(CONFIG, CAPACITY, 2, "\"a\":1");

        assertThat(run.collector.overflowed(), is(false));
        assertThat(run.collector.overflow(), nullValue());
    }

    private static void assertCollects(
        String[] containers,
        int capacity,
        String members,
        String expectedOutput,
        List<String> expectedFragments)
    {
        for (int window = 1; window <= members.length() + 1; window++)
        {
            final Run run = collect(containers, capacity, window, members);

            assertThat("window " + window, run.output, equalTo(expectedOutput));
            assertThat("window " + window + " overflowed", run.collector.overflowed(), is(false));
            assertThat("window " + window + " fragments", run.fragments(0), equalTo(expectedFragments));
        }
    }

    private static Run collect(
        String[] containers,
        int capacity,
        int window,
        String members)
    {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final UnsafeBufferEx store = new UnsafeBufferEx(new byte[capacity]);
        final LlmMemberCollector collector = new LlmMemberCollector(containers,
            (buffer, offset, length) -> collect(output, buffer, offset, length), () -> store);

        collector.begin();
        feed(collector, members, window);

        return new Run(collector, output.toString(UTF_8));
    }

    private static void feed(
        LlmMemberCollector collector,
        String members,
        int window)
    {
        final byte[] bytes = members.getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);

        for (int offset = 0; offset < bytes.length; offset += window)
        {
            collector.put(input, offset, Math.min(window, bytes.length - offset));
        }
    }

    private static List<String> drained(
        LlmMemberCollector collector,
        int container)
    {
        final List<String> fragments = new ArrayList<>();

        collector.drain(container, (buffer, offset, length) -> fragments.add(buffer.getStringWithoutLengthUtf8(offset, length)));

        return fragments;
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

    private static final class Run
    {
        private final LlmMemberCollector collector;
        private final String output;

        private Run(
            LlmMemberCollector collector,
            String output)
        {
            this.collector = collector;
            this.output = output;
        }

        private List<String> fragments(
            int container)
        {
            return drained(collector, container);
        }
    }
}
