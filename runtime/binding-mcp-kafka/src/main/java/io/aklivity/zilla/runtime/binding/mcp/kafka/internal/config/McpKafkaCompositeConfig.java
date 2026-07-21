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

import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public final class McpKafkaCompositeConfig
{
    public final List<NamespaceConfig> namespaces;
    public final long exitId;

    public McpKafkaCompositeConfig(
        List<NamespaceConfig> namespaces,
        long exitId)
    {
        this.namespaces = namespaces;
        this.exitId = exitId;
    }
}
