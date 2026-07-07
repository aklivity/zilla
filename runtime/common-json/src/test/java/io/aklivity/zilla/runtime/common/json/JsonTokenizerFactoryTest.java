/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class JsonTokenizerFactoryTest
{
    @Test
    void shouldSelectHighestPriorityAvailableCandidate()
    {
        JsonTokenizerFactory low = new FakeFactory(0, true);
        JsonTokenizerFactory high = new FakeFactory(10, true);

        JsonTokenizerFactory selected = JsonTokenizerFactory.instantiate(List.of(low, high));

        assertSame(high, selected);
    }

    @Test
    void shouldSkipUnavailableCandidateInFavorOfLowerPriorityAvailableOne()
    {
        JsonTokenizerFactory unavailableHigh = new FakeFactory(10, false);
        JsonTokenizerFactory availableLow = new FakeFactory(0, true);

        JsonTokenizerFactory selected = JsonTokenizerFactory.instantiate(List.of(unavailableHigh, availableLow));

        assertSame(availableLow, selected);
    }

    @Test
    void shouldSelectSoleAvailableCandidateRegardlessOfOrder()
    {
        JsonTokenizerFactory only = new FakeFactory(0, true);

        JsonTokenizerFactory selected = JsonTokenizerFactory.instantiate(List.of(only));

        assertSame(only, selected);
    }

    @Test
    void shouldThrowWhenNoCandidateIsAvailable()
    {
        JsonTokenizerFactory unavailable = new FakeFactory(0, false);

        assertThrows(IllegalStateException.class, () -> JsonTokenizerFactory.instantiate(List.of(unavailable)));
    }

    @Test
    void shouldThrowWhenNoCandidatesAtAll()
    {
        assertThrows(IllegalStateException.class, () -> JsonTokenizerFactory.instantiate(List.of()));
    }

    @Test
    void shouldDiscoverBuiltInFactoryViaServiceLoader()
    {
        JsonTokenizerFactory selected = JsonTokenizerFactory.instantiate();

        JsonTokenizer tokenizer = selected.create(false, Integer.MAX_VALUE);

        assertEquals("", tokenizer.currentPath());
    }

    private static final class FakeFactory implements JsonTokenizerFactory
    {
        private final int priority;
        private final boolean available;

        private FakeFactory(
            int priority,
            boolean available)
        {
            this.priority = priority;
            this.available = available;
        }

        @Override
        public JsonTokenizer create(
            boolean terminalEof,
            int maxValueSize)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int priority()
        {
            return priority;
        }

        @Override
        public boolean available()
        {
            return available;
        }
    }
}
