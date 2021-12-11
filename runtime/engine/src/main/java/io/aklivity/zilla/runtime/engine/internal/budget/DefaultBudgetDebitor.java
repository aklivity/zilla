/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.budget;

import static io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout.budgetIdOffset;
import static io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout.budgetRemainingOffset;
import static io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout.budgetWatchersOffset;
import static io.aklivity.zilla.runtime.engine.internal.stream.BudgetId.budgetMask;
import static org.agrona.BitUtil.isPowerOfTwo;

import java.util.function.LongConsumer;

import org.agrona.collections.Hashing;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.agrona.concurrent.AtomicBuffer;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.cog.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout;

public final class DefaultBudgetDebitor implements BudgetDebitor, AutoCloseable
{
    private final BudgetsLayout layout;
    private final AtomicBuffer storage;
    private final int entries;
    private final long budgetMask;
    private final long watcherMask;
    private final Long2LongHashMap budgetIdByIndex;
    private final Long2ObjectHashMap<Long2ObjectHashMap<LongConsumer>> flushersByBudgetId;
    private final Long2ObjectHashMap<LongHashSet> watcherIdsByBudgetId;

    public DefaultBudgetDebitor(
        int watcherIndex,
        int ownerIndex,
        BudgetsLayout layout)
    {
        this.layout = layout;
        this.storage = layout.buffer();

        final int entries = layout.entries();
        assert isPowerOfTwo(entries);
        this.entries = entries;
        this.budgetMask = budgetMask(ownerIndex);
        this.watcherMask = 1L << watcherIndex;
        this.budgetIdByIndex = new Long2LongHashMap(-1L);
        this.flushersByBudgetId = new Long2ObjectHashMap<>();
        this.watcherIdsByBudgetId = new Long2ObjectHashMap<>();
    }

    @Override
    public void close() throws Exception
    {
        layout.close();
    }

    @Override
    public long acquire(
        long budgetId,
        long watcherId,
        LongConsumer flusher)
    {
        assert (budgetId & budgetMask) == budgetMask;

        long budgetIndex = NO_DEBITOR_INDEX;

        final int entriesMask = entries - 1;
        int index = Hashing.hash(budgetId, entriesMask);
        for (int i = 0; i < entries; i++)
        {
            if (storage.getLong(budgetIdOffset(index)) == budgetId)
            {
                budgetIndex = budgetMask | (long) index;
                final Long2ObjectHashMap<LongConsumer> flushersByWatcherId =
                        flushersByBudgetId.computeIfAbsent(budgetId, id -> new Long2ObjectHashMap<>());
                flushersByWatcherId.put(watcherId, flusher);
                break;
            }

            index = ++index & entriesMask;
        }

        if (budgetIndex != NO_DEBITOR_INDEX)
        {
            budgetIdByIndex.put(budgetIndex, budgetId);

            if (EngineConfiguration.DEBUG_BUDGETS)
            {
                System.out.format("[%d] [0x%016x] [0x%016x] debitor acquired %d\n",
                        System.nanoTime(), watcherId, budgetId, budgetIndex);
            }
        }

        return budgetIndex;
    }

    @Override
    public int claim(
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum)
    {
        return claim(budgetIndex, watcherId, minimum, maximum, 0);
    }

    @Override
    public int claim(
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum,
        int deferred)
    {
        return claim(0L, budgetIndex, watcherId, minimum, maximum, deferred);
    }

    @Override
    public int claim(
        long traceId,
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum,
        int deferred)
    {
        assert (budgetIndex & budgetMask) == budgetMask;
        assert 0 <= minimum;
        assert minimum <= maximum;

        final int index = (int) (budgetIndex & ~budgetMask);
        final int budgetRemainingOffset = budgetRemainingOffset(index);

        long claimed = maximum;
        long previous = storage.getAndAddLong(budgetRemainingOffset, -claimed);
        if (previous - claimed < 0L)
        {
            if (previous >= minimum)
            {
                storage.getAndAddLong(budgetRemainingOffset, claimed - previous);
                claimed = previous;
            }
            else
            {
                storage.getAndAddLong(budgetRemainingOffset, claimed);
                claimed = 0L;
            }
        }

        if (EngineConfiguration.DEBUG_BUDGETS)
        {
            final long budgetId = budgetIdByIndex.get(budgetIndex);
            System.out.format("[%d] [0x%016x] [0x%016x] [0x%016x] claimed %d / %d @ %d => %d\n",
                    System.nanoTime(), traceId, watcherId, budgetId,
                    claimed, maximum, previous, previous - claimed);
        }

        if (claimed != maximum)
        {
            watch(index, watcherId);
        }
        else
        {
            unwatch(index, watcherId);
        }

        return (int) claimed;
    }

    @Override
    public void release(
        long budgetIndex,
        long watcherId)
    {
        assert (budgetIndex & budgetMask) == budgetMask;
        final int index = (int) (budgetIndex & ~budgetMask);

        unwatch(index, watcherId);

        final long budgetId = budgetIdByIndex.get(budgetIndex);

        if (EngineConfiguration.DEBUG_BUDGETS)
        {
            System.out.format("[%d] [0x%016x] [0x%016x] debitor release %d\n",
                    System.nanoTime(), watcherId, budgetId, budgetIndex);
        }

        final Long2ObjectHashMap<LongConsumer> flushersByWatcherId = flushersByBudgetId.get(budgetId);
        assert flushersByWatcherId != null;
        final LongConsumer flusher = flushersByWatcherId.remove(watcherId);
        assert flusher != null;
        if (flushersByWatcherId.isEmpty())
        {
            flushersByBudgetId.remove(budgetId);
            budgetIdByIndex.remove(budgetIndex);
        }
    }

    public void flush(
        long traceId,
        long budgetId)
    {
        assert (budgetId & budgetMask) == budgetMask;

        final LongHashSet watcherIds = watcherIdsByBudgetId.get(budgetId);
        final Long2ObjectHashMap<LongConsumer> flushersByWatcherId = flushersByBudgetId.get(budgetId);

        if (EngineConfiguration.DEBUG_BUDGETS)
        {
            System.out.format("[%d] [0x%016x] [0x%016x] flush %s %d\n",
                    System.nanoTime(), traceId, budgetId, watcherIds,
                    flushersByWatcherId != null ? flushersByWatcherId.size() : 0);
        }

        if (watcherIds != null && flushersByWatcherId != null)
        {
            for (long watcherId : watcherIds)
            {
                final LongConsumer flush = flushersByWatcherId.get(watcherId);
                if (flush != null)
                {
                    flush.accept(traceId);
                }
            }
        }
    }

    public long available(
        long budgetIndex)
    {
        assert (budgetIndex & budgetMask) == budgetMask;
        final int index = (int) (budgetIndex & ~budgetMask);
        return storage.getLongVolatile(budgetRemainingOffset(index));
    }

    public int acquired()
    {
        return budgetIdByIndex.size();
    }

    long watchers(
        long budgetIndex)
    {
        assert (budgetIndex & budgetMask) == budgetMask;
        final int index = (int) (budgetIndex & ~budgetMask);
        return storage.getLongVolatile(budgetWatchersOffset(index));
    }

    long budgetId(
        long budgetIndex)
    {
        assert (budgetIndex & budgetMask) == budgetMask;
        final int index = (int) (budgetIndex & ~budgetMask);
        return storage.getLongVolatile(budgetIdOffset(index));
    }

    private void watch(
        int index,
        long watcherId)
    {
        final long budgetId = storage.getLongVolatile(budgetIdOffset(index));
        final LongHashSet watcherIds = watcherIdsByBudgetId.computeIfAbsent(budgetId, id -> new LongHashSet());
        watcherIds.add(watcherId);

        final int watchersOffset = budgetWatchersOffset(index);
        for (long watchers = storage.getLongVolatile(watchersOffset);
                (watchers & watcherMask) == 0L &&
                !storage.compareAndSetLong(watchersOffset, watchers, watchers | watcherMask);
                watchers = storage.getLongVolatile(watchersOffset))
        {
            Thread.onSpinWait();
        }
    }

    private void unwatch(
        int index,
        long watcherId)
    {
        final long budgetId = storage.getLongVolatile(budgetIdOffset(index));
        final LongHashSet watcherIds = watcherIdsByBudgetId.get(budgetId);
        if (watcherIds != null)
        {
            watcherIds.remove(watcherId);

            if (watcherIds.isEmpty())
            {
                watcherIdsByBudgetId.remove(budgetId);

                final int watchersOffset = budgetWatchersOffset(index);
                for (long watchers = storage.getLongVolatile(watchersOffset);
                        (watchers & watcherMask) != 0L &&
                        !storage.compareAndSetLong(watchersOffset, watchers, watchers & ~watcherMask);
                        watchers = storage.getLongVolatile(watchersOffset))
                {
                    Thread.onSpinWait();
                }
            }
        }
    }
}
