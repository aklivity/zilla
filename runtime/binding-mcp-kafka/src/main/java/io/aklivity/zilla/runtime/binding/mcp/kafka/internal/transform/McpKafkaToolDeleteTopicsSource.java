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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.transform;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteTopicsRequest.Source;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code delete_topics} tool call's JSON arguments body
 * into a small internal scratch representation, then exposes it as a {@link Source} that any
 * consumer (a {@code Generator}, a size calculator, or a future transform) can drive, without
 * materializing a generic JSON tree. Mirrors {@link McpKafkaToolCreateTopicsSource}, simplified since
 * {@code arguments.topics} is a flat array of topic name strings rather than nested objects.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "topics": ["events", "snapshots"],
 *     "timeout": 30000
 *   }
 * }
 * }</pre>
 * {@code timeout} is optional, defaulting to {@code zilla.binding.mcp.kafka.request.timeout}
 * (default {@code PT30S}).
 */
public final class McpKafkaToolDeleteTopicsSource implements JsonSink, Source
{
    private enum Context
    {
        ROOT,
        ARGUMENTS,
        TOPICS
    }

    private final int defaultTimeoutMs;
    private final Deque<Context> stack = new ArrayDeque<>();
    private final List<String> topics = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();

    private String key;
    private int timeoutMs;
    private boolean completed;

    public McpKafkaToolDeleteTopicsSource(
        int defaultTimeoutMs)
    {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public boolean completed()
    {
        return completed;
    }

    @Override
    public int topicCount()
    {
        return topics.size();
    }

    @Override
    public void forEach(
        Consumer<String> consumer)
    {
        topics.forEach(consumer);
    }

    @Override
    public int timeoutMs()
    {
        return timeoutMs;
    }

    @Override
    public void reset()
    {
        stack.clear();
        topics.clear();
        text.setLength(0);
        key = null;
        timeoutMs = defaultTimeoutMs;
        completed = false;
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
        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_OBJECT:
            onStartObject();
            break;
        case END_OBJECT:
            status = onEndObject();
            break;
        case START_ARRAY:
            onStartArray();
            break;
        case END_ARRAY:
            stack.pop();
            break;
        case KEY_NAME:
            key = source.getStringView().toString();
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
            text.append(source.getStringView());
            if (!source.deferredBytes())
            {
                onScalar(text.toString());
                text.setLength(0);
            }
            break;
        default:
            break;
        }

        return status;
    }

    private Context current()
    {
        return stack.peek();
    }

    private void onStartObject()
    {
        final Context parent = current();
        final Context next;
        if (parent == null)
        {
            next = Context.ROOT;
        }
        else if (parent == Context.ROOT && "arguments".equals(key))
        {
            next = Context.ARGUMENTS;
        }
        else
        {
            next = null;
        }
        stack.push(next);
    }

    private Status onEndObject()
    {
        final Context ending = stack.pop();
        Status status = Status.ADVANCED;

        if (ending == Context.ROOT)
        {
            if (topics.isEmpty())
            {
                status = Status.REJECTED;
            }
            else
            {
                completed = true;
                status = Status.COMPLETED;
            }
        }

        return status;
    }

    private void onStartArray()
    {
        final Context parent = current();
        final Context next = parent == Context.ARGUMENTS && "topics".equals(key) ? Context.TOPICS : null;
        stack.push(next);
    }

    private void onScalar(
        String value)
    {
        switch (current())
        {
        case TOPICS:
            topics.add(value);
            break;
        case ARGUMENTS:
            if ("timeout".equals(key))
            {
                timeoutMs = parseInt(value, defaultTimeoutMs);
            }
            break;
        default:
            break;
        }
    }

    private static int parseInt(
        String value,
        int defaultValue)
    {
        int parsed = defaultValue;
        try
        {
            parsed = Integer.parseInt(value);
        }
        catch (NumberFormatException ex)
        {
        }
        return parsed;
    }
}
