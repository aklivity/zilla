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
import java.util.function.Consumer;

import org.agrona.collections.IntArrayList;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MqttKafkaClientTickets
{
    private final StoreHandler store;
    private final String key;
    private final MqttKafkaTickets codec;
    private final Consumer<MqttKafkaTicket> onLoadedTicket;
    private final Runnable onWritten;
    private final IntArrayList recoverable;

    private List<MqttKafkaTicket> tickets;
    private List<Runnable> loading;
    private List<Runnable> writing;
    private List<Runnable> pending;

    MqttKafkaClientTickets(
        StoreHandler store,
        String key,
        MqttKafkaTickets codec,
        Consumer<MqttKafkaTicket> onLoadedTicket,
        Runnable onWritten)
    {
        this.store = store;
        this.key = key;
        this.codec = codec;
        this.onLoadedTicket = onLoadedTicket;
        this.onWritten = onWritten;
        this.recoverable = new IntArrayList();
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

    boolean ready()
    {
        return loading == null && tickets != null;
    }

    boolean idle()
    {
        return writing == null && pending == null;
    }

    MqttKafkaTicket find(
        int packetId)
    {
        MqttKafkaTicket found = null;

        for (int i = 0; found == null && ready() && i < tickets.size(); i++)
        {
            final MqttKafkaTicket ticket = tickets.get(i);
            found = ticket.packetId == packetId ? ticket : null;
        }

        return found;
    }

    List<MqttKafkaTicket> recover(
        String shareName,
        String filter)
    {
        final List<MqttKafkaTicket> recovered = new ArrayList<>();

        for (int i = 0; ready() && i < recoverable.size(); )
        {
            final MqttKafkaTicket ticket = find(recoverable.getInt(i));

            if (ticket == null || ticket.subscribed(shareName, filter))
            {
                recoverable.removeAt(i);

                if (ticket != null)
                {
                    recovered.add(ticket);
                }
            }
            else
            {
                i++;
            }
        }

        return recovered;
    }

    void add(
        MqttKafkaTicket ticket)
    {
        tickets.add(ticket);
    }

    void replace(
        MqttKafkaTicket ticket)
    {
        tickets.replaceAll(t -> t.packetId == ticket.packetId ? ticket : t);
    }

    void remove(
        int packetId)
    {
        tickets.removeIf(t -> t.packetId == packetId);
    }

    void abandon(
        int packetId,
        Runnable completion)
    {
        remove(packetId);
        write(completion);
    }

    List<MqttKafkaTicket> clear()
    {
        final List<MqttKafkaTicket> cleared = tickets != null ? tickets : new ArrayList<>();

        tickets = new ArrayList<>();

        return cleared;
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

        loading = null;
        tickets = codec.decode(value);

        if (tickets != null)
        {
            tickets.forEach(t ->
            {
                onLoadedTicket.accept(t);
                recoverable.addInt(t.packetId);
            });
        }

        continuations.forEach(Runnable::run);
    }

    private void doWrite()
    {
        writing = pending;
        pending = null;

        if (tickets.isEmpty())
        {
            store.delete(key, v -> onWriteCompleted());
        }
        else
        {
            store.put(key, codec.encode(tickets), null, v -> onWriteCompleted());
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

        onWritten.run();
    }
}
