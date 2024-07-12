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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;


public class AyncapiKafkaProtocol extends AsyncapiProtocol
{
    public static final String SCHEME = "kafka";
    private static final String SECURE_PROTOCOL = "kafka-secure";
    private static final Pattern PARAMETERIZED_TOPIC_PATTERN = Pattern.compile("\\{.*?\\}");
    private final KafkaSaslConfig sasl;
    private final List<AsyncapiServerView> servers;

    public AyncapiKafkaProtocol(
        String qname,
        List<Asyncapi> asyncapis,
        List<AsyncapiServerView> servers,
        AsyncapiOptionsConfig options,
        String protocol)
    {
        super(qname, asyncapis, protocol, SCHEME);
        this.servers = servers;
        this.sasl = options.kafka != null ? options.kafka.sasl : null;
    }

    @Override
    public <C> NamespaceConfigBuilder<C> injectProtocolClientCache(
        NamespaceConfigBuilder<C> namespace,
        List<MetricRefConfig> metricRefs,
        AsyncapiOptionsConfig options)
    {
        final KafkaOptionsConfig kafka = options.kafka;
        return namespace
                .binding()
                    .name("kafka_cache_client0")
                    .type("kafka")
                    .kind(KindConfig.CACHE_CLIENT)
                    .inject(b -> this.injectMetrics(b, metricRefs))
                                        .options(KafkaOptionsConfig::builder)
                                            .inject(optionBuilder -> this.injectKafkaTopicOptions(optionBuilder, kafka))
                                            .build()
                    .exit("kafka_cache_server0")
                .build()
                .binding()
                    .name("kafka_cache_server0")
                    .type("kafka")
                    .kind(KindConfig.CACHE_SERVER)
                    .inject(b -> this.injectMetrics(b, metricRefs))
                    .options(KafkaOptionsConfig::builder)
                        .inject(this::injectKafkaBootstrapOptions)
                        .inject(optionBuilder -> this.injectKafkaTopicOptions(optionBuilder, kafka))
                        .build()
                    .exit("kafka_client0")
                .build();
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolClientOptions(
        BindingConfigBuilder<C> binding)
    {
        return binding.options(KafkaOptionsConfig::builder)
                .inject(this::injectKafkaSaslOptions)
                .inject(this::injectKafkaServerOptions)
                .build();
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding)
    {
        return binding;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProtocolServerRoutes(
        BindingConfigBuilder<C> binding,
        String qname,
        AsyncapiOptionsConfig options)
    {
        return binding;
    }

    @Override
    protected boolean isSecure()
    {
        return protocol.equals(SECURE_PROTOCOL);
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaSaslOptions(
        KafkaOptionsConfigBuilder<C> options)
    {
        return sasl != null ? options.sasl(KafkaSaslConfig::builder)
            .mechanism(sasl.mechanism)
            .username(sasl.username)
            .password(sasl.password)
            .build() : options;
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaServerOptions(
        KafkaOptionsConfigBuilder<C> options)
    {
        return options.servers(servers.stream().map(s ->
        {
            String[] hostAndPort = s.host().split(":");
            return KafkaServerConfig.builder()
                .host(hostAndPort[0])
                .port(Integer.parseInt(hostAndPort[1]))
                .build();
        }).collect(Collectors.toList()));
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaTopicOptions(
        KafkaOptionsConfigBuilder<C> options,
        KafkaOptionsConfig kafka)
    {
        for (Asyncapi asyncapi : asyncapis)
        {
            for (String name : asyncapi.operations.keySet())
            {
                AsyncapiOperation operation = asyncapi.operations.get(name);
                AsyncapiChannelView channel = AsyncapiChannelView.of(asyncapi.channels, operation.channel);
                String topic = channel.address();
                String replyTo = operation.reply != null
                    ? AsyncapiChannelView.of(asyncapi.channels, operation.reply.channel).address()
                    : null;

                if (channel.messages() != null && !channel.messages().isEmpty() ||
                    channel.parameters() != null && !channel.parameters().isEmpty())
                {
                    KafkaTopicConfig kafkaTopic = kafka != null && kafka.topics != null
                        ? kafka.topics.stream()
                            .filter(t -> t.name.equals(topic))
                            .findFirst()
                            .orElse(null)
                        : null;
                    options
                        .topic(KafkaTopicConfig::builder)
                            .name(topic)
                            .inject(topicConfig -> injectHeader(topicConfig, kafkaTopic))
                            .inject(topicConfig -> injectKey(topicConfig, asyncapi, channel.messages()))
                            .inject(topicConfig -> injectValue(topicConfig, asyncapi, channel.messages()))
                            .build()
                        .build();

                    if (replyTo != null)
                    {
                        KafkaTopicConfig kafkaReply = kafka != null && kafka.topics != null
                            ? kafka.topics.stream()
                                .filter(t -> t.name.equals(replyTo))
                                .findFirst()
                                .orElse(null)
                            : null;
                        AsyncapiChannelView replyView = AsyncapiChannelView.of(asyncapi.channels, operation.reply.channel);

                        options
                            .topic(KafkaTopicConfig::builder)
                                .name(replyTo)
                                .inject(topicConfig -> injectHeader(topicConfig, kafkaReply))
                                .inject(topicConfig -> injectValue(topicConfig, asyncapi, replyView.messages()))
                                .build()
                            .build();
                    }
                }
            }
        }
        return options;
    }

    private <C> KafkaOptionsConfigBuilder<C> injectKafkaBootstrapOptions(
        KafkaOptionsConfigBuilder<C> options)
    {
        List<String> bootstrap = new ArrayList<>();
        for (Asyncapi asyncapi : asyncapis)
        {
            bootstrap.addAll(asyncapi.channels.values().stream()
                .filter(c -> !PARAMETERIZED_TOPIC_PATTERN.matcher(c.address).find())
                .map(c -> AsyncapiChannelView.of(asyncapi.channels, c).address()).collect(Collectors.toList()));
        }
        return options.bootstrap(bootstrap);
    }

    private <C> KafkaTopicConfigBuilder<C> injectHeader(
        KafkaTopicConfigBuilder<C> topic,
        KafkaTopicConfig kafkaTopic)
    {
        if (kafkaTopic != null)
        {
            kafkaTopic.headers.forEach(h -> topic.header(h.name, h.path));

        }
        return topic;
    }

    private <C> KafkaTopicConfigBuilder<C> injectKey(
        KafkaTopicConfigBuilder<C> topic,
        Asyncapi asyncapi,
        Map<String, AsyncapiMessage> messages)
    {
        if (messages != null)
        {
            for (Map.Entry<String, AsyncapiMessage> messageEntry : messages.entrySet())
            {
                AsyncapiMessageView message =
                    AsyncapiMessageView.of(asyncapi.components.messages, messageEntry.getValue());
                if (message.key() != null)
                {
                    topic.key(AvroModelConfig.builder()
                        .catalog()
                            .name(INLINE_CATALOG_NAME)
                            .inject(catalog -> injectKeySchema(catalog, asyncapi, message))
                            .build()
                        .build());
                }
            }
        }
        return topic;
    }

    private <C> KafkaTopicConfigBuilder<C> injectValue(
        KafkaTopicConfigBuilder<C> topic,
        Asyncapi asyncapi,
        Map<String, AsyncapiMessage> messages)
    {
        if (messages != null)
        {
            AsyncapiMessageView message =
                AsyncapiMessageView.of(asyncapi.components.messages, messages.values().stream().findFirst().get());
            String contentType = message.contentType() == null ? asyncapi.defaultContentType : message.contentType();
            ModelConfig model = null;
            if (contentType != null)
            {
                if (jsonContentType.reset(contentType).matches())
                {
                    model = JsonModelConfig.builder()
                            .catalog()
                            .name(INLINE_CATALOG_NAME)
                            .inject(catalog -> injectValueSchemas(catalog, asyncapi, messages))
                            .build()
                        .build();
                }
                else if (avroContentType.reset(contentType).matches())
                {
                    model = AvroModelConfig.builder()
                        .view("json")
                        .catalog()
                            .name(INLINE_CATALOG_NAME)
                            .inject(catalog -> injectValueSchemas(catalog, asyncapi, messages))
                            .build()
                        .build();
                }
                else if (protobufContentType.reset(contentType).matches())
                {
                    model = ProtobufModelConfig.builder()
                        .view("json")
                        .catalog()
                            .name(INLINE_CATALOG_NAME)
                            .inject(catalog -> injectValueSchemas(catalog, asyncapi, messages))
                            .build()
                        .build();
                }
            }
            topic.value(model);
        }
        return topic;
    }
}
