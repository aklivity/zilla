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

import io.aklivity.zilla.config.binding.mcp.kafka.McpKafkaConditionConfig;
import io.aklivity.zilla.runtime.common.lang.Matchers;

final class McpKafkaConditionMatcher
{
    private final List<Pattern> tool;
    private final List<Pattern> topics;

    McpKafkaConditionMatcher(
        McpKafkaConditionConfig condition)
    {
        this.tool = Matchers.globAll(condition.tool);
        this.topics = Matchers.globAll(condition.topics);
    }

    boolean matchesTool(
        String tool)
    {
        return Matchers.admits(this.tool, tool);
    }

    boolean matchesTopic(
        String topic)
    {
        return topic == null || Matchers.admits(topics, topic);
    }
}
