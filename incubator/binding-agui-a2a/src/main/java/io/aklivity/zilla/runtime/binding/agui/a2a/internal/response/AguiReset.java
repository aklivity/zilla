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

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aErrorResetExFW;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aResetExFW;

public record AguiReset(
    int status,
    String code,
    String message)
{
    public static final String CODE_UNAUTHORIZED = "agent_unauthorized";
    public static final String CODE_FORBIDDEN = "forbidden";
    public static final String CODE_NOT_FOUND = "agent_not_found";
    public static final String CODE_INVALID_RUN = "invalid_run";

    private static final int STATUS_INTERNAL_ERROR = 500;
    private static final int STATUS_BAD_GATEWAY = 502;
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_UNPROCESSABLE = 422;
    private static final int INTERNAL_ERROR_JSON_RPC = -32603;

    public static AguiReset of(
        A2aResetExFW resetEx)
    {
        AguiReset reset = new AguiReset(STATUS_BAD_GATEWAY, null, null);

        if (resetEx != null)
        {
            switch (resetEx.kind())
            {
            case A2aResetExFW.KIND_BEARER:
                reset = resetEx.bearer().error().get() == A2aBearerError.INSUFFICIENT_SCOPE
                    ? new AguiReset(STATUS_FORBIDDEN, CODE_FORBIDDEN, null)
                    : new AguiReset(STATUS_BAD_GATEWAY, CODE_UNAUTHORIZED, null);
                break;
            case A2aResetExFW.KIND_ERROR:
                reset = of(resetEx.error());
                break;
            default:
                break;
            }
        }

        return reset;
    }

    private static AguiReset of(
        A2aErrorResetExFW error)
    {
        final int code = error.code();
        AguiReset reset;

        if (code == STATUS_NOT_FOUND)
        {
            reset = new AguiReset(STATUS_NOT_FOUND, CODE_NOT_FOUND, null);
        }
        else if (code >= STATUS_INTERNAL_ERROR || code == INTERNAL_ERROR_JSON_RPC)
        {
            reset = new AguiReset(STATUS_BAD_GATEWAY, null, null);
        }
        else
        {
            reset = new AguiReset(STATUS_UNPROCESSABLE, CODE_INVALID_RUN, error.message().asString());
        }

        return reset;
    }
}
