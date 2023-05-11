/*
 * Copyright 2021-2022 Aklivity Inc
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

import io.aklivity.zilla.runtime.engine.Configuration;

public class MqttKafkaConfiguration extends Configuration
{
    private static final ConfigurationDef MQTT_KAFKA_CONFIG;

    public static final PropertyDef<String> KAFKA_MESSAGES_TOPIC;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mqtt.kafka");
        KAFKA_MESSAGES_TOPIC = config.property("messages.topic", "mqtt_messages");
        MQTT_KAFKA_CONFIG = config;
    }

    public MqttKafkaConfiguration(
        Configuration config)
    {
        super(MQTT_KAFKA_CONFIG, config);
    }

    public String kafkaMessagesTopic()
    {
        return KAFKA_MESSAGES_TOPIC.get(this);
    }
}
