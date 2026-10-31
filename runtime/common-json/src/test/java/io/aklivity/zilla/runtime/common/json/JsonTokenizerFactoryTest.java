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
package io.aklivity.zilla.runtime.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

public class JsonTokenizerFactoryTest
{
    @Test
    public void shouldSelectHighestPriorityAvailableCandidate()
    {
        final JsonTokenizerFactory low = candidate(0, true);
        final JsonTokenizerFactory high = candidate(100, true);

        final JsonTokenizerFactory selected = JsonTokenizerFactory.instantiate(List.of(low, high));

        assertSame(high, selected);
    }

    @Test
    public void shouldSkipUnavailableCandidateEvenWhenHigherPriority()
    {
        final JsonTokenizerFactory low = candidate(0, true);
        final JsonTokenizerFactory unavailableHigh = candidate(100, false);

        final JsonTokenizerFactory selected = JsonTokenizerFactory.instantiate(List.of(low, unavailableHigh));

        assertSame(low, selected);
    }

    @Test
    public void shouldThrowWhenNoCandidateAvailable()
    {
        final JsonTokenizerFactory unavailable = candidate(0, false);

        assertThrows(IllegalStateException.class, () -> JsonTokenizerFactory.instantiate(List.of(unavailable)));
    }

    @Test
    public void shouldResolveTheRegisteredBaselineFactory()
    {
        final JsonTokenizerFactory factory = JsonTokenizerFactory.instantiate();

        assertEquals(0, factory.priority());
    }

    private static JsonTokenizerFactory candidate(
        int priority,
        boolean available)
    {
        return new JsonTokenizerFactory()
        {
            @Override
            public JsonTokenizer create(
                int maxValueSize,
                boolean quoted)
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
        };
    }
}
