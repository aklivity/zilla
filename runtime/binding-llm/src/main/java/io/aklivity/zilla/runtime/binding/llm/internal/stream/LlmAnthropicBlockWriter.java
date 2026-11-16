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

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

final class LlmAnthropicBlockWriter
{
    static final int MORE = 0;
    static final int VALUE = 1;
    static final int REJECT = 2;
    static final int SKIP = 3;

    static final int ACTION_DROP = 0;
    static final int ACTION_COPY = 1;
    static final int ACTION_STRING = 2;
    static final int ACTION_OBJECT = 3;
    static final int ACTION_OPTIONAL = 4;
    static final int ACTION_HOLD = 5;
    static final int ACTION_BREAKPOINT = 6;

    static final int KIND_TOOL_CALL = 0;
    static final int KIND_TOOL_DEFINITION = 1;

    private static final String PREFIX = LlmAnthropicDialectFactorySpi.NAME + ":";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_INPUT = "input";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final String MEMBER_DESCRIPTION = "description";
    private static final String MEMBER_INPUT_SCHEMA = "input_schema";
    private static final String MEMBER_PARAMETERS = "parameters";
    private static final String MEMBER_STRICT = "strict";
    private static final String MEMBER_FUNCTION = "function";
    private static final String TYPE_CUSTOM = "custom";
    private static final String TYPE_FUNCTION = "function";
    private static final int HEADROOM = 16;

    private final LlmOutputSink sink;
    private final JsonGeneratorEx generator;
    private final StringBuilder key = new StringBuilder();
    private final StringBuilder ttl = new StringBuilder();

    private MutableDirectBufferEx buffer;
    private int origin;
    private int kind;
    private int depth;
    private boolean argumented;
    private boolean named;
    private String heldType;
    private String pendingKey;
    private int breakpointDepth;
    private boolean breakpointKeyType;
    private boolean breakpointKeyTtl;
    private boolean breakpointEphemeral;

    LlmAnthropicBlockWriter(
        LlmOutputSink sink,
        JsonGeneratorEx generator)
    {
        this.sink = sink;
        this.generator = generator;
    }

    void resume(
        MutableDirectBufferEx buffer,
        int origin)
    {
        this.buffer = buffer;
        this.origin = origin;

        generator.wrap(buffer, origin, buffer.capacity());
    }

    void beginToolCall(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, KIND_TOOL_CALL);

        generator.writeStartObject();
    }

    void beginToolDefinition(
        MutableDirectBufferEx buffer,
        int origin)
    {
        begin(buffer, origin, KIND_TOOL_DEFINITION);

        generator.writeStartObject().write(MEMBER_TYPE, TYPE_FUNCTION).writeStartObject(MEMBER_FUNCTION);
    }

    int action(
        CharSequence name)
    {
        return kind == KIND_TOOL_CALL ? toolCallAction(name) : toolDefinitionAction(name);
    }

    boolean reserve(
        JsonEvent event)
    {
        int need = HEADROOM;

        if (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER)
        {
            need += PREFIX.length();
        }

        if (generator.remaining() < need)
        {
            flush(false);
        }

        return generator.remaining() >= need;
    }

    int pending()
    {
        return generator.length();
    }

    boolean finish()
    {
        boolean accepted = true;

        if (kind == KIND_TOOL_CALL)
        {
            if (!argumented)
            {
                generator.writeStartObject(MEMBER_ARGUMENTS).writeEnd();
            }
        }
        else
        {
            accepted = named && (heldType == null || TYPE_CUSTOM.equals(heldType));

            if (accepted)
            {
                generator.writeEnd();
            }
        }

        if (accepted)
        {
            generator.writeEnd();
            flush(true);
        }

        return accepted;
    }

    int drop(
        JsonEvent event,
        JsonParserEx parser)
    {
        depth += step(event);

        return depth == 0 && !deferred(event, parser) ? VALUE : MORE;
    }

    int copyValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        copy(event, parser);

        return depth == 0 && !deferred(event, parser) ? VALUE : MORE;
    }

    int stringValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        return event == JsonEvent.VALUE_STRING ? copyValue(event, parser) : REJECT;
    }

    int objectValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        return depth > 0 || event == JsonEvent.START_OBJECT ? copyValue(event, parser) : REJECT;
    }

    int optionalValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome;

        if (pendingKey == null)
        {
            outcome = copyValue(event, parser);
        }
        else if (event == JsonEvent.VALUE_STRING)
        {
            generator.writeKey(pendingKey);
            pendingKey = null;
            outcome = copyValue(event, parser);
        }
        else
        {
            pendingKey = null;
            outcome = drop(event, parser);
            outcome = outcome == MORE ? SKIP : outcome;
        }

        return outcome;
    }

    int holdValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        int outcome = REJECT;

        if (event == JsonEvent.VALUE_STRING && !parser.deferredBytes())
        {
            heldType = parser.getStringView().toString();
            outcome = VALUE;
        }

        return outcome;
    }

    int breakpointValue(
        JsonEvent event,
        JsonParserEx parser)
    {
        switch (event)
        {
        case START_OBJECT, START_ARRAY:
            breakpointDepth++;
            break;
        case END_OBJECT, END_ARRAY:
            breakpointDepth--;
            break;
        case KEY_NAME:
            if (breakpointDepth == 1)
            {
                final CharSequence name = parser.getStringView();

                breakpointKeyType = LlmCacheBreakpoint.NATIVE_TYPE.contentEquals(name);
                breakpointKeyTtl = LlmCacheBreakpoint.TTL.contentEquals(name);
            }
            break;
        case VALUE_STRING:
            if (!parser.deferredBytes() && breakpointDepth == 1 && breakpointKeyType)
            {
                breakpointEphemeral = LlmCacheBreakpoint.NATIVE_EPHEMERAL.contentEquals(parser.getStringView());
            }
            else if (!parser.deferredBytes() && breakpointDepth == 1 && breakpointKeyTtl)
            {
                ttl.append(parser.getStringView());
            }
            break;
        default:
            break;
        }

        int outcome = MORE;

        if (breakpointDepth == 0)
        {
            if (breakpointEphemeral)
            {
                generator.writeKey(LlmCacheBreakpoint.MEMBER).writeStartObject()
                    .write(LlmCacheBreakpoint.MODE, LlmCacheBreakpoint.MODE_EXPLICIT);

                if (ttl.length() > 0)
                {
                    generator.write(LlmCacheBreakpoint.TTL, ttl.toString());
                }

                generator.writeEnd();
            }

            outcome = VALUE;
        }

        return outcome;
    }

    void flush(
        boolean last)
    {
        final int length = generator.length();

        if (length > 0 || last)
        {
            sink.put(buffer, origin, length, last);
            generator.wrap(buffer, origin, buffer.capacity());
        }
    }

    private void begin(
        MutableDirectBufferEx buffer,
        int origin,
        int kind)
    {
        this.buffer = buffer;
        this.origin = origin;
        this.kind = kind;
        this.depth = 0;
        this.argumented = false;
        this.named = false;
        this.heldType = null;
        this.pendingKey = null;

        generator.reset();
        generator.wrap(buffer, origin, buffer.capacity());
    }

    private int toolCallAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_INPUT.contentEquals(name))
        {
            generator.writeKey(MEMBER_ARGUMENTS);
            argumented = true;
            action = ACTION_OBJECT;
        }
        else if (LlmCacheBreakpoint.NATIVE_MEMBER.contentEquals(name))
        {
            action = beginBreakpoint();
        }
        else if (!MEMBER_TYPE.contentEquals(name) && !MEMBER_ID.contentEquals(name) && !MEMBER_NAME.contentEquals(name))
        {
            writePrefixed(name);
            action = ACTION_COPY;
        }

        return action;
    }

    private int toolDefinitionAction(
        CharSequence name)
    {
        int action;

        if (MEMBER_NAME.contentEquals(name))
        {
            generator.writeKey(MEMBER_NAME);
            named = true;
            action = ACTION_STRING;
        }
        else if (MEMBER_DESCRIPTION.contentEquals(name))
        {
            pendingKey = MEMBER_DESCRIPTION;
            action = ACTION_OPTIONAL;
        }
        else if (MEMBER_INPUT_SCHEMA.contentEquals(name))
        {
            generator.writeKey(MEMBER_PARAMETERS);
            action = ACTION_OBJECT;
        }
        else if (MEMBER_TYPE.contentEquals(name))
        {
            action = ACTION_HOLD;
        }
        else if (MEMBER_STRICT.contentEquals(name))
        {
            generator.writeKey(MEMBER_STRICT);
            action = ACTION_COPY;
        }
        else if (LlmCacheBreakpoint.NATIVE_MEMBER.contentEquals(name))
        {
            action = beginBreakpoint();
        }
        else
        {
            writePrefixed(name);
            action = ACTION_COPY;
        }

        return action;
    }

    private int beginBreakpoint()
    {
        breakpointDepth = 0;
        breakpointKeyType = false;
        breakpointKeyTtl = false;
        breakpointEphemeral = false;
        ttl.setLength(0);

        return ACTION_BREAKPOINT;
    }

    private void writePrefixed(
        CharSequence name)
    {
        key.setLength(0);
        key.append(PREFIX).append(name);
        generator.writeKey(key);
    }

    private void copy(
        JsonEvent event,
        JsonParserEx parser)
    {
        switch (event)
        {
        case START_OBJECT:
            generator.writeStartObject();
            depth++;
            break;
        case START_ARRAY:
            generator.writeStartArray();
            depth++;
            break;
        case END_OBJECT, END_ARRAY:
            generator.writeEnd();
            depth--;
            break;
        case VALUE_TRUE:
            generator.write(true);
            break;
        case VALUE_FALSE:
            generator.write(false);
            break;
        case VALUE_NULL:
            generator.writeNull();
            break;
        default:
            copyText(event, parser.getStringView(), parser.deferredBytes());
            break;
        }
    }

    private void copyText(
        JsonEvent event,
        JsonStringView chars,
        boolean deferred)
    {
        final Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
        final int length = chars.length();

        int taken = 0;

        do
        {
            final CharSequence rest = taken == 0 ? chars : chars.subSequence(taken, length);
            final int before = generator.consumed();

            if (event == JsonEvent.KEY_NAME)
            {
                generator.writeKey(rest, completion);
            }
            else if (event == JsonEvent.VALUE_NUMBER)
            {
                generator.writeNumber(rest, completion);
            }
            else
            {
                generator.write(rest, completion);
            }

            final int advanced = generator.consumed() - before;

            taken += advanced;

            if (taken < length)
            {
                if (advanced == 0 && generator.length() == 0)
                {
                    throw new JsonException("insufficient room to write the next character");
                }

                flush(false);
            }
        }
        while (taken < length);
    }

    private static int step(
        JsonEvent event)
    {
        return switch (event)
        {
        case START_OBJECT, START_ARRAY -> 1;
        case END_OBJECT, END_ARRAY -> -1;
        default -> 0;
        };
    }

    private static boolean deferred(
        JsonEvent event,
        JsonParserEx parser)
    {
        return event == JsonEvent.VALUE_STRING && parser.deferredBytes();
    }
}
