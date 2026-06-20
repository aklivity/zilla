/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.budget.BudgetDebit;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.budget.BudgetFlusher;

/**
 * Phase 1 facade implementing {@link BudgetDebit} over today's {@link BudgetDebitor}.
 * <p>
 * Behavior-preserving: it owns the acquire/claim/release dance the binding does by hand
 * today, folds the declared per-frame padding into one claimed frame, composes the declared
 * minimum with each claim, and delegates to the existing six-argument
 * {@link BudgetDebitor#claim}, passing through the per-claim {@code deferred} bytes.
 * </p>
 * <p>
 * {@link BudgetDebitor#acquire} returns {@link BudgetDebitor#NO_DEBITOR_INDEX} when the shared
 * budget is not yet present in the debitor's storage. The hand-coded bindings handled this by
 * guarding the claim on the acquired index and re-acquiring on each subsequent window until the
 * budget appeared. This facade reproduces that: it re-attempts {@code acquire} on each
 * {@link #claim} while still unacquired and grants the full requested {@code maximum} (no budget
 * constraint) until the budget is present, then claims normally. A handle that never acquired is
 * never released.
 * </p>
 */
public final class FacadeBudgetDebit implements BudgetDebit
{
    private final BudgetDebitor debitor;
    private final long budgetId;
    private final long watcherId;
    private final LongConsumer flusher;

    private long budgetIndex;
    private int padding;
    private int minimum;
    private boolean released;

    public FacadeBudgetDebit(
        BudgetDebitor debitor,
        long budgetId,
        long watcherId,
        BudgetFlusher onResume)
    {
        this.debitor = debitor;
        this.budgetId = budgetId;
        this.watcherId = watcherId;
        this.flusher = onResume::onResume;
        this.budgetIndex = debitor.acquire(budgetId, watcherId, flusher);
    }

    @Override
    public void declare(
        long traceId,
        int padding,
        int minimum)
    {
        this.padding = padding;
        this.minimum = minimum;
    }

    @Override
    public int claim(
        long traceId,
        int minimum,
        int maximum,
        int deferred)
    {
        if (budgetIndex == NO_DEBITOR_INDEX)
        {
            budgetIndex = debitor.acquire(budgetId, watcherId, flusher);
        }

        final int claimed;
        if (budgetIndex == NO_DEBITOR_INDEX)
        {
            claimed = maximum;
        }
        else
        {
            final int claimMinimum = Math.max(minimum, this.minimum) + padding;
            final int claimMaximum = maximum + padding;
            final int reserved = debitor.claim(traceId, budgetIndex, watcherId, claimMinimum, claimMaximum, deferred);
            claimed = reserved == 0 ? 0 : reserved - padding;
        }

        return claimed;
    }

    @Override
    public boolean available()
    {
        return budgetIndex != NO_DEBITOR_INDEX;
    }

    @Override
    public void close()
    {
        if (!released)
        {
            released = true;
            if (budgetIndex != NO_DEBITOR_INDEX)
            {
                debitor.release(budgetIndex, watcherId);
                budgetIndex = NO_DEBITOR_INDEX;
            }
        }
    }
}
