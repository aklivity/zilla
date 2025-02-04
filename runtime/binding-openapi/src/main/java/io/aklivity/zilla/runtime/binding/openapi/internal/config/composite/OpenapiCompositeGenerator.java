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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiHeaderView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiMediaTypeView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiRequestBodyView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiResponseView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiView;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfigBuilder;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.DoubleModelConfig;
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.StringPattern;
import io.aklivity.zilla.runtime.model.core.internal.StringModel;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public abstract class OpenapiCompositeGenerator
{
    public final OpenapiCompositeConfig generate(
        OpenapiBindingConfig binding)
    {
        final OpenapiParser parser = new OpenapiParser();
        final List<OpenapiSchemaConfig> schemas = new ArrayList<>();

        int tagIndex = 1;
        for (OpenapiSpecificationConfig specification : binding.options.specs)
        {
            final String label = specification.label;

            for (OpenapiCatalogConfig catalog : specification.catalogs)
            {
                final long catalogId = binding.resolveId.applyAsLong(catalog.name);
                final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                final List<OpenapiServerConfig> configs =
                    specification.servers == null || specification.servers.isEmpty()
                        ? List.of(OpenapiServerConfig.builder().build())
                        : specification.servers;
                final OpenapiView asyncapi = OpenapiView.of(tagIndex++, label, parser.parse(payload), configs);

                schemas.add(new OpenapiSchemaConfig(label, schemaId, asyncapi));
            }
        }

        return generate(binding, schemas);
    }

    protected abstract OpenapiCompositeConfig generate(
        OpenapiBindingConfig binding,
        List<OpenapiSchemaConfig> schemas);

    @FunctionalInterface
    public interface NamespaceInjector
    {
        <C> NamespaceConfigBuilder<C> inject(
                NamespaceConfigBuilder<C> builder);
    }

    protected abstract class NamespaceHelper
    {
        protected final OpenapiBindingConfig config;
        protected final String name;

        protected NamespaceHelper(
            OpenapiBindingConfig config,
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

        protected final String resolveIdentity(
            String value)
        {
            if ("{identity}".equals(value))
            {
                value = String.format("${guarded['%s:jwt0'].identity}", config.namespace);
            }

            return value;
        }

        protected abstract class CatalogsHelper
        {
            protected final OpenapiSchemaConfig schema;

            protected CatalogsHelper(
                    OpenapiSchemaConfig schema)
            {
                this.schema = schema;
            }

            public abstract <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace);

            protected final <C> void injectInlineRequests(
                NamespaceConfigBuilder<C> namespace)
            {
                namespace
                    .catalog()
                        .name("catalog0")
                        .type("inline")
                        .options(InlineOptionsConfig::builder)
                            .inject(this::injectInlineRequests)
                            .build()
                        .build();
            }

            protected final <C> void injectInlineResponses(
                NamespaceConfigBuilder<C> namespace)
            {
                namespace
                    .catalog()
                        .name("catalog0")
                        .type("inline")
                        .options(InlineOptionsConfig::builder)
                            .inject(this::injectInlineResponses)
                            .build()
                        .build();
            }

            private <C> InlineOptionsConfigBuilder<C> injectInlineRequests(
                InlineOptionsConfigBuilder<C> options)
            {
                try (Jsonb jsonb = JsonbBuilder.create())
                {
                    Stream.of(schema)
                        .map(s -> s.openapi)
                        .flatMap(v -> v.operations.values().stream())
                        .map(o -> o.requestBody)
                        .filter(Objects::nonNull)
                        .forEach(m -> injectInlineRequest(jsonb, options, m));

                    Stream.of(schema)
                        .map(s -> s.openapi)
                        .flatMap(v -> v.operations.values().stream())
                        .filter(o -> o.parameters != null)
                        .flatMap(c -> c.parameters.stream())
                        .filter(p -> p.schema != null) // TODO: runtime expressions
                        .forEach(p ->
                        {
                            final String subject = "%s-params-%s".formatted(p.operation.id, p.name);

                            options.schema()
                                .subject(subject)
                                .version("latest")
                                .schema(toSchemaJson(jsonb, p.schema.model))
                                .build();
                        });
                }
                catch (Exception ex)
                {
                    rethrowUnchecked(ex);
                }

                return options;
            }

            private <C> void injectInlineRequest(
                Jsonb jsonb,
                InlineOptionsConfigBuilder<C> options,
                OpenapiRequestBodyView request)
            {
                if (request.content != null)
                {
                    for (OpenapiMediaTypeView typed : request.content.values())
                    {
                        options.schema()
                            .subject("%s-%s-value".formatted(request.operation.id, typed.name))
                            .version("latest")
                            .schema(toSchemaJson(jsonb, typed.schema.model))
                            .build();

                        Stream.of(typed)
                            .filter(t -> t.encoding != null)
                            .map(t -> t.encoding)
                            .filter(e -> e.headers != null)
                            .forEach(encoding ->
                            {
                                for (OpenapiHeaderView header : encoding.headers.values())
                                {
                                    final String name = header.name;
                                    final OpenapiSchemaView schema = header.schema;

                                    final String subject = "%s-header-%s-%s"
                                            .formatted(request.operation.id, name, encoding.contentType);

                                    options.schema()
                                        .subject(subject)
                                        .version("latest")
                                        .schema(toSchemaJson(jsonb, schema.model))
                                        .build();
                                }
                            });
                    }
                }
            }

            private <C> InlineOptionsConfigBuilder<C> injectInlineResponses(
                InlineOptionsConfigBuilder<C> options)
            {
                try (Jsonb jsonb = JsonbBuilder.create())
                {
                    Stream.of(schema)
                        .map(s -> s.openapi)
                        .flatMap(v -> v.operations.values().stream())
                        .filter(OpenapiOperationView::hasResponses)
                        .flatMap(o -> o.responses.values().stream())
                        .forEach(o -> injectInlineResponse(jsonb, options, o));
                }
                catch (Exception ex)
                {
                    rethrowUnchecked(ex);
                }

                return options;
            }

            private <C> void injectInlineResponse(
                Jsonb jsonb,
                InlineOptionsConfigBuilder<C> options,
                OpenapiResponseView response)
            {
                if (response.headers != null)
                {
                    for (OpenapiHeaderView header : response.headers.values())
                    {
                        final String name = header.name;
                        final OpenapiSchemaView schema = header.schema;

                        final String subject = "%s-header-%s".formatted(response.operation.id, name);

                        options.schema()
                            .subject(subject)
                            .version("latest")
                            .schema(toSchemaJson(jsonb, schema.model))
                            .build();
                    }
                }

                if (response.content != null)
                {
                    for (OpenapiMediaTypeView typed : response.content.values())
                    {
                        options.schema()
                            .subject("%s-%s-value".formatted(response.operation.id, typed.name))
                            .version("latest")
                            .schema(toSchemaJson(jsonb, typed.schema.model))
                            .build();
                    }
                }
            }

            protected final String toSchemaJson(
                Jsonb jsonb,
                OpenapiSchemaView.OpenapiJsonSchema schema)
            {
                String schemaJson = jsonb.toJson(schema);

                JsonReader reader = Json.createReader(new StringReader(schemaJson));
                JsonValue jsonValue = reader.readValue();

                if (jsonValue instanceof JsonObject)
                {
                    JsonObject jsonObject = (JsonObject) jsonValue;

                    if (jsonObject.containsKey("schema"))
                    {
                        JsonValue modifiedJsonValue = jsonObject.get("schema");
                        StringWriter stringWriter = new StringWriter();
                        JsonWriter jsonWriter = Json.createWriter(stringWriter);
                        jsonWriter.write(modifiedJsonValue);
                        jsonWriter.close();

                        schemaJson = stringWriter.toString();
                    }
                }

                return schemaJson;
            }
        }

        protected abstract class BindingsHelper
        {
            protected static final String REGEX_ADDRESS_PARAMETER = "\\{[^}]+\\}";

            protected abstract <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace);

            protected final void injectPayloadModel(
                Consumer<ModelConfig> injector,
                OpenapiRequestBodyView request)
            {
                String subject = "%s-value".formatted(request.operation.id);
                injectPayloadModel(injector, request, subject);
            }

            protected final void injectPayloadModel(
                Consumer<ModelConfig> injector,
                OpenapiRequestBodyView message,
                String subject)
            {
                ModelConfig model = JsonModelConfig.builder()
                    .catalog()
                        .name("catalog0")
                        .schema()
                            .subject(subject)
                            .version("latest")
                            .build()
                        .build()
                    .build();

                injector.accept(model);
            }

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

            protected final ModelConfig resolveModelBySchema(
                String type,
                String format)
            {
                return StringModel.NAME.equals(type)
                    ? StringModelConfig.builder()
                        .inject(s -> injectStringPattern(s, format))
                        .build()
                    : MODELS.get(format != null ? String.format("%s:%s", type, format) : type);
            }

            private <C> StringModelConfigBuilder<C> injectStringPattern(
                StringModelConfigBuilder<C> model,
                String format)
            {
                if (format != null)
                {
                    model.pattern(StringPattern.of(format));
                }

                return model;
            }

            private static final Map<String, ModelConfig> MODELS = Map.of(
                "integer", Int32ModelConfig.builder().build(),
                "integer:int32", Int32ModelConfig.builder().build(),
                "integer:int64", Int64ModelConfig.builder().build(),
                "number", FloatModelConfig.builder().build(),
                "number:float", FloatModelConfig.builder().build(),
                "number:double", DoubleModelConfig.builder().build()
            );
        }
    }
}
