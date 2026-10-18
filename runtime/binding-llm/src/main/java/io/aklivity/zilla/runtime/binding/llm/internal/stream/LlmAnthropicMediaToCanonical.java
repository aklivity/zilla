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

final class LlmAnthropicMediaToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";

    private final String canonicalType;
    private final String container;
    private final String dataMember;
    private final String urlMember;

    static LlmAnthropicMediaToCanonical image()
    {
        return new LlmAnthropicMediaToCanonical("image_url", "image_url", "url", "url");
    }

    static LlmAnthropicMediaToCanonical document()
    {
        return new LlmAnthropicMediaToCanonical("file", "file", "file_data", "file_url");
    }

    private LlmAnthropicMediaToCanonical(
        String canonicalType,
        String container,
        String dataMember,
        String urlMember)
    {
        this.canonicalType = canonicalType;
        this.container = container;
        this.dataMember = dataMember;
        this.urlMember = urlMember;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, "type"))
        {
            action = DROP;
        }
        else if (depth == 1 && matches(key, "source"))
        {
            target = container;
            action = DESCEND_KEPT;
        }
        else if (depth == 2 && (matches(key, "type") || matches(key, "media_type")))
        {
            action = DROP;
        }
        else if (depth == 2 && matches(key, "data"))
        {
            target = dataMember;
            action = PASS_DATA_URI;
        }
        else if (depth == 2 && matches(key, "url"))
        {
            target = urlMember;
            action = PASS_STRING;
        }
        else if (depth == 2 && matches(key, "file_id"))
        {
            target = "file_id";
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
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
