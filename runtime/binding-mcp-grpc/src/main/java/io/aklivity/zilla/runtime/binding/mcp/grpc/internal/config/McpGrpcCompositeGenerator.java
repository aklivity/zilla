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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config;

import java.util.List;

import io.aklivity.zilla.config.binding.grpc.GrpcBindingConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcCatalogConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.NamespaceConfig;

public final class McpGrpcCompositeGenerator
{
    private static final String BINDING_NAME = "grpc0";

    public McpGrpcCompositeConfig generate(
        McpGrpcBindingConfig binding)
    {
        final McpGrpcCatalogConfig catalog = binding.options.catalog;

        final NamespaceConfig namespace = NamespaceConfig.builder()
            .name("%s/mcp-grpc".formatted(binding.qname))
            .binding(GrpcBindingConfig::builder)
                .name(BINDING_NAME)
                .kind(KindConfig.CLIENT)
                .catalog()
                    .name(binding.catalogQname)
                    .schema()
                        .subject(catalog.subject)
                        .version(catalog.version != null ? catalog.version : "latest")
                        .build()
                    .build()
                .exit(binding.exit)
                .build()
            .build();

        final BindingConfig grpc = namespace.bindings.stream()
            .filter(b -> BINDING_NAME.equals(b.name))
            .findFirst()
            .orElseThrow();

        return new McpGrpcCompositeConfig(List.of(namespace), binding.supplyBindingId.applyAsLong(namespace, grpc));
    }
}
