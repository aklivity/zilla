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

final class LlmCitationsEnabled
{
    static final String MEMBER = "citations";
    static final String SHARED_MEMBER = "x:citations";

    private static final String ENABLED = "enabled";

    static boolean enabled(
        LlmCapturedValue value)
    {
        final int index = value.member(0, ENABLED);

        return index != LlmCapturedValue.ABSENT && value.event(index) == JsonEvent.VALUE_TRUE;
    }

    static boolean emit(
        LlmToolTransform transform,
        LlmCapturedValue value,
        String member)
    {
        final boolean enabled = enabled(value);

        if (enabled)
        {
            transform.emit(JsonEvent.KEY_NAME, member);
            transform.emit(JsonEvent.START_OBJECT);
            transform.emit(JsonEvent.KEY_NAME, ENABLED);
            transform.emit(JsonEvent.VALUE_TRUE);
            transform.emit(JsonEvent.END_OBJECT);
        }

        return enabled;
    }

    private LlmCitationsEnabled()
    {
    }
}
