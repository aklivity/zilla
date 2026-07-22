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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaConditionConfig;

final class McpKafkaConditionMatcher
{
    private final String tool;
    private final List<Pattern> topics;

    McpKafkaConditionMatcher(
        McpKafkaConditionConfig condition)
    {
        this.tool = condition.tool;
        this.topics = compile(condition.topics);
    }

    boolean matchesTool(
        String tool)
    {
        return this.tool == null || this.tool.equals(tool);
    }

    boolean matchesTopic(
        String topic)
    {
        return topic == null || admits(topics, topic);
    }

    private static boolean admits(
        List<Pattern> allow,
        String name)
    {
        boolean result = allow == null;

        if (!result)
        {
            for (Pattern pattern : allow)
            {
                if (pattern.matcher(name).matches())
                {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    private static List<Pattern> compile(
        List<String> globs)
    {
        return globs == null
            ? null
            : globs.stream()
                .map(McpKafkaConditionMatcher::compile)
                .collect(Collectors.toList());
    }

    private static Pattern compile(
        String glob)
    {
        final StringBuilder regex = new StringBuilder();
        final String[] literals = glob.split("\\*", -1);
        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }
            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
