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

import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * A dialect of the LLM protocol, created once per engine, that supplies a {@link LlmDialectContext} for each
 * engine worker.
 */
public interface LlmDialect
{
    /**
     * Returns the dialect name, matching the {@code dialect} binding option and the dialect carried on the stream.
     *
     * @return the dialect name
     */
    String name();

    /**
     * Supplies the per-worker context.
     *
     * @param context  the engine context of the worker
     * @return the dialect context for that worker
     */
    LlmDialectContext supply(
        EngineContext context);
}
