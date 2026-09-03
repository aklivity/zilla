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
 * Terminal {@link JsonSink} shared by the {@code list_topics} and {@code cluster_overview} tools,
 * neither of which takes any required argument - both request every topic in the cluster. Parses
 * just enough of the JSON body to find the matching root object close, ignoring any content under
 * {@code arguments} rather than requiring it to be empty, so a client that passes an empty object or
 * omits {@code arguments} entirely both complete the same way.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {}
 * }
 * }</pre>
 */
public final class McpKafkaToolAllTopicsSource implements JsonSink, Source
{
    private enum Context
    {
        ROOT,
        ARGUMENTS,
        IGNORED
    }

    private final Deque<Context> stack = new ArrayDeque<>();

    private String key;
    private boolean completed;

    public boolean completed()
    {
        return completed;
    }

    @Override
    public boolean allTopics()
    {
        return true;
    }

    @Override
    public int topicCount()
    {
        return 0;
    }

    @Override
    public void forEach(
        Consumer<String> consumer)
    {
    }

    @Override
    public void reset()
    {
        stack.clear();
        key = null;
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
            // ArrayDeque forbids null elements, so IGNORED stands in for null to keep the stack
            // depth correct while still ignoring any array content, including under "arguments".
            stack.push(Context.IGNORED);
            break;
        case END_ARRAY:
            stack.pop();
            break;
        case KEY_NAME:
            key = source.getStringView().toString();
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
            completed = true;
            status = Status.COMPLETED;
        }

        return status;
    }
}
