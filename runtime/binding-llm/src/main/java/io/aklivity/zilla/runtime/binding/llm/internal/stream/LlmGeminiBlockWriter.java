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

final class LlmGeminiBlockWriter
{
    static final int MORE = 0;
    static final int VALUE = 1;
    static final int REJECT = 2;

    static final int ACTION_DROP = 0;
    static final int ACTION_COPY = 1;
    static final int ACTION_FUNCTION = 2;

    private static final String PREFIX = LlmGeminiDialectFactorySpi.NAME + ":";
    private static final String MEMBER_FUNCTION_CALL = "functionCall";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_ID = "id";
    private static final String MEMBER_ARGS = "args";
    private static final String MEMBER_ARGUMENTS = "arguments";
    private static final int HEADROOM = 16;

    private final LlmOutputSink sink;
    private final JsonGeneratorEx generator;
    private final StringBuilder key = new StringBuilder();

    private MutableDirectBufferEx buffer;
    private int origin;
    private int depth;
    private boolean functioned;
    private boolean argumented;
    private boolean objectRequired;

    LlmGeminiBlockWriter(
        LlmOutputSink sink,
        JsonGeneratorEx generator)
    {
        this.sink = sink;
        this.generator = generator;
    }

    void begin(
        MutableDirectBufferEx buffer,
        int origin)
    {
        this.buffer = buffer;
        this.origin = origin;
        this.depth = 0;
        this.functioned = false;
        this.argumented = false;
        this.objectRequired = false;

        generator.reset();
        generator.wrap(buffer, origin, buffer.capacity());
        generator.writeStartObject();
    }

    int pending()
    {
        return generator.length();
    }

    boolean functioned()
    {
        return functioned;
    }

    boolean reserve(
        JsonEvent event,
        JsonParserEx parser)
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

    int rootAction(
        CharSequence name)
    {
        int action = ACTION_COPY;

        if (MEMBER_FUNCTION_CALL.contentEquals(name))
        {
            functioned = true;
            action = ACTION_FUNCTION;
        }
        else
        {
            writePrefixedKey(name);
        }

        return action;
    }

    int functionAction(
        CharSequence name)
    {
        int action = ACTION_DROP;

        if (MEMBER_ARGS.contentEquals(name))
        {
            generator.writeKey(MEMBER_ARGUMENTS);
            argumented = true;
            objectRequired = true;
            action = ACTION_COPY;
        }
        else if (!MEMBER_NAME.contentEquals(name) && !MEMBER_ID.contentEquals(name))
        {
            writePrefixedKey(name);
            action = ACTION_COPY;
        }

        return action;
    }

    void closeFunction()
    {
        if (!argumented)
        {
            generator.writeStartObject(MEMBER_ARGUMENTS).writeEnd();
            argumented = true;
        }
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
        int outcome = REJECT;

        if (!objectRequired || event == JsonEvent.START_OBJECT)
        {
            objectRequired = false;
            copy(event, parser);
            outcome = depth == 0 && !deferred(event, parser) ? VALUE : MORE;
        }

        return outcome;
    }

    void finish()
    {
        generator.writeEnd();
        flush(true);
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

    private void writePrefixedKey(
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
