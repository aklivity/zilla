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

/**
 * A function that accepts an object, a primitive {@code long}, and two primitive {@code int} arguments
 * and produces a result, avoiding boxing overhead for the numeric arguments.
 *
 * @param <T>  the type of the object argument
 * @param <R>  the result type
 */
@FunctionalInterface
public interface ObjectLongIntIntFunction<T, R>
{
    /**
     * Applies this function to the given arguments.
     *
     * @param object  the object argument
     * @param value1  the {@code long} argument
     * @param value2  the first {@code int} argument
     * @param value3  the second {@code int} argument
     * @return the result
     */
    R apply(T object, long value1, int value2, int value3);
}
