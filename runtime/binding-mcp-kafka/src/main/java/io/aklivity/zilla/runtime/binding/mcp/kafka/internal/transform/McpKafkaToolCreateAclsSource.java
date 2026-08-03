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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAclTypes;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaCreateAclsRequest.Source.CreationConsumer;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code create_acls} tool call's JSON arguments body into
 * a small internal scratch representation, then exposes it as a {@link Source} that any consumer (a
 * {@code Generator}, a size calculator, or a future transform) can drive, without materializing a
 * generic JSON tree. Follows {@link McpKafkaToolCreateTopicsSource}'s context-stack approach for a
 * variable-length array of nested objects, flattened to one level since a single ACL creation has no
 * further nested arrays. {@code resource_type}, {@code resource_name}, {@code principal},
 * {@code operation} and {@code permission_type} are required per creation; {@code pattern_type}
 * defaults to {@code literal} and {@code host} defaults to {@code *} when omitted.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "acls": [
 *       {
 *         "resource_type": "topic",
 *         "resource_name": "events",
 *         "pattern_type": "literal",
 *         "principal": "User:alice",
 *         "host": "*",
 *         "operation": "read",
 *         "permission_type": "allow"
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 */
public final class McpKafkaToolCreateAclsSource implements JsonSink, Source
{
    private static final String DEFAULT_PATTERN_TYPE = "literal";
    private static final String DEFAULT_HOST = "*";

    private enum Context
    {
        ROOT,
        ARGUMENTS,
        ACLS,
        ACL
    }

    private final Deque<Context> stack = new ArrayDeque<>();
    private final List<ParsedCreation> creations = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();

    private String key;

    private String resourceType;
    private String resourceName;
    private String patternType;
    private String principal;
    private String host;
    private String operation;
    private String permissionType;

    private boolean completed;

    public boolean completed()
    {
        return completed;
    }

    @Override
    public int creationCount()
    {
        return creations.size();
    }

    @Override
    public void forEach(
        CreationConsumer consumer)
    {
        creations.forEach(consumer::accept);
    }

    @Override
    public void reset()
    {
        stack.clear();
        creations.clear();
        text.setLength(0);
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
        else if (parent == Context.ACLS)
        {
            next = Context.ACL;
            resourceType = null;
            resourceName = null;
            patternType = null;
            principal = null;
            host = null;
            operation = null;
            permissionType = null;
        }
        else
        {
            next = null;
        }
        stack.push(next);
    }

    private void onStartArray()
    {
        final Context parent = current();
        final Context next;
        if (parent == Context.ARGUMENTS && "acls".equals(key))
        {
            next = Context.ACLS;
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
        case ACL:
            if (resourceType == null || resourceName == null || principal == null ||
                operation == null || permissionType == null)
            {
                status = Status.REJECTED;
            }
            else
            {
                creations.add(new ParsedCreation(resourceType, resourceName, patternType, principal, host, operation,
                    permissionType));
            }
            break;
        case ROOT:
            if (creations.isEmpty())
            {
                status = Status.REJECTED;
            }
            else
            {
                completed = true;
                status = Status.COMPLETED;
            }
            break;
        default:
            break;
        }

        return status;
    }

    private void onScalar(
        String value)
    {
        if (current() == Context.ACL)
        {
            switch (key)
            {
            case "resource_type":
                resourceType = value;
                break;
            case "resource_name":
                resourceName = value;
                break;
            case "pattern_type":
                patternType = value;
                break;
            case "principal":
                principal = value;
                break;
            case "host":
                host = value;
                break;
            case "operation":
                operation = value;
                break;
            case "permission_type":
                permissionType = value;
                break;
            default:
                break;
            }
        }
    }

    private record ParsedCreation(
        String resourceTypeRaw,
        String resourceName,
        String resourcePatternTypeRaw,
        String principal,
        String hostRaw,
        String operationRaw,
        String permissionTypeRaw) implements Source.Creation
    {
        @Override
        public byte resourceType()
        {
            return KafkaAclTypes.resourceType(resourceTypeRaw);
        }

        @Override
        public byte resourcePatternType()
        {
            return KafkaAclTypes.patternType(resourcePatternTypeRaw == null ? DEFAULT_PATTERN_TYPE : resourcePatternTypeRaw);
        }

        @Override
        public String host()
        {
            return hostRaw == null ? DEFAULT_HOST : hostRaw;
        }

        @Override
        public byte operation()
        {
            return KafkaAclTypes.operation(operationRaw);
        }

        @Override
        public byte permissionType()
        {
            return KafkaAclTypes.permissionType(permissionTypeRaw);
        }
    }
}
