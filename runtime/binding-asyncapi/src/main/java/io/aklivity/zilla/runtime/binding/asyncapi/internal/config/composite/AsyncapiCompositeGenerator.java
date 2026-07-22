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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite;

import static java.util.Map.entry;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
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

import io.aklivity.zilla.config.binding.mqtt.MqttCredentialsConfigBuilder;
import io.aklivity.zilla.config.binding.mqtt.MqttOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.mqtt.MqttPatternConfig.MqttConnectProperty;
import io.aklivity.zilla.config.catalog.apicurio.ApicurioOptionsConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfigBuilder;
import io.aklivity.zilla.config.catalog.karapace.KarapaceOptionsConfig;
import io.aklivity.zilla.config.catalog.schema.registry.SchemaRegistryOptionsConfig;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.GuardedConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.model.core.BooleanModelConfig;
import io.aklivity.zilla.config.model.core.DoubleModelConfig;
import io.aklivity.zilla.config.model.core.FloatModelConfig;
import io.aklivity.zilla.config.model.core.Int32ModelConfig;
import io.aklivity.zilla.config.model.core.Int64ModelConfig;
import io.aklivity.zilla.config.model.core.StringModelConfig;
import io.aklivity.zilla.config.model.core.StringPattern;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.AsyncapiHttpOperationBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.http.kafka.AsyncapiHttpKafkaOperationBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.kafka.AsyncapiKafkaMessageBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.kafka.AsyncapiKafkaServerBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.sse.AsyncapiSseOperationBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.sse.kafka.AsyncapiSseKafkaOperationBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.extensions.mqtt.kafka.AsyncapiMqttKafkaChannelEx;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiExtension;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiParserFactory;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiSchemaItem;
import io.aklivity.zilla.runtime.common.asyncapi.security.AsyncapiGuardResolver;
import io.aklivity.zilla.runtime.common.asyncapi.security.GuardedResolution;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSchemaItemView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSecuritySchemeView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;
import io.aklivity.zilla.runtime.common.json.JsonOverlay;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public abstract class AsyncapiCompositeGenerator
{
    public static final Map<String, ModelConfig> MODELS = Map.ofEntries(
        entry("boolean", BooleanModelConfig.builder().build()),
        entry("integer", Int32ModelConfig.builder().build()),
        entry("integer:%s".formatted(Int32ModelConfig.INT_32),
            Int32ModelConfig.builder().build()),
        entry("integer:%s".formatted(Int64ModelConfig.INT_64),
            Int64ModelConfig.builder().build()),
        entry("number", FloatModelConfig.builder().build()),
        entry("number:%s".formatted(FloatModelConfig.FLOAT),
            FloatModelConfig.builder().build()),
        entry("number:%s".formatted(DoubleModelConfig.DOUBLE),
            DoubleModelConfig.builder().build()),
        entry("string", StringModelConfig.builder().build()),
        entry("string:%s".formatted(StringPattern.DATE.format),
            StringModelConfig.builder()
                .pattern(StringPattern.DATE.pattern)
                .build()),
        entry("string:%s".formatted(StringPattern.DATE_TIME.format),
            StringModelConfig.builder()
                .pattern(StringPattern.DATE_TIME.pattern)
                .build()),
        entry("string:%s".formatted(StringPattern.EMAIL.format),
            StringModelConfig.builder()
                .pattern(StringPattern.EMAIL.pattern)
                .build())
    );

    private static final Map<String, String> KAFKA_SASL_MECHANISMS = Map.of(
        "plain", "plain",
        "scramSha256", "scram-sha-256",
        "scramSha512", "scram-sha-512");

    private final Set<String> unresolved = new LinkedHashSet<>();
    protected final List<String> denied = new ArrayList<>();

    public final AsyncapiCompositeConfig generate(
        AsyncapiBindingConfig binding)
    {
        final AsyncapiParser parser = new AsyncapiParserFactory()
            .withOperationBinding("http", AsyncapiHttpOperationBindingEx.class)
            .withOperationBinding("x-zilla-http-kafka", AsyncapiHttpKafkaOperationBindingEx.class)
            .withOperationBinding("x-zilla-sse", AsyncapiSseOperationBindingEx.class)
            .withOperationBinding("x-zilla-sse-kafka", AsyncapiSseKafkaOperationBindingEx.class)
            .withMessageBinding("kafka", AsyncapiKafkaMessageBindingEx.class)
            .withServerBinding("kafka", AsyncapiKafkaServerBindingEx.class)
            .withExtension(AsyncapiExtension.of(
                AsyncapiExtension.Scope.CHANNEL, "x-zilla-mqtt-kafka", AsyncapiMqttKafkaChannelEx.class))
            .createParser();
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
                final String materialized = materialize(binding, specification, payload);
                final AsyncapiView asyncapi = AsyncapiView.of(tagIndex++, label, parser.parse(materialized));

                unresolved.addAll(asyncapi.unresolvedRefs());
                validateSecurity(label, specification.security, asyncapi);

                schemas.add(new AsyncapiSchemaConfig(label, schemaId, asyncapi, specification.security, specification.store));
            }
        }

        return generate(binding, schemas);
    }

    private void validateSecurity(
        String label,
        Map<String, String> security,
        AsyncapiView asyncapi)
    {
        final Map<String, AsyncapiSecuritySchemeView> schemes = asyncapi.components != null
            ? asyncapi.components.securitySchemes
            : null;

        if (security != null)
        {
            for (String name : security.keySet())
            {
                final AsyncapiSecuritySchemeView scheme = schemes != null ? schemes.get(name) : null;
                if (scheme == null)
                {
                    denied.add("security scheme \"%s\" in spec \"%s\" is not defined in components.securitySchemes"
                        .formatted(name, label));
                }
                else if (!isSupportedSecurityScheme(scheme))
                {
                    denied.add("security scheme \"%s\" in spec \"%s\" has unsupported type \"%s\" for guard-based authorization"
                        .formatted(name, label, scheme.type));
                }
            }
        }
    }

    private static boolean isSupportedSecurityScheme(
        AsyncapiSecuritySchemeView scheme)
    {
        return isHttpCredentialScheme(scheme) || isMqttCredentialScheme(scheme) || isKafkaSaslScheme(scheme);
    }

    private static boolean isHttpCredentialScheme(
        AsyncapiSecuritySchemeView scheme)
    {
        return "http".equals(scheme.type) && "bearer".equals(scheme.scheme) ||
            "httpApiKey".equals(scheme.type) && scheme.parameterName != null && isHttpCredentialLocation(scheme.in);
    }

    private static boolean isHttpCredentialLocation(
        String in)
    {
        return "header".equals(in) || "query".equals(in) || "cookie".equals(in);
    }

    private static boolean isMqttCredentialScheme(
        AsyncapiSecuritySchemeView scheme)
    {
        return "apiKey".equals(scheme.type) && ("user".equals(scheme.in) || "password".equals(scheme.in));
    }

    private static boolean isKafkaSaslScheme(
        AsyncapiSecuritySchemeView scheme)
    {
        return KAFKA_SASL_MECHANISMS.containsKey(scheme.type);
    }

    private String materialize(
        AsyncapiBindingConfig binding,
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

    public final Collection<String> unresolvedRefs()
    {
        return unresolved;
    }

    public final Collection<String> deniedOperations()
    {
        return denied;
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
        protected final String name;

        protected NamespaceHelper(
            AsyncapiBindingConfig config,
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
            String value,
            String guardQname)
        {
            if ("{identity}".equals(value) && guardQname != null)
            {
                value = String.format("${guarded['%s'].identity}", guardQname);
            }

            return value;
        }

        protected class CatalogsHelper
        {
            protected final AsyncapiSchemaConfig schema;

            protected CatalogsHelper(
                AsyncapiSchemaConfig schema)
            {
                this.schema = schema;
            }

            public <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                Optional<AsyncapiServerView> serverRef = Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .filter(s -> s.binding("kafka", AsyncapiKafkaServerBindingEx.class)
                        .map(b -> b.schemaRegistryUrl).isPresent())
                    .findFirst();

                if (serverRef.isPresent())
                {
                    final AsyncapiServerView server = serverRef.get();

                    final String vendor = server.binding("kafka", AsyncapiKafkaServerBindingEx.class)
                            .map(b -> b.schemaRegistryVendor)
                            .orElse("schema-registry");

                    switch (vendor)
                    {
                    case "apicurio":
                        injectApicurioRegistry(server, namespace);
                        break;
                    case "karapace":
                        injectKarapaceSchemaRegistry(server, namespace);
                        break;
                    case "schema-registry":
                    default:
                        injectSchemaRegistry(server, namespace);
                        break;
                    }
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
                namespace
                    .catalog()
                        .name("catalog0")
                        .type("schema-registry")
                        .options(SchemaRegistryOptionsConfig::builder)
                            .url(server.binding("kafka", AsyncapiKafkaServerBindingEx.class).get().schemaRegistryUrl)
                            .context("default")
                            .maxAge(Duration.ofHours(1))
                            .build()
                        .build();
            }

            private <C> void injectApicurioRegistry(
                AsyncapiServerView server,
                NamespaceConfigBuilder<C> namespace)
            {
                namespace
                    .catalog()
                        .name("catalog0")
                        .type("apicurio-registry")
                        .options(ApicurioOptionsConfig::builder)
                            .url(server.binding("kafka", AsyncapiKafkaServerBindingEx.class).get().schemaRegistryUrl)
                            .groupId("default")
                            .maxAge(Duration.ofHours(1))
                            .build()
                        .build();
            }

            private <C> void injectKarapaceSchemaRegistry(
                AsyncapiServerView server,
                NamespaceConfigBuilder<C> namespace)
            {
                namespace
                    .catalog()
                        .name("catalog0")
                        .type("karapace-schema-registry")
                        .options(KarapaceOptionsConfig::builder)
                            .url(server.binding("kafka", AsyncapiKafkaServerBindingEx.class).get().schemaRegistryUrl)
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
                        .filter(o -> o.messages != null)
                        .flatMap(o -> o.messages.stream())
                        .forEach(m -> injectInlineSubject(jsonb, options, m));

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

            protected <C> void injectInlineSubject(
                Jsonb jsonb,
                InlineOptionsConfigBuilder<C> options,
                AsyncapiMessageView message)
            {
                if (message.payload != null)
                {
                    options.schema()
                        .subject("%s-%s-value".formatted(message.channel.name, message.name))
                        .version("latest")
                        .schema(toSchemaJson(jsonb, message.payload.model))
                        .build();
                }

                if (message.headers != null && message.headers.properties != null)
                {
                    for (Map.Entry<String, AsyncapiSchemaView> header : message.headers.properties.entrySet())
                    {
                        final String name = header.getKey();
                        final AsyncapiSchemaItemView schema = header.getValue();

                        final String subject = "%s-header-%s".formatted(message.channel.address, name);

                        options.schema()
                            .subject(subject)
                            .version("latest")
                            .schema(toSchemaJson(jsonb, schema.model))
                            .build();
                    }
                }
            }

            protected static String toSchemaJson(
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
                String subject = "%s-%s-value".formatted(message.channel.name, message.name);
                injectPayloadModel(injector, message, subject);
            }

            protected final void injectPayloadModel(
                Consumer<ModelConfig> injector,
                AsyncapiMessageView message,
                String subject)
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

            protected final <C> HttpOptionsConfigBuilder<C> injectHttpAuthorization(
                HttpOptionsConfigBuilder<C> options,
                AsyncapiSchemaConfig schema)
            {
                final Map.Entry<String, AsyncapiSecuritySchemeView> secured =
                    resolveSecurityScheme(schema, AsyncapiCompositeGenerator::isHttpCredentialScheme);

                if (secured != null)
                {
                    final AsyncapiSecuritySchemeView scheme = secured.getValue();
                    final long guardId = config.resolveId.applyAsLong(schema.security.get(secured.getKey()));
                    final String qname = config.supplyQName.apply(guardId);

                    if ("http".equals(scheme.type) && "bearer".equals(scheme.scheme))
                    {
                        injectBearerAuthorization(options, qname);
                    }
                    else if ("httpApiKey".equals(scheme.type))
                    {
                        injectApiKeyAuthorization(options, qname, scheme);
                    }
                }

                return options;
            }

            private <C> void injectBearerAuthorization(
                HttpOptionsConfigBuilder<C> options,
                String qname)
            {
                options
                    .authorization()
                        .name(qname)
                        .credentials()
                            .header()
                                .name("authorization")
                                .pattern("Bearer {credentials}")
                                .build()
                            .build()
                        .build();
            }

            private <C> void injectApiKeyAuthorization(
                HttpOptionsConfigBuilder<C> options,
                String qname,
                AsyncapiSecuritySchemeView scheme)
            {
                switch (scheme.in)
                {
                case "header":
                    options
                        .authorization()
                            .name(qname)
                            .credentials()
                                .header()
                                    .name(scheme.parameterName)
                                    .pattern("{credentials}")
                                    .build()
                                .build()
                            .build();
                    break;
                case "query":
                    options
                        .authorization()
                            .name(qname)
                            .credentials()
                                .parameter()
                                    .name(scheme.parameterName)
                                    .pattern("{credentials}")
                                    .build()
                                .build()
                            .build();
                    break;
                case "cookie":
                    options
                        .authorization()
                            .name(qname)
                            .credentials()
                                .cookie()
                                    .name(scheme.parameterName)
                                    .pattern("{credentials}")
                                    .build()
                                .build()
                            .build();
                    break;
                default:
                    break;
                }
            }

            protected final <C> MqttOptionsConfigBuilder<C> injectMqttAuthorization(
                MqttOptionsConfigBuilder<C> options,
                AsyncapiSchemaConfig schema)
            {
                final List<Map.Entry<String, AsyncapiSecuritySchemeView>> secured =
                    resolveSecuritySchemes(schema, AsyncapiCompositeGenerator::isMqttCredentialScheme);

                if (!secured.isEmpty())
                {
                    final long guardId = config.resolveId.applyAsLong(schema.security.get(secured.get(0).getKey()));
                    final String qname = config.supplyQName.apply(guardId);

                    options
                        .authorization()
                            .name(qname)
                            .credentials()
                                .inject(credentials -> injectMqttConnectPatterns(credentials, secured))
                                .build()
                            .build();
                }

                return options;
            }

            private <C> MqttCredentialsConfigBuilder<C> injectMqttConnectPatterns(
                MqttCredentialsConfigBuilder<C> credentials,
                List<Map.Entry<String, AsyncapiSecuritySchemeView>> secured)
            {
                for (Map.Entry<String, AsyncapiSecuritySchemeView> entry : secured)
                {
                    credentials
                        .connect()
                            .property(toMqttConnectProperty(entry.getValue().in))
                            .pattern("{credentials}")
                            .build();
                }

                return credentials;
            }

            private MqttConnectProperty toMqttConnectProperty(
                String in)
            {
                return "user".equals(in) ? MqttConnectProperty.USERNAME
                    : "password".equals(in) ? MqttConnectProperty.PASSWORD
                    : null;
            }

            protected final <C> KafkaOptionsConfigBuilder<C> injectKafkaAuthorization(
                KafkaOptionsConfigBuilder<C> options,
                AsyncapiSchemaConfig schema)
            {
                final Map.Entry<String, AsyncapiSecuritySchemeView> secured =
                    resolveSecurityScheme(schema, AsyncapiCompositeGenerator::isKafkaSaslScheme);

                if (secured != null)
                {
                    final String mechanism = KAFKA_SASL_MECHANISMS.get(secured.getValue().type);
                    final long guardId = config.resolveId.applyAsLong(schema.security.get(secured.getKey()));
                    final String qname = config.supplyQName.apply(guardId);

                    options
                        .authorization()
                            .name(qname)
                            .credentials()
                                .mechanism(mechanism)
                                .username("{identity}")
                                .password("{credentials}")
                                .build()
                            .build();
                }

                return options;
            }

            private Map.Entry<String, AsyncapiSecuritySchemeView> resolveSecurityScheme(
                AsyncapiSchemaConfig schema,
                Predicate<AsyncapiSecuritySchemeView> filter)
            {
                return resolveSecuritySchemes(schema, filter).stream().findFirst().orElse(null);
            }

            private List<Map.Entry<String, AsyncapiSecuritySchemeView>> resolveSecuritySchemes(
                AsyncapiSchemaConfig schema,
                Predicate<AsyncapiSecuritySchemeView> filter)
            {
                final Map<String, String> security = schema.security;
                final Map<String, AsyncapiSecuritySchemeView> schemes = schema.asyncapi.components != null
                    ? schema.asyncapi.components.securitySchemes
                    : null;

                final List<Map.Entry<String, AsyncapiSecuritySchemeView>> resolved = new ArrayList<>();
                if (security != null && schemes != null)
                {
                    for (String name : security.keySet())
                    {
                        final AsyncapiSecuritySchemeView scheme = schemes.get(name);
                        if (scheme != null && filter.test(scheme))
                        {
                            resolved.add(Map.entry(name, scheme));
                        }
                    }
                }

                return resolved;
            }

            protected final GuardedResolution resolveGuarded(
                AsyncapiSchemaConfig schema,
                AsyncapiOperationView operation)
            {
                return AsyncapiGuardResolver.resolve(
                    operation.name, schema.specLabel, operation.security, schema.security,
                    config.resolveId, config.supplyQName);
            }

            protected final String guardQname(
                GuardedResolution resolution)
            {
                return resolution.guarded.isEmpty() ? null : resolution.guarded.get(0).qname;
            }

            protected final boolean allowed(
                AsyncapiSchemaConfig schema,
                AsyncapiOperationView operation)
            {
                final GuardedResolution resolution = resolveGuarded(schema, operation);
                final boolean allowed = !resolution.denied();

                if (!allowed)
                {
                    denied.add(resolution.reason);
                }

                return allowed;
            }
        }
    }
}
