/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class AguiA2aLocalOwnerState implements AguiA2aOwnerState
{
    private static final int SWEEP_SIGNAL = 1;
    private static final long MIN_SWEEP_PERIOD_MILLIS = 1000L;

    private final Map<AguiA2aThreadKey, ContextEntry> contexts = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, HandleEntry> handles = new LinkedHashMap<>();
    private final Signaler signaler;
    private final LongSupplier clock;
    private final Supplier<String> handleIdSupplier;
    private final long threadTtlMillis;
    private final long handleTtlMillis;
    private final int capacity;
    private final long sweepPeriodMillis;

    private long sweepId = Signaler.NO_CANCEL_ID;

    public AguiA2aLocalOwnerState(
        Signaler signaler,
        LongSupplier clock,
        Supplier<String> handleIdSupplier,
        long threadTtlMillis,
        long handleTtlMillis,
        int capacity)
    {
        this.signaler = signaler;
        this.clock = clock;
        this.handleIdSupplier = handleIdSupplier;
        this.threadTtlMillis = threadTtlMillis;
        this.handleTtlMillis = handleTtlMillis;
        this.capacity = capacity;
        this.sweepPeriodMillis = max(MIN_SWEEP_PERIOD_MILLIS, min(threadTtlMillis, handleTtlMillis));
    }

    @Override
    public String lookupContext(
        AguiA2aThreadKey key)
    {
        final long now = clock.getAsLong();

        String contextId = null;

        final ContextEntry entry = contexts.get(key);
        if (entry != null)
        {
            if (entry.expiresAt > now)
            {
                entry.expiresAt = now + threadTtlMillis;
                contextId = entry.contextId;
            }
            else
            {
                contexts.remove(key);
            }
        }

        return contextId;
    }

    @Override
    public void rememberContext(
        AguiA2aThreadKey key,
        String contextId)
    {
        if (contextId != null)
        {
            final long now = clock.getAsLong();

            contexts.put(key, new ContextEntry(contextId, now + threadTtlMillis));
            evictContexts();
            scheduleSweep(now);
        }
    }

    @Override
    public String issueHandle(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String taskId,
        String subject)
    {
        final long now = clock.getAsLong();

        final String handle = handleIdSupplier.get();

        handles.put(handle, new HandleEntry(key, kind, taskId, subject, now + handleTtlMillis));
        evictHandles();
        scheduleSweep(now);

        return handle;
    }

    @Override
    public AguiA2aHandleTarget resolveTarget(
        AguiA2aThreadKey key,
        AguiA2aHandleKind kind,
        String handle)
    {
        final long now = clock.getAsLong();

        AguiA2aHandleTarget target = null;

        final HandleEntry entry = handle != null ? handles.get(handle) : null;
        if (entry != null)
        {
            if (entry.expiresAt <= now)
            {
                handles.remove(handle);
            }
            else if (entry.key.equals(key) && entry.kind == kind)
            {
                handles.remove(handle);
                target = new AguiA2aHandleTarget(entry.taskId, entry.subject);
            }
        }

        return target;
    }

    public void sweep()
    {
        final long now = clock.getAsLong();

        for (Iterator<ContextEntry> iterator = contexts.values().iterator(); iterator.hasNext(); )
        {
            if (iterator.next().expiresAt > now)
            {
                break;
            }
            iterator.remove();
        }

        for (Iterator<HandleEntry> iterator = handles.values().iterator(); iterator.hasNext(); )
        {
            if (iterator.next().expiresAt > now)
            {
                break;
            }
            iterator.remove();
        }
    }

    private void evictContexts()
    {
        for (Iterator<ContextEntry> iterator = contexts.values().iterator();
             contexts.size() > capacity && iterator.hasNext(); )
        {
            iterator.next();
            iterator.remove();
        }
    }

    private void evictHandles()
    {
        for (Iterator<HandleEntry> iterator = handles.values().iterator();
             handles.size() > capacity && iterator.hasNext(); )
        {
            iterator.next();
            iterator.remove();
        }
    }

    private void scheduleSweep(
        long now)
    {
        if (sweepId == Signaler.NO_CANCEL_ID)
        {
            sweepId = signaler.signalAt(now + sweepPeriodMillis, SWEEP_SIGNAL, this::onSweep);
        }
    }

    private void onSweep(
        int signalId)
    {
        sweepId = Signaler.NO_CANCEL_ID;
        sweep();

        if (!contexts.isEmpty() || !handles.isEmpty())
        {
            scheduleSweep(clock.getAsLong());
        }
    }

    private static final class ContextEntry
    {
        private final String contextId;

        private long expiresAt;

        private ContextEntry(
            String contextId,
            long expiresAt)
        {
            this.contextId = contextId;
            this.expiresAt = expiresAt;
        }
    }

    private static final class HandleEntry
    {
        private final AguiA2aThreadKey key;
        private final AguiA2aHandleKind kind;
        private final String taskId;
        private final String subject;
        private final long expiresAt;

        private HandleEntry(
            AguiA2aThreadKey key,
            AguiA2aHandleKind kind,
            String taskId,
            String subject,
            long expiresAt)
        {
            this.key = key;
            this.kind = kind;
            this.taskId = taskId;
            this.subject = subject;
            this.expiresAt = expiresAt;
        }
    }
}
