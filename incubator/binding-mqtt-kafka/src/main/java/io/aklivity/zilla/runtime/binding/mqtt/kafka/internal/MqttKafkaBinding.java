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

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public class MqttKafkaBinding implements Binding
{
    public static final String NAME = "mqtt-kafka";

    private final MqttKafkaConfiguration config;

    MqttKafkaBinding(
        MqttKafkaConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return MqttKafkaBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/mqtt.kafka.schema.patch.json");
    }

    @Override
    public MqttKafkaBindingContext supply(
        EngineContext context)
    {
        return new MqttKafkaBindingContext(config, context);
    }
}
