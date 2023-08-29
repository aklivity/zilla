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


import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.INSTANCE_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.LIFETIME_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.MESSAGES_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.RETAINED_MESSAGES_TOPIC;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.SESSION_EXPIRY_INTERVAL;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.SESSION_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.TIME;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.WILL_AVAILABLE;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.WILL_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration.WILL_STREAM_RECONNECT_DELAY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MqttKafkaConfigurationTest
{
    public static final String MESSAGES_TOPIC_NAME = "zilla.binding.mqtt.kafka.messages.topic";
    public static final String RETAINED_MESSAGES_TOPIC_NAME = "zilla.binding.mqtt.kafka.retained.messages.topic";
    public static final String TIME_NAME = "zilla.binding.mqtt.kafka.time";
    public static final String WILL_AVAILABLE_NAME = "zilla.binding.mqtt.kafka.will.available";
    public static final String WILL_STREAM_RECONNECT_DELAY_NAME = "zilla.binding.mqtt.kafka.will.stream.reconnect";
    public static final String SESSION_ID_NAME = "zilla.binding.mqtt.kafka.session.id";
    public static final String WILL_ID_NAME = "zilla.binding.mqtt.kafka.will.id";
    public static final String LIFETIME_ID_NAME = "zilla.binding.mqtt.kafka.lifetime.id";
    public static final String INSTANCE_ID_NAME = "zilla.binding.mqtt.kafka.instance.id";
    public static final String SESSION_EXPIRY_INTERVAL_NAME = "zilla.binding.mqtt.kafka.session.expiry.interval";

    @Test
    public void shouldVerifyConstants()
    {
        assertEquals(MESSAGES_TOPIC.name(), MESSAGES_TOPIC_NAME);
        assertEquals(RETAINED_MESSAGES_TOPIC.name(), RETAINED_MESSAGES_TOPIC_NAME);
        assertEquals(TIME.name(), TIME_NAME);
        assertEquals(WILL_AVAILABLE.name(), WILL_AVAILABLE_NAME);
        assertEquals(WILL_STREAM_RECONNECT_DELAY.name(), WILL_STREAM_RECONNECT_DELAY_NAME);
        assertEquals(SESSION_ID.name(), SESSION_ID_NAME);
        assertEquals(WILL_ID.name(), WILL_ID_NAME);
        assertEquals(LIFETIME_ID.name(), LIFETIME_ID_NAME);
        assertEquals(INSTANCE_ID.name(), INSTANCE_ID_NAME);
        assertEquals(SESSION_EXPIRY_INTERVAL.name(), SESSION_EXPIRY_INTERVAL_NAME);
    }
}
