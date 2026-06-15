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

public final class McpHttpResourceConfig
{
    public final String name;
    public final String uri;
    public final String description;
    public final String mimeType;
    public final ModelConfig output;

    public McpHttpResourceConfig(
        String name,
        String uri,
        String description,
        String mimeType,
        ModelConfig output)
    {
        this.name = name;
        this.uri = uri;
        this.description = description;
        this.mimeType = mimeType;
        this.output = output;
    }
}
