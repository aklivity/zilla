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
import java.util.Deque;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaMetadataRequest.Source;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code describe_topic} tool call's JSON arguments body,
 * exposing the single named topic as a {@link Source} of exactly one topic - mirrors
 * {@link McpKafkaToolDeleteTopicsSource}, simplified further since there is only ever one topic name
 * rather than an array.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "topic": "events"
 *   }
 * }
 * }</pre>
 */
public final class McpKafkaToolDescribeTopicSource implements JsonSink, Source
{
    private enum Context
    {
        ROOT,
        ARGUMENTS,
        IGNORED
    }

    private final Deque<Context> stack = new ArrayDeque<>();
    private final StringBuilder text = new StringBuilder();

    private String key;
    private String topic;
    private boolean completed;

    public boolean completed()
    {
        return completed;
    }

    @Override
    public boolean allTopics()
    {
        return false;
    }

    @Override
    public int topicCount()
    {
        return topic != null ? 1 : 0;
    }

    @Override
    public void forEach(
        Consumer<String> consumer)
    {
        if (topic != null)
        {
            consumer.accept(topic);
        }
    }

    @Override
    public void reset()
    {
        stack.clear();
        text.setLength(0);
        key = null;
        topic = null;
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
        case KEY_NAME:
            key = source.getStringView().toString();
            break;
        case VALUE_STRING:
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
            // ArrayDeque forbids null elements, so an unrecognized object (e.g. a JSON-RPC "_meta"
            // sibling of "arguments") pushes IGNORED rather than null to keep the stack depth correct.
            next = Context.IGNORED;
        }
        stack.push(next);
    }

    private Status onEndObject()
    {
        final Context ending = stack.pop();
        Status status = Status.ADVANCED;

        if (ending == Context.ROOT)
        {
            if (topic == null)
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

    private void onScalar(
        String value)
    {
        if (current() == Context.ARGUMENTS && "topic".equals(key))
        {
            topic = value;
        }
    }
}
