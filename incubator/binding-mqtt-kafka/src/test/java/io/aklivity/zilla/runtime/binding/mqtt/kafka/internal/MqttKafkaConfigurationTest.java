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


import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.MESSAGES_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.RETAINED_MESSAGES_TOPIC;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MqttKafkaConfigurationTest
{
    public static final String KAFKA_MESSAGES_TOPIC_NAME = "zilla.binding.mqtt.kafka.messages.topic";
    public static final String KAFKA_RETAINED_MESSAGES_TOPIC_NAME = "zilla.binding.mqtt.kafka.retained.messages.topic";

    @Test
    public void shouldVerifyConstants()
    {
        assertEquals(MESSAGES_TOPIC.name(), KAFKA_MESSAGES_TOPIC_NAME);
        assertEquals(RETAINED_MESSAGES_TOPIC.name(), KAFKA_RETAINED_MESSAGES_TOPIC_NAME);
    }
}
