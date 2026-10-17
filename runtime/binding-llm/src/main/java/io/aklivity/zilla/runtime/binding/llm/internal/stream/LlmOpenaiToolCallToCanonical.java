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

final class LlmOpenaiToolCallToCanonical extends LlmToolTransform
{
    private boolean functioned;
    private boolean argumented;

    LlmOpenaiToolCallToCanonical()
    {
        super(MODE_EXTRACT);
    }

    @Override
    public void reset()
    {
        super.reset();
        functioned = false;
        argumented = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (depth == 1 && matches(key, "function"))
        {
            functioned = true;
            action = DESCEND;
        }
        else if (depth == 2 && matches(key, "arguments"))
        {
            argumented = true;
            action = UNESCAPE_OBJECT;
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
        Status status = Status.ADVANCED;

        if (depth == 2)
        {
            if (!argumented)
            {
                emit(JsonEvent.START_OBJECT);
                emit(JsonEvent.END_OBJECT);
            }
        }
        else if (!functioned)
        {
            status = Status.REJECTED;
        }

        return status;
    }
}
