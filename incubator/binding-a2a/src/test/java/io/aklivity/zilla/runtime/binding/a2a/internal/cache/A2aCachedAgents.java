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
package io.aklivity.zilla.runtime.binding.a2a.internal.cache;

import java.io.StringReader;

import jakarta.json.Json;

public final class A2aCachedAgents
{
    public static A2aCachedAgent of(
        String agent,
        String card)
    {
        return new A2aCachedAgent(agent, Json.createReader(new StringReader(card)).readObject());
    }

    private A2aCachedAgents()
    {
    }
}
