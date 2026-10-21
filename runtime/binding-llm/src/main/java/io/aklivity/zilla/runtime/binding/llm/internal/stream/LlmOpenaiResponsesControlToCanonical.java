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
    private static final String MEMBER_NATIVE_TEXT = "openai:text";
    private static final String MEMBER_SHARED_RESPONSE_FORMAT = "x:response_format";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_FORMAT = "format";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_JSON_SCHEMA = "json_schema";

    private static final int CAPTURE_MAX = 2048;

    private static final int TEXT_NONE = 0;
    private static final int TEXT_RESIDUE = 1;
    private static final int TEXT_LIFTED = 2;

    private static final int FORMAT_AWAITING = 0;
    private static final int FORMAT_FLATTENING = 1;
    private static final int FORMAT_PLAIN = 2;

    private final LlmToolChoice choice;
    private final LlmCapturedValue preceding;

    private int text;
    private int format;
    private int precedingSize;

    LlmOpenaiResponsesControlToCanonical()
    {
        this.choice = new LlmToolChoice();
        this.preceding = new LlmCapturedValue();
        captureLimit(CAPTURE_MAX);
    }

    @Override
    public void reset()
    {
        super.reset();
        preceding.clear();
        text = TEXT_NONE;
        format = FORMAT_AWAITING;
        precedingSize = 0;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, TOOL_CHOICE))
        {
            action = capture(key);
        }
        else if (depth == 1 && matches(key, MEMBER_TEXT))
        {
            target = MEMBER_NATIVE_TEXT;
            action = DESCEND_OR_PASS;
        }
        else if (depth == 2)
        {
            action = textMember(key);
        }
        else if (depth == 3)
        {
            action = formatMember(key);
        }
        else
        {
            action = prefix(DIALECT, key);
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
            emit(JsonEvent.START_OBJECT);
        }

        return Status.ADVANCED;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        final String member = capturedMember();
        Status status = Status.ADVANCED;

        if (matches(member, TOOL_CHOICE))
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
            pass(JsonEvent.END_OBJECT);
        }

        return status;
    }

    private int textMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_FORMAT))
        {
            closeResidue();
            text = TEXT_LIFTED;
            emit(JsonEvent.KEY_NAME, MEMBER_SHARED_RESPONSE_FORMAT);
            action = DESCEND_OR_REPLACE;
        }
        else
        {
            openResidue();
            target = name(key);
            action = PASS_VALUE;
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
            format = FORMAT_FLATTENING;
            emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
            emit(JsonEvent.VALUE_STRING, TYPE_JSON_SCHEMA);
            emit(JsonEvent.KEY_NAME, MEMBER_JSON_SCHEMA);
            emit(JsonEvent.START_OBJECT);
            emitPreceding();
            status = Status.ADVANCED;
        }
        else if (TYPE_TEXT.equals(type) || TYPE_JSON_OBJECT.equals(type))
        {
            format = FORMAT_PLAIN;
            emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
            emit(JsonEvent.VALUE_STRING, type);
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

    private void emitPreceding()
    {
        for (int index = 0; index < preceding.count(); index++)
        {
            final String text = preceding.text(index);

            if (text != null)
            {
                emit(preceding.event(index), text);
            }
            else
            {
                emit(preceding.event(index));
            }
        }

        preceding.clear();
        precedingSize = 0;
    }

    private Status onFormatClose()
    {
        Status status = Status.ADVANCED;

        if (format == FORMAT_FLATTENING)
        {
            emit(JsonEvent.END_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }
        else if (format == FORMAT_PLAIN)
        {
            emit(JsonEvent.END_OBJECT);
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
            emit(JsonEvent.END_OBJECT);
        }
        else if (text == TEXT_NONE)
        {
            emit(JsonEvent.KEY_NAME, MEMBER_NATIVE_TEXT);
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }

        text = TEXT_NONE;
    }

    private void openResidue()
    {
        if (text != TEXT_RESIDUE)
        {
            text = TEXT_RESIDUE;
            emit(JsonEvent.KEY_NAME, MEMBER_NATIVE_TEXT);
            emit(JsonEvent.START_OBJECT);
        }
    }

    private void closeResidue()
    {
        if (text == TEXT_RESIDUE)
        {
            emit(JsonEvent.END_OBJECT);
        }
    }
}
