/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KafkaShareRangesTest
{
    @Test
    public void shouldStartEmpty()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        assertTrue(ranges.isEmpty());
        assertEquals(0, ranges.size());
        assertFalse(ranges.contains(0L));
    }

    @Test
    public void shouldAddSingleRange()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 9L);

        assertEquals(1, ranges.size());
        assertEquals(5L, ranges.first(0));
        assertEquals(9L, ranges.last(0));
        assertTrue(ranges.contains(5L));
        assertTrue(ranges.contains(9L));
        assertFalse(ranges.contains(4L));
        assertFalse(ranges.contains(10L));
    }

    @Test
    public void shouldKeepDisjointRangesOrdered()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(10L, 12L);
        ranges.add(5L, 6L);
        ranges.add(20L, 20L);

        assertEquals(3, ranges.size());
        assertEquals(5L, ranges.first(0));
        assertEquals(6L, ranges.last(0));
        assertEquals(10L, ranges.first(1));
        assertEquals(12L, ranges.last(1));
        assertEquals(20L, ranges.first(2));
        assertEquals(20L, ranges.last(2));
    }

    @Test
    public void shouldMergeAdjacentRanges()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 6L);
        ranges.add(7L, 8L);
        ranges.add(4L, 4L);

        assertEquals(1, ranges.size());
        assertEquals(4L, ranges.first(0));
        assertEquals(8L, ranges.last(0));
    }

    @Test
    public void shouldMergeOverlappingRanges()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 6L);
        ranges.add(10L, 12L);
        ranges.add(6L, 11L);

        assertEquals(1, ranges.size());
        assertEquals(5L, ranges.first(0));
        assertEquals(12L, ranges.last(0));
    }

    @Test
    public void shouldRemoveOnlyOffsetInRange()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 5L);
        ranges.add(7L, 9L);
        ranges.remove(5L);

        assertEquals(1, ranges.size());
        assertEquals(7L, ranges.first(0));
        assertFalse(ranges.contains(5L));
    }

    @Test
    public void shouldRemoveFirstOffsetInRange()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 9L);
        ranges.remove(5L);

        assertEquals(1, ranges.size());
        assertEquals(6L, ranges.first(0));
        assertEquals(9L, ranges.last(0));
    }

    @Test
    public void shouldRemoveLastOffsetInRange()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 9L);
        ranges.remove(9L);

        assertEquals(1, ranges.size());
        assertEquals(5L, ranges.first(0));
        assertEquals(8L, ranges.last(0));
    }

    @Test
    public void shouldSplitRangeOnRemoveInside()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 9L);
        ranges.remove(7L);

        assertEquals(2, ranges.size());
        assertEquals(5L, ranges.first(0));
        assertEquals(6L, ranges.last(0));
        assertEquals(8L, ranges.first(1));
        assertEquals(9L, ranges.last(1));
    }

    @Test
    public void shouldIgnoreRemoveOutsideRanges()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 9L);
        ranges.remove(4L);
        ranges.remove(10L);

        assertEquals(1, ranges.size());
        assertEquals(5L, ranges.first(0));
        assertEquals(9L, ranges.last(0));
    }

    @Test
    public void shouldClear()
    {
        final KafkaShareRanges ranges = new KafkaShareRanges();

        ranges.add(5L, 9L);
        ranges.clear();

        assertTrue(ranges.isEmpty());
    }
}
