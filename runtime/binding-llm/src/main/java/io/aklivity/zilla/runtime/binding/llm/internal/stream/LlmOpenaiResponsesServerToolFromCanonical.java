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

final class LlmOpenaiResponsesServerToolFromCanonical extends LlmToolTransform
{
    static final int PART_MEMBERS = 0;
    static final int PART_CALL = 1;
    static final int PART_RESULT = 2;
    static final int PART_OUTPUT = 3;
    static final int PART_EXTRAS = 4;

    private static final String STATUS_COMPLETED = "completed";
    private static final int BIT_EMIT_KEY_NAME = 1 << 0;
    private static final int BIT_EMIT_VALUE_STRING = 1 << 1;
    private static final int BIT_EMIT_KEY_NAME_2 = 1 << 2;
    private static final int BIT_EMIT_VALUE_STRING_2 = 1 << 3;
    private static final int BIT_EMIT_KEY_NAME_3 = 1 << 4;
    private static final int BIT_EMIT_VALUE_STRING_3 = 1 << 5;
    private static final int BIT_EMIT_KEY_NAME_4 = 1 << 6;
    private static final int BIT_EMIT_VALUE_STRING_4 = 1 << 7;
    private static final int BIT_EMIT_KEY_NAME_5 = 1 << 8;
    private static final int BIT_EMIT_VALUE_STRING_5 = 1 << 9;
    private static final int BIT_EMIT_KEY_NAME_6 = 1 << 10;
    private static final int BIT_EMIT_VALUE_STRING_6 = 1 << 11;
    private static final int BIT_PASS_END_OBJECT = 1 << 12;

    private int part;
    private String itemType;
    private String exclude;

    LlmOpenaiResponsesServerToolFromCanonical(
        String dialect)
    {
        super(MODE_OBJECT, dialect);
    }

    void configure(
        int part,
        String itemType,
        String exclude)
    {
        this.part = part;
        this.itemType = itemType;
        this.exclude = exclude;
    }

    @Override
    protected boolean resumable()
    {
        return true;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && part == PART_EXTRAS && matches(key, "arguments"))
        {
            action = DROP;
        }
        else if (depth == 1 && part != PART_RESULT && part != PART_OUTPUT && part != PART_EXTRAS &&
            matches(key, "arguments"))
        {
            action = DESCEND;
        }
        else if (depth == 2 && exclude != null && matches(key, exclude))
        {
            action = DROP;
        }
        else if (depth == 2 || part == PART_RESULT || part == PART_OUTPUT)
        {
            target = name(key);
            action = PASS_VALUE;
        }
        else
        {
            action = unprefix(key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        if (part == PART_CALL || part == PART_OUTPUT)
        {
            emit(BIT_EMIT_KEY_NAME, JsonEvent.KEY_NAME, "type");
            emit(BIT_EMIT_VALUE_STRING, JsonEvent.VALUE_STRING, itemType);

            if (part == PART_OUTPUT)
            {
                emit(BIT_EMIT_KEY_NAME_2, JsonEvent.KEY_NAME, "call_id");
                emit(BIT_EMIT_VALUE_STRING_2, JsonEvent.VALUE_STRING, attribute("tool-call-id"));
            }
            else
            {
                if (!LlmOpenaiResponsesItems.hasCallId(itemType))
                {
                    emit(BIT_EMIT_KEY_NAME_3, JsonEvent.KEY_NAME, "id");
                    emit(BIT_EMIT_VALUE_STRING_3, JsonEvent.VALUE_STRING, attribute("tool-call-id"));
                }

                if (LlmOpenaiResponsesItems.hasStatus(itemType))
                {
                    emit(BIT_EMIT_KEY_NAME_4, JsonEvent.KEY_NAME, "status");
                    emit(BIT_EMIT_VALUE_STRING_4, JsonEvent.VALUE_STRING, STATUS_COMPLETED);
                }
            }
        }
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.ADVANCED;

        if (depth == 1)
        {
            if (part == PART_CALL)
            {
                if (LlmOpenaiResponsesItems.hasCallId(itemType))
                {
                    emit(BIT_EMIT_KEY_NAME_5, JsonEvent.KEY_NAME, "call_id");
                    emit(BIT_EMIT_VALUE_STRING_5, JsonEvent.VALUE_STRING, attribute("tool-call-id"));
                }

                if (LlmOpenaiResponsesItems.hasName(itemType))
                {
                    emit(BIT_EMIT_KEY_NAME_6, JsonEvent.KEY_NAME, "name");
                    emit(BIT_EMIT_VALUE_STRING_6, JsonEvent.VALUE_STRING, attribute("tool-name"));
                }
            }

            pass(BIT_PASS_END_OBJECT, JsonEvent.END_OBJECT);
        }

        return status;
    }
}
