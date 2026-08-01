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
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsRequest.Source;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDeleteAclsRequest.Source.FilterConsumer;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code delete_acls} tool call's JSON arguments body into
 * a small internal scratch representation, then exposes it as a {@link Source} that any consumer (a
 * {@code Generator}, a size calculator, or a future transform) can drive, without materializing a
 * generic JSON tree. Same array-of-objects shape as {@link McpKafkaToolCreateAclsSource}, but every
 * field per filter is optional, matching {@code AclBindingFilter} semantics - an absent field matches
 * any value for that field. At least one filter is required; an empty filter (matching every ACL
 * binding) is a valid, if broad, single entry.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "acls": [
 *       {
 *         "resource_type": "topic",
 *         "resource_name": "events",
 *         "principal": "User:alice"
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 */
public final class McpKafkaToolDeleteAclsSource implements JsonSink, Source
{
    private enum Context
    {
        ROOT,
        ARGUMENTS,
        ACLS,
        ACL
    }

    private final Deque<Context> stack = new ArrayDeque<>();
    private final List<ParsedFilter> filters = new ArrayList<>();
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
    public int filterCount()
    {
        return filters.size();
    }

    @Override
    public void forEach(
        FilterConsumer consumer)
    {
        filters.forEach(consumer::accept);
    }

    @Override
    public void reset()
    {
        stack.clear();
        filters.clear();
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
            filters.add(new ParsedFilter(resourceType, resourceName, patternType, principal, host, operation,
                permissionType));
            break;
        case ROOT:
            if (filters.isEmpty())
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

    private record ParsedFilter(
        String resourceTypeRaw,
        String resourceName,
        String patternTypeRaw,
        String principal,
        String host,
        String operationRaw,
        String permissionTypeRaw) implements Source.Filter
    {
        @Override
        public byte resourceType()
        {
            return KafkaAclTypes.resourceType(resourceTypeRaw);
        }

        @Override
        public byte patternType()
        {
            return KafkaAclTypes.patternType(patternTypeRaw == null ? "any" : patternTypeRaw);
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
