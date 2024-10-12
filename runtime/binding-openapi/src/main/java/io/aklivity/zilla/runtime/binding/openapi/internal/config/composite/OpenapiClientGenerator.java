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
package io.aklivity.zilla.runtime.binding.openapi.internal.config.composite;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.json.bind.Jsonb;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.OpenapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.OpenapiMessageView;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConditionConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.config.OpenapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public final class OpenapiClientGenerator extends OpenapiCompositeGenerator
{
    @Override
    protected OpenapiCompositeConfig generate(
        OpenapiBindingConfig binding,
        List<OpenapiSchemaConfig> schemas)
    {
        List<NamespaceConfig> namespaces = new LinkedList<>();
        List<OpenapiCompositeRouteConfig> routes = new LinkedList<>();
        for (OpenapiSchemaConfig schema  : schemas)
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

                    final OpenapiCompositeConditionConfig when = new OpenapiCompositeConditionConfig(
                        schema.schemaId,
                        operationTypeId);

                    routes.add(new OpenapiCompositeRouteConfig(routeId, when));
                });
        }

        return new OpenapiCompositeConfig(schemas, namespaces, routes);
    }

    private final class ClientNamespaceHelper extends NamespaceHelper
    {
        private final CatalogsHelper catalogs;
        private final BindingsHelper bindings;

        private ClientNamespaceHelper(
            OpenapiBindingConfig config,
            OpenapiSchemaConfig schema)
        {
            super(config, schema.apiLabel);
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
                OpenapiSchemaConfig schema)
            {
                super(schema);
            }

            @Override
            protected <C> void injectInlineSubject(
                Jsonb jsonb,
                InlineOptionsConfigBuilder<C> options,
                OpenapiMessageView message)
            {
                super.injectInlineSubject(jsonb, options, message);

                if (message.bindings != null &&
                    message.bindings.kafka != null &&
                    message.bindings.kafka.key != null)
                {
                    Optional<OpenapiServerView> serverRef = Stream.of(schema)
                            .map(s -> s.asyncapi)
                            .flatMap(v -> v.servers.stream())
                            .filter(s -> s.bindings != null)
                            .filter(s -> s.bindings.kafka != null)
                            .filter(s -> s.bindings.kafka.schemaRegistryUrl != null)
                            .findFirst();

                    String subject = serverRef.isPresent()
                        ? "%s-key".formatted(message.channel.address)
                        : "%s-%s-key".formatted(message.channel.name, message.name);

                    options.schema()
                        .subject(subject)
                        .version("latest")
                        .schema(toSchemaJson(jsonb, message.bindings.kafka.key.model))
                        .build();
                }
            }
        }

        private final class ClientBindingsHelper extends BindingsHelper
        {
            private static final Pattern PARAMETERIZED_TOPIC_PATTERN = Pattern.compile(REGEX_ADDRESS_PARAMETER);

            private final OpenapiSchemaConfig schema;
            private final Map<String, NamespaceInjector> protocols;
            private final List<String> secure;

            private ClientBindingsHelper(
                OpenapiSchemaConfig schema)
            {
                this.schema = schema;
                this.protocols = Map.of(
                    "kafka", this::injectKafka,
                    "kafka-secure", this::injectKafkaSecure,
                    "http", this::injectHttp,
                    "https", this::injectHttps,
                    "mqtt", this::injectMqtt,
                    "mqtts", this::injectMqtts);
                this.secure = List.of("kafka-secure", "https", "mqtts");
            }

            @Override
            protected <C> NamespaceConfigBuilder<C> injectAll(
                NamespaceConfigBuilder<C> namespace)
            {
                return namespace
                        .inject(this::injectProtocols)
                        .inject(this::injectTlsClient)
                        .inject(this::injectTcpClient);
            }

            private <C> NamespaceConfigBuilder<C> injectProtocols(
                NamespaceConfigBuilder<C> namespace)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .map(s -> s.protocol)
                    .distinct()
                    .map(protocols::get)
                    .filter(Objects::nonNull)
                    .forEach(p -> p.inject(namespace));

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectTlsClient(
                NamespaceConfigBuilder<C> namespace)
            {
                if (Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream()).anyMatch(s -> secure.contains(s.protocol)))
                {
                    namespace
                        .binding()
                            .name("tls_client0")
                            .type("tls")
                            .kind(CLIENT)
                            .inject(this::injectMetrics)
                            .options(config.options.tls)
                            .vault(config.qvault)
                            .exit("tcp_client0")
                            .build();
                }

                return namespace;
            }

            private <C> NamespaceConfigBuilder<C> injectTcpClient(
                NamespaceConfigBuilder<C> namespace)
            {
                final TcpOptionsConfig tcpOptions = config.options.tcp != null
                        ? config.options.tcp
                        : TcpOptionsConfig.builder()
                            .inject(o ->
                                Stream.of(schema)
                                    .map(s -> s.asyncapi)
                                    .flatMap(v -> v.servers.stream())
                                    .findFirst()
                                    .map(s -> o
                                        .host(s.hostname)
                                        .ports(new int[] { s.port }))
                                    .get())
                            .build();

                return namespace
                    .binding()
                    .name("tcp_client0")
                    .type("tcp")
                    .kind(CLIENT)
                    .inject(this::injectMetrics)
                    .options(tcpOptions)
                .build();

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
                            .inject(this::injectKafkaServerOptions)
                            .build()
                        .inject(this::injectMetrics)
                        .exit("tcp_client0")
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
                            .inject(t -> injectKafkaTopicTransforms(t, channel, topics))
                            .inject(t -> injectKafkaTopicKey(t, channel))
                            .inject(t -> injectKafkaTopicValue(t, channel))
                            .build());

                return options;
            }

            private <C> KafkaTopicConfigBuilder<C> injectKafkaTopicTransforms(
                KafkaTopicConfigBuilder<C> topic,
                OpenapiChannelView channel,
                List<KafkaTopicConfig> topics)
            {
                if (topics != null)
                {
                    Optional<KafkaTopicConfig> topicConfig = topics.stream()
                        .filter(t -> t.name.equals(channel.address))
                        .findFirst();
                    topicConfig.ifPresent(kafkaTopicConfig -> topic
                        .transforms()
                        .extractKey(kafkaTopicConfig.transforms.extractKey)
                        .extractHeaders(kafkaTopicConfig.transforms.extractHeaders)
                        .build());
                }
                return topic;
            }

            private <C> KafkaTopicConfigBuilder<C> injectKafkaTopicKey(
                KafkaTopicConfigBuilder<C> topic,
                OpenapiChannelView channel)
            {
                if (channel.hasMessages())
                {
                    Optional<OpenapiServerView> serverRef = Stream.of(schema)
                            .map(s -> s.asyncapi)
                            .flatMap(v -> v.servers.stream())
                            .filter(s -> s.bindings != null)
                            .filter(s -> s.bindings.kafka != null)
                            .filter(s -> s.bindings.kafka.schemaRegistryUrl != null)
                            .findFirst();

                    channel.messages.stream()
                        .filter(m -> m.bindings != null && m.bindings.kafka != null && m.bindings.kafka.key != null)
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
                OpenapiChannelView channel)
            {
                if (channel.hasMessages())
                {
                    Optional<OpenapiServerView> serverRef = Stream.of(schema)
                            .map(s -> s.asyncapi)
                            .flatMap(v -> v.servers.stream())
                            .filter(s -> s.bindings != null)
                            .filter(s -> s.bindings.kafka != null)
                            .filter(s -> s.bindings.kafka.schemaRegistryUrl != null)
                            .findFirst();

                    OpenapiMessageView message = channel.messages.get(0);
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

            private <C> KafkaOptionsConfigBuilder<C> injectKafkaServerOptions(
                KafkaOptionsConfigBuilder<C> options)
            {
                Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.servers.stream())
                    .forEach(s ->
                        options.server()
                            .host(s.hostname)
                            .port(s.port)
                            .build());

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
                        .inject(this::injectMetrics)
                        .exit("tcp_client0")
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
                        .inject(this::injectMetrics)
                        .exit("tls_client0")
                        .build();
            }

            private <C> NamespaceConfigBuilder<C> injectSseClient(
                NamespaceConfigBuilder<C> namespace)
            {
                if (Stream.of(schema)
                    .map(s -> s.asyncapi)
                    .flatMap(v -> v.operations.values().stream())
                    .anyMatch(OpenapiOperationView::hasBindingsSse))
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
                        .inject(this::injectMetrics)
                        .exit("tcp_client0")
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
                        .inject(this::injectMetrics)
                        .exit("tls_client0")
                        .build();
            }
        }
    }
}
