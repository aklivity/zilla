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
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class LlmDialectSelectorTest
{
    @Test
    public void shouldSelectOnlyDetectingDialect()
    {
        final LlmDialectHandler matching = new Detecting(true, 1);
        final LlmDialectHandler other = new Detecting(false, 0);

        assertThat(LlmDialectSelector.select(List.of(other, matching), null), sameInstance(matching));
    }

    @Test
    public void shouldSelectNoneWhenNoDialectDetects()
    {
        assertThat(LlmDialectSelector.select(List.of(new Detecting(false, 0), new Detecting(false, 0)), null), nullValue());
    }

    @Test
    public void shouldSelectHighestScoringDialect()
    {
        final LlmDialectHandler generic = new Detecting(true, 1);
        final LlmDialectHandler specific = new Detecting(true, 2);

        assertThat(LlmDialectSelector.select(List.of(generic, specific), null), sameInstance(specific));
        assertThat(LlmDialectSelector.select(List.of(specific, generic), null), sameInstance(specific));
    }

    @Test
    public void shouldScoreDetectingDialectOneByDefault()
    {
        final LlmDialectHandler generic = new Defaulted(true);
        final LlmDialectHandler specific = new Detecting(true, 2);

        assertThat(LlmDialectSelector.select(List.of(generic, specific), null), sameInstance(specific));
        assertThat(LlmDialectSelector.select(List.of(generic, new Defaulted(false)), null), sameInstance(generic));
    }

    @Test
    public void shouldSelectNoneWhenHighestScoresTie()
    {
        final LlmDialectHandler first = new Detecting(true, 2);
        final LlmDialectHandler second = new Detecting(true, 2);
        final LlmDialectHandler generic = new Detecting(true, 1);

        assertThat(LlmDialectSelector.select(List.of(first, second, generic), null), nullValue());
    }

    private static class Defaulted implements LlmDialectHandler
    {
        private final boolean detects;

        Defaulted(
            boolean detects)
        {
            this.detects = detects;
        }

        @Override
        public boolean detect(
            JsonEnvelope headers)
        {
            return detects;
        }

        @Override
        public MessageConsumer newStream(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length,
            MessageConsumer sender)
        {
            return null;
        }
    }

    private static final class Detecting extends Defaulted
    {
        private final int score;

        Detecting(
            boolean detects,
            int score)
        {
            super(detects);
            this.score = score;
        }

        @Override
        public int score(
            JsonEnvelope headers)
        {
            return score;
        }
    }
}
