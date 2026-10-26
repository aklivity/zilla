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
import io.aklivity.zilla.runtime.common.json.JsonEvent;

final class LlmGeminiAttributeProbe extends LlmAttributeProbe
{
    private static final int KEY_OTHER = -1;
    private static final int KEY_ID = 1;
    private static final int KEY_NAME = 2;
    private static final int KEY_RESPONSE = 3;
    private static final int KEY_OUTPUT = 4;
    private static final int KEY_ERROR = 5;
    private static final int KEY_MIME_TYPE = 6;
    private static final int KEY_DATA = 7;
    private static final int KEY_FILE_URI = 8;
    private static final int KEY_THOUGHT = 9;

    static final int SHAPE_OTHER = 0;
    static final int SHAPE_OUTPUT = 1;
    static final int SHAPE_ERROR = 2;

    private static final int SECTION_NONE = 0;
    private static final int SECTION_TOOL_CALL = 1;
    private static final int SECTION_TOOL_RESULT = 2;
    private static final int SECTION_TOOL_DEFINITION = 3;
    private static final int SECTION_MEDIA = 4;
    private static final int SECTION_TEXT = 5;
    private static final String FILES_SEGMENT = "/files/";

    private int section;
    private int responseMembers;
    private int responseKey;
    private boolean responseText;

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
        responseMembers = 0;
        responseKey = KEY_OTHER;
        responseText = false;
    }

    boolean unclassified()
    {
        return section == SECTION_MEDIA && attributes.mediaType() == null;
    }

    int settle()
    {
        final boolean single = responseMembers == 1 && responseText;
        final int shape = !single ? SHAPE_OTHER : responseKey == KEY_ERROR ? SHAPE_ERROR : SHAPE_OUTPUT;

        if (shape == SHAPE_ERROR)
        {
            attributes.isError(LlmSectionAttributes.TRUE);
        }

        return shape;
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
        else if (matches(key, "response"))
        {
            code = KEY_RESPONSE;
        }
        else if (matches(key, "output"))
        {
            code = KEY_OUTPUT;
        }
        else if (matches(key, "error"))
        {
            code = KEY_ERROR;
        }
        else if (matches(key, "mimeType") || matches(key, "mime_type"))
        {
            code = KEY_MIME_TYPE;
        }
        else if (matches(key, "data"))
        {
            code = KEY_DATA;
        }
        else if (matches(key, "fileUri") || matches(key, "file_uri"))
        {
            code = KEY_FILE_URI;
        }
        else if (matches(key, "thought"))
        {
            code = KEY_THOUGHT;
        }
        else
        {
            code = KEY_OTHER;
        }

        if (section == SECTION_TOOL_RESULT && depth == 3 && key(2) == KEY_RESPONSE && ++responseMembers == 1)
        {
            responseKey = code;
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
        case SECTION_MEDIA:
            onMedia(depth, value, deferred);
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
        if (section == SECTION_TOOL_RESULT && depth == 3 && key(2) == KEY_RESPONSE && responseMembers == 1 &&
            (key(3) == KEY_OUTPUT || key(3) == KEY_ERROR))
        {
            responseText = true;
        }

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

        if (section == SECTION_TOOL_CALL && attributes.toolCallId() != null && attributes.toolName() != null)
        {
            resolve();
        }
    }

    @Override
    protected void onLiteral(
        int depth,
        JsonEvent event)
    {
        if (section == SECTION_TEXT && depth == 1 && key(1) == KEY_THOUGHT && event == JsonEvent.VALUE_TRUE)
        {
            retype(LlmSectionType.ASSISTANT_REASONING);
            resolve();
        }
    }

    private void onMedia(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 2)
        {
            switch (key(2))
            {
            case KEY_MIME_TYPE:
                attributes.mediaType(text(value, deferred));
                retype(typeOf(value));
                break;
            case KEY_DATA:
                attributes.source(LlmSectionAttributes.SOURCE_INLINE);
                break;
            case KEY_FILE_URI:
                final String uri = text(value, deferred);

                if (isReference(uri))
                {
                    attributes.reference(uri);
                }
                else
                {
                    attributes.url();
                }
                break;
            default:
                break;
            }

            if (attributes.mediaType() != null && attributes.source() != null)
            {
                resolve();
            }
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
        case USER_IMAGE, USER_DOCUMENT, USER_AUDIO, USER_VIDEO -> SECTION_MEDIA;
        case ASSISTANT_TEXT -> SECTION_TEXT;
        default -> SECTION_NONE;
        };
    }

    private static LlmSectionType typeOf(
        CharSequence mediaType)
    {
        return startsWith(mediaType, "image/") ? LlmSectionType.USER_IMAGE
            : startsWith(mediaType, "audio/") ? LlmSectionType.USER_AUDIO
            : startsWith(mediaType, "video/") ? LlmSectionType.USER_VIDEO
            : LlmSectionType.USER_DOCUMENT;
    }

    private static boolean isReference(
        String uri)
    {
        final boolean web = uri != null && (uri.startsWith("https://") || uri.startsWith("http://"));

        return uri != null && (!web || uri.contains(FILES_SEGMENT));
    }

    private static boolean startsWith(
        CharSequence value,
        String prefix)
    {
        final int length = prefix.length();
        boolean starts = value.length() >= length;

        for (int index = 0; starts && index < length; index++)
        {
            starts = value.charAt(index) == prefix.charAt(index);
        }

        return starts;
    }
}
