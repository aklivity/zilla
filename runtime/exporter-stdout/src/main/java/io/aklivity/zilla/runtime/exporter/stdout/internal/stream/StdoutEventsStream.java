/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.stdout.internal.stream;

import java.io.PrintStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.exporter.stdout.internal.StdoutExporterContext;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;

public class StdoutEventsStream
{
    // {zilla namespace}:{component name} [dd/MMM/yyyy:HH:mm:ss Z] {event name} - {event body}\n
    private static final String FORMAT = "%s [%s] %s - %s%n";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private final StdoutExporterContext context;
    private final MessageReader readEvent;
    private final EventFormatter formatter;
    private final EventFW eventRO = new EventFW();
    private final PrintStream out;

    public StdoutEventsStream(
        StdoutExporterContext context,
        PrintStream out)
    {
        this.context = context;
        this.readEvent = context.supplyEventReader();
        this.formatter = context.supplyEventFormatter();
        this.out = out;
    }

    public int process()
    {
        return readEvent.read(this::handleEvent, 1);
    }

    private void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        String qname = context.supplyQName(event.namespacedId());
        String eventName = context.supplyEventName(event.id());
        String extension = formatter.format(msgTypeId, buffer, index, length);
        out.format(FORMAT, qname, asDateTime(event.timestamp()), eventName, extension);
    }

    private static String asDateTime(
        long timestamp)
    {
        Instant instant = Instant.ofEpochMilli(timestamp);
        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        return offsetDateTime.format(FORMATTER);
    }
}
