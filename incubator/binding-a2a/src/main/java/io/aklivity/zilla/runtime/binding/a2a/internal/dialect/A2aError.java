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

public enum A2aError
{
    PARSE_ERROR(-32700, "Parse error", null),
    INVALID_REQUEST(-32600, "Invalid request", null),
    METHOD_NOT_FOUND(-32601, "Method not found", null),
    INVALID_PARAMS(-32602, "Invalid params", "INVALID_PARAMS"),
    INTERNAL_ERROR(-32603, "Internal error", null),
    TASK_NOT_FOUND(-32001, "Task not found", "TASK_NOT_FOUND"),
    TASK_NOT_CANCELABLE(-32002, "Task cannot be canceled", "TASK_NOT_CANCELABLE"),
    PUSH_NOTIFICATION_NOT_SUPPORTED(-32003, "Push notification is not supported", "PUSH_NOTIFICATION_NOT_SUPPORTED"),
    UNSUPPORTED_OPERATION(-32004, "This operation is not supported", "UNSUPPORTED_OPERATION"),
    CONTENT_TYPE_NOT_SUPPORTED(-32005, "Incompatible content types", "CONTENT_TYPE_NOT_SUPPORTED"),
    INVALID_AGENT_RESPONSE(-32006, "Invalid agent response", "INVALID_AGENT_RESPONSE"),
    EXTENDED_AGENT_CARD_NOT_CONFIGURED(-32007, "Extended agent card not configured", "EXTENDED_AGENT_CARD_NOT_CONFIGURED"),
    EXTENSION_SUPPORT_REQUIRED(-32008, "Extension support required", "EXTENSION_SUPPORT_REQUIRED"),
    VERSION_NOT_SUPPORTED(-32009, "Version not supported", "VERSION_NOT_SUPPORTED");

    public static final String DOMAIN = "a2a-protocol.org";

    private final int code;
    private final String message;
    private final String reason;

    A2aError(
        int code,
        String message,
        String reason)
    {
        this.code = code;
        this.message = message;
        this.reason = reason;
    }

    public int code()
    {
        return code;
    }

    public String message()
    {
        return message;
    }

    public String reason()
    {
        return reason;
    }

    public static String reason(
        int code)
    {
        final A2aError error = of(code);
        return error != null ? error.reason : null;
    }

    public static String message(
        int code)
    {
        final A2aError error = of(code);
        return error != null ? error.message : "Error";
    }

    private static A2aError of(
        int code)
    {
        A2aError match = null;
        for (A2aError error : values())
        {
            if (error.code == code)
            {
                match = error;
                break;
            }
        }
        return match;
    }
}
