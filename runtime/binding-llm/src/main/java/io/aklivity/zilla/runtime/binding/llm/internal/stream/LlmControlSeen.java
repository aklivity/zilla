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

import java.util.Arrays;

final class LlmControlSeen
{
    private static final int INITIAL_CAPACITY = 16;

    private String[] members;
    private int[] messages;
    private int count;

    boolean carried(
        CharSequence member,
        int message)
    {
        boolean carried = false;

        for (int index = 0; index < count && !carried; index++)
        {
            carried = messages[index] == message && members[index].contentEquals(member);
        }

        if (!carried)
        {
            remember(member, message);
        }

        return carried;
    }

    private void remember(
        CharSequence member,
        int message)
    {
        if (members == null)
        {
            members = new String[INITIAL_CAPACITY];
            messages = new int[INITIAL_CAPACITY];
        }
        else if (count == members.length)
        {
            members = Arrays.copyOf(members, count << 1);
            messages = Arrays.copyOf(messages, count << 1);
        }

        members[count] = member.toString();
        messages[count] = message;
        count++;
    }
}
