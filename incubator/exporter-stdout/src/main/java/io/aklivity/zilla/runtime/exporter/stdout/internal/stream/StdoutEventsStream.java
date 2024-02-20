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
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;

public class StdoutEventsStream
{
    private final MessageReader readEvent;
    private final ToIntFunction<String> lookupTypeId;
    private final Int2ObjectHashMap<MessageConsumer> eventHandlers;

    public StdoutEventsStream(
        MessageReader readEvent,
        LongFunction<String> supplyNamespace,
        LongFunction<String> supplyLocalName,
        ToIntFunction<String> lookupTypeId,
        PrintStream out)
    {
        this.readEvent = readEvent;
        this.lookupTypeId = lookupTypeId;

        final HttpEventHandler httpEventHandler = new HttpEventHandler(supplyNamespace, supplyLocalName, out);
        final KafkaEventHandler kafkaEventHandler = new KafkaEventHandler(supplyNamespace, supplyLocalName, out);
        final MqttEventHandler mqttEventHandler = new MqttEventHandler(supplyNamespace, supplyLocalName, out);
        final SchemaRegistryEventHandler schemaRegistryEventHandler =
            new SchemaRegistryEventHandler(supplyNamespace, supplyLocalName, out);
        final TcpEventHandler tcpEventHandler = new TcpEventHandler(supplyNamespace, supplyLocalName, out);
        final TlsEventHandler tlsEventHandler = new TlsEventHandler(supplyNamespace, supplyLocalName, out);
        final Int2ObjectHashMap<MessageConsumer> eventHandlers = new Int2ObjectHashMap<>();
        addEventHandler(eventHandlers, "http", httpEventHandler::handleEvent);
        addEventHandler(eventHandlers, "kafka", kafkaEventHandler::handleEvent);
        addEventHandler(eventHandlers, "mqtt", mqttEventHandler::handleEvent);
        addEventHandler(eventHandlers, "schema-registry", schemaRegistryEventHandler::handleEvent);
        addEventHandler(eventHandlers, "tcp", tcpEventHandler::handleEvent);
        addEventHandler(eventHandlers, "tls", tlsEventHandler::handleEvent);
        this.eventHandlers = eventHandlers;
    }

    public int process()
    {
        return readEvent.read(this::handleEvent, 1);
    }

    private void addEventHandler(
        Int2ObjectHashMap<MessageConsumer> eventHandlers,
        String type,
        MessageConsumer consumer)
    {
        int labelId = lookupTypeId.applyAsInt(type);
        if (labelId != 0)
        {
            eventHandlers.put(labelId, consumer);
        }
    }

    private void handleEvent(
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
    }
}
