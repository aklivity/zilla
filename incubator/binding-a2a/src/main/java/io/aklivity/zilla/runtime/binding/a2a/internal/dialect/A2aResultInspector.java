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

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;

import java.util.Map;

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.binding.a2a.internal.types.A2aFlag;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public final class A2aResultInspector
{
    public static final int NO_KIND = -1;

    private static final String STATE_PREFIX = "TASK_STATE_";

    private final JsonParserEx parser = JsonEx.createParser(Map.of());

    public int kind;
    public String taskId;
    public String contextId;
    public String state;
    public String artifactId;
    public String messageId;
    public A2aFlag append;
    public A2aFlag lastChunk;

    public boolean inspect(
        int operation,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        kind = NO_KIND;
        taskId = null;
        contextId = null;
        state = null;
        artifactId = null;
        messageId = null;
        append = A2aFlag.UNSPECIFIED;
        lastChunk = A2aFlag.UNSPECIFIED;

        final boolean wrapped = operation == KIND_MESSAGE_SEND ||
            operation == KIND_MESSAGE_STREAM ||
            operation == KIND_TASKS_SUBSCRIBE;
        final boolean direct = operation == KIND_TASKS_GET || operation == KIND_TASKS_CANCEL;

        if (wrapped || direct)
        {
            if (direct)
            {
                kind = A2aDataExFW.KIND_TASK;
            }

            parser.wrap(buffer, offset, limit);

            final int base = wrapped ? 1 : 0;
            int depth = 0;
            String inner = null;
            String member = null;
            String leaf = null;

            while (parser.hasNext())
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
                    final String key = parser.getString();
                    if (depth == 1 && wrapped)
                    {
                        kind = switch (key)
                        {
                        case "message" -> A2aDataExFW.KIND_MESSAGE;
                        case "task" -> A2aDataExFW.KIND_TASK;
                        case "statusUpdate" -> A2aDataExFW.KIND_STATUS_UPDATE;
                        case "artifactUpdate" -> A2aDataExFW.KIND_ARTIFACT_UPDATE;
                        default -> NO_KIND;
                        };
                    }
                    else if (depth == 1 + base)
                    {
                        inner = key;
                    }
                    else if (depth == 2 + base)
                    {
                        member = key;
                    }
                    else if (depth == 3 + base)
                    {
                        leaf = key;
                    }
                    break;
                case VALUE_TRUE:
                case VALUE_FALSE:
                    if (depth == 1 + base && kind == A2aDataExFW.KIND_ARTIFACT_UPDATE)
                    {
                        final A2aFlag flag = event == Event.VALUE_TRUE ? A2aFlag.TRUE : A2aFlag.FALSE;
                        switch (inner)
                        {
                        case "append":
                            append = flag;
                            break;
                        case "lastChunk":
                            lastChunk = flag;
                            break;
                        default:
                            break;
                        }
                    }
                    break;
                case VALUE_STRING:
                    if (depth == 1 + base)
                    {
                        final String value = parser.getString();
                        switch (inner)
                        {
                        case "id":
                            taskId = kind != A2aDataExFW.KIND_MESSAGE ? value : taskId;
                            break;
                        case "taskId":
                            taskId = value;
                            break;
                        case "contextId":
                            contextId = value;
                            break;
                        case "messageId":
                            messageId = kind == A2aDataExFW.KIND_MESSAGE ? value : messageId;
                            break;
                        default:
                            break;
                        }
                    }
                    else if (depth == 3 + base && kind != A2aDataExFW.KIND_MESSAGE)
                    {
                        if ("status".equals(inner) && "message".equals(member) && "messageId".equals(leaf))
                        {
                            messageId = parser.getString();
                        }
                    }
                    else if (depth == 2 + base && kind != A2aDataExFW.KIND_MESSAGE)
                    {
                        final String value = parser.getString();
                        if ("status".equals(inner) && "state".equals(member))
                        {
                            state = value.startsWith(STATE_PREFIX) ? value.substring(STATE_PREFIX.length()) : value;
                        }
                        else if ("artifact".equals(inner) && "artifactId".equals(member))
                        {
                            artifactId = value;
                        }
                    }
                    break;
                default:
                    break;
                }
            }
            parser.reset();
        }

        return kind != NO_KIND;
    }
}
