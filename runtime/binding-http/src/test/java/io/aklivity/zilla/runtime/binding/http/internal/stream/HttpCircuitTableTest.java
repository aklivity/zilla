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
package io.aklivity.zilla.runtime.binding.http.internal.stream;

import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.ADMIT;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_CIRCUIT_OPEN;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_COOLDOWN_ELAPSED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_EVICTED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_FAILURE_THRESHOLD;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_PROBE_FAILED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_PROBE_IN_FLIGHT;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_PROBE_SUCCEEDED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_RETRY_AFTER;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.CAUSE_RETRY_AFTER_ELAPSED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.REJECT;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.TRANSITION_CLOSED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.TRANSITION_HALF_OPENED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.TRANSITION_OPENED;
import static io.aklivity.zilla.runtime.binding.http.internal.stream.HttpCircuitTable.TRANSITION_REJECTED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class HttpCircuitTableTest
{
    private static final long TRACE_ID = 7L;
    private static final long ROUTED_ID = 11L;
    private static final long ORIGIN_ID = 13L;
    private static final long CIRCUIT_ID = 42L;
    private static final int THRESHOLD = 3;
    private static final long COOLDOWN = 30_000L;
    private static final long RETRY_AFTER_MAXIMUM = 60_000L;

    private final List<long[]> transitions = new ArrayList<>();

    private HttpCircuitTable newTable(
        int maximum)
    {
        return new HttpCircuitTable(maximum, THRESHOLD, COOLDOWN, RETRY_AFTER_MAXIMUM,
            (traceId, routedId, originId, circuitId, transition, cause, remaining) ->
                transitions.add(new long[] {traceId, routedId, originId, circuitId, transition, cause, remaining}));
    }

    private long[] lastTransition()
    {
        return transitions.get(transitions.size() - 1);
    }

    private void fail(
        HttpCircuitTable table,
        long routedId,
        long originId,
        long circuitId,
        long now,
        int times)
    {
        for (int i = 0; i < times; i++)
        {
            table.fail(TRACE_ID, routedId, originId, circuitId, ADMIT, now);
        }
    }

    @Test
    public void shouldAdmitUntrackedCircuit()
    {
        HttpCircuitTable table = newTable(4);

        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L));
        assertEquals(0, table.size());
    }

    @Test
    public void shouldOpenCircuitAtFailureThreshold()
    {
        HttpCircuitTable table = newTable(4);

        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 100L, THRESHOLD - 1);
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 100L));
        assertTrue(transitions.isEmpty());

        table.fail(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, ADMIT, 100L);

        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID,
            TRANSITION_OPENED, CAUSE_FAILURE_THRESHOLD, COOLDOWN},
            lastTransition());
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 100L + COOLDOWN - 1L));
        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, TRANSITION_REJECTED, CAUSE_CIRCUIT_OPEN, 1L},
            lastTransition());
    }

    @Test
    public void shouldResetCounterOnSuccess()
    {
        HttpCircuitTable table = newTable(4);

        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD - 1);
        table.succeed(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, ADMIT, 0L);
        assertEquals(0, table.size());
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD - 1);

        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L));
    }

    @Test
    public void shouldAdmitOneProbeAfterCooldown()
    {
        HttpCircuitTable table = newTable(4);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);

        final long probe = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN);

        assertTrue(probe > 0L);
        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID,
            TRANSITION_HALF_OPENED, CAUSE_COOLDOWN_ELAPSED, 0L},
            lastTransition());
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 1L));
        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, TRANSITION_REJECTED, CAUSE_PROBE_IN_FLIGHT,
            COOLDOWN - 1L}, lastTransition());
    }

    @Test
    public void shouldCloseCircuitWhenProbeSucceeds()
    {
        HttpCircuitTable table = newTable(4);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);
        final long probe = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN);

        table.succeed(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, probe, COOLDOWN + 5L);

        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, TRANSITION_CLOSED, CAUSE_PROBE_SUCCEEDED, 0L},
            lastTransition());
        assertEquals(0, table.size());
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 5L));
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 5L, THRESHOLD - 1);
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 5L));
    }

    @Test
    public void shouldReopenCircuitWhenProbeFails()
    {
        HttpCircuitTable table = newTable(4);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);
        final long probe = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN);

        table.fail(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, probe, COOLDOWN + 5L);

        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID,
            TRANSITION_OPENED, CAUSE_PROBE_FAILED, COOLDOWN},
            lastTransition());
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 6L));
        assertTrue(table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 5L + COOLDOWN) > 0L);
    }

    @Test
    public void shouldIgnoreOutcomeOfStreamAdmittedBeforeCircuitOpened()
    {
        HttpCircuitTable table = newTable(4);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);
        final long probe = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN);
        assertNotEquals(ADMIT, probe);

        table.succeed(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, ADMIT, COOLDOWN + 1L);
        table.fail(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, ADMIT, COOLDOWN + 1L);

        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 2L));
        assertEquals(1, table.size());
    }

    @Test
    public void shouldAdmitAnotherProbeWhenProbeLeaseExpires()
    {
        HttpCircuitTable table = newTable(4);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);
        final long probe = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN);

        final long next = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + COOLDOWN);

        assertTrue(next > probe);
    }

    @Test
    public void shouldOpenCircuitUntilRetryAfterThenCloseWithoutProbe()
    {
        HttpCircuitTable table = newTable(4);

        table.retryAfter(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, ADMIT, 5_000L, 1_000L);

        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, TRANSITION_OPENED, CAUSE_RETRY_AFTER, 5_000L},
            lastTransition());
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 5_999L));
        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, TRANSITION_REJECTED, CAUSE_RETRY_AFTER, 1L},
            lastTransition());
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 6_000L));
        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID,
            TRANSITION_CLOSED, CAUSE_RETRY_AFTER_ELAPSED, 0L},
            lastTransition());
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 6_000L));
        assertEquals(0, table.size());
    }

    @Test
    public void shouldClampRetryAfterToMaximum()
    {
        HttpCircuitTable table = newTable(4);

        table.retryAfter(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, ADMIT, 3_600_000L, 0L);

        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, TRANSITION_OPENED, CAUSE_RETRY_AFTER,
            RETRY_AFTER_MAXIMUM}, lastTransition());
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, RETRY_AFTER_MAXIMUM - 1L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, RETRY_AFTER_MAXIMUM));
    }

    @Test
    public void shouldOpenCircuitFromRetryAfterOfFailingProbe()
    {
        HttpCircuitTable table = newTable(4);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);
        final long probe = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN);

        table.retryAfter(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, probe, 2_000L, COOLDOWN + 1L);

        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 2_000L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 2_001L));
    }

    @Test
    public void shouldIsolateCircuitsByCircuitIdRoutedIdAndOriginId()
    {
        HttpCircuitTable table = newTable(4);

        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);

        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 1L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID + 1L, 1L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID + 1L, ORIGIN_ID, CIRCUIT_ID, 1L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 1L));
    }

    @Test
    public void shouldNotCountFailuresAcrossOrigins()
    {
        HttpCircuitTable table = newTable(4);

        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD - 1);
        fail(table, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 0L, 1);

        assertEquals(2, table.size());
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 0L));
    }

    @Test
    public void shouldNotRejectOtherOriginWhenRetryAfterOpensCircuit()
    {
        HttpCircuitTable table = newTable(4);

        table.retryAfter(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, ADMIT, 5_000L, 0L);

        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 1L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 1L));
    }

    @Test
    public void shouldLeaseProbeToOriginThatOpenedCircuit()
    {
        HttpCircuitTable table = newTable(4);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, THRESHOLD);
        fail(table, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 0L, THRESHOLD);

        final long probe = table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN);
        table.succeed(TRACE_ID, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, probe, COOLDOWN + 1L);

        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 1L));
        table.succeed(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, probe, COOLDOWN + 2L);
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, COOLDOWN + 2L));
    }

    @Test
    public void shouldEvictDistinctOriginsIndependently()
    {
        HttpCircuitTable table = newTable(2);
        fail(table, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L, 1);
        fail(table, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 0L, 1);

        fail(table, ROUTED_ID, ORIGIN_ID + 2L, CIRCUIT_ID, 0L, 1);

        assertEquals(2, table.size());
        fail(table, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 0L, THRESHOLD - 1);
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID + 1L, CIRCUIT_ID, 0L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, CIRCUIT_ID, 0L));
    }

    @Test
    public void shouldEvictExpiredCircuitFirst()
    {
        HttpCircuitTable table = newTable(3);
        table.retryAfter(TRACE_ID, ROUTED_ID, ORIGIN_ID, 1L, ADMIT, 1_000L, 0L);
        fail(table, ROUTED_ID, ORIGIN_ID, 2L, 0L, 1);
        fail(table, ROUTED_ID, ORIGIN_ID, 3L, 0L, THRESHOLD);

        fail(table, ROUTED_ID, ORIGIN_ID, 4L, 2_000L, 1);

        assertEquals(3, table.size());
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 3L, 2_000L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 1L, 2_000L));
        fail(table, ROUTED_ID, ORIGIN_ID, 2L, 2_000L, THRESHOLD - 1);
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 2L, 2_000L));
    }

    @Test
    public void shouldEvictLeastRecentlyUsedClosedCircuitBeforeOpenCircuit()
    {
        HttpCircuitTable table = newTable(3);
        fail(table, ROUTED_ID, ORIGIN_ID, 1L, 0L, THRESHOLD);
        fail(table, ROUTED_ID, ORIGIN_ID, 2L, 0L, 1);
        fail(table, ROUTED_ID, ORIGIN_ID, 3L, 0L, 1);
        table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 2L, 0L);

        fail(table, ROUTED_ID, ORIGIN_ID, 4L, 0L, 1);

        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 1L, 0L));
        fail(table, ROUTED_ID, ORIGIN_ID, 2L, 0L, THRESHOLD - 1);
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 2L, 0L));
        fail(table, ROUTED_ID, ORIGIN_ID, 3L, 0L, THRESHOLD - 1);
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 3L, 0L));
    }

    @Test
    public void shouldNotTrackClosedCircuitWhenTableIsFullOfOpenCircuits()
    {
        HttpCircuitTable table = newTable(2);
        fail(table, ROUTED_ID, ORIGIN_ID, 1L, 0L, THRESHOLD);
        fail(table, ROUTED_ID, ORIGIN_ID, 2L, 0L, THRESHOLD);

        fail(table, ROUTED_ID, ORIGIN_ID, 3L, 0L, THRESHOLD - 1);

        assertEquals(2, table.size());
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 1L, 0L));
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 2L, 0L));
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 3L, 0L));
    }

    @Test
    public void shouldEvictLeastRecentlyUsedOpenCircuitForCircuitThatOpens()
    {
        HttpCircuitTable table = newTable(2);
        fail(table, ROUTED_ID, ORIGIN_ID, 1L, 0L, THRESHOLD);
        fail(table, ROUTED_ID, ORIGIN_ID, 2L, 0L, THRESHOLD);
        table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 1L, 0L);

        table.retryAfter(TRACE_ID, ROUTED_ID, ORIGIN_ID, 3L, ADMIT, 1_000L, 0L);

        assertArrayEquals(new long[] {TRACE_ID, ROUTED_ID, ORIGIN_ID, 2L, TRANSITION_CLOSED, CAUSE_EVICTED, 0L}, transitions.get(
            transitions.size() - 2));
        assertEquals(2, table.size());
        assertEquals(ADMIT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 2L, 0L));
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 1L, 0L));
        assertEquals(REJECT, table.admit(TRACE_ID, ROUTED_ID, ORIGIN_ID, 3L, 0L));
    }
}
