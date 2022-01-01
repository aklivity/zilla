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

import java.util.function.LongFunction;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.budget.BudgetDebitor;

public final class KafkaMergedBudgetAccountant
{
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final KafkaMergedBudgetCreditor creditor;
    private final KafkaMergedBudgetDebitor debitor;
    private final Long2ObjectHashMap<KafkaMergedBudget> budgetsByMergedId;

    public KafkaMergedBudgetAccountant(
        AxleContext context)
    {
        this.supplyDebitor = context::supplyDebitor;
        this.budgetsByMergedId = new Long2ObjectHashMap<>();
        this.creditor = new KafkaMergedBudgetCreditor(budgetsByMergedId, context::supplyBudgetId, context.creditor());
        this.debitor = new KafkaMergedBudgetDebitor(budgetsByMergedId, context::supplyDebitor);
    }

    public MergedBudgetCreditor creditor()
    {
        return creditor;
    }

    public BudgetDebitor supplyDebitor(
        long debitorId)
    {
        return budgetsByMergedId.containsKey(debitorId) ? debitor : supplyDebitor.apply(debitorId);
    }
}
