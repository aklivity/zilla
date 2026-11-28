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
package io.aklivity.zilla.runtime.binding.kafka.internal.budget;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

import org.agrona.collections.IntArrayList;

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheProduceClaims;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheProduceIdleHead;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheSegment;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;

public final class KafkaCacheClientProduceBudget implements KafkaCacheProduceClaims
{
    private final BudgetCreditor creditor;
    private final BudgetDebitor debitor;
    private final long budgetId;
    private final long budgetIndex;
    private final List<KafkaCacheSegment> removedSegments;
    private final IntArrayList removedBytes;
    private final Set<KafkaCacheProduceIdleHead> idleHeads;

    public KafkaCacheClientProduceBudget(
        BudgetCreditor creditor,
        LongFunction<BudgetDebitor> supplyDebitor,
        long budgetId,
        long capacity)
    {
        this.creditor = creditor;
        this.debitor = supplyDebitor.apply(budgetId);
        this.budgetId = budgetId;
        this.budgetIndex = creditor.acquire(budgetId);
        this.removedSegments = new ArrayList<>();
        this.removedBytes = new IntArrayList();
        this.idleHeads = new LinkedHashSet<>();

        creditor.credit(0L, budgetIndex, capacity);
    }

    @Override
    public boolean claim(
        int bytes)
    {
        creditClosed();

        boolean claimed = claimBytes(bytes);

        final Iterator<KafkaCacheProduceIdleHead> candidates = idleHeads.iterator();
        while (!claimed && candidates.hasNext())
        {
            if (candidates.next().release())
            {
                candidates.remove();
                creditClosed();
                claimed = claimBytes(bytes);
            }
        }

        return claimed;
    }

    public void idle(
        KafkaCacheProduceIdleHead head)
    {
        idleHeads.add(head);
    }

    public void busy(
        KafkaCacheProduceIdleHead head)
    {
        idleHeads.remove(head);
    }

    @Override
    public void unclaim(
        int bytes)
    {
        creditor.credit(0L, budgetIndex, bytes);
    }

    @Override
    public void remove(
        KafkaCacheSegment segment,
        int bytes)
    {
        removedSegments.add(segment);
        removedBytes.addInt(bytes);

        creditClosed();
    }

    public void close()
    {
        idleHeads.clear();
        removedSegments.clear();
        removedBytes.clear();

        creditor.release(budgetIndex);
    }

    private boolean claimBytes(
        int bytes)
    {
        final long debitorIndex = debitor.acquire(budgetId, budgetId, traceId -> {});
        final int claimed = debitor.claim(0L, debitorIndex, budgetId, bytes, bytes, 0);
        debitor.release(debitorIndex, budgetId);

        return claimed == bytes;
    }

    public void creditClosed()
    {
        for (int index = removedSegments.size() - 1; index >= 0; index--)
        {
            if (removedSegments.get(index).closed())
            {
                creditor.credit(0L, budgetIndex, removedBytes.getInt(index));
                removedSegments.remove(index);
                removedBytes.removeAt(index);
            }
        }
    }
}
