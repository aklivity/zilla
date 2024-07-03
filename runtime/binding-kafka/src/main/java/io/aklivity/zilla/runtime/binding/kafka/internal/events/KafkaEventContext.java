/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventType.CLUSTER_AUTHORIZATION_FAILED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.event.KafkaEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class KafkaEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final KafkaEventExFW.Builder kafkaEventExRW = new KafkaEventExFW.Builder();
    private final int kafkaTypeId;
    private final int authorizationFailedEventId;
    private final int apiVersionRejectedEventId;
    private final int clusterAuthorizationFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public KafkaEventContext(
        EngineContext context)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.authorizationFailedEventId = context.supplyEventId("binding.kafka.authorization.failed");
        this.apiVersionRejectedEventId = context.supplyEventId("binding.kafka.api.version.rejected");
        this.clusterAuthorizationFailedEventId = context.supplyEventId("binding.kafka.cluster.authorization.failed");
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
}
