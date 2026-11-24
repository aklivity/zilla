/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MqttClientStateEntry
{
    private final StoreHandler store;
    private final String key;
    private final MqttClientStates states;
    private final Supplier<Duration> ttl;

    private MqttClientStateRecord record;
    private List<Runnable> loading;
    private List<Runnable> writing;
    private List<Runnable> pending;
    private List<Runnable> idling;

    MqttClientStateEntry(
        StoreHandler store,
        String key,
        MqttClientStates states,
        Supplier<Duration> ttl)
    {
        this.store = store;
        this.key = key;
        this.states = states;
        this.ttl = ttl;
        this.loading = new ArrayList<>();
        store.get(key, (k, value) -> onLoaded(value));
    }

    void awaitLoaded(
        Runnable continuation)
    {
        if (loading != null)
        {
            loading.add(continuation);
        }
        else
        {
            continuation.run();
        }
    }

    void awaitIdle(
        Runnable continuation)
    {
        if (idle())
        {
            continuation.run();
        }
        else
        {
            if (idling == null)
            {
                idling = new ArrayList<>();
            }
            idling.add(continuation);
        }
    }

    boolean ready()
    {
        return loading == null && record != null;
    }

    boolean idle()
    {
        return loading == null && writing == null && pending == null;
    }

    List<MqttPacketRecord> packets()
    {
        return record.packets;
    }

    List<MqttMembershipRecord> memberships()
    {
        return record.memberships;
    }

    boolean empty()
    {
        return record.packets.isEmpty() && record.memberships.isEmpty();
    }

    void discard()
    {
        record.packets.clear();
        record.memberships.clear();
    }

    MqttPacketRecord packet(
        int packetId)
    {
        MqttPacketRecord found = null;

        if (ready())
        {
            final List<MqttPacketRecord> packets = record.packets;
            for (int index = 0; found == null && index < packets.size(); index++)
            {
                final MqttPacketRecord packet = packets.get(index);
                if (packet.packetId == packetId)
                {
                    found = packet;
                }
            }
        }

        return found;
    }

    void add(
        MqttPacketRecord packet)
    {
        record.packets.add(packet);
    }

    void replace(
        MqttPacketRecord packet)
    {
        final List<MqttPacketRecord> packets = record.packets;
        for (int index = 0; index < packets.size(); index++)
        {
            if (packets.get(index).packetId == packet.packetId)
            {
                packets.set(index, packet);
            }
        }
    }

    boolean remove(
        int packetId)
    {
        return ready() && record.packets.removeIf(packet -> packet.packetId == packetId);
    }

    void write(
        Runnable completion)
    {
        if (pending == null)
        {
            pending = new ArrayList<>();
        }

        pending.add(completion);

        if (writing == null)
        {
            doWrite();
        }
    }

    private void onLoaded(
        String value)
    {
        final List<Runnable> continuations = loading;

        record = states.decode(value);
        loading = null;

        continuations.forEach(Runnable::run);
        settled();
    }

    private void doWrite()
    {
        writing = pending;
        pending = null;

        if (record.packets.isEmpty() && record.memberships.isEmpty())
        {
            store.delete(key, v -> onWriteCompleted());
        }
        else
        {
            store.put(key, states.encode(record.memberships, record.packets), ttl.get(), v -> onWriteCompleted());
        }
    }

    private void onWriteCompleted()
    {
        final List<Runnable> completions = writing;

        writing = null;
        completions.forEach(Runnable::run);

        if (pending != null && writing == null)
        {
            doWrite();
        }

        settled();
    }

    private void settled()
    {
        if (idling != null && idle())
        {
            final List<Runnable> continuations = idling;
            idling = null;
            continuations.forEach(Runnable::run);
        }
    }
}
