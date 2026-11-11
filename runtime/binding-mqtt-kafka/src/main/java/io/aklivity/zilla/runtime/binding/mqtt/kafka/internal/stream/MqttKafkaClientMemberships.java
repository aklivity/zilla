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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MqttKafkaClientMemberships extends MqttKafkaClientEntry<MqttKafkaMembership>
{
    private final MqttKafkaMemberships codec;
    private final MqttKafkaRegistry registry;
    private final BiFunction<String, String, String> supplyRegistryKey;

    private List<Consumer<Runnable>> operations;

    MqttKafkaClientMemberships(
        StoreHandler store,
        String key,
        MqttKafkaMemberships codec,
        MqttKafkaRegistry registry,
        BiFunction<String, String, String> supplyRegistryKey,
        Runnable onSettled)
    {
        super(store, key, onSettled);
        this.codec = codec;
        this.registry = registry;
        this.supplyRegistryKey = supplyRegistryKey;
    }

    @Override
    boolean idle()
    {
        return operations == null && super.idle();
    }

    void join(
        String shareName,
        String filter,
        LongConsumer joined,
        Runnable failed)
    {
        submit(done -> onJoin(shareName, filter, joined, failed, done));
    }

    void reconcile(
        List<MqttKafkaMembership> subscribed)
    {
        submit(done -> onReconcile(subscribed, done));
    }

    void discard()
    {
        submit(this::onDiscard);
    }

    @Override
    List<MqttKafkaMembership> decode(
        String value)
    {
        return codec.decode(value);
    }

    @Override
    String encode(
        List<MqttKafkaMembership> memberships)
    {
        return codec.encode(memberships);
    }

    private void submit(
        Consumer<Runnable> operation)
    {
        if (operations == null)
        {
            operations = new ArrayList<>();
            run(operation);
        }
        else
        {
            operations.add(operation);
        }
    }

    private void run(
        Consumer<Runnable> operation)
    {
        awaitLoaded(() -> operation.accept(this::complete));
    }

    private void complete()
    {
        if (operations.isEmpty())
        {
            operations = null;
            settled();
        }
        else
        {
            run(operations.remove(0));
        }
    }

    private void onJoin(
        String shareName,
        String filter,
        LongConsumer joined,
        Runnable failed,
        Runnable done)
    {
        final Runnable refused = () ->
        {
            failed.run();
            done.run();
        };

        if (ready())
        {
            final MqttKafkaMembership membership = new MqttKafkaMembership(shareName, filter);
            final String registryKey = supplyRegistryKey.apply(shareName, filter);
            final LongConsumer found = epoch ->
            {
                joined.accept(epoch);
                done.run();
            };

            if (entries.contains(membership))
            {
                registry.epoch(registryKey, found, refused);
            }
            else
            {
                registry.join(registryKey, epoch ->
                {
                    entries.add(membership);
                    write(() -> found.accept(epoch));
                }, refused);
            }
        }
        else
        {
            refused.run();
        }
    }

    private void onReconcile(
        List<MqttKafkaMembership> subscribed,
        Runnable done)
    {
        final List<MqttKafkaMembership> withdrawn = new ArrayList<>();

        if (ready())
        {
            entries.forEach(m ->
            {
                if (!subscribed.contains(m))
                {
                    withdrawn.add(m);
                }
            });
        }

        if (withdrawn.isEmpty())
        {
            done.run();
        }
        else
        {
            entries.removeAll(withdrawn);
            write(() -> leave(withdrawn, 0, done));
        }
    }

    private void onDiscard(
        Runnable done)
    {
        final List<MqttKafkaMembership> withdrawn = entries != null ? List.copyOf(entries) : List.of();

        if (entries == null || !entries.isEmpty())
        {
            entries = new ArrayList<>();
            write(() -> leave(withdrawn, 0, done));
        }
        else
        {
            done.run();
        }
    }

    private void leave(
        List<MqttKafkaMembership> withdrawn,
        int index,
        Runnable done)
    {
        if (index < withdrawn.size())
        {
            final MqttKafkaMembership membership = withdrawn.get(index);

            registry.leave(supplyRegistryKey.apply(membership.shareName, membership.filter),
                () -> leave(withdrawn, index + 1, done));
        }
        else
        {
            done.run();
        }
    }
}
