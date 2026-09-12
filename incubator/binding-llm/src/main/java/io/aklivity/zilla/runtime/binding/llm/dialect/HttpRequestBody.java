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
 * Read-only access to the top-level scalar members of an HTTP request body, used by
 * {@link LlmDialect#contentType(LlmDialect.Kind, HttpHeaders, HttpRequestBody)} to resolve behavior that
 * depends on the request payload rather than just its path and headers (e.g. selecting a streaming versus
 * non-streaming content-type from a boolean flag in the request body).
 */
public interface HttpRequestBody
{
    /**
     * Returns the literal text of a top-level scalar member's value (e.g. {@code "true"}, {@code "gpt-4"}),
     * left for the caller to interpret, mirroring {@link HttpHeaders#header(String)} treating header values
     * as opaque strings.
     *
     * @param name  the member name
     * @return the member's value as text, or {@code null} if absent or not a scalar
     */
    String value(
        String name);
}
