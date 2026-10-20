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

final class LlmGeminiToolResultToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";

    private int shape;

    void shape(
        int shape)
    {
        this.shape = shape;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, "functionResponse"))
        {
            action = DESCEND;
        }
        else if (depth == 2 && (matches(key, "name") || matches(key, "id")))
        {
            action = DROP;
        }
        else if (depth == 2 && matches(key, "response"))
        {
            target = "text";
            action = shape == LlmGeminiAttributeProbe.SHAPE_OTHER ? ESCAPE_OBJECT : DESCEND;
        }
        else if (depth == 3 && (matches(key, "output") || matches(key, "error")))
        {
            target = "text";
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
