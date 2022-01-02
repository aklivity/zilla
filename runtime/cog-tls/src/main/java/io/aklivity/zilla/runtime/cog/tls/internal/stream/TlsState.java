/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.tls.internal.stream;

public final class TlsState
{
    private static final int INITIAL_OPENING = 0x10;
    private static final int INITIAL_OPENED = 0x20;
    private static final int INITIAL_CLOSING = 0x40;
    private static final int INITIAL_CLOSED = 0x80;
    private static final int REPLY_OPENED = 0x01;
    private static final int REPLY_OPENING = 0x02;
    private static final int REPLY_CLOSING = 0x04;
    private static final int REPLY_CLOSED = 0x08;

    static int openingInitial(
        int state)
    {
        return state | INITIAL_OPENING;
    }

    static int openInitial(
        int state)
    {
        return openingInitial(state) | INITIAL_OPENED;
    }

    static boolean initialOpening(
        int state)
    {
        return (state & INITIAL_OPENING) != 0;
    }

    static boolean initialOpened(
        int state)
    {
        return (state & INITIAL_OPENED) != 0;
    }

    static int closingInitial(
        int state)
    {
        return state | INITIAL_CLOSING;
    }

    static int closeInitial(
        int state)
    {
        return closingInitial(state) | INITIAL_CLOSED;
    }

    static boolean initialClosing(
        int state)
    {
        return (state & INITIAL_CLOSING) != 0;
    }

    static boolean initialClosed(
        int state)
    {
        return (state & INITIAL_CLOSED) != 0;
    }

    static int openingReply(
        int state)
    {
        return state | REPLY_OPENING;
    }

    static int openReply(
        int state)
    {
        return openingReply(state) | REPLY_OPENED;
    }

    static boolean replyOpening(
            int state)
    {
        return (state & REPLY_OPENING) != 0;
    }

    static boolean replyOpened(
            int state)
    {
        return (state & REPLY_OPENED) != 0;
    }

    static int closingReply(
        int state)
    {
        return state | REPLY_CLOSING;
    }

    static int closeReply(
        int state)
    {
        return closingReply(state) | REPLY_CLOSED;
    }

    static boolean replyClosing(
        int state)
    {
        return (state & REPLY_CLOSING) != 0;
    }

    static boolean replyClosed(
        int state)
    {
        return (state & REPLY_CLOSED) != 0;
    }

    private TlsState()
    {
        // utility
    }
}
