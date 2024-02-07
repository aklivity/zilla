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

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.exporter.stdout.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.exporter.stdout.internal.layouts.EventsLayout;
import io.aklivity.zilla.runtime.exporter.stdout.internal.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.HttpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.KafkaEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.MqttEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.SchemaRegistryEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TcpEventFW;
import io.aklivity.zilla.runtime.exporter.stdout.internal.types.event.TlsEventFW;

public class PrintableEventsStream
{
    private final EventFW eventRO = new EventFW();
    private final HttpEventFW httpEventRO = new HttpEventFW();
    private final KafkaEventFW kafkaEventRO = new KafkaEventFW();
    private final MqttEventFW mqttEventRO = new MqttEventFW();
    private final SchemaRegistryEventFW schemaRegistryEventRO = new SchemaRegistryEventFW();
    private final TcpEventFW tcpEventRO = new TcpEventFW();
    private final TlsEventFW tlsEventRO = new TlsEventFW();
    private final Int2ObjectHashMap<MessageConsumer> eventHandlers;
    private final int index;
    private final LabelManager labels;
    private final RingBufferSpy eventsBuffer;

    public PrintableEventsStream(
        int index,
        LabelManager labels,
        EventsLayout layout)
    {
        this.index = index;
        this.labels = labels;
        this.eventsBuffer = layout.eventsBuffer();

        final Int2ObjectHashMap<MessageConsumer> eventHandlers = new Int2ObjectHashMap<>();
        eventHandlers.put(6, (t, b, i, l) -> onTcpEvent(tcpEventRO.wrap(b, i, i + l))); // TODO: Ati

        this.eventHandlers = eventHandlers;
    }

    public int process()
    {
        return eventsBuffer.spy(this::handleFrame, 1);
    }

    @SuppressWarnings("checkstyle:Regexp")
    private boolean handleFrame(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        // TODO: Ati
        System.out.printf("[PrintableEventsStream.handleFrame] %d %s %d %d%n", msgTypeId, buffer, index, length);

        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final long timestamp = event.timestamp();

        // TODO: Ati - chk timestamp ?

        final MessageConsumer handler = eventHandlers.get(msgTypeId);
        if (handler != null)
        {
            handler.accept(msgTypeId, buffer, index, length);
        }

        /*final FrameFW frame = frameRO.wrap(buffer, index, index + length);
        final long timestamp = frame.timestamp();

        if (!nextTimestamp.test(timestamp))
        {
            return false;
        }

        final MessageConsumer handler = frameHandlers.get(msgTypeId);
        if (handler != null)
        {
            handler.accept(msgTypeId, buffer, index, length);
        }*/

        return true;
    }

    private void onTcpEvent(
        final TcpEventFW tcpEvent)
    {
        System.out.printf("hello onTcpEvent %s%n", tcpEvent);
    }
}
