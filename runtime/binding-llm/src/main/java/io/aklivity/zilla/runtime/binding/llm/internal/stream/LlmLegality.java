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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.PARALLEL_TOOL_CALLS;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.RESPONSE_FORMAT;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

final class LlmLegality
{
    static final int UNDECIDED = 0;

    private static final int STARTED = 1 << 0;
    private static final int HARMLESS = 1 << 1;
    private static final int ONLY_TYPE = 1 << 2;
    private static final int TYPE_PENDING = 1 << 3;
    private static final int TYPE_SEEN = 1 << 4;
    private static final int TYPE_TEXT = 1 << 5;
    private static final int MEMBER_DEPTH = 1;
    private static final String TOOL_CHOICE_AUTO = "auto";
    private static final String MEMBER_TYPE = "type";
    private static final String TYPE_TEXT_VALUE = "text";

    static int advance(
        CharSequence member,
        int state,
        int depth,
        JsonEvent event,
        CharSequence text)
    {
        int next = state;

        if ((state & STARTED) == 0)
        {
            next = STARTED | first(member, event, text);
        }
        else if ((state & ONLY_TYPE) != 0)
        {
            next = type(state, depth, event, text);
        }

        return next;
    }

    static boolean harmless(
        int state)
    {
        return (state & HARMLESS) != 0 || (state & (ONLY_TYPE | TYPE_TEXT)) == (ONLY_TYPE | TYPE_TEXT);
    }

    private static int first(
        CharSequence member,
        JsonEvent event,
        CharSequence text)
    {
        int state = 0;

        if (event == JsonEvent.VALUE_NULL)
        {
            state = HARMLESS;
        }
        else if (LlmBehaviour.listed(member))
        {
            state = LlmBehaviour.harmless(member, event, text) ? HARMLESS : 0;
        }
        else if (LlmSharedMembers.matches(member, PARALLEL_TOOL_CALLS))
        {
            state = event == JsonEvent.VALUE_TRUE ? HARMLESS : 0;
        }
        else if (LlmSharedMembers.matches(member, TOOL_CHOICE))
        {
            state = event == JsonEvent.VALUE_STRING && TOOL_CHOICE_AUTO.contentEquals(text) ? HARMLESS : 0;
        }
        else if (LlmSharedMembers.matches(member, RESPONSE_FORMAT))
        {
            state = ONLY_TYPE;
        }

        return state;
    }

    private static int type(
        int state,
        int depth,
        JsonEvent event,
        CharSequence text)
    {
        int next = state;

        if ((state & TYPE_PENDING) != 0)
        {
            next = state & ~TYPE_PENDING | TYPE_SEEN;
            next |= event == JsonEvent.VALUE_STRING && TYPE_TEXT_VALUE.contentEquals(text) ? TYPE_TEXT : 0;
        }
        else if (event == JsonEvent.KEY_NAME && depth == MEMBER_DEPTH)
        {
            if (!MEMBER_TYPE.contentEquals(text))
            {
                next = state & ~ONLY_TYPE;
            }
            else if ((state & TYPE_SEEN) == 0)
            {
                next = state | TYPE_PENDING;
            }
        }

        return next;
    }

    private LlmLegality()
    {
    }
}
