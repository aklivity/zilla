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

public final class LlmEffort
{
    public static final int INVALID = -2;
    public static final int ABSENT = -1;
    public static final int NONE = 0;
    public static final int MINIMAL = 1;
    public static final int LOW = 2;
    public static final int MEDIUM = 3;
    public static final int HIGH = 4;
    public static final int XHIGH = 5;
    public static final int MAX = 6;
    public static final int COUNT = 7;

    private static final String[] NAMES = {"none", "minimal", "low", "medium", "high", "xhigh", "max"};

    public static int of(
        CharSequence name)
    {
        int found = INVALID;

        for (int effort = 0; found == INVALID && effort < COUNT; effort++)
        {
            if (NAMES[effort].contentEquals(name))
            {
                found = effort;
            }
        }

        return found;
    }

    public static String nameOf(
        int effort)
    {
        return NAMES[effort];
    }

    public static int clamp(
        int effort,
        int lowest,
        int highest)
    {
        return Math.max(lowest, Math.min(highest, effort));
    }

    private LlmEffort()
    {
    }
}
