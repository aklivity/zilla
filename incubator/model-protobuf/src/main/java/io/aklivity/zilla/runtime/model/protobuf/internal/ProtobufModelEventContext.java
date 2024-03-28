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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.model.protobuf.internal.types.event.ProtobufModelEventType.VALIDATION_FAILED;

import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.model.protobuf.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.model.protobuf.internal.types.event.ProtobufModelEventExFW;

public class ProtobufModelEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final ProtobufModelEventExFW.Builder protobufModelEventExRW = new ProtobufModelEventExFW.Builder();
    private final int protobufModelTypeId;
    private final int validationFailedEventId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public ProtobufModelEventContext(
        EngineContext context)
    {
        this.protobufModelTypeId = context.supplyTypeId(ProtobufModel.NAME);
        this.validationFailedEventId = context.supplyEventId("model.protobuf.validation.failed");
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void validationFailure(
        long traceId,
        long bindingId,
        String error)
    {
        ProtobufModelEventExFW extension = protobufModelEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .validationFailed(e -> e
                .typeId(VALIDATION_FAILED.value())
                .error(error)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .id(validationFailedEventId)
            .timestamp(clock.millis())
            .traceId(traceId)
            .namespacedId(bindingId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(protobufModelTypeId, event.buffer(), event.offset(), event.limit());
    }
}
