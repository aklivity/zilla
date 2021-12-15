/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.kafka.internal.cache;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class KafkaCacheObject<T extends KafkaCacheObject<T>> implements AutoCloseable
{
    private final AtomicInteger references;

    private volatile boolean closed;
    private volatile boolean closing;

    protected KafkaCacheObject()
    {
        this.references = new AtomicInteger(1);
    }

    public final T acquire()
    {
        if (closed)
        {
            return null;
        }
        references.incrementAndGet();
        return self();
    }

    public final void release()
    {
        final int count = references.decrementAndGet();
        assert count >= 0;
        if (count == 0)
        {
            assert !closed;
            closed = true;
            onClosed();
        }
    }

    @Override
    public final void close()
    {
        if (!closing)
        {
            closing = true;
            release();
        }
    }

    public final boolean closed()
    {
        return closed;
    }

    protected final int references()
    {
        return references.get();
    }

    protected abstract T self();

    protected abstract void onClosed();
}
