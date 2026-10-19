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

final class LlmAnthropicReasoningToEvent extends LlmToolTransform
{
    private final String member;
    private final String eventType;
    private final String objectKey;
    private final String objectType;
    private final String valueKey;

    private int index;
    private boolean opened;

    private LlmAnthropicReasoningToEvent(
        String member,
        String eventType,
        String objectKey,
        String objectType,
        String valueKey)
    {
        super(MODE_EXTRACT);
        this.member = member;
        this.eventType = eventType;
        this.objectKey = objectKey;
        this.objectType = objectType;
        this.valueKey = valueKey;
    }

    static LlmAnthropicReasoningToEvent signature()
    {
        return new LlmAnthropicReasoningToEvent("anthropic:signature", "content_block_delta", "delta", "signature_delta",
            "signature");
    }

    static LlmAnthropicReasoningToEvent redacted()
    {
        return new LlmAnthropicReasoningToEvent("anthropic:data", "content_block_start", "content_block", "redacted_thinking",
            "data");
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

        if (!opened && matches(key, member))
        {
            open();
            emit(JsonEvent.KEY_NAME, valueKey);
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
        Status status = Status.COMPLETED;

        if (opened)
        {
            emit(JsonEvent.END_OBJECT);
            emit(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private void open()
    {
        emit(JsonEvent.START_OBJECT);
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, eventType);
        emit(JsonEvent.KEY_NAME, "index");
        emit(JsonEvent.VALUE_NUMBER, Integer.toString(index));
        emit(JsonEvent.KEY_NAME, objectKey);
        emit(JsonEvent.START_OBJECT);
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, objectType);

        opened = true;
    }
}
