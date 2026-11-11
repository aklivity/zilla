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

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

final class LlmTextSink implements JsonSink
{
    private final LlmTextPipeline.CharOutput output;
    private final boolean completing;

    LlmTextSink(
        LlmTextPipeline.CharOutput output,
        boolean completing)
    {
        this.output = output;
        this.completing = completing;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        if (event == JsonEvent.VALUE_STRING)
        {
            final boolean deferred = source.deferredBytes();
            final CharSequence chars = source.getStringView();
            final int length = chars.length();

            if (length > 0)
            {
                output.put(chars, 0, length);
            }

            control.consumed(length);

            status = deferred || !completing ? Status.ADVANCED : Status.COMPLETED;
        }
        else if (event == JsonEvent.END_DOCUMENT && !completing)
        {
            status = Status.COMPLETED;
        }

        return status;
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public boolean deterministic()
    {
        return true;
    }
}
