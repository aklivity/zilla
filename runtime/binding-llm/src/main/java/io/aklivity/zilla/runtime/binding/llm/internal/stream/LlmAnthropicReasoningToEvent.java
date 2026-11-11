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
    private static final int BIT_EVENT_START = 1 << 0;
    private static final int BIT_EVENT_TYPE_KEY = 1 << 1;
    private static final int BIT_EVENT_TYPE_VALUE = 1 << 2;
    private static final int BIT_INDEX_KEY = 1 << 3;
    private static final int BIT_INDEX_VALUE = 1 << 4;
    private static final int BIT_OBJECT_KEY = 1 << 5;
    private static final int BIT_OBJECT_START = 1 << 6;
    private static final int BIT_OBJECT_TYPE_KEY = 1 << 7;
    private static final int BIT_OBJECT_TYPE_VALUE = 1 << 8;
    private static final int BIT_VALUE_KEY = 1 << 9;
    private static final int BIT_OBJECT_END = 1 << 10;
    private static final int BIT_EVENT_END = 1 << 11;

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
        return new LlmAnthropicReasoningToEvent("x:signature", "content_block_delta", "delta", "signature_delta",
            "signature");
    }

    static LlmAnthropicReasoningToEvent redacted()
    {
        return new LlmAnthropicReasoningToEvent("x:redacted_data", "content_block_start", "content_block", "redacted_thinking",
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
            if (open() && emit(BIT_VALUE_KEY, JsonEvent.KEY_NAME, valueKey))
            {
                opened = true;
            }

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
            emit(BIT_OBJECT_END, JsonEvent.END_OBJECT);
            emit(BIT_EVENT_END, JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private boolean open()
    {
        return emit(BIT_EVENT_START, JsonEvent.START_OBJECT) &&
            emit(BIT_EVENT_TYPE_KEY, JsonEvent.KEY_NAME, "type") &&
            emit(BIT_EVENT_TYPE_VALUE, JsonEvent.VALUE_STRING, eventType) &&
            emit(BIT_INDEX_KEY, JsonEvent.KEY_NAME, "index") &&
            emit(BIT_INDEX_VALUE, JsonEvent.VALUE_NUMBER, Integer.toString(index)) &&
            emit(BIT_OBJECT_KEY, JsonEvent.KEY_NAME, objectKey) &&
            emit(BIT_OBJECT_START, JsonEvent.START_OBJECT) &&
            emit(BIT_OBJECT_TYPE_KEY, JsonEvent.KEY_NAME, "type") &&
            emit(BIT_OBJECT_TYPE_VALUE, JsonEvent.VALUE_STRING, objectType);
    }
}
