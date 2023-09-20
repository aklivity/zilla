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

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.budget.KafkaMergedBudgetAccountant;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class KafkaClientFactory implements KafkaStreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();

    private final int kafkaTypeId;
    private final Long2ObjectHashMap<KafkaBindingConfig> bindings;
    private final Int2ObjectHashMap<BindingHandler> factories;
    private final KafkaClientConnectionPoolFactory connectionPool;

    public KafkaClientFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaClientRoute> supplyClientRoute)
    {
        final Long2ObjectHashMap<KafkaBindingConfig> bindings = new Long2ObjectHashMap<>();
        final KafkaMergedBudgetAccountant accountant = new KafkaMergedBudgetAccountant(context);

        this.connectionPool = new KafkaClientConnectionPoolFactory(
            config, context, bindings::get, accountant.creditor());

        final KafkaClientMetaFactory clientMetaFactory = new KafkaClientMetaFactory(
                config, context, bindings::get, accountant::supplyDebitor, supplyClientRoute);

        final KafkaClientDescribeFactory clientDescribeFactory = new KafkaClientDescribeFactory(
                config, context, bindings::get, accountant::supplyDebitor);

        final KafkaClientGroupFactory clientGroupFactory = new KafkaClientGroupFactory(
            config, context, bindings::get, accountant::supplyDebitor, connectionPool);

        final KafkaClientFetchFactory clientFetchFactory = new KafkaClientFetchFactory(
                config, context, bindings::get, accountant::supplyDebitor, supplyClientRoute);

        final KafkaClientProduceFactory clientProduceFactory = new KafkaClientProduceFactory(
                config, context, bindings::get, supplyClientRoute);

        final KafkaClientOffsetFetchFactory clientOffsetFetchFactory = new KafkaClientOffsetFetchFactory(
            config, context, bindings::get, supplyClientRoute);

        final KafkaMergedFactory clientMergedFactory = new KafkaMergedFactory(
                config, context, bindings::get, accountant.creditor());

        final Int2ObjectHashMap<BindingHandler> factories = new Int2ObjectHashMap<>();
        factories.put(KafkaBeginExFW.KIND_META, clientMetaFactory);
        factories.put(KafkaBeginExFW.KIND_DESCRIBE, clientDescribeFactory);
        factories.put(KafkaBeginExFW.KIND_GROUP, clientGroupFactory);
        factories.put(KafkaBeginExFW.KIND_FETCH, clientFetchFactory);
        factories.put(KafkaBeginExFW.KIND_PRODUCE, clientProduceFactory);
        factories.put(KafkaBeginExFW.KIND_OFFSET_FETCH, clientOffsetFetchFactory);
        factories.put(KafkaBeginExFW.KIND_MERGED, clientMergedFactory);

        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.factories = factories;
        this.bindings = bindings;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        KafkaBindingConfig kafkaBinding = new KafkaBindingConfig(binding);
        bindings.put(binding.id, kafkaBinding);

        KafkaClientGroupFactory clientGroupFactory = (KafkaClientGroupFactory) factories.get(KafkaBeginExFW.KIND_GROUP);
        clientGroupFactory.onAttached(binding.id);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
        KafkaClientGroupFactory clientGroupFactory = (KafkaClientGroupFactory) factories.get(KafkaBeginExFW.KIND_GROUP);
        clientGroupFactory.onDetached(bindingId);
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

        MessageConsumer newStream = null;

        if (kafkaBeginEx != null)
        {
            final BindingHandler factory = factories.get(kafkaBeginEx.kind());
            if (factory != null)
            {
                newStream = factory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), application);
            }
        }

        return newStream;
    }
}
