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
package io.aklivity.zilla.runtime.engine.util;

/**
 * Flags carried by a data frame, describing how its payload fits within a message that may span several frames.
 * <p>
 * The first frame of a message has {@link #INIT} set and the last frame has {@link #FIN} set, so a message that
 * fits in a single frame has both.
 */
public final class Flags
{
    /**
     * No flags set, for a frame in the middle of a message.
     */
    public static final int NONE = 0x00;

    /**
     * Set on the last frame of a message.
     */
    public static final int FIN = 0x01;

    /**
     * Set on the first frame of a message.
     */
    public static final int INIT = 0x02;

    /**
     * Set on a frame whose message is incomplete.
     */
    public static final int INCOMPLETE = 0x04;

    /**
     * Set on a frame whose message the receiver is to skip.
     */
    public static final int SKIP = 0x08;

    /**
     * Set on the only frame of a message that fits in a single frame.
     */
    public static final int COMPLETE = INIT | FIN;

    /**
     * Tests whether the frame is the first of its message.
     *
     * @param flags  the data frame flags
     * @return {@code true} if {@link #INIT} is set
     */
    public static boolean hasInit(
        int flags)
    {
        return (flags & INIT) != 0;
    }

    /**
     * Tests whether the frame is the last of its message.
     *
     * @param flags  the data frame flags
     * @return {@code true} if {@link #FIN} is set
     */
    public static boolean hasFin(
        int flags)
    {
        return (flags & FIN) != 0;
    }

    /**
     * Tests whether the frame has an incomplete message.
     *
     * @param flags  the data frame flags
     * @return {@code true} if {@link #INCOMPLETE} is set
     */
    public static boolean hasIncomplete(
        int flags)
    {
        return (flags & INCOMPLETE) != 0;
    }

    /**
     * Tests whether the receiver is to skip the message of the frame.
     *
     * @param flags  the data frame flags
     * @return {@code true} if {@link #SKIP} is set
     */
    public static boolean hasSkip(
        int flags)
    {
        return (flags & SKIP) != 0;
    }

    /**
     * Sets {@link #INIT}.
     *
     * @param flags  the data frame flags
     * @return the flags with {@link #INIT} set
     */
    public static int init(
        int flags)
    {
        return flags | INIT;
    }

    /**
     * Sets {@link #FIN}.
     *
     * @param flags  the data frame flags
     * @return the flags with {@link #FIN} set
     */
    public static int fin(
        int flags)
    {
        return flags | FIN;
    }

    /**
     * Sets {@link #INCOMPLETE}.
     *
     * @param flags  the data frame flags
     * @return the flags with {@link #INCOMPLETE} set
     */
    public static int incomplete(
        int flags)
    {
        return flags | INCOMPLETE;
    }

    /**
     * Sets {@link #SKIP}.
     *
     * @param flags  the data frame flags
     * @return the flags with {@link #SKIP} set
     */
    public static int skip(
        int flags)
    {
        return flags | SKIP;
    }

    private Flags()
    {
    }
}
