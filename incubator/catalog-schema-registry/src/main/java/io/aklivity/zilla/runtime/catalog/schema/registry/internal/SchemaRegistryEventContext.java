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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import static io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventType.REMOTE_ACCESS_REJECTED;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.time.Clock;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.catalog.schema.registry.internal.types.event.SchemaRegistryEventExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class SchemaRegistryEventContext
{
    private static final int EVENT_BUFFER_CAPACITY = 1024;

    private final AtomicBuffer eventBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final AtomicBuffer extensionBuffer = new UnsafeBuffer(ByteBuffer.allocate(EVENT_BUFFER_CAPACITY));
    private final EventFW.Builder eventRW = new EventFW.Builder();
    private final SchemaRegistryEventExFW.Builder schemaRegistryEventExRW = new SchemaRegistryEventExFW.Builder();
    private final int schemaRegistryTypeId;
    private final MessageConsumer eventWriter;
    private final Clock clock;

    public SchemaRegistryEventContext(
        EngineContext context)
    {
        this.schemaRegistryTypeId = context.supplyTypeId(SchemaRegistryCatalog.NAME);
        this.eventWriter = context.supplyEventWriter();
        this.clock = context.clock();
    }

    public void remoteAccessRejected(
        long catalogId,
        HttpRequest httpRequest,
        int status)
    {
        SchemaRegistryEventExFW extension = schemaRegistryEventExRW
            .wrap(extensionBuffer, 0, extensionBuffer.capacity())
            .remoteAccessRejected(e -> e
                .typeId(REMOTE_ACCESS_REJECTED.value())
                .method(httpRequest.method())
                .url(httpRequest.uri().toString())
                .status((short) status)
            )
            .build();
        EventFW event = eventRW
            .wrap(eventBuffer, 0, eventBuffer.capacity())
            .timestamp(clock.millis())
            .traceId(0L)
            .namespacedId(catalogId)
            .extension(extension.buffer(), extension.offset(), extension.limit())
            .build();
        eventWriter.accept(schemaRegistryTypeId, event.buffer(), event.offset(), event.limit());
    }
}
