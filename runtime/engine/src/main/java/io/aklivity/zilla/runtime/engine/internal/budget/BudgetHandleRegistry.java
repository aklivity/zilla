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

import org.agrona.LangUtil;
import org.agrona.collections.Long2ObjectHashMap;

/**
 * Tracks budget handles supplied for a stream so the engine can release them automatically
 * when the stream terminates. A handle is registered against the stream it was supplied for
 * and closed from the engine's per-stream cleanup, removing the need for bindings to close
 * handles by hand. Closing is idempotent, so a handle that a binding also closes explicitly
 * is released exactly once.
 */
public final class BudgetHandleRegistry
{
    private final Long2ObjectHashMap<AutoCloseable> handlesByStreamId = new Long2ObjectHashMap<>();

    public void register(
        long streamId,
        AutoCloseable handle)
    {
        final AutoCloseable existing = handlesByStreamId.put(streamId, handle);
        if (existing != null)
        {
            handlesByStreamId.put(streamId, () ->
            {
                close(existing);
                close(handle);
            });
        }
    }

    public void release(
        long streamId)
    {
        final AutoCloseable handle = handlesByStreamId.remove(streamId);
        if (handle != null)
        {
            close(handle);
        }
    }

    public void releaseAll()
    {
        handlesByStreamId.values().forEach(BudgetHandleRegistry::close);
        handlesByStreamId.clear();
    }

    private static void close(
        AutoCloseable handle)
    {
        try
        {
            handle.close();
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
