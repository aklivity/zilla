/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.events;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.API_VERSION_REJECTED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.AUTHORIZATION_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.BROKER_CONNECTION_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.CLUSTER_AUTHORIZATION_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.GROUP_AUTHORIZATION_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.OFFSET_COMMIT_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.PRODUCE_ERROR;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.SASL_AUTHENTICATION_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.TOPIC_AUTHORIZATION_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.TRANSACTIONAL_ID_AUTHORIZATION_FAILED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.UNSUPPORTED_SASL_MECHANISM;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.concurent.SafeBuffer;

public class KafkaEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new SafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new SafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final KafkaEventExFW.Builder kafkaEventExRW = new KafkaEventExFW.Builder();
    private final int kafkaTypeId;
    private final int authorizationFailedEventId;
    private final int apiVersionRejectedEventId;
    private final int clusterAuthorizationFailedEventId;
    private final int topicAuthorizationFailedEventId;
    private final int saslAuthenticationFailedEventId;
    private final int brokerConnectionFailedEventId;
    private final int produceErrorEventId;
    private final int groupAuthorizationFailedEventId;
    private final int transactionalIdAuthorizationFailedEventId;
    private final int unsupportedSaslMechanismEventId;
    private final int offsetCommitFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public KafkaEventContext(
        EngineContext context)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.authorizationFailedEventId = context.supplyEventId("binding.kafka.authorization.failed");
        this.apiVersionRejectedEventId = context.supplyEventId("binding.kafka.api.version.rejected");
        this.clusterAuthorizationFailedEventId = context.supplyEventId("binding.kafka.cluster.authorization.failed");
        this.topicAuthorizationFailedEventId = context.supplyEventId("binding.kafka.topic.authorization.failed");
        this.saslAuthenticationFailedEventId = context.supplyEventId("binding.kafka.sasl.authentication.failed");
        this.brokerConnectionFailedEventId = context.supplyEventId("binding.kafka.broker.connection.failed");
        this.produceErrorEventId = context.supplyEventId("binding.kafka.produce.error");
        this.groupAuthorizationFailedEventId = context.supplyEventId("binding.kafka.group.authorization.failed");
        this.transactionalIdAuthorizationFailedEventId =
            context.supplyEventId("binding.kafka.transactional.id.authorization.failed");
        this.unsupportedSaslMechanismEventId = context.supplyEventId("binding.kafka.unsupported.sasl.mechanism");
        this.offsetCommitFailedEventId = context.supplyEventId("binding.kafka.offset.commit.failed");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void authorizationFailed(
        long traceId,
        long bindingId,
        String identity)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .authorizationFailed(e -> e
                .typeId(AUTHORIZATION_FAILED.value())
                .identity(identity)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(authorizationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void apiVersionRejected(
        long traceId,
        long bindingId,
        int apiKey,
        int apiVersion)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .apiVersionRejected(e -> e
                .typeId(API_VERSION_REJECTED.value())
                .apiKey(apiKey)
                .apiVersion(apiVersion)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(apiVersionRejectedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void clusterAuthorizationFailed(
        long traceId,
        long bindingId,
        int apiKey,
        int apiVersion)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .clusterAuthorizationFailed(e -> e
                .typeId(CLUSTER_AUTHORIZATION_FAILED.value())
                .apiKey(apiKey)
                .apiVersion(apiVersion)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(clusterAuthorizationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void topicAuthorizationFailed(
        long traceId,
        long bindingId,
        int apiKey,
        int apiVersion,
        String topic)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .topicAuthorizationFailed(e -> e
                .typeId(TOPIC_AUTHORIZATION_FAILED.value())
                .apiKey(apiKey)
                .apiVersion(apiVersion)
                .topic(topic)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(topicAuthorizationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void saslAuthenticationFailed(
        long traceId,
        long bindingId,
        String identity)
    {
        saslAuthenticationFailed(traceId, bindingId, identity, null);
    }

    public void saslAuthenticationFailed(
        long traceId,
        long bindingId,
        String identity,
        String error)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .saslAuthenticationFailed(e -> e
                .typeId(SASL_AUTHENTICATION_FAILED.value())
                .identity(identity)
                .error(error)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(saslAuthenticationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void brokerConnectionFailed(
        long traceId,
        long bindingId,
        String host,
        int port)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .brokerConnectionFailed(e -> e
                .typeId(BROKER_CONNECTION_FAILED.value())
                .host(host)
                .port(port)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(brokerConnectionFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void produceError(
        long traceId,
        long bindingId,
        int apiKey,
        int apiVersion,
        int errorCode,
        String topic)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .produceError(e -> e
                .typeId(PRODUCE_ERROR.value())
                .apiKey(apiKey)
                .apiVersion(apiVersion)
                .errorCode(errorCode)
                .topic(topic)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(produceErrorEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void groupAuthorizationFailed(
        long traceId,
        long bindingId,
        int apiKey,
        int apiVersion)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .groupAuthorizationFailed(e -> e
                .typeId(GROUP_AUTHORIZATION_FAILED.value())
                .apiKey(apiKey)
                .apiVersion(apiVersion)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(groupAuthorizationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void transactionalIdAuthorizationFailed(
        long traceId,
        long bindingId,
        int apiKey,
        int apiVersion)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .transactionalIdAuthorizationFailed(e -> e
                .typeId(TRANSACTIONAL_ID_AUTHORIZATION_FAILED.value())
                .apiKey(apiKey)
                .apiVersion(apiVersion)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(transactionalIdAuthorizationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void unsupportedSaslMechanism(
        long traceId,
        long bindingId,
        String mechanism)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .unsupportedSaslMechanism(e -> e
                .typeId(UNSUPPORTED_SASL_MECHANISM.value())
                .mechanism(mechanism)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(unsupportedSaslMechanismEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }

    public void offsetCommitFailed(
        long traceId,
        long bindingId,
        int apiKey,
        int apiVersion,
        int errorCode)
    {
        KafkaEventExFW extension = kafkaEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .offsetCommitFailed(e -> e
                .typeId(OFFSET_COMMIT_FAILED.value())
                .apiKey(apiKey)
                .apiVersion(apiVersion)
                .errorCode(errorCode)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(offsetCommitFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(kafkaTypeId, event.buffer(), event.offset(), event.limit());
    }
}
