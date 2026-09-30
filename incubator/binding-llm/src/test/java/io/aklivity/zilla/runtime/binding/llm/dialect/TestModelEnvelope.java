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

import java.util.HashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// A minimal single-value-per-name ModelEnvelope test double, shared across this package's dialect tests.
final class TestModelEnvelope implements ModelEnvelope
{
    private final Map<String, DirectBufferEx> valuesByName = new HashMap<>();

    @Override
    public int count(
        String name)
    {
        return valuesByName.containsKey(name) ? 1 : 0;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        return index == 0 ? valuesByName.get(name) : null;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        valuesByName.put(name, value);
    }
}
