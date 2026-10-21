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

final class LlmOpenaiResponsesMediaFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";

    private final String part;
    private final String container;

    static LlmOpenaiResponsesMediaFromCanonical image(
        boolean sameDialect)
    {
        return new LlmOpenaiResponsesMediaFromCanonical("input_image", "image_url", sameDialect);
    }

    static LlmOpenaiResponsesMediaFromCanonical document(
        boolean sameDialect)
    {
        return new LlmOpenaiResponsesMediaFromCanonical("input_file", "file", sameDialect);
    }

    private LlmOpenaiResponsesMediaFromCanonical(
        String part,
        String container,
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect);
        this.part = part;
        this.container = container;
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
        else if (depth == 1 && matches(key, container))
        {
            action = DESCEND;
        }
        else if (depth == 2 && matches(key, "url") && "image_url".equals(container))
        {
            target = "image_url";
            action = PASS_STRING;
        }
        else
        {
            action = unprefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, part);
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (depth == 1)
        {
            pass(JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }
}
