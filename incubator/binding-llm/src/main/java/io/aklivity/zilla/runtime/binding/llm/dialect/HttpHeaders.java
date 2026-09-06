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
 * Read-only access to the headers of an HTTP request, used by {@link LlmDialect#detect(String, HttpHeaders)}
 * to recognize a dialect without binding to any particular wire representation of the headers.
 */
public interface HttpHeaders
{
    /**
     * Returns the value of the named header.
     *
     * @param name  the header name, case-insensitive
     * @return the header value, or {@code null} if absent
     */
    String header(
        String name);
}
