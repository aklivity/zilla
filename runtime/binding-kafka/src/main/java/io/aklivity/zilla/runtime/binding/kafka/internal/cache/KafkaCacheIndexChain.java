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

import java.util.Arrays;

import org.agrona.collections.Int2IntHashMap;

final class KafkaCacheIndexChain
{
    static final int NONE = -1;

    private static final int[] EMPTY = new int[0];

    private final Int2IntHashMap lastIndexByKey = new Int2IntHashMap(NONE);

    private int[] previousIndexByIndex = EMPTY;
    private int length;

    int length()
    {
        return length;
    }

    void append(
        int key)
    {
        if (length == previousIndexByIndex.length)
        {
            previousIndexByIndex = Arrays.copyOf(previousIndexByIndex, Math.max(length << 1, 1024));
        }

        previousIndexByIndex[length] = lastIndexByKey.put(key, length);
        length++;
    }

    int last(
        int key)
    {
        return lastIndexByKey.get(key);
    }

    int previous(
        int index)
    {
        return previousIndexByIndex[index];
    }

    int floor(
        int key,
        int index)
    {
        int floor = lastIndexByKey.get(key);

        while (floor > index)
        {
            floor = previousIndexByIndex[floor];
        }

        return floor;
    }

    void clear()
    {
        lastIndexByKey.clear();
        lastIndexByKey.compact();
        previousIndexByIndex = EMPTY;
        length = 0;
    }
}
