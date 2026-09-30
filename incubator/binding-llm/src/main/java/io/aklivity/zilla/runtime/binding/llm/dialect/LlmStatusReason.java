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
package io.aklivity.zilla.runtime.binding.llm.dialect;

/**
 * The standard reason phrase for an HTTP status, e.g. {@code Too Many Requests} for {@code 429}, as a
 * fallback message for an {@link LlmDialect#errorBody(int, String, String)} whose canonical message is
 * unknown.
 */
public final class LlmStatusReason
{
    private LlmStatusReason()
    {
    }

    /**
     * Returns the reason phrase for {@code status}, or {@code Error} for a status with no well-known phrase.
     *
     * @param status  the HTTP status
     * @return the reason phrase
     */
    public static String of(
        int status)
    {
        return switch (status)
        {
        case 400 -> "Bad Request";
        case 401 -> "Unauthorized";
        case 403 -> "Forbidden";
        case 404 -> "Not Found";
        case 408 -> "Request Timeout";
        case 413 -> "Payload Too Large";
        case 415 -> "Unsupported Media Type";
        case 422 -> "Unprocessable Entity";
        case 429 -> "Too Many Requests";
        case 500 -> "Internal Server Error";
        case 502 -> "Bad Gateway";
        case 503 -> "Service Unavailable";
        case 504 -> "Gateway Timeout";
        case 529 -> "Overloaded";
        default -> "Error";
        };
    }
}
