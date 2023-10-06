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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;


import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;

public class MqttKafkaWithResolver
{
    private final String16FW topic;


    public MqttKafkaWithResolver(
        MqttKafkaOptionsConfig options,
        MqttKafkaWithConfig with)
    {
        this.topic = with.topic == null ? options.topics.messages : new String16FW(with.topic);
    }

    public String16FW topic()
    {
        return topic;
    }
}
