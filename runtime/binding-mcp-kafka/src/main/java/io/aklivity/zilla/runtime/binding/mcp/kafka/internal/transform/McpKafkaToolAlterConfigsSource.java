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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Source.ConfigConsumer;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAlterConfigsRequest.Source.ResourceConsumer;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code alter_topic_configs}/{@code alter_broker_configs}
 * tool call's JSON arguments body into a small internal scratch representation, then exposes it as a
 * {@link Source} (and, since the tool operates on exactly one resource per call, that resource's own
 * {@link Source.Resource} too) that any consumer (a {@code Generator}, a size calculator, or a
 * future transform) can drive, without materializing a generic JSON tree. Mirrors
 * {@link McpKafkaToolCreateTopicsSource}'s context-stack approach, simplified to a single resource
 * with a flat {@code configs} object rather than an array of nested resources. The resource type is
 * fixed at construction by which of the two tools is calling - it is implied by the tool name and is
 * not part of the JSON arguments.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "resource_name": "events",
 *     "configs": {
 *       "cleanup.policy": "delete",
 *       "retention.ms": "60000"
 *     }
 *   }
 * }
 * }</pre>
 */
public final class McpKafkaToolAlterConfigsSource implements JsonSink, Source, Source.Resource
{
    private enum Context
    {
        ROOT,
        ARGUMENTS,
        CONFIGS
    }

    private final Deque<Context> stack = new ArrayDeque<>();
    private final List<ParsedConfig> configs = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private final byte resourceType;

    private String key;
    private String resourceName;
    private boolean completed;

    public McpKafkaToolAlterConfigsSource(
        byte resourceType)
    {
        this.resourceType = resourceType;
    }

    public boolean completed()
    {
        return completed;
    }

    @Override
    public int resourceCount()
    {
        return 1;
    }

    @Override
    public void forEach(
        ResourceConsumer consumer)
    {
        consumer.accept(this);
    }

    @Override
    public boolean validateOnly()
    {
        return false;
    }

    @Override
    public byte type()
    {
        return resourceType;
    }

    @Override
    public String name()
    {
        return resourceName;
    }

    @Override
    public int configCount()
    {
        return configs.size();
    }

    @Override
    public void forEachConfig(
        ConfigConsumer consumer)
    {
        configs.forEach(consumer::accept);
    }

    @Override
    public void reset()
    {
        stack.clear();
        configs.clear();
        text.setLength(0);
        key = null;
        resourceName = null;
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
        else if (parent == Context.ARGUMENTS && "configs".equals(key))
        {
            next = Context.CONFIGS;
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
            if (resourceName == null)
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
        switch (current())
        {
        case CONFIGS:
            configs.add(new ParsedConfig(key, value));
            break;
        case ARGUMENTS:
            onArgumentsScalar(value);
            break;
        default:
            break;
        }
    }

    private void onArgumentsScalar(
        String value)
    {
        if ("resource_name".equals(key))
        {
            resourceName = value;
        }
    }

    private record ParsedConfig(
        String name,
        String value) implements Source.Config
    {
    }
}
