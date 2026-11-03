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

final class LlmGeminiMediaToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";

    private final String canonicalType;
    private final String container;
    private final String dataMember;
    private final String urlMember;
    private final boolean formatted;

    static LlmGeminiMediaToCanonical image()
    {
        return new LlmGeminiMediaToCanonical("image_url", "image_url", "url", "url", false);
    }

    static LlmGeminiMediaToCanonical document()
    {
        return new LlmGeminiMediaToCanonical("file", "file", "file_data", "file_url", false);
    }

    static LlmGeminiMediaToCanonical audio()
    {
        return new LlmGeminiMediaToCanonical("input_audio", "input_audio", "data", "url", true);
    }

    static LlmGeminiMediaToCanonical video()
    {
        return new LlmGeminiMediaToCanonical("input_video", "input_video", "data", "url", true);
    }

    private LlmGeminiMediaToCanonical(
        String canonicalType,
        String container,
        String dataMember,
        String urlMember,
        boolean formatted)
    {
        this.canonicalType = canonicalType;
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
        int action;

        if (depth == 1 && (matches(key, "inlineData") || matches(key, "inline_data") ||
            matches(key, "fileData") || matches(key, "file_data")))
        {
            target = container;
            action = DESCEND_KEPT;
        }
        else if (depth == 2 && (matches(key, "mimeType") || matches(key, "mime_type")))
        {
            action = DROP;
        }
        else if (depth == 2 && matches(key, "data"))
        {
            target = dataMember;
            action = formatted ? PASS_STRING : PASS_DATA_URI;
        }
        else if (depth == 2 && (matches(key, "fileUri") || matches(key, "file_uri")))
        {
            target = attributeEquals("source", LlmSectionAttributes.SOURCE_REFERENCE) ? "file_id" : urlMember;
            action = PASS_STRING;
        }
        else
        {
            action = prefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, canonicalType);
    }

    @Override
    protected Status onClose(
        int depth)
    {
        final String format = depth == 2 && formatted ? LlmGeminiMediaFormat.formatOf(attribute("media-type")) : null;

        if (format != null)
        {
            emit(JsonEvent.KEY_NAME, "format");
            emit(JsonEvent.VALUE_STRING, format);
        }

        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
