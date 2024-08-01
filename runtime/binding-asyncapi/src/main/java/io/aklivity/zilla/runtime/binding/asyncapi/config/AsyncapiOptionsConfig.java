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
package io.aklivity.zilla.runtime.binding.asyncapi.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class AsyncapiOptionsConfig extends OptionsConfig
{
    public final TcpOptionsConfig tcp;
    public final TlsOptionsConfig tls;
    public final HttpOptionsConfig http;
    public final MqttOptionsConfig mqtt;
    public final KafkaOptionsConfig kafka;
    public final AsyncapiMqttKafkaConfig mqttKafka;
    public final List<AsyncapiSpecificationConfig> specs;

    public static AsyncapiOptionsConfigBuilder<AsyncapiOptionsConfig> builder()
    {
        return new AsyncapiOptionsConfigBuilder<>(AsyncapiOptionsConfig.class::cast);
    }

    public static <T> AsyncapiOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new AsyncapiOptionsConfigBuilder<>(mapper);
    }

    AsyncapiOptionsConfig(
        TcpOptionsConfig tcp,
        TlsOptionsConfig tls,
        HttpOptionsConfig http,
        MqttOptionsConfig mqtt,
        KafkaOptionsConfig kafka,
        AsyncapiMqttKafkaConfig mqttKafka,
        List<AsyncapiSpecificationConfig> specs)
    {
        this.http = http;
        this.mqtt = mqtt;
        this.tcp = tcp;
        this.tls = tls;
        this.kafka = kafka;
        this.mqttKafka = mqttKafka;
        this.specs = specs;
    }
}
