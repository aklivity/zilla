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
package io.aklivity.zilla.runtime.engine.binding;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

/**
 * Accepts and dispatches new protocol streams for a bound configuration.
 * <p>
 * A {@code BindingHandler} is returned by {@link BindingContext#attach(BindingConfig)} and
 * is confined to a single I/O thread. Each call to {@link #newStream} is an offer from the
 * engine to begin a new stream interaction through this binding. The implementation returns
 * a {@link MessageConsumer} that will receive all subsequent frames on that stream, or
 * {@code null} to reject the stream.
 * </p>
 * <p>
 * All methods are called on the owning I/O thread only; no synchronization is required.
 * </p>
 *
 * @see BindingContext
 * @see MessageConsumer
 */
public interface BindingHandler
{
    /** Type identifier for standard Zilla stream messages. */
    int STREAM_TYPE = 0;

    /** Type identifier for externally sourced stream messages (e.g., from NIO channels). */
    int EXTERNAL_TYPE = -1;

    /**
     * Returns the message type identifier for frames arriving at the origin (inbound) side
     * of this handler. Defaults to {@link #STREAM_TYPE}.
     *
     * @return the origin type id
     */
    default int originTypeId()
    {
        return STREAM_TYPE;
    }

    /**
     * Returns the message type identifier for frames departing at the routed (outbound) side
     * of this handler. Defaults to {@link #STREAM_TYPE}.
     *
     * @return the routed type id
     */
    default int routedTypeId()
    {
        return STREAM_TYPE;
    }

    /**
     * Called by the engine to open a new stream through this binding.
     * <p>
     * The {@code buffer} contains the initial frame (typically a {@code BEGIN} frame) at
     * the given {@code index} and {@code length}. The {@code sender} is the
     * {@link MessageConsumer} to which reply frames should be sent back to the stream
     * originator.
     * </p>
     *
     * @param msgTypeId  the message type identifier of the initial frame
     * @param buffer     the buffer containing the initial frame
     * @param index      the offset of the initial frame in the buffer
     * @param length     the length of the initial frame
     * @param sender     the consumer to send reply frames back to the originator
     * @return a {@link MessageConsumer} that will receive subsequent frames for this stream,
     *         or {@code null} to reject the stream
     */
    MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender);
}
