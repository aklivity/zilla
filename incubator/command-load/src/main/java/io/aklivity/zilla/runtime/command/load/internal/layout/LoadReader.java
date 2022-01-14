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
package io.aklivity.zilla.runtime.command.load.internal.layout;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.AtomicBuffer;

import io.aklivity.zilla.runtime.command.load.internal.types.LoadSnapshot;

public final class LoadReader
{
    private final Long2ObjectHashMap<LoadSnapshot> entries;

    private int nextOffset;

    public LoadReader(
        AtomicBuffer buffer)
    {
        int sizeofEntry = LoadSnapshot.sizeofAligned();
        Long2ObjectHashMap<LoadSnapshot> entries = new Long2ObjectHashMap<>();
        while (nextOffset < buffer.capacity() - sizeofEntry)
        {
            LoadSnapshot entry = new LoadSnapshot(buffer, nextOffset);
            long namespacedId =
                    (long) entry.namespaceId() << Integer.SIZE |
                    (long) entry.entryId() << 0;
            if (namespacedId == 0L)
            {
                break;
            }

            entries.put(namespacedId, entry);
            nextOffset += sizeofEntry;
        }
        this.entries = entries;
    }

    public Long2ObjectHashMap<LoadSnapshot>.KeySet ids()
    {
        return entries.keySet();
    }

    public LoadSnapshot entry(
        long namespacedId)
    {
        return entries.get(namespacedId);
    }
}
