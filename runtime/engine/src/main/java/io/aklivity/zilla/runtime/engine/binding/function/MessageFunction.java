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
package io.aklivity.zilla.runtime.engine.binding.function;

import org.agrona.DirectBuffer;

/**
 * Applies a function to a frame from a {@link DirectBuffer} slice and returns a result.
 * <p>
 * The generic counterpart to {@link MessageConsumer} for cases where frame processing
 * must return a value (e.g., decoding a frame header into a config object, or extracting
 * a routing key).
 * </p>
 * <p>
 * Implementations must not retain a reference to {@code buffer} beyond the duration of
 * the {@link #apply} call.
 * </p>
 *
 * @param <R>  the result type
 * @see MessageConsumer
 */
@FunctionalInterface
public interface MessageFunction<R>
{
    /**
     * Applies this function to the frame at {@code buffer[index..index+length)}.
     *
     * @param msgTypeId  the frame type identifier
     * @param buffer     the buffer containing the frame
     * @param index      the offset of the frame in the buffer
     * @param length     the length of the frame
     * @return the function result
     */
    R apply(int msgTypeId, DirectBuffer buffer, int index, int length);
}
