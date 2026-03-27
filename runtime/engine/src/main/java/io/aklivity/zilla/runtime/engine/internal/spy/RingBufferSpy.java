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
package io.aklivity.zilla.runtime.engine.internal.spy;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

/**
 * A read-only spy on an Agrona ring buffer, used internally by the engine to drain
 * event and stream frame buffers without consuming them.
 * <p>
 * Unlike a standard ring buffer consumer, a {@code RingBufferSpy} observes messages
 * without advancing the buffer's consumer position, allowing multiple independent
 * readers to observe the same frames (e.g., multiple exporters draining the same
 * event ring buffer). The spy's position within the buffer is controlled via
 * {@link #spyAt(SpyPosition)}.
 * </p>
 * <p>
 * This is an internal engine interface; bindings and plugins should not implement or
 * depend on it directly.
 * </p>
 *
 * @see io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer
 */
public interface RingBufferSpy
{
    /**
     * The starting position within the ring buffer from which this spy begins reading.
     */
    enum SpyPosition
    {
        /** Start from offset zero, regardless of the current producer or consumer position. */
        ZERO,
        /** Start from the current consumer (head) position — reads only new messages. */
        HEAD,
        /** Start from the current producer (tail) position — skips all existing messages. */
        TAIL
    }

    /**
     * Positions this spy at the given {@link SpyPosition} within the ring buffer.
     * Must be called before the first {@link #spy} or {@link #peek} call.
     *
     * @param position  the starting position
     */
    void spyAt(SpyPosition position);

    /**
     * Reads and dispatches all available messages to {@code handler}, advancing the spy
     * position past each message read.
     *
     * @param handler  the consumer to receive each message
     * @return the number of messages read
     */
    int spy(MessageConsumer handler);

    /**
     * Reads and dispatches up to {@code messageCountLimit} messages to {@code handler},
     * advancing the spy position past each message read.
     *
     * @param handler            the consumer to receive each message
     * @param messageCountLimit  the maximum number of messages to read
     * @return the number of messages read
     */
    int spy(MessageConsumer handler, int messageCountLimit);

    /**
     * Reads and dispatches the next available message to {@code handler} without
     * advancing the spy position, allowing the same message to be re-read.
     *
     * @param handler  the consumer to receive the next message
     * @return the number of messages read (0 or 1)
     */
    int peek(MessageConsumer handler);

    /**
     * Returns the current producer position in the ring buffer — the offset at which
     * the next write will occur.
     *
     * @return the producer position
     */
    long producerPosition();

    /**
     * Returns the current consumer position tracked by this spy — the offset of the
     * next unread message from this spy's perspective.
     *
     * @return the spy's consumer position
     */
    long consumerPosition();

    /**
     * Returns the underlying {@link DirectBuffer} backing this ring buffer spy,
     * for direct low-level inspection.
     *
     * @return the backing buffer
     */
    DirectBuffer buffer();
}
