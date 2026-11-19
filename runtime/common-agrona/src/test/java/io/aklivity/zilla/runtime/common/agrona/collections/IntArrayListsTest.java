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
package io.aklivity.zilla.runtime.common.agrona.collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.agrona.collections.IntArrayList;
import org.junit.Test;

public class IntArrayListsTest
{
    @Test
    public void shouldShareEmptyList()
    {
        assertSame(IntArrayLists.emptyList(), IntArrayLists.emptyList());
    }

    @Test
    public void shouldReadEmptyList()
    {
        final IntArrayList list = IntArrayLists.emptyList();

        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
        assertFalse(list.containsInt(1));
        assertFalse(list.contains(1));
        assertFalse(list.iterator().hasNext());
    }

    @Test
    public void shouldRemoveAbsentFromEmptyList()
    {
        final IntArrayList list = IntArrayLists.emptyList();

        assertFalse(list.removeInt(1));
        assertFalse(list.remove(Integer.valueOf(1)));
        list.clear();
        assertTrue(list.isEmpty());
    }

    @Test
    public void shouldRejectInsertIntoEmptyList()
    {
        final IntArrayList list = IntArrayLists.emptyList();

        assertThrows(UnsupportedOperationException.class, () -> list.add(1));
        assertThrows(UnsupportedOperationException.class, () -> list.addInt(1));
        assertThrows(UnsupportedOperationException.class, () -> list.add(0, Integer.valueOf(1)));
        assertThrows(UnsupportedOperationException.class, () -> list.addInt(0, 1));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(new IntArrayList()));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, new IntArrayList()));
        assertTrue(list.isEmpty());
    }
}
