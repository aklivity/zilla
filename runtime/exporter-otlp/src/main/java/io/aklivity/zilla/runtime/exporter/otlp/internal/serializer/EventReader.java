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
package io.aklivity.zilla.runtime.exporter.otlp.internal.serializer;

import java.io.StringReader;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.exporter.otlp.internal.types.event.EventFW;

public class EventReader
{
    private static final int MESSAGE_COUNT_LIMIT = 100;
    private static final String TIME_UNIX_NANO = "timeUnixNano";
    private static final String OBSERVED_TIME_UNIX_NANO = "observedTimeUnixNano";
    private static final String BODY = "body";
    private static final String ATTRIBUTES = "attributes";
    private static final String BODY_FORMAT = "{\"stringValue\": \"%s %s\"}";
    private static final String STRING_ATTRIBUTE_FORMAT = "{\"key\":\"%s\", \"value\":{\"stringValue\": \"%s\"}}";

    private final EngineContext context;
    private final MessageReader readEvent;
    private final EventFormatter formatter;
    private final EventFW eventRO = new EventFW();

    private JsonArrayBuilder eventsJson;
    private JsonObjectBuilder eventJson;
    private JsonArrayBuilder eventAttributesJson;

    public EventReader(
        EngineContext context)
    {
        this.context = context;
        this.readEvent = context.supplyEventReader();
        this.formatter = context.supplyEventFormatter();
    }

    public JsonArray readEvents()
    {
        eventsJson = Json.createArrayBuilder();
        readEvent.read(this::handleEvent, MESSAGE_COUNT_LIMIT);
        return eventsJson.build();
    }

    private void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        eventJson = Json.createObjectBuilder();
        long nanos = TimeUnit.MILLISECONDS.toNanos(event.timestamp());
        eventJson.add(TIME_UNIX_NANO, nanos);
        eventJson.add(OBSERVED_TIME_UNIX_NANO, nanos);
        String qname = context.supplyQName(event.namespacedId());
        String eventName = context.supplyEventName(event.id());
        String extension = formatter.format(msgTypeId, buffer, index, length);
        eventAttributesJson = Json.createArrayBuilder();
        addStringAttribute("event.name", eventName);
        addBody(qname, extension);
        eventJson.add(ATTRIBUTES, eventAttributesJson);
        eventsJson.add(eventJson);
    }

    private void addBody(
        String qname,
        String extension)
    {
        String json = String.format(BODY_FORMAT, qname, extension);
        JsonReader reader = Json.createReader(new StringReader(json));
        eventJson.add(BODY, reader.readObject());
    }

    private void addStringAttribute(
        String key,
        String value)
    {
        String json = String.format(STRING_ATTRIBUTE_FORMAT, key, value);
        JsonReader reader = Json.createReader(new StringReader(json));
        eventAttributesJson.add(reader.readObject());
    }
}
