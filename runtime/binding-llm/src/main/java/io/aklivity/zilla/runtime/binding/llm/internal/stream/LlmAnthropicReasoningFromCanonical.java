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

final class LlmAnthropicReasoningFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final String TYPE_THINKING = "thinking";
    private static final String TYPE_REDACTED = "redacted_thinking";

    private final boolean unsigned;

    private String type;
    private boolean signed;

    LlmAnthropicReasoningFromCanonical(
        boolean sameDialect)
    {
        this(false, sameDialect);
    }

    LlmAnthropicReasoningFromCanonical(
        boolean unsigned,
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect);
        this.unsigned = unsigned;
    }

    @Override
    public void reset()
    {
        super.reset();
        type = null;
        signed = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "text"))
        {
            emitType(TYPE_THINKING);
            target = TYPE_THINKING;
            action = PASS_STRING;
        }
        else
        {
            action = unprefix(DIALECT, key);

            if (action != DROP && matches(key, "anthropic:signature"))
            {
                emitType(TYPE_THINKING);
                signed = true;
            }
            else if (action != DROP && matches(key, "anthropic:data"))
            {
                emitType(TYPE_REDACTED);
            }
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
        Status status = Status.REJECTED;

        if (type != null)
        {
            if (unsigned && !signed && TYPE_THINKING.equals(type))
            {
                emit(JsonEvent.KEY_NAME, "signature");
                emit(JsonEvent.VALUE_STRING, "");
            }

            pass(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private void emitType(
        String blockType)
    {
        if (type == null)
        {
            emit(JsonEvent.KEY_NAME, "type");
            emit(JsonEvent.VALUE_STRING, blockType);
            type = blockType;
        }
    }
}
