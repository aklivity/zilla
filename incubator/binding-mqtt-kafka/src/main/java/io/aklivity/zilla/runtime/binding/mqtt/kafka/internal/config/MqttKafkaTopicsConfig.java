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

public class MqttKafkaTopicsConfig
{
    public final String16FW sessions;
    public final String16FW messages;
    public final String16FW retained;

    public MqttKafkaTopicsConfig(
        String16FW sessions,
        String16FW messages,
        String16FW retained)
    {
        this.sessions = sessions;
        this.messages = messages;
        this.retained = retained;
    }
}

