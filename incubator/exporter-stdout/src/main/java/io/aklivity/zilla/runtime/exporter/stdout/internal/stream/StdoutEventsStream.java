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
import java.util.function.Function;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.exporter.stdout.internal.layouts.EventsLayoutReader;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.StringFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpRemoteAccessFailedEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventFW;

public class StdoutEventsStream
{
    private static final String TCP_REMOTE_ACCESS_FAILED_FORMAT =
        "ERROR: Remote Access Failed [timestamp = %d] [traceId = 0x%016x] [binding = %s.%s] [address = %s]%n";

    private final HttpEventFW httpEventRO = new HttpEventFW();
    private final KafkaEventFW kafkaEventRO = new KafkaEventFW();
    private final MqttEventFW mqttEventRO = new MqttEventFW();
    private final SchemaRegistryEventFW schemaRegistryEventRO = new SchemaRegistryEventFW();
    private final TcpEventFW tcpEventRO = new TcpEventFW();
    private final TlsEventFW tlsEventRO = new TlsEventFW();

    private final EventsLayoutReader layout;
    private final LongFunction<String> supplyNamespace;
    private final LongFunction<String> supplyLocalName;
    private final Function<String, Integer> lookupLabelId;
    private final PrintStream out;
    private final Int2ObjectHashMap<MessageConsumer> eventHandlers;

    public StdoutEventsStream(
        EventsLayoutReader layout,
        LongFunction<String> supplyNamespace,
        LongFunction<String> supplyLocalName,
        Function<String, Integer> lookupLabelId,
        PrintStream out)
    {
        this.layout = layout;
        this.supplyNamespace = supplyNamespace;
        this.supplyLocalName = supplyLocalName;
        this.lookupLabelId = lookupLabelId;
        this.out = out;

        final Int2ObjectHashMap<MessageConsumer> eventHandlers = new Int2ObjectHashMap<>();
        addEventHandler(eventHandlers, "http", this::handleHttpEvent);
        addEventHandler(eventHandlers, "kafka", this::handleKafkaEvent);
        addEventHandler(eventHandlers, "mqtt", this::handleMqttEvent);
        addEventHandler(eventHandlers, "schema-registry", this::handleSchemaRegistryEvent);
        addEventHandler(eventHandlers, "tcp", this::handleTcpEvent);
        addEventHandler(eventHandlers, "tls", this::handleTlsEvent);
        this.eventHandlers = eventHandlers;
    }

    private void addEventHandler(
        Int2ObjectHashMap<MessageConsumer> eventHandlers,
        String type,
        MessageConsumer consumer)
    {
        int labelId = lookupLabelId.apply(type);
        if (labelId != 0)
        {
            eventHandlers.put(labelId, consumer);
        }
    }

    public int process()
    {
        return layout.eventsBuffer().spy(this::handleEvent, 1);
    }

    private boolean handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final MessageConsumer handler = eventHandlers.get(msgTypeId);
        if (handler != null)
        {
            handler.accept(msgTypeId, buffer, index, length);
        }
        return true;
    }

    private void handleHttpEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        // TODO: Ati
    }

    private void handleKafkaEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        // TODO: Ati
    }

    private void handleMqttEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        // TODO: Ati
    }

    private void handleSchemaRegistryEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        // TODO: Ati
    }

    private void handleTcpEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final TcpEventFW event = tcpEventRO.wrap(buffer, index, index + length);
        switch (event.kind())
        {
        case REMOTE_ACCESS_FAILED:
            TcpRemoteAccessFailedEventFW e = event.remoteAccessFailed();
            String namespace = supplyNamespace.apply(e.namespacedId());
            String binding = supplyLocalName.apply(e.namespacedId());
            out.printf(TCP_REMOTE_ACCESS_FAILED_FORMAT, e.timestamp(), e.traceId(), namespace, binding, asString(e.address()));
            break;
        }
    }

    private void handleTlsEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        // TODO: Ati
    }

    private static String asString(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
