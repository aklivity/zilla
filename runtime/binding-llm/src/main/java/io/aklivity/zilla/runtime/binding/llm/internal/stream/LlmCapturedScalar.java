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

final class LlmCapturedScalar
{
    private JsonEvent event;
    private String text;
    private int size;

    void clear()
    {
        event = null;
        text = null;
        size = 0;
    }

    boolean add(
        JsonEvent event,
        String text,
        int limit)
    {
        size += LlmCapturedValue.cost(text);

        final boolean accepted = size <= limit;

        if (accepted && this.event == null)
        {
            this.event = event;
            this.text = text;
        }

        return accepted;
    }

    JsonEvent event()
    {
        return event;
    }

    String text()
    {
        return text;
    }

    String string()
    {
        return event == JsonEvent.VALUE_STRING ? text : null;
    }
}
