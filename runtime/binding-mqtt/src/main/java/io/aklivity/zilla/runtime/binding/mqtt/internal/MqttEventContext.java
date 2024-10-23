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
package io.aklivity.zilla.runtime.binding.mqtt.internal;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.types.event.MqttEventType.CLIENT_CONNECTED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.event.MqttEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class MqttEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final MqttEventExFW.Builder mqttEventExRW = new MqttEventExFW.Builder();
    private final int mqttTypeId;
    private final int clientConnectedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public MqttEventContext(
        EngineContext context)
    {
        this.mqttTypeId = context.supplyTypeId(MqttBinding.NAME);
        this.clientConnectedEventId = context.supplyEventId("binding.mqtt.client.connected");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void onClientConnected(
        long traceId,
        long bindingId,
        GuardHandler guard,
        long authorization,
        String clientId)
    {
        String identity = guard == null ? null : guard.identity(authorization);
        MqttEventExFW extension = mqttEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .clientConnected(e -> e
                .typeId(CLIENT_CONNECTED.value())
                .identity(identity)
                .clientId(clientId)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(clientConnectedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mqttTypeId, event.buffer(), event.offset(), event.limit());
    }
}
