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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

import org.agrona.collections.Hashing;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongObjectToObjectFunction;

/**
 * Factory for shared immutable {@link Long2ObjectHashMap} instances.
 */
public final class Long2ObjectHashMaps
{
    private static final Long2ObjectHashMap<?> EMPTY = new EmptyLong2ObjectHashMap<>();

    /**
     * Returns an immutable empty map, analogous to {@link java.util.Collections#emptyMap()}.
     * Reads, iteration and removal of absent keys behave as for any empty map, and every operation
     * that could insert an entry throws {@link UnsupportedOperationException}.
     * The instance is safe to share between threads.
     */
    @SuppressWarnings("unchecked")
    public static <V> Long2ObjectHashMap<V> emptyMap()
    {
        return (Long2ObjectHashMap<V>) EMPTY;
    }

    private Long2ObjectHashMaps()
    {
    }

    private static final class EmptyLong2ObjectHashMap<V> extends Long2ObjectHashMap<V>
    {
        private EmptyLong2ObjectHashMap()
        {
            super(0, Hashing.DEFAULT_LOAD_FACTOR, false);
            keySet();
            values();
            entrySet();
        }

        @Override
        public V put(
            long key,
            V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V putIfAbsent(
            long key,
            V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V computeIfAbsent(
            long key,
            LongFunction<? extends V> mappingFunction)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V compute(
            long key,
            LongObjectToObjectFunction<? super V, ? extends V> remappingFunction)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V merge(
            long key,
            V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(
            Map<? extends Long, ? extends V> map)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(
            Long2ObjectHashMap<? extends V> map)
        {
            throw new UnsupportedOperationException();
        }
    }
}
