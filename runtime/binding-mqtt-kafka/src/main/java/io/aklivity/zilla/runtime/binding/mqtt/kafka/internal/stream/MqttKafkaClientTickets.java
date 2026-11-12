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

final class MqttKafkaClientTickets extends MqttKafkaClientEntry<MqttKafkaTicket>
{
    private final MqttKafkaTickets codec;
    private final Consumer<MqttKafkaTicket> onLoadedTicket;
    private final IntArrayList recoverable;

    MqttKafkaClientTickets(
        StoreHandler store,
        String key,
        MqttKafkaTickets codec,
        Consumer<MqttKafkaTicket> onLoadedTicket,
        Runnable onWritten)
    {
        super(store, key, onWritten);
        this.codec = codec;
        this.onLoadedTicket = onLoadedTicket;
        this.recoverable = new IntArrayList();
    }

    MqttKafkaTicket find(
        int packetId)
    {
        MqttKafkaTicket found = null;

        for (int i = 0; found == null && ready() && i < entries.size(); i++)
        {
            final MqttKafkaTicket ticket = entries.get(i);
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
        entries.add(ticket);
    }

    void replace(
        MqttKafkaTicket ticket)
    {
        entries.replaceAll(t -> t.packetId == ticket.packetId ? ticket : t);
    }

    void remove(
        int packetId)
    {
        entries.removeIf(t -> t.packetId == packetId);
    }

    void abandon(
        int packetId,
        Runnable completion)
    {
        remove(packetId);
        write(completion);
    }

    void discard(
        Consumer<MqttKafkaTicket> discarded)
    {
        awaitLoaded(() ->
        {
            final List<MqttKafkaTicket> stored = entries;

            if (stored == null || !stored.isEmpty())
            {
                if (stored != null)
                {
                    stored.forEach(discarded);
                }

                entries = new ArrayList<>();
                recoverable.clear();
                write(() -> {});
            }
        });
    }

    @Override
    List<MqttKafkaTicket> decode(
        String value)
    {
        return codec.decode(value);
    }

    @Override
    String encode(
        List<MqttKafkaTicket> tickets)
    {
        return codec.encode(tickets);
    }

    @Override
    void onDecoded()
    {
        entries.forEach(t ->
        {
            onLoadedTicket.accept(t);
            recoverable.addInt(t.packetId);
        });
    }
}
