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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.util.Collection;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectHandler;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

final class LlmDialectSelector
{
    static LlmDialectHandler select(
        Collection<LlmDialectHandler> candidates,
        JsonEnvelope headers)
    {
        LlmDialectHandler selected = null;
        int highest = 0;
        boolean tied = false;

        for (LlmDialectHandler candidate : candidates)
        {
            final int score = candidate.detect(headers) ? candidate.score(headers) : 0;

            if (score > highest)
            {
                selected = candidate;
                highest = score;
                tied = false;
            }
            else if (score > 0 && score == highest)
            {
                tied = true;
            }
        }

        return tied ? null : selected;
    }

    private LlmDialectSelector()
    {
    }
}
