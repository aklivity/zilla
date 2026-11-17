/*
 * Copyright 2021-2026 Aklivity Inc
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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventType.KAFKA_STREAM_FAILED;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventType.NON_COMPACT_RETAINED_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventType.NON_COMPACT_SESSIONS_TOPIC;

import java.nio.ByteBuffer;
import java.time.Clock;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaStreamKind;
import io.aklivity.zilla.runtime.common.agrona.buffer.AtomicBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class MqttKafkaEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 2048;

    private final AtomicBufferEx eventBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBufferEx extensionBuffer = new UnsafeBufferEx(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final MqttKafkaEventExFW.Builder mqttKafkaEventExRW = new MqttKafkaEventExFW.Builder();
    private final int mqttTypeId;
    private final int nonCompactSessionsTopicEventId;
    private final int nonCompactRetainedTopicEventId;
    private final int kafkaStreamFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public MqttKafkaEventContext(
        EngineContext context)
    {
        this.mqttTypeId = context.supplyTypeId(MqttKafkaBinding.TYPE);
        this.nonCompactSessionsTopicEventId = context.supplyEventId("binding.mqtt.kafka.non.compact.sessions.topic");
        this.nonCompactRetainedTopicEventId = context.supplyEventId("binding.mqtt.kafka.non.compact.retained.topic");
        this.kafkaStreamFailedEventId = context.supplyEventId("binding.mqtt.kafka.stream.failed");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void onMqttConnectionReset(
        long traceId,
        long bindingId,
        String16FW topic)
    {
        MqttKafkaEventExFW extension = mqttKafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .nonCompactSessionsTopic(e -> e
                .typeId(NON_COMPACT_SESSIONS_TOPIC.value())
                .topic(topic))
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

    public void onNonCompactRetainedTopic(
        long traceId,
        long bindingId,
        String16FW topic)
    {
        MqttKafkaEventExFW extension = mqttKafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .nonCompactRetainedTopic(e -> e
                .typeId(NON_COMPACT_RETAINED_TOPIC.value())
                .topic(topic))
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(nonCompactRetainedTopicEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mqttTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void onKafkaStreamFailed(
        long traceId,
        long bindingId,
        MqttKafkaStreamKind stream,
        boolean connected,
        String clientId,
        String topic,
        String group)
    {
        MqttKafkaEventExFW extension = mqttKafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .kafkaStreamFailed(e -> e
                .typeId(KAFKA_STREAM_FAILED.value())
                .stream(k -> k.set(stream))
                .connected(connected ? 1 : 0)
                .clientId(clientId != null ? clientId : "")
                .topic(topic != null ? topic : "")
                .group(group != null ? group : ""))
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(kafkaStreamFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(mqttTypeId, event.buffer(), event.offset(), event.limit());
    }
}
