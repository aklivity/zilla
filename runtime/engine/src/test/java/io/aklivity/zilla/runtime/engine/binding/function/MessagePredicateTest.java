/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.binding.function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MessagePredicateTest
{
    @Test
    public void shouldDefaultTrueAndTrue()
    {
        MessagePredicate trueFirst = (t, b, i, l) -> true;
        MessagePredicate trueSecond = (t, b, i, l) -> true;

        assertTrue(trueFirst.and(trueSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultTrueAndFalse()
    {
        MessagePredicate trueFirst = (t, b, i, l) -> true;
        MessagePredicate falseSecond = (t, b, i, l) -> false;

        assertFalse(trueFirst.and(falseSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultFalseAndTrue()
    {
        MessagePredicate falseFirst = (t, b, i, l) -> false;
        MessagePredicate trueSecond = (t, b, i, l) -> true;

        assertFalse(falseFirst.and(trueSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultFalseAndFalse()
    {
        MessagePredicate falseFirst = (t, b, i, l) -> false;
        MessagePredicate falseSecond = (t, b, i, l) -> false;

        assertFalse(falseFirst.and(falseSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultTrueORTrue()
    {
        MessagePredicate trueFirst = (t, b, i, l) -> true;
        MessagePredicate trueSecond = (t, b, i, l) -> true;

        assertTrue(trueFirst.or(trueSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultTrueOrFalse()
    {
        MessagePredicate trueFirst = (t, b, i, l) -> true;
        MessagePredicate falseSecond = (t, b, i, l) -> false;

        assertTrue(trueFirst.or(falseSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultFalseOrTrue()
    {
        MessagePredicate falseFirst = (t, b, i, l) -> false;
        MessagePredicate trueSecond = (t, b, i, l) -> true;

        assertTrue(falseFirst.or(trueSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultFalseOrFalse()
    {
        MessagePredicate falseFirst = (t, b, i, l) -> false;
        MessagePredicate falseSecond = (t, b, i, l) -> false;

        assertFalse(falseFirst.or(falseSecond).test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultNotTrue()
    {
        MessagePredicate trueFirst = (t, b, i, l) -> true;

        assertFalse(trueFirst.negate().test(0, null, 0, 0));
    }

    @Test
    public void shouldDefaultNotFalse()
    {
        MessagePredicate falseFirst = (t, b, i, l) -> false;

        assertTrue(falseFirst.negate().test(0, null, 0, 0));
    }
}
