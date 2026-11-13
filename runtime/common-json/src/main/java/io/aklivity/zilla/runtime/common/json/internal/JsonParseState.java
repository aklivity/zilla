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
package io.aklivity.zilla.runtime.common.json.internal;

/**
 * The container path of an in-flight parse, held in a {@code long} bitmask and an {@code int} depth.
 * <p>
 * Bit {@code n} of the path is set when the container open at level {@code n} (0 is the outermost) is an array and
 * clear when it is an object, so a path of at most {@value #MAX_DEPTH} levels needs no array. Bits at or above the
 * depth are always clear, so equal paths are equal words.
 * <p>
 * The depth is a limit by construction: a shift of a {@code long} by 64 wraps to a shift by 0, so
 * {@link #pushPath(long, int, boolean)} rejects a push at {@link #MAX_DEPTH} rather than corrupting level 0.
 */
final class JsonParseState
{
    static final int MAX_DEPTH = Long.SIZE;

    static boolean pathFull(
        int depth)
    {
        return depth >= MAX_DEPTH;
    }

    static long pushPath(
        long path,
        int depth,
        boolean inArray)
    {
        if (pathFull(depth))
        {
            throw new IllegalArgumentException("path depth exceeds " + MAX_DEPTH);
        }

        return inArray ? path | 1L << depth : path & ~(1L << depth);
    }

    static long popPath(
        long path,
        int depth)
    {
        if (depth <= 0)
        {
            throw new IllegalArgumentException("path is empty");
        }

        return path & ~(1L << depth - 1);
    }

    static boolean pathArrayAt(
        long path,
        int level)
    {
        return (path >>> level & 1L) != 0L;
    }

    static boolean pathInArray(
        long path,
        int depth)
    {
        return depth > 0 && pathArrayAt(path, depth - 1);
    }

    static boolean pathInObject(
        long path,
        int depth)
    {
        return depth > 0 && !pathArrayAt(path, depth - 1);
    }

    private JsonParseState()
    {
    }
}
