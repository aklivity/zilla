/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.budget;

import static io.aklivity.zilla.runtime.engine.cog.budget.BudgetDebitor.NO_DEBITOR_INDEX;

import java.util.function.LongConsumer;
import java.util.function.LongFunction;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongArrayList;

import io.aklivity.zilla.runtime.engine.cog.budget.BudgetDebitor;

final class KafkaMergedBudget
{
    private final long budgetId;
    private final long mergedWatcherId;
    private final LongConsumer mergedFlusher;
    private final Long2ObjectHashMap<LongConsumer> flushers;
    private final LongArrayList watchers;

    private long debitorIndex = NO_DEBITOR_INDEX;
    private BudgetDebitor debitor;
    private long budget;
    private int watcherIndex;
    private long fragmenterId;

    KafkaMergedBudget(
        long budgetId,
        long mergedWatcherId)
    {
        this.budgetId = budgetId;
        this.mergedWatcherId = mergedWatcherId;
        this.mergedFlusher = this::flush;
        this.flushers = new Long2ObjectHashMap<>();
        this.watchers = new LongArrayList();
    }

    public long budgetId()
    {
        return budgetId;
    }

    long credit(
        long traceId,
        long credit)
    {
        final long budgetSnapshot = budget;

        budget += credit;

        flush(traceId);

        return budgetSnapshot;
    }

    int claim(
        long traceId,
        long watcherId,
        int minimum,
        int maximum,
        int deferred)
    {
        final int budgetMax = (int)(Math.min(budget, Integer.MAX_VALUE) & 0x7fff_ffff);

        int claimed = Math.min(budgetMax, maximum);

        if (fragmenterId != 0 && fragmenterId != watcherId)
        {
            claimed = 0;
            assert watchers.containsLong(fragmenterId);
        }
        else if (claimed < minimum)
        {
            claimed = 0;
        }

        if (claimed >= minimum && debitorIndex != NO_DEBITOR_INDEX)
        {
            claimed = debitor.claim(traceId, debitorIndex, mergedWatcherId, minimum, claimed, deferred);
        }

        assert claimed == 0 || minimum <= claimed && claimed <= maximum :
            String.format("%d == 0 || (%d <= %d && %d <= %d)", claimed, minimum, claimed, claimed, maximum);

        if (claimed >= minimum)
        {
            budget -= claimed;
        }

        final int watcherAt = watchers.indexOf(watcherId);
        if (claimed == maximum + deferred)
        {
            if (watcherAt != -1)
            {
                watchers.remove(watcherAt);
            }

            fragmenterId = 0;
        }
        else
        {
            if (watcherAt == -1)
            {
                watchers.addLong(watcherId);
            }

            if (fragmenterId == 0)
            {
                fragmenterId = watcherId;
            }
        }

        return claimed;
    }

    void attach(
        long watcherId,
        LongConsumer flusher,
        LongFunction<BudgetDebitor> supplyDebitor)
    {
        if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
        {
            debitor = supplyDebitor.apply(budgetId);
            debitorIndex = debitor.acquire(budgetId, mergedWatcherId, mergedFlusher);
        }

        flushers.put(watcherId, flusher);
    }

    void detach(
        long watcherId)
    {
        if (fragmenterId == watcherId)
        {
            fragmenterId = 0;
        }

        watchers.removeLong(watcherId);
        flushers.remove(watcherId);

        if (flushers.isEmpty() &&
            budgetId != 0L && debitorIndex != NO_DEBITOR_INDEX)
        {
            assert watchers.isEmpty();

            release();
        }
    }

    void release()
    {
        if (debitor != null && debitorIndex != NO_DEBITOR_INDEX)
        {
            debitor.release(debitorIndex, mergedWatcherId);
            debitor = null;
            debitorIndex = NO_DEBITOR_INDEX;
        }
    }

    private void flush(
        long traceId)
    {
        if (fragmenterId != 0)
        {
            assert watchers.containsLong(fragmenterId);
            final LongConsumer flusher = flushers.get(fragmenterId);
            flusher.accept(traceId);
        }

        if (fragmenterId == 0 && !watchers.isEmpty())
        {
            if (watcherIndex >= watchers.size())
            {
                watcherIndex = 0;
            }

            for (int index = watcherIndex; index < watchers.size(); index++)
            {
                final long watcherId = watchers.getLong(index);
                final LongConsumer flusher = flushers.get(watcherId);
                flusher.accept(traceId);
            }

            if (watcherIndex >= watchers.size())
            {
                watcherIndex = 0;
            }

            for (int index = 0; index < Math.min(watcherIndex, watchers.size()); index++)
            {
                final long watcherId = watchers.getLong(index);
                final LongConsumer flusher = flushers.get(watcherId);
                flusher.accept(traceId);
            }

            watcherIndex++;
        }
    }
}
