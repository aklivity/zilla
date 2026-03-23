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
package io.aklivity.zilla.runtime.engine.poller;

import java.nio.channels.SelectableChannel;
import java.util.function.ToIntFunction;

/**
 * Represents a registered {@link SelectableChannel} in the engine's I/O poller.
 * <p>
 * Obtained via {@link EngineContext#supplyPollerKey(SelectableChannel)}, a {@code PollerKey}
 * wraps an NIO channel and allows bindings to register for readiness events (readable, writable,
 * connectable, acceptable) and attach a per-operation handler that is called when the channel
 * becomes ready.
 * </p>
 * <p>
 * All methods must be called from the owning I/O thread.
 * </p>
 *
 * @see EngineContext#supplyPollerKey(SelectableChannel)
 */
public interface PollerKey
{
    /**
     * Attaches an arbitrary object to this key, replacing any previously attached object.
     * <p>
     * The attachment is not interpreted by the engine; it is available to handlers via
     * {@link #attachment()}.
     * </p>
     *
     * @param attachment  the object to attach, or {@code null} to clear
     * @return the previously attached object, or {@code null}
     */
    Object attach(
        Object attachment);

    /**
     * Returns the object most recently attached via {@link #attach}, or {@code null}.
     *
     * @return the current attachment
     */
    Object attachment();

    /**
     * Returns the {@link SelectableChannel} this key was registered for.
     *
     * @return the underlying NIO channel
     */
    SelectableChannel channel();

    /**
     * Returns {@code true} if this key is still valid (i.e., not yet cancelled and the
     * channel has not been closed).
     *
     * @return {@code true} if valid
     */
    boolean isValid();

    /**
     * Adds the given NIO interest operations (e.g., {@link java.nio.channels.SelectionKey#OP_READ})
     * to the set currently registered with the poller.
     *
     * @param registerOps  the bitwise OR of {@link java.nio.channels.SelectionKey} operation constants
     */
    void register(
        int registerOps);

    /**
     * Cancels this key, deregistering the channel from the poller and invalidating the key.
     * After cancellation, further calls to this key's methods have no effect.
     */
    void cancel();

    /**
     * Removes the given NIO operations from the set currently registered with the poller.
     *
     * @param clearOps  the bitwise OR of operation constants to remove
     */
    void clear(
        int clearOps);

    /**
     * Registers a handler to be invoked when any of the given NIO operations become ready
     * on the channel.
     * <p>
     * The handler receives this {@code PollerKey} as its argument and should return the
     * number of work units performed (positive to request rescheduling, zero otherwise).
     * </p>
     *
     * @param handlerOps  the bitwise OR of operation constants this handler covers
     * @param handler     the function to invoke when any of {@code handlerOps} are ready
     */
    void handler(
        int handlerOps,
        ToIntFunction<PollerKey> handler);
}
