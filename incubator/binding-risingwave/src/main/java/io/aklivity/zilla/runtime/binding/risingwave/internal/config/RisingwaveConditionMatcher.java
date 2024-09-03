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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import java.nio.ByteBuffer;
import java.util.List;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveConditionConfig;

public final class RisingwaveConditionMatcher
{
    private final List<byte[]> commands;

    public RisingwaveConditionMatcher(
        RisingwaveConditionConfig condition)
    {
        this.commands = condition.commands;
    }

    public boolean matches(
        ByteBuffer statement)
    {
        return commands.stream().anyMatch(c ->
        {
            boolean matches = statement.remaining() < c.length;

            if (matches)
            {
                int position = statement.position();

                match:
                for (byte b : c)
                {
                    if (statement.get() != b)
                    {
                        statement.position(position);
                        break match;
                    }
                }

                statement.position(position);
            }

            return matches;
        });
    }
}
