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
 * Creates a fresh {@link LlmEventMapper} for a dialect name, or {@code null} when no event
 * mapper is registered for that dialect -- a caller bridging between two dialects neither of
 * which has one falls back to field-rename-only translation instead.
 */
public final class LlmEventMapperFactory
{
    private static final String OPENAI = "openai";
    private static final String ANTHROPIC = "anthropic";

    private LlmEventMapperFactory()
    {
    }

    public static LlmEventMapper supply(
        String dialectName)
    {
        final LlmEventMapper mapper;
        if (OPENAI.equals(dialectName))
        {
            mapper = new LlmOpenaiEventMapper();
        }
        else if (ANTHROPIC.equals(dialectName))
        {
            mapper = new LlmAnthropicEventMapper();
        }
        else
        {
            mapper = null;
        }
        return mapper;
    }
}
