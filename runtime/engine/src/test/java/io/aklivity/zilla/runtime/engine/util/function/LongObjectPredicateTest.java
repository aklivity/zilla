/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.util.function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.junit.Test;

public class LongObjectPredicateTest
{
    @Test
    public void shouldValidateWhenBothPredicatesMatch()
    {
        final UnaryOperator<String> resolver = UnaryOperator.identity();

        LongObjectPredicate<UnaryOperator<String>> predicate1 = (value, fn) ->
            fn.apply("Hello").equals("Hello");

        LongObjectPredicate<UnaryOperator<String>> predicate2 = (value, fn) ->
            fn.apply("World").equals("World");

        boolean result = predicate1.and(predicate2).test(1L, resolver);
        assertTrue(result);
    }

    @Test
    public void shouldValidateWhenPredicatesFails()
    {
        final UnaryOperator<String> resolver = UnaryOperator.identity();

        LongObjectPredicate<UnaryOperator<String>> predicate1 = (value, fn) ->
            fn.apply("Hello").equals("Hello");

        LongObjectPredicate<UnaryOperator<String>> predicate2 = (value, fn) ->
            fn.apply("World").equals("Zilla");

        boolean result = predicate1.and(predicate2).test(1L, resolver);
        assertFalse(result);
    }

    @Test
    public void shouldValidatePredicatesWithTransformer()
    {
        Function<String, String> function = key -> switch (key)
        {
        case "key" -> "Zilla";
        case "anotherKey" -> "Value";
        default -> "UNKNOWN";
        };

        final UnaryOperator<String> resolver = input ->
        {
            String result = input;
            boolean replaced = false;

            if (result.contains("${key}"))
            {
                result = result.replace("${key}", function.apply("key"));
                replaced = true;
            }

            return replaced ? result : function.apply(input);
        };


        LongObjectPredicate<UnaryOperator<String>> predicate1 = (value, fn) ->
            fn.apply("Hello").equals("Hello");

        LongObjectPredicate<UnaryOperator<String>> predicate2 = (value, fn) ->
            fn.apply("key").equals("Zilla");

        boolean result = predicate1.and(predicate2).test(1L, resolver);
        assertFalse(result);
    }
}
