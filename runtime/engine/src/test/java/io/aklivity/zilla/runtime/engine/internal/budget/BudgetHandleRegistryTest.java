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

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.budget.BudgetDebit;

public class BudgetHandleRegistryTest
{
    private static final long STREAM_ID = 17L;
    private static final long OTHER_STREAM_ID = 19L;

    @Test
    public void shouldCloseHandleOnStreamCleanup() throws Exception
    {
        final BudgetHandleRegistry registry = new BudgetHandleRegistry();
        final BudgetDebit handle = mock(BudgetDebit.class);

        registry.register(STREAM_ID, handle);
        registry.release(STREAM_ID);

        verify(handle).close();
    }

    @Test
    public void shouldCloseHandleOnceWhenStreamCleanedUpRepeatedly() throws Exception
    {
        final BudgetHandleRegistry registry = new BudgetHandleRegistry();
        final BudgetDebit handle = mock(BudgetDebit.class);

        registry.register(STREAM_ID, handle);
        registry.release(STREAM_ID);
        registry.release(STREAM_ID);

        verify(handle, times(1)).close();
    }

    @Test
    public void shouldIgnoreCleanupForUnknownStream() throws Exception
    {
        final BudgetHandleRegistry registry = new BudgetHandleRegistry();
        final BudgetDebit handle = mock(BudgetDebit.class);

        registry.register(STREAM_ID, handle);
        registry.release(OTHER_STREAM_ID);

        verify(handle, times(0)).close();
    }

    @Test
    public void shouldCloseEveryRemainingHandleOnReleaseAll() throws Exception
    {
        final BudgetHandleRegistry registry = new BudgetHandleRegistry();
        final BudgetDebit first = mock(BudgetDebit.class);
        final BudgetDebit second = mock(BudgetDebit.class);

        registry.register(STREAM_ID, first);
        registry.register(OTHER_STREAM_ID, second);
        registry.releaseAll();

        verify(first).close();
        verify(second).close();
    }

    @Test
    public void shouldNotCloseAlreadyReleasedHandleOnReleaseAll() throws Exception
    {
        final BudgetHandleRegistry registry = new BudgetHandleRegistry();
        final BudgetDebit handle = mock(BudgetDebit.class);

        registry.register(STREAM_ID, handle);
        registry.release(STREAM_ID);
        registry.releaseAll();

        verify(handle, times(1)).close();
    }

    @Test
    public void shouldCloseAllHandlesRegisteredForSameStream() throws Exception
    {
        final BudgetHandleRegistry registry = new BudgetHandleRegistry();
        final BudgetDebit first = mock(BudgetDebit.class);
        final BudgetDebit second = mock(BudgetDebit.class);

        registry.register(STREAM_ID, first);
        registry.register(STREAM_ID, second);
        registry.release(STREAM_ID);

        verify(first).close();
        verify(second).close();
    }
}
