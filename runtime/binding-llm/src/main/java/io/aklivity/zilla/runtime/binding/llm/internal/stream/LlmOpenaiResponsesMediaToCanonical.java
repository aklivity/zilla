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

final class LlmOpenaiResponsesMediaToCanonical extends LlmToolTransform
{
    private static final String DIALECT = LlmOpenaiResponsesDialectFactorySpi.NAME;
    private static final int BIT_EMIT_KEY_NAME = 1 << 0;
    private static final int BIT_EMIT_VALUE_STRING = 1 << 1;
    private static final int BIT_EMIT_KEY_NAME_2 = 1 << 2;
    private static final int BIT_EMIT_START_OBJECT = 1 << 3;
    private static final int BIT_EMIT_END_OBJECT = 1 << 4;
    private static final int BIT_PASS_END_OBJECT = 1 << 5;

    private final String canonicalType;
    private final boolean image;

    static LlmOpenaiResponsesMediaToCanonical image()
    {
        return new LlmOpenaiResponsesMediaToCanonical("image_url", true);
    }

    static LlmOpenaiResponsesMediaToCanonical document()
    {
        return new LlmOpenaiResponsesMediaToCanonical("file", false);
    }

    private LlmOpenaiResponsesMediaToCanonical(
        String canonicalType,
        boolean image)
    {
        this.canonicalType = canonicalType;
        this.image = image;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "type"))
        {
            action = DROP;
        }
        else if (image && matches(key, "image_url"))
        {
            target = "url";
            action = PASS_STRING_OR_DROP;
        }
        else if (image && (matches(key, "file_id") || matches(key, "detail")) ||
            !image && (matches(key, "file_data") || matches(key, "file_url") ||
            matches(key, "file_id") || matches(key, "filename")))
        {
            target = name(key);
            action = PASS_STRING_OR_DROP;
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
        emit(BIT_EMIT_KEY_NAME, JsonEvent.KEY_NAME, "type");
        emit(BIT_EMIT_VALUE_STRING, JsonEvent.VALUE_STRING, canonicalType);
        emit(BIT_EMIT_KEY_NAME_2, JsonEvent.KEY_NAME, canonicalType);
        emit(BIT_EMIT_START_OBJECT, JsonEvent.START_OBJECT);
    }

    @Override
    protected Status onClose(
        int depth)
    {
        emit(BIT_EMIT_END_OBJECT, JsonEvent.END_OBJECT);
        pass(BIT_PASS_END_OBJECT, JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
