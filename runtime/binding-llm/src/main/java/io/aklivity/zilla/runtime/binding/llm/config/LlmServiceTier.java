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
package io.aklivity.zilla.runtime.binding.llm.config;

import java.util.List;

public final class LlmServiceTier
{
    public static final int INVALID = -1;
    public static final int AUTO = 0;
    public static final int DEFAULT = 1;
    public static final int FLEX = 2;
    public static final int PRIORITY = 3;
    public static final int COUNT = 4;
    public static final int ALL = (1 << COUNT) - 1;

    private static final String[] NAMES = {"auto", "default", "flex", "priority"};

    public static int of(
        CharSequence name)
    {
        int found = INVALID;

        for (int tier = 0; found == INVALID && tier < COUNT; tier++)
        {
            if (NAMES[tier].contentEquals(name))
            {
                found = tier;
            }
        }

        return found;
    }

    public static String nameOf(
        int tier)
    {
        return NAMES[tier];
    }

    public static int maskOf(
        List<String> names)
    {
        int mask = 0;

        for (String name : names)
        {
            final int tier = of(name);

            if (tier != INVALID)
            {
                mask |= 1 << tier;
            }
        }

        return mask;
    }

    private LlmServiceTier()
    {
    }
}
