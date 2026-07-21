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
package io.aklivity.zilla.runtime.engine.internal.budget;

import io.aklivity.zilla.runtime.engine.budget.BudgetCredit;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;

/**
 * Phase 1 facade implementing {@link BudgetCredit} over today's {@link BudgetCreditor}.
 * <p>
 * Behavior-preserving: it owns the acquire/credit/release dance the binding does by hand
 * today and delegates the byte axis to {@link BudgetCreditor#credit}. The count-based axis
 * carried by {@link #capacity(long, int, int)} is not yet plumbed through the budget
 * machinery in Phase 1 — it rides each protocol's native frame, owned by the binding — so
 * only the byte axis is credited here.
 * </p>
 */
public final class FacadeBudgetCredit implements BudgetCredit
{
    private final BudgetCreditor creditor;
    private final long budgetIndex;

    private boolean released;

    public FacadeBudgetCredit(
        BudgetCreditor creditor,
        long budgetId)
    {
        this.creditor = creditor;
        this.budgetIndex = creditor.acquire(budgetId);
    }

    @Override
    public void capacity(
        long traceId,
        int bytes)
    {
        creditor.credit(traceId, budgetIndex, bytes);
    }

    @Override
    public void capacity(
        long traceId,
        int messages,
        int bytes)
    {
        creditor.credit(traceId, budgetIndex, bytes);
    }

    @Override
    public void close()
    {
        if (!released)
        {
            released = true;
            creditor.release(budgetIndex);
        }
    }
}
