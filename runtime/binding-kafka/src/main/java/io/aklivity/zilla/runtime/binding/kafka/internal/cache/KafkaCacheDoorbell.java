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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps at most one notification in flight towards a worker that reads shared cache state.
 *
 * A writer publishes its state with a volatile store, then calls {@link #ring(long)} and sends a notification
 * only when it returns true. The reader calls {@link #clear()} before it reads the published state, so any state
 * published while the notification was in flight is observed by that read.
 */
public final class KafkaCacheDoorbell
{
    private static final long IDLE = 0L;

    private final AtomicLong pending = new AtomicLong(IDLE);

    public boolean ring(
        long senderId)
    {
        return pending.compareAndSet(IDLE, senderId);
    }

    public void clear()
    {
        pending.set(IDLE);
    }

    public void release(
        long senderId)
    {
        pending.compareAndSet(senderId, IDLE);
    }
}
