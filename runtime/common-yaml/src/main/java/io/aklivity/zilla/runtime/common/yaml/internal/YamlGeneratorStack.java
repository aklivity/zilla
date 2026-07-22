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
package io.aklivity.zilla.runtime.common.yaml.internal;

import java.util.Arrays;

/**
 * Allocation-free nesting stack for the streaming YAML generators. Each open object or array is one
 * entry, encoded as a packed {@code int} frame plus an intro key and a pending key, so a generator
 * holds two reusable arrays instead of one stack node object per nesting level.
 */
public final class YamlGeneratorStack
{
    public static final int ROOT = 0;
    public static final int OBJECT_VALUE = 1;
    public static final int ARRAY_ELEMENT = 2;

    private static final int KIND_MASK = 0x3;
    private static final int ARRAY = 1 << 2;
    private static final int OPENED = 1 << 3;
    private static final int FIRST = 1 << 4;
    private static final int FIRST_INLINE = 1 << 5;
    private static final int INTRO_INDENT_SHIFT = 6;

    private int[] frames;
    private String[] keys;
    private int depth;

    public YamlGeneratorStack()
    {
        this.frames = new int[8];
        this.keys = new String[16];
    }

    public boolean isEmpty()
    {
        return depth == 0;
    }

    public void push(
        int kind,
        boolean array,
        int introIndent,
        String introKey)
    {
        if (depth == frames.length)
        {
            frames = Arrays.copyOf(frames, frames.length * 2);
            keys = Arrays.copyOf(keys, keys.length * 2);
        }
        frames[depth] = kind | (array ? ARRAY : 0) | FIRST | (introIndent << INTRO_INDENT_SHIFT);
        keys[depth * 2] = introKey;
        keys[depth * 2 + 1] = null;
        depth++;
    }

    public void pop()
    {
        depth--;
    }

    public int kind()
    {
        return frames[depth - 1] & KIND_MASK;
    }

    public boolean array()
    {
        return (frames[depth - 1] & ARRAY) != 0;
    }

    public boolean opened()
    {
        return (frames[depth - 1] & OPENED) != 0;
    }

    public boolean first()
    {
        return (frames[depth - 1] & FIRST) != 0;
    }

    public boolean firstInline()
    {
        return (frames[depth - 1] & FIRST_INLINE) != 0;
    }

    public int introIndent()
    {
        return frames[depth - 1] >>> INTRO_INDENT_SHIFT;
    }

    public int childIndent()
    {
        return kind() == ROOT ? 0 : introIndent() + 1;
    }

    public String introKey()
    {
        return keys[(depth - 1) * 2];
    }

    public String pendingKey()
    {
        return keys[(depth - 1) * 2 + 1];
    }

    public void markOpened()
    {
        frames[depth - 1] |= OPENED;
    }

    public void markFirstInline()
    {
        frames[depth - 1] |= FIRST_INLINE;
    }

    public void clearFirst()
    {
        frames[depth - 1] &= ~FIRST;
    }

    public void clearFirstInline()
    {
        frames[depth - 1] &= ~FIRST_INLINE;
    }

    public void pendingKey(
        String name)
    {
        keys[(depth - 1) * 2 + 1] = name;
    }
}
