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

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
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
    private static final String MEMBER_RESPONSE_FORMAT = "responseFormat";
    private static final String MEMBER_RESPONSE_FORMAT_SNAKE = "response_format";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_MIME_TYPE = "mimeType";
    private static final String MEMBER_MIME_TYPE_SNAKE = "mime_type";
    private static final String MEMBER_SCHEMA = "schema";
    private static final String MEMBER_SHARED_RESPONSE_FORMAT = "x:response_format";
    private static final String MEMBER_NATIVE_GENERATION_CONFIG = "gemini:generationConfig";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String MEMBER_NAME = "name";
    private static final String TYPE_JSON_SCHEMA = "json_schema";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String DEFAULT_NAME = "response";
    private static final String MIME_JSON = "application/json";
    private static final String MIME_JSON_ENUM = "APPLICATION_JSON";
    private static final String MEMBER_THINKING_CONFIG = "thinkingConfig";
    private static final String MEMBER_THINKING_CONFIG_SNAKE = "thinking_config";
    private static final String MEMBER_THINKING_LEVEL = "thinkingLevel";
    private static final String MEMBER_THINKING_LEVEL_SNAKE = "thinking_level";
    private static final String MEMBER_THINKING_BUDGET = "thinkingBudget";
    private static final String MEMBER_THINKING_BUDGET_SNAKE = "thinking_budget";
    private static final String MEMBER_SHARED_REASONING_EFFORT = "x:reasoning_effort";
    private static final String MEMBER_SHARED_THINKING_BUDGET = "x:thinking_budget_tokens";
    private static final String MEMBER_SERVICE_TIER = "serviceTier";
    private static final String MEMBER_SERVICE_TIER_SNAKE = "service_tier";
    private static final String MEMBER_SHARED_SERVICE_TIER = "x:service_tier";
    private static final String[] LEVELS = {"MINIMAL", "LOW", "MEDIUM", "HIGH"};
    private static final String[] EFFORTS = {"minimal", "low", "medium", "high"};

    // One dispatch runs one hook branch, so branches reuse bits by role: the wrapper bits also open the thinking and
    // tool config residue, captured bits serve every lifted or passed capture, object bits serve the format lift
    // and the schema lift, end bits close whichever object the branch opened.
    private static final int BIT_RESIDUE_KEY = 1 << 0;
    private static final int BIT_RESIDUE_START = 1 << 1;
    private static final int BIT_FORMAT_KEY = 1 << 2;
    private static final int BIT_FORMAT_START = 1 << 3;
    private static final int BIT_TEXT_KEY = 1 << 4;
    private static final int BIT_TEXT_START = 1 << 5;
    private static final int BIT_CAPTURED_KEY = 1 << 6;
    private static final int BIT_CAPTURED_VALUE = 1 << 7;
    private static final int BIT_CHOICE = 1 << 8;
    private static final int BIT_MEMBERS = 1 << 9;
    private static final int BIT_END = 1 << 10;
    private static final int BIT_END_OUTER = 1 << 11;
    private static final int BIT_TYPE_KEY = 1 << 12;
    private static final int BIT_TYPE_VALUE = 1 << 13;
    private static final int BIT_OBJECT_KEY = 1 << 14;
    private static final int BIT_OBJECT_START = 1 << 15;
    private static final int BIT_NAME_KEY = 1 << 16;
    private static final int BIT_NAME_VALUE = 1 << 17;

    private static final int FORMAT_OFF = 0;
    private static final int FORMAT_UNDECIDED = 1;
    private static final int FORMAT_RESIDUE = 2;
    private static final int FORMAT_TEXT = 3;
    private static final int FORMAT_LIFTED = 4;
    private static final int FORMAT_SCHEMA = 5;
    private static final int FORMAT_RESIDUE_TEXT = 6;
    private static final int FORMAT_LIFTED_CLOSED = 7;

    private static final int CONTAINER_NONE = 0;
    private static final int CONTAINER_FORMAT = 1;
    private static final int CONTAINER_THINKING = 2;

    private static final int THINKING_NONE = 0;
    private static final int THINKING_RESIDUE = 1;
    private static final int THINKING_LIFTED = 2;

    private final LlmToolChoice choice;

    private String residueKey;
    private String formatKey;
    private String thinkingKey;
    private String serviceTierKey;
    private int format;
    private int container;
    private int thinking;

    LlmGeminiControlToCanonical()
    {
        this.choice = new LlmToolChoice();
        this.residueKey = MEMBER_NATIVE_GENERATION_CONFIG;
        captureLimit(CAPTURE_MAX);
    }

    void residueKey(
        String key)
    {
        residueKey = key;
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
        format = FORMAT_OFF;
        formatKey = null;
        thinkingKey = null;
        serviceTierKey = null;
        container = CONTAINER_NONE;
        thinking = THINKING_NONE;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && (matches(key, MEMBER_TOOL_CONFIG) || matches(key, MEMBER_TOOL_CONFIG_SNAKE)))
        {
            action = capture(key);
        }
        else if (depth == 1 && (matches(key, MEMBER_SERVICE_TIER) || matches(key, MEMBER_SERVICE_TIER_SNAKE)))
        {
            prefix(DIALECT, key);
            serviceTierKey = target;
            action = capture(key);
        }
        else if (depth == 1 && (matches(key, MEMBER_RESPONSE_FORMAT) || matches(key, MEMBER_RESPONSE_FORMAT_SNAKE)))
        {
            container = CONTAINER_FORMAT;
            formatKey = name(key);
            target = formatKey;
            nest(residueKey);
            action = DESCEND_OR_PASS;
        }
        else if (depth == 1 && (matches(key, MEMBER_THINKING_CONFIG) || matches(key, MEMBER_THINKING_CONFIG_SNAKE)))
        {
            container = CONTAINER_THINKING;
            prefix(DIALECT, key);
            thinkingKey = target;
            action = DESCEND_OR_PASS;
        }
        else if (depth == 2)
        {
            action = container == CONTAINER_THINKING ? thinkingMember(key) : formatMember(key);
        }
        else if (depth == 3)
        {
            action = textMember(key);
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
        if (container == CONTAINER_THINKING)
        {
            thinking = THINKING_NONE;
        }
        else
        {
            format = depth == 2 ? FORMAT_UNDECIDED : FORMAT_TEXT;
        }

        return Status.ADVANCED;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        Status status = Status.ADVANCED;

        if (serviceTierKey != null)
        {
            onServiceTier(value);
        }
        else if (container == CONTAINER_THINKING)
        {
            onThinkingCaptured(value);
        }
        else if (matches(capturedMember(), MEMBER_MIME_TYPE) || matches(capturedMember(), MEMBER_MIME_TYPE_SNAKE))
        {
            onMimeType(value);
        }
        else
        {
            onToolConfig(value);
        }

        return status;
    }

    private void onServiceTier(
        LlmCapturedValue value)
    {
        final String tier = LlmServiceTiers.canonicalGemini(value.string(0));

        if (tier != null)
        {
            if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_SERVICE_TIER))
            {
                emit(BIT_CAPTURED_VALUE, JsonEvent.VALUE_STRING, tier);
            }
        }
        else
        {
            if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, serviceTierKey))
            {
                emitCaptured(BIT_CAPTURED_VALUE, value, 0);
            }
        }
    }

    private void onToolConfig(
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
            if (emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, residueKey))
            {
                emitCaptured(BIT_CAPTURED_VALUE, value, 0);
            }
        }
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (depth == 3)
        {
            onTextClose();
        }
        else if (depth == 2 && container == CONTAINER_THINKING)
        {
            onThinkingClose();
        }
        else if (depth == 2)
        {
            onFormatClose();
        }
        else
        {
            pass(BIT_END, JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }

    private int thinkingMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_THINKING_LEVEL) ||
            matches(key, MEMBER_THINKING_LEVEL_SNAKE) ||
            matches(key, MEMBER_THINKING_BUDGET) ||
            matches(key, MEMBER_THINKING_BUDGET_SNAKE))
        {
            action = capture(key);
        }
        else
        {
            target = name(key);
            action = PASS_VALUE;

            if (openThinkingResidue())
            {
                thinking = THINKING_RESIDUE;
            }
        }

        return action;
    }

    private void onThinkingCaptured(
        LlmCapturedValue value)
    {
        final String member = capturedMember();
        final boolean level = matches(member, MEMBER_THINKING_LEVEL) || matches(member, MEMBER_THINKING_LEVEL_SNAKE);
        final String effort = level ? effortOf(value.string(0)) : null;
        final int budget = !level && value.event(0) == JsonEvent.VALUE_NUMBER ? LlmReasoning.budgetOf(value.text(0)) : -1;

        if (effort != null)
        {
            liftThinking(MEMBER_SHARED_REASONING_EFFORT, JsonEvent.VALUE_STRING, effort);
        }
        else if (budget == 0)
        {
            liftThinking(MEMBER_SHARED_REASONING_EFFORT, JsonEvent.VALUE_STRING, LlmEffort.nameOf(LlmEffort.NONE));
        }
        else if (budget > 0)
        {
            liftThinking(MEMBER_SHARED_THINKING_BUDGET, JsonEvent.VALUE_NUMBER, value.text(0));
        }
        else if (openThinkingResidue() &&
            emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, member) &&
            emitCaptured(BIT_CAPTURED_VALUE, value, 0))
        {
            thinking = THINKING_RESIDUE;
        }
    }

    private void liftThinking(
        String key,
        JsonEvent event,
        String text)
    {
        if (closeThinkingResidue() &&
            emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, key) &&
            emit(BIT_CAPTURED_VALUE, event, text))
        {
            thinking = THINKING_LIFTED;
        }
    }

    private void onThinkingClose()
    {
        final boolean closed = thinking == THINKING_RESIDUE
            ? emit(BIT_END, JsonEvent.END_OBJECT)
            : thinking != THINKING_NONE ||
                emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, thinkingKey) &&
                emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT) &&
                emit(BIT_END, JsonEvent.END_OBJECT);

        if (closed)
        {
            thinking = THINKING_NONE;
        }
    }

    private boolean openThinkingResidue()
    {
        return thinking == THINKING_RESIDUE ||
            emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, thinkingKey) &&
            emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT);
    }

    private boolean closeThinkingResidue()
    {
        return thinking != THINKING_RESIDUE || emit(BIT_END, JsonEvent.END_OBJECT);
    }

    private static String effortOf(
        String level)
    {
        String effort = null;

        for (int index = 0; effort == null && level != null && index < LEVELS.length; index++)
        {
            effort = LEVELS[index].equalsIgnoreCase(level) ? EFFORTS[index] : null;
        }

        return effort;
    }

    private int formatMember(
        CharSequence key)
    {
        int action;

        if (format == FORMAT_UNDECIDED && matches(key, MEMBER_TEXT))
        {
            action = DESCEND_OR_REPLACE;
        }
        else if (format == FORMAT_UNDECIDED || format == FORMAT_RESIDUE)
        {
            target = name(key);
            action = PASS_VALUE;

            if (openWrapper(false))
            {
                format = FORMAT_RESIDUE;
            }
        }
        else
        {
            action = DROP;
        }

        return action;
    }

    private int textMember(
        CharSequence key)
    {
        int action = DROP;

        if (format == FORMAT_TEXT && (matches(key, MEMBER_MIME_TYPE) || matches(key, MEMBER_MIME_TYPE_SNAKE)))
        {
            action = capture(key);
        }
        else if (format == FORMAT_TEXT || format == FORMAT_RESIDUE_TEXT)
        {
            target = name(key);
            action = PASS_VALUE;

            if (openWrapper(true))
            {
                format = FORMAT_RESIDUE_TEXT;
            }
        }
        else if (format == FORMAT_LIFTED && matches(key, MEMBER_SCHEMA))
        {
            target = MEMBER_SCHEMA;
            action = PASS_VALUE;

            if (emit(BIT_TYPE_KEY, JsonEvent.KEY_NAME, MEMBER_TYPE) &&
                emit(BIT_TYPE_VALUE, JsonEvent.VALUE_STRING, TYPE_JSON_SCHEMA) &&
                emit(BIT_OBJECT_KEY, JsonEvent.KEY_NAME, MEMBER_JSON_SCHEMA) &&
                emit(BIT_OBJECT_START, JsonEvent.START_OBJECT) &&
                emit(BIT_NAME_KEY, JsonEvent.KEY_NAME, MEMBER_NAME) &&
                emit(BIT_NAME_VALUE, JsonEvent.VALUE_STRING, DEFAULT_NAME))
            {
                format = FORMAT_SCHEMA;
            }
        }

        return action;
    }

    private void onMimeType(
        LlmCapturedValue value)
    {
        final String mime = value.string(0);

        if (MIME_JSON.equalsIgnoreCase(mime) || MIME_JSON_ENUM.equals(mime))
        {
            if (emit(BIT_OBJECT_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_RESPONSE_FORMAT) &&
                emit(BIT_OBJECT_START, JsonEvent.START_OBJECT))
            {
                format = FORMAT_LIFTED;
            }
        }
        else if (openWrapper(true) &&
            emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, capturedMember()) &&
            emitCaptured(BIT_CAPTURED_VALUE, value, 0))
        {
            format = FORMAT_RESIDUE_TEXT;
        }
    }

    private void onTextClose()
    {
        switch (format)
        {
        case FORMAT_TEXT:
            if (openWrapper(true) && emit(BIT_END, JsonEvent.END_OBJECT))
            {
                format = FORMAT_RESIDUE;
            }
            break;
        case FORMAT_LIFTED:
            if (emit(BIT_TYPE_KEY, JsonEvent.KEY_NAME, MEMBER_TYPE) &&
                emit(BIT_TYPE_VALUE, JsonEvent.VALUE_STRING, TYPE_JSON_OBJECT) &&
                emit(BIT_END, JsonEvent.END_OBJECT))
            {
                format = FORMAT_LIFTED_CLOSED;
            }
            break;
        case FORMAT_SCHEMA:
            if (emit(BIT_END, JsonEvent.END_OBJECT) && emit(BIT_END_OUTER, JsonEvent.END_OBJECT))
            {
                format = FORMAT_LIFTED_CLOSED;
            }
            break;
        default:
            if (emit(BIT_END, JsonEvent.END_OBJECT))
            {
                format = FORMAT_RESIDUE;
            }
            break;
        }
    }

    private void onFormatClose()
    {
        final boolean closed = format != FORMAT_UNDECIDED && format != FORMAT_RESIDUE ||
            openWrapper(false) &&
            emit(BIT_END, JsonEvent.END_OBJECT) &&
            emit(BIT_END_OUTER, JsonEvent.END_OBJECT);

        if (closed)
        {
            format = FORMAT_OFF;
        }
    }

    private boolean openWrapper(
        boolean text)
    {
        final boolean wrapped = format != FORMAT_UNDECIDED && format != FORMAT_TEXT ||
            emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, residueKey) &&
            emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT) &&
            emit(BIT_FORMAT_KEY, JsonEvent.KEY_NAME, formatKey) &&
            emit(BIT_FORMAT_START, JsonEvent.START_OBJECT);

        return wrapped &&
            (!text || format == FORMAT_RESIDUE_TEXT ||
                emit(BIT_TEXT_KEY, JsonEvent.KEY_NAME, MEMBER_TEXT) &&
                emit(BIT_TEXT_START, JsonEvent.START_OBJECT));
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

        if (!lifted ||
            emit(BIT_CAPTURED_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_TOOL_CHOICE) &&
            choice.emitCanonical(this, BIT_CHOICE))
        {
            emitResidue(value, residueKey, lifted ? config : LlmCapturedValue.ABSENT);
        }
    }

    private void emitResidue(
        LlmCapturedValue value,
        String residueKey,
        int liftedAt)
    {
        boolean residue = false;
        int index = 1;

        while (value.event(index) == JsonEvent.KEY_NAME)
        {
            if (index + 1 != liftedAt)
            {
                if (!residue)
                {
                    residue = true;
                    emitLoop(BIT_MEMBERS, JsonEvent.KEY_NAME, residueKey);
                    emitLoop(BIT_MEMBERS, JsonEvent.START_OBJECT);
                }

                emitLoop(BIT_MEMBERS, JsonEvent.KEY_NAME, value.text(index));
                emitLoopCaptured(BIT_MEMBERS, value, index + 1);
            }

            index = value.end(index + 1);
        }

        if (endLoop(BIT_MEMBERS))
        {
            if (residue)
            {
                emit(BIT_END, JsonEvent.END_OBJECT);
            }
            else if (liftedAt == LlmCapturedValue.ABSENT)
            {
                emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, residueKey);
                emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT);
                emit(BIT_END, JsonEvent.END_OBJECT);
            }
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
