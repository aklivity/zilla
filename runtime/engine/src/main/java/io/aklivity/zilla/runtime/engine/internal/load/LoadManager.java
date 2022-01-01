/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.load;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.AtomicBuffer;

public final class LoadManager
{
    private final AtomicBuffer buffer;
    private final Long2ObjectHashMap<LoadEntry> entries;

    private int nextOffset;

    public LoadManager(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;

        int sizeofEntry = LoadEntry.sizeofAligned();
        Long2ObjectHashMap<LoadEntry> entries = new Long2ObjectHashMap<>();
        while (nextOffset < buffer.capacity() - sizeofEntry)
        {
            LoadEntry entry = new LoadEntry(buffer, nextOffset);
            if (entry.namespacedId() == 0L)
            {
                break;
            }

            entries.put(entry.namespacedId(), entry);
            nextOffset += sizeofEntry;
        }
        this.entries = entries;
    }

    public LoadEntry entry(
        long namespacedId)
    {
        LoadEntry entry = entries.get(namespacedId);

        if (entry == null)
        {
            int sizeofEntry = LoadEntry.sizeofAligned();
            entry = new LoadEntry(buffer, nextOffset);
            entry.init(namespacedId);
            entries.put(namespacedId, entry);

            nextOffset += sizeofEntry;
            assert nextOffset < buffer.capacity();
        }

        return entry;
    }
}
