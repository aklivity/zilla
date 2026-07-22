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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Paths;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.budget.BudgetCredit;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebit;
import io.aklivity.zilla.runtime.engine.budget.BudgetFlusher;
import io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout;

public class FacadeBudgetTest
{
    @Test
    public void shouldCreditClaimParkAndResumeThroughRealBudget() throws Exception
    {
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets-facade"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long watcherId = 2L;
        final long traceId = 3L;

        final BudgetFlusher onResume = mock(BudgetFlusher.class);

        try (DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            final DefaultBudgetCreditor creditor =
                new DefaultBudgetCreditor(0, layout, (trace, budget, watchers) -> debitor.flush(trace, budget));

            // the consumer acquires the shared budget; the producer then attaches to it
            final BudgetCredit credit = new FacadeBudgetCredit(creditor, budgetId);
            final BudgetDebit debit = new FacadeBudgetDebit(debitor, budgetId, watcherId, onResume);
            debit.declare(traceId, 0, 0);

            // grant 512 bytes of downstream capacity
            credit.capacity(traceId, 512);

            // a claim within the granted capacity succeeds
            final int granted = debit.claim(traceId, 0, 512);
            assertEquals(512, granted);

            // with no capacity left the next claim parks and registers a watcher
            final int parked = debit.claim(traceId, 1, 512);
            assertEquals(0, parked);
            verifyNoInteractions(onResume);

            // granting more capacity flushes the parked watcher
            credit.capacity(traceId, 512);
            verify(onResume).onResume(traceId);

            // the resumed claim now succeeds against the replenished capacity
            final int resumed = debit.claim(traceId, 0, 512);
            assertEquals(512, resumed);

            debit.close();
            credit.close();
        }
    }
}
