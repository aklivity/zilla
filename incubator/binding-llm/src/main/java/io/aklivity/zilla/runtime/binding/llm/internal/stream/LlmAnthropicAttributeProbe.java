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

final class LlmAnthropicAttributeProbe extends LlmAttributeProbe
{
    private static final int KEY_OTHER = -1;
    private static final int KEY_SOURCE = 1;
    private static final int KEY_MEDIA_TYPE = 2;
    private static final int KEY_DATA = 3;
    private static final int KEY_URL = 4;
    private static final int KEY_FILE_ID = 5;
    private static final int KEY_CONTENT = 6;
    private static final int KEY_ID = 7;
    private static final int KEY_NAME = 8;
    private static final int KEY_TOOL_USE_ID = 9;

    private static final int SECTION_NONE = 0;
    private static final int SECTION_MEDIA = 1;
    private static final int SECTION_TOOL_CALL = 2;
    private static final int SECTION_SERVER_TOOL_USE = 3;
    private static final int SECTION_SERVER_TOOL_RESULT = 4;
    private static final int SECTION_TOOL_DEFINITION = 5;

    private int section;

    static boolean carries(
        String type)
    {
        return sectionOf(type, null) != SECTION_NONE;
    }

    @Override
    protected void onBegin(
        String type,
        String blockType)
    {
        section = sectionOf(type, blockType);

        if ("server-tool-result".equals(type))
        {
            attributes.providerExecuted(true);
        }
    }

    @Override
    protected int onKey(
        int depth,
        CharSequence key)
    {
        final int code;

        if (matches(key, "source"))
        {
            code = KEY_SOURCE;
        }
        else if (matches(key, "media_type"))
        {
            code = KEY_MEDIA_TYPE;
        }
        else if (matches(key, "data"))
        {
            code = KEY_DATA;
        }
        else if (matches(key, "url"))
        {
            code = KEY_URL;
        }
        else if (matches(key, "file_id"))
        {
            code = KEY_FILE_ID;
        }
        else if (matches(key, "content"))
        {
            code = KEY_CONTENT;
        }
        else if (matches(key, "id"))
        {
            code = KEY_ID;
        }
        else if (matches(key, "name"))
        {
            code = KEY_NAME;
        }
        else if (matches(key, "tool_use_id"))
        {
            code = KEY_TOOL_USE_ID;
        }
        else
        {
            code = KEY_OTHER;
        }

        return code;
    }

    @Override
    protected void onStart(
        int depth)
    {
        if (section == SECTION_MEDIA && depth == 3 && key(1) == KEY_SOURCE && key(2) == KEY_CONTENT)
        {
            attributes.source(LlmSectionAttributes.SOURCE_INLINE);
            resolve();
        }
    }

    @Override
    protected void onString(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        switch (section)
        {
        case SECTION_MEDIA:
            onMedia(depth, value, deferred);
            break;
        case SECTION_TOOL_CALL:
        case SECTION_SERVER_TOOL_USE:
            onToolUse(depth, value, deferred);
            break;
        case SECTION_SERVER_TOOL_RESULT:
            onToolResult(depth, value, deferred);
            break;
        case SECTION_TOOL_DEFINITION:
            onToolDefinition(depth, value, deferred);
            break;
        default:
            break;
        }
    }

    private void onMedia(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 2 && key(1) == KEY_SOURCE)
        {
            switch (key(2))
            {
            case KEY_MEDIA_TYPE:
                attributes.mediaType(text(value, deferred));
                break;
            case KEY_DATA:
                attributes.source(LlmSectionAttributes.SOURCE_INLINE);
                resolve();
                break;
            case KEY_URL:
                attributes.url(text(value, deferred));
                resolve();
                break;
            case KEY_FILE_ID:
                attributes.reference(text(value, deferred));
                resolve();
                break;
            default:
                break;
            }
        }
    }

    private void onToolUse(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 1)
        {
            switch (key(1))
            {
            case KEY_ID:
                attributes.toolCallId(text(value, deferred));
                break;
            case KEY_NAME:
                attributes.toolName(text(value, deferred));
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

    private void onToolResult(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 1 && key(1) == KEY_TOOL_USE_ID)
        {
            attributes.toolCallId(text(value, deferred));
            resolve();
        }
    }

    private void onToolDefinition(
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
        String type,
        String blockType)
    {
        int section = SECTION_NONE;

        if (type != null)
        {
            section = switch (type)
            {
            case "user-image", "user-document" -> SECTION_MEDIA;
            case "tool-call" -> SECTION_TOOL_CALL;
            case "tool-definition" -> SECTION_TOOL_DEFINITION;
            case "server-tool-result" -> "server_tool_use".equals(blockType) ? SECTION_SERVER_TOOL_USE
                : SECTION_SERVER_TOOL_RESULT;
            default -> SECTION_NONE;
            };
        }

        return section;
    }
}
