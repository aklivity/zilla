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

final class LlmOpenaiControlFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_INCLUDE_USAGE = "include_usage";

    private String member;

    LlmOpenaiControlFromCanonical()
    {
        super(MODE_EXTRACT);
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
            target = MEMBER_STREAM_OPTIONS;
            member = target;
            action = DESCEND_KEPT;
        }
        else if (depth == 2 && matches(key, MEMBER_INCLUDE_USAGE))
        {
            target = MEMBER_INCLUDE_USAGE;
            action = PASS_VALUE;
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
        if (depth == 2)
        {
            pass(JsonEvent.END_OBJECT);
        }
        else if (member == null)
        {
            emit(JsonEvent.VALUE_NULL);
        }

        return Status.ADVANCED;
    }
}
