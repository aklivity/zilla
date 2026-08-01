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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeConfigsRequest.Source.ResourceConsumer;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code describe_configs} tool call's JSON arguments
 * body into a small internal scratch representation, then exposes it as a {@link Source} (and,
 * since the tool describes exactly one resource per call, that resource's own
 * {@link Source.Resource} too) that any consumer (a {@code Generator}, a size calculator, or a
 * future transform) can drive, without materializing a generic JSON tree. Mirrors
 * {@link McpKafkaToolDeleteTopicsSource}'s simplicity, since {@code arguments} is a flat object of
 * scalars with no nested array. Always requests every config for the resource
 * ({@link KafkaDescribeConfigsRequest#ALL_CONFIGS}) - the tool exposes no config-name filter.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "resource_type": "topic",
 *     "resource_name": "events"
 *   }
 * }
 * }</pre>
 */
public final class McpKafkaToolDescribeConfigsSource implements JsonSink, Source, Source.Resource
{
    private static final String RESOURCE_TYPE_TOPIC_NAME = "topic";
    private static final String RESOURCE_TYPE_BROKER_NAME = "broker";

    private enum Context
    {
        ROOT,
        ARGUMENTS
    }

    private final Deque<Context> stack = new ArrayDeque<>();
    private final StringBuilder text = new StringBuilder();

    private String key;
    private byte resourceType;
    private String resourceName;
    private boolean completed;

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
        return KafkaDescribeConfigsRequest.ALL_CONFIGS;
    }

    @Override
    public void forEachConfigName(
        Consumer<String> consumer)
    {
    }

    @Override
    public void reset()
    {
        stack.clear();
        text.setLength(0);
        key = null;
        resourceType = 0;
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
            if (resourceType == 0 || resourceName == null)
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
        if (current() == Context.ARGUMENTS)
        {
            switch (key)
            {
            case "resource_type":
                resourceType = resourceTypeOf(value);
                break;
            case "resource_name":
                resourceName = value;
                break;
            default:
                break;
            }
        }
    }

    private static byte resourceTypeOf(
        String value)
    {
        byte type = 0;
        if (RESOURCE_TYPE_TOPIC_NAME.equals(value))
        {
            type = KafkaDescribeConfigsRequest.RESOURCE_TYPE_TOPIC;
        }
        else if (RESOURCE_TYPE_BROKER_NAME.equals(value))
        {
            type = KafkaDescribeConfigsRequest.RESOURCE_TYPE_BROKER;
        }
        return type;
    }
}
