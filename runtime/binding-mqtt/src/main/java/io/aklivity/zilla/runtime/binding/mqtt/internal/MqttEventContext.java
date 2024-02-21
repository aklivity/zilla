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
import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.event.MqttEventFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class MqttEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final MqttEventFW.Builder mqttEventRW = new MqttEventFW.Builder();
    private final MutableDirectBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final int mqttTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public MqttEventContext(
        EngineContext context)
    {
        this.mqttTypeId = context.supplyTypeId(MqttBinding.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void authorizationFailure(
        long sessionId,
        long traceId,
        long routedId,
        GuardHandler guard,
        long authorization)
    {
        if (sessionId == 0)
        {
            String identity = guard == null ? null : guard.identity(authorization);
            MqttEventFW event = mqttEventRW
                .wrap(eventBuffer, 0, eventBuffer.capacity())
                .authorizationFailure(e -> e
                    .timestamp(clock.millis())
                    .traceId(traceId)
                    .namespacedId(routedId)
                    .identity(identity)
                )
                .build();
            eventWriter.accept(mqttTypeId, event.buffer(), event.offset(), event.limit());
        }
    }
}
