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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaEventExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.event.MqttKafkaNonCompactSessionsTopicExFW;
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
        DirectBuffer buffer,
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
        }
        return result;
    }

    private static String asString(
        String16FW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }
}
