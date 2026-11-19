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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectHashMap;

final class EmptyCollections
{
    private final Long2ObjectHashMap<Object> longMap = new Long2ObjectHashMap<>()
    {
        @Override
        public Object put(
            long key,
            Object value)
        {
            assert false : "insert into shared empty collection";
            return super.put(key, value);
        }

        @Override
        public Object computeIfAbsent(
            long key,
            LongFunction<?> mappingFunction)
        {
            assert false : "insert into shared empty collection";
            return super.computeIfAbsent(key, mappingFunction);
        }
    };

    private final Int2ObjectHashMap<Object> intMap = new Int2ObjectHashMap<>()
    {
        @Override
        public Object put(
            int key,
            Object value)
        {
            assert false : "insert into shared empty collection";
            return super.put(key, value);
        }

        @Override
        public Object computeIfAbsent(
            int key,
            IntFunction<?> mappingFunction)
        {
            assert false : "insert into shared empty collection";
            return super.computeIfAbsent(key, mappingFunction);
        }
    };

    private final Map<Object, Object> map = new HashMap<>()
    {
        @Override
        public Object put(
            Object key,
            Object value)
        {
            assert false : "insert into shared empty collection";
            return super.put(key, value);
        }

        @Override
        public Object computeIfAbsent(
            Object key,
            Function<? super Object, ?> mappingFunction)
        {
            assert false : "insert into shared empty collection";
            return super.computeIfAbsent(key, mappingFunction);
        }
    };

    private final List<Object> list = new ArrayList<>()
    {
        @Override
        public boolean add(
            Object element)
        {
            assert false : "insert into shared empty collection";
            return super.add(element);
        }
    };

    private final Deque<Object> deque = new ArrayDeque<>()
    {
        @Override
        public void addLast(
            Object element)
        {
            assert false : "insert into shared empty collection";
            super.addLast(element);
        }
    };

    private final IntArrayList intList = new IntArrayList()
    {
        @Override
        public boolean add(
            Integer element)
        {
            assert false : "insert into shared empty collection";
            return super.add(element);
        }
    };

    @SuppressWarnings("unchecked")
    <V> Long2ObjectHashMap<V> longMap()
    {
        return (Long2ObjectHashMap<V>) longMap;
    }

    @SuppressWarnings("unchecked")
    <V> Int2ObjectHashMap<V> intMap()
    {
        return (Int2ObjectHashMap<V>) intMap;
    }

    @SuppressWarnings("unchecked")
    <K, V> Map<K, V> map()
    {
        return (Map<K, V>) map;
    }

    @SuppressWarnings("unchecked")
    <E> List<E> list()
    {
        return (List<E>) list;
    }

    @SuppressWarnings("unchecked")
    <E> Deque<E> deque()
    {
        return (Deque<E>) deque;
    }

    IntArrayList intList()
    {
        return intList;
    }

    boolean shared(
        Long2ObjectHashMap<?> candidate)
    {
        return candidate == longMap;
    }

    boolean shared(
        Int2ObjectHashMap<?> candidate)
    {
        return candidate == intMap;
    }

    boolean shared(
        Map<?, ?> candidate)
    {
        return candidate == map;
    }

    boolean shared(
        List<?> candidate)
    {
        return candidate == list;
    }

    boolean shared(
        Deque<?> candidate)
    {
        return candidate == deque;
    }

    boolean shared(
        IntArrayList candidate)
    {
        return candidate == intList;
    }
}
