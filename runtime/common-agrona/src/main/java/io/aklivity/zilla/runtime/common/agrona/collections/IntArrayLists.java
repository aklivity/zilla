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

import org.agrona.collections.IntArrayList;

/**
 * Factory for shared immutable {@link IntArrayList} instances.
 */
public final class IntArrayLists
{
    private static final IntArrayList EMPTY = new EmptyIntArrayList();

    /**
     * Returns an immutable empty list, analogous to {@link java.util.Collections#emptyList()}.
     * Reads, iteration and removal of absent values behave as for any empty list, and every operation
     * that could insert an element throws {@link UnsupportedOperationException}.
     */
    public static IntArrayList emptyList()
    {
        return EMPTY;
    }

    private IntArrayLists()
    {
    }

    private static final class EmptyIntArrayList extends IntArrayList
    {
        private EmptyIntArrayList()
        {
            super(0, DEFAULT_NULL_VALUE);
        }

        @Override
        public boolean add(
            Integer element)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addInt(
            int element)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(
            int index,
            Integer element)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addInt(
            int index,
            int element)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(
            IntArrayList list)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(
            int index,
            IntArrayList list)
        {
            throw new UnsupportedOperationException();
        }
    }
}
