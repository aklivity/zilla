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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class AguiA2aRetention
{
    private final BufferPool pool;

    private int slot = NO_SLOT;

    public AguiA2aRetention(
        BufferPool pool)
    {
        this.pool = pool;
    }

    public boolean acquire(
        long streamId)
    {
        if (slot == NO_SLOT)
        {
            slot = pool.acquire(streamId);
        }

        return slot != NO_SLOT;
    }

    public boolean acquired()
    {
        return slot != NO_SLOT;
    }

    public int capacity()
    {
        return pool.slotCapacity();
    }

    public MutableDirectBufferEx buffer()
    {
        return pool.buffer(slot);
    }

    public void release()
    {
        if (slot != NO_SLOT)
        {
            pool.release(slot);
            slot = NO_SLOT;
        }
    }
}
