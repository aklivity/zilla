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
package io.aklivity.zilla.runtime.engine.internal.budget;

import static io.aklivity.zilla.runtime.engine.cog.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout.budgetIdOffset;
import static io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout.budgetRemainingOffset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.file.Paths;
import java.util.function.LongConsumer;

import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout;

public class DefaultBudgetDebitorTest
{
    @Test
    public void shouldAcquire() throws Exception
    {
        final LongConsumer flusher = Mockito.mock(LongConsumer.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long watcherId = 2L;
        final int creditorLocalIndex = 1;

        try (DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            layout.buffer().putLongVolatile(budgetIdOffset(creditorLocalIndex), budgetId);

            final long debitorIndex = debitor.acquire(budgetId, watcherId, flusher);

            assertEquals(1, debitor.acquired());
            assertNotEquals(NO_DEBITOR_INDEX, debitorIndex);
            assertEquals(budgetId, debitor.budgetId(debitorIndex));
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldAcquireAndRelease() throws Exception
    {
        final LongConsumer flusher = Mockito.mock(LongConsumer.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long watcherId = 2L;
        final int creditorLocalIndex = 1;

        try (DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            layout.buffer().putLongVolatile(budgetIdOffset(creditorLocalIndex), budgetId);

            final long debitorIndex = debitor.acquire(budgetId, watcherId, flusher);
            debitor.release(debitorIndex, watcherId);

            assertEquals(0, debitor.acquired());
            assertEquals(budgetId, debitor.budgetId(debitorIndex));
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldClaimWithSurplusBudgetThenFlush() throws Exception
    {
        final LongConsumer flusher = Mockito.mock(LongConsumer.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long watcherId = 2L;
        final long traceId = 3L;
        final int creditorLocalIndex = 1;

        try (DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            layout.buffer().putLongVolatile(budgetIdOffset(creditorLocalIndex), budgetId);
            layout.buffer().putLongVolatile(budgetRemainingOffset(creditorLocalIndex), 4096L);

            final long debitorIndex = debitor.acquire(budgetId, watcherId, flusher);
            final int claimed = debitor.claim(debitorIndex, watcherId, 256, 1024);
            final long available = debitor.available(debitorIndex);
            final long watchers = debitor.watchers(debitorIndex);

            debitor.flush(traceId, budgetId);

            assertEquals(1024, claimed);
            assertEquals(3072L, available);
            assertEquals(0L, watchers);
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldClaimWithSufficientBudgetThenFlush() throws Exception
    {
        final LongConsumer flusher = Mockito.mock(LongConsumer.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long watcherId = 2L;
        final long traceId = 3L;
        final int creditorLocalIndex = 1;

        try (DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            layout.buffer().putLongVolatile(budgetIdOffset(creditorLocalIndex), budgetId);
            layout.buffer().putLongVolatile(budgetRemainingOffset(creditorLocalIndex), 1024L);

            final long debitorIndex = debitor.acquire(budgetId, watcherId, flusher);
            final int claimed = debitor.claim(debitorIndex, watcherId, 256, 1024);
            final long available = debitor.available(debitorIndex);
            final long watchers = debitor.watchers(debitorIndex);

            debitor.flush(traceId, budgetId);

            assertEquals(1024, claimed);
            assertEquals(0L, available);
            assertEquals(0L, watchers);
        }

        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldClaimWithNecessaryBudgetThenFlush() throws Exception
    {
        final LongConsumer flusher = Mockito.mock(LongConsumer.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long watcherId = 2L;
        final long traceId = 3L;
        final int creditorLocalIndex = 1;

        try (DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            layout.buffer().putLongVolatile(budgetIdOffset(creditorLocalIndex), budgetId);
            layout.buffer().putLongVolatile(budgetRemainingOffset(creditorLocalIndex), 256L);

            final long debitorIndex = debitor.acquire(budgetId, watcherId, flusher);
            final int claimed = debitor.claim(debitorIndex, watcherId, 256, 1024);
            final long available = debitor.available(debitorIndex);
            final long watchers = debitor.watchers(debitorIndex);

            debitor.flush(traceId, budgetId);

            assertEquals(256, claimed);
            assertEquals(0L, available);
            assertEquals(1L << 1, watchers);
        }

        verify(flusher).accept(traceId);
        verifyNoMoreInteractions(flusher);
    }

    @Test
    public void shouldClaimWithInsufficientBudget() throws Exception
    {
        final LongConsumer flusher = Mockito.mock(LongConsumer.class);
        final BudgetsLayout layout = new BudgetsLayout.Builder()
            .owner(true)
            .path(Paths.get("target/zilla-itests/budgets0"))
            .capacity(1024)
            .build();

        final long budgetId = 1L;
        final long watcherId = 2L;
        final long traceId = 3L;
        final int creditorLocalIndex = 1;

        try (DefaultBudgetDebitor debitor = new DefaultBudgetDebitor(1, 0, layout))
        {
            layout.buffer().putLongVolatile(budgetIdOffset(creditorLocalIndex), budgetId);
            layout.buffer().putLongVolatile(budgetRemainingOffset(creditorLocalIndex), 256L);

            final long debitorIndex = debitor.acquire(budgetId, watcherId, flusher);
            final int claimed = debitor.claim(debitorIndex, watcherId, 512, 1024);
            final long available = debitor.available(debitorIndex);
            final long watchers = debitor.watchers(debitorIndex);

            debitor.flush(traceId, budgetId);

            assertEquals(0, claimed);
            assertEquals(256L, available);
            assertEquals(1L << 1, watchers);
        }

        verify(flusher).accept(traceId);
        verifyNoMoreInteractions(flusher);
    }
}
