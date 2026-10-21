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

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmAnthropicControlFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";

    private String member;

    LlmAnthropicControlFromCanonical(
        boolean sameDialect)
    {
        super(MODE_EXTRACT, sameDialect);
    }

    String member()
    {
        return member;
    }

    @Override
    public void reset()
    {
        super.reset();
        member = null;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, MEMBER_STREAM_OPTIONS))
        {
            action = DROP;
        }
        else
        {
            action = unprefix(DIALECT, key);

            if (depth == 1 && action != DROP)
            {
                member = target;
            }
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
        if (member == null)
        {
            emit(JsonEvent.VALUE_NULL);
        }

        return Status.ADVANCED;
    }
}
