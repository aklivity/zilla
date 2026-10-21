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

final class LlmOpenaiResponsesServerToolToCanonical extends LlmToolTransform
{
    private final boolean call;

    LlmOpenaiResponsesServerToolToCanonical(
        boolean call)
    {
        this.call = call;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (!matches(key, "id") &&
            !matches(key, "type") &&
            !matches(key, "status") &&
            !matches(key, "call_id") &&
            !matches(key, "name"))
        {
            target = name(key);
            action = PASS_VALUE;
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        if (call)
        {
            emit(JsonEvent.KEY_NAME, "arguments");
            emit(JsonEvent.START_OBJECT);
        }
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (call)
        {
            emit(JsonEvent.END_OBJECT);
        }

        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
