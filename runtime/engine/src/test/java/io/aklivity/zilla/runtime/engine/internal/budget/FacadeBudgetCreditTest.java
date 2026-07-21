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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;

public class FacadeBudgetCreditTest
{
    private static final long BUDGET_ID = 7L;
    private static final long BUDGET_INDEX = 3L;
    private static final long TRACE_ID = 13L;

    @Test
    public void shouldAcquireCreditorSlotOnConstruction()
    {
        final BudgetCreditor creditor = mock(BudgetCreditor.class);
        when(creditor.acquire(BUDGET_ID)).thenReturn(BUDGET_INDEX);

        new FacadeBudgetCredit(creditor, BUDGET_ID);

        verify(creditor).acquire(BUDGET_ID);
        verifyNoMoreInteractions(creditor);
    }

    @Test
    public void shouldCreditByteCapacity()
    {
        final BudgetCreditor creditor = mock(BudgetCreditor.class);
        when(creditor.acquire(BUDGET_ID)).thenReturn(BUDGET_INDEX);

        final FacadeBudgetCredit credit = new FacadeBudgetCredit(creditor, BUDGET_ID);
        credit.capacity(TRACE_ID, 1024);

        verify(creditor).credit(TRACE_ID, BUDGET_INDEX, 1024);
    }

    @Test
    public void shouldCreditOnlyByteAxisWhenMessagesGiven()
    {
        final BudgetCreditor creditor = mock(BudgetCreditor.class);
        when(creditor.acquire(BUDGET_ID)).thenReturn(BUDGET_INDEX);

        final FacadeBudgetCredit credit = new FacadeBudgetCredit(creditor, BUDGET_ID);
        credit.capacity(TRACE_ID, 4, 1024);

        verify(creditor).credit(TRACE_ID, BUDGET_INDEX, 1024);
    }

    @Test
    public void shouldReleaseCreditorSlotOnClose()
    {
        final BudgetCreditor creditor = mock(BudgetCreditor.class);
        when(creditor.acquire(BUDGET_ID)).thenReturn(BUDGET_INDEX);

        final FacadeBudgetCredit credit = new FacadeBudgetCredit(creditor, BUDGET_ID);
        credit.close();

        verify(creditor).release(BUDGET_INDEX);
    }

    @Test
    public void shouldReleaseCreditorSlotOnlyOnceWhenClosedRepeatedly()
    {
        final BudgetCreditor creditor = mock(BudgetCreditor.class);
        when(creditor.acquire(BUDGET_ID)).thenReturn(BUDGET_INDEX);

        final FacadeBudgetCredit credit = new FacadeBudgetCredit(creditor, BUDGET_ID);
        credit.close();
        credit.close();

        verify(creditor, times(1)).release(BUDGET_INDEX);
    }
}
