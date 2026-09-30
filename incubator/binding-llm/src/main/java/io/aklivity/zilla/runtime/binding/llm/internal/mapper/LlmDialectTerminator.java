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
 * Implemented by an encode {@link io.aklivity.zilla.runtime.common.json.JsonSink} whose source dialect signals
 * completion with a literal, non-JSON terminator (e.g. OpenAI's SSE data value {@code [DONE]}) that never
 * reaches the pipeline (see {@link io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect#terminator}) --
 * a caller that recognizes the terminator bypasses the pipeline for that one chunk and calls this directly. A
 * dialect with no such terminator implements this as a no-op.
 */
public interface LlmDialectTerminator
{
    void terminate();
}
