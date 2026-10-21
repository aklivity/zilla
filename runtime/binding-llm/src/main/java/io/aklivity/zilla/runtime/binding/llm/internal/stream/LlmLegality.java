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
    private static final String TOOL_CHOICE_AUTO = "auto";
    private static final String MEMBER_TYPE = "type";
    private static final String TYPE_TEXT = "text";
    private static final String[] TYPE_ONLY = {MEMBER_TYPE};

    static boolean harmless(
        CharSequence member,
        LlmCapturedValue value)
    {
        boolean harmless = value.event(0) == JsonEvent.VALUE_NULL;

        if (!harmless && LlmSharedMembers.matches(member, PARALLEL_TOOL_CALLS))
        {
            harmless = value.event(0) == JsonEvent.VALUE_TRUE;
        }
        else if (!harmless && LlmSharedMembers.matches(member, TOOL_CHOICE))
        {
            harmless = value.isString(0, TOOL_CHOICE_AUTO);
        }
        else if (!harmless && LlmSharedMembers.matches(member, RESPONSE_FORMAT))
        {
            harmless = value.onlyMembers(0, TYPE_ONLY) && value.isString(value.member(0, MEMBER_TYPE), TYPE_TEXT);
        }

        return harmless;
    }

    private LlmLegality()
    {
    }
}
