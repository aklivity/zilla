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

import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.binding.llm.dialect.HttpRequestBody;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * A {@link HttpRequestBody} view over a fully-buffered JSON object, read via a one-shot,
 * complete-buffer parse (see {@link JsonEx#createParser()}) rather than the chunked windowed API.
 */
final class LlmJsonRequestBody implements HttpRequestBody
{
    private final JsonParserEx parser;
    private final DirectBufferEx buffer;
    private final int offset;
    private final int length;

    LlmJsonRequestBody(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        this.parser = JsonEx.createParser();
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    @Override
    public String value(
        String name)
    {
        String value = null;

        try
        {
            parser.reset();
            parser.wrap(buffer, offset, offset + length);

            int depth = 0;
            boolean matched = false;
            while (value == null && parser.hasNext())
            {
                final Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    matched = false;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    matched = depth == 1 && name.equals(parser.getString());
                    break;
                case VALUE_STRING:
                case VALUE_NUMBER:
                    if (matched)
                    {
                        value = parser.getString();
                    }
                    matched = false;
                    break;
                case VALUE_TRUE:
                    if (matched)
                    {
                        value = Boolean.TRUE.toString();
                    }
                    matched = false;
                    break;
                case VALUE_FALSE:
                    if (matched)
                    {
                        value = Boolean.FALSE.toString();
                    }
                    matched = false;
                    break;
                case VALUE_NULL:
                    matched = false;
                    break;
                default:
                    break;
                }
            }
        }
        catch (JsonParsingException ex)
        {
            value = null;
        }

        return value;
    }
}
