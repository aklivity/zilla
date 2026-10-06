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

import io.aklivity.zilla.runtime.engine.util.Flags;

final class LlmEventFrames
{
    private static final int NONE = -1;
    private static final int INITIAL_CAPACITY = 8;

    private int[] offsets;
    private int[] lengths;
    private int[] flags;
    private String[] types;
    private int count;

    private boolean open;
    private boolean started;
    private String type;
    private int start;

    LlmEventFrames()
    {
        this.offsets = new int[INITIAL_CAPACITY];
        this.lengths = new int[INITIAL_CAPACITY];
        this.flags = new int[INITIAL_CAPACITY];
        this.types = new String[INITIAL_CAPACITY];
        this.start = NONE;
    }

    void wrap(
        int offset)
    {
        if (open && start == NONE)
        {
            start = offset;
        }
    }

    void begin(
        int position,
        String type)
    {
        this.open = true;
        this.started = false;
        this.type = type;
        this.start = position;
    }

    void end(
        int position)
    {
        if (open)
        {
            emit(position, true);
            open = false;
            start = NONE;
        }
    }

    void flush(
        int position)
    {
        if (open && start != NONE)
        {
            emit(position, false);
            start = NONE;
        }
    }

    int count()
    {
        return count;
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

    int flags(
        int index)
    {
        return flags[index];
    }

    String type(
        int index)
    {
        return types[index];
    }

    void clear()
    {
        count = 0;
    }

    private void emit(
        int position,
        boolean fin)
    {
        final int length = position - start;

        if (length > 0 || fin && started)
        {
            if (count == offsets.length)
            {
                final int capacity = count << 1;
                offsets = Arrays.copyOf(offsets, capacity);
                lengths = Arrays.copyOf(lengths, capacity);
                flags = Arrays.copyOf(flags, capacity);
                types = Arrays.copyOf(types, capacity);
            }

            final int init = started ? Flags.NONE : Flags.INIT;

            offsets[count] = start;
            lengths[count] = length;
            flags[count] = fin ? Flags.fin(init) : init;
            types[count] = started ? null : type;
            count++;

            started = true;
        }
    }
}
