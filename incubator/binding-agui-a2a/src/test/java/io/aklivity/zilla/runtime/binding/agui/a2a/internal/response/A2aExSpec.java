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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aFlag;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

record A2aExSpec(
    int kind,
    String taskId,
    String contextId,
    String state,
    String messageId,
    String artifactId,
    Boolean append,
    Boolean lastChunk)
{
    static final int A2A_TYPE_ID = 3;
    private static final String STATE_PREFIX = "TASK_STATE_";

    A2aDataExFW build()
    {
        final A2aDataExFW.Builder builder = new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(new byte[1024]), 0, 1024)
            .typeId(A2A_TYPE_ID);

        final A2aDataExFW dataEx;

        switch (kind)
        {
        case A2aDataExFW.KIND_TASK:
            dataEx = builder.task(t -> t.taskId(required(taskId)).contextId(contextId)
                .state(s -> s.set(A2aTaskState.valueOf(state != null ? state : "UNSPECIFIED"))).messageId(messageId)).build();
            break;
        case A2aDataExFW.KIND_STATUS_UPDATE:
            dataEx = builder.statusUpdate(t -> t.taskId(required(taskId)).contextId(contextId)
                .state(s -> s.set(A2aTaskState.valueOf(state != null ? state : "UNSPECIFIED"))).messageId(messageId)).build();
            break;
        case A2aDataExFW.KIND_MESSAGE:
            dataEx = builder.message(m -> m.taskId(taskId).contextId(contextId).messageId(messageId)).build();
            break;
        default:
            dataEx = builder.artifactUpdate(a -> a.taskId(required(taskId)).contextId(contextId).artifactId(artifactId)
                .append(f -> f.set(flag(append))).lastChunk(f -> f.set(flag(lastChunk)))).build();
            break;
        }

        return dataEx;
    }

    static A2aExSpec of(
        String json)
    {
        JsonObject parsed;

        try
        {
            parsed = Json.createReader(new StringReader(json)).readObject();
        }
        catch (JsonException ex)
        {
            parsed = Json.createObjectBuilder().add("message", Json.createObjectBuilder()).build();
        }

        final JsonObject root = parsed;
        final String key = root.containsKey("id") ? "task" : root.keySet().iterator().next();
        final JsonObject event = root.containsKey("id") ? root : root.getJsonObject(key);
        final JsonObject status = event.containsKey("status") ? event.getJsonObject("status") : null;
        final JsonObject statusMessage = status != null && status.containsKey("message") ? status.getJsonObject("message") : null;
        final JsonObject artifact = event.containsKey("artifact") ? event.getJsonObject("artifact") : null;

        final A2aExSpec spec;

        switch (key)
        {
        case "task":
            spec = new A2aExSpec(A2aDataExFW.KIND_TASK, string(event, "id"), string(event, "contextId"), state(status),
                string(statusMessage, "messageId"), null, null, null);
            break;
        case "statusUpdate":
            spec = new A2aExSpec(A2aDataExFW.KIND_STATUS_UPDATE, string(event, "taskId"), string(event, "contextId"),
                state(status), string(statusMessage, "messageId"), null, null, null);
            break;
        case "message":
            spec = new A2aExSpec(A2aDataExFW.KIND_MESSAGE, string(event, "taskId"), string(event, "contextId"), null,
                string(event, "messageId"), null, null, null);
            break;
        default:
            spec = new A2aExSpec(A2aDataExFW.KIND_ARTIFACT_UPDATE, string(event, "taskId"), string(event, "contextId"), null,
                null, string(artifact, "artifactId"), event.getBoolean("append", false), event.getBoolean("lastChunk", false));
            break;
        }

        return spec;
    }

    private static String required(
        String value)
    {
        return value != null ? value : "";
    }

    private static A2aFlag flag(
        Boolean value)
    {
        return value == null ? A2aFlag.UNSPECIFIED : value ? A2aFlag.TRUE : A2aFlag.FALSE;
    }

    private static String string(
        JsonObject object,
        String name)
    {
        final JsonValue value = object != null ? object.get(name) : null;

        return value instanceof JsonString string ? string.getString() : null;
    }

    private static String state(
        JsonObject status)
    {
        final String state = string(status, "state");

        return state != null && state.startsWith(STATE_PREFIX) ? state.substring(STATE_PREFIX.length()) : state;
    }
}
