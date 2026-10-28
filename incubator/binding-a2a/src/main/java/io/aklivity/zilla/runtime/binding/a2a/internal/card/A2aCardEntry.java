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
package io.aklivity.zilla.runtime.binding.a2a.internal.card;

import java.util.ArrayDeque;
import java.util.Deque;

public final class A2aCardEntry
{
    public final String agent;
    public final Deque<A2aCardWaiter> waiters = new ArrayDeque<>();

    public A2aAgentCard card;
    public A2aAgentInterface selected;
    public long expiresAt;
    public A2aCardConsumer fetch;

    public A2aCardEntry(
        String agent)
    {
        this.agent = agent;
    }

    public boolean fresh(
        long now)
    {
        return card != null && now < expiresAt;
    }

    public void resolve(
        A2aAgentCard card,
        A2aAgentInterface selected,
        long expiresAt)
    {
        this.card = card;
        this.selected = selected;
        this.expiresAt = expiresAt;
    }
}
