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
import java.util.function.BiConsumer;

/**
 * A consumer that accepts a primitive {@code long} and an object argument.
 * <p>
 * Extends {@link BiConsumer}{@code <Long, T>} with an unboxed primitive overload to avoid
 * autoboxing on the hot path. The boxed {@link #accept(Long, Object)} default delegates to the
 * primitive overload. Supports sequential composition via {@link #andThen}.
 * </p>
 *
 * @param <T>  the type of the object argument
 */
@FunctionalInterface
public interface LongObjectBiConsumer<T> extends BiConsumer<Long, T>
{
    /**
     * Boxed bridge method; delegates to {@link #accept(long, Object)}.
     */
    @Override
    default void accept(Long value, T t)
    {
        this.accept(value.longValue(), t);
    }

    /**
     * Returns a composed consumer that performs this operation followed by {@code after}.
     *
     * @param after  the consumer to invoke after this one
     * @return a composed consumer
     */
    default LongObjectBiConsumer<T> andThen(
        LongObjectBiConsumer<? super T> after)
    {
        Objects.requireNonNull(after);

        return (l, r) ->
        {
            accept(l, r);
            after.accept(l, r);
        };
    }

    /**
     * Performs this operation on the given arguments.
     *
     * @param l  the {@code long} argument
     * @param t  the object argument
     */
    void accept(long l, T t);
}
