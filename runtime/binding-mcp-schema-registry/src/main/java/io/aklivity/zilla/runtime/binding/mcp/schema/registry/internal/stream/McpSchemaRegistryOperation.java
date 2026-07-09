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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.stream;

public final class McpSchemaRegistryOperation
{
    public final String tool;
    public final String operationId;
    public final String method;
    public final String pathTemplate;
    public final boolean hasRequestBody;
    public final String wrapKey;
    public final String summary;

    McpSchemaRegistryOperation(
        String tool,
        String operationId,
        String method,
        String pathTemplate,
        boolean hasRequestBody,
        String wrapKey,
        String summary)
    {
        this.tool = tool;
        this.operationId = operationId;
        this.method = method;
        this.pathTemplate = pathTemplate;
        this.hasRequestBody = hasRequestBody;
        this.wrapKey = wrapKey;
        this.summary = summary;
    }
}
