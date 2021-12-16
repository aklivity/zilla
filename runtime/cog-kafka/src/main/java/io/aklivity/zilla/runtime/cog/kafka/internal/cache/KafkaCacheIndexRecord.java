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

import static java.lang.Integer.toUnsignedLong;

public final class KafkaCacheIndexRecord
{
    public static final int SIZEOF_INDEX_RECORD = Long.BYTES;

    public static int indexKey(
        long indexEntry)
    {
        return (int)(indexEntry >>> 32);
    }

    public static int indexValue(
        long indexEntry)
    {
        return (int)(indexEntry & 0xFFFF_FFFFL);
    }

    public static long indexEntry(
        int indexKey,
        int indexValue)
    {
        return ((long) indexKey << 32) | toUnsignedLong(indexValue);
    }

    private KafkaCacheIndexRecord()
    {
        // no instances
    }
}
