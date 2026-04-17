/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.codec;

import java.util.Map;
import java.util.Optional;

public record McpServerCapabilities(
    Optional<Map<String, Object>> experimental,
    Optional<Logging> logging,
    Optional<Completions> completions,
    Optional<Prompts> prompts,
    Optional<Resources> resources,
    Optional<Tools> tools)
{
    public record Logging()
    {
    }

    public record Completions()
    {
    }

    public record Prompts(
        boolean listChanged)
    {
    }

    public record Resources(
        boolean subscribe,
        boolean listChanged)
    {
    }

    public record Tools(
        boolean listChanged)
    {
    }

    public record Tasks(
        Optional<List> list,
        Optional<Cancel> cancel,
        Optional<Requests> requests)
    {
        public record List()
        {
        }

        public record Cancel()
        {
        }

        public record Requests(
            Optional<Tools> tools)
        {
            public record Tools(
                Optional<Call> call)
            {
                public record Call()
                {
                }
            }
        }
    }
}
