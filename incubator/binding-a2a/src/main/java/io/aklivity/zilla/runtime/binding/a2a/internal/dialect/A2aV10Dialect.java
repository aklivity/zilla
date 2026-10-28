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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.SCHEME_BEARER;
import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.integer;
import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.string;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

public final class A2aV10Dialect implements A2aDialect
{
    public static final String VERSION = "1.0";

    private static final String BINDING_JSONRPC = "JSONRPC";

    @Override
    public String version()
    {
        return VERSION;
    }

    @Override
    public A2aOperation operation(
        String method)
    {
        A2aOperation operation = null;
        for (A2aOperation candidate : A2aOperation.values())
        {
            if (candidate.method().equals(method))
            {
                operation = candidate;
                break;
            }
        }
        return operation;
    }

    @Override
    public A2aRequestParams request(
        A2aOperation operation,
        JsonObject params)
    {
        final JsonObject members = params != null ? params : JsonValue.EMPTY_JSON_OBJECT;

        String taskId = null;
        String configId = null;
        String contextId = null;
        int historyLength = -1;

        switch (operation)
        {
        case TASKS_GET:
            taskId = string(members, "id");
            historyLength = integer(members, "historyLength", -1);
            break;
        case TASKS_CANCEL:
        case TASKS_SUBSCRIBE:
            taskId = string(members, "id");
            break;
        case PUSH_CONFIG_CREATE:
        case PUSH_CONFIG_LIST:
            taskId = string(members, "taskId");
            break;
        case PUSH_CONFIG_GET:
        case PUSH_CONFIG_DELETE:
            taskId = string(members, "taskId");
            configId = string(members, "id");
            break;
        case TASKS_LIST:
            contextId = string(members, "contextId");
            break;
        default:
            break;
        }

        final boolean valid = switch (operation)
        {
        case TASKS_GET, TASKS_CANCEL, TASKS_SUBSCRIBE, PUSH_CONFIG_CREATE, PUSH_CONFIG_LIST -> taskId != null;
        case PUSH_CONFIG_GET, PUSH_CONFIG_DELETE -> taskId != null && configId != null;
        default -> true;
        };

        return valid ? new A2aRequestParams(taskId, configId, contextId, historyLength, null) : null;
    }

    @Override
    public JsonValue result(
        A2aOperation operation,
        JsonValue canonical)
    {
        return canonical;
    }

    @Override
    public JsonObject card(
        JsonObject canonical,
        String url,
        boolean secured)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        canonical.forEach((name, value) ->
        {
            switch (name)
            {
            case "supportedInterfaces":
                builder.add(name, interfaces(value, url));
                break;
            case "signatures":
            case "securitySchemes":
            case "securityRequirements":
            case "security":
                break;
            default:
                builder.add(name, value);
                break;
            }
        });

        if (!canonical.containsKey("supportedInterfaces"))
        {
            builder.add("supportedInterfaces", interfaces(JsonValue.EMPTY_JSON_ARRAY, url));
        }

        if (secured)
        {
            builder.add("securitySchemes", Json.createObjectBuilder()
                .add(SCHEME_BEARER, Json.createObjectBuilder()
                    .add("httpAuthSecurityScheme", Json.createObjectBuilder()
                        .add("scheme", "Bearer"))));
            builder.add("securityRequirements", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("schemes", Json.createObjectBuilder()
                        .add(SCHEME_BEARER, Json.createObjectBuilder()
                            .add("list", Json.createArrayBuilder())))));
        }

        return builder.build();
    }

    @Override
    public JsonObject requestParams(
        A2aOperation operation,
        A2aRequestParams request,
        String tenant)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();

        if (tenant != null && (request.body() == null || !(request.body() instanceof JsonObject body) ||
            !body.containsKey("tenant")))
        {
            builder.add("tenant", tenant);
        }

        switch (operation)
        {
        case TASKS_GET:
            builder.add("id", request.taskId());
            if (request.historyLength() >= 0)
            {
                builder.add("historyLength", request.historyLength());
            }
            break;
        case TASKS_CANCEL:
        case TASKS_SUBSCRIBE:
            builder.add("id", request.taskId());
            break;
        case PUSH_CONFIG_LIST:
            builder.add("taskId", request.taskId());
            break;
        case PUSH_CONFIG_GET:
        case PUSH_CONFIG_DELETE:
            builder.add("taskId", request.taskId()).add("id", request.configId());
            break;
        case EXTENDED_AGENT_CARD:
            break;
        default:
            if (request.body() instanceof JsonObject body)
            {
                body.forEach(builder::add);
            }
            break;
        }

        final JsonObject params = builder.build();

        return operation == A2aOperation.EXTENDED_AGENT_CARD && tenant == null ? null : params;
    }

    @Override
    public JsonValue resultFromWire(
        A2aOperation operation,
        JsonValue wire)
    {
        return wire;
    }

    @Override
    public JsonObject cardFromWire(
        JsonObject wire)
    {
        return wire;
    }

    @Override
    public boolean verbatim()
    {
        return true;
    }

    private JsonArray interfaces(
        JsonValue declared,
        String url)
    {
        final JsonArray canonical = declared instanceof JsonArray array ? array : JsonValue.EMPTY_JSON_ARRAY;
        final JsonArrayBuilder builder = Json.createArrayBuilder();

        boolean served = false;
        for (JsonValue item : canonical)
        {
            if (item instanceof JsonObject candidate && BINDING_JSONRPC.equals(string(candidate, "protocolBinding")))
            {
                final JsonObjectBuilder rewritten = Json.createObjectBuilder();
                candidate.forEach((name, value) ->
                {
                    if ("url".equals(name))
                    {
                        rewritten.add(name, url);
                    }
                    else
                    {
                        rewritten.add(name, value);
                    }
                });
                builder.add(rewritten);
                served = true;
            }
        }

        if (!served)
        {
            builder.add(Json.createObjectBuilder()
                .add("url", url)
                .add("protocolBinding", BINDING_JSONRPC)
                .add("protocolVersion", VERSION));
        }

        return builder.build();
    }
}
