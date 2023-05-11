/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.file.Paths;

import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.engine.internal.budget.DefaultBudgetCreditor.BudgetFlusher;
import io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout;

public class DefaultBudgetCreditorTest
{
    @Test
    public void shouldAcquire() throws Exception
    {
        final BudgetFlusher flusher = Mockito.mock(BudgetFlusher.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        try (DefaultBudgetCreditor creditor = new DefaultBudgetCreditor(0, layout, flusher))
        {
            final long budgetId = 1L;
            final long creditorIndex = creditor.acquire(budgetId);

            assertEquals(1, creditor.acquired());
            assertNotEquals(NO_CREDITOR_INDEX, creditorIndex);
            assertEquals(budgetId, creditor.budgetId(creditorIndex));
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldAcquireAndRelease() throws Exception
    {
        final BudgetFlusher flusher = Mockito.mock(BudgetFlusher.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        try (DefaultBudgetCreditor creditor = new DefaultBudgetCreditor(0, layout, flusher))
        {
            final long budgetId = 1L;
            final long creditorIndex = creditor.acquire(budgetId);
            creditor.release(creditorIndex);

            assertEquals(0, creditor.acquired());
            assertEquals(0L, creditor.budgetId(creditorIndex));
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldCreditByIndex() throws Exception
    {
        final BudgetFlusher flusher = Mockito.mock(BudgetFlusher.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        try (DefaultBudgetCreditor creditor = new DefaultBudgetCreditor(0, layout, flusher))
        {
            final long budgetId = 1L;
            final long traceId = 1L;
            final long creditorIndex = creditor.acquire(budgetId);

            creditor.credit(traceId, creditorIndex, 1024L);
            assertEquals(1024L, creditor.available(creditorIndex));
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldCreditById() throws Exception
    {
        final BudgetFlusher flusher = Mockito.mock(BudgetFlusher.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        try (DefaultBudgetCreditor creditor = new DefaultBudgetCreditor(0, layout, flusher))
        {
            final long budgetId = 1L;
            final long traceId = 1L;
            final long creditorIndex = creditor.acquire(budgetId);

            creditor.creditById(traceId, budgetId, 1024L);
            assertEquals(1024L, creditor.available(creditorIndex));
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldCreditByIndexWithWatchers() throws Exception
    {
        final BudgetFlusher flusher = Mockito.mock(BudgetFlusher.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long traceId = 1L;

        try (DefaultBudgetCreditor creditor = new DefaultBudgetCreditor(0, layout, flusher))
        {
            final long creditorIndex = creditor.acquire(budgetId);
            creditor.watchers(creditorIndex, 0x01L);
            creditor.credit(traceId, creditorIndex, 1024L);

            assertEquals(1024L, creditor.available(creditorIndex));
        }

        verify(flusher).flush(traceId, budgetId, 0x01L);
        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldCreditByIdWithWatchers() throws Exception
    {
        final BudgetFlusher flusher = Mockito.mock(BudgetFlusher.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long traceId = 1L;

        try (DefaultBudgetCreditor creditor = new DefaultBudgetCreditor(0, layout, flusher))
        {
            final long creditorIndex = creditor.acquire(budgetId);
            creditor.watchers(creditorIndex, 0x01L);
            creditor.creditById(traceId, budgetId, 1024L);

            assertEquals(1024L, creditor.available(creditorIndex));
        }

        verify(flusher).flush(traceId, budgetId, 0x01L);
        verifyNoMoreInteractions(flusher);
    }
}
