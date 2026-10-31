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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

import java.util.Objects;

public final class AguiA2aThreadKey
{
    private final String identity;
    private final String agent;
    private final String threadId;

    public AguiA2aThreadKey(
        String identity,
        String agent,
        String threadId)
    {
        this.identity = identity;
        this.agent = agent;
        this.threadId = threadId;
    }

    public long hash()
    {
        return AguiA2aKeyHash.hash(identity, agent, threadId);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        return this == obj ||
            obj instanceof AguiA2aThreadKey that &&
            Objects.equals(identity, that.identity) &&
            Objects.equals(agent, that.agent) &&
            Objects.equals(threadId, that.threadId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(identity, agent, threadId);
    }

    @Override
    public String toString()
    {
        return String.format("%s [agent=%s, threadId=%s]", getClass().getSimpleName(), agent, threadId);
    }
}
