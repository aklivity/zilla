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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class MqttKafkaProxyFactory implements MqttKafkaStreamFactory
{
    private static final String MQTT_TYPE_NAME = "mqtt";
    private final BeginFW beginRO = new BeginFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();

    private final int mqttTypeId;
    private final Int2ObjectHashMap<BindingHandler> factories;
    private final Long2ObjectHashMap<MqttKafkaBindingConfig> bindings;

    public MqttKafkaProxyFactory(
        MqttKafkaConfiguration config,
        EngineContext context)
    {
        final Long2ObjectHashMap<MqttKafkaBindingConfig> bindings = new Long2ObjectHashMap<>();
        final Int2ObjectHashMap<BindingHandler> factories = new Int2ObjectHashMap<>();

        final MqttKafkaPublishFactory publishFactory = new MqttKafkaPublishFactory(
            config, context, bindings::get);

        final MqttKafkaSubscribeFactory subscribeFactory = new MqttKafkaSubscribeFactory(
            config, context, bindings::get);

        //        final MqttKafkaSessionFactory sessionFactory = new MqttKafkaSessionFactory(
        //            config, context, bindings::get);

        factories.put(MqttBeginExFW.KIND_PUBLISH, publishFactory);
        factories.put(MqttBeginExFW.KIND_SUBSCRIBE, subscribeFactory);
        //        factories.put(MqttBeginExFW.KIND_SESSION, sessionFactory);

        this.mqttTypeId = context.supplyTypeId(MQTT_TYPE_NAME);
        this.factories = factories;
        this.bindings = bindings;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        MqttKafkaBindingConfig kafkaBinding = new MqttKafkaBindingConfig(binding);
        bindings.put(binding.id, kafkaBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extension.get(extensionRO::tryWrap);
        assert beginEx != null;
        final int typeId = beginEx.typeId();
        assert beginEx != null && typeId == mqttTypeId;

        MessageConsumer newStream = null;

        final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);
        if (mqttBeginEx != null)
        {
            final BindingHandler streamFactory = factories.get(mqttBeginEx.kind());
            if (streamFactory != null)
            {
                newStream = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);
            }
        }

        return newStream;
    }
}
