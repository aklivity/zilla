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
package io.aklivity.zilla.runtime.engine.util.function;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A consumer that accepts an object argument and a primitive {@code int}.
 * <p>
 * Extends {@link BiConsumer}{@code <T, Integer>} with an unboxed primitive overload to avoid
 * autoboxing on the hot path. The boxed {@link #accept(Object, Integer)} default delegates to
 * the primitive overload. Supports sequential composition via {@link #andThen}.
 * </p>
 *
 * @param <T>  the type of the object argument
 */
@FunctionalInterface
public interface ObjectIntBiConsumer<T> extends BiConsumer<T, Integer>
{
    /**
     * Boxed bridge method; delegates to {@link #accept(Object, int)}.
     */
    @Override
    default void accept(T t, Integer value)
    {
        this.accept(t, value.intValue());
    }

    /**
     * Returns a composed consumer that performs this operation followed by {@code after}.
     *
     * @param after  the consumer to invoke after this one
     * @return a composed consumer
     */
    default ObjectIntBiConsumer<T> andThen(
        ObjectIntBiConsumer<? super T> after)
    {
        Objects.requireNonNull(after);

        return (t, i) ->
        {
            accept(t, i);
            after.accept(t, i);
        };
    }

    /**
     * Performs this operation on the given arguments.
     *
     * @param t  the object argument
     * @param i  the {@code int} argument
     */
    void accept(T t, int i);
}
