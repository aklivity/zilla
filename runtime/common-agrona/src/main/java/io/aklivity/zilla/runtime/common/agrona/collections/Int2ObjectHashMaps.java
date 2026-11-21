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
import java.util.function.IntFunction;

import org.agrona.collections.Hashing;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntObjectToObjectFunction;

/**
 * Factory for shared immutable {@link Int2ObjectHashMap} instances.
 */
public final class Int2ObjectHashMaps
{
    private static final Int2ObjectHashMap<?> EMPTY = new EmptyInt2ObjectHashMap<>();

    /**
     * Returns an immutable empty map, analogous to {@link java.util.Collections#emptyMap()}.
     * Reads, iteration and removal of absent keys behave as for any empty map, and every operation
     * that could insert an entry throws {@link UnsupportedOperationException}.
     * The instance is safe to share between threads.
     */
    @SuppressWarnings("unchecked")
    public static <V> Int2ObjectHashMap<V> emptyMap()
    {
        return (Int2ObjectHashMap<V>) EMPTY;
    }

    private Int2ObjectHashMaps()
    {
    }

    private static final class EmptyInt2ObjectHashMap<V> extends Int2ObjectHashMap<V>
    {
        private EmptyInt2ObjectHashMap()
        {
            super(0, Hashing.DEFAULT_LOAD_FACTOR, false);
            keySet();
            values();
            entrySet();
        }

        @Override
        public V put(
            int key,
            V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V putIfAbsent(
            int key,
            V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V computeIfAbsent(
            int key,
            IntFunction<? extends V> mappingFunction)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V compute(
            int key,
            IntObjectToObjectFunction<? super V, ? extends V> remappingFunction)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public V merge(
            int key,
            V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(
            Map<? extends Integer, ? extends V> map)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(
            Int2ObjectHashMap<? extends V> map)
        {
            throw new UnsupportedOperationException();
        }
    }
}
