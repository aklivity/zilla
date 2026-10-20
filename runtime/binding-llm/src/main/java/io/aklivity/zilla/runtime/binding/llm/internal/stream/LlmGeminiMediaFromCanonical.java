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

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmGeminiMediaFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";

    private final String container;
    private final String dataMember;
    private final String urlMember;
    private final boolean formatted;

    static LlmGeminiMediaFromCanonical image()
    {
        return new LlmGeminiMediaFromCanonical("image_url", "url", "url", false);
    }

    static LlmGeminiMediaFromCanonical document()
    {
        return new LlmGeminiMediaFromCanonical("file", "file_data", "file_url", false);
    }

    static LlmGeminiMediaFromCanonical audio()
    {
        return new LlmGeminiMediaFromCanonical("input_audio", "data", "url", true);
    }

    static LlmGeminiMediaFromCanonical video()
    {
        return new LlmGeminiMediaFromCanonical("input_video", "data", "url", true);
    }

    private LlmGeminiMediaFromCanonical(
        String container,
        String dataMember,
        String urlMember,
        boolean formatted)
    {
        this.container = container;
        this.dataMember = dataMember;
        this.urlMember = urlMember;
        this.formatted = formatted;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        final boolean inline = attributeEquals("source", LlmSectionAttributes.SOURCE_INLINE);
        int action;

        if (depth == 1 && matches(key, container))
        {
            target = inline ? "inlineData" : "fileData";
            action = DESCEND_KEPT;
        }
        else if (depth == 2 && matches(key, dataMember) && inline)
        {
            target = "data";
            action = formatted ? PASS_STRING : STRIP_DATA_URI;
        }
        else if (depth == 2 && (matches(key, dataMember) || matches(key, urlMember) || matches(key, "file_id")))
        {
            target = "fileUri";
            action = PASS_STRING;
        }
        else
        {
            action = contains(key, ':') ? unprefix(DIALECT, key) : DROP;
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onDescend(
        int depth)
    {
        final boolean sourced = attributeEquals("source", LlmSectionAttributes.SOURCE_INLINE) ||
            attributeEquals("source", LlmSectionAttributes.SOURCE_URL) ||
            attributeEquals("source", LlmSectionAttributes.SOURCE_REFERENCE);
        final String mediaType = sourced ? attribute("media-type") : null;
        Status status = sourced ? Status.ADVANCED : Status.REJECTED;

        if (mediaType != null)
        {
            emit(JsonEvent.KEY_NAME, "mimeType");
            emit(JsonEvent.VALUE_STRING, mediaType);
        }

        return status;
    }

    @Override
    protected Status onClose(
        int depth)
    {
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }

    private static boolean contains(
        CharSequence key,
        char value)
    {
        boolean found = false;

        for (int index = 0; !found && index < key.length(); index++)
        {
            found = key.charAt(index) == value;
        }

        return found;
    }
}
