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

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmAnthropicReasoningToContent extends LlmToolTransform
{
    private boolean extracted;

    LlmAnthropicReasoningToContent()
    {
        super(MODE_EXTRACT);
    }

    @Override
    public void reset()
    {
        super.reset();
        extracted = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (!extracted && (matches(key, "text") || matches(key, "anthropic:signature") || matches(key, "anthropic:data")))
        {
            extracted = true;
            action = PASS_STRING;
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        return Status.ADVANCED;
    }
}
