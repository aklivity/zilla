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

    void emitCanonical(
        LlmToolTransform transform)
    {
        switch (kind)
        {
        case MODE:
            transform.emit(JsonEvent.VALUE_STRING, mode);
            break;
        case FUNCTION:
            emitFunction(transform, name, false);
            break;
        case ALLOWED:
            transform.emit(JsonEvent.START_OBJECT);
            transform.emit(JsonEvent.KEY_NAME, TYPE);
            transform.emit(JsonEvent.VALUE_STRING, ALLOWED_TOOLS);
            transform.emit(JsonEvent.KEY_NAME, ALLOWED_TOOLS);
            emitAllowedBody(transform, false);
            transform.emit(JsonEvent.END_OBJECT);
            break;
        default:
            break;
        }
    }

    void emitResponses(
        LlmToolTransform transform)
    {
        switch (kind)
        {
        case MODE:
            transform.emit(JsonEvent.VALUE_STRING, mode);
            break;
        case FUNCTION:
            emitFunction(transform, name, true);
            break;
        case ALLOWED:
            transform.emit(JsonEvent.START_OBJECT);
            transform.emit(JsonEvent.KEY_NAME, TYPE);
            transform.emit(JsonEvent.VALUE_STRING, ALLOWED_TOOLS);
            emitAllowedMembers(transform, true);
            transform.emit(JsonEvent.END_OBJECT);
            break;
        default:
            break;
        }
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
        String function,
        boolean flat)
    {
        transform.emit(JsonEvent.START_OBJECT);
        transform.emit(JsonEvent.KEY_NAME, TYPE);
        transform.emit(JsonEvent.VALUE_STRING, FUNCTION_MEMBER);

        if (flat)
        {
            transform.emit(JsonEvent.KEY_NAME, NAME);
            transform.emit(JsonEvent.VALUE_STRING, function);
        }
        else
        {
            transform.emit(JsonEvent.KEY_NAME, FUNCTION_MEMBER);
            transform.emit(JsonEvent.START_OBJECT);
            transform.emit(JsonEvent.KEY_NAME, NAME);
            transform.emit(JsonEvent.VALUE_STRING, function);
            transform.emit(JsonEvent.END_OBJECT);
        }

        transform.emit(JsonEvent.END_OBJECT);
    }

    private void emitAllowedBody(
        LlmToolTransform transform,
        boolean flat)
    {
        transform.emit(JsonEvent.START_OBJECT);
        emitAllowedMembers(transform, flat);
        transform.emit(JsonEvent.END_OBJECT);
    }

    private void emitAllowedMembers(
        LlmToolTransform transform,
        boolean flat)
    {
        transform.emit(JsonEvent.KEY_NAME, MODE_MEMBER);
        transform.emit(JsonEvent.VALUE_STRING, mode);
        transform.emit(JsonEvent.KEY_NAME, TOOLS);
        transform.emit(JsonEvent.START_ARRAY);

        for (int index = 0; index < names.size(); index++)
        {
            emitFunction(transform, names.get(index), flat);
        }

        transform.emit(JsonEvent.END_ARRAY);
    }
}
