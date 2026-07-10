/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform;

import java.util.Map;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that captures the fixed set of scalar tool-call argument fields
 * mcp-kafka's {@code produce}/{@code consume} tools accept ({@code arguments.topic},
 * {@code arguments.key}, {@code arguments.value}, {@code arguments.partition},
 * {@code arguments.offset}, {@code arguments.limit}) as the request body streams in, without
 * buffering the whole body first. There is no downstream stage -- captured values are read back
 * from {@code captured} once the pipeline reports {@link Status#COMPLETED} -- so every event is
 * observed here and never forwarded.
 */
public final class McpKafkaArguments implements JsonSink
{
    private static final String[] PATHS =
    {
        "arguments.topic",
        "arguments.key",
        "arguments.value",
        "arguments.partition",
        "arguments.offset",
        "arguments.limit"
    };

    private final Map<String, String> captured;
    private final String[][] segments;
    private final int[] matched;
    private final StringBuilder text = new StringBuilder();

    private int depth;
    private int awaiting = -1;

    public McpKafkaArguments(
        Map<String, String> captured)
    {
        this.captured = captured;
        this.segments = new String[PATHS.length][];
        for (int i = 0; i < PATHS.length; i++)
        {
            this.segments[i] = PATHS[i].split("\\.");
        }
        this.matched = new int[PATHS.length];
    }

    @Override
    public void reset()
    {
        depth = 0;
        awaiting = -1;
        text.setLength(0);
        for (int i = 0; i < matched.length; i++)
        {
            matched[i] = 0;
        }
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        if (awaiting != -1)
        {
            capture(awaiting, event, source);
        }

        Status status = Status.ADVANCED;
        switch (event)
        {
        case START_OBJECT:
            depth++;
            break;
        case END_OBJECT:
            depth--;
            if (depth == 0)
            {
                status = Status.COMPLETED;
            }
            break;
        case START_ARRAY:
            depth++;
            break;
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            onKeyName(source);
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            if (depth == 0)
            {
                status = Status.REJECTED;
            }
            break;
        default:
            break;
        }

        return status;
    }

    private void onKeyName(
        JsonSource source)
    {
        for (int i = 0; i < segments.length; i++)
        {
            if (matched[i] < segments[i].length &&
                depth == matched[i] + 1 &&
                segments[i][matched[i]].contentEquals(source.getStringView()))
            {
                matched[i]++;
                if (matched[i] == segments[i].length)
                {
                    awaiting = i;
                }
            }
        }
    }

    private void capture(
        int index,
        JsonEvent event,
        JsonSource source)
    {
        switch (event)
        {
        case VALUE_STRING:
        case VALUE_NUMBER:
            text.append(source.getStringView());
            if (!source.deferredBytes())
            {
                captured.put(PATHS[index], text.toString());
                text.setLength(0);
                awaiting = -1;
            }
            break;
        case VALUE_TRUE:
            captured.put(PATHS[index], "true");
            awaiting = -1;
            break;
        case VALUE_FALSE:
            captured.put(PATHS[index], "false");
            awaiting = -1;
            break;
        default:
            awaiting = -1;
            break;
        }
    }
}
