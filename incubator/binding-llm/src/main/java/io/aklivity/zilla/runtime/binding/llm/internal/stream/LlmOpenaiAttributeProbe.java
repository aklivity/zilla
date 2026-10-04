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

final class LlmOpenaiAttributeProbe extends LlmAttributeProbe
{
    private static final int KEY_OTHER = -1;
    private static final int KEY_IMAGE_URL = 1;
    private static final int KEY_URL = 2;
    private static final int KEY_FILE = 3;
    private static final int KEY_FILENAME = 4;
    private static final int KEY_FILE_ID = 5;
    private static final int KEY_FILE_DATA = 6;
    private static final int KEY_INPUT_AUDIO = 7;
    private static final int KEY_FORMAT = 8;
    private static final int KEY_DATA = 9;
    private static final int KEY_ID = 10;
    private static final int KEY_FUNCTION = 11;
    private static final int KEY_CUSTOM = 12;
    private static final int KEY_NAME = 13;

    private static final int SECTION_NONE = 0;
    private static final int SECTION_IMAGE = 1;
    private static final int SECTION_DOCUMENT = 2;
    private static final int SECTION_AUDIO = 3;
    private static final int SECTION_TOOL_CALL = 4;
    private static final int SECTION_TOOL_DEFINITION = 5;

    private int section;

    static boolean carries(
        String type)
    {
        return sectionOf(type) != SECTION_NONE;
    }

    @Override
    protected void onBegin(
        String type,
        String blockType)
    {
        section = sectionOf(type);
    }

    @Override
    protected int onKey(
        int depth,
        CharSequence key)
    {
        final int code;

        if (matches(key, "image_url"))
        {
            code = KEY_IMAGE_URL;
        }
        else if (matches(key, "url"))
        {
            code = KEY_URL;
        }
        else if (matches(key, "file"))
        {
            code = KEY_FILE;
        }
        else if (matches(key, "filename"))
        {
            code = KEY_FILENAME;
        }
        else if (matches(key, "file_id"))
        {
            code = KEY_FILE_ID;
        }
        else if (matches(key, "file_data"))
        {
            code = KEY_FILE_DATA;
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
        else if (matches(key, "id"))
        {
            code = KEY_ID;
        }
        else if (matches(key, "function"))
        {
            code = KEY_FUNCTION;
        }
        else if (matches(key, "custom"))
        {
            code = KEY_CUSTOM;
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
        case SECTION_IMAGE:
            onImage(depth, value, deferred);
            break;
        case SECTION_DOCUMENT:
            onDocument(depth, value, deferred);
            break;
        case SECTION_AUDIO:
            onAudio(depth, value);
            break;
        case SECTION_TOOL_CALL:
            onToolCall(depth, value, deferred);
            break;
        case SECTION_TOOL_DEFINITION:
            onToolDefinition(depth, value, deferred);
            break;
        default:
            break;
        }
    }

    private void onImage(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 2 && key(1) == KEY_IMAGE_URL && key(2) == KEY_URL)
        {
            mediaUrl(value, deferred);
            resolve();
        }
    }

    private void onDocument(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 2 && key(1) == KEY_FILE)
        {
            switch (key(2))
            {
            case KEY_FILENAME:
                attributes.filename(text(value, deferred));
                break;
            case KEY_FILE_ID:
                attributes.reference(text(value, deferred));
                resolve();
                break;
            case KEY_FILE_DATA:
                fileData(value);
                resolve();
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

    private void onToolCall(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 1 && key(1) == KEY_ID)
        {
            attributes.toolCallId(text(value, deferred));
        }
        else if (depth == 2 && key(1) == KEY_FUNCTION && key(2) == KEY_NAME)
        {
            attributes.toolName(text(value, deferred));
        }

        if (attributes.toolCallId() != null && attributes.toolName() != null)
        {
            resolve();
        }
    }

    private void onToolDefinition(
        int depth,
        CharSequence value,
        boolean deferred)
    {
        if (depth == 2 && (key(1) == KEY_FUNCTION || key(1) == KEY_CUSTOM) && key(2) == KEY_NAME)
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

    private static String audioMediaType(
        CharSequence format)
    {
        return matches(format, "mp3") ? "audio/mpeg" : "audio/" + format;
    }

    private static int sectionOf(
        String type)
    {
        int section = SECTION_NONE;

        if (type != null)
        {
            section = switch (type)
            {
            case "user-image" -> SECTION_IMAGE;
            case "user-document" -> SECTION_DOCUMENT;
            case "user-audio" -> SECTION_AUDIO;
            case "tool-call" -> SECTION_TOOL_CALL;
            case "tool-definition" -> SECTION_TOOL_DEFINITION;
            default -> SECTION_NONE;
            };
        }

        return section;
    }
}
