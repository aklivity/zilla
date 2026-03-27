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

import java.util.function.BiFunction;

/**
 * A function that accepts a primitive {@code long} and an object argument and produces a result.
 * <p>
 * Extends {@link BiFunction}{@code <Long, U, R>} with an unboxed primitive overload to avoid
 * autoboxing on the hot path. The boxed {@link #apply(Long, Object)} default delegates to the
 * primitive overload.
 * </p>
 *
 * @param <U>  the type of the object argument
 * @param <R>  the result type
 */
@FunctionalInterface
public interface LongObjectBiFunction<U, R> extends BiFunction<Long, U, R>
{
    /**
     * Boxed bridge method; delegates to {@link #apply(long, Object)}.
     */
    @Override
    default R apply(Long value, U u)
    {
        return this.apply(value.longValue(), u);
    }

    /**
     * Applies this function to the given arguments.
     *
     * @param l  the {@code long} argument
     * @param u  the object argument
     * @return the result
     */
    R apply(long l, U u);
}
