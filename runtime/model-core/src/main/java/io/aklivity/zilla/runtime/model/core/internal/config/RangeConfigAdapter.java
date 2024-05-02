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

import io.aklivity.zilla.runtime.model.core.config.RangeConfig;

public final class RangeConfigAdapter
{
    private static final Pattern PATTERN =
        Pattern.compile("((?:\\(|\\[))(-?\\d+(?:\\.\\d+)?)?,(-?\\d+(?:\\.\\d+)?)?((?:\\)|\\]))");

    public RangeConfig adaptFromString(
        String range)
    {
        final Matcher matcher = PATTERN.matcher(range);
        String max = null;
        String min = null;
        boolean exclusiveMax = false;
        boolean exclusiveMin = false;

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
        return new RangeConfig(max, min, exclusiveMax, exclusiveMin);
    }

    public String adaptToString(
        RangeConfig config)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(config.exclusiveMin ? "(" : "[");
        if (config.min != null)
        {
            sb.append(config.min);
        }
        sb.append(",");
        if (config.max != null)
        {
            sb.append(config.max);
        }
        sb.append(config.exclusiveMax ? ")" : "]");
        return sb.toString();
    }
}
