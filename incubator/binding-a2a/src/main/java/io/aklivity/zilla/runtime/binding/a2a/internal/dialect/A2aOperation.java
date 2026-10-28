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

public enum A2aOperation
{
    MESSAGE_SEND("SendMessage", "message/send", false, true),
    MESSAGE_STREAM("SendStreamingMessage", "message/stream", true, true),
    TASKS_GET("GetTask", "tasks/get", false, false),
    TASKS_LIST("ListTasks", null, false, true),
    TASKS_CANCEL("CancelTask", "tasks/cancel", false, false),
    TASKS_SUBSCRIBE("SubscribeToTask", "tasks/resubscribe", true, false),
    PUSH_CONFIG_CREATE("CreateTaskPushNotificationConfig", "tasks/pushNotificationConfig/set", false, true),
    PUSH_CONFIG_GET("GetTaskPushNotificationConfig", "tasks/pushNotificationConfig/get", false, false),
    PUSH_CONFIG_LIST("ListTaskPushNotificationConfigs", "tasks/pushNotificationConfig/list", false, false),
    PUSH_CONFIG_DELETE("DeleteTaskPushNotificationConfig", "tasks/pushNotificationConfig/delete", false, false),
    EXTENDED_AGENT_CARD("GetExtendedAgentCard", "agent/getAuthenticatedExtendedCard", false, false);

    private final String method;
    private final String legacyMethod;
    private final boolean streaming;
    private final boolean content;

    A2aOperation(
        String method,
        String legacyMethod,
        boolean streaming,
        boolean content)
    {
        this.method = method;
        this.legacyMethod = legacyMethod;
        this.streaming = streaming;
        this.content = content;
    }

    public String method()
    {
        return method;
    }

    public String legacyMethod()
    {
        return legacyMethod;
    }

    public boolean streaming()
    {
        return streaming;
    }

    public boolean content()
    {
        return content;
    }

    public boolean message()
    {
        return this == MESSAGE_SEND || this == MESSAGE_STREAM;
    }

    public boolean wrapped()
    {
        return this == MESSAGE_SEND || this == MESSAGE_STREAM || this == TASKS_SUBSCRIBE;
    }
}
