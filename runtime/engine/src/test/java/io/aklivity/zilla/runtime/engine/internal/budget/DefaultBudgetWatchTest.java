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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout;

public class DefaultBudgetWatchTest
{
    @Test(timeout = 120_000)
    public void shouldNotMissCreditAddedWhileClaimFails() throws Exception
    {
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final Semaphore woken = new Semaphore(0);
        final Semaphore consumed = new Semaphore(0);

        final long budgetId = 1L;
        final long watcherId = 2L;
        final int credits = 100_000;

        try (DefaultBudgetCreditor creditor = new DefaultBudgetCreditor(0, layout, (traceId, id, watchers) -> woken.release());
             DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            final long creditorIndex = creditor.acquire(budgetId);
            final long debitorIndex = debitor.acquire(budgetId, watcherId, traceId -> woken.release());

            final Thread granter = new Thread(() ->
            {
                for (int i = 0; i < credits; i++)
                {
                    consumed.acquireUninterruptibly();

                    for (int spin = ThreadLocalRandom.current().nextInt(2_000); spin > 0; spin--)
                    {
                        Thread.onSpinWait();
                    }

                    creditor.credit(1L, creditorIndex, 1L);
                }
            });

            consumed.release();
            granter.start();

            for (int i = 0; i < credits; i++)
            {
                woken.drainPermits();

                int claimed = debitor.claim(debitorIndex, watcherId, 1, 1);
                while (claimed == 0)
                {
                    assertTrue("credit " + i + " was never signalled", woken.tryAcquire(5, SECONDS));
                    claimed = debitor.claim(debitorIndex, watcherId, 1, 1);
                }

                consumed.release();
            }

            granter.join();
        }
    }
}
