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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

public final class A2aState
{
    private static final int INITIAL_OPENING = 0x10;
    private static final int INITIAL_OPENED = 0x20;
    private static final int INITIAL_CLOSING = 0x40;
    private static final int INITIAL_CLOSED = 0x80;
    private static final int REPLY_OPENED = 0x02;
    private static final int REPLY_OPENING = 0x01;
    private static final int REPLY_CLOSING = 0x04;
    private static final int REPLY_CLOSED = 0x08;
    private static final int REPLY_ABORT_DEFERRED = 0x100;
    private static final int REPLY_FRAMING = 0x200;

    public static int openingInitial(
        int state)
    {
        return state | INITIAL_OPENING;
    }

    public static int openInitial(
        int state)
    {
        return state | INITIAL_OPENED;
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

    public static int closingInitial(
        int state)
    {
        return state | INITIAL_CLOSING;
    }

    public static int closeInitial(
        int state)
    {
        return state | INITIAL_CLOSED;
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

    public static int openingReply(
        int state)
    {
        return state | REPLY_OPENING;
    }

    public static int openReply(
        int state)
    {
        return state | REPLY_OPENED;
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

    public static int closingReply(
        int state)
    {
        return state | REPLY_CLOSING;
    }

    public static int closeReply(
        int state)
    {
        return state | REPLY_CLOSED;
    }

    public static int deferReplyAbort(
        int state)
    {
        return state | REPLY_ABORT_DEFERRED;
    }

    public static boolean replyAbortDeferred(
        int state)
    {
        return (state & REPLY_ABORT_DEFERRED) != 0;
    }

    public static int framingReply(
        int state)
    {
        return state | REPLY_FRAMING;
    }

    public static boolean replyFraming(
        int state)
    {
        return (state & REPLY_FRAMING) != 0;
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

    public static boolean closed(
        int state)
    {
        return initialClosed(state) && replyClosed(state);
    }

    private A2aState()
    {
    }
}
