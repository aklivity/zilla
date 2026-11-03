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
    private static final int KEY_IMAGE_URL = 4;
    private static final int KEY_FILE_ID = 5;
    private static final int KEY_FILE_DATA = 6;
    private static final int KEY_FILE_URL = 7;
    private static final int KEY_FILENAME = 8;
    private static final int KEY_INPUT_AUDIO = 9;
    private static final int KEY_FORMAT = 10;
    private static final int KEY_DATA = 11;

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
        else if (matches(key, "image_url"))
        {
            code = KEY_IMAGE_URL;
        }
        else if (matches(key, "file_id"))
        {
            code = KEY_FILE_ID;
        }
        else if (matches(key, "file_data"))
        {
            code = KEY_FILE_DATA;
        }
        else if (matches(key, "file_url"))
        {
            code = KEY_FILE_URL;
        }
        else if (matches(key, "filename"))
        {
            code = KEY_FILENAME;
        }
        else if (matches(key, "input_audio"))
        {
            code = KEY_INPUT_AUDIO;
        }
        else if (matches(key, "format"))
        {
            code = KEY_FORMAT;
        }
        else if (matches(key, "data"))
        {
            code = KEY_DATA;
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
        switch (type())
        {
        case USER_IMAGE:
            onImage(depth, value, deferred);
            break;
        case USER_DOCUMENT:
            onDocument(depth, value, deferred);
            break;
        case USER_AUDIO:
            onAudio(depth, value);
            break;
        default:
            onItem(depth, value, deferred);
            break;
        }
    }

    private void onImage(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 1)
        {
            switch (key(1))
            {
            case KEY_IMAGE_URL:
                if (mediaUrl(value, deferred))
                {
                    resolve();
                }
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

    private void onDocument(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 1)
        {
            switch (key(1))
            {
            case KEY_FILENAME:
                attributes.filename(text(value, deferred));
                break;
            case KEY_FILE_ID:
                attributes.reference(text(value, deferred));
                resolve();
                break;
            case KEY_FILE_DATA:
                if (fileData(value, deferred))
                {
                    resolve();
                }
                break;
            case KEY_FILE_URL:
                if (mediaUrl(value, deferred))
                {
                    resolve();
                }
                break;
            default:
                break;
            }
        }
    }

    private void onAudio(
        int depth,
        CharSequence value)
    {
        if (depth == 2 && key(1) == KEY_INPUT_AUDIO)
        {
            switch (key(2))
            {
            case KEY_FORMAT:
                attributes.mediaType(audioMediaType(value));
                break;
            case KEY_DATA:
                attributes.source(LlmSectionAttributes.SOURCE_INLINE);
                resolve();
                break;
            default:
                break;
            }
        }
    }

    private void onItem(
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

    private static String audioMediaType(
        CharSequence format)
    {
        return matches(format, "mp3") ? "audio/mpeg" : "audio/" + format;
    }
}
