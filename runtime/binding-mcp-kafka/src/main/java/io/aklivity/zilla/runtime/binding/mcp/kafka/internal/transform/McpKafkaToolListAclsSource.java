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

import io.aklivity.zilla.runtime.binding.kafka.api.KafkaAclTypes;
import io.aklivity.zilla.runtime.binding.kafka.api.KafkaDescribeAclsRequest.Source;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that parses the {@code list_acls} tool call's JSON arguments body into a
 * small internal scratch representation, then exposes it as a {@link Source} that any consumer (a
 * {@code Generator}, a size calculator, or a future transform) can drive, without materializing a
 * generic JSON tree. Mirrors {@link McpKafkaToolDescribeConfigsSource}'s flat single-filter shape,
 * since the tool describes exactly one ACL filter per call. Every field is optional, matching
 * {@code AclBindingFilter} semantics - an absent field matches any value for that field.
 * <p>
 * Expected shape:
 * <pre>{@code
 * {
 *   "arguments": {
 *     "resource_type": "topic",
 *     "resource_name": "events",
 *     "pattern_type": "literal",
 *     "principal": "User:alice",
 *     "host": "*",
 *     "operation": "read",
 *     "permission_type": "allow"
 *   }
 * }
 * }</pre>
 */
public final class McpKafkaToolListAclsSource implements JsonSink, Source
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
    public byte resourceType()
    {
        return KafkaAclTypes.resourceType(resourceType);
    }

    @Override
    public String resourceName()
    {
        return resourceName;
    }

    @Override
    public byte patternType()
    {
        return KafkaAclTypes.patternType(patternType == null ? "any" : patternType);
    }

    @Override
    public String principal()
    {
        return principal;
    }

    @Override
    public String host()
    {
        return host;
    }

    @Override
    public byte operation()
    {
        return KafkaAclTypes.operation(operation);
    }

    @Override
    public byte permissionType()
    {
        return KafkaAclTypes.permissionType(permissionType);
    }

    @Override
    public void reset()
    {
        stack.clear();
        text.setLength(0);
        key = null;
        resourceType = null;
        resourceName = null;
        patternType = null;
        principal = null;
        host = null;
        operation = null;
        permissionType = null;
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

    private void onScalar(
        String value)
    {
        if (current() == Context.ARGUMENTS)
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
}
