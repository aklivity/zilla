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
    private final Int2ObjectHashMap<MessageConsumer> eventHandlers;

    public StdoutEventsStream(
        MessageReader readEvent,
        LongFunction<String> supplyQName,
        ToIntFunction<String> supplyTypeId,
        PrintStream out)
    {
        this.readEvent = readEvent;

        final Int2ObjectHashMap<MessageConsumer> eventHandlers = new Int2ObjectHashMap<>();
        eventHandlers.put(supplyTypeId.applyAsInt("http"), new StdoutHttpHandler(supplyQName, out)::handleEvent);
        eventHandlers.put(supplyTypeId.applyAsInt("kafka"), new StdoutKafkaHandler(supplyQName, out)::handleEvent);
        eventHandlers.put(supplyTypeId.applyAsInt("mqtt"), new StdoutMqttHandler(supplyQName, out)::handleEvent);
        eventHandlers.put(supplyTypeId.applyAsInt("schema-registry"),
            new StdoutSchemaRegistryHandler(supplyQName, out)::handleEvent);
        eventHandlers.put(supplyTypeId.applyAsInt("tcp"), new StdoutTcpHandler(supplyQName, out)::handleEvent);
        eventHandlers.put(supplyTypeId.applyAsInt("tls"), new StdoutTlsHandler(supplyQName, out)::handleEvent);
        this.eventHandlers = eventHandlers;
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
        final MessageConsumer handler = eventHandlers.get(msgTypeId);
        if (handler != null)
        {
            handler.accept(msgTypeId, buffer, index, length);
        }
    }
}
