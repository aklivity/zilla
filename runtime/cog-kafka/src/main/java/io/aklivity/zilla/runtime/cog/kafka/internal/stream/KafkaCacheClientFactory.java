/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.stream;

import static io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration.KAFKA_CACHE_CLIENT_RECONNECT_DELAY;

import java.util.function.Function;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaCog;
import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.cog.kafka.internal.budget.KafkaMergedBudgetAccountant;
import io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class KafkaCacheClientFactory implements KafkaStreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();

    private final int kafkaTypeId;
    private final Int2ObjectHashMap<StreamFactory> factories;
    private final Long2ObjectHashMap<KafkaBindingConfig> bindings;

    public KafkaCacheClientFactory(
        KafkaConfiguration config,
        AxleContext context,
        Function<String, KafkaCache> supplyCache,
        LongFunction<KafkaCacheRoute> supplyCacheRoute)
    {
        final Long2ObjectHashMap<KafkaBindingConfig> bindings = new Long2ObjectHashMap<>();
        final KafkaMergedBudgetAccountant accountant = new KafkaMergedBudgetAccountant(context);

        final KafkaCacheMetaFactory cacheMetaFactory = new KafkaCacheMetaFactory(
                config, context, bindings::get, supplyCache, supplyCacheRoute, (routeId, resolvedId) -> resolvedId,
                KAFKA_CACHE_CLIENT_RECONNECT_DELAY);

        final KafkaCacheClientDescribeFactory cacheDescribeFactory = new KafkaCacheClientDescribeFactory(
                config, context, bindings::get, supplyCacheRoute);

        final KafkaCacheClientFetchFactory cacheFetchFactory = new KafkaCacheClientFetchFactory(
                config, context, bindings::get, accountant::supplyDebitor, supplyCache, supplyCacheRoute);

        final KafkaCacheClientProduceFactory cacheProduceFactory = new KafkaCacheClientProduceFactory(
                config, context, bindings::get, supplyCache, supplyCacheRoute);

        final KafkaMergedFactory cacheMergedFactory = new KafkaMergedFactory(
                config, context, bindings::get, accountant.creditor());

        final Int2ObjectHashMap<StreamFactory> factories = new Int2ObjectHashMap<>();
        factories.put(KafkaBeginExFW.KIND_META, cacheMetaFactory);
        factories.put(KafkaBeginExFW.KIND_DESCRIBE, cacheDescribeFactory);
        factories.put(KafkaBeginExFW.KIND_FETCH, cacheFetchFactory);
        factories.put(KafkaBeginExFW.KIND_PRODUCE, cacheProduceFactory);
        factories.put(KafkaBeginExFW.KIND_MERGED, cacheMergedFactory);

        this.kafkaTypeId = context.supplyTypeId(KafkaCog.NAME);
        this.factories = factories;
        this.bindings = bindings;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        KafkaBindingConfig kafkaBinding = new KafkaBindingConfig(binding);
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
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        MessageConsumer newStream = null;

        if (kafkaBeginEx != null)
        {
            final StreamFactory factory = factories.get(kafkaBeginEx.kind());
            if (factory != null)
            {
                newStream = factory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);
            }
        }

        return newStream;
    }
}
