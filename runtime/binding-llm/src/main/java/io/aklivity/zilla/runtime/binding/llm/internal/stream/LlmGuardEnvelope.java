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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

final class LlmGuardEnvelope implements ModelEnvelope
{
    static final String DIRECTION = "direction";
    static final String DIRECTION_REQUEST = "request";
    static final String DIRECTION_RESPONSE = "response";

    private final DirectBufferEx direction;

    LlmGuardEnvelope(
        String direction)
    {
        this.direction = new UnsafeBufferEx(direction.getBytes(UTF_8));
    }

    @Override
    public int count(
        String name)
    {
        return DIRECTION.equals(name) ? 1 : 0;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        return DIRECTION.equals(name) && index == 0 ? direction : null;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
    }
}
