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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.json.bind.Jsonb;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiBindingConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConditionConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.config.AsyncapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.kafka.AsyncapiKafkaMessageBindingEx;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.bindings.kafka.AsyncapiKafkaServerBindingEx;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfigBuilder;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttOptionsConfigBuilder;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiMultiFormatSchemaView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public final class AsyncapiClientGenerator extends AsyncapiCompositeGenerator
{
    @Override
    protected AsyncapiCompositeConfig generate(
        AsyncapiBindingConfig binding,
        List<AsyncapiSchemaConfig> schemas)
    {
        List<NamespaceConfig> namespaces = new LinkedList<>();
        List<AsyncapiCompositeRouteConfig> routes = new LinkedList<>();
        for (AsyncapiSchemaConfig schema  : schemas)
        {
            NamespaceHelper helper = new ClientNamespaceHelper(binding, schema);
            NamespaceConfig namespace = NamespaceConfig.builder()
                    .inject(helper::injectAll)
                    .build();

            namespaces.add(namespace);

            Matcher routedType = Pattern.compile("(?:http|sse|mqtt|kafka_cache)_client0").matcher("");
            namespace.bindings.stream()
                .filter(b -> routedType.reset(b.name).matches())
                .forEach(b ->
                {
                    final int operationTypeId = binding.supplyTypeId.applyAsInt(b.type);
                    final long routeId = binding.supplyBindingId.applyAsLong(namespace, b);

                    final AsyncapiCompositeConditionConfig when = new AsyncapiCompositeConditionConfig(
                        schema.schemaId,
                        operationTypeId);

                    routes.add(new AsyncapiCompositeRouteConfig(routeId, when));
                });
        }

        return new AsyncapiCompositeConfig(schemas, namespaces, routes);
    }

    private final class ClientNamespaceHelper extends NamespaceHelper
    {
        private final CatalogsHelper catalogs;
        private final BindingsHelper bindings;

        private ClientNamespaceHelper(
            AsyncapiBindingConfig config,
            AsyncapiSchemaConfig schema)
        {
            super(config, schema.specLabel);
            this.catalogs = new ClientCatalogsHelper(schema);
            this.bindings = new ClientBindingsHelper(schema);
        }

        protected <C> NamespaceConfigBuilder<C> injectComponents(
            NamespaceConfigBuilder<C> namespace)
        {
            return namespace
                    .inject(catalogs::injectAll)
                    .inject(bindings::injectAll);
        }

        private final class ClientCatalogsHelper extends CatalogsHelper
        {
            private ClientCatalogsHelper(
                AsyncapiSchemaConfig schema)
            {
                super(schema);
            }

            @Override
            protected <C> void injectInlineSubject(
                Jsonb jsonb,
                InlineOptionsConfigBuilder<C> options,
                AsyncapiMessageView message)
            {
                super.injectInlineSubject(jsonb, options, message);

                Optional<AsyncapiKafkaMessageBindingEx> kafkaBinding =
                    message.binding("kafka", AsyncapiKafkaMessageBindingEx.class);
                if (kafkaBinding.isPresent() && kafkaBinding.get().key != null)
                {
                    Optional<AsyncapiServerView> serverRef = Stream.of(schema)
                            .map(s -> s.asyncapi)
                            .flatMap(v -> v.servers.stream())
                            .filter(s -> s.binding("kafka", AsyncapiKafkaServerBindingEx.class)
                                .map(b -> b.schemaRegistryUrl).isPresent())
                            .findFirst();

                    String subject = serverRef.isPresent()
                        ? "%s-key".formatted(message.channel.address)
                        : "%s-%s-key".formatted(message.channel.name, message.name);

                    AsyncapiMultiFormatSchemaView key = schema.asyncapi.resolveSchema(kafkaBinding.get().key);

                    options.schema()
                        .subject(subject)
                        .version("latest")
                        .schema(toSchemaJson(jsonb, key.model))
                        .build();
                }
            }
        }

        private final class ClientBindingsHelper extends BindingsHelper
        {
            private static final Pattern PARAMETERIZED_TOPIC_PATTERN = Pattern.compile(REGEX_ADDRESS_PARAMETER);

            private final AsyncapiSchemaConfig schema;
            private final Map<String, NamespaceInjector> protocols;
            private final List<String> secure;

            private ClientBindingsHelper(
                AsyncapiSchemaConfig schema)
            {
                this.schema = schema;
                this.protocols = Map.of(
                    "kafka", this::injectKafka,
                    "kafka-secure", this::injectKafkaSecure,
                    "http", this::injectHttp,
                    "https", this::injectHttps,
                    "mqtt", this::injectMqtt,
                    "mqtts", this::injectMqtts,
                    "mqtt+secure", this::injectMqtts);
                this.secure = List.of("kafka-secure", "https", "mqtts", "mqtt+secure");
            }

            @Override
            protected <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                        .inject(this::injectProtocols)
                        .inject(this::injectTlsClient);
            }

            private <C> NamespaceConfigBuilder<C> injectProtocols(
                NamespaceConfigBuilder<C> namespace)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .map(s -> s.url)
                    .filter(Objects::nonNull)
                    .map(URI::getScheme)
                    .filter(Objects::nonNull)
                    .map(this::resolveProtocol)
                    .distinct()
                    .map(protocols::get)
                    .filter(Objects::nonNull)
                    .forEach(p -> p.inject(namespace));

                return namespace;
            }

            private String resolveProtocol(
                String protocol)
            {
                boolean secure = isSecure();

                return switch (protocol)
                {
                case "kafka", "kafka-secure" -> secure ? "kafka-secure" : "kafka";
                case "http", "https" -> secure ? "https" : "http";
                case "mqtt", "mqtts", "mqtt+secure" -> secure ? "mqtts" : "mqtt";
                default -> protocol;
                };
            }

            private URI resolveServer()
            {
                return config.resolveServers(schema.specLabel).stream()
                    .findFirst()
                    .orElseThrow();
            }

            private boolean isSecure()
            {
                return secure.stream().anyMatch(protocol -> protocol.equals(resolveServer().getScheme()));
            }

            private String resolveAuthority()
            {
                return authority(resolveServer());
            }

            private String authority(
                URI uri)
            {
                return uri.getPort() != -1
                    ? "%s:%d".formatted(uri.getHost(), uri.getPort())
                    : uri.getHost();
            }

            private <C> NamespaceConfigBuilder<C> injectTlsClient(
                NamespaceConfigBuilder<C> namespace)
            {
                if (isSecure())
                {
                    namespace
                        .binding()
                            .name("tls_client0")
                            .type("tls")
                            .kind(CLIENT)
                            .inject(this::injectMetrics)
                            .vault(config.qvault)
                            .exit("sys:tcp_client")
                            .build();
                }

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectKafka(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .inject(this::injectKafkaCache)
                    .binding()
                        .name("kafka_client0")
                        .type("kafka")
                        .kind(CLIENT)
                        .options(KafkaOptionsConfig::builder)
                            .inject(this::injectKafkaSaslOptions)
                            .inject(this::injectKafkaAuthorizationOptions)
                            .inject(this::injectKafkaServerOptions)
                            .build()
                        .inject(this::injectMetrics)
                        .exit("sys:tcp_client")
                        .build();
            }

            private <C> NamespaceConfigBuilder<C> injectKafkaSecure(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .inject(this::injectKafkaCache)
                    .binding()
                        .name("kafka_client0")
                        .type("kafka")
                        .kind(CLIENT)
                        .options(KafkaOptionsConfig::builder)
                            .inject(this::injectKafkaSaslOptions)
                            .inject(this::injectKafkaAuthorizationOptions)
                            .inject(this::injectKafkaServerOptions)
                            .build()
                        .inject(this::injectMetrics)
                        .exit("tls_client0")
                        .build();
            }

            private <C> NamespaceConfigBuilder<C> injectKafkaCache(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                        .binding()
                            .name("kafka_cache_client0")
                            .type("kafka")
                            .kind(KindConfig.CACHE_CLIENT)
                            .inject(this::injectMetrics)
                            .options(KafkaOptionsConfig::builder)
                                .inject(this::injectKafkaTopicOptions)
                                .build()
                            .exit("kafka_cache_server0")
                        .build()
                        .binding()
                            .name("kafka_cache_server0")
                            .type("kafka")
                            .kind(KindConfig.CACHE_SERVER)
                            .inject(this::injectMetrics)
                            .options(KafkaOptionsConfig::builder)
                                .inject(this::injectKafkaBootstrapOptions)
                                .inject(this::injectKafkaTopicOptions)
                                .build()
                            .exit("kafka_client0")
                        .build();
            }

            private <C> KafkaOptionsConfigBuilder<C> injectKafkaTopicOptions(
                KafkaOptionsConfigBuilder<C> options)
            {
                List<KafkaTopicConfig> topics = config.options.kafka != null
                    ? config.options.kafka.topics
                    : null;

                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.channels.values().stream())
                    .filter(c -> !PARAMETERIZED_TOPIC_PATTERN.matcher(c.address).find())
                    .distinct()
                    .forEach(channel ->
                        options.topic()
                            .name(channel.address)
                            .inject(t -> injectKafkaTopicDefaults(t, channel, topics))
                            .inject(t -> injectKafkaTopicKey(t, channel))
                            .inject(t -> injectKafkaTopicValue(t, channel))
                            .build());

                return options;
            }

            private <C> KafkaTopicConfigBuilder<C> injectKafkaTopicDefaults(
                KafkaTopicConfigBuilder<C> topic,
                AsyncapiChannelView channel,
                List<KafkaTopicConfig> topics)
            {
                if (topics != null)
                {
                    Optional<KafkaTopicConfig> topicConfig = topics.stream()
                        .filter(t -> t.name.equals(channel.address))
                        .findFirst();
                    topicConfig.ifPresent(kafkaTopicConfig -> injectKafkaTopicConfig(topic, kafkaTopicConfig));
                }
                return topic;
            }

            private <C> void injectKafkaTopicConfig(
                KafkaTopicConfigBuilder<C> topic,
                KafkaTopicConfig kafkaTopicConfig)
            {
                topic.defaultOffset(kafkaTopicConfig.defaultOffset)
                    .deltaType(kafkaTopicConfig.deltaType);

                if (kafkaTopicConfig.transforms != null)
                {
                    topic.transforms()
                        .extractKey(kafkaTopicConfig.transforms.extractKey)
                        .extractHeaders(kafkaTopicConfig.transforms.extractHeaders)
                        .build();
                }
            }

            private <C> KafkaTopicConfigBuilder<C> injectKafkaTopicKey(
                KafkaTopicConfigBuilder<C> topic,
                AsyncapiChannelView channel)
            {
                if (channel.hasMessages())
                {
                    Optional<AsyncapiServerView> serverRef = Stream.of(schema)
                            .map(s -> s.asyncapi)
                            .flatMap(v -> v.servers.stream())
                            .filter(s -> s.binding("kafka", AsyncapiKafkaServerBindingEx.class)
                                .map(b -> b.schemaRegistryUrl).isPresent())
                            .findFirst();

                    channel.messages.stream()
                        .filter(m -> m.binding("kafka", AsyncapiKafkaMessageBindingEx.class)
                            .map(b -> b.key).isPresent())
                        .forEach(message ->
                            topic.key(AvroModelConfig::builder) // TODO: assumes AVRO
                                .catalog()
                                    .name("catalog0")
                                    .schema()
                                        .version("latest")
                                        .subject(serverRef.isPresent()
                                            ? "%s-key".formatted(message.channel.address)
                                            : "%s-%s-key".formatted(message.channel.name, message.name))
                                        .build()
                                    .build()
                                .build());
                }
                return topic;
            }

            private <C> KafkaTopicConfigBuilder<C> injectKafkaTopicValue(
                KafkaTopicConfigBuilder<C> topic,
                AsyncapiChannelView channel)
            {
                if (channel.hasMessages())
                {
                    Optional<AsyncapiServerView> serverRef = Stream.of(schema)
                            .map(s -> s.asyncapi)
                            .flatMap(v -> v.servers.stream())
                            .filter(s -> s.binding("kafka", AsyncapiKafkaServerBindingEx.class)
                                .map(b -> b.schemaRegistryUrl).isPresent())
                            .findFirst();

                    AsyncapiMessageView message = channel.messages.get(0);
                    String subject = serverRef.isPresent()
                        ? "%s-value".formatted(message.channel.address)
                        : "%s-%s-value".formatted(message.channel.name, message.name);

                    injectPayloadModel(topic::value, message, subject);
                }

                return topic;
            }

            private <C> KafkaOptionsConfigBuilder<C> injectKafkaBootstrapOptions(
                KafkaOptionsConfigBuilder<C> options)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.channels.values().stream())
                    .filter(c -> !PARAMETERIZED_TOPIC_PATTERN.matcher(c.address).find())
                    .map(c -> c.address)
                    .distinct()
                    .forEach(options::bootstrap);

                return options;
            }

            private <C> KafkaOptionsConfigBuilder<C> injectKafkaSaslOptions(
                KafkaOptionsConfigBuilder<C> options)
            {
                KafkaSaslConfig sasl = config.options != null && config.options.kafka != null
                    ? config.options.kafka.sasl
                    : null;

                if (sasl != null)
                {
                    options.sasl()
                        .mechanism(sasl.mechanism)
                        .username(sasl.username)
                        .password(sasl.password)
                        .build();
                }

                return options;
            }

            private <C> KafkaOptionsConfigBuilder<C> injectKafkaAuthorizationOptions(
                KafkaOptionsConfigBuilder<C> options)
            {
                final KafkaAuthorizationConfig authorization = config.options != null && config.options.kafka != null
                    ? config.options.kafka.authorization
                    : null;

                if (authorization != null)
                {
                    options.authorization()
                        .name(authorization.qname)
                        .credentials(authorization.credentials)
                        .build();
                }

                return options;
            }

            private <C> KafkaOptionsConfigBuilder<C> injectKafkaServerOptions(
                KafkaOptionsConfigBuilder<C> options)
            {
                URI server = resolveServer();

                options.server()
                    .host(server.getHost())
                    .port(server.getPort())
                    .build();

                return options;
            }

            private <C> NamespaceConfigBuilder<C> injectHttp(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .inject(this::injectSseClient)
                    .binding()
                        .name("http_client0")
                        .type("http")
                        .kind(CLIENT)
                        .options(HttpOptionsConfig::builder)
                            .inject(this::injectHttpOptions)
                            .build()
                        .inject(this::injectMetrics)
                        .exit("sys:tcp_client")
                        .build();
            }

            private <C> NamespaceConfigBuilder<C> injectHttps(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .inject(this::injectSseClient)
                    .binding()
                        .name("http_client0")
                        .type("http")
                        .kind(CLIENT)
                        .options(HttpOptionsConfig::builder)
                            .inject(this::injectHttpOptions)
                            .build()
                        .inject(this::injectMetrics)
                        .exit("tls_client0")
                        .build();
            }

            private <C> HttpOptionsConfigBuilder<C> injectHttpOptions(
                HttpOptionsConfigBuilder<C> options)
            {
                options.override(":authority", resolveAuthority());

                return options;
            }

            private <C> NamespaceConfigBuilder<C> injectSseClient(
                NamespaceConfigBuilder<C> namespace)
            {
                if (Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.operations.values().stream())
                    .anyMatch(op -> op.hasBinding("x-zilla-sse")))
                {
                    namespace
                        .binding()
                            .name("sse_client0")
                            .type("sse")
                            .kind(CLIENT)
                            .inject(this::injectMetrics)
                            .exit("http_client0")
                            .build();
                }

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectMqtt(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .binding()
                        .name("mqtt_client0")
                        .type("mqtt")
                        .kind(CLIENT)
                        .options(MqttOptionsConfig::builder)
                            .inject(this::injectMqttOptions)
                            .build()
                        .inject(this::injectMetrics)
                        .exit("sys:tcp_client")
                        .build();
            }

            private <C> NamespaceConfigBuilder<C> injectMqtts(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                    .binding()
                        .name("mqtt_client0")
                        .type("mqtt")
                        .kind(CLIENT)
                        .options(MqttOptionsConfig::builder)
                            .inject(this::injectMqttOptions)
                            .build()
                        .inject(this::injectMetrics)
                        .exit("tls_client0")
                        .build();
            }

            private <C> MqttOptionsConfigBuilder<C> injectMqttOptions(
                MqttOptionsConfigBuilder<C> options)
            {
                options.server("mqtt://" + resolveAuthority());

                return options;
            }
        }
    }
}
