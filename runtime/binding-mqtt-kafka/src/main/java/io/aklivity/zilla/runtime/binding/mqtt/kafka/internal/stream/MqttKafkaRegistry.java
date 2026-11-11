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

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MqttKafkaRegistry
{
    static final int ATTEMPTS_MAX = 16;

    private static final long EPOCH_FIRST = 0L;

    private final StoreHandler store;

    MqttKafkaRegistry(
        StoreHandler store)
    {
        this.store = store;
    }

    void join(
        String key,
        LongConsumer joined,
        Runnable failed)
    {
        doJoin(key, 1, joined, failed);
    }

    void epoch(
        String key,
        LongConsumer found,
        Runnable failed)
    {
        store.get(key, (k, value) ->
        {
            final Subscribers subscribers = value != null ? Subscribers.parse(value) : new Subscribers(EPOCH_FIRST, 0L);

            if (subscribers != null)
            {
                found.accept(subscribers.epoch);
            }
            else
            {
                failed.run();
            }
        });
    }

    void leave(
        String key,
        Runnable completed)
    {
        doLeave(key, 1, completed);
    }

    private void doJoin(
        String key,
        int attempt,
        LongConsumer joined,
        Runnable failed)
    {
        store.get(key, (k, value) -> onJoinRead(key, value, attempt, joined, failed));
    }

    private void onJoinRead(
        String key,
        String value,
        int attempt,
        LongConsumer joined,
        Runnable failed)
    {
        if (value == null)
        {
            final Subscribers first = new Subscribers(EPOCH_FIRST, 1L);

            store.putIfAbsent(key, first.format(), null, existing ->
            {
                if (existing == null)
                {
                    joined.accept(first.epoch);
                }
                else
                {
                    onJoinLost(key, attempt, joined, failed);
                }
            });
        }
        else
        {
            final Subscribers subscribers = Subscribers.parse(value);

            if (subscribers != null)
            {
                final Subscribers next = subscribers.joined();

                store.replace(key, value, next.format(), null, replaced ->
                {
                    if (replaced)
                    {
                        joined.accept(next.epoch);
                    }
                    else
                    {
                        onJoinLost(key, attempt, joined, failed);
                    }
                });
            }
            else
            {
                failed.run();
            }
        }
    }

    private void onJoinLost(
        String key,
        int attempt,
        LongConsumer joined,
        Runnable failed)
    {
        if (attempt < ATTEMPTS_MAX)
        {
            doJoin(key, attempt + 1, joined, failed);
        }
        else
        {
            failed.run();
        }
    }

    private void doLeave(
        String key,
        int attempt,
        Runnable completed)
    {
        store.get(key, (k, value) -> onLeaveRead(key, value, attempt, completed));
    }

    private void onLeaveRead(
        String key,
        String value,
        int attempt,
        Runnable completed)
    {
        final Subscribers subscribers = value != null ? Subscribers.parse(value) : null;

        if (subscribers != null && subscribers.count > 0L)
        {
            store.replace(key, value, subscribers.left().format(), null, replaced ->
            {
                if (replaced || attempt == ATTEMPTS_MAX)
                {
                    completed.run();
                }
                else
                {
                    doLeave(key, attempt + 1, completed);
                }
            });
        }
        else
        {
            completed.run();
        }
    }

    private static final class Subscribers
    {
        private final long epoch;
        private final long count;

        private Subscribers(
            long epoch,
            long count)
        {
            this.epoch = epoch;
            this.count = count;
        }

        private Subscribers joined()
        {
            return count > 0L ? new Subscribers(epoch, count + 1L) : new Subscribers(epoch + 1L, 1L);
        }

        private Subscribers left()
        {
            return new Subscribers(epoch, Math.max(count - 1L, 0L));
        }

        private String format()
        {
            return String.format("%d:%d", epoch, count);
        }

        private static Subscribers parse(
            String value)
        {
            Subscribers parsed = null;

            final int separator = value.indexOf(':');

            if (separator > 0 && separator < value.length() - 1)
            {
                try
                {
                    final Subscribers candidate = new Subscribers(
                        Long.parseLong(value, 0, separator, 10),
                        Long.parseLong(value, separator + 1, value.length(), 10));

                    parsed = candidate.epoch >= 0L && candidate.count >= 0L && candidate.format().equals(value)
                        ? candidate
                        : null;
                }
                catch (NumberFormatException ex)
                {
                    parsed = null;
                }
            }

            return parsed;
        }
    }
}
