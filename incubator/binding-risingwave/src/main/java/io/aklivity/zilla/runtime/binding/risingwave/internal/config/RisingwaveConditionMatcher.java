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

import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveConditionConfig;

public final class RisingwaveConditionMatcher
{
    private final DirectBuffer commandBuffer = new UnsafeBuffer(0, 0);
    private final DirectBuffer statementBuffer = new UnsafeBuffer(0, 0);
    private final List<RisingwaveCommandType> commands;

    public RisingwaveConditionMatcher(
        RisingwaveConditionConfig condition)
    {
        this.commands = condition.commands;
    }

    public boolean matches(
        DirectBuffer statement,
        int offset,
        int length)
    {
        return commands.stream().anyMatch(c ->
        {
            final int commandLength = c.value().length;
            boolean matches = length >= commandLength;

            int progressOffset = offset;

            statementBuffer.wrap(statement, progressOffset, commandLength);
            commandBuffer.wrap(c.value());

            matches = matches ? statementBuffer.compareTo(commandBuffer) == 0 : matches;

            return matches;
        });
    }
}
