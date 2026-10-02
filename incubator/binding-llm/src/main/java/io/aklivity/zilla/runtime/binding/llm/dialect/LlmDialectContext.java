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

import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;

/**
 * Per-worker state of a {@link LlmDialect}, attaching to each binding it supports.
 */
public interface LlmDialectContext
{
    /**
     * Attaches to a configured binding.
     *
     * @param binding  the configured binding
     * @return the handler for streams of that binding, or {@code null} if this dialect does not support it
     */
    LlmDialectHandler attach(
        LlmBindingConfig binding);

    /**
     * Detaches from a binding.
     *
     * @param bindingId  the binding identifier
     */
    default void detach(
        long bindingId)
    {
    }
}
