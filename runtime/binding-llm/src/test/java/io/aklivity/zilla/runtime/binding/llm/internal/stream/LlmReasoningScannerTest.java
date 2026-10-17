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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmReasoningScannerTest
{
    private final LlmReasoningScanner scanner = new LlmReasoningScanner(LlmReasoningTags.DEFAULTS);
    private final List<String> pieces = new ArrayList<>();
    private final LlmReasoningScanner.Visitor visitor = new LlmReasoningScanner.Visitor()
    {
        @Override
        public void text(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            pieces.add("text:" + buffer.getStringWithoutLengthUtf8(offset, length));
        }

        @Override
        public void reasoningBegin(
            String tag)
        {
            pieces.add("begin:" + tag);
        }

        @Override
        public void reasoning(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            pieces.add("reasoning:" + buffer.getStringWithoutLengthUtf8(offset, length));
        }

        @Override
        public void reasoningEnd()
        {
            pieces.add("end");
        }
    };

    @Test
    public void shouldPassTextWithoutDelimiters()
    {
        scanner.begin();
        accept("plain 1 < 2 > 0");
        scanner.end(visitor);

        assertThat(merged(pieces), contains("text:plain 1 < 2 > 0"));
    }

    @Test
    public void shouldSplitBlockAmongText()
    {
        scanner.begin();
        accept("Sure. <think>plan</think>Done");
        scanner.end(visitor);

        assertThat(pieces, contains("text:Sure. ", "begin:think", "reasoning:plan", "end", "text:Done"));
    }

    @Test
    public void shouldRecognizeEveryDefaultTag()
    {
        for (String tag : LlmReasoningTags.DEFAULTS)
        {
            pieces.clear();
            scanner.begin();
            accept("<" + tag + ">x</" + tag + ">y");
            scanner.end(visitor);

            assertThat(tag, pieces, contains("begin:" + tag, "reasoning:x", "end", "text:y"));
        }
    }

    @Test
    public void shouldRecognizeOnlyConfiguredTags()
    {
        final LlmReasoningScanner scanner = new LlmReasoningScanner(Set.of("scratchpad"));
        scanner.begin();
        accept(scanner, "<think>x</think><scratchpad>y</scratchpad>");
        scanner.end(visitor);

        assertThat(merged(pieces), contains("text:<think>x</think>", "begin:scratchpad", "reasoning:y", "end"));
    }

    @Test
    public void shouldSplitSeveralBlocksWithDifferentTags()
    {
        scanner.begin();
        accept("A<think>x</think>B<reasoning>y</reasoning>C");
        scanner.end(visitor);

        assertThat(pieces, contains("text:A", "begin:think", "reasoning:x", "end",
            "text:B", "begin:reasoning", "reasoning:y", "end", "text:C"));
    }

    @Test
    public void shouldDistinguishTagsSharingPrefix()
    {
        scanner.begin();
        accept("<thinking>x</thinking><think>y</think>");
        scanner.end(visitor);

        assertThat(pieces, contains("begin:thinking", "reasoning:x", "end", "begin:think", "reasoning:y", "end"));
    }

    @Test
    public void shouldCloseOnlyWithOpeningTag()
    {
        scanner.begin();
        accept("<think>x</reasoning>y</think>z");
        scanner.end(visitor);

        assertThat(merged(pieces), contains("begin:think", "reasoning:x</reasoning>y", "end", "text:z"));
    }

    @Test
    public void shouldHoldOpeningDelimiterSplitAcrossFrames()
    {
        scanner.begin();
        accept("a <thi");
        accept("nk>x</think>b");
        scanner.end(visitor);

        assertThat(pieces, contains("text:a ", "begin:think", "reasoning:x", "end", "text:b"));
    }

    @Test
    public void shouldHoldClosingDelimiterSplitAcrossFrames()
    {
        scanner.begin();
        accept("<think>Let me th");
        accept("ink.</thi");
        accept("nk>Hello");
        scanner.end(visitor);

        assertThat(pieces, contains("begin:think", "reasoning:Let me th", "reasoning:ink.", "end", "text:Hello"));
    }

    @Test
    public void shouldReleaseHeldPrefixWhenDelimiterDoesNotComplete()
    {
        scanner.begin();
        accept("a <thi");
        accept("s is");
        scanner.end(visitor);

        assertThat(pieces, contains("text:a ", "text:<thi", "text:s is"));
    }

    @Test
    public void shouldRestartDelimiterAfterNearMiss()
    {
        scanner.begin();
        accept("<t<think>x</think>");
        scanner.end(visitor);

        assertThat(pieces, contains("text:<t", "begin:think", "reasoning:x", "end"));
    }

    @Test
    public void shouldNormalizeUnclosedBlockToClosed()
    {
        scanner.begin();
        accept("<think>plan");
        scanner.end(visitor);

        assertThat(pieces, contains("begin:think", "reasoning:plan", "end"));
    }

    @Test
    public void shouldReleaseHeldClosingPrefixAsReasoningAtEnd()
    {
        scanner.begin();
        accept("<think>plan</thi");
        scanner.end(visitor);

        assertThat(pieces, contains("begin:think", "reasoning:plan", "reasoning:</thi", "end"));
    }

    @Test
    public void shouldReleaseHeldOpeningPrefixAsTextAtEnd()
    {
        scanner.begin();
        accept("hi <thi");
        scanner.end(visitor);

        assertThat(pieces, contains("text:hi ", "text:<thi"));
    }

    @Test
    public void shouldFlushHeldOpeningPrefixAsTextAndScanOn()
    {
        scanner.begin();
        accept("a <thi");
        scanner.flush(visitor);
        accept("nk>x");
        scanner.end(visitor);

        assertThat(merged(pieces), contains("text:a <think>x"));
    }

    @Test
    public void shouldFlushHeldClosingPrefixAsReasoningAndKeepBlockOpen()
    {
        scanner.begin();
        accept("<think>x</thi");
        scanner.flush(visitor);
        accept("y</think>z");
        scanner.end(visitor);

        assertThat(merged(pieces), contains("begin:think", "reasoning:x</thiy", "end", "text:z"));
    }

    @Test
    public void shouldKeepBlockOpenAcrossFrames()
    {
        scanner.begin();
        accept("<think>x");
        scanner.flush(visitor);
        accept("y");
        scanner.flush(visitor);
        accept("</think>z");
        scanner.end(visitor);

        assertThat(merged(pieces), contains("begin:think", "reasoning:xy", "end", "text:z"));
    }

    @Test
    public void shouldExposeTagWhileInsideBlock()
    {
        scanner.begin();
        assertThat(scanner.tag(), nullValue());
        accept("<think>x");
        assertThat(scanner.tag(), equalTo("think"));
        accept("</think>");
        assertThat(scanner.tag(), nullValue());
    }

    @Test
    public void shouldReportShortestBlockLength()
    {
        assertThat(new LlmReasoningScanner(Set.of("think", "ab")).minBlock(), equalTo(9));
    }

    @Test
    public void shouldEmitEmptyBlock()
    {
        scanner.begin();
        accept("<think></think>");
        scanner.end(visitor);

        assertThat(pieces, contains("begin:think", "end"));
    }

    @Test
    public void shouldEmitNothingForEmptyPayload()
    {
        scanner.begin();
        accept("");
        scanner.end(visitor);

        assertThat(pieces, empty());
    }

    @Test
    public void shouldScanAgainAfterBegin()
    {
        scanner.begin();
        accept("<think>x");
        scanner.end(visitor);
        scanner.begin();
        accept("<reasoning>y</reasoning>z");
        scanner.end(visitor);

        assertThat(pieces, contains("begin:think", "reasoning:x", "end", "begin:reasoning", "reasoning:y", "end", "text:z"));
    }

    @Test
    public void shouldProduceSameSequenceForEveryFrameSize()
    {
        final String text = "ab <t<thinking>x</thin</think>y</thinking> c <reas<reason>z</reason>";
        final List<String> expected = merged(scan(text, text.length()));

        for (int size = 1; size < text.length(); size++)
        {
            assertThat("size " + size, merged(scan(text, size)), contains(expected.toArray()));
        }
    }

    private List<String> scan(
        String text,
        int size)
    {
        pieces.clear();
        scanner.begin();
        final byte[] bytes = text.getBytes(UTF_8);
        for (int offset = 0; offset < bytes.length; offset += size)
        {
            final DirectBufferEx buffer = new UnsafeBufferEx(bytes);
            scanner.accept(buffer, offset, Math.min(size, bytes.length - offset), visitor);
        }
        scanner.end(visitor);
        return new ArrayList<>(pieces);
    }

    private static List<String> merged(
        List<String> pieces)
    {
        final List<String> merged = new ArrayList<>();
        for (String piece : pieces)
        {
            final int last = merged.size() - 1;
            final boolean data = piece.startsWith("text:") || piece.startsWith("reasoning:");
            final String kind = piece.substring(0, piece.indexOf(':') + 1);
            if (data && last >= 0 && merged.get(last).startsWith(kind))
            {
                merged.set(last, merged.get(last) + piece.substring(kind.length()));
            }
            else
            {
                merged.add(piece);
            }
        }
        return merged;
    }

    private void accept(
        String text)
    {
        accept(scanner, text);
    }

    private void accept(
        LlmReasoningScanner scanner,
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);
        final DirectBufferEx buffer = new UnsafeBufferEx(bytes);
        scanner.accept(buffer, 0, bytes.length, visitor);
    }
}
