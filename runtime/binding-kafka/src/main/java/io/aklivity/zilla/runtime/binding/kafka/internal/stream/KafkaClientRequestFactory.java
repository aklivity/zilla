/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.util.function.LongFunction;
import java.util.function.UnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaApi;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaRequestBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class KafkaClientRequestFactory implements BindingHandler
{
    private final BeginFW beginRO = new BeginFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();

    private final int kafkaTypeId;
    private final Int2ObjectHashMap<BindingHandler> factories;

    public KafkaClientRequestFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        LongFunction<BudgetDebitor> supplyDebitor,
        Signaler signaler,
        BindingHandler streamFactory,
        UnaryOperator<KafkaSaslConfig> resolveSasl)
    {
        final KafkaClientDescribeClusterFactory clientDescribeClusterFactory = new KafkaClientDescribeClusterFactory(
            config, context, supplyBinding, supplyDebitor, signaler, streamFactory, resolveSasl);
        final KafkaClientCreateTopicsFactory clientCreateTopicsFactory = new KafkaClientCreateTopicsFactory(
            config, context, supplyBinding, supplyDebitor, signaler, streamFactory, resolveSasl);
        final KafkaClientDeleteTopicsFactory clientDeleteTopicsFactory = new KafkaClientDeleteTopicsFactory(
            config, context, supplyBinding, supplyDebitor, signaler, streamFactory, resolveSasl);

        final Int2ObjectHashMap<BindingHandler> factories = new Int2ObjectHashMap<>();
        factories.put(KafkaApi.DESCRIBE_CLUSTER.value(), clientDescribeClusterFactory);
        factories.put(KafkaApi.CREATE_TOPICS.value(), clientCreateTopicsFactory);
        factories.put(KafkaApi.DELETE_TOPICS.value(), clientDeleteTopicsFactory);

        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.factories = factories;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        final KafkaRequestBeginExFW request = kafkaBeginEx.request();

        MessageConsumer newStream = null;

        if (request != null)
        {
            final BindingHandler factory = factories.get(request.kind());
            if (factory != null)
            {
                newStream = factory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), application);
            }
        }

        return newStream;
    }
}
