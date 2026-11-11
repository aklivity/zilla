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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.SERVICE_TIER;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmOpenaiResponsesControlToCanonical extends LlmToolTransform
{
    private static final String DIALECT = LlmOpenaiResponsesDialectFactorySpi.NAME;
    private static final String MEMBER_NATIVE_TOOL_CHOICE = DIALECT + ":tool_choice";
    private static final String MEMBER_SHARED_TOOL_CHOICE = "x:tool_choice";
    private static final String MEMBER_NATIVE_TEXT = DIALECT + ":text";
    private static final String MEMBER_REASONING = "reasoning";
    private static final String MEMBER_NATIVE_REASONING = DIALECT + ":reasoning";
    private static final String MEMBER_EFFORT = "effort";
    private static final String MEMBER_SHARED_REASONING_EFFORT = "x:reasoning_effort";
    private static final String MEMBER_SHARED_SERVICE_TIER = "x:service_tier";
    private static final String MEMBER_NATIVE_SERVICE_TIER = "openai:service_tier";
    private static final String MEMBER_SHARED_RESPONSE_FORMAT = "x:response_format";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_FORMAT = "format";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_JSON_SCHEMA = "json_schema";

    private static final int CAPTURE_MAX = 2048;

    private static final int BIT_RESIDUE_KEY = 1 << 0;
    private static final int BIT_RESIDUE_START = 1 << 1;
    private static final int BIT_RESIDUE_END = 1 << 2;
    private static final int BIT_FORMAT_KEY = 1 << 3;
    private static final int BIT_FORMAT_START = 1 << 4;
    private static final int BIT_TYPE_KEY = 1 << 5;
    private static final int BIT_TYPE_VALUE = 1 << 6;
    private static final int BIT_SCHEMA_KEY = 1 << 7;
    private static final int BIT_SCHEMA_START = 1 << 8;
    private static final int BIT_PRECEDING = 1 << 9;
    private static final int BIT_CAPTURED_KEY = 1 << 10;
    private static final int BIT_CAPTURED_VALUE = 1 << 11;
    private static final int BIT_CHOICE = 1 << 12;
    private static final int BIT_SCHEMA_END = 1 << 13;
    private static final int BIT_FORMAT_END = 1 << 14;
    private static final int BIT_ROOT_END = 1 << 15;

    private static final int TEXT_NONE = 0;
    private static final int TEXT_RESIDUE = 1;
    private static final int TEXT_LIFTED = 2;

    private static final int CONTAINER_TEXT = 0;
    private static final int CONTAINER_REASONING = 1;

    private static final int FORMAT_AWAITING = 0;
    private static final int FORMAT_FLATTENING = 1;
    private static final int FORMAT_PLAIN = 2;

    private final LlmToolChoice choice;
    private final LlmCapturedValue preceding;

    private int text;
    private int container;
    private int format;
    private int precedingSize;

    LlmOpenaiResponsesControlToCanonical()
    {
        this.choice = new LlmToolChoice();
        this.preceding = new LlmCapturedValue();
        captureLimit(CAPTURE_MAX);
    }

    @Override
    protected boolean resumable()
    {
        return true;
    }

    @Override
    public void reset()
    {
        super.reset();
        preceding.clear();
        text = TEXT_NONE;
        container = CONTAINER_TEXT;
        format = FORMAT_AWAITING;
        precedingSize = 0;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && (matches(key, TOOL_CHOICE) || matches(key, SERVICE_TIER)))
        {
            action = capture(key);
        }
        else if (depth == 1 && matches(key, MEMBER_TEXT))
        {
            container = CONTAINER_TEXT;
            target = MEMBER_NATIVE_TEXT;
            action = DESCEND_OR_PASS;
        }
        else if (depth == 1 && matches(key, MEMBER_REASONING))
        {
            container = CONTAINER_REASONING;
            target = MEMBER_NATIVE_REASONING;
            action = DESCEND_OR_PASS;
        }
        else if (depth == 2)
        {
            action = container == CONTAINER_REASONING ? reasoningMember(key) : textMember(key);
        }
        else if (depth == 3)
        {
            action = formatMember(key);
        }
        else
        {
            action = prefix(LlmOpenaiMembers.scope(DIALECT, key), key);
        }

        return action;
    }

    @Override
    protected Status onDescend(
        int depth)
    {
        if (depth == 2)
        {
            text = TEXT_NONE;
        }
        else if (depth == 3)
        {
            format = FORMAT_AWAITING;
            preceding.clear();
            precedingSize = 0;
            emit(BIT_FORMAT_START, JsonEvent.START_OBJECT);
        }

        return Status.ADVANCED;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        final String member = capturedMember();
        Status status = Status.ADVANCED;

        if (matches(member, SERVICE_TIER))
        {
            onServiceTier(value);
        }
        else if (matches(member, TOOL_CHOICE))
        {
            if (choice.parseResponses(value))
            {
                if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_TOOL_CHOICE))
                {
                    choice.emitCanonical(this, BIT_CHOICE);
                }
            }
            else
            {
                if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE))
                {
                    emitCaptured(BIT_CAPTURED_VALUE, value, 0);
                }
            }
        }
        else if (matches(member, MEMBER_TYPE))
        {
            status = onFormatType(value.string(0));
        }
        else
        {
            status = onPrecedingMember(member, value);
        }

        return status;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.ADVANCED;

        if (depth == 3)
        {
            status = onFormatClose();
        }
        else if (depth == 2)
        {
            onTextClose();
        }
        else
        {
            pass(BIT_ROOT_END, JsonEvent.END_OBJECT);
        }

        return status;
    }

    private void onServiceTier(
        LlmCapturedValue value)
    {
        final String tier = LlmServiceTiers.canonicalOpenai(value.string(0));

        if (tier != null)
        {
            if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_SERVICE_TIER))
            {
                emit(BIT_CAPTURED_VALUE, JsonEvent.VALUE_STRING, tier);
            }
        }
        else
        {
            if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, MEMBER_NATIVE_SERVICE_TIER))
            {
                emitCaptured(BIT_CAPTURED_VALUE, value, 0);
            }
        }
    }

    private int reasoningMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_EFFORT))
        {
            target = MEMBER_SHARED_REASONING_EFFORT;
            action = PASS_STRING_OR_DROP;

            if (closeResidue())
            {
                text = TEXT_LIFTED;
            }
        }
        else
        {
            target = name(key);
            action = PASS_VALUE;

            if (openResidue())
            {
                text = TEXT_RESIDUE;
            }
        }

        return action;
    }

    private int textMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_FORMAT))
        {
            action = DESCEND_OR_REPLACE;

            if (closeResidue() && emit(BIT_FORMAT_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_RESPONSE_FORMAT))
            {
                text = TEXT_LIFTED;
            }
        }
        else
        {
            target = name(key);
            action = PASS_VALUE;

            if (openResidue())
            {
                text = TEXT_RESIDUE;
            }
        }

        return action;
    }

    private int formatMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_TYPE))
        {
            action = capture(key);
        }
        else if (format == FORMAT_FLATTENING)
        {
            target = name(key);
            action = PASS_VALUE;
        }
        else if (format == FORMAT_AWAITING)
        {
            action = capture(key);
        }
        else
        {
            action = DROP;
        }

        return action;
    }

    private Status onFormatType(
        String type)
    {
        Status status = Status.REJECTED;

        if (TYPE_JSON_SCHEMA.equals(type))
        {
            if (emit(BIT_TYPE_KEY, JsonEvent.KEY_NAME, MEMBER_TYPE) &&
                emit(BIT_TYPE_VALUE, JsonEvent.VALUE_STRING, TYPE_JSON_SCHEMA) &&
                emit(BIT_SCHEMA_KEY, JsonEvent.KEY_NAME, MEMBER_JSON_SCHEMA) &&
                emit(BIT_SCHEMA_START, JsonEvent.START_OBJECT) &&
                emitPreceding())
            {
                format = FORMAT_FLATTENING;
                preceding.clear();
                precedingSize = 0;
            }

            status = Status.ADVANCED;
        }
        else if (TYPE_TEXT.equals(type) || TYPE_JSON_OBJECT.equals(type))
        {
            if (emit(BIT_TYPE_KEY, JsonEvent.KEY_NAME, MEMBER_TYPE) &&
                emit(BIT_TYPE_VALUE, JsonEvent.VALUE_STRING, type))
            {
                format = FORMAT_PLAIN;
            }

            status = Status.ADVANCED;
        }

        return status;
    }

    private Status onPrecedingMember(
        String member,
        LlmCapturedValue value)
    {
        final int size = precedingSize + value.count() + 1;

        Status status = Status.REJECTED;

        if (size <= CAPTURE_MAX)
        {
            precedingSize = size;
            preceding.add(JsonEvent.KEY_NAME, member, CAPTURE_MAX);

            for (int index = 0; index < value.count(); index++)
            {
                preceding.add(value.event(index), value.text(index), CAPTURE_MAX);
            }

            status = Status.ADVANCED;
        }

        return status;
    }

    private boolean emitPreceding()
    {
        for (int index = 0; index < preceding.count(); index++)
        {
            emitLoop(BIT_PRECEDING, preceding.event(index), preceding.text(index));
        }

        return endLoop(BIT_PRECEDING);
    }

    private Status onFormatClose()
    {
        Status status = Status.ADVANCED;

        if (format == FORMAT_FLATTENING)
        {
            if (emit(BIT_SCHEMA_END, JsonEvent.END_OBJECT))
            {
                emit(BIT_FORMAT_END, JsonEvent.END_OBJECT);
            }
        }
        else if (format == FORMAT_PLAIN)
        {
            emit(BIT_FORMAT_END, JsonEvent.END_OBJECT);
        }
        else
        {
            status = Status.REJECTED;
        }

        return status;
    }

    private void onTextClose()
    {
        if (text == TEXT_RESIDUE)
        {
            emit(BIT_RESIDUE_END, JsonEvent.END_OBJECT);
        }
        else if (text == TEXT_NONE)
        {
            if (emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, residueKey()) &&
                emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT))
            {
                emit(BIT_RESIDUE_END, JsonEvent.END_OBJECT);
            }
        }
    }

    private String residueKey()
    {
        return container == CONTAINER_REASONING ? MEMBER_NATIVE_REASONING : MEMBER_NATIVE_TEXT;
    }

    private boolean openResidue()
    {
        return text == TEXT_RESIDUE ||
            emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, residueKey()) &&
            emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT);
    }

    private boolean closeResidue()
    {
        return text != TEXT_RESIDUE || emit(BIT_RESIDUE_END, JsonEvent.END_OBJECT);
    }
}
