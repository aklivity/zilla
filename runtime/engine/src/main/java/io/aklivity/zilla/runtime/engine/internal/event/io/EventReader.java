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

import java.util.List;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;

public final class EventReader implements MessageReader
{
    private final EventFW eventRO = new EventFW();

    private final EventAccessor[] accessors;

    private int minAccessorIndex;
    private long minTimeStamp;

    public EventReader(List<EventWriter> writers)
    {
        accessors = writers.stream()
            .map(EventWriter::createEventAccessor)
            .toArray(EventAccessor[]::new);
    }

    @Override
    public int read(
        MessageConsumer handler,
        int messageCountLimit)
    {
        int messagesRead = 0;
        boolean empty = false;
        while (!empty && messagesRead < messageCountLimit)
        {
            int eventCount = 0;
            minAccessorIndex = 0;
            minTimeStamp = Long.MAX_VALUE;
            for (int j = 0; j < accessors.length; j++)
            {
                final int accessorIndex = j;
                int eventPeeked = accessors[accessorIndex].peekEvent((m, b, i, l) ->
                {
                    eventRO.wrap(b, i, i + l);
                    if (eventRO.timestamp() < minTimeStamp)
                    {
                        minTimeStamp = eventRO.timestamp();
                        minAccessorIndex = accessorIndex;
                    }
                });
                eventCount += eventPeeked;
            }
            empty = eventCount == 0;
            if (!empty)
            {
                messagesRead += accessors[minAccessorIndex].readEvent(handler, 1);
            }
        }
        return messagesRead;
    }
}
