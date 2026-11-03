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

final class LlmOpenaiResponsesReasoningFromCanonical extends LlmToolTransform
{

    LlmOpenaiResponsesReasoningFromCanonical(
        String dialect)
    {
        super(MODE_OBJECT, dialect);
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        return hasPrefix(key) ? unprefix(key) : DROP;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }

    private static boolean hasPrefix(
        CharSequence key)
    {
        boolean prefixed = false;

        for (int index = 0; index < key.length() && !prefixed; index++)
        {
            prefixed = key.charAt(index) == ':';
        }

        return prefixed;
    }
}
