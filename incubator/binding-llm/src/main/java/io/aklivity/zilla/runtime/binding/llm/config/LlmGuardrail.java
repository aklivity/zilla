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
package io.aklivity.zilla.runtime.binding.llm.config;

import java.util.ArrayList;
import java.util.List;

public final class LlmGuardrail
{
    public static final LlmGuardrail NONE = new LlmGuardrail(List.of(), List.of());

    public final List<LlmGuardrailStage> request;
    public final List<LlmGuardrailStage> response;

    LlmGuardrail(
        List<LlmGuardrailStage> request,
        List<LlmGuardrailStage> response)
    {
        this.request = request;
        this.response = response;
    }

    public LlmGuardrail then(
        LlmGuardrail next)
    {
        return new LlmGuardrail(concat(request, next.request), concat(response, next.response));
    }

    private static List<LlmGuardrailStage> concat(
        List<LlmGuardrailStage> first,
        List<LlmGuardrailStage> second)
    {
        final List<LlmGuardrailStage> stages = new ArrayList<>(first.size() + second.size());
        stages.addAll(first);
        stages.addAll(second);
        return stages;
    }
}
