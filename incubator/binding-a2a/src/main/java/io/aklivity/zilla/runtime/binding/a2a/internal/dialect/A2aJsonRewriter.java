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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import java.util.Map;

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

abstract class A2aJsonRewriter
{
    protected final JsonParserEx parser = JsonEx.createParser(Map.of());
    protected final JsonGeneratorEx generator = JsonEx.createGenerator();

    private DirectBufferEx input;
    private int inputOffset;
    private int inputLimit;
    private int outputOffset;

    protected final void begin(
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        this.input = input;
        this.inputOffset = inputOffset;
        this.inputLimit = inputLimit;
        this.outputOffset = outputOffset;

        parser.wrap(input, inputOffset, inputLimit);
        generator.wrap(output, outputOffset, output.capacity());
    }

    protected final void rewind()
    {
        parser.reset();
        parser.wrap(input, inputOffset, inputLimit);
    }

    protected final int end()
    {
        generator.flush();
        final int limit = outputOffset + generator.length();
        parser.reset();
        generator.reset();
        return limit;
    }

    protected final String stringOfTopLevel(
        String name)
    {
        String value = null;
        int depth = 0;
        String key = null;

        while (value == null && parser.hasNext())
        {
            final Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                break;
            case KEY_NAME:
                key = depth == 1 ? parser.getString() : null;
                break;
            case VALUE_STRING:
                if (depth == 1 && name.equals(key))
                {
                    value = parser.getString();
                }
                break;
            default:
                break;
            }
        }

        return value;
    }

    protected final void copyValue(
        Event event)
    {
        switch (event)
        {
        case START_OBJECT:
            generator.writeStartObject();
            for (Event e = parser.next(); e != Event.END_OBJECT; e = parser.next())
            {
                generator.writeKey(parser.getString());
                copyValue(parser.next());
            }
            generator.writeEnd();
            break;
        case START_ARRAY:
            generator.writeStartArray();
            for (Event e = parser.next(); e != Event.END_ARRAY; e = parser.next())
            {
                copyValue(e);
            }
            generator.writeEnd();
            break;
        case VALUE_STRING:
            generator.write(parser.getString());
            break;
        case VALUE_NUMBER:
            generator.writeNumber(parser.getString());
            break;
        case VALUE_TRUE:
            generator.write(true);
            break;
        case VALUE_FALSE:
            generator.write(false);
            break;
        default:
            generator.writeNull();
            break;
        }
    }

    protected final void skipValue(
        Event event)
    {
        int depth = event == Event.START_OBJECT || event == Event.START_ARRAY ? 1 : 0;
        while (depth > 0)
        {
            final Event e = parser.next();
            if (e == Event.START_OBJECT || e == Event.START_ARRAY)
            {
                depth++;
            }
            else if (e == Event.END_OBJECT || e == Event.END_ARRAY)
            {
                depth--;
            }
        }
    }
}
