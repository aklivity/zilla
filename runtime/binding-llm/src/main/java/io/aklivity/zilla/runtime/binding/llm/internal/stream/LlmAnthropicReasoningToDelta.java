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

final class LlmAnthropicReasoningToDelta extends LlmToolTransform
{
    private static final String DELTA_THINKING = "thinking_delta";
    private static final String DELTA_SIGNATURE = "signature_delta";

    private int index;
    private boolean opened;

    LlmAnthropicReasoningToDelta()
    {
        super(MODE_EXTRACT);
    }

    void index(
        int index)
    {
        this.index = index;
    }

    @Override
    public void reset()
    {
        super.reset();
        opened = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (!opened && matches(key, "text"))
        {
            open(DELTA_THINKING);
            emit(JsonEvent.KEY_NAME, "thinking");
            action = PASS_STRING;
        }
        else if (!opened && matches(key, "anthropic:signature"))
        {
            open(DELTA_SIGNATURE);
            emit(JsonEvent.KEY_NAME, "signature");
            action = PASS_STRING;
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
        if (opened)
        {
            emit(JsonEvent.END_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }

    private void open(
        String deltaType)
    {
        emit(JsonEvent.START_OBJECT);
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, "content_block_delta");
        emit(JsonEvent.KEY_NAME, "index");
        emit(JsonEvent.VALUE_NUMBER, Integer.toString(index));
        emit(JsonEvent.KEY_NAME, "delta");
        emit(JsonEvent.START_OBJECT);
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, deltaType);

        opened = true;
    }
}
