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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.LongConsumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class TcpUsageTrackerTest
{
    private final EngineContext context = mock(EngineContext.class);
    private final LongConsumer recordUsage = mock(LongConsumer.class);
    private final LongConsumer recordRebalanced = mock(LongConsumer.class);

    {
        when(context.supplyUtilizationMetric()).thenReturn(recordUsage);
        when(context.supplyRebalancedMetric()).thenReturn(recordRebalanced);
    }

    @Test
    public void shouldRecordRebalanced()
    {
        final TcpUsageTracker usage = new TcpUsageTracker(new TcpConfiguration(new Configuration()), context);

        usage.rebalanced();
        usage.rebalanced();

        verify(recordRebalanced, times(2)).accept(1L);
        verify(recordUsage, never()).accept(anyLong());
    }

    @Test
    public void shouldNotRecordRebalancedWhenClaimedAndReleased()
    {
        final TcpUsageTracker usage = new TcpUsageTracker(new TcpConfiguration(new Configuration()), context);

        usage.claim();
        usage.released();

        verify(recordRebalanced, never()).accept(anyLong());
    }
}
