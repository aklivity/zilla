/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiServerConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchemaItem;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.kafka.AsyncapiKafkaServerBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaItemView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiView;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfigBuilder;
import io.aklivity.zilla.runtime.catalog.karapace.config.KarapaceOptionsConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.core.config.DoubleModelConfig;
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringPattern;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public abstract class AsyncapiCompositeGenerator
{
    public static final Map<String, ModelConfig> MODELS = Map.of(
        "string", StringModelConfig.builder().build(),
        "string:%s".formatted(StringPattern.DATE.format),
            StringModelConfig.builder()
                .pattern(StringPattern.DATE.pattern)
                .build(),
        "string:%s".formatted(StringPattern.DATE_TIME.format),
            StringModelConfig.builder()
                .pattern(StringPattern.DATE_TIME.pattern)
                .build(),
        "string:%s".formatted(StringPattern.EMAIL.format),
            StringModelConfig.builder()
                .pattern(StringPattern.EMAIL.pattern)
                .build(),
        "integer", Int32ModelConfig.builder().build(),
        "integer:%s".formatted(Int32ModelConfig.INT_32),
            Int32ModelConfig.builder().build(),
        "integer:%s".formatted(Int64ModelConfig.INT_64),
            Int64ModelConfig.builder().build(),
        "number", FloatModelConfig.builder().build(),
        "number:%s".formatted(FloatModelConfig.FLOAT),
            FloatModelConfig.builder().build(),
        "number:%s".formatted(DoubleModelConfig.DOUBLE),
            DoubleModelConfig.builder().build()
    );

    public final AsyncapiCompositeConfig generate(
        AsyncapiBindingConfig binding)
    {
        final AsyncapiParser parser = new AsyncapiParser();
        final List<AsyncapiSchemaConfig> schemas = new ArrayList<>();

        int tagIndex = 1;
        for (AsyncapiSpecificationConfig specification : binding.options.specs)
        {
            final String label = specification.label;

            for (AsyncapiCatalogConfig catalog : specification.catalogs)
            {
                final long catalogId = binding.resolveId.applyAsLong(catalog.name);
                final CatalogHandler handler = binding.supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                final List<AsyncapiServerConfig> configs =
                    specification.servers == null || specification.servers.isEmpty()
                        ? List.of(AsyncapiServerConfig.builder().build())
                        : specification.servers;
                final AsyncapiView asyncapi = AsyncapiView.of(tagIndex++, label, parser.parse(payload), configs);

                schemas.add(new AsyncapiSchemaConfig(label, schemaId, asyncapi));
            }
        }

        return generate(binding, schemas);
    }

    protected abstract AsyncapiCompositeConfig generate(
        AsyncapiBindingConfig binding,
        List<AsyncapiSchemaConfig> schemas);

    @FunctionalInterface
    public interface NamespaceInjector
    {
        <C> NamespaceConfigBuilder<C> inject(
                NamespaceConfigBuilder<C> builder);
    }

    protected abstract class NamespaceHelper
    {
        protected final AsyncapiBindingConfig config;
        protected final AsyncapiSchemaConfig schema;

        protected NamespaceHelper(
            AsyncapiBindingConfig config,
            AsyncapiSchemaConfig schema)
        {
            this.config = config;
            this.schema = schema;
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
            return namespace.name(
                String.format("%s/%s", config.qname,
                    String.join("+", Stream.of(schema) // TODO: multiple
                            .map(s -> s.apiLabel)
                            .toList())));
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

        protected final class CatalogsHelper
        {
            public <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                Optional<AsyncapiServerView> serverRef = Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> s.bindings != null)
                    .filter(s -> s.bindings.kafka != null)
                    .findFirst();

                if (serverRef.isPresent())
                {
                    final AsyncapiServerView server = serverRef.get();

                    injectSchemaRegistry(server, namespace);
                }
                else
                {
                    injectInline(namespace);
                }

                return namespace;
            }

            private <C> void injectSchemaRegistry(
                AsyncapiServerView server,
                NamespaceConfigBuilder<C> namespace)
            {
                final AsyncapiKafkaServerBinding kafka = server.bindings.kafka;

                namespace
                    .catalog()
                        .name("catalog0")
                        .type("karapace")
                        .options(KarapaceOptionsConfig::builder)
                            .url(kafka.schemaRegistryUrl)
                            .context("default")
                            .maxAge(Duration.ofHours(1))
                            .build()
                        .build();
            }

            private <C> void injectInline(
                NamespaceConfigBuilder<C> namespace)
            {
                namespace
                    .catalog()
                        .name("catalog0")
                        .type("inline")
                        .options(InlineOptionsConfig::builder)
                            .inject(this::injectInlineSubjects)
                            .build()
                        .build();
            }

            private <C> InlineOptionsConfigBuilder<C> injectInlineSubjects(
                InlineOptionsConfigBuilder<C> options)
            {
                try (Jsonb jsonb = JsonbBuilder.create())
                {
                    Stream.of(schema)
                        .map(s -> s.asyncapi)
                        .flatMap(v -> v.operations.values().stream())
                        .map(o -> o.channel)
                        .filter(c -> c.messages != null)
                        .flatMap(c -> c.messages.stream())
                        .forEach(m ->
                        {
                            if (m.payload != null)
                            {
                                final String subject = "%s-%s-payload".formatted(m.channel.name, m.name);

                                options.schema()
                                    .subject(subject)
                                    .version("latest")
                                    .schema(toSchemaJson(jsonb, m.payload.model))
                                    .build();
                            }

                            if (m.headers != null && m.headers.properties != null)
                            {
                                for (Map.Entry<String, AsyncapiSchemaView> header : m.headers.properties.entrySet())
                                {
                                    final String name = header.getKey();
                                    final AsyncapiSchemaItemView schema = header.getValue();

                                    final String subject = "%s-%s-header-%s".formatted(m.channel.name, m.name, name);

                                    options.schema()
                                        .subject(subject)
                                        .version("latest")
                                        .schema(toSchemaJson(jsonb, schema.model))
                                        .build();
                                }
                            }
                        });

                    Stream.of(schema)
                        .map(s -> s.asyncapi)
                        .flatMap(v -> v.operations.values().stream())
                        .map(o -> o.channel)
                        .filter(c -> c.parameters != null)
                        .flatMap(c -> c.parameters.stream())
                        .filter(p -> p.schema != null) // TODO: runtime expressions
                        .forEach(p ->
                        {
                            final String subject = "%s-params-%s".formatted(p.channel.name, p.name);

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

            private static String toSchemaJson(
                Jsonb jsonb,
                AsyncapiSchemaItem schema)
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
            private static final Pattern MODEL_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?(json|avro|protobuf)$");

            protected static final String REGEX_ADDRESS_PARAMETER = "\\{[^}]+\\}";

            private final Matcher modelContentType = MODEL_CONTENT_TYPE.matcher("");

            protected abstract <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace);

            protected final void injectPayloadModel(
                Consumer<ModelConfig> injector,
                AsyncapiMessageView message)
            {
                ModelConfig model = null;

                if (message.payload instanceof AsyncapiSchemaView schema &&
                    schema.type != null)
                {
                    String modelType = schema.format != null
                        ? String.format("%s:%s", schema.type, schema.format)
                        : schema.type;

                    model = MODELS.get(modelType);
                }

                if (model == null &&
                    message.contentType != null &&
                    modelContentType.reset(message.contentType).matches())
                {
                    final String subject = "%s-%s-payload".formatted(message.channel.name, message.name);

                    switch (modelContentType.group(1))
                    {
                    case "json":
                        model = JsonModelConfig.builder()
                            .catalog()
                                .name("catalog0")
                                .schema()
                                    .version("latest")
                                    .subject(subject)
                                    .build()
                                .build()
                            .build();
                        break;
                    case "avro":
                        model = AvroModelConfig.builder()
                            .view("json")
                            .catalog()
                                .name("catalog0")
                                .schema()
                                    .version("latest")
                                    .subject(subject)
                                    .build()
                                .build()
                            .build();
                        break;
                    case "protobuf":
                        model = ProtobufModelConfig.builder()
                            .view("json")
                            .catalog()
                                .name("catalog0")
                                .schema()
                                    .version("latest")
                                    .subject(subject)
                                    .build()
                                .build()
                            .build();
                        break;
                    }
                }

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
        }
    }
}
