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
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;
import io.aklivity.zilla.runtime.exporter.stdout.internal.StdoutExporterContext;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;

public class StdoutEventsStream
{
    private static final String FORMAT = "%s [%s] %s%n"; // qname [timestamp] extension\n
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private final StdoutExporterContext context;
    private final MessageReader readEvent;
    private final Int2ObjectHashMap<EventFormatterSpi> formatters;
    private final Int2ObjectHashMap<MessageConsumer> eventHandlers;  // TODO: Ati - rm this
    private final EventFW eventRO = new EventFW();
    private final PrintStream out;

    public StdoutEventsStream(
        StdoutExporterContext context,
        Map<String, EventFormatterSpi> formatters,
        PrintStream out)
    {
        this.context = context;
        this.readEvent = context.supplyEventReader();

        Int2ObjectHashMap<EventFormatterSpi> f = new Int2ObjectHashMap<>();
        formatters.forEach((k, v) -> f.put(context.supplyTypeId(k), v));
        this.formatters = f;

        final Int2ObjectHashMap<MessageConsumer> eventHandlers = new Int2ObjectHashMap<>();
        eventHandlers.put(context.supplyTypeId("schema-registry"),
            new StdoutSchemaRegistryHandler(context, out)::handleEvent);
        this.eventHandlers = eventHandlers;
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
        if (formatters.containsKey(msgTypeId))
        {
            final EventFW event = eventRO.wrap(buffer, index, index + length);
            String qname = context.supplyQName(event.namespacedId());
            String extension = formatters.get(msgTypeId).formatEventEx(msgTypeId, buffer, index, length);
            out.format(FORMAT, qname, asDateTime(event.timestamp()), extension);
        }
        else // TODO: Ati
        {
            final MessageConsumer handler = eventHandlers.get(msgTypeId);
            if (handler != null)
            {
                handler.accept(msgTypeId, buffer, index, length);
            }
        }
    }

    private static String asDateTime(
        long timestamp)
    {
        Instant instant = Instant.ofEpochMilli(timestamp);
        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        return offsetDateTime.format(FORMATTER);
    }
}
