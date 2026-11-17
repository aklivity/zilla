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

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaKafkaStreamFailedExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaNonCompactRetainedTopicExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaNonCompactSessionsTopicExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaStreamKind;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.event.EventFormatterSpi;

public final class MqttKafkaEventFormatter implements EventFormatterSpi
{
    private final EventFW eventRO = new EventFW();
    private final MqttKafkaEventExFW mqttKafkaEventExRO = new MqttKafkaEventExFW();

    MqttKafkaEventFormatter(
        Configuration config)
    {
    }

    public String format(
        DirectBufferEx buffer,
        int index,
        int length)
    {
        final EventFW event = eventRO.wrap(buffer, index, index + length);
        final MqttKafkaEventExFW extension = mqttKafkaEventExRO
            .wrap(event.extension().buffer(), event.extension().offset(), event.extension().limit());
        String result = null;
        switch (extension.kind())
        {
        case NON_COMPACT_SESSIONS_TOPIC:
        {
            MqttKafkaNonCompactSessionsTopicExFW ex = extension.nonCompactSessionsTopic();
            result = String.format(
                    "The sessions topic (%s) is not log compacted. Update the cleanup policy to enable log compaction.",
                    asString(ex.topic())
            );
            break;
        }
        case NON_COMPACT_RETAINED_TOPIC:
        {
            MqttKafkaNonCompactRetainedTopicExFW ex = extension.nonCompactRetainedTopic();
            result = String.format(
                    "The retained topic (%s) is not log compacted, retained messages are not supported. " +
                    "Update the cleanup policy to enable log compaction.",
                    asString(ex.topic())
            );
            break;
        }
        case KAFKA_STREAM_FAILED:
        {
            MqttKafkaKafkaStreamFailedExFW ex = extension.kafkaStreamFailed();
            result = String.format(
                    "The Kafka %s stream%s%s failed %s MQTT client (%s) was connected, the MQTT stream was closed.",
                    streamName(ex.stream().get()),
                    qualifier("topic", ex.topic()),
                    qualifier("group", ex.group()),
                    ex.connected() != 0 ? "after" : "before",
                    asString(ex.clientId())
            );
            break;
        }
        }
        return result;
    }

    private static String streamName(
        MqttKafkaStreamKind kind)
    {
        String name;
        switch (kind)
        {
        case INIT_PRODUCER:
            name = "init producer id";
            break;
        case OFFSET_FETCH:
            name = "offset fetch";
            break;
        case OFFSET_COMMIT:
            name = "offset commit";
            break;
        case WILL_SIGNAL:
            name = "will signal";
            break;
        default:
            name = kind.name().toLowerCase();
            break;
        }
        return name;
    }

    private static String qualifier(
        String label,
        String16FW value)
    {
        String s = asString(value);
        return s.isEmpty() ? "" : String.format(" for %s (%s)", label, s);
    }

    private static String asString(
        String16FW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
