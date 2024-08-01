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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class AsyncapiOptionsConfigBuilder<T> extends ConfigBuilder<T, AsyncapiOptionsConfigBuilder<T>>
{
    private static final String DEFAULT_SESSIONS_CHANNEL = "mqttSessions";
    private static final String DEFAULT_RETAINED_CHANNEL = "mqttRetained";
    private static final String DEFAULT_MESSAGES_CHANNEL = "mqttMessages";
    private static final AsyncapiMqttKafkaConfig DEFAULT_MQTT_KAFKA =
        AsyncapiMqttKafkaConfig.builder()
            .channels()
                .sessions(DEFAULT_SESSIONS_CHANNEL)
                .messages(DEFAULT_MESSAGES_CHANNEL)
                .retained(DEFAULT_RETAINED_CHANNEL)
                .build()
            .build();

    private final Function<OptionsConfig, T> mapper;

    private TcpOptionsConfig tcp;
    private TlsOptionsConfig tls;
    private HttpOptionsConfig http;
    private MqttOptionsConfig mqtt;
    private KafkaOptionsConfig kafka;
    private List<AsyncapiSpecificationConfig> specs;
    private AsyncapiMqttKafkaConfig mqttKafka = DEFAULT_MQTT_KAFKA;

    AsyncapiOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiOptionsConfigBuilder<T>> thisType()
    {
        return (Class<AsyncapiOptionsConfigBuilder<T>>) getClass();
    }

    public TcpOptionsConfigBuilder<AsyncapiOptionsConfigBuilder<T>> tcp()
    {
        Function<TcpOptionsConfig, AsyncapiOptionsConfigBuilder<T>> mapper = this::tcp;
        return TcpOptionsConfig.builder(mapper.compose(TcpOptionsConfig.class::cast));
    }

    public AsyncapiOptionsConfigBuilder<T> tcp(
        TcpOptionsConfig tcp)
    {
        this.tcp = tcp;
        return this;
    }

    public TlsOptionsConfigBuilder<AsyncapiOptionsConfigBuilder<T>> tls()
    {
        Function<TlsOptionsConfig, AsyncapiOptionsConfigBuilder<T>> mapper = this::tls;
        return TlsOptionsConfig.builder(mapper.compose(TlsOptionsConfig.class::cast));
    }

    public AsyncapiOptionsConfigBuilder<T> tls(
        TlsOptionsConfig tls)
    {
        this.tls = tls;
        return this;
    }

    public HttpOptionsConfigBuilder<AsyncapiOptionsConfigBuilder<T>> http()
    {
        Function<HttpOptionsConfig, AsyncapiOptionsConfigBuilder<T>> mapper = this::http;
        return HttpOptionsConfig.builder(mapper.compose(HttpOptionsConfig.class::cast));
    }

    public AsyncapiOptionsConfigBuilder<T> http(
        HttpOptionsConfig http)
    {
        this.http = http;
        return this;
    }

    public MqttOptionsConfigBuilder<AsyncapiOptionsConfigBuilder<T>> mqtt()
    {
        Function<MqttOptionsConfig, AsyncapiOptionsConfigBuilder<T>> mapper = this::mqtt;
        return MqttOptionsConfig.builder(mapper.compose(MqttOptionsConfig.class::cast));
    }

    public AsyncapiOptionsConfigBuilder<T> mqtt(
        MqttOptionsConfig mqtt)
    {
        this.mqtt = mqtt;
        return this;
    }

    public KafkaOptionsConfigBuilder<AsyncapiOptionsConfigBuilder<T>> kafka()
    {
        Function<KafkaOptionsConfig, AsyncapiOptionsConfigBuilder<T>> mapper = this::kafka;
        return KafkaOptionsConfig.builder(mapper.compose(KafkaOptionsConfig.class::cast));
    }

    public AsyncapiOptionsConfigBuilder<T> kafka(
        KafkaOptionsConfig kafka)
    {
        this.kafka = kafka;
        return this;
    }

    public AsyncapiMqttKafkaConfigBuilder<AsyncapiOptionsConfigBuilder<T>> mqttKafka()
    {
        return new AsyncapiMqttKafkaConfigBuilder<>(this::mqttKafka);
    }

    public AsyncapiOptionsConfigBuilder<T> mqttKafka(
        AsyncapiMqttKafkaConfig mqttKafka)
    {
        this.mqttKafka = mqttKafka;
        return this;
    }

    public AsyncapiSpecificationConfigBuilder<AsyncapiOptionsConfigBuilder<T>> spec()
    {
        return new AsyncapiSpecificationConfigBuilder<>(this::spec);
    }

    public AsyncapiOptionsConfigBuilder<T> spec(
        AsyncapiSpecificationConfig spec)
    {
        if (specs == null)
        {
            specs = new ArrayList<>();
        }
        specs.add(spec);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new AsyncapiOptionsConfig(tcp, tls, http, mqtt, kafka, mqttKafka, specs));
    }
}
