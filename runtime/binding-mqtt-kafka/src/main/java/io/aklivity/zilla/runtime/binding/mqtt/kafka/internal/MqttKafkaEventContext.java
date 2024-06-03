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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal;

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventType.NON_COMPACT_SESSIONS_TOPIC;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class MqttKafkaEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final MqttKafkaEventExFW.Builder mqttKafkaEventExRW = new MqttKafkaEventExFW.Builder();
    private final int mqttTypeId;
    private final int nonCompactSessionsTopicEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public MqttKafkaEventContext(
        EngineContext context)
    {
        this.mqttTypeId = context.supplyTypeId(MqttKafkaBinding.NAME);
        this.nonCompactSessionsTopicEventId = context.supplyEventId("binding.mqtt.kafka.non.compact.sessions.topic");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void onMqttConnectionReset(
        long traceId,
        long bindingId)
    {
        MqttKafkaEventExFW extension = mqttKafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .nonCompactSessionsTopic(e -> e
                .typeId(NON_COMPACT_SESSIONS_TOPIC.value()))
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(nonCompactSessionsTopicEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mqttTypeId, event.buffer(), event.offset(), event.limit());
    }
}
