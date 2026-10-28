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
import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.array;
import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.integer;
import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.map;
import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.object;
import static io.aklivity.zilla.runtime.binding.a2a.internal.dialect.A2aJsonMembers.string;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class A2aV03Dialect implements A2aDialect
{
    public static final String VERSION = "0.3";

    private static final String PROTOCOL_VERSION = "0.3.0";
    private static final String BINDING_JSONRPC = "JSONRPC";

    private static final Map<String, String> ROLES_FROM_CANONICAL = Map.of(
        "ROLE_USER", "user",
        "ROLE_AGENT", "agent");

    private static final Map<String, String> ROLES_TO_CANONICAL = Map.of(
        "user", "ROLE_USER",
        "agent", "ROLE_AGENT");

    private static final Map<String, String> STATES_FROM_CANONICAL = Map.of(
        "TASK_STATE_SUBMITTED", "submitted",
        "TASK_STATE_WORKING", "working",
        "TASK_STATE_COMPLETED", "completed",
        "TASK_STATE_FAILED", "failed",
        "TASK_STATE_CANCELED", "canceled",
        "TASK_STATE_INPUT_REQUIRED", "input-required",
        "TASK_STATE_REJECTED", "rejected",
        "TASK_STATE_AUTH_REQUIRED", "auth-required");

    private static final Map<String, String> STATES_TO_CANONICAL = Map.of(
        "submitted", "TASK_STATE_SUBMITTED",
        "working", "TASK_STATE_WORKING",
        "completed", "TASK_STATE_COMPLETED",
        "failed", "TASK_STATE_FAILED",
        "canceled", "TASK_STATE_CANCELED",
        "input-required", "TASK_STATE_INPUT_REQUIRED",
        "rejected", "TASK_STATE_REJECTED",
        "auth-required", "TASK_STATE_AUTH_REQUIRED");

    private static final Map<String, String> KINDS = Map.of(
        "task", "task",
        "message", "message",
        "statusUpdate", "status-update",
        "artifactUpdate", "artifact-update");

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
            if (candidate.legacyMethod() != null && candidate.legacyMethod().equals(method))
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

        A2aRequestParams request = null;

        switch (operation)
        {
        case MESSAGE_SEND:
        case MESSAGE_STREAM:
            request = new A2aRequestParams(null, null, null, -1, messageParams(members));
            break;
        case TASKS_GET:
            request = withTask(string(members, "id"), null, integer(members, "historyLength", -1), null);
            break;
        case TASKS_CANCEL:
        case TASKS_SUBSCRIBE:
        case PUSH_CONFIG_LIST:
            request = withTask(string(members, "id"), null, -1, null);
            break;
        case PUSH_CONFIG_GET:
        case PUSH_CONFIG_DELETE:
            final String configId = string(members, "pushNotificationConfigId");
            request = configId != null ? withTask(string(members, "id"), configId, -1, null) : null;
            break;
        case PUSH_CONFIG_CREATE:
            request = pushConfigCreate(members);
            break;
        default:
            request = new A2aRequestParams(null, null, null, -1, null);
            break;
        }

        return request;
    }

    @Override
    public JsonValue result(
        A2aOperation operation,
        JsonValue canonical)
    {
        JsonValue result = canonical;

        if (canonical instanceof JsonObject object)
        {
            switch (operation)
            {
            case TASKS_GET:
            case TASKS_CANCEL:
                result = task(object);
                break;
            case MESSAGE_SEND:
            case MESSAGE_STREAM:
            case TASKS_SUBSCRIBE:
                result = event(object);
                break;
            case PUSH_CONFIG_CREATE:
            case PUSH_CONFIG_GET:
                result = pushConfig(object);
                break;
            case PUSH_CONFIG_LIST:
                final JsonArray configs = array(object, "configs");
                final JsonArrayBuilder list = Json.createArrayBuilder();
                if (configs != null)
                {
                    configs.forEach(c -> list.add(c instanceof JsonObject config ? pushConfig(config) : c));
                }
                result = list.build();
                break;
            default:
                break;
            }
        }

        return result;
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
                builder.add("url", url);
                builder.add("preferredTransport", BINDING_JSONRPC);
                builder.add("protocolVersion", PROTOCOL_VERSION);
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
            builder.add("url", url);
            builder.add("preferredTransport", BINDING_JSONRPC);
            builder.add("protocolVersion", PROTOCOL_VERSION);
        }

        if (secured)
        {
            builder.add("securitySchemes", Json.createObjectBuilder()
                .add(SCHEME_BEARER, Json.createObjectBuilder()
                    .add("type", "http")
                    .add("scheme", SCHEME_BEARER)));
            builder.add("security", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add(SCHEME_BEARER, Json.createArrayBuilder())));
        }

        return builder.build();
    }

    @Override
    public boolean verbatim()
    {
        return false;
    }

    private A2aRequestParams withTask(
        String taskId,
        String configId,
        int historyLength,
        JsonValue body)
    {
        return taskId != null ? new A2aRequestParams(taskId, configId, null, historyLength, body) : null;
    }

    private JsonObject messageParams(
        JsonObject params)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        params.forEach((name, value) -> builder.add(name,
            "message".equals(name) && value instanceof JsonObject message ? messageToCanonical(message) : value));
        return builder.build();
    }

    private JsonObject messageToCanonical(
        JsonObject message)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        message.forEach((name, value) ->
        {
            switch (name)
            {
            case "kind":
                break;
            case "role":
                builder.add(name, value instanceof JsonString role
                    ? ROLES_TO_CANONICAL.getOrDefault(role.getString(), role.getString())
                    : value.toString());
                break;
            case "parts":
                builder.add(name, value instanceof JsonArray parts ? map(parts, this::partToCanonical) : value);
                break;
            default:
                builder.add(name, value);
                break;
            }
        });
        return builder.build();
    }

    private JsonObject partToCanonical(
        JsonObject part)
    {
        final String kind = string(part, "kind");
        final JsonObjectBuilder builder = Json.createObjectBuilder();

        part.forEach((name, value) ->
        {
            if ("file".equals(kind) && "file".equals(name) && value instanceof JsonObject file)
            {
                file.forEach((member, memberValue) -> builder.add(switch (member)
                {
                case "name" -> "filename";
                case "mimeType" -> "mediaType";
                case "bytes" -> "raw";
                case "uri" -> "url";
                default -> member;
                }, memberValue));
            }
            else if (!"kind".equals(name))
            {
                builder.add(name, value);
            }
        });

        return builder.build();
    }

    private A2aRequestParams pushConfigCreate(
        JsonObject params)
    {
        final String taskId = string(params, "taskId");
        final JsonObject config = object(params, "pushNotificationConfig");

        A2aRequestParams request = null;

        if (taskId != null && config != null)
        {
            final String configId = string(config, "id");

            final JsonObjectBuilder body = Json.createObjectBuilder().add("taskId", taskId);
            if (configId != null)
            {
                body.add("configId", configId);
            }

            final JsonObjectBuilder canonical = Json.createObjectBuilder();
            config.forEach((name, value) ->
            {
                if ("authentication".equals(name) && value instanceof JsonObject authentication)
                {
                    canonical.add(name, authenticationToCanonical(authentication));
                }
                else if (!"id".equals(name))
                {
                    canonical.add(name, value);
                }
            });

            request = new A2aRequestParams(taskId, configId, null, -1, body.add("config", canonical).build());
        }

        return request;
    }

    private JsonObject authenticationToCanonical(
        JsonObject authentication)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        authentication.forEach((name, value) ->
        {
            if ("schemes".equals(name) && value instanceof JsonArray schemes)
            {
                if (!schemes.isEmpty())
                {
                    builder.add("scheme", schemes.get(0));
                }
            }
            else
            {
                builder.add(name, value);
            }
        });
        return builder.build();
    }

    private JsonObject authenticationFromCanonical(
        JsonObject authentication)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        authentication.forEach((name, value) ->
        {
            if ("scheme".equals(name))
            {
                builder.add("schemes", Json.createArrayBuilder().add(value));
            }
            else
            {
                builder.add(name, value);
            }
        });
        return builder.build();
    }

    private JsonObject pushConfig(
        JsonObject canonical)
    {
        final String taskId = string(canonical, "taskId");

        final JsonObjectBuilder config = Json.createObjectBuilder();
        canonical.forEach((name, value) ->
        {
            if ("authentication".equals(name) && value instanceof JsonObject authentication)
            {
                config.add(name, authenticationFromCanonical(authentication));
            }
            else if (!"taskId".equals(name))
            {
                config.add(name, value);
            }
        });

        final JsonObjectBuilder builder = Json.createObjectBuilder();
        if (taskId != null)
        {
            builder.add("taskId", taskId);
        }
        return builder.add("pushNotificationConfig", config).build();
    }

    private JsonObject event(
        JsonObject wrapper)
    {
        JsonObject event = wrapper;

        for (Map.Entry<String, JsonValue> member : wrapper.entrySet())
        {
            final String kind = KINDS.get(member.getKey());
            if (kind != null && member.getValue() instanceof JsonObject body)
            {
                event = switch (member.getKey())
                {
                case "task" -> task(body);
                case "message" -> kinded(kind, messageFromCanonical(body));
                case "statusUpdate" -> statusUpdate(body);
                default -> artifactUpdate(body);
                };
            }
        }

        return event;
    }

    private JsonObject kinded(
        String kind,
        JsonObject body)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder().add("kind", kind);
        body.forEach(builder::add);
        return builder.build();
    }

    private JsonObject task(
        JsonObject task)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder().add("kind", KINDS.get("task"));
        task.forEach((name, value) ->
        {
            switch (name)
            {
            case "status":
                builder.add(name, value instanceof JsonObject status ? status(status) : value);
                break;
            case "artifacts":
                builder.add(name, value instanceof JsonArray artifacts ? map(artifacts, this::artifact) : value);
                break;
            case "history":
                builder.add(name, value instanceof JsonArray history ? map(history, this::messageFromCanonical) : value);
                break;
            default:
                builder.add(name, value);
                break;
            }
        });
        return builder.build();
    }

    private JsonObject status(
        JsonObject status)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        status.forEach((name, value) ->
        {
            if ("state".equals(name) && value instanceof JsonString state)
            {
                builder.add(name, STATES_FROM_CANONICAL.getOrDefault(state.getString(), state.getString()));
            }
            else if ("message".equals(name) && value instanceof JsonObject message)
            {
                builder.add(name, messageFromCanonical(message));
            }
            else
            {
                builder.add(name, value);
            }
        });
        return builder.build();
    }

    private JsonObject statusUpdate(
        JsonObject update)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder().add("kind", KINDS.get("statusUpdate"));
        update.forEach((name, value) -> builder.add(name,
            "status".equals(name) && value instanceof JsonObject status ? status(status) : value));

        final JsonObject status = object(update, "status");
        final String state = status != null ? string(status, "state") : null;
        return builder.add("final", isFinal(state)).build();
    }

    private boolean isFinal(
        String state)
    {
        return state != null && switch (state)
        {
        case "TASK_STATE_COMPLETED", "TASK_STATE_FAILED", "TASK_STATE_CANCELED", "TASK_STATE_REJECTED",
             "TASK_STATE_INPUT_REQUIRED", "TASK_STATE_AUTH_REQUIRED" -> true;
        default -> false;
        };
    }

    private JsonObject artifactUpdate(
        JsonObject update)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder().add("kind", KINDS.get("artifactUpdate"));
        update.forEach((name, value) -> builder.add(name,
            "artifact".equals(name) && value instanceof JsonObject artifact ? artifact(artifact) : value));
        return builder.build();
    }

    private JsonObject artifact(
        JsonObject artifact)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        artifact.forEach((name, value) -> builder.add(name,
            "parts".equals(name) && value instanceof JsonArray parts ? map(parts, this::partFromCanonical) : value));
        return builder.build();
    }

    private JsonObject messageFromCanonical(
        JsonObject message)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder().add("kind", KINDS.get("message"));
        message.forEach((name, value) ->
        {
            switch (name)
            {
            case "role":
                builder.add(name, value instanceof JsonString role
                    ? ROLES_FROM_CANONICAL.getOrDefault(role.getString(), role.getString())
                    : value.toString());
                break;
            case "parts":
                builder.add(name, value instanceof JsonArray parts ? map(parts, this::partFromCanonical) : value);
                break;
            default:
                builder.add(name, value);
                break;
            }
        });
        return builder.build();
    }

    private JsonObject partFromCanonical(
        JsonObject part)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();

        if (part.containsKey("raw") || part.containsKey("url"))
        {
            final JsonObjectBuilder file = Json.createObjectBuilder();
            final JsonObjectBuilder rest = Json.createObjectBuilder();
            part.forEach((name, value) ->
            {
                switch (name)
                {
                case "filename":
                    file.add("name", value);
                    break;
                case "mediaType":
                    file.add("mimeType", value);
                    break;
                case "raw":
                    file.add("bytes", value);
                    break;
                case "url":
                    file.add("uri", value);
                    break;
                default:
                    rest.add(name, value);
                    break;
                }
            });
            builder.add("kind", "file").add("file", file);
            rest.build().forEach(builder::add);
        }
        else
        {
            final String kind = part.containsKey("data") ? "data" : "text";
            builder.add("kind", kind);
            part.forEach(builder::add);
        }

        return builder.build();
    }
}
