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

final class LlmOpenaiResponsesAttributeProbe extends LlmAttributeProbe
{
    private static final int KEY_OTHER = -1;
    private static final int KEY_ID = 1;
    private static final int KEY_CALL_ID = 2;
    private static final int KEY_NAME = 3;

    private String itemType;
    private String id;
    private String callId;
    private String name;

    void configure(
        String itemType,
        String id,
        String name)
    {
        this.itemType = itemType;
        this.id = id;
        this.callId = null;
        this.name = name;

        update();
    }

    @Override
    protected void onBegin(
        LlmSectionType type)
    {
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
        else if (matches(key, "call_id"))
        {
            code = KEY_CALL_ID;
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
        if (depth == 1)
        {
            switch (key(1))
            {
            case KEY_ID:
                id = text(value, deferred);
                break;
            case KEY_CALL_ID:
                callId = text(value, deferred);
                break;
            case KEY_NAME:
                name = text(value, deferred);
                break;
            default:
                break;
            }

            update();
        }
    }

    private void update()
    {
        switch (type())
        {
        case TOOL_CALL, SERVER_TOOL_CALL:
            final String toolCallId = LlmOpenaiResponsesItems.hasCallId(itemType) ? callId : id;
            final String toolName = name != null || LlmOpenaiResponsesItems.hasName(itemType)
                ? name
                : LlmOpenaiResponsesItems.toolName(itemType);

            attributes.toolCallId(toolCallId);
            attributes.toolName(toolName);

            if (toolCallId != null && toolName != null)
            {
                resolve();
            }
            break;
        case SERVER_TOOL_RESULT:
            attributes.toolCallId(LlmOpenaiResponsesItems.hasCallId(itemType) || callId != null ? callId : id);

            if (attributes.toolCallId() != null)
            {
                resolve();
            }
            break;
        case TOOL_DEFINITION:
            attributes.toolName(name);

            if (name != null)
            {
                resolve();
            }
            break;
        case SERVER_TOOL_DEFINITION:
            attributes.toolName(name != null ? name : itemType);

            if (name != null)
            {
                resolve();
            }
            break;
        default:
            break;
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
}
