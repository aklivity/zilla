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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;

import org.agrona.LangUtil;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.binding.kafka.KafkaServerConfig;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class KafkaClientBrokers
{
    private static final String BROKERS_NAME = "brokers";
    private static final String PARTITIONS_NAME = "partitions";
    private static final String EXPIRES_AT_NAME = "expiresAt";

    private final Object2ObjectHashMap<String, KafkaClientBrokersEntry> entriesByKey = new Object2ObjectHashMap<>();

    static String key(
        String namespace,
        String binding,
        String route)
    {
        return String.format("zilla:%s-%s:brokers:%s", namespace, binding, route);
    }

    static String metaKey(
        String namespace,
        String binding,
        String route,
        String topic)
    {
        return String.format("zilla:%s-%s:meta:%s:%s", namespace, binding, route, topic);
    }

    static String encodePartitions(
        long expiresAt,
        Int2IntHashMap leadersByPartitionId)
    {
        final JsonObjectBuilder partitions = Json.createObjectBuilder();
        leadersByPartitionId.keySet().stream()
            .sorted()
            .forEach(id -> partitions.add(String.valueOf(id), leadersByPartitionId.get(id)));

        return Json.createObjectBuilder()
            .add(EXPIRES_AT_NAME, expiresAt)
            .add(PARTITIONS_NAME, partitions)
            .build()
            .toString();
    }

    static long decodePartitions(
        String value,
        Int2IntHashMap leadersByPartitionId)
    {
        try (JsonReader reader = Json.createReader(new StringReader(value)))
        {
            final JsonObject meta = reader.readObject();
            final JsonObject partitions = meta.getJsonObject(PARTITIONS_NAME);

            leadersByPartitionId.clear();
            partitions.forEach((id, leader) ->
                leadersByPartitionId.put(Integer.parseInt(id), ((JsonNumber) leader).intValue()));

            return meta.getJsonNumber(EXPIRES_AT_NAME).longValue();
        }
    }

    static String encode(
        Long2ObjectHashMap<KafkaServerConfig> serversByBrokerId)
    {
        final JsonObjectBuilder brokers = Json.createObjectBuilder();
        serversByBrokerId.keySet().stream()
            .sorted()
            .forEach(id -> brokers.add(String.valueOf(id), serversByBrokerId.get(id).toString()));

        return Json.createObjectBuilder().add(BROKERS_NAME, brokers).build().toString();
    }

    void attach(
        long bindingId,
        StoreHandler store,
        String key,
        KafkaClientRoute clientRoute)
    {
        if (!entriesByKey.containsKey(key))
        {
            final KafkaClientBrokersEntry entry = new KafkaClientBrokersEntry(bindingId, clientRoute);
            entriesByKey.put(key, entry);

            store.get(key, (k, value) -> entry.update(value));
            entry.watch = store.watch(key, (k, value) -> entry.update(value));
        }
    }

    void detach(
        long bindingId)
    {
        entriesByKey.values().removeIf(entry -> entry.close(bindingId));
    }

    private static final class KafkaClientBrokersEntry
    {
        private final long bindingId;
        private final KafkaClientRoute clientRoute;

        private Closeable watch;

        private KafkaClientBrokersEntry(
            long bindingId,
            KafkaClientRoute clientRoute)
        {
            this.bindingId = bindingId;
            this.clientRoute = clientRoute;
        }

        private void update(
            String value)
        {
            if (value != null)
            {
                try (JsonReader reader = Json.createReader(new StringReader(value)))
                {
                    final JsonObject brokers = reader.readObject().getJsonObject(BROKERS_NAME);

                    clientRoute.servers.clear();
                    brokers.forEach((id, server) ->
                        clientRoute.servers.put(Long.parseLong(id), decode(((JsonString) server).getString())));
                }
            }
        }

        private static KafkaServerConfig decode(
            String server)
        {
            final int separator = server.lastIndexOf(':');

            return KafkaServerConfig.builder()
                .host(server.substring(0, separator))
                .port(Integer.parseInt(server.substring(separator + 1)))
                .build();
        }

        private boolean close(
            long bindingId)
        {
            final boolean closing = this.bindingId == bindingId;

            if (closing && watch != null)
            {
                try
                {
                    watch.close();
                }
                catch (IOException ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }
            }

            return closing;
        }
    }
}
