/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.command.logs.internal.printer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.command.logs.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;

public final class LogsReader
{
    private final MessageReader events;
    private final MessageFormatter formatter;
    private final LongFunction<String> supplyQName;
    private final LongFunction<String> supplyEventName;
    private final EventFW eventRO = new EventFW();

    public LogsReader(
        MessageReader events,
        MessageFormatter formatter,
        LongFunction<String> supplyQName,
        LongFunction<String> supplyEventName)
    {
        this.events = events;
        this.formatter = formatter;
        this.supplyQName = supplyQName;
        this.supplyEventName = supplyEventName;
    }

    public List<LogRecord> read(
        int limit)
    {
        List<LogRecord> records = new ArrayList<>();
        events.read((msgTypeId, buffer, index, length) ->
        {
            EventFW event = eventRO.wrap(buffer, index, index + length);
            String qualifiedName = supplyQName.apply(event.namespacedId());
            String eventName = supplyEventName.apply(event.id());
            String message = formatter.format(msgTypeId, buffer, index, length);
            records.add(new LogRecord(event.timestamp(), event.traceId(), qualifiedName, eventName, message));
        }, limit);
        return records;
    }
}
