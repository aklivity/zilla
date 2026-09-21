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
 * Implemented by the {@link io.aklivity.zilla.runtime.common.json.JsonTransform} an
 * {@link LlmDialect#supplyResponseDecodeTransform()} returns, when it needs the native source dialect's
 * out-of-band SSE {@code event:} name to decide its decode behavior (e.g. Anthropic's
 * {@code message_start}/{@code content_block_start}/... dispatch), set once before each native chunk is fed
 * to the pipeline. A dialect whose decode behavior does not depend on the event name (e.g. OpenAI) still
 * implements this, as a no-op, so a caller driving any dialect through the same call site needs no
 * {@code instanceof} check.
 */
public interface LlmDialectEvent
{
    void event(
        String name);
}
