/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RangeConfigAdapter
{
    private static final Pattern PATTERN =
        Pattern.compile("((?:\\(|\\[))(-?\\d+(?:\\.\\d+)?)?,(-?\\d+(?:\\.\\d+)?)?((?:\\)|\\]))");

    public String max;
    public String min;
    public boolean exclusiveMax;
    public boolean exclusiveMin;

    public RangeConfigAdapter(
        String max,
        String min,
        boolean exclusiveMax,
        boolean exclusiveMin)
    {
        this.max = max;
        this.min = min;
        this.exclusiveMax = exclusiveMax;
        this.exclusiveMin = exclusiveMin;
    }

    public RangeConfigAdapter(
        String range)
    {
        final Matcher matcher = PATTERN.matcher(range);
        if (matcher.matches())
        {
            if (matcher.group(1).equals("("))
            {
                exclusiveMin = true;
            }
            if (matcher.group(2) != null)
            {
                min = matcher.group(2);
            }
            if (matcher.group(3) != null)
            {
                max = matcher.group(3);
            }
            if (matcher.group(4).equals(")"))
            {
                exclusiveMax = true;
            }
        }
        else
        {
            throw new IllegalArgumentException("Unexpected format: " + range);
        }
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(exclusiveMin ? "(" : "[");
        if (min != null)
        {
            sb.append(min);
        }
        sb.append(",");
        if (max != null)
        {
            sb.append(max);
        }
        sb.append(exclusiveMax ? ")" : "]");
        return sb.toString();
    }
}
