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

import java.util.Objects;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Tests a frame from a {@link DirectBuffer} slice, returning {@code true} or {@code false}.
 * <p>
 * Used with {@link MessageConsumer#filter(MessagePredicate)} to conditionally gate frame
 * delivery, and in binding logic to test frame characteristics (e.g., message type, header
 * values) without decoding into heap objects.
 * </p>
 * <p>
 * Supports standard boolean composition via {@link #and}, {@link #or}, and {@link #negate}.
 * </p>
 *
 * @see MessageConsumer#filter(MessagePredicate)
 */
@FunctionalInterface
public interface MessagePredicate
{
    /**
     * Tests the frame at {@code buffer[index..index+length)} with the given type id.
     *
     * @param msgTypeId  the frame type identifier
     * @param buffer     the buffer containing the frame
     * @param index      the offset of the frame in the buffer
     * @param length     the length of the frame
     * @return {@code true} if the frame passes this predicate's test
     */
    boolean test(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length);

    /**
     * Returns a predicate that is the logical AND of this predicate and {@code other}.
     * Short-circuits: {@code other} is not evaluated if this predicate returns {@code false}.
     *
     * @param other  the predicate to AND with
     * @return a composed AND predicate
     */
    default MessagePredicate and(
        MessagePredicate other)
    {
        Objects.requireNonNull(other);
        return (t, b, i, l) -> test(t, b, i, l) && other.test(t, b, i, l);
    }

    /**
     * Returns a predicate that is the logical negation of this predicate.
     *
     * @return a negated predicate
     */
    default MessagePredicate negate()
    {
        return (t, b, i, l) -> !test(t, b, i, l);
    }

    /**
     * Returns a predicate that is the logical OR of this predicate and {@code other}.
     * Short-circuits: {@code other} is not evaluated if this predicate returns {@code true}.
     *
     * @param other  the predicate to OR with
     * @return a composed OR predicate
     */
    default MessagePredicate or(
        MessagePredicate other)
    {
        Objects.requireNonNull(other);
        return (t, b, i, l) -> test(t, b, i, l) || other.test(t, b, i, l);
    }
}
