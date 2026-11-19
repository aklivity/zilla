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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EmptyCollectionsTest
{
    private final EmptyCollections empty = new EmptyCollections();

    @Test
    public void shouldReadEmptyCollections()
    {
        assertTrue(empty.longMap().isEmpty());
        assertNull(empty.longMap().get(1L));
        empty.longMap().values().forEach(value -> assertTrue(false));
        assertTrue(empty.intMap().isEmpty());
        assertNull(empty.intMap().get(1));
        assertTrue(empty.map().isEmpty());
        assertTrue(empty.list().isEmpty());
        assertTrue(empty.deque().isEmpty());
        assertFalse(empty.intList().contains(1));
        assertEquals(0, empty.list().size());
    }

    @Test
    public void shouldRemoveFromEmptyCollections()
    {
        assertNull(empty.longMap().remove(1L));
        assertNull(empty.intMap().remove(1));
        assertFalse(empty.list().remove("element"));
        assertFalse(empty.deque().remove("element"));
    }

    @Test
    public void shouldRejectInsertIntoLongMap()
    {
        assertThrows(AssertionError.class, () -> empty.longMap().put(1L, "value"));
        assertThrows(AssertionError.class, () -> empty.longMap().computeIfAbsent(1L, key -> "value"));
    }

    @Test
    public void shouldRejectInsertIntoIntMap()
    {
        assertThrows(AssertionError.class, () -> empty.intMap().put(1, "value"));
        assertThrows(AssertionError.class, () -> empty.<String>intMap().computeIfAbsent(1, key -> "value"));
    }

    @Test
    public void shouldRejectInsertIntoMap()
    {
        assertThrows(AssertionError.class, () -> empty.map().put("key", "value"));
        assertThrows(AssertionError.class, () -> empty.map().computeIfAbsent("key", key -> "value"));
    }

    @Test
    public void shouldRejectInsertIntoList()
    {
        assertThrows(AssertionError.class, () -> empty.list().add("element"));
    }

    @Test
    public void shouldRejectInsertIntoDeque()
    {
        assertThrows(AssertionError.class, () -> empty.deque().add("element"));
        assertThrows(AssertionError.class, () -> empty.deque().addLast("element"));
    }

    @Test
    public void shouldRejectInsertIntoIntList()
    {
        assertThrows(AssertionError.class, () -> empty.intList().add(1));
    }
}
