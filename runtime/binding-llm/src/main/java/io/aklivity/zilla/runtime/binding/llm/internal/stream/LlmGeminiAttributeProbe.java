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

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;

final class LlmGeminiAttributeProbe extends LlmAttributeProbe
{
    private static final int KEY_OTHER = -1;
    private static final int KEY_ID = 1;
    private static final int KEY_NAME = 2;

    private static final int SECTION_NONE = 0;
    private static final int SECTION_TOOL_CALL = 1;
    private static final int SECTION_TOOL_RESULT = 2;
    private static final int SECTION_TOOL_DEFINITION = 3;

    private int section;

    static boolean carries(
        LlmSectionType type)
    {
        return sectionOf(type) != SECTION_NONE;
    }

    @Override
    protected void onBegin(
        LlmSectionType type)
    {
        section = sectionOf(type);
    }

    @Override
    protected int onKey(
        int depth,
        CharSequence key)
    {
        final int code;

        if (matches(key, "id"))
        {
            code = KEY_ID;
        }
        else if (matches(key, "name"))
        {
            code = KEY_NAME;
        }
        else
        {
            code = KEY_OTHER;
        }

        return code;
    }

    @Override
    protected void onString(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        switch (section)
        {
        case SECTION_TOOL_CALL, SECTION_TOOL_RESULT:
            onFunction(depth, value, deferred);
            break;
        case SECTION_TOOL_DEFINITION:
            onDeclaration(depth, value, deferred);
            break;
        default:
            break;
        }
    }

    private void onFunction(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 2)
        {
            switch (key(2))
            {
            case KEY_ID:
                if (attributes.toolCallId() == null)
                {
                    attributes.toolCallId(text(value, deferred));
                }
                break;
            case KEY_NAME:
                if (attributes.toolName() == null)
                {
                    attributes.toolName(text(value, deferred));
                }
                break;
            default:
                break;
            }
        }

        if (attributes.toolCallId() != null && attributes.toolName() != null)
        {
            resolve();
        }
    }

    private void onDeclaration(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 1 && key(1) == KEY_NAME)
        {
            attributes.toolName(text(value, deferred));
            resolve();
        }
    }

    private String text(
        CharSequence value,
        boolean deferred)
    {
        String text = null;

        if (deferred)
        {
            reject();
        }
        else
        {
            text = value.toString();
        }

        return text;
    }

    private static int sectionOf(
        LlmSectionType type)
    {
        return switch (type)
        {
        case TOOL_CALL -> SECTION_TOOL_CALL;
        case TOOL_RESULT -> SECTION_TOOL_RESULT;
        case TOOL_DEFINITION -> SECTION_TOOL_DEFINITION;
        default -> SECTION_NONE;
        };
    }
}
