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

import static java.util.Objects.requireNonNull;

import org.agrona.concurrent.MessageHandler;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * Receives typed frames from a {@link DirectBuffer} slice on the I/O hot path.
 * <p>
 * {@code MessageConsumer} is the fundamental message-passing interface in the Zilla stream
 * pipeline. Every stream interaction — BEGIN, DATA, END, ABORT, WINDOW, RESET, and SIGNAL
 * frames — is delivered to handlers via this interface. A binding's stream handler
 * ({@link BindingHandler#newStream}) returns a {@code MessageConsumer} for each new stream;
 * subsequent frames on that stream are delivered by calling {@link #accept} on the returned
 * consumer.
 * </p>
 * <p>
 * All calls are made on the owning I/O thread. Implementations must not retain a reference
 * to {@code buffer} beyond the duration of the {@link #accept} call.
 * </p>
 * <p>
 * The sentinel {@link #NOOP} implementation discards all frames and is safe to use as a
 * placeholder for streams that require no reply handling.
 * </p>
 *
 * @see BindingHandler
 * @see EngineContext#supplySender(long)
 */
@FunctionalInterface
public interface MessageConsumer extends MessageHandler, AutoCloseable
{
    /**
     * No-op consumer that discards all frames. Its {@link #andThen} and {@link #filter}
     * overrides are optimized to avoid unnecessary wrapping.
     */
    MessageConsumer NOOP = new MessageConsumer()
    {
        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            // no op
        }

        @Override
        public MessageConsumer andThen(
            MessageConsumer after)
        {
            return requireNonNull(after);
        }

        @Override
        public MessageConsumer filter(
            MessagePredicate condition)
        {
            return NOOP;
        }
    };

    /**
     * Receives a single frame of type {@code msgTypeId} from {@code buffer[index..index+length)}.
     * <p>
     * The buffer slice is only valid for the duration of this call. Implementations that need
     * to retain frame data must copy the relevant bytes before returning.
     * </p>
     *
     * @param msgTypeId  the frame type identifier (e.g., BEGIN, DATA, END, WINDOW, RESET)
     * @param buffer     the buffer containing the frame
     * @param index      the offset of the frame in the buffer
     * @param length     the length of the frame
     */
    void accept(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length);

    /**
     * Bridges to the Agrona {@link MessageHandler} interface by delegating to {@link #accept}.
     */
    @Override
    default void onMessage(
        int msgTypeId,
        MutableDirectBufferEx buffer,
        int index,
        int length)
    {
        accept(msgTypeId, buffer, index, length);
    }

    /**
     * No-op close implementation. Override to release resources when the stream is torn down.
     */
    @Override
    default void close() throws Exception
    {
    }

    /**
     * Returns a composed {@code MessageConsumer} that delivers each frame to this consumer
     * first and then to {@code after}.
     *
     * @param after  the consumer to invoke after this one
     * @return a composed consumer
     */
    default MessageConsumer andThen(
        MessageConsumer after)
    {
        requireNonNull(after);
        return (int msgTypeId, DirectBufferEx buffer, int index, int length) ->
        {
            accept(msgTypeId, buffer, index, length);
            after.accept(msgTypeId, buffer, index, length);
        };
    }

    /**
     * Returns a {@code MessageConsumer} that only forwards frames to this consumer when
     * the given {@code condition} predicate returns {@code true}.
     *
     * @param condition  the predicate to evaluate for each frame
     * @return a filtering consumer
     */
    default MessageConsumer filter(
        MessagePredicate condition)
    {
        requireNonNull(condition);
        return (int msgTypeId, DirectBufferEx buffer, int index, int length) ->
        {
            if (condition.test(msgTypeId, buffer, index, length))
            {
                accept(msgTypeId, buffer, index, length);
            }
        };
    }
}
