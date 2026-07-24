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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequestGenerator.Assignment;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequestGenerator.Config;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequestGenerator.Request;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateTopicsRequestGenerator.Topic;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code create_topics} tool call's JSON arguments
 * body directly into a {@link Request} for {@code KafkaCreateTopicsRequestGenerator}, without
 * materializing a generic JSON tree. Follows {@link McpKafkaArguments}'s streaming-first
 * approach, generalized with an explicit context stack since {@code arguments.topics} is a
 * variable-length array of nested objects rather than a fixed set of scalar paths.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "topics": [
 *       {
 *         "name": "events",
 *         "partitions": 1,
 *         "replicas": 1,
 *         "assignments": [ { "partition": 0, "brokers": [0] } ],
 *         "configs": { "cleanup.policy": "delete" }
 *       }
 *     ],
 *     "timeout": 0,
 *     "validateOnly": false
 *   }
 * }
 * }</pre>
 * {@code assignments}, {@code configs}, {@code timeout} and {@code validateOnly} are optional.
 */
public final class McpKafkaToolCreateTopicsSource implements JsonSink
{
    private enum Context
    {
        ROOT,
        ARGUMENTS,
        TOPICS,
        TOPIC,
        ASSIGNMENTS,
        ASSIGNMENT,
        BROKERS,
        CONFIGS
    }

    private final Deque<Context> stack = new ArrayDeque<>();
    private final List<Topic> topics = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();

    private String key;

    private String topicName;
    private int partitions;
    private short replicas;
    private List<Assignment> assignments;
    private List<Config> configs;

    private int partitionIndex;
    private List<Integer> brokerIds;

    private int timeoutMs;
    private boolean validateOnly;

    private Request request;

    public Request request()
    {
        return request;
    }

    @Override
    public void reset()
    {
        stack.clear();
        topics.clear();
        text.setLength(0);
        key = null;
        timeoutMs = 0;
        validateOnly = false;
        request = null;
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
        case VALUE_TRUE:
            onScalar("true");
            break;
        case VALUE_FALSE:
            onScalar("false");
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
        else if (parent == Context.TOPICS)
        {
            next = Context.TOPIC;
            topicName = null;
            partitions = 0;
            replicas = 0;
            assignments = new ArrayList<>();
            configs = new ArrayList<>();
        }
        else if (parent == Context.ASSIGNMENTS)
        {
            next = Context.ASSIGNMENT;
            partitionIndex = 0;
            brokerIds = new ArrayList<>();
        }
        else if (parent == Context.TOPIC && "configs".equals(key))
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

        switch (ending)
        {
        case TOPIC:
            if (topicName == null || partitions <= 0 || replicas <= 0)
            {
                status = Status.REJECTED;
            }
            else
            {
                topics.add(new Topic(topicName, partitions, replicas, assignments, configs));
            }
            break;
        case ASSIGNMENT:
            assignments.add(new Assignment(partitionIndex, brokerIds));
            break;
        case ROOT:
            if (topics.isEmpty())
            {
                status = Status.REJECTED;
            }
            else
            {
                request = new Request(topics, timeoutMs, validateOnly);
                status = Status.COMPLETED;
            }
            break;
        default:
            break;
        }

        return status;
    }

    private void onStartArray()
    {
        final Context parent = current();
        final Context next;
        if (parent == Context.ARGUMENTS && "topics".equals(key))
        {
            next = Context.TOPICS;
        }
        else if (parent == Context.TOPIC && "assignments".equals(key))
        {
            next = Context.ASSIGNMENTS;
        }
        else if (parent == Context.ASSIGNMENT && "brokers".equals(key))
        {
            next = Context.BROKERS;
        }
        else
        {
            next = null;
        }
        stack.push(next);
    }

    private void onScalar(
        String value)
    {
        switch (current())
        {
        case TOPIC:
            onTopicScalar(value);
            break;
        case ASSIGNMENT:
            if ("partition".equals(key))
            {
                partitionIndex = parseInt(value, 0);
            }
            break;
        case BROKERS:
            brokerIds.add(parseInt(value, 0));
            break;
        case CONFIGS:
            configs.add(new Config(key, value));
            break;
        case ARGUMENTS:
            onArgumentsScalar(value);
            break;
        default:
            break;
        }
    }

    private void onTopicScalar(
        String value)
    {
        switch (key)
        {
        case "name":
            topicName = value;
            break;
        case "partitions":
            partitions = parseInt(value, 0);
            break;
        case "replicas":
            replicas = (short) parseInt(value, 0);
            break;
        default:
            break;
        }
    }

    private void onArgumentsScalar(
        String value)
    {
        switch (key)
        {
        case "timeout":
            timeoutMs = parseInt(value, 0);
            break;
        case "validateOnly":
            validateOnly = Boolean.parseBoolean(value);
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
