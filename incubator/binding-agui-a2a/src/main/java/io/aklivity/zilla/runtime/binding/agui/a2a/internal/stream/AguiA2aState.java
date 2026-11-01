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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.stream;

final class AguiA2aState
{
    private static final int INITIAL_OPENING = 0x10;
    private static final int INITIAL_CLOSING = 0x40;
    private static final int INITIAL_CLOSED = 0x80;
    private static final int REPLY_OPENED = 0x02;
    private static final int REPLY_CLOSED = 0x08;

    static int openingInitial(
        int state)
    {
        return state | INITIAL_OPENING;
    }

    static int closingInitial(
        int state)
    {
        return state | INITIAL_CLOSING;
    }

    static int closeInitial(
        int state)
    {
        return state | INITIAL_CLOSING | INITIAL_CLOSED;
    }

    static boolean initialOpening(
        int state)
    {
        return (state & INITIAL_OPENING) != 0;
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

    static int openReply(
        int state)
    {
        return state | REPLY_OPENED;
    }

    static int closeReply(
        int state)
    {
        return state | REPLY_CLOSED;
    }

    static boolean replyOpened(
        int state)
    {
        return (state & REPLY_OPENED) != 0;
    }

    static boolean replyClosed(
        int state)
    {
        return (state & REPLY_CLOSED) != 0;
    }

    private AguiA2aState()
    {
    }
}
