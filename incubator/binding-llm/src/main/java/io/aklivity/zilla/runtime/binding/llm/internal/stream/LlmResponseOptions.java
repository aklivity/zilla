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

final class LlmResponseOptions
{
    static final int NONE = -1;

    private final String[] members;
    private final String[] latest;
    private final String[] delivered;
    private final int[] order;

    private int seen;

    LlmResponseOptions(
        String... members)
    {
        this.members = members;
        this.latest = new String[members.length];
        this.delivered = new String[members.length];
        this.order = new int[members.length];
    }

    String member(
        int slot)
    {
        return members[slot];
    }

    String latest(
        int slot)
    {
        return latest[slot];
    }

    void set(
        int slot,
        String literal)
    {
        if (latest[slot] == null)
        {
            order[seen++] = slot;
        }

        latest[slot] = literal;
    }

    int next()
    {
        int next = NONE;

        for (int index = 0; next == NONE && index < seen; index++)
        {
            final int slot = order[index];

            if (!latest[slot].equals(delivered[slot]))
            {
                next = slot;
            }
        }

        return next;
    }

    void deliver(
        int slot)
    {
        delivered[slot] = latest[slot];
    }

    void clear()
    {
        for (int slot = 0; slot < latest.length; slot++)
        {
            latest[slot] = null;
            delivered[slot] = null;
        }

        seen = 0;
    }
}
