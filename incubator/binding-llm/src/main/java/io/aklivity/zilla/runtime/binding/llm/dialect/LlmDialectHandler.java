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

import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

/**
 * Handles the streams of one binding for one {@link LlmDialect}.
 */
public interface LlmDialectHandler extends BindingHandler
{
    /**
     * Detects whether an inbound request is of this dialect, from the headers of the stream.
     *
     * @param headers  the headers of the inbound request
     * @return {@code true} if the request is of this dialect, {@code false} by default
     */
    default boolean detect(
        JsonEnvelope headers)
    {
        return false;
    }
}
