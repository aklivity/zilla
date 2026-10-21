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

final class LlmGeminiControlToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";
    private static final String MEMBER_TOOL_CONFIG = "toolConfig";
    private static final String MEMBER_TOOL_CONFIG_SNAKE = "tool_config";
    private static final String MEMBER_NATIVE_TOOL_CONFIG = "gemini:toolConfig";
    private static final String MEMBER_NATIVE_TOOL_CONFIG_SNAKE = "gemini:tool_config";
    private static final String MEMBER_SHARED_TOOL_CHOICE = "x:tool_choice";
    private static final String MEMBER_FUNCTION_CALLING_CONFIG = "functionCallingConfig";
    private static final String MEMBER_FUNCTION_CALLING_CONFIG_SNAKE = "function_calling_config";
    private static final String MEMBER_MODE = "mode";
    private static final String MEMBER_ALLOWED_FUNCTION_NAMES = "allowedFunctionNames";
    private static final String MEMBER_ALLOWED_FUNCTION_NAMES_SNAKE = "allowed_function_names";
    private static final String[] CONFIG_MEMBERS =
    {
        MEMBER_MODE, MEMBER_ALLOWED_FUNCTION_NAMES, MEMBER_ALLOWED_FUNCTION_NAMES_SNAKE
    };
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_ANY = "ANY";
    private static final String MODE_NONE = "NONE";
    private static final int CAPTURE_MAX = 768;

    private final LlmToolChoice choice;

    LlmGeminiControlToCanonical()
    {
        this.choice = new LlmToolChoice();
        captureLimit(CAPTURE_MAX);
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        return depth == 1 && (matches(key, MEMBER_TOOL_CONFIG) || matches(key, MEMBER_TOOL_CONFIG_SNAKE))
            ? capture(key)
            : prefix(DIALECT, key);
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        final String residueKey = matches(capturedMember(), MEMBER_TOOL_CONFIG_SNAKE)
            ? MEMBER_NATIVE_TOOL_CONFIG_SNAKE
            : MEMBER_NATIVE_TOOL_CONFIG;

        if (value.isObject(0))
        {
            splitToolConfig(value, residueKey);
        }
        else
        {
            emit(JsonEvent.KEY_NAME, residueKey);
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

    private void splitToolConfig(
        LlmCapturedValue value,
        String residueKey)
    {
        int config = value.member(0, MEMBER_FUNCTION_CALLING_CONFIG);

        if (config == LlmCapturedValue.ABSENT)
        {
            config = value.member(0, MEMBER_FUNCTION_CALLING_CONFIG_SNAKE);
        }

        final boolean lifted = config != LlmCapturedValue.ABSENT && parseCallingConfig(value, config);

        if (lifted)
        {
            emit(JsonEvent.KEY_NAME, MEMBER_SHARED_TOOL_CHOICE);
            choice.emitCanonical(this);
        }

        boolean residue = false;
        int index = 1;

        while (value.event(index) == JsonEvent.KEY_NAME)
        {
            if (!lifted || index + 1 != config)
            {
                if (!residue)
                {
                    residue = true;
                    emit(JsonEvent.KEY_NAME, residueKey);
                    emit(JsonEvent.START_OBJECT);
                }

                emit(JsonEvent.KEY_NAME, value.text(index));
                emitCaptured(value, index + 1);
            }

            index = value.end(index + 1);
        }

        if (residue)
        {
            emit(JsonEvent.END_OBJECT);
        }
        else if (!lifted)
        {
            emit(JsonEvent.KEY_NAME, residueKey);
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }
    }

    private boolean parseCallingConfig(
        LlmCapturedValue value,
        int config)
    {
        choice.clear();

        if (value.onlyMembers(config, CONFIG_MEMBERS))
        {
            int names = value.member(config, MEMBER_ALLOWED_FUNCTION_NAMES);

            if (names == LlmCapturedValue.ABSENT)
            {
                names = value.member(config, MEMBER_ALLOWED_FUNCTION_NAMES_SNAKE);
            }

            if (names == LlmCapturedValue.ABSENT || readNames(value, names))
            {
                chooseMode(value.string(value.member(config, MEMBER_MODE)));
            }
        }

        return choice.kind() != LlmToolChoice.UNRECOGNIZED;
    }

    private boolean readNames(
        LlmCapturedValue value,
        int names)
    {
        boolean valid = value.isArray(names);
        int index = names + 1;

        while (valid && value.event(index) != JsonEvent.END_ARRAY)
        {
            final String entry = value.string(index);

            valid = entry != null;

            if (valid)
            {
                choice.names().add(entry);
            }

            index++;
        }

        return valid;
    }

    private void chooseMode(
        String mode)
    {
        final int count = choice.names().size();

        if (MODE_AUTO.equals(mode))
        {
            if (count == 0)
            {
                choice.mode(LlmToolChoice.MODE_AUTO);
            }
            else
            {
                choice.allowed(LlmToolChoice.MODE_AUTO);
            }
        }
        else if (MODE_ANY.equals(mode))
        {
            if (count == 0)
            {
                choice.mode(LlmToolChoice.MODE_REQUIRED);
            }
            else if (count == 1)
            {
                choice.function(choice.names().get(0));
                choice.names().clear();
            }
            else
            {
                choice.allowed(LlmToolChoice.MODE_REQUIRED);
            }
        }
        else if (MODE_NONE.equals(mode) && count == 0)
        {
            choice.mode(LlmToolChoice.MODE_NONE);
        }
    }
}
