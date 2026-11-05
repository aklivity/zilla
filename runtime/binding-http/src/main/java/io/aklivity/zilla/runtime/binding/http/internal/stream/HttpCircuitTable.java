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

import org.agrona.collections.Long2ObjectHashMap;

final class HttpCircuitTable
{
    static final long ADMIT = 0L;
    static final long REJECT = -1L;

    static final int TRANSITION_OPENED = 0;
    static final int TRANSITION_HALF_OPENED = 1;
    static final int TRANSITION_CLOSED = 2;
    static final int TRANSITION_REJECTED = 3;

    static final int CAUSE_FAILURE_THRESHOLD = 0;
    static final int CAUSE_RETRY_AFTER = 1;
    static final int CAUSE_PROBE_FAILED = 2;
    static final int CAUSE_COOLDOWN_ELAPSED = 3;
    static final int CAUSE_PROBE_SUCCEEDED = 4;
    static final int CAUSE_RETRY_AFTER_ELAPSED = 5;
    static final int CAUSE_EVICTED = 6;
    static final int CAUSE_CIRCUIT_OPEN = 7;
    static final int CAUSE_PROBE_IN_FLIGHT = 8;

    private static final int STATE_FREE = 0;
    private static final int STATE_CLOSED = 1;
    private static final int STATE_OPEN_FAILURES = 2;
    private static final int STATE_OPEN_RETRY_AFTER = 3;
    private static final int STATE_HALF_OPEN = 4;

    @FunctionalInterface
    interface Listener
    {
        void onTransition(
            long traceId,
            long routedId,
            long originId,
            long circuitId,
            int transition,
            int cause,
            long remaining);
    }

    private final Long2ObjectHashMap<Long2ObjectHashMap<Long2ObjectHashMap<Circuit>>> circuitsByRoutedId =
        new Long2ObjectHashMap<>();
    private final Circuit[] pool;
    private final int[] free;
    private final int failureThreshold;
    private final long cooldown;
    private final long retryAfterMaximum;
    private final Listener listener;

    private int freeCount;
    private long touches;
    private long probes;

    HttpCircuitTable(
        int maximum,
        int failureThreshold,
        long cooldown,
        long retryAfterMaximum,
        Listener listener)
    {
        this.pool = new Circuit[maximum];
        this.free = new int[maximum];
        for (int index = 0; index < maximum; index++)
        {
            pool[index] = new Circuit(index);
            free[index] = maximum - 1 - index;
        }
        this.freeCount = maximum;
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
        this.retryAfterMaximum = retryAfterMaximum;
        this.listener = listener;
    }

    int size()
    {
        return pool.length - freeCount;
    }

    long admit(
        long traceId,
        long routedId,
        long originId,
        long circuitId,
        long now)
    {
        long result = ADMIT;

        final Circuit circuit = lookup(traceId, routedId, originId, circuitId, now);

        if (circuit != null)
        {
            circuit.touched = ++touches;

            switch (circuit.state)
            {
            case STATE_OPEN_FAILURES:
                result = now >= circuit.until
                    ? halfOpen(traceId, circuit, now)
                    : reject(traceId, circuit, CAUSE_CIRCUIT_OPEN, now);
                break;
            case STATE_OPEN_RETRY_AFTER:
                result = reject(traceId, circuit, CAUSE_RETRY_AFTER, now);
                break;
            case STATE_HALF_OPEN:
                result = now >= circuit.until
                    ? halfOpen(traceId, circuit, now)
                    : reject(traceId, circuit, CAUSE_PROBE_IN_FLIGHT, now);
                break;
            default:
                break;
            }
        }

        return result;
    }

    void succeed(
        long traceId,
        long routedId,
        long originId,
        long circuitId,
        long probe,
        long now)
    {
        final Circuit circuit = lookup(traceId, routedId, originId, circuitId, now);

        if (circuit != null)
        {
            if (circuit.state == STATE_CLOSED)
            {
                release(circuit);
            }
            else if (circuit.state == STATE_HALF_OPEN && circuit.probe == probe)
            {
                notify(traceId, circuit, TRANSITION_CLOSED, CAUSE_PROBE_SUCCEEDED, 0L);
                release(circuit);
            }
        }
    }

    void fail(
        long traceId,
        long routedId,
        long originId,
        long circuitId,
        long probe,
        long now)
    {
        final Circuit circuit = lookupOrAcquire(traceId, routedId, originId, circuitId, failureThreshold <= 1, now);

        if (circuit != null)
        {
            circuit.touched = ++touches;

            if (circuit.state == STATE_CLOSED)
            {
                circuit.failures++;

                if (circuit.failures >= failureThreshold)
                {
                    open(traceId, circuit, STATE_OPEN_FAILURES, CAUSE_FAILURE_THRESHOLD, cooldown, now);
                }
            }
            else if (circuit.state == STATE_HALF_OPEN && circuit.probe == probe)
            {
                open(traceId, circuit, STATE_OPEN_FAILURES, CAUSE_PROBE_FAILED, cooldown, now);
            }
        }
    }

    void retryAfter(
        long traceId,
        long routedId,
        long originId,
        long circuitId,
        long probe,
        long retryAfter,
        long now)
    {
        final Circuit circuit = lookupOrAcquire(traceId, routedId, originId, circuitId, true, now);

        if (circuit != null)
        {
            circuit.touched = ++touches;

            if (circuit.state == STATE_CLOSED || circuit.state == STATE_HALF_OPEN && circuit.probe == probe)
            {
                open(traceId, circuit, STATE_OPEN_RETRY_AFTER, CAUSE_RETRY_AFTER, Math.min(retryAfter, retryAfterMaximum), now);
            }
        }
    }

    private long halfOpen(
        long traceId,
        Circuit circuit,
        long now)
    {
        circuit.state = STATE_HALF_OPEN;
        circuit.until = now + cooldown;
        circuit.probe = ++probes;
        notify(traceId, circuit, TRANSITION_HALF_OPENED, CAUSE_COOLDOWN_ELAPSED, 0L);

        return circuit.probe;
    }

    private long reject(
        long traceId,
        Circuit circuit,
        int cause,
        long now)
    {
        notify(traceId, circuit, TRANSITION_REJECTED, cause, circuit.until - now);

        return REJECT;
    }

    private void open(
        long traceId,
        Circuit circuit,
        int state,
        int cause,
        long duration,
        long now)
    {
        circuit.state = state;
        circuit.until = now + duration;
        notify(traceId, circuit, TRANSITION_OPENED, cause, duration);
    }

    private void notify(
        long traceId,
        Circuit circuit,
        int transition,
        int cause,
        long remaining)
    {
        listener.onTransition(traceId, circuit.routedId, circuit.originId, circuit.circuitId, transition, cause, remaining);
    }

    private Circuit lookup(
        long traceId,
        long routedId,
        long originId,
        long circuitId,
        long now)
    {
        final Long2ObjectHashMap<Long2ObjectHashMap<Circuit>> circuitsByOriginId = circuitsByRoutedId.get(routedId);
        final Long2ObjectHashMap<Circuit> circuits = circuitsByOriginId != null ? circuitsByOriginId.get(originId) : null;
        Circuit circuit = circuits != null ? circuits.get(circuitId) : null;

        if (circuit != null && circuit.state == STATE_OPEN_RETRY_AFTER && now >= circuit.until)
        {
            notify(traceId, circuit, TRANSITION_CLOSED, CAUSE_RETRY_AFTER_ELAPSED, 0L);
            release(circuit);
            circuit = null;
        }

        return circuit;
    }

    private Circuit lookupOrAcquire(
        long traceId,
        long routedId,
        long originId,
        long circuitId,
        boolean opens,
        long now)
    {
        Circuit circuit = lookup(traceId, routedId, originId, circuitId, now);

        if (circuit == null)
        {
            circuit = acquire(traceId, routedId, originId, circuitId, opens, now);
        }

        return circuit;
    }

    private Circuit acquire(
        long traceId,
        long routedId,
        long originId,
        long circuitId,
        boolean opens,
        long now)
    {
        if (freeCount == 0)
        {
            final Circuit victim = victim(opens, now);

            if (victim != null)
            {
                evict(traceId, victim, now);
            }
        }

        Circuit circuit = null;

        if (freeCount > 0)
        {
            circuit = pool[free[--freeCount]];
            circuit.routedId = routedId;
            circuit.originId = originId;
            circuit.circuitId = circuitId;
            circuit.state = STATE_CLOSED;
            circuit.failures = 0;
            circuit.until = 0L;
            circuit.probe = 0L;
            circuit.touched = ++touches;

            Long2ObjectHashMap<Long2ObjectHashMap<Circuit>> circuitsByOriginId = circuitsByRoutedId.get(routedId);
            if (circuitsByOriginId == null)
            {
                circuitsByOriginId = new Long2ObjectHashMap<>();
                circuitsByRoutedId.put(routedId, circuitsByOriginId);
            }
            Long2ObjectHashMap<Circuit> circuits = circuitsByOriginId.get(originId);
            if (circuits == null)
            {
                circuits = new Long2ObjectHashMap<>();
                circuitsByOriginId.put(originId, circuits);
            }
            circuits.put(circuitId, circuit);
        }

        return circuit;
    }

    private Circuit victim(
        boolean opens,
        long now)
    {
        Circuit expired = null;
        Circuit closed = null;
        Circuit open = null;

        for (Circuit circuit : pool)
        {
            if (circuit.state == STATE_OPEN_RETRY_AFTER && now >= circuit.until)
            {
                expired = expired != null ? expired : circuit;
            }
            else if (circuit.state == STATE_CLOSED)
            {
                closed = closed == null || circuit.touched < closed.touched ? circuit : closed;
            }
            else
            {
                open = open == null || circuit.touched < open.touched ? circuit : open;
            }
        }

        return expired != null ? expired : closed != null ? closed : opens ? open : null;
    }

    private void evict(
        long traceId,
        Circuit circuit,
        long now)
    {
        if (circuit.state == STATE_OPEN_RETRY_AFTER && now >= circuit.until)
        {
            notify(traceId, circuit, TRANSITION_CLOSED, CAUSE_RETRY_AFTER_ELAPSED, 0L);
        }
        else if (circuit.state != STATE_CLOSED)
        {
            notify(traceId, circuit, TRANSITION_CLOSED, CAUSE_EVICTED, 0L);
        }

        release(circuit);
    }

    private void release(
        Circuit circuit)
    {
        circuitsByRoutedId.get(circuit.routedId).get(circuit.originId).remove(circuit.circuitId);
        circuit.state = STATE_FREE;
        free[freeCount++] = circuit.index;
    }

    private static final class Circuit
    {
        private final int index;

        private long routedId;
        private long originId;
        private long circuitId;
        private int state;
        private int failures;
        private long until;
        private long probe;
        private long touched;

        private Circuit(
            int index)
        {
            this.index = index;
        }
    }
}
