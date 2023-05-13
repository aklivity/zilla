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
package io.aklivity.zilla.runtime.engine.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.agrona.CloseHelper;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;

public final class Counters implements AutoCloseable
{
    private final CountersManager manager;
    private final ConcurrentMap<String, AtomicCounter> counters;
    private final ConcurrentMap<String, LongSupplier> readonlyCounters;
    private final Function<? super String, ? extends AtomicCounter> newCounter;

    public Counters(
        CountersManager manager)
    {
        this.manager = manager;
        this.counters = new ConcurrentHashMap<>();
        this.readonlyCounters = new ConcurrentHashMap<>();
        this.newCounter = this::newCounter;
    }

    @Override
    public void close() throws Exception
    {
        counters.values().forEach(CloseHelper::quietClose);
    }

    public AtomicCounter counter(
        String name)
    {
        return counters.computeIfAbsent(name, newCounter);
    }

    public LongSupplier readonlyCounter(
        String name)
    {
        LongSupplier readonlyCounter = readonlyCounters.get(name);
        if (readonlyCounter == null)
        {
            manager.forEach(this::populateReadonlyCounter);
            readonlyCounter = readonlyCounters.get(name);
        }

        if (readonlyCounter == null)
        {
            readonlyCounter = () -> 0L;
        }

        return readonlyCounter;
    }

    private AtomicCounter newCounter(
        String name)
    {
        return manager.newCounter(name);
    }

    private void populateReadonlyCounter(
        int counterId,
        String name)
    {
        readonlyCounters.computeIfAbsent(name, k -> () -> manager.getCounterValue(counterId));
    }
}
