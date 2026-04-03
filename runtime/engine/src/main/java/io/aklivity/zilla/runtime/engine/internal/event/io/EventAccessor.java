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
package io.aklivity.zilla.runtime.engine.internal.event.io;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.event.io.EventWriter.EventWriterEntry;
import io.aklivity.zilla.runtime.engine.internal.spy.RingBufferSpy;

final class EventAccessor
{
    private EventAccessorEntry current;

    EventAccessor(
        EventWriterEntry entry)
    {
        this.current = new EventAccessorEntry(entry);
    }

    public int readEvent(
        MessageConsumer handler,
        int messageCountLimit)
    {
        int messageCount = current.spy(handler, messageCountLimit);
        if (messageCount == 0)
        {
            EventAccessorEntry next = current.next();
            if (next != null)
            {
                current.close();
                current = next;
                messageCount = current.spy(handler, messageCountLimit);
            }
        }
        return messageCount;
    }

    public int peekEvent(
        MessageConsumer handler)
    {
        int result = current.peek(handler);
        if (result == 0)
        {
            EventAccessorEntry next = current.next();
            if (next != null)
            {
                result = next.peek(handler);
            }
        }
        return result;
    }

    void close()
    {
        for (EventAccessorEntry entry = current; entry != null; entry = entry.next())
        {
            entry.close();
        }
    }

    private final class EventAccessorEntry
    {
        private final EventWriterEntry entry;
        private final RingBufferSpy spy;
        private EventAccessorEntry nextEntry;

        private EventAccessorEntry(
            EventWriterEntry entry)
        {
            this.entry = entry;
            this.spy = entry.createSpy();
            entry.increment();
        }

        EventAccessorEntry next()
        {
            if (nextEntry == null && entry.next != null)
            {
                nextEntry = new EventAccessorEntry(entry.next);
            }
            return nextEntry;
        }

        int spy(
            MessageConsumer handler,
            int messageCountLimit)
        {
            return spy.spy(handler, messageCountLimit);
        }

        int peek(
            MessageConsumer handler)
        {
            return spy.peek(handler);
        }

        void close()
        {
            spy.close();
            entry.decrement();
        }
    }
}
