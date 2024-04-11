/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.binding;

public final class BindingHandlerState
{
    private static final int INITIAL_OPENING = 0x1000;
    private static final int INITIAL_OPENED = 0x2000;
    private static final int INITIAL_CLOSING = 0x4000;
    private static final int INITIAL_CLOSED = 0x8000;

    private static final int REPLY_OPENED = 0x0001;
    private static final int REPLY_OPENING = 0x0002;
    private static final int REPLY_CLOSING = 0x0004;
    private static final int REPLY_CLOSED = 0x0008;
    private static final int REPLY_ABORTING = 0x0010;

    private static final int CLOSING = INITIAL_CLOSING | REPLY_CLOSING;
    private static final int CLOSED = INITIAL_CLOSED | REPLY_CLOSED;

    public static int openingInitial(
        int state)
    {
        return state | INITIAL_OPENING;
    }

    public static int openInitialOnly(
        int state)
    {
        return state | INITIAL_OPENED;
    }

    public static int openInitial(
        int state)
    {
        return state | INITIAL_OPENING | INITIAL_OPENED;
    }

    public static int closingInitial(
        int state)
    {
        return state | INITIAL_CLOSING;
    }

    public static int closeInitialOnly(
        int state)
    {
        return state | INITIAL_CLOSED;
    }

    public static int closeInitial(
        int state)
    {
        return state | INITIAL_CLOSING | INITIAL_CLOSED;
    }

    public static int openingReply(
        int state)
    {
        return state | REPLY_OPENING;
    }

    public static int openReplyOnly(
        int state)
    {
        return state | REPLY_OPENED;
    }

    public static int openReply(
        int state)
    {
        return state | REPLY_OPENING | REPLY_OPENED;
    }

    public static int closingReply(
        int state)
    {
        return state | REPLY_CLOSING;
    }

    public static int closeReplyOnly(
        int state)
    {
        return state | REPLY_CLOSED;
    }

    public static int closeReply(
        int state)
    {
        return state | REPLY_CLOSING | REPLY_CLOSED;
    }

    public static int abortingReply(
        int state)
    {
        return state | REPLY_ABORTING;
    }

    public static boolean initialOpening(
        int state)
    {
        return (state & INITIAL_OPENING) != 0;
    }

    public static boolean initialOpened(
        int state)
    {
        return (state & INITIAL_OPENED) != 0;
    }

    public static boolean initialClosing(
        int state)
    {
        return (state & INITIAL_CLOSING) != 0;
    }

    public static boolean initialClosed(
        int state)
    {
        return (state & INITIAL_CLOSED) != 0;
    }

    public static boolean replyOpening(
        int state)
    {
        return (state & REPLY_OPENING) != 0;
    }

    public static boolean replyOpened(
        int state)
    {
        return (state & REPLY_OPENED) != 0;
    }

    public static boolean replyClosing(
        int state)
    {
        return (state & REPLY_CLOSING) != 0;
    }

    public static boolean replyClosed(
        int state)
    {
        return (state & REPLY_CLOSED) != 0;
    }

    public static boolean replyAborting(
        int state)
    {
        return (state & REPLY_ABORTING) != 0;
    }

    public static boolean closing(
        int state)
    {
        return (state & CLOSING) == CLOSING;
    }

    public static boolean closed(
        int state)
    {
        return (state & CLOSED) == CLOSED;
    }

    private BindingHandlerState()
    {
        // utility
    }
}
