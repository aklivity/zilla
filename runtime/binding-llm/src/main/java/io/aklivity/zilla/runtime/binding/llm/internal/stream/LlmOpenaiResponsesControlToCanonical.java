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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmOpenaiResponsesControlToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";
    private static final String MEMBER_NATIVE_TOOL_CHOICE = "openai:tool_choice";
    private static final String MEMBER_SHARED_TOOL_CHOICE = "x:tool_choice";

    private static final int CAPTURE_MAX = 2048;

    private final LlmToolChoice choice;

    LlmOpenaiResponsesControlToCanonical()
    {
        this.choice = new LlmToolChoice();
        captureLimit(CAPTURE_MAX);
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        return depth == 1 && matches(key, TOOL_CHOICE) ? capture(key) : prefix(DIALECT, key);
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        if (choice.parseResponses(value))
        {
            emit(JsonEvent.KEY_NAME, MEMBER_SHARED_TOOL_CHOICE);
            choice.emitCanonical(this);
        }
        else
        {
            emit(JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE);
            emitCaptured(value, 0);
        }

        return Status.ADVANCED;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
