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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CACHE_CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CACHE_SERVER;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;

import java.util.List;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.config.McpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaCompositeConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public final class McpKafkaClientGenerator
{
    private static final String KAFKA_TYPE_NAME = "kafka";
    private static final String TCP_TYPE_NAME = "tcp";

    private static final String CACHE_CLIENT_NAME = "kafka_cache_client0";
    private static final String CACHE_SERVER_NAME = "kafka_cache_server0";
    private static final String KAFKA_CLIENT_NAME = "kafka_client0";
    private static final String TCP_CLIENT_NAME = "tcp_client0";

    private final String cacheClientExit;

    public McpKafkaClientGenerator(
        String cacheClientExit)
    {
        this.cacheClientExit = cacheClientExit;
    }

    public McpKafkaCompositeConfig generate(
        BindingConfig binding,
        EngineContext context)
    {
        McpKafkaOptionsConfig options = (McpKafkaOptionsConfig) binding.options;

        NamespaceConfig namespace = NamespaceConfig.builder()
            .name("%s/mcp_kafka".formatted(binding.qname))
            .inject(this::injectKafkaCache)
            .inject(n -> injectKafkaClient(n, options))
            .inject(n -> injectTcpClient(n, options))
            .build();

        long exitId = cacheClientExit != null && !cacheClientExit.isBlank()
            ? binding.resolveId.applyAsLong(cacheClientExit)
            : namespace.bindings.stream()
                .filter(b -> CACHE_CLIENT_NAME.equals(b.name))
                .mapToLong(b -> context.supplyBindingId(namespace, b))
                .findFirst()
                .orElseThrow();

        return new McpKafkaCompositeConfig(List.of(namespace), exitId);
    }

    private <C> NamespaceConfigBuilder<C> injectKafkaCache(
        NamespaceConfigBuilder<C> namespace)
    {
        return namespace
            .binding()
                .name(CACHE_CLIENT_NAME)
                .type(KAFKA_TYPE_NAME)
                .kind(CACHE_CLIENT)
                .exit(CACHE_SERVER_NAME)
                .build()
            .binding()
                .name(CACHE_SERVER_NAME)
                .type(KAFKA_TYPE_NAME)
                .kind(CACHE_SERVER)
                .exit(KAFKA_CLIENT_NAME)
                .build();
    }

    private <C> NamespaceConfigBuilder<C> injectKafkaClient(
        NamespaceConfigBuilder<C> namespace,
        McpKafkaOptionsConfig options)
    {
        return namespace
            .binding()
                .name(KAFKA_CLIENT_NAME)
                .type(KAFKA_TYPE_NAME)
                .kind(CLIENT)
                .options(KafkaOptionsConfig::builder)
                    .inject(o -> injectKafkaServers(o, options))
                    .inject(o -> injectKafkaAuthorization(o, options))
                    .build()
                .exit(TCP_CLIENT_NAME)
                .build();
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaServers(
        KafkaOptionsConfigBuilder<C> options,
        McpKafkaOptionsConfig mcpOptions)
    {
        mcpOptions.servers.forEach(s -> options
            .server()
                .host(s.host)
                .port(s.port)
                .build());

        return options;
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaAuthorization(
        KafkaOptionsConfigBuilder<C> options,
        McpKafkaOptionsConfig mcpOptions)
    {
        if (mcpOptions.authorization != null)
        {
            options.authorization(mcpOptions.authorization);
        }

        return options;
    }

    private <C> NamespaceConfigBuilder<C> injectTcpClient(
        NamespaceConfigBuilder<C> namespace,
        McpKafkaOptionsConfig options)
    {
        KafkaServerConfig server = options.servers.get(0);

        return namespace
            .binding()
                .name(TCP_CLIENT_NAME)
                .type(TCP_TYPE_NAME)
                .kind(CLIENT)
                .options(TcpOptionsConfig::builder)
                    .host(server.host)
                    .ports(new int[] { server.port })
                    .build()
                .build();
    }
}
