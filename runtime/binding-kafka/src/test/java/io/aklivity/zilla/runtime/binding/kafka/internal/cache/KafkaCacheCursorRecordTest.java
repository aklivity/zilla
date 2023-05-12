/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursor;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorIndex;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorValue;
import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

public class KafkaCacheCursorRecordTest
{
    @Test
    public void shouldMakeCursor()
    {
        final Random random = new Random();
        final int index = random.nextInt() & 0x7FFF_FFFF;
        final int value = random.nextInt() & 0x7FFF_FFFF;
        final long cursor = cursor(index, value);

        assertEquals(index, cursorIndex(cursor));
        assertEquals(value, cursorValue(cursor));
    }

    @Test
    public void shouldMakeCursorWithNegativeValue()
    {
        final Random random = new Random();
        final int index = random.nextInt() & 0x7FFF_FFFF;
        final int value = random.nextInt() | 0x8000_0000;
        final long cursor = cursor(index, value);

        assertEquals(index, cursorIndex(cursor));
        assertEquals(value, cursorValue(cursor));
    }

    @Test
    public void shouldMakeCursorWithNegativeIndex()
    {
        final Random random = new Random();
        final int index = random.nextInt() | 0x8000_0000;
        final int value = random.nextInt() & 0x7FFF_FFFF;
        final long cursor = cursor(index, value);

        assertEquals(index, cursorIndex(cursor));
        assertEquals(value, cursorValue(cursor));
    }
}
