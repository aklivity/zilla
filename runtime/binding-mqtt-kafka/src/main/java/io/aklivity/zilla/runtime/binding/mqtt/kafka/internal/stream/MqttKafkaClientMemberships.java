/*
 * Copyright 2021-2026 Aklivity Inc
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

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MqttKafkaClientMemberships extends MqttKafkaClientEntry<MqttKafkaMembership>
{
    MqttKafkaClientMemberships(
        StoreHandler store,
        String key,
        MqttKafkaMemberships codec,
        MqttKafkaRegistry registry,
        BiFunction<String, String, String> supplyRegistryKey,
        Runnable onSettled)
    {
        super(store, key, onSettled);
    }

    void join(
        String shareName,
        String filter,
        LongConsumer joined,
        Runnable failed)
    {
        throw new UnsupportedOperationException();
    }

    void reconcile(
        List<MqttKafkaMembership> subscribed)
    {
        throw new UnsupportedOperationException();
    }

    void discard()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    List<MqttKafkaMembership> decode(
        String value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    String encode(
        List<MqttKafkaMembership> memberships)
    {
        throw new UnsupportedOperationException();
    }
}
