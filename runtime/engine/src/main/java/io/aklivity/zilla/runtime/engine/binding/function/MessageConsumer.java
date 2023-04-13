/*
 * Copyright 2021-2022 Aklivity Inc.
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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;

@FunctionalInterface
public interface MessageConsumer extends MessageHandler, AutoCloseable
{
    MessageConsumer NOOP = new MessageConsumer()
    {
        @Override
        public void accept(
            int msgTypeId,
            DirectBuffer buffer,
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
    };

    void accept(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length);

    @Override
    default void onMessage(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        accept(msgTypeId, buffer, index, length);
    }

    @Override
    default void close() throws Exception
    {
    }

    default MessageConsumer andThen(
        MessageConsumer after)
    {
        requireNonNull(after);
        return (int msgTypeId, DirectBuffer buffer, int index, int length) ->
        {
            accept(msgTypeId, buffer, index, length);
            after.accept(msgTypeId, buffer, index, length);
        };
    }
}
