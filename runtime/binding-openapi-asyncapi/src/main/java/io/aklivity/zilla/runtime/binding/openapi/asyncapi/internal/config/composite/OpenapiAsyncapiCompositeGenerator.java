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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.composite;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.parser.AsyncapiParser;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiView;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiView;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public abstract class OpenapiAsyncapiCompositeGenerator
{
    public final OpenapiAsyncapiCompositeConfig generate(
        OpenapiAsyncapiBindingConfig binding)
    {
        int tagIndex = 1;

        final OpenapiParser openapiParser = new OpenapiParser();
        final List<OpenapiSchemaConfig> openapiSchemas = new ArrayList<>();
        for (OpenapiSpecificationConfig openapiSpec : binding.options.specs.openapi)
        {
            final String label = openapiSpec.label;

            for (OpenapiCatalogConfig catalog : openapiSpec.catalogs)
            {
                final long catalogId = binding.resolveId.applyAsLong(catalog.name);
                final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                final List<OpenapiServerConfig> configs =
                    openapiSpec.servers == null || openapiSpec.servers.isEmpty()
                        ? List.of(OpenapiServerConfig.builder().build())
                        : openapiSpec.servers;
                final OpenapiView openapi = OpenapiView.of(tagIndex++, label, openapiParser.parse(payload), configs);

                openapiSchemas.add(new OpenapiSchemaConfig(label, schemaId, openapi));
            }
        }

        final AsyncapiParser asyncapiParser = new AsyncapiParser();
        final List<AsyncapiSchemaConfig> asyncapiSchemas = new ArrayList<>();
        for (AsyncapiSpecificationConfig asyncapiSpec : binding.options.specs.asyncapi)
        {
            final String label = asyncapiSpec.label;

            for (AsyncapiCatalogConfig catalog : asyncapiSpec.catalogs)
            {
                final long catalogId = binding.resolveId.applyAsLong(catalog.name);
                final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                final List<AsyncapiServerConfig> configs =
                    asyncapiSpec.servers == null || asyncapiSpec.servers.isEmpty()
                        ? List.of(AsyncapiServerConfig.builder().build())
                        : asyncapiSpec.servers;
                final AsyncapiView asyncapi = AsyncapiView.of(tagIndex++, label, asyncapiParser.parse(payload), configs);

                asyncapiSchemas.add(new AsyncapiSchemaConfig(label, schemaId, asyncapi));
            }
        }

        return generate(binding, openapiSchemas, asyncapiSchemas);
    }

    protected abstract OpenapiAsyncapiCompositeConfig generate(
        OpenapiAsyncapiBindingConfig binding,
        List<OpenapiSchemaConfig> openapis,
        List<AsyncapiSchemaConfig> asyncapis);

    @FunctionalInterface
    public interface NamespaceInjector
    {
        <C> NamespaceConfigBuilder<C> inject(
                NamespaceConfigBuilder<C> builder);
    }

    protected abstract class NamespaceHelper
    {
        protected final OpenapiAsyncapiBindingConfig config;
        protected final String name;

        protected NamespaceHelper(
            OpenapiAsyncapiBindingConfig config,
            String name)
        {
            this.config = config;
            this.name = name;
        }

        public final <C> NamespaceConfigBuilder<C> injectAll(
            NamespaceConfigBuilder<C> namespace)
        {
            return namespace
                .inject(this::injectName)
                .inject(this::injectMetrics)
                .inject(this::injectComponents);
        }

        protected abstract <C> NamespaceConfigBuilder<C> injectComponents(
            NamespaceConfigBuilder<C> namespace);

        protected final String resolveIdentity(
            String value)
        {
            if ("{identity}".equals(value))
            {
                value = String.format("${guarded['%s:jwt0'].identity}", config.namespace);
            }

            return value;
        }

        private <C> NamespaceConfigBuilder<C> injectName(
            NamespaceConfigBuilder<C> namespace)
        {
            return namespace.name("%s/%s".formatted(config.qname, name));
        }

        private <C> NamespaceConfigBuilder<C> injectMetrics(
            NamespaceConfigBuilder<C> namespace)
        {
            if (!config.metricRefs.isEmpty())
            {
                namespace
                    .telemetry()
                        .metric()
                            .group("stream")
                            .name("stream.active.received")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.active.sent")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.opens.received")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.opens.sent")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.data.received")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.data.sent")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.errors.received")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.errors.sent")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.closes.received")
                            .build()
                        .metric()
                            .group("stream")
                            .name("stream.closes.sent")
                            .build()
                        .build();
            }

            return namespace;
        }

        protected abstract class BindingsHelper
        {
            protected static final String REGEX_ADDRESS_PARAMETER = "\\{[^}]+\\}";

            protected abstract <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace);

            protected final <C> BindingConfigBuilder<C> injectMetrics(
                BindingConfigBuilder<C> binding)
            {
                if (config.metricRefs.stream()
                        .anyMatch(m -> m.name.startsWith("stream.")))
                {
                    binding.telemetry()
                        .metric()
                            .name("stream.*")
                            .build()
                        .build();
                }

                return binding;
            }

            protected final <C> GuardedConfigBuilder<C> injectGuardedRoles(
                GuardedConfigBuilder<C> guarded,
                List<String> roles)
            {
                for (String role : roles)
                {
                    guarded.role(role);
                }

                return guarded;
            }
        }
    }
}
