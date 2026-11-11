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

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

final class LlmToolChoice
{
    static final int UNRECOGNIZED = 0;
    static final int MODE = 1;
    static final int FUNCTION = 2;
    static final int ALLOWED = 3;

    static final String MODE_AUTO = "auto";
    static final String MODE_REQUIRED = "required";
    static final String MODE_NONE = "none";

    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String MODE_MEMBER = "mode";
    private static final String TOOLS = "tools";
    private static final String FUNCTION_MEMBER = "function";
    private static final String ALLOWED_TOOLS = "allowed_tools";
    private static final String[] FUNCTION_MEMBERS = {TYPE, FUNCTION_MEMBER};
    private static final String[] FLAT_FUNCTION_MEMBERS = {TYPE, NAME};
    private static final String[] NAME_MEMBERS = {NAME};
    private static final String[] ALLOWED_MEMBERS = {TYPE, ALLOWED_TOOLS};
    private static final String[] ALLOWED_BODY_MEMBERS = {MODE_MEMBER, TOOLS};
    private static final String[] FLAT_ALLOWED_MEMBERS = {TYPE, MODE_MEMBER, TOOLS};

    private final List<String> names;

    private int kind;
    private String mode;
    private String name;

    LlmToolChoice()
    {
        this.names = new ArrayList<>();
    }

    void clear()
    {
        kind = UNRECOGNIZED;
        mode = null;
        name = null;
        names.clear();
    }

    int kind()
    {
        return kind;
    }

    String mode()
    {
        return mode;
    }

    String name()
    {
        return name;
    }

    List<String> names()
    {
        return names;
    }

    void mode(
        String value)
    {
        kind = MODE;
        mode = value;
    }

    void function(
        String value)
    {
        kind = FUNCTION;
        name = value;
    }

    void allowed(
        String value)
    {
        kind = ALLOWED;
        mode = value;
    }

    boolean parseCanonical(
        LlmCapturedValue value)
    {
        clear();

        if (value.event(0) == JsonEvent.VALUE_STRING)
        {
            parseMode(value.string(0));
        }
        else if (value.isObject(0))
        {
            final String type = value.string(value.member(0, TYPE));

            if ("function".equals(type) && value.onlyMembers(0, FUNCTION_MEMBERS))
            {
                parseFunction(value, value.member(0, FUNCTION_MEMBER));
            }
            else if ("allowed_tools".equals(type) && value.onlyMembers(0, ALLOWED_MEMBERS))
            {
                parseAllowed(value, value.member(0, ALLOWED_TOOLS), false);
            }
        }

        return kind != UNRECOGNIZED;
    }

    boolean parseResponses(
        LlmCapturedValue value)
    {
        clear();

        if (value.event(0) == JsonEvent.VALUE_STRING)
        {
            parseMode(value.string(0));
        }
        else if (value.isObject(0))
        {
            final String type = value.string(value.member(0, TYPE));

            if ("function".equals(type) && value.onlyMembers(0, FLAT_FUNCTION_MEMBERS))
            {
                final String declared = value.string(value.member(0, NAME));

                if (declared != null)
                {
                    function(declared);
                }
            }
            else if ("allowed_tools".equals(type) && value.onlyMembers(0, FLAT_ALLOWED_MEMBERS))
            {
                parseAllowedBody(value, 0, true);
            }
        }

        return kind != UNRECOGNIZED;
    }

    boolean emitCanonical(
        LlmToolTransform transform,
        int loopBit)
    {
        switch (kind)
        {
        case MODE:
            emit(transform, loopBit, JsonEvent.VALUE_STRING, mode);
            break;
        case FUNCTION:
            emitFunction(transform, loopBit, name, false);
            break;
        case ALLOWED:
            emit(transform, loopBit, JsonEvent.START_OBJECT);
            emit(transform, loopBit, JsonEvent.KEY_NAME, TYPE);
            emit(transform, loopBit, JsonEvent.VALUE_STRING, ALLOWED_TOOLS);
            emit(transform, loopBit, JsonEvent.KEY_NAME, ALLOWED_TOOLS);
            emitAllowedBody(transform, loopBit, false);
            emit(transform, loopBit, JsonEvent.END_OBJECT);
            break;
        default:
            break;
        }

        return transform.endLoop(loopBit);
    }

    boolean emitResponses(
        LlmToolTransform transform,
        int loopBit)
    {
        switch (kind)
        {
        case MODE:
            emit(transform, loopBit, JsonEvent.VALUE_STRING, mode);
            break;
        case FUNCTION:
            emitFunction(transform, loopBit, name, true);
            break;
        case ALLOWED:
            emit(transform, loopBit, JsonEvent.START_OBJECT);
            emit(transform, loopBit, JsonEvent.KEY_NAME, TYPE);
            emit(transform, loopBit, JsonEvent.VALUE_STRING, ALLOWED_TOOLS);
            emitAllowedMembers(transform, loopBit, true);
            emit(transform, loopBit, JsonEvent.END_OBJECT);
            break;
        default:
            break;
        }

        return transform.endLoop(loopBit);
    }

    private void parseMode(
        String value)
    {
        if (MODE_AUTO.equals(value) || MODE_REQUIRED.equals(value) || MODE_NONE.equals(value))
        {
            mode(value);
        }
    }

    private void parseFunction(
        LlmCapturedValue value,
        int function)
    {
        if (function != LlmCapturedValue.ABSENT && value.onlyMembers(function, NAME_MEMBERS))
        {
            final String declared = value.string(value.member(function, NAME));

            if (declared != null)
            {
                function(declared);
            }
        }
    }

    private void parseAllowed(
        LlmCapturedValue value,
        int body,
        boolean flat)
    {
        if (body != LlmCapturedValue.ABSENT && value.onlyMembers(body, ALLOWED_BODY_MEMBERS))
        {
            parseAllowedBody(value, body, flat);
        }
    }

    private void parseAllowedBody(
        LlmCapturedValue value,
        int body,
        boolean flat)
    {
        final String selected = value.string(value.member(body, MODE_MEMBER));
        final int tools = value.member(body, TOOLS);

        if ((MODE_AUTO.equals(selected) || MODE_REQUIRED.equals(selected)) && value.isArray(tools))
        {
            boolean valid = true;
            int index = tools + 1;

            while (valid && value.event(index) != JsonEvent.END_ARRAY)
            {
                final String entry = toolName(value, index, flat);

                valid = entry != null;

                if (valid)
                {
                    names.add(entry);
                }

                index = value.end(index);
            }

            if (valid)
            {
                allowed(selected);
            }
            else
            {
                names.clear();
            }
        }
    }

    private static String toolName(
        LlmCapturedValue value,
        int entry,
        boolean flat)
    {
        String found = null;

        if (value.isObject(entry) && value.isString(value.member(entry, TYPE), "function"))
        {
            if (flat)
            {
                found = value.onlyMembers(entry, FLAT_FUNCTION_MEMBERS) ? value.string(value.member(entry, NAME)) : null;
            }
            else
            {
                final int function = value.member(entry, FUNCTION_MEMBER);

                found = value.onlyMembers(entry, FUNCTION_MEMBERS) && function != LlmCapturedValue.ABSENT &&
                    value.onlyMembers(function, NAME_MEMBERS) ? value.string(value.member(function, NAME)) : null;
            }
        }

        return found;
    }

    private static void emitFunction(
        LlmToolTransform transform,
        int loopBit,
        String function,
        boolean flat)
    {
        emit(transform, loopBit, JsonEvent.START_OBJECT);
        emit(transform, loopBit, JsonEvent.KEY_NAME, TYPE);
        emit(transform, loopBit, JsonEvent.VALUE_STRING, FUNCTION_MEMBER);

        if (flat)
        {
            emit(transform, loopBit, JsonEvent.KEY_NAME, NAME);
            emit(transform, loopBit, JsonEvent.VALUE_STRING, function);
        }
        else
        {
            emit(transform, loopBit, JsonEvent.KEY_NAME, FUNCTION_MEMBER);
            emit(transform, loopBit, JsonEvent.START_OBJECT);
            emit(transform, loopBit, JsonEvent.KEY_NAME, NAME);
            emit(transform, loopBit, JsonEvent.VALUE_STRING, function);
            emit(transform, loopBit, JsonEvent.END_OBJECT);
        }

        emit(transform, loopBit, JsonEvent.END_OBJECT);
    }

    private void emitAllowedBody(
        LlmToolTransform transform,
        int loopBit,
        boolean flat)
    {
        emit(transform, loopBit, JsonEvent.START_OBJECT);
        emitAllowedMembers(transform, loopBit, flat);
        emit(transform, loopBit, JsonEvent.END_OBJECT);
    }

    private void emitAllowedMembers(
        LlmToolTransform transform,
        int loopBit,
        boolean flat)
    {
        emit(transform, loopBit, JsonEvent.KEY_NAME, MODE_MEMBER);
        emit(transform, loopBit, JsonEvent.VALUE_STRING, mode);
        emit(transform, loopBit, JsonEvent.KEY_NAME, TOOLS);
        emit(transform, loopBit, JsonEvent.START_ARRAY);

        for (int index = 0; index < names.size(); index++)
        {
            emitFunction(transform, loopBit, names.get(index), flat);
        }

        emit(transform, loopBit, JsonEvent.END_ARRAY);
    }

    private static void emit(
        LlmToolTransform transform,
        int loopBit,
        JsonEvent event)
    {
        transform.emitLoop(loopBit, event);
    }

    private static void emit(
        LlmToolTransform transform,
        int loopBit,
        JsonEvent event,
        String text)
    {
        transform.emitLoop(loopBit, event, text);
    }
}
