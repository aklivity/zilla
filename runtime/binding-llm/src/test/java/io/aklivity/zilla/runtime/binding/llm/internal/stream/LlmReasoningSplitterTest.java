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

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmReasoningSplitterTest
{
    private final LlmReasoningSplitter splitter = new LlmReasoningSplitter();
    private final List<String> pieces = new ArrayList<>();
    private final LlmReasoningSplitter.Visitor visitor = this::record;

    @Test
    public void shouldPassTextThroughBeforeActivation()
    {
        splitter.begin();
        accept("a <reasoning>b</reasoning>");
        splitter.end(visitor);

        assertThat(pieces, contains("text:a <reasoning>b</reasoning>"));
    }

    @Test
    public void shouldSplitBlockAmongText()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("Sure. <reasoning>plan</reasoning>Done");
        splitter.end(visitor);

        assertThat(pieces, contains("text:Sure. ", "reasoning:plan", "text:Done"));
    }

    @Test
    public void shouldSplitSeveralBlocks()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("A<reasoning>x</reasoning>B<reasoning>y</reasoning>C");
        splitter.end(visitor);

        assertThat(pieces, contains("text:A", "reasoning:x", "text:B", "reasoning:y", "text:C"));
    }

    @Test
    public void shouldHoldOpeningDelimiterSplitAcrossFrames()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("a <reas");
        accept("oning>x</reasoning>b");
        splitter.end(visitor);

        assertThat(pieces, contains("text:a ", "reasoning:x", "text:b"));
    }

    @Test
    public void shouldHoldClosingDelimiterSplitAcrossFrames()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("<reasoning>Let me th");
        accept("ink.</reaso");
        accept("ning>Hello");
        splitter.end(visitor);

        assertThat(pieces, contains("reasoning:Let me th", "reasoning:ink.", "text:Hello"));
    }

    @Test
    public void shouldReleaseHeldPrefixWhenDelimiterDoesNotComplete()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("a <rea");
        accept("dy");
        splitter.end(visitor);

        assertThat(pieces, contains("text:a ", "text:<rea", "text:dy"));
    }

    @Test
    public void shouldRestartDelimiterAfterNearMiss()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("<r<reasoning>x");
        splitter.end(visitor);

        assertThat(pieces, contains("text:<r", "reasoning:x"));
    }

    @Test
    public void shouldEndUnclosedBlockAtEnd()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("<reasoning>think</reas");
        splitter.end(visitor);
        accept("after");

        assertThat(pieces, contains("reasoning:think", "reasoning:</reas", "text:after"));
    }

    @Test
    public void shouldReleaseHeldOpeningPrefixAtEnd()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("hi <reas");
        splitter.end(visitor);

        assertThat(pieces, contains("text:hi ", "text:<reas"));
    }

    @Test
    public void shouldEmitNothingForEmptyPayload()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("");
        splitter.end(visitor);

        assertThat(pieces, empty());
    }

    @Test
    public void shouldScanForAnotherTagAfterRestart()
    {
        splitter.begin();
        splitter.activate("reasoning");
        accept("<reasoning>x</reasoning>");
        splitter.end(visitor);
        splitter.begin();
        splitter.activate("think");
        accept("<think>y</think>z");
        splitter.end(visitor);

        assertThat(pieces, contains("reasoning:x", "reasoning:y", "text:z"));
    }

    private void accept(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);
        final DirectBufferEx buffer = new UnsafeBufferEx(bytes);
        splitter.accept(buffer, 0, bytes.length, visitor);
    }

    private void record(
        boolean reasoning,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        pieces.add((reasoning ? "reasoning:" : "text:") + buffer.getStringWithoutLengthUtf8(offset, length));
    }
}
