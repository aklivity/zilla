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

final class LlmTextTransform extends LlmToolTransform
{
    private final boolean optional;

    private boolean extracted;

    LlmTextTransform()
    {
        this(false);
    }

    LlmTextTransform(
        boolean optional)
    {
        super(MODE_EXTRACT);
        this.optional = optional;
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
        final boolean text = matches(key, "text");

        extracted |= text;

        return text ? PASS_STRING : DROP;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        return optional && !extracted ? Status.COMPLETED : Status.ADVANCED;
    }
}
