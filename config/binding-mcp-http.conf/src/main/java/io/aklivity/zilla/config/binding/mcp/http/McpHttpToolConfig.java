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
package io.aklivity.zilla.config.binding.mcp.http;

import io.aklivity.zilla.config.engine.ModelConfig;

public final class McpHttpToolConfig
{
    public final String name;
    public final String summary;
    public final String description;
    public final ModelConfig input;
    public final ModelConfig output;
    public final boolean outputMaybeWrapped;

    public McpHttpToolConfig(
        String name,
        String summary,
        String description,
        ModelConfig input,
        ModelConfig output)
    {
        this(name, summary, description, input, output, false);
    }

    // outputMaybeWrapped is true when nothing proves the real response body is already a JSON object --
    // e.g. no output override and no declared object response schema. McpHttpResultWrap is then routed
    // into the response pipeline to decide, from the real body's own first event, whether it actually
    // needs wrapping into {"result": <value>} before validation/projection against output; a declared
    // object schema skips the transform entirely (never needed), a declared array/scalar schema always
    // wraps (never possible to be an object)
    public McpHttpToolConfig(
        String name,
        String summary,
        String description,
        ModelConfig input,
        ModelConfig output,
        boolean outputMaybeWrapped)
    {
        this.name = name;
        this.summary = summary;
        this.description = description;
        this.input = input;
        this.output = output;
        this.outputMaybeWrapped = outputMaybeWrapped;
    }
}
