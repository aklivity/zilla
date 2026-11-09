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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

public enum AguiA2aRejection
{
    MALFORMED(400, null, "Malformed JSON"),
    TOO_LARGE(400, null, "Request too large"),
    RESERVED_KEY(400, "reserved_key", "Reserved key in forwardedProps"),
    INVALID_RUN(422, "invalid_run", "RunAgentInput does not match the run"),
    NO_USER_MESSAGE(422, "invalid_run", "No user message to run"),
    RETENTION_UNAVAILABLE(503, "retention_unavailable", "Service Unavailable");

    private final int status;
    private final String code;
    private final String message;

    AguiA2aRejection(
        int status,
        String code,
        String message)
    {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public int status()
    {
        return status;
    }

    public String code()
    {
        return code;
    }

    public String message()
    {
        return message;
    }
}
