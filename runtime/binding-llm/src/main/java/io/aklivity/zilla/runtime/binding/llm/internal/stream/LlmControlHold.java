/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;

final class LlmControlHold
{
    private static final int NONE = -1;
    private static final int INITIAL_CAPACITY = 16;

    private final Supplier<MutableDirectBufferEx> store;

    private String[] names;
    private int[] offsets;
    private int[] lengths;
    private int[] kinds;
    private int count;
    private int limit;
    private int open;
    private boolean failed;

    LlmControlHold(
        Supplier<MutableDirectBufferEx> store)
    {
        this.store = store;
        this.open = NONE;
    }

    boolean begin(
        String name)
    {
        return begin(name, 0);
    }

    boolean begin(
        String name,
        int kind)
    {
        final boolean stored = store.get() != null;

        failed |= !stored;

        if (stored)
        {
            if (names == null)
            {
                names = new String[INITIAL_CAPACITY];
                offsets = new int[INITIAL_CAPACITY];
                lengths = new int[INITIAL_CAPACITY];
                kinds = new int[INITIAL_CAPACITY];
            }
            else if (count == names.length)
            {
                names = Arrays.copyOf(names, count << 1);
                offsets = Arrays.copyOf(offsets, count << 1);
                lengths = Arrays.copyOf(lengths, count << 1);
                kinds = Arrays.copyOf(kinds, count << 1);
            }

            names[count] = name;
            offsets[count] = limit;
            lengths[count] = 0;
            kinds[count] = kind;
            open = count;
        }

        return stored;
    }

    boolean put(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        final MutableDirectBufferEx held = store.get();
        final boolean fits = held != null && limit + length <= held.capacity();

        failed |= !fits;

        if (fits)
        {
            held.putBytes(limit, buffer, offset, length);
            limit += length;
            lengths[open] += length;
        }

        return fits;
    }

    String end()
    {
        String name = null;

        if (open != NONE)
        {
            name = names[open];
            count++;
            open = NONE;
        }

        return name;
    }

    boolean failed()
    {
        return failed;
    }

    int count()
    {
        return count;
    }

    int held()
    {
        return limit;
    }

    int indexOf(
        String name)
    {
        int found = NONE;

        for (int index = count - 1; found == NONE && index >= 0; index--)
        {
            found = names[index].equals(name) ? index : NONE;
        }

        return found;
    }

    String name(
        int index)
    {
        return names[index];
    }

    DirectBufferEx buffer()
    {
        return store.get();
    }

    int offset(
        int index)
    {
        return offsets[index];
    }

    int length(
        int index)
    {
        return lengths[index];
    }

    void write(
        JsonGeneratorEx generator,
        Predicate<String> included)
    {
        write(generator, 0, included);
    }

    void write(
        JsonGeneratorEx generator,
        int kind,
        Predicate<String> included)
    {
        for (int index = 0; index < count; index++)
        {
            if (kinds[index] == kind && included.test(names[index]))
            {
                generator.writeKey(names[index]);
                generator.writeRaw(store.get(), offsets[index], lengths[index]);
            }
        }
    }

    void reset()
    {
        count = 0;
        limit = 0;
        open = NONE;
        failed = false;
    }
}
