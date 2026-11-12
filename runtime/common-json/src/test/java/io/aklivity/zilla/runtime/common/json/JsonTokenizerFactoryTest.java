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
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class JsonTokenizerFactoryTest
{
    private static final Function<String, String> NO_PROPERTIES = name -> null;

    @Test
    public void shouldSelectHighestPriorityCandidate()
    {
        final JsonTokenizerFactory low = candidate(property -> 0);
        final JsonTokenizerFactory high = candidate(property -> 100);

        final JsonTokenizerFactory selected = JsonTokenizerFactory.instantiate(List.of(low, high), NO_PROPERTIES);

        assertSame(high, selected);
    }

    @Test
    public void shouldNotSelectCandidateThatAnswersBelowTheBaseline()
    {
        final JsonTokenizerFactory baseline = candidate(property -> 0);
        final JsonTokenizerFactory declined = candidate(property -> -100);

        assertSame(baseline, JsonTokenizerFactory.instantiate(List.of(declined, baseline), NO_PROPERTIES));
        assertSame(baseline, JsonTokenizerFactory.instantiate(List.of(baseline, declined), NO_PROPERTIES));
    }

    @Test
    public void shouldSelectTheFirstOfEqualPriorityCandidates()
    {
        final JsonTokenizerFactory first = candidate(property -> 50);
        final JsonTokenizerFactory second = candidate(property -> 50);

        assertSame(first, JsonTokenizerFactory.instantiate(List.of(first, second), NO_PROPERTIES));
    }

    @Test
    public void shouldLetAPropertyDecideThePriority()
    {
        final JsonTokenizerFactory baseline = candidate(property -> 0);
        final JsonTokenizerFactory optIn = candidate(property -> Integer.parseInt(
            property.apply("test.priority") != null ? property.apply("test.priority") : "-100"));

        assertSame(baseline, JsonTokenizerFactory.instantiate(List.of(baseline, optIn), NO_PROPERTIES));
        assertSame(optIn, JsonTokenizerFactory.instantiate(List.of(baseline, optIn), Map.of("test.priority", "100")::get));
    }

    @Test
    public void shouldThrowWhenThereIsNoCandidate()
    {
        assertThrows(IllegalStateException.class, () -> JsonTokenizerFactory.instantiate(List.of(), NO_PROPERTIES));
    }

    @Test
    public void shouldResolveTheRegisteredBaselineFactory()
    {
        final JsonTokenizerFactory factory = JsonTokenizerFactory.instantiate();

        assertEquals(0, factory.priority(NO_PROPERTIES));
    }

    private static JsonTokenizerFactory candidate(
        Function<Function<String, String>, Integer> priority)
    {
        return new JsonTokenizerFactory()
        {
            @Override
            public JsonTokenizer create(
                boolean quoted)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int priority(
                Function<String, String> property)
            {
                return priority.apply(property);
            }
        };
    }
}
