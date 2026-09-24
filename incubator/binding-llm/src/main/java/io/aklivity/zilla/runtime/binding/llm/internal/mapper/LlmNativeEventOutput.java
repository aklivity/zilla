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
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

/**
 * Receives the dialect-native events a mapper's {@code encode} produces from
 * canonical events, as an SSE event name (nullable, e.g. OpenAI names none) paired
 * with its payload text (a JSON document, except the dialect's own non-JSON
 * terminal marker such as {@code [DONE]}).
 */
public interface LlmNativeEventOutput
{
    void event(
        String name,
        String data);
}
