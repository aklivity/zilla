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
package io.aklivity.zilla.runtime.cog.kafka.internal.cache;

import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheIndexRecord.indexEntry;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheIndexRecord.indexKey;
import static io.aklivity.zilla.runtime.cog.kafka.internal.cache.KafkaCacheIndexRecord.indexValue;
import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

public class KafkaCacheIndexRecordTest
{
    @Test
    public void shouldMakeIndexEntry()
    {
        final Random random = new Random();
        final int key = random.nextInt() & 0x7FFF_FFFF;
        final int value = random.nextInt() & 0x7FFF_FFFF;
        final long entry = indexEntry(key, value);

        assertEquals(key, indexKey(entry));
        assertEquals(value, indexValue(entry));
    }

    @Test
    public void shouldMakeIndexEntryWithNegativeValue()
    {
        final Random random = new Random();
        final int key = random.nextInt() & 0x7FFF_FFFF;
        final int value = random.nextInt() | 0x8000_0000;
        final long entry = indexEntry(key, value);

        assertEquals(key, indexKey(entry));
        assertEquals(value, indexValue(entry));
    }

    @Test
    public void shouldMakeIndexEntryWithNegativeIndex()
    {
        final Random random = new Random();
        final int key = random.nextInt() | 0x8000_0000;
        final int value = random.nextInt() & 0x7FFF_FFFF;
        final long entry = indexEntry(key, value);

        assertEquals(key, indexKey(entry));
        assertEquals(value, indexValue(entry));
    }
}
