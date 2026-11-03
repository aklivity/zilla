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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmRangeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmRangeQueueTest
{
    private final LlmRangeQueue queue = new LlmRangeQueue(1024);

    @Test
    public void shouldEmitRangesCoveringTheirBytes()
    {
        queue.begin();
        queue.accept(ranges(range(6, true, "a=1"), range(6, true, "b=2")));

        assertThat(emit(12), contains("6:closing:a=1", "6:closing:b=2"));
    }

    @Test
    public void shouldMergePartsOfOneRangeEmittedTogether()
    {
        queue.begin();
        queue.accept(ranges(range(6, true, "a=1"), range(3, false)));
        queue.accept(ranges(range(3, true, "b=2")));

        assertThat(emit(12), contains("6:closing:a=1", "6:closing:b=2"));
    }

    @Test
    public void shouldCarryRangeSplitByEmittedBytes()
    {
        queue.begin();
        queue.accept(ranges(range(6, true, "a=1"), range(6, true, "b=2")));

        assertThat(emit(4), contains("4:open"));
        assertThat(emit(5), contains("2:closing:a=1", "3:open"));
        assertThat(emit(3), contains("3:closing:b=2"));
    }

    @Test
    public void shouldEmitRangeWithoutBytesAtItsPosition()
    {
        queue.begin();
        queue.accept(ranges(range(3, true, "a=1"), range(0, true, "z=9"), range(3, true, "b=2")));

        assertThat(emit(3), contains("3:closing:a=1", "0:closing:z=9"));
        assertThat(emit(3), contains("3:closing:b=2"));
    }

    @Test
    public void shouldEmitNothingWhenNoRangesPending()
    {
        queue.begin();

        assertThat(emit(5), empty());
    }

    @Test
    public void shouldDropRangesBeyondBudget()
    {
        final LlmRangeQueue small = new LlmRangeQueue(24);
        small.begin();
        small.accept(ranges(range(6, true, "a=1"), range(6, true, "b=2")));

        assertThat(emit(small, 12), empty());
    }

    @Test
    public void shouldGrowToHoldManyRanges()
    {
        final LlmRangeQueue large = new LlmRangeQueue(1 << 16);
        large.begin();

        final List<String> expected = new ArrayList<>();
        for (int index = 0; index < 40; index++)
        {
            large.accept(ranges(range(1, true, "t=" + index)));
            expected.add("1:closing:t=" + index);
        }

        assertThat(emit(large, 40), contains(expected.toArray(String[]::new)));
    }

    @Test
    public void shouldBeginAfreshForNextSection()
    {
        queue.begin();
        queue.accept(ranges(range(6, true, "a=1")));
        emit(2);

        queue.begin();
        queue.accept(ranges(range(1, true, "c=3")));

        assertThat(emit(1), contains("1:closing:c=3"));
    }

    private List<String> emit(
        int values)
    {
        return emit(queue, values);
    }

    private static List<String> emit(
        LlmRangeQueue queue,
        int values)
    {
        final LlmSectionDataExFW.Builder section = new LlmSectionDataExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[4096]), 0, 4096);

        queue.emit(section, values);

        final List<String> emitted = new ArrayList<>();
        section.build().ranges().forEach(r ->
        {
            final StringBuilder attributes = new StringBuilder();
            r.attributes().forEach(a -> attributes.append(a.name().asString()).append('=').append(a.value().asString()));
            emitted.add(r.length() + ":" + (r.closing() != 0 ? "closing" : "open") +
                (attributes.length() > 0 ? ":" + attributes : ""));
        });

        return emitted;
    }

    private static Array32FW<LlmRangeFW> ranges(
        String... specs)
    {
        final LlmSectionDataExFW.Builder section = new LlmSectionDataExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[4096]), 0, 4096);

        for (String spec : specs)
        {
            final String[] parts = spec.split("\\|");
            section.rangesItem(r ->
            {
                r.length(Integer.parseInt(parts[0])).closing(Integer.parseInt(parts[1]));
                if (parts.length > 2)
                {
                    r.attributesItem(a -> a.name(parts[2].split("=")[0]).value(parts[2].split("=")[1]));
                }
            });
        }

        return section.build().ranges();
    }

    private static String range(
        int length,
        boolean closing,
        String... attribute)
    {
        return length + "|" + (closing ? 1 : 0) + (attribute.length > 0 ? "|" + attribute[0] : "");
    }
}
