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
package io.aklivity.zilla.runtime.binding.kafka.internal.budget;

import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;

import org.agrona.collections.LongHashSet;

import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;

public final class KafkaCacheClientBudget
{
    public final long topicBudgetId;

    private final BudgetCreditor creditor;
    private final LongHashSet partitionIds;
    private final int topicBudgetMax;

    private long topicBudgetIndex = NO_CREDITOR_INDEX;

    private long topicBudgetExcess;

    public KafkaCacheClientBudget(
        BudgetCreditor creditor,
        long creditorId,
        int topicBudgetMax)
    {
        this.creditor = creditor;
        this.topicBudgetId = creditorId;
        this.topicBudgetMax = topicBudgetMax;
        this.partitionIds = new LongHashSet();
    }

    public long acquire(
        int partitionId)
    {
        if (partitionIds.isEmpty())
        {
            assert topicBudgetIndex == NO_CREDITOR_INDEX;
            this.topicBudgetIndex = creditor.acquire(topicBudgetId);
        }

        partitionIds.add(partitionId);
        assert topicBudgetIndex != NO_CREDITOR_INDEX;

        return partitionId;
    }

    public void release(
        long partitionId,
        int partitionBudget)
    {
        topicBudgetExcess -= partitionBudget;

        if (topicBudgetExcess < 0)
        {
            final long topicBudgetPrevious = creditor.credit(0L, topicBudgetIndex, topicBudgetExcess);
            assert topicBudgetPrevious + topicBudgetExcess >= 0L;
            topicBudgetExcess = 0;
        }

        partitionIds.remove(partitionId);

        if (partitionIds.isEmpty())
        {
            creditor.release(topicBudgetIndex);
            this.topicBudgetIndex = NO_CREDITOR_INDEX;
        }
    }

    public void credit(
        long traceId,
        long partitionId,
        long partitionCredit)
    {
        final long topicBudgetLimit = creditor.credit(0L, topicBudgetIndex, 0);
        final long newTopicBudgetMax = topicBudgetLimit + partitionCredit;
        final long newTopicBudget = Math.min(newTopicBudgetMax, topicBudgetMax);
        final long topicCredit = Math.max(newTopicBudget - topicBudgetLimit, 0L);

        if (topicCredit != 0L)
        {
            creditor.credit(traceId, topicBudgetIndex, topicCredit);
        }

        assert partitionCredit >= topicCredit;
        topicBudgetExcess += partitionCredit - topicCredit;
    }
}
