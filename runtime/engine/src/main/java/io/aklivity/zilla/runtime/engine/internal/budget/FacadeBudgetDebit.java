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

import io.aklivity.zilla.runtime.engine.budget.BudgetDebit;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.budget.BudgetFlusher;

/**
 * Phase 1 facade implementing {@link BudgetDebit} over today's {@link BudgetDebitor}.
 * <p>
 * Behavior-preserving: it owns the acquire/claim/release dance the binding does by hand
 * today, folds the declared per-frame padding into one claimed frame, composes the declared
 * minimum with each claim, and delegates to the existing six-argument
 * {@link BudgetDebitor#claim} with {@code deferred} of {@code 0}.
 * </p>
 */
public final class FacadeBudgetDebit implements BudgetDebit
{
    private final BudgetDebitor debitor;
    private final long watcherId;
    private final long budgetIndex;

    private int padding;
    private int minimum;

    public FacadeBudgetDebit(
        BudgetDebitor debitor,
        long budgetId,
        long watcherId,
        BudgetFlusher onResume)
    {
        this.debitor = debitor;
        this.watcherId = watcherId;
        this.budgetIndex = debitor.acquire(budgetId, watcherId, onResume::onResume);
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
        int maximum)
    {
        final int claimMinimum = Math.max(minimum, this.minimum) + padding;
        final int claimMaximum = maximum + padding;
        final int reserved = debitor.claim(traceId, budgetIndex, watcherId, claimMinimum, claimMaximum, 0);
        return reserved == 0 ? 0 : reserved - padding;
    }

    @Override
    public void close()
    {
        debitor.release(budgetIndex, watcherId);
    }
}
