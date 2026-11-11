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

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

abstract class MqttKafkaClientEntry<T>
{
    private final StoreHandler store;
    private final String key;
    private final Runnable onSettled;

    List<T> entries;
    private List<Runnable> loading;
    private List<Runnable> writing;
    private List<Runnable> pending;

    MqttKafkaClientEntry(
        StoreHandler store,
        String key,
        Runnable onSettled)
    {
        this.store = store;
        this.key = key;
        this.onSettled = onSettled;
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
        return loading == null && entries != null;
    }

    boolean idle()
    {
        return writing == null && pending == null;
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

    abstract List<T> decode(
        String value);

    abstract String encode(
        List<T> entries);

    void onDecoded()
    {
    }

    void settled()
    {
        onSettled.run();
    }

    private void onLoaded(
        String value)
    {
        final List<Runnable> continuations = loading;

        loading = null;
        entries = decode(value);

        if (entries != null)
        {
            onDecoded();
        }

        continuations.forEach(Runnable::run);
    }

    private void doWrite()
    {
        writing = pending;
        pending = null;

        if (entries.isEmpty())
        {
            store.delete(key, v -> onWriteCompleted());
        }
        else
        {
            store.put(key, encode(entries), null, v -> onWriteCompleted());
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
}
