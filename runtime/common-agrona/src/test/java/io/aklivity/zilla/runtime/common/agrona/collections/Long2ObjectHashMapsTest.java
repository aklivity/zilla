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

import org.agrona.collections.Long2ObjectHashMap;
import org.junit.Test;

public class Long2ObjectHashMapsTest
{
    @Test
    public void shouldShareEmptyMap()
    {
        assertSame(Long2ObjectHashMaps.emptyMap(), Long2ObjectHashMaps.emptyMap());
    }

    @Test
    public void shouldReadEmptyMap()
    {
        final Long2ObjectHashMap<String> map = Long2ObjectHashMaps.emptyMap();

        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertNull(map.get(1L));
        assertFalse(map.containsKey(1L));
        assertFalse(map.containsValue("value"));
        assertFalse(map.values().iterator().hasNext());
        assertFalse(map.keySet().iterator().hasNext());
        assertFalse(map.entrySet().iterator().hasNext());
        map.values().forEach(value -> assertTrue(false));
    }

    @Test
    public void shouldRemoveAbsentFromEmptyMap()
    {
        final Long2ObjectHashMap<String> map = Long2ObjectHashMaps.emptyMap();

        assertNull(map.remove(1L));
        assertFalse(map.values().removeIf(value -> true));
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void shouldRejectInsertIntoEmptyMap()
    {
        final Long2ObjectHashMap<String> map = Long2ObjectHashMaps.emptyMap();

        assertThrows(UnsupportedOperationException.class, () -> map.put(1L, "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.put(Long.valueOf(1L), "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(1L, "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.computeIfAbsent(1L, key -> "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.compute(1L, (key, value) -> "value"));
        assertThrows(UnsupportedOperationException.class, () -> map.merge(1L, "value", (a, b) -> b));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(Map.of(1L, "value")));
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(new Long2ObjectHashMap<String>()));
        assertTrue(map.isEmpty());
    }
}
