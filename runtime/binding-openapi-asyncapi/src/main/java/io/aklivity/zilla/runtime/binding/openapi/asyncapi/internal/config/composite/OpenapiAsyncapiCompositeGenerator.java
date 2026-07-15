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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config.OpenapiAsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.model.extensions.http.kafka.OpenapiHttpKafkaOperationEx;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;
import io.aklivity.zilla.runtime.common.json.JsonOverlay;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiExtension;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiParserFactory;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public abstract class OpenapiAsyncapiCompositeGenerator
{
    private final Set<String> unresolved = new LinkedHashSet<>();
    protected final List<String> denied = new ArrayList<>();

    public final Collection<String> unresolvedRefs()
    {
        return unresolved;
    }

    public final Collection<String> deniedOperations()
    {
        return denied;
    }

    public final OpenapiAsyncapiCompositeConfig generate(
        OpenapiAsyncapiBindingConfig binding)
    {
        int tagIndex = 1;

        final OpenapiParser openapiParser = new OpenapiParserFactory()
            .withExtension(OpenapiExtension.of(OpenapiExtension.Scope.OPERATION,
                "x-zilla-http-kafka", OpenapiHttpKafkaOperationEx.class))
            .createParser();
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
                final String materialized = materialize(binding, openapiSpec, payload);
                final OpenapiView openapi = OpenapiView.of(tagIndex++, label, openapiParser.parse(materialized));

                unresolved.addAll(openapi.unresolvedRefs());

                openapiSchemas.add(new OpenapiSchemaConfig(label, schemaId, openapi, openapiSpec.security));
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
                final String materialized = materialize(binding, asyncapiSpec, payload);
                final AsyncapiView asyncapi = AsyncapiView.of(tagIndex++, label, asyncapiParser.parse(materialized));

                unresolved.addAll(asyncapi.unresolvedRefs());

                asyncapiSchemas.add(new AsyncapiSchemaConfig(label, schemaId, asyncapi));
            }
        }

        return generate(binding, openapiSchemas, asyncapiSchemas);
    }

    private String materialize(
        OpenapiAsyncapiBindingConfig binding,
        OpenapiSpecificationConfig specification,
        String payload)
    {
        String materialized = payload;
        if (specification.overlay != null)
        {
            final long catalogId = binding.resolveId.applyAsLong(specification.overlay.name);
            final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
            final int schemaId = handler.resolve(specification.overlay.subject, specification.overlay.version);
            final String overlayPayload = handler.resolve(schemaId);

            final JsonObject document = YamlJson.createReader(new StringReader(payload)).readObject();
            final JsonObject overlayDocument = YamlJson.createReader(new StringReader(overlayPayload)).readObject();
            materialized = JsonOverlay.of(overlayDocument).apply(document).toString();
        }

        return materialized;
    }

    private String materialize(
        OpenapiAsyncapiBindingConfig binding,
        AsyncapiSpecificationConfig specification,
        String payload)
    {
        String materialized = payload;
        if (specification.overlay != null)
        {
            final long catalogId = binding.resolveId.applyAsLong(specification.overlay.name);
            final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
            final int schemaId = handler.resolve(specification.overlay.subject, specification.overlay.version);
            final String overlayPayload = handler.resolve(schemaId);

            final JsonObject document = YamlJson.createReader(new StringReader(payload)).readObject();
            final JsonObject overlayDocument = YamlJson.createReader(new StringReader(overlayPayload)).readObject();
            materialized = JsonOverlay.of(overlayDocument).apply(document).toString();
        }

        return materialized;
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
            String value,
            String guardQname)
        {
            if ("{identity}".equals(value) && guardQname != null)
            {
                value = String.format("${guarded['%s'].identity}", guardQname);
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
