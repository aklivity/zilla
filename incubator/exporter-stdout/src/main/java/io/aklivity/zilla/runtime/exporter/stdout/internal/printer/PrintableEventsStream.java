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
package io.aklivity.zilla.runtime.exporter.stdout.internal.printer;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.exporter.stdout.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.exporter.stdout.internal.layouts.EventsLayout;
import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.StringFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpRemoteAccessFailedEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventFW;

public class PrintableEventsStream
{
    private static final String TCP_REMOTE_ACCESS_FAILED_FORMAT =
        "ERROR: Remote Access Failed [timestamp = %d] [traceId = 0x%016x] [bindingId = 0x%016x] [address = %s]%n";

    private final EventFW eventRO = new EventFW();
    private final HttpEventFW httpEventRO = new HttpEventFW();
    private final KafkaEventFW kafkaEventRO = new KafkaEventFW();
    private final MqttEventFW mqttEventRO = new MqttEventFW();
    private final SchemaRegistryEventFW schemaRegistryEventRO = new SchemaRegistryEventFW();
    private final TcpEventFW tcpEventRO = new TcpEventFW();
    private final TcpRemoteAccessFailedEventFW tcpRemoteAccessFailedEventRO = new TcpRemoteAccessFailedEventFW();
    private final TlsEventFW tlsEventRO = new TlsEventFW();

    private final LabelManager labels;
    private final RingBufferSpy eventsBuffer;
    private final PrintStream out;
    private final Int2ObjectHashMap<MessageConsumer> eventHandlers;

    public PrintableEventsStream(
        LabelManager labels,
        EventsLayout layout,
        PrintStream out)
    {
        this.labels = labels;
        this.eventsBuffer = layout.eventsBuffer();
        this.out = out;

        final Int2ObjectHashMap<MessageConsumer> eventHandlers = new Int2ObjectHashMap<>();
        addEventHandler(labels, eventHandlers, "tcp", this::handleTcpEvent);
        addEventHandler(labels, eventHandlers, "http", this::handleHttpEvent);
        // TODO: Ati - add more
        this.eventHandlers = eventHandlers;
    }

    private void addEventHandler(
        LabelManager labels,
        Int2ObjectHashMap<MessageConsumer> eventHandlers,
        String type,
        MessageConsumer consumer)
    {
        int labelId = labels.lookupLabelId(type);
        if (labelId != 0)
        {
            eventHandlers.put(labelId, consumer);
        }
    }

    public int process()
    {
        return eventsBuffer.spy(this::handleEvent, 1);
    }

    private boolean handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final long timestamp = event.timestamp();
        // TODO: Ati - chk timestamp ?

        final MessageConsumer handler = eventHandlers.get(msgTypeId);
        if (handler != null)
        {
            handler.accept(msgTypeId, buffer, index, length);
        }
        return true;
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
            out.printf(TCP_REMOTE_ACCESS_FAILED_FORMAT, e.timestamp(), e.traceId(), e.bindingId(), asString(e.address()));
            break;
        }
    }

    private void handleHttpEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        // TODO: Ati
    }

    private String asString(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
