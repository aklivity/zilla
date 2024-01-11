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

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_RECONNECT_DELAY;

import java.util.function.Function;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCache;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class KafkaCacheServerFactory implements KafkaStreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();

    private final int kafkaTypeId;
    private final Int2ObjectHashMap<BindingHandler> factories;
    private final Long2ObjectHashMap<KafkaBindingConfig> bindings;
    private final KafkaCacheServerAddressFactory cacheAddressFactory;
    private final EngineContext context;

    public KafkaCacheServerFactory(
        KafkaConfiguration config,
        EngineContext context,
        Function<String, KafkaCache> supplyCache,
        LongFunction<KafkaCacheRoute> supplyCacheRoute)
    {
        final Long2ObjectHashMap<KafkaBindingConfig> bindings = new Long2ObjectHashMap<>();
        final Int2ObjectHashMap<BindingHandler> factories = new Int2ObjectHashMap<>();

        final KafkaCacheBootstrapFactory cacheBootstrapFactory = new KafkaCacheBootstrapFactory(
                config, context, bindings::get);

        final KafkaCacheMetaFactory cacheMetaFactory = new KafkaCacheMetaFactory(
                config, context, bindings::get, supplyCache, supplyCacheRoute, (routedId, resolvedId) -> routedId,
                KAFKA_CACHE_SERVER_RECONNECT_DELAY);

        final KafkaCacheServerDescribeFactory cacheDescribeFactory = new KafkaCacheServerDescribeFactory(
                config, context, bindings::get, supplyCache, supplyCacheRoute);

        final KafkaCacheGroupFactory cacheGroupFactory = new KafkaCacheGroupFactory(config, context, bindings::get);

        final KafkaCacheServerConsumerFactory consumerGroupFactory =
            new KafkaCacheServerConsumerFactory(config, context, bindings::get);

        final KafkaCacheOffsetCommitFactory cacheOffsetCommitFactory =
            new KafkaCacheOffsetCommitFactory(config, context, bindings::get);

        final KafkaCacheOffsetFetchFactory cacheOffsetFetchFactory =
            new KafkaCacheOffsetFetchFactory(config, context, bindings::get);

        final KafkaCacheServerFetchFactory cacheFetchFactory = new KafkaCacheServerFetchFactory(
                config, context, bindings::get, supplyCache, supplyCacheRoute);

        final KafkaCacheServerProduceFactory cacheProduceFactory = new KafkaCacheServerProduceFactory(
                config, context, bindings::get, supplyCache, supplyCacheRoute);

        factories.put(KafkaBeginExFW.KIND_BOOTSTRAP, cacheBootstrapFactory);
        factories.put(KafkaBeginExFW.KIND_META, cacheMetaFactory);
        factories.put(KafkaBeginExFW.KIND_DESCRIBE, cacheDescribeFactory);
        factories.put(KafkaBeginExFW.KIND_GROUP, cacheGroupFactory);
        factories.put(KafkaBeginExFW.KIND_CONSUMER, consumerGroupFactory);
        factories.put(KafkaBeginExFW.KIND_OFFSET_COMMIT, cacheOffsetCommitFactory);
        factories.put(KafkaBeginExFW.KIND_OFFSET_FETCH, cacheOffsetFetchFactory);
        factories.put(KafkaBeginExFW.KIND_FETCH, cacheFetchFactory);
        factories.put(KafkaBeginExFW.KIND_PRODUCE, cacheProduceFactory);

        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.factories = factories;
        this.bindings = bindings;
        this.context = context;
        this.cacheAddressFactory = new KafkaCacheServerAddressFactory(config, context, bindings::get);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        KafkaBindingConfig kafkaBinding = new KafkaBindingConfig(binding, context);

        bindings.put(binding.id, kafkaBinding);

        cacheAddressFactory.onAttached(binding.id);
    }

    @Override
    public void detach(
        long bindingId)
    {
        cacheAddressFactory.onDetached(bindingId);

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
        assert beginEx != null && typeId == kafkaTypeId;

        MessageConsumer newStream = null;

        final KafkaBeginExFW kafkaBeginEx = extension.get(kafkaBeginExRO::tryWrap);
        if (kafkaBeginEx != null)
        {
            final BindingHandler streamFactory = factories.get(kafkaBeginEx.kind());
            if (streamFactory != null)
            {
                newStream = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);
            }
        }

        return newStream;
    }
}
