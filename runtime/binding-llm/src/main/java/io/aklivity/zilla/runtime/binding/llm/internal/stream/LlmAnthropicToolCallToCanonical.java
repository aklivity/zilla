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

final class LlmAnthropicToolCallToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final int BIT_ARGUMENTS_KEY = 1 << 0;
    private static final int BIT_ARGUMENTS_START = 1 << 1;
    private static final int BIT_ARGUMENTS_END = 1 << 2;
    private static final int BIT_END_OBJECT = 1 << 3;

    private boolean inputted;

    @Override
    public void reset()
    {
        super.reset();
        inputted = false;
    }

    @Override
    protected boolean resumable()
    {
        return true;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "type") || matches(key, "id") || matches(key, "name"))
        {
            action = DROP;
        }
        else if (matches(key, "input"))
        {
            target = "arguments";
            inputted = true;
            action = PASS_OBJECT;
        }
        else if (matches(key, LlmCacheBreakpoint.NATIVE_MEMBER))
        {
            action = capture(key);
        }
        else
        {
            action = prefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        LlmCacheBreakpoint.toCanonical(this, value);

        return Status.ADVANCED;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (!inputted)
        {
            emit(BIT_ARGUMENTS_KEY, JsonEvent.KEY_NAME, "arguments");
            emit(BIT_ARGUMENTS_START, JsonEvent.START_OBJECT);
            emit(BIT_ARGUMENTS_END, JsonEvent.END_OBJECT);
        }

        pass(BIT_END_OBJECT, JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
