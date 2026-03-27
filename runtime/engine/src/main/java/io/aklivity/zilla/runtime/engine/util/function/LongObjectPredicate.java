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

import java.util.Objects;

/**
 * A predicate that accepts a primitive {@code long} and an object argument, avoiding the boxing
 * overhead of {@link java.util.function.BiPredicate BiPredicate&lt;Long, T&gt;}.
 * <p>
 * Used in the guard API to test session authorization: the {@code long} carries the session id
 * and {@code T} typically carries a credential transformer.
 * </p>
 * <p>
 * Supports short-circuit AND composition via {@link #and}.
 * </p>
 *
 * @param <T>  the type of the object argument
 * @see io.aklivity.zilla.runtime.engine.guard.Guard#verifier
 */
@FunctionalInterface
public interface LongObjectPredicate<T>
{
    /**
     * Evaluates this predicate on the given arguments.
     *
     * @param value   the {@code long} argument (typically a session id)
     * @param object  the object argument
     * @return {@code true} if the arguments match the predicate
     */
    boolean test(long value, T object);

    /**
     * Returns a composed predicate that is the logical AND of this predicate and {@code other}.
     * Short-circuits: {@code other} is not evaluated if this predicate returns {@code false}.
     *
     * @param other  the predicate to AND with
     * @return a composed AND predicate
     */
    default LongObjectPredicate<T> and(
        LongObjectPredicate<T> other)
    {
        Objects.requireNonNull(other);
        return (value, object) -> this.test(value, object) && other.test(value, object);
    }
}

