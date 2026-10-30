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

package io.aklivity.zilla.runtime.binding.a2a.internal.push;

import java.util.Map;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public final class A2aPushTaskScanner
{
    public enum Result
    {
        FOUND,
        INCOMPLETE,
        MISSING
    }

    private static final String TASK_ID_NAME = "taskId";
    private static final String ID_NAME = "id";
    private static final String TASK_NAME = "task";

    private final JsonParserEx parser = JsonEx.createParser(Map.of());

    private String taskId;

    public String taskId()
    {
        return taskId;
    }

    public Result scan(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        Result result = Result.INCOMPLETE;
        taskId = null;

        try
        {
            parser.reset();
            parser.wrap(buffer, offset, limit, last);

            int depth = 0;
            String member = null;

            scan:
            while (result == Result.INCOMPLETE && parser.hasNext())
            {
                switch (parser.next())
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
                    final String name = parser.getString();
                    if (depth == 1)
                    {
                        member = name;
                    }

                    if (TASK_ID_NAME.equals(name) && depth <= 2 ||
                        ID_NAME.equals(name) && (depth == 1 || depth == 2 && TASK_NAME.equals(member)))
                    {
                        if (!parser.hasNext())
                        {
                            break scan;
                        }

                        if (parser.next() == JsonParser.Event.VALUE_STRING)
                        {
                            if (parser.deferredBytes())
                            {
                                break scan;
                            }

                            taskId = parser.getString();
                            result = Result.FOUND;
                        }
                    }
                    break;
                default:
                    break;
                }
            }

            if (result == Result.INCOMPLETE && last)
            {
                result = Result.MISSING;
            }
        }
        catch (JsonParsingException ex)
        {
            result = Result.MISSING;
        }

        return result;
    }
}
