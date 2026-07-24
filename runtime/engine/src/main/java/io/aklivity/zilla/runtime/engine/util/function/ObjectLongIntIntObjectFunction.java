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

/**
 * A function that accepts an object, a primitive {@code long}, two primitive {@code int} arguments,
 * and a second object, and produces a result, avoiding boxing overhead for the numeric arguments.
 *
 * @param <T>  the type of the first object argument
 * @param <U>  the type of the second object argument
 * @param <R>  the result type
 */
@FunctionalInterface
public interface ObjectLongIntIntObjectFunction<T, U, R>
{
    /**
     * Applies this function to the given arguments.
     *
     * @param object1  the first object argument
     * @param value1   the {@code long} argument
     * @param value2   the first {@code int} argument
     * @param value3   the second {@code int} argument
     * @param object2  the second object argument
     * @return the result
     */
    R apply(T object1, long value1, int value2, int value3, U object2);
}
