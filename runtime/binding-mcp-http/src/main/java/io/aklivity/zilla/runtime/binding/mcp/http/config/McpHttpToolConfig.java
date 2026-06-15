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
package io.aklivity.zilla.runtime.binding.mcp.http.config;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class McpHttpToolConfig
{
    public final String name;
    public final String description;
    public final String summary;
    public final ModelConfig input;
    public final ModelConfig output;

    public McpHttpToolConfig(
        String name,
        String description,
        String summary,
        ModelConfig input,
        ModelConfig output)
    {
        this.name = name;
        this.description = description;
        this.summary = summary;
        this.input = input;
        this.output = output;
    }
}
