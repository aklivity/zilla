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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.function.LongConsumer;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.budget.BudgetFlusher;

public class FacadeBudgetDebitTest
{
    private static final long BUDGET_ID = 7L;
    private static final long WATCHER_ID = 11L;
    private static final long BUDGET_INDEX = 3L;
    private static final long TRACE_ID = 13L;

    @Test
    public void shouldAcquireDebitorSlotOnConstruction()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BUDGET_INDEX);

        new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);

        verify(debitor).acquire(eq(BUDGET_ID), eq(WATCHER_ID), any());
        verifyNoMoreInteractions(debitor);
    }

    @Test
    public void shouldAdaptFlusherToOnResume()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        final ArgumentCaptor<LongConsumer> flusher = ArgumentCaptor.forClass(LongConsumer.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), flusher.capture())).thenReturn(BUDGET_INDEX);

        new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        flusher.getValue().accept(TRACE_ID);

        verify(onResume).onResume(TRACE_ID);
        verifyNoMoreInteractions(onResume);
    }

    @Test
    public void shouldClaimWithPaddingFoldedPerFrame()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BUDGET_INDEX);
        when(debitor.claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 108, 1008, 0)).thenReturn(1008);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        debit.declare(TRACE_ID, 8, 64);
        final int granted = debit.claim(TRACE_ID, 100, 1000);

        assertEquals(1000, granted);
        verify(debitor).claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 108, 1008, 0);
    }

    @Test
    public void shouldComposeDeclaredMinimumWithClaimMinimum()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BUDGET_INDEX);
        when(debitor.claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 64, 1000, 0)).thenReturn(1000);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        debit.declare(TRACE_ID, 0, 64);
        final int granted = debit.claim(TRACE_ID, 16, 1000);

        assertEquals(1000, granted);
        verify(debitor).claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 64, 1000, 0);
    }

    @Test
    public void shouldReturnZeroWhenClaimParks()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BUDGET_INDEX);
        when(debitor.claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 108, 1008, 0)).thenReturn(0);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        debit.declare(TRACE_ID, 8, 0);
        final int granted = debit.claim(TRACE_ID, 100, 1000);

        assertEquals(0, granted);
    }

    @Test
    public void shouldReleaseDebitorSlotOnClose()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BUDGET_INDEX);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        debit.close();

        verify(debitor).release(BUDGET_INDEX, WATCHER_ID);
    }

    @Test
    public void shouldGrantMaximumWhenBudgetNotYetAcquired()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BudgetDebitor.NO_DEBITOR_INDEX);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        final int granted = debit.claim(TRACE_ID, 100, 1000);

        assertEquals(1000, granted);
        verify(debitor, never()).claim(anyLong(), anyLong(), anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void shouldReacquireOnClaimWhenBudgetBecomesAvailable()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any()))
            .thenReturn(BudgetDebitor.NO_DEBITOR_INDEX)
            .thenReturn(BUDGET_INDEX);
        when(debitor.claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 100, 1000, 0)).thenReturn(1000);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        final int grantedBefore = debit.claim(TRACE_ID, 100, 1000);
        final int grantedAfter = debit.claim(TRACE_ID, 100, 1000);

        assertEquals(1000, grantedBefore);
        assertEquals(1000, grantedAfter);
        verify(debitor, times(2)).acquire(eq(BUDGET_ID), eq(WATCHER_ID), any());
        verify(debitor, times(2)).claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 100, 1000, 0);
    }

    @Test
    public void shouldPassDeferredThrough()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BUDGET_INDEX);
        when(debitor.claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 100, 1000, 64)).thenReturn(1000);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        final int granted = debit.claim(TRACE_ID, 100, 1000, 64);

        assertEquals(1000, granted);
        verify(debitor).claim(TRACE_ID, BUDGET_INDEX, WATCHER_ID, 100, 1000, 64);
    }

    @Test
    public void shouldNotReleaseWhenNeverAcquired()
    {
        final BudgetDebitor debitor = mock(BudgetDebitor.class);
        final BudgetFlusher onResume = mock(BudgetFlusher.class);
        when(debitor.acquire(eq(BUDGET_ID), eq(WATCHER_ID), any())).thenReturn(BudgetDebitor.NO_DEBITOR_INDEX);

        final FacadeBudgetDebit debit = new FacadeBudgetDebit(debitor, BUDGET_ID, WATCHER_ID, onResume);
        debit.close();

        verify(debitor, never()).release(anyLong(), anyLong());
    }
}
