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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.agrona.collections.Int2ObjectHashMap;
import org.junit.Test;

public class Int2ObjectHashMapsTest
{
    @Test
    public void shouldShareEmptyMap()
    {
        assertSame(Int2ObjectHashMaps.emptyMap(), Int2ObjectHashMaps.emptyMap());
    }

    @Test
    public void shouldReadEmptyMap()
    {
        final Int2ObjectHashMap<String> map = Int2ObjectHashMaps.emptyMap();

        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertNull(map.get(1));
        assertFalse(map.containsKey(1));
        assertFalse(map.containsValue("value"));
        assertFalse(map.values().iterator().hasNext());
        assertFalse(map.keySet().iterator().hasNext());
        assertFalse(map.entrySet().iterator().hasNext());
        map.values().forEach(value -> assertTrue(false));
    }

    @Test
    public void shouldRemoveAbsentFromEmptyMap()
    {
        final Int2ObjectHashMap<String> map = Int2ObjectHashMaps.emptyMap();

        assertNull(map.remove(1));
        assertFalse(map.values().removeIf(value -> true));
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void shouldRejectInsertIntoEmptyMap()
    {
        final Int2ObjectHashMap<String> map = Int2ObjectHashMaps.emptyMap();

        assertThrows(UnsupportedOperationException.class, () -> map.put(1, "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.put(Integer.valueOf(1), "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(1, "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.computeIfAbsent(1, key -> "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.compute(1, (key, value) -> "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.merge(1, "value", (a, b) -> b));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(Map.of(1, "value")));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(new Int2ObjectHashMap<String>()));
        assertTrue(map.isEmpty());
    }
}
