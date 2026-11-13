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
 * <p>
 * The tokenizer record written at a clean boundary holds a {@code mode} word and a {@code context} word, both
 * {@code int}, followed by the path and the stream positions, so that no field of the live tokenizer has to be
 * packed for the record to be small. The bit layout is fixed and stable:
 * <ul>
 * <li>{@code mode} bits 0 to 3 the parse state, 4 to 6 the resume operation, 7 to 10 the number state,
 * 11 member separated, 12 separator before, 13 segmenting, 14 scalar segment, 15 fragmenting, 16 escape armed,
 * 17 lens, 18 resume escape, 19 to 21 the literal chars matched</li>
 * <li>{@code context} bits 0 to 6 the depth, 7 to 13 the depth the escaped scope opened at, 14 to 16 the hex
 * digits of a unicode escape still to read</li>
 * </ul>
 * The parser record is a {@code mode} word of its own followed by the tokenizer record: bits 0 and 1 the document
 * state, 2 to 7 the last event delivered (0 for none, else its ordinal plus one), 8 arm next value, 9 verbatim,
 * 10 trim leading separator, 11 fragment open.
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

    private static final int STATE_SHIFT = 0;
    private static final int RESUME_OP_SHIFT = 4;
    private static final int NUMBER_STATE_SHIFT = 7;
    private static final int MEMBER_SEPARATED = 1 << 11;
    private static final int SEPARATOR_BEFORE = 1 << 12;
    private static final int SEGMENTING = 1 << 13;
    private static final int SCALAR_SEGMENT = 1 << 14;
    private static final int FRAGMENTING = 1 << 15;
    private static final int ESCAPE_ARMED = 1 << 16;
    private static final int LENS = 1 << 17;
    private static final int RESUME_ESCAPE = 1 << 18;
    private static final int LITERAL_INDEX_SHIFT = 19;

    private static final int DEPTH_SHIFT = 0;
    private static final int SCOPE_DEPTH_SHIFT = 7;
    private static final int UNICODE_PENDING_SHIFT = 14;

    private static final int FOUR_BITS = 0xf;
    private static final int THREE_BITS = 0x7;
    private static final int SEVEN_BITS = 0x7f;

    static int state(
        int mode)
    {
        return mode >>> STATE_SHIFT & FOUR_BITS;
    }

    static int state(
        int mode,
        int state)
    {
        return mode & ~(FOUR_BITS << STATE_SHIFT) | state << STATE_SHIFT;
    }

    static int resumeOp(
        int mode)
    {
        return mode >>> RESUME_OP_SHIFT & THREE_BITS;
    }

    static int resumeOp(
        int mode,
        int resumeOp)
    {
        return mode & ~(THREE_BITS << RESUME_OP_SHIFT) | resumeOp << RESUME_OP_SHIFT;
    }

    static int numberState(
        int mode)
    {
        return mode >>> NUMBER_STATE_SHIFT & FOUR_BITS;
    }

    static int numberState(
        int mode,
        int numberState)
    {
        return mode & ~(FOUR_BITS << NUMBER_STATE_SHIFT) | numberState << NUMBER_STATE_SHIFT;
    }

    static int literalIndex(
        int mode)
    {
        return mode >>> LITERAL_INDEX_SHIFT & THREE_BITS;
    }

    static int literalIndex(
        int mode,
        int literalIndex)
    {
        return mode & ~(THREE_BITS << LITERAL_INDEX_SHIFT) | literalIndex << LITERAL_INDEX_SHIFT;
    }

    static boolean memberSeparated(
        int mode)
    {
        return (mode & MEMBER_SEPARATED) != 0;
    }

    static int memberSeparated(
        int mode,
        boolean on)
    {
        return flag(mode, MEMBER_SEPARATED, on);
    }

    static boolean separatorBefore(
        int mode)
    {
        return (mode & SEPARATOR_BEFORE) != 0;
    }

    static int separatorBefore(
        int mode,
        boolean on)
    {
        return flag(mode, SEPARATOR_BEFORE, on);
    }

    static boolean segmenting(
        int mode)
    {
        return (mode & SEGMENTING) != 0;
    }

    static int segmenting(
        int mode,
        boolean on)
    {
        return flag(mode, SEGMENTING, on);
    }

    static boolean scalarSegment(
        int mode)
    {
        return (mode & SCALAR_SEGMENT) != 0;
    }

    static int scalarSegment(
        int mode,
        boolean on)
    {
        return flag(mode, SCALAR_SEGMENT, on);
    }

    static boolean fragmenting(
        int mode)
    {
        return (mode & FRAGMENTING) != 0;
    }

    static int fragmenting(
        int mode,
        boolean on)
    {
        return flag(mode, FRAGMENTING, on);
    }

    static boolean escapeArmed(
        int mode)
    {
        return (mode & ESCAPE_ARMED) != 0;
    }

    static int escapeArmed(
        int mode,
        boolean on)
    {
        return flag(mode, ESCAPE_ARMED, on);
    }

    static boolean lens(
        int mode)
    {
        return (mode & LENS) != 0;
    }

    static int lens(
        int mode,
        boolean on)
    {
        return flag(mode, LENS, on);
    }

    static boolean resumeEscape(
        int mode)
    {
        return (mode & RESUME_ESCAPE) != 0;
    }

    static int resumeEscape(
        int mode,
        boolean on)
    {
        return flag(mode, RESUME_ESCAPE, on);
    }

    static int depth(
        int context)
    {
        return context >>> DEPTH_SHIFT & SEVEN_BITS;
    }

    static int depth(
        int context,
        int depth)
    {
        return context & ~(SEVEN_BITS << DEPTH_SHIFT) | depth << DEPTH_SHIFT;
    }

    static int scopeDepth(
        int context)
    {
        return context >>> SCOPE_DEPTH_SHIFT & SEVEN_BITS;
    }

    static int scopeDepth(
        int context,
        int scopeDepth)
    {
        return context & ~(SEVEN_BITS << SCOPE_DEPTH_SHIFT) | scopeDepth << SCOPE_DEPTH_SHIFT;
    }

    static int unicodePending(
        int context)
    {
        return context >>> UNICODE_PENDING_SHIFT & THREE_BITS;
    }

    static int unicodePending(
        int context,
        int unicodePending)
    {
        return context & ~(THREE_BITS << UNICODE_PENDING_SHIFT) | unicodePending << UNICODE_PENDING_SHIFT;
    }

    private static final int DOC_STATE_SHIFT = 0;
    private static final int LAST_EVENT_SHIFT = 2;
    private static final int ARM_NEXT_VALUE = 1 << 8;
    private static final int VERBATIM = 1 << 9;
    private static final int TRIM_LEADING_SEPARATOR = 1 << 10;
    private static final int FRAGMENT_OPEN = 1 << 11;
    private static final int TWO_BITS = 0x3;
    private static final int SIX_BITS = 0x3f;

    static int docState(
        int mode)
    {
        return mode >>> DOC_STATE_SHIFT & TWO_BITS;
    }

    static int docState(
        int mode,
        int docState)
    {
        return mode & ~(TWO_BITS << DOC_STATE_SHIFT) | docState << DOC_STATE_SHIFT;
    }

    static int lastEvent(
        int mode)
    {
        return mode >>> LAST_EVENT_SHIFT & SIX_BITS;
    }

    static int lastEvent(
        int mode,
        int lastEvent)
    {
        return mode & ~(SIX_BITS << LAST_EVENT_SHIFT) | lastEvent << LAST_EVENT_SHIFT;
    }

    static boolean armNextValue(
        int mode)
    {
        return (mode & ARM_NEXT_VALUE) != 0;
    }

    static int armNextValue(
        int mode,
        boolean on)
    {
        return flag(mode, ARM_NEXT_VALUE, on);
    }

    static boolean verbatim(
        int mode)
    {
        return (mode & VERBATIM) != 0;
    }

    static int verbatim(
        int mode,
        boolean on)
    {
        return flag(mode, VERBATIM, on);
    }

    static boolean trimLeadingSeparator(
        int mode)
    {
        return (mode & TRIM_LEADING_SEPARATOR) != 0;
    }

    static int trimLeadingSeparator(
        int mode,
        boolean on)
    {
        return flag(mode, TRIM_LEADING_SEPARATOR, on);
    }

    static boolean fragmentOpen(
        int mode)
    {
        return (mode & FRAGMENT_OPEN) != 0;
    }

    static int fragmentOpen(
        int mode,
        boolean on)
    {
        return flag(mode, FRAGMENT_OPEN, on);
    }

    private static int flag(
        int word,
        int mask,
        boolean on)
    {
        return on ? word | mask : word & ~mask;
    }

    private JsonParseState()
    {
    }
}
