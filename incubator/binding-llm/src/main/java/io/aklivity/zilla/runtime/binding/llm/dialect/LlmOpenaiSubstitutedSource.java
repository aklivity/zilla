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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelSource;

/**
 * The {@link ModelSource} an OpenAI dialect transform hands its {@code sink} to answer
 * {@link io.aklivity.zilla.runtime.engine.model.ModelEvent#REPLACED} -- a substitute path (renaming a field
 * to its sibling synonym), a substitute value (remapping a known enumerated value), or both at once.
 * <p>
 * One instance is held per transform and rewrapped once per substituted field via
 * {@link #wrap(String, DirectBufferEx)}; it is never retained beyond the current {@code transform} call.
 * </p>
 */
final class LlmOpenaiSubstitutedSource implements ModelSource
{
    private String path;
    private DirectBufferEx value;

    LlmOpenaiSubstitutedSource wrap(
        String path,
        DirectBufferEx value)
    {
        this.path = path;
        this.value = value;
        return this;
    }

    @Override
    public String getPath()
    {
        return path;
    }

    @Override
    public DirectBufferEx getValue()
    {
        return value;
    }
}
