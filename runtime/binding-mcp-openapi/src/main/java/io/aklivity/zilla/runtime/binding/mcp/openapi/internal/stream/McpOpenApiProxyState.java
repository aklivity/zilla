/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.stream;

final class McpOpenApiProxyState
{
    static final int INITIAL_OPENING = 0x01;
    static final int INITIAL_OPENED = 0x02;
    static final int INITIAL_CLOSED = 0x04;
    static final int REPLY_OPENING = 0x10;
    static final int REPLY_OPENED = 0x20;
    static final int REPLY_CLOSED = 0x40;

    static int openingInitial(
        int state)
    {
        return state | INITIAL_OPENING;
    }

    static int openedInitial(
        int state)
    {
        return state | INITIAL_OPENED;
    }

    static int closedInitial(
        int state)
    {
        return (state | INITIAL_CLOSED) & ~INITIAL_OPENING & ~INITIAL_OPENED;
    }

    static int openingReply(
        int state)
    {
        return state | REPLY_OPENING;
    }

    static int openedReply(
        int state)
    {
        return state | REPLY_OPENED;
    }

    static int closedReply(
        int state)
    {
        return (state | REPLY_CLOSED) & ~REPLY_OPENING & ~REPLY_OPENED;
    }

    private McpOpenApiProxyState()
    {
    }
}
