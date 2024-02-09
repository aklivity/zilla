/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.event.MqttEventFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.event.Result;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class MqttEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final MqttEventFW.Builder mqttEventRW = new MqttEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int mqttTypeId;
    private final MessageConsumer logEvent;
    private final LongSupplier timestamp;

    public MqttEventContext(
        EngineContext context)
    {
        this.mqttTypeId = context.supplyTypeId(MqttBinding.NAME);
        this.logEvent = context.logger();
        this.timestamp = context.timestamp();
    }

    public void authorization(
        long sessionId,
        long traceId,
        long routedId,
        String identity)
    {
        Result result = sessionId == 0 ? Result.FAILURE : Result.SUCCESS;
        MqttEventFW event = mqttEventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .authorization(e -> e
                .timestamp(timestamp.getAsLong())
                .traceId(traceId)
                .bindingId(routedId)
                .result(r -> r.set(result))
                .identity(identity)
            )
            .build();
        System.out.println(event); // TODO: Ati
        logEvent.accept(mqttTypeId, event.buffer(), event.offset(), event.limit());
    }
}
